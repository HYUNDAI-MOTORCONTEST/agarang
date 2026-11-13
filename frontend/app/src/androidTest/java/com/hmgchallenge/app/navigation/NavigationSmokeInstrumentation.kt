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
import com.hmgchallenge.shared.journey.JourneySnapshot
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
    private var officialPoiWaypoint = false
    private var cancelOwnedPublicFixture: String? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        mutateRoute = arguments?.getString("mutateEmulatorRoute") == "true"
        officialPoiWaypoint = arguments?.getString("officialPoiWaypoint") == "true"
        cancelOwnedPublicFixture = arguments?.getString("cancelOwnedPublicFixture")
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var resultCode = Activity.RESULT_OK
        var fixture: NaviHelper? = null
        val trace = CopyOnWriteArrayList<String>()
        val stateReplies = AtomicInteger()
        val latestMode = AtomicReference<NaviMode>()
        val latestDestination = AtomicReference<DestinationInfo>()
        val latestWaypoints = AtomicReference<List<WaypointInfo>>()
        var createdRoute = false
        fun note(value: String) {
            trace += "${Thread.currentThread().name}:$value"
            if (trace.size > 80) trace.removeAt(0)
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
            override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                latestDestination.set(destinationInfo)
                val samplePoint = abs(destinationInfo.latitude - 37.48544722) < 0.002 && abs(destinationInfo.longitude - 127.03636666) < 0.002
                val sampleLabel = destinationInfo.name in setOf("42dot", FIXTURE_NAME)
                note("destination:${destinationInfo.remainTime}:${destinationInfo.remainDistance}:samplePoint=$samplePoint:sampleLabel=$sampleLabel:officialPoi=${destinationInfo.poiId == PUBLIC_DESTINATION_ID}:lat=${destinationInfo.latitude},lon=${destinationInfo.longitude}")
                if (createdRoute && destinationInfo.poiId == PUBLIC_DESTINATION_ID)
                    note("ownedPublicDestination:${destinationInfo.latitude},${destinationInfo.longitude}:display=${destinationInfo.displayLatitude},${destinationInfo.displayLongitude}")
            }
            override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
                latestWaypoints.set(waypointInfoList.toList())
                note("waypoints:count=${waypointInfoList.size}:zero=${waypointInfoList.count { it.latitude == 0.0 && it.longitude == 0.0 }}:officialPoi=${waypointInfoList.count { it.poiId == OFFICIAL_WAYPOINT_ID }}")
                waypointInfoList.forEachIndexed { idx, wp ->
                    note(
                        "wp[$idx]:name=${wp.name}:addr=${wp.address}:lat=${wp.latitude},lon=${wp.longitude}" +
                            ":disp=${wp.displayLatitude},${wp.displayLongitude}:remD=${wp.remainDistance}:remT=${wp.remainTime}" +
                            ":poi=${wp.poiId}:sub=${wp.poiSubId}:prov=${wp.poiProvider}",
                    )
                }
                if (createdRoute && officialPoiWaypoint) waypointInfoList.filter { it.poiId == OFFICIAL_WAYPOINT_ID }.forEach {
                    note("ownedPublicWaypoint:${it.latitude},${it.longitude}:display=${it.displayLatitude},${it.displayLongitude}")
                }
            }
            override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
                val startInfo = waypointChangedInfo.routeStartInfo
                note(
                    "waypointChanged:${waypointChangedInfo.index}:${waypointChangedInfo.cmdType}" +
                        ":start=${startInfo.name}/${startInfo.distance}m/${startInfo.duration}s",
                )
            }
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
        fun ownedFixture(journey: JourneySnapshot?): Boolean = journey?.let { value ->
            val point = value.destination
            val priorSample = value.destinationLabel in setOf("42dot", FIXTURE_NAME) && point != null &&
                abs(point.latitudeDegrees - 37.48544722) < 0.002 && abs(point.longitudeDegrees - 127.03636666) < 0.002
            val raw = latestDestination.get()
            // Once this run created the route from EMPTY, verify the echoed official POI and
            // valid returned geographic point. This does not establish facility access or an
            // entrance. Never substitute display coordinates or accept 0,0.
            val createdSample = createdRoute && raw?.poiId == PUBLIC_DESTINATION_ID && point != null &&
                (point.latitudeDegrees != 0.0 || point.longitudeDegrees != 0.0) &&
                point.latitudeDegrees == raw.latitude && point.longitudeDegrees == raw.longitude
            val existingOwnedPublic = mutateRoute && raw?.poiId == PUBLIC_DESTINATION_ID &&
                raw.name in setOf("42dot", FIXTURE_NAME) && point != null &&
                (point.latitudeDegrees != 0.0 || point.longitudeDegrees != 0.0) &&
                latestWaypoints.get().orEmpty().all {
                    it.poiId == OFFICIAL_WAYPOINT_ID ||
                        (abs(it.latitude - 37.49) <= 0.002 && abs(it.longitude - 127.04) <= 0.002)
                }
            priorSample || createdSample || existingOwnedPublic
        } == true
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
                runOnMainSync {
                    // No second SDK connection: this object only checks local input/permission state.
                    val localCheck = PleosNavigationClient(targetContext)
                    val coordinateOnly = StopCandidate(
                        CareFacility("sdk-local-preflight", "검증용 시설", GeoPoint(37.49, 127.04), FacilityType.REST_AREA),
                        DataOrigin.NAVI_SDK, RouteAccess.CONFIRMED_ON_ROUTE,
                        navigationLocation = GeoPoint(37.49, 127.04), journeyId = "local-preflight",
                        accessVerifiedAtEpochMillis = System.currentTimeMillis(),
                    )
                    check(localCheck.waypointPreflight(coordinateOnly) == com.hmgchallenge.shared.journey.StopRequestProblem.INVALID_CANDIDATE)
                    localCheck.release()
                    result.putString("coordinateOnlyPreflight", "blocked-before-ipc")
                }
                if (cancelOwnedPublicFixture != null) {
                    // Explicit recovery for a late recreation AFTER a previous fixture cleanup.
                    // The caller must have the earlier EMPTY/creation trace and supply its exact
                    // returned destination key. Never infer ownership from READY or a label alone.
                    check(!mutateRoute) { "cleanup cannot also create or add a route" }
                    check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "fixture cleanup requires an emulator" }
                    check(initial.status == DataStatus.READY)
                    withTimeout(5_000) { while (latestWaypoints.get() == null) delay(25) }
                    val raw = checkNotNull(latestDestination.get())
                    check(raw.poiId == "36khp37hh8gqcm5nqhe5" && raw.name in setOf("42dot", "내비 연동 검증 목적지"))
                    check("${raw.poiId}:${raw.latitude}:${raw.longitude}" == cancelOwnedPublicFixture) { "fixture identity changed" }
                    check(latestWaypoints.get()!!.all { it.poiId == OFFICIAL_WAYPOINT_ID }) { "non-fixture waypoint must be preserved" }
                    createdRoute = true // Only the finally block may cancel this verified fixture.
                    result.putString("ownedFixtureRecovery", "passed")
                } else if (mutateRoute) {
                    check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "route fixtures require an emulator" }
                    if (initial.status == DataStatus.READY) {
                        withTimeout(5_000) { while (latestDestination.get() == null || latestWaypoints.get() == null) delay(25) }
                        // A prior run may have received a late completion for this same fixture.
                        // Continue only that owned, publicly identified sample, never another route.
                        check(ownedFixture(initial.readyValue)) { "an unrelated pre-existing route must be preserved" }
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
                    val routed = withTimeout(45_000) { source.snapshots.first { it.status == DataStatus.READY && ownedFixture(it.readyValue) } }
                    checkNotNull(routed.readyValue?.estimatedArrivalAtEpochMillis)
                    check(routed.readyValue!!.estimatedArrivalAtEpochMillis!! >= routed.observedAtEpochMillis!!)
                    result.putString("routeRead", "passed")
                    result.putString("routeIdentity", "passed")
                    result.putLong("remainingDurationSeconds", (routed.readyValue!!.estimatedArrivalAtEpochMillis!! - routed.observedAtEpochMillis!!) / 1_000)
                    result.putLong("remainingDistanceMeters", routed.readyValue!!.remainingDistanceMeters ?: -1)
                    // Returning from background must recover an already active native route too.
                    runOnMainSync { source.stop(); source.start() }
                    withTimeout(20_000) { source.snapshots.first { it.status == DataStatus.READY && ownedFixture(it.readyValue) } }
                    runOnMainSync { checkNotNull(fixture).addListener(probe) }
                    result.putString("activeRouteReconnect", "passed")

                    // A synthetic on-route fixture in an empty emulator session. Production
                    // facilities still require #6's provider/access verification.
                    val waypointLocation = if (officialPoiWaypoint) GeoPoint(37.41275833, 127.09481944) else GeoPoint(37.49, 127.04)
                    val waypointName = if (officialPoiWaypoint) OFFICIAL_WAYPOINT_NAME else "내비 연동 검증 경유지"
                    val candidate = StopCandidate(
                        CareFacility("sdk-test-stop", waypointName, waypointLocation, FacilityType.REST_AREA),
                        DataOrigin.NAVI_SDK, RouteAccess.CONFIRMED_ON_ROUTE,
                        navigationLocation = waypointLocation,
                        journeyId = source.snapshots.value.readyValue!!.id,
                        accessVerifiedAtEpochMillis = System.currentTimeMillis(),
                        navigationPoiId = OFFICIAL_WAYPOINT_ID.takeIf { officialPoiWaypoint },
                    )
                    result.putString("waypointFixture", if (officialPoiWaypoint) "official-poi-id" else "coordinates-only")
                    runOnMainSync { source.prepareStop(candidate) }
                    val prepared = withTimeout(15_000) { source.stopRequest.first { it.phase != StopRequestPhase.CHECKING } }
                    if (prepared.phase == StopRequestPhase.CONFIRMED) {
                        result.putString("reusedRegisteredStop", "passed")
                    } else {
                        check(prepared.phase == StopRequestPhase.AWAITING_CONFIRMATION) { "prepare=${prepared.phase}:${prepared.problem}" }
                        runOnMainSync { note("fixture:confirmStop"); source.confirmStop(checkNotNull(prepared.confirmation).id) }
                        val confirmed = withTimeout(45_000) {
                            source.stopRequest.first {
                                // Guidance can temporarily be cleared during a waypoint recalculation.
                                // OUTCOME_UNKNOWN is not final: allow later readback to resolve it.
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
            result.putString("routeIdentityAtFailure", ownedFixture(source.snapshots.value.readyValue).toString())
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
                        repeat(15) {
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
                    result.putString("fixtureQuietWindow", "15-fresh-empty-safe-guiding-replies")
                }
            } catch (error: Exception) {
                result.putString("fixtureCleanup", "unconfirmed")
                result.putString("fixtureCleanupProblem", error.javaClass.simpleName)
                result.putString("result", "failed")
                if (!result.containsKey("problem")) result.putString("problem", "fixture cleanup unconfirmed")
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

    private companion object {
        const val FIXTURE_NAME = "내비 연동 검증 목적지"
        const val PUBLIC_DESTINATION_ID = "36khp37hh8gqcm5nqhe5"
        // Public waypoint from the official Pleos waypoint-settings example, not a facility recommendation.
        const val OFFICIAL_WAYPOINT_ID = "36khp2hdj2cfcm5nqhe3"
        const val OFFICIAL_WAYPOINT_NAME = "소프트웨어드림센터"
    }
}
