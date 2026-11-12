package com.hmgchallenge.app.navigation

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.CareFacility
import com.hmgchallenge.shared.facility.FacilityType
import com.hmgchallenge.shared.facility.RouteAccess
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.NavigationRepository
import com.hmgchallenge.shared.journey.StopRequestPhase
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import ai.pleos.playground.navi.constants.NaviMode
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Platform-only, opt-in SDK smoke test. Never launched by the production app. The default run
 * reads state only. Route mutation requires an explicit runner argument, an emulator, and no
 * pre-existing route. It never accepts navigation terms or overwrites a caregiver's journal.
 */
class NavigationSmokeInstrumentation : Instrumentation() {
    private var mutateRoute = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        mutateRoute = arguments?.getString("mutateEmulatorRoute") == "true"
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var resultCode = Activity.RESULT_OK
        var fixture: NaviHelper? = null
        val trace = CopyOnWriteArrayList<String>()
        val stateReplies = AtomicInteger()
        val latestMode = AtomicReference<NaviMode>()
        fun note(value: String) {
            trace += "${Thread.currentThread().name}:$value"
            if (trace.size > 40) trace.removeAt(0)
        }
        val probe = object : NaviHelperEventListener {
            override fun onRouteStarted(routeStartInfo: RouteStartInfo) { note("start:${routeStartInfo.duration}:${routeStartInfo.distance}") }
            override fun onRouteEnded() { note("routeEnd") }
            override fun onRoutingCompleted() { note("routingComplete") }
            override fun onNavigationStatus(navigationStatus: NavigationStatus) { note("status:$navigationStatus") }
            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                latestMode.set(routeStateInfo.naviMode)
                stateReplies.incrementAndGet()
                note("state:${routeStateInfo.isRouting}:mode=${routeStateInfo.naviMode}:terms=${routeStateInfo.termsAgreement}")
            }
            override fun onDestinationInfo(destinationInfo: DestinationInfo) { note("destination:${destinationInfo.remainTime}:${destinationInfo.remainDistance}") }
            override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) { note("waypoints:${waypointInfoList.map { "${it.latitude},${it.longitude}" }}") }
            override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) { note("waypointChanged:${waypointChangedInfo.index}:${waypointChangedInfo.cmdType}") }
            override fun onRouteCancelled() { note("routeCancelled") }
            override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) { note("destinationArrived") }
            override fun onError(errorCode: NaviErrorCode) { note("error:$errorCode") }
        }
        // Use the same single SDK instance for the fixture request and adapter subscription. A
        // second NaviHelper in the target process can replace the service's callback registration.
        val source = NavigationRepository({
            PleosNavigationClient(targetContext) { context -> NaviHelper(context).also { fixture = it } }
        })
        val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        timerScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1_000)
                runOnMainSync {
                    source.tick()
                    if (++seconds % 30 == 0 && source.connection.value == DataStatus.READY) source.refresh()
                }
            }
        }
        var createdRoute = false
        try {
            runOnMainSync { source.start() }
            runBlocking {
                val connection = withTimeout(20_000) { source.connection.first { it != DataStatus.LOADING && it != DataStatus.EMPTY } }
                check(connection == DataStatus.READY) { "connection=$connection" }
                runOnMainSync { checkNotNull(fixture).addListener(probe) }
                val initial = withTimeout(15_000) { source.snapshots.first { it.status != DataStatus.LOADING } }
                check(initial.status == DataStatus.READY || initial.status == DataStatus.EMPTY) { "read=${initial.status}" }
                result.putString("passive", "passed")
                result.putString("initialRoute", initial.status.name)
                if (mutateRoute) {
                    check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "route fixtures require an emulator" }
                    if (initial.status == DataStatus.READY) {
                        val existing = checkNotNull(initial.readyValue)
                        val point = existing.destination
                        // A prior run may have received a late completion for this same fixture.
                        // Continue only that owned, publicly identified sample, never another route.
                        check(existing.destinationLabel in setOf("42dot", "내비 연동 검증 목적지") && point != null &&
                            abs(point.latitudeDegrees - 37.48544722) < 0.002 &&
                            abs(point.longitudeDegrees - 127.03636666) < 0.002
                        ) { "an unrelated pre-existing route must be preserved" }
                        createdRoute = true
                        result.putString("reusedOwnedFixture", "passed")
                    }
                    // Public sample location from the official destinations guide. An isolated test
                    // destination, never a facility recommendation or a real user's journey.
                    if (initial.status == DataStatus.EMPTY) runOnMainSync {
                        note("fixture:requestRoute")
                        checkNotNull(fixture).requestRoute(RouteInfo(
                            latitude = 37.48544722, longitude = 127.03636666,
                            poiName = "내비 연동 검증 목적지", poiId = "36khp37hh8gqcm5nqhe5",
                            address = "", poiSubId = "0",
                        ))
                        createdRoute = true
                    }
                    val routed = withTimeout(45_000) { source.snapshots.first { it.status == DataStatus.READY } }
                    checkNotNull(routed.readyValue?.estimatedArrivalAtEpochMillis)
                    check(routed.readyValue!!.estimatedArrivalAtEpochMillis!! >= routed.observedAtEpochMillis!!)
                    result.putString("routeRead", "passed")
                    result.putLong("remainingDurationSeconds", (routed.readyValue!!.estimatedArrivalAtEpochMillis!! - routed.observedAtEpochMillis!!) / 1_000)
                    result.putLong("remainingDistanceMeters", routed.readyValue!!.remainingDistanceMeters ?: -1)
                    // Returning from background must recover an already active native route too.
                    runOnMainSync { source.stop(); source.start() }
                    withTimeout(20_000) { source.snapshots.first { it.status == DataStatus.READY } }
                    runOnMainSync { checkNotNull(fixture).addListener(probe) }
                    result.putString("activeRouteReconnect", "passed")

                    // A synthetic on-route fixture in an empty emulator session. Production
                    // facilities still require #6's provider/access verification.
                    val candidate = StopCandidate(
                        CareFacility("sdk-test-stop", "내비 연동 검증 경유지", GeoPoint(37.49, 127.04), FacilityType.REST_AREA),
                        DataOrigin.NAVI_SDK, RouteAccess.CONFIRMED_ON_ROUTE,
                    )
                    runOnMainSync { source.prepareStop(candidate) }
                    val prepared = withTimeout(15_000) { source.stopRequest.first { it.phase != StopRequestPhase.CHECKING } }
                    if (prepared.phase == StopRequestPhase.CONFIRMED) {
                        result.putString("reusedRegisteredStop", "passed")
                    } else {
                        check(prepared.phase == StopRequestPhase.AWAITING_CONFIRMATION) { "prepare=${prepared.phase}:${prepared.problem}" }
                        runOnMainSync { note("fixture:confirmStop"); source.confirmStop(checkNotNull(prepared.confirmation).id) }
                        val confirmed = withTimeout(45_000) {
                            source.stopRequest.first {
                                it.phase == StopRequestPhase.CONFIRMED || it.phase == StopRequestPhase.FAILED
                            }
                        }
                        check(confirmed.phase == StopRequestPhase.CONFIRMED) { "register=${confirmed.phase}:${confirmed.problem}" }
                    }
                    result.putString("confirmedWaypoint", "passed")
                }
            }
            result.putString("result", "passed")
        } catch (error: Exception) {
            result.putString("result", "failed")
            result.putString("problem", "${error.javaClass.simpleName}:${error.message}")
            result.putString("stopState", "${source.stopRequest.value.phase}:${source.stopRequest.value.problem}")
            resultCode = Activity.RESULT_CANCELED
        } finally {
            timerScope.cancel()
            try {
                if (createdRoute) {
                    runOnMainSync { note("fixture:cleanup"); fixture?.cancelRoute() }
                    runBlocking {
                        withTimeout(15_000) { source.snapshots.first { it.status == DataStatus.EMPTY } }
                        // Check fresh mode responses too. A FREE_DRIVE event alone can arrive
                        // before the navigation backend has actually finished cancelling.
                        repeat(3) {
                            delay(1_000)
                            val previous = stateReplies.get()
                            runOnMainSync { source.refresh() }
                            withTimeout(5_000) { while (stateReplies.get() == previous) delay(25) }
                            runOnMainSync { }
                            check(latestMode.get() == NaviMode.SAFE_GUIDING || latestMode.get() == NaviMode.NOT_WORKING)
                            check(source.snapshots.value.status == DataStatus.EMPTY)
                        }
                    }
                    result.putString("fixtureCleanup", "passed")
                }
            } catch (_: Exception) {
                result.putString("fixtureCleanup", "unconfirmed")
                resultCode = Activity.RESULT_CANCELED
            } finally {
                runOnMainSync {
                    fixture?.removeListener(probe)
                    source.stop()
                }
            }
        }
        result.putString("callbackTrace", trace.joinToString("|"))
        // Complete only after cleanup: Instrumentation.finish may terminate the target process.
        finish(resultCode, result)
    }
}
