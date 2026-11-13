package com.hmgchallenge.shared.journey

import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.data.SnapshotSource
import com.hmgchallenge.shared.facility.StopCandidate
import kotlinx.coroutines.flow.StateFlow

data class NavigationWaypoint(val label: String, val location: GeoPoint, val poiId: String? = null) {
    init { require(label.isNotBlank() && poiId?.isNotBlank() != false) }
}

/** Normalized at the SDK boundary. These are durations in seconds, never epoch seconds or minutes.
 * DestinationInfo's API reference documents remainTime in seconds and remainDistance in meters.
 */
data class NavigationDestination(
    val key: String,
    val label: String,
    val location: GeoPoint,
    val remainingDurationSeconds: Long?,
    val remainingDistanceMeters: Long?,
    val isSimulation: Boolean = false,
) {
    fun toJourney(id: String, observedAtEpochMillis: Long): JourneySnapshot {
        require(key.isNotBlank() && label.isNotBlank() && observedAtEpochMillis >= 0)
        require(remainingDurationSeconds == null || remainingDurationSeconds >= 0)
        require(remainingDistanceMeters == null || remainingDistanceMeters >= 0)
        val arrival = remainingDurationSeconds?.let { seconds ->
            Math.addExact(observedAtEpochMillis, Math.multiplyExact(seconds, 1_000L))
        }
        return JourneySnapshot(id, label, arrival, remainingDistanceMeters, location, isSimulation)
    }
}

/** Only the app implements this interface with Pleos objects. No SDK objects escape into shared. */
interface NavigationClient {
    interface Listener {
        fun onConnected()
        fun onFailure(status: DataStatus)
        fun onRouteState(active: Boolean)
        fun onRouteInvalidated()
        fun onRouteCleared()
        fun onDestination(destination: NavigationDestination)
        fun onWaypoints(waypoints: List<NavigationWaypoint>)
    }

    fun connect(listener: Listener)
    fun requestRouteState()
    fun requestDestination()
    fun requestWaypoints()
    /** Local capability/permission check only; must not change the native route. */
    fun waypointPreflight(candidate: StopCandidate): StopRequestProblem? = null
    fun addWaypoint(candidate: StopCandidate, slot: Int)
    fun release()
}

enum class StopRequestPhase { IDLE, CHECKING, AWAITING_CONFIRMATION, SUBMITTING, CONFIRMED, FAILED, OUTCOME_UNKNOWN }
enum class StopRequestProblem { ROUTE_UNAVAILABLE, ROUTE_CHANGED, WAYPOINTS_FULL, INVALID_CANDIDATE, PERMISSION_DENIED, REQUEST_FAILED, TIMED_OUT }

/** The client can prove that no mutation IPC began. Unknown SDK failures must not use this type. */
class NavigationRequestNotSent(val problem: StopRequestProblem) : Exception(problem.name)

data class StopConfirmation(
    val id: String,
    val candidate: StopCandidate,
    val journeyId: String,
    val slot: Int,
    val existingWaypoints: List<NavigationWaypoint>,
    val createdAtEpochMillis: Long,
)

data class StopRequestState(
    val phase: StopRequestPhase = StopRequestPhase.IDLE,
    val candidate: StopCandidate? = null,
    val confirmation: StopConfirmation? = null,
    val problem: StopRequestProblem? = null,
    val isDemo: Boolean = false,
)

interface NavigationSource : SnapshotSource<JourneySnapshot> {
    val connection: StateFlow<DataStatus>
    val waypoints: StateFlow<DataSnapshot<List<NavigationWaypoint>>>
    val stopRequest: StateFlow<StopRequestState>
    fun start()
    fun stop()
    fun refresh()
    fun tick()
    fun prepareStop(candidate: StopCandidate)
    fun confirmStop(confirmationId: String)
    fun cancelStop()
}
