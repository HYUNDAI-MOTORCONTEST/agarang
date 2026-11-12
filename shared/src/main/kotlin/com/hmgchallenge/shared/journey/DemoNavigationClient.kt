package com.hmgchallenge.shared.journey

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.facility.StopCandidate

/** Same callbacks as the SDK client. It owns no SDK and cannot change a real route. */
class DemoNavigationClient(initial: NavigationDestination) : NavigationClient {
    private var listener: NavigationClient.Listener? = null
    private var destination: NavigationDestination? = initial
    private var slots = emptyList<NavigationWaypoint>()

    override fun connect(listener: NavigationClient.Listener) {
        this.listener = listener
        listener.onConnected()
    }
    override fun requestRouteState() { listener?.onRouteState(destination != null) }
    override fun requestDestination() { destination?.let { listener?.onDestination(it) } }
    override fun requestWaypoints() { listener?.onWaypoints(slots) }
    override fun addWaypoint(candidate: StopCandidate, slot: Int) {
        require(candidate.origin == DataOrigin.DEMO && slot == slots.size + 1 && slot in 1..2)
        slots = slots + NavigationWaypoint(candidate.facility.name, candidate.facility.location)
        requestWaypoints()
    }
    override fun release() { listener = null }

    fun publish(next: NavigationDestination?) {
        destination = next
        if (next == null) listener?.onRouteCleared() else listener?.onRouteInvalidated()
    }
}
