package com.hmgchallenge.app.navigation

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.NavigationClient
import com.hmgchallenge.shared.journey.NavigationDestination
import com.hmgchallenge.shared.journey.NavigationWaypoint
import com.hmgchallenge.shared.journey.NavigationRequestNotSent
import com.hmgchallenge.shared.journey.StopRequestProblem

/** Owns the SDK connection and translates every binder callback onto the main looper. Creation,
 * requests and release are called on that same looper by the foreground ViewModel session.
 */
class PleosNavigationClient(
    context: Context,
    private val createHelper: (Context) -> NaviHelper = { NaviHelper(it) },
) : NavigationClient {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var target: NavigationClient.Listener? = null
    private var helper: NaviHelper? = null
    private var released = false
    private var listenerRegistered = false
    private var lastStatus: NavigationStatus? = null
    private var simulation = false

    private fun deliver(block: NavigationClient.Listener.() -> Unit) {
        handler.post {
            if (!released) {
                try { target?.block() }
                catch (_: IllegalArgumentException) { target?.onFailure(DataStatus.FAILED) }
            }
        }
    }

    private val events = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = deliver {
            simulation = routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
            val guiding = routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING || simulation
            // Service 3.0.4 reports isRouting=false even during ROUTE_GUIDING. Status-change
            // events can precede the actual change; the queried mode also works after rebinding.
            onRouteState(guiding)
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = deliver {
            // API Reference: remainTime is a duration in SECONDS; remainDistance is METERS.
            // Preserve geographic latitude/longitude, not optional map-display coordinates.
            // These fields alone do not verify a facility entrance or current-route access.
            // docs/agarang/navigation.md links the exact DestinationInfo API Reference.
            onDestination(NavigationDestination(
                key = "${destinationInfo.poiId}:${destinationInfo.latitude}:${destinationInfo.longitude}",
                label = destinationInfo.name,
                location = GeoPoint(destinationInfo.latitude, destinationInfo.longitude),
                remainingDurationSeconds = destinationInfo.remainTime.toLong(),
                remainingDistanceMeters = destinationInfo.remainDistance.toLong(),
                isSimulation = simulation,
            ))
        }
        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = deliver {
            onWaypoints(waypointInfoList.map { NavigationWaypoint(it.name, GeoPoint(it.latitude, it.longitude), it.poiId.takeIf(String::isNotBlank)) })
        }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = deliver {
            lastStatus = NavigationStatus.INITIALIZING
            onRouteInvalidated()
        }
        override fun onRoutingCompleted() = deliver { onRouteInvalidated() }
        override fun onReRouteEnded() = deliver { onRouteInvalidated() }
        override fun onDestinationChanged() = deliver { onRouteInvalidated() }
        override fun onRouteOptionChanged(routeOption: RouteOption) = deliver { onRouteInvalidated() }
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = deliver { onRouteInvalidated() }
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = deliver { onRouteInvalidated() }
        override fun onRouteCancelled() = deliver {
            onRouteInvalidated()
        }
        // Navi service 3.0.4 sends this deprecated SEARCH-completion event. NaviHelper 3.0.5's
        // AAR says "Use onRoutingCompleted() instead"; it does not mean guidance was cancelled.
        override fun onRouteEnded() = deliver { onRouteInvalidated() }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = deliver {
            onRouteInvalidated()
        }
        override fun onNavigationStatus(navigationStatus: NavigationStatus) = deliver {
            if (navigationStatus != lastStatus) {
                lastStatus = navigationStatus
                onRouteInvalidated()
            }
        }
        override fun onError(errorCode: NaviErrorCode) = deliver {
            Log.w(TAG, "Navigation request failed (${errorCode.name})")
            onFailure(DataStatus.FAILED)
        }
    }

    override fun connect(listener: NavigationClient.Listener) {
        check(helper == null && !released)
        target = listener
        // The published guide allows NaviHelper without a separate SDK application. Still verify
        // this installation's permission grant before binding/calling an API; absence is not 0 ETA.
        @Suppress("DEPRECATION")
        try { context.packageManager.getPermissionInfo(READ_PERMISSION, 0) }
        catch (_: PackageManager.NameNotFoundException) {
            listener.onFailure(DataStatus.UNSUPPORTED)
            return
        }
        if (context.checkSelfPermission(READ_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            listener.onFailure(DataStatus.PERMISSION_DENIED)
            return
        }
        val navi = createHelper(context)
        helper = navi
        navi.initialize(
            onConnected = {
                deliver {
                    try {
                        navi.addListener(events)
                        listenerRegistered = true
                        Log.d(TAG, "Navigation connected; callbacks serialized on main")
                        onConnected()
                    } catch (_: SecurityException) {
                        onFailure(DataStatus.PERMISSION_DENIED)
                    } catch (_: Exception) {
                        onFailure(DataStatus.FAILED)
                    }
                }
            },
            onFailed = { error ->
                deliver { onFailure(if (error is SecurityException) DataStatus.PERMISSION_DENIED else DataStatus.FAILED) }
            },
        )
    }

    override fun requestRouteState() { checkNotNull(helper).getRouteStateInfo() }
    override fun requestDestination() { checkNotNull(helper).getDestinationInfo() }
    override fun requestWaypoints() { checkNotNull(helper).getWaypointInfo() }

    override fun waypointPreflight(candidate: StopCandidate): StopRequestProblem? = when {
        !candidate.canRequestNavigation || candidate.navigationPoiId == null -> StopRequestProblem.INVALID_CANDIDATE
        context.checkSelfPermission(WRITE_PERMISSION) != PackageManager.PERMISSION_GRANTED -> StopRequestProblem.PERMISSION_DENIED
        else -> null
    }

    override fun addWaypoint(candidate: StopCandidate, slot: Int) {
        // Coordinate-only requests are undocumented and returned zero geographic coordinates in the
        // test service. Require a provider POI ID; never manufacture one from a catalog row ID.
        waypointPreflight(candidate)?.let { throw NavigationRequestNotSent(it) }
        if (slot !in 1..2) throw NavigationRequestNotSent(StopRequestProblem.INVALID_CANDIDATE)
        checkNotNull(helper).addWaypoint(RequestWaypointInfo(
            latitude = requireNotNull(candidate.navigationLocation).latitudeDegrees,
            longitude = requireNotNull(candidate.navigationLocation).longitudeDegrees,
            poiName = candidate.facility.name,
            waypointIndex = if (slot == 1) WaypointIndex.FIRST else WaypointIndex.SECOND,
            poiId = requireNotNull(candidate.navigationPoiId),
            address = "",
            poiSubId = "0",
        ))
    }

    override fun release() {
        released = true
        target = null
        val navi = helper
        helper = null
        try { if (listenerRegistered) navi?.removeListener(events) }
        finally {
            listenerRegistered = false
            navi?.release()
        }
    }

    private companion object {
        const val TAG = "HmgApp"
        const val READ_PERMISSION = "pleos.car.permission.NAVI_ROUTE"
        const val WRITE_PERMISSION = "pleos.car.permission.NAVI_ROUTE_SEARCH"
    }
}
