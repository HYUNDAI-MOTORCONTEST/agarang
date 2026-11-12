package com.hmgchallenge.shared.journey

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.RouteAccess
import com.hmgchallenge.shared.facility.StopCandidate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A callback-driven session, shared by the real and demo clients. Public calls and callbacks are
 * serialized by this monitor. The app supplies the foreground-only refresh/expiry timer.
 */
class NavigationRepository(
    private val clientFactory: () -> NavigationClient,
    private val origin: DataOrigin = DataOrigin.NAVI_SDK,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : NavigationSource {
    init { require(origin == DataOrigin.NAVI_SDK || origin == DataOrigin.DEMO) }

    private val mutableSnapshots = MutableStateFlow(DataSnapshot<JourneySnapshot>(origin, DataStatus.EMPTY))
    override val snapshots = mutableSnapshots.asStateFlow()
    private val mutableConnection = MutableStateFlow(DataStatus.EMPTY)
    override val connection = mutableConnection.asStateFlow()
    private val mutableWaypoints = MutableStateFlow(DataSnapshot<List<NavigationWaypoint>>(origin, DataStatus.EMPTY))
    override val waypoints = mutableWaypoints.asStateFlow()
    private val mutableStop = MutableStateFlow(StopRequestState(isDemo = origin == DataOrigin.DEMO))
    override val stopRequest = mutableStop.asStateFlow()

    private var client: NavigationClient? = null
    private var generation = 0L
    private var started = false
    private var activeRoute = false
    private var destinationKey: String? = null
    private var journeyId: String? = null
    private var startedAt = 0L
    private var requestedAt: Long? = null
    private var awaitingState = false
    private var awaitingDestination = false
    private var awaitingWaypoints = false
    private var submittedAt: Long? = null
    private var checkingConfirmation: StopConfirmation? = null
    private var routeRevision = 0L
    private var confirmationRevision = -1L

    @Synchronized
    override fun start() {
        if (started) return
        started = true
        startedAt = clock()
        val session = ++generation
        activeRoute = false
        mutableConnection.value = DataStatus.LOADING
        mutableSnapshots.value = unavailable(DataStatus.LOADING)
        mutableWaypoints.value = unavailable(DataStatus.LOADING)
        try {
            client = clientFactory()
            client!!.connect(object : NavigationClient.Listener {
                private fun receive(block: () -> Unit) = synchronized(this@NavigationRepository) {
                    if (started && generation == session) block()
                }
                override fun onConnected() = receive {
                    mutableConnection.value = DataStatus.READY
                    query()
                }
                override fun onFailure(status: DataStatus) = receive { fail(status) }
                override fun onRouteState(active: Boolean) = receive { routeState(active) }
                override fun onRouteInvalidated() = receive {
                    invalidateRoute()
                    query(force = true)
                }
                override fun onRouteCleared() = receive { clearRoute() }
                override fun onDestination(destination: NavigationDestination) = receive { destination(destination) }
                override fun onWaypoints(waypoints: List<NavigationWaypoint>) = receive { waypointUpdate(waypoints) }
            })
        } catch (_: SecurityException) {
            fail(DataStatus.PERMISSION_DENIED)
        } catch (_: Exception) {
            fail(DataStatus.FAILED)
        } catch (_: LinkageError) {
            fail(DataStatus.UNSUPPORTED)
        }
    }

    @Synchronized
    override fun stop() {
        started = false
        generation++ // Ignore callbacks queued by an old binder or a late initialize completion.
        activeRoute = false
        awaitingState = false
        awaitingDestination = false
        awaitingWaypoints = false
        requestedAt = null
        routeRevision++
        abortStop(StopRequestProblem.ROUTE_CHANGED)
        mutableSnapshots.value = staleOrFailed(snapshots.value, DataStatus.EMPTY)
        mutableWaypoints.value = staleOrFailed(waypoints.value, DataStatus.EMPTY)
        mutableConnection.value = DataStatus.EMPTY
        val old = client
        client = null
        try { old?.release() } catch (_: Exception) { /* State has already been invalidated. */ }
    }

    @Synchronized
    override fun refresh() {
        if (!started || connection.value != DataStatus.READY) {
            stop()
            start()
        } else query()
    }

    /** Invoked only while visible. No endless reconnect/retry on permission failure. */
    @Synchronized
    override fun tick() {
        if (!started) return
        val now = clock()
        if (connection.value == DataStatus.LOADING && expired(startedAt, now, CONNECT_TIMEOUT_MILLIS)) {
            fail(DataStatus.FAILED)
        }
        val pending = requestedAt
        if (pending != null && expired(pending, now, REQUEST_TIMEOUT_MILLIS)) {
            requestedAt = null
            awaitingState = false
            awaitingDestination = false
            awaitingWaypoints = false
            mutableSnapshots.value = staleOrFailed(snapshots.value, DataStatus.FAILED)
            mutableWaypoints.value = staleOrFailed(waypoints.value, DataStatus.FAILED)
            abortStop(StopRequestProblem.TIMED_OUT)
        }
        val sent = submittedAt
        if (sent != null && stopRequest.value.phase == StopRequestPhase.SUBMITTING &&
            expired(sent, now, REQUEST_TIMEOUT_MILLIS)
        ) abortStop(StopRequestProblem.TIMED_OUT)
        expireSnapshots(now)
    }

    @Synchronized
    override fun prepareStop(candidate: StopCandidate) {
        if (stopRequest.value.phase == StopRequestPhase.SUBMITTING || stopRequest.value.phase == StopRequestPhase.OUTCOME_UNKNOWN ||
            stopRequest.value.phase == StopRequestPhase.CHECKING) return
        val allowed = if (origin == DataOrigin.DEMO) {
            candidate.origin == DataOrigin.DEMO && candidate.routeAccess == RouteAccess.CONFIRMED_ON_ROUTE
        } else candidate.canRequestNavigation
        if (!allowed) {
            setStop(StopRequestPhase.FAILED, candidate, problem = StopRequestProblem.INVALID_CANDIDATE)
            return
        }
        if (!usableJourney()) {
            setStop(StopRequestPhase.FAILED, candidate, problem = StopRequestProblem.ROUTE_UNAVAILABLE)
            return
        }
        checkingConfirmation = null
        submittedAt = null
        setStop(StopRequestPhase.CHECKING, candidate)
        query(force = true) // Query slots again before offering a confirmation; never silently replace one.
    }

    @Synchronized
    override fun confirmStop(confirmationId: String) {
        val state = stopRequest.value
        val confirmation = state.confirmation ?: return
        if (state.phase != StopRequestPhase.AWAITING_CONFIRMATION || confirmation.id != confirmationId) return
        if (!usableJourney() || confirmation.journeyId != journeyId || confirmationRevision != routeRevision ||
            expired(confirmation.createdAtEpochMillis, clock(), CONFIRMATION_TTL_MILLIS)
        ) {
            abortStop(StopRequestProblem.ROUTE_CHANGED)
            return
        }
        checkingConfirmation = confirmation
        setStop(StopRequestPhase.CHECKING, confirmation.candidate, confirmation)
        query(force = true) // Re-read destination and slots after the user's explicit confirmation.
    }

    @Synchronized
    override fun cancelStop() {
        // A submitted IPC request cannot be undone by hiding a dialog. Keep its outcome visible.
        if (stopRequest.value.phase == StopRequestPhase.SUBMITTING ||
            stopRequest.value.phase == StopRequestPhase.OUTCOME_UNKNOWN) return
        checkingConfirmation = null
        setStop(StopRequestPhase.IDLE)
    }

    private fun query(force: Boolean = false) {
        if (!started || connection.value != DataStatus.READY) return
        if (requestedAt != null && !force) return
        requestedAt = clock()
        awaitingState = true
        awaitingDestination = false
        awaitingWaypoints = false
        callClient { requestRouteState() }
    }

    private fun routeState(active: Boolean) {
        // Like destination updates, mode changes may be pushed without a matching request.
        // Repeated active-mode notifications need no extra IPC, but loss must clear ETA at once.
        if (!awaitingState && active && activeRoute) return
        awaitingState = false
        if (!active) {
            clearRoute()
            return
        }
        activeRoute = true
        if (requestedAt == null) requestedAt = clock()
        if (snapshots.value.readyValue == null) mutableSnapshots.value = unavailable(DataStatus.LOADING)
        awaitingDestination = true
        awaitingWaypoints = true
        callClient { requestDestination(); requestWaypoints() }
    }

    private fun destination(reading: NavigationDestination) {
        // The SDK also pushes traffic/ETA changes without an explicit getDestinationInfo request.
        // Keep the active-route barrier, but do not discard those updates between polls.
        if (!activeRoute || awaitingState) return
        awaitingDestination = false
        try {
            if (destinationKey != reading.key) {
                destinationKey = reading.key
                journeyId = newId()
                routeRevision++
                abortStop(StopRequestProblem.ROUTE_CHANGED)
            }
            val now = clock()
            val journey = reading.toJourney(journeyId ?: newId().also { journeyId = it }, now)
            mutableSnapshots.value = DataSnapshot(origin, DataStatus.READY, journey, now)
        } catch (_: IllegalArgumentException) {
            mutableSnapshots.value = unavailable(DataStatus.FAILED)
            abortStop(StopRequestProblem.ROUTE_UNAVAILABLE)
        } catch (_: ArithmeticException) {
            mutableSnapshots.value = unavailable(DataStatus.FAILED)
            abortStop(StopRequestProblem.ROUTE_UNAVAILABLE)
        }
        finishRead()
    }

    private fun waypointUpdate(values: List<NavigationWaypoint>) {
        if (!activeRoute || awaitingState) return
        val now = clock()
        val previous = waypoints.value.readyValue
        // The current emulator service can return a placeholder waypoint at (0, 0). It cannot
        // establish valid/available slots. Preserve ETA, but never discard it as an empty slot or
        // submit a write that could overwrite an unknown waypoint. Explicit demo fixtures may use 0.
        val placeholder = origin == DataOrigin.NAVI_SDK && values.any {
            it.location.latitudeDegrees == 0.0 && it.location.longitudeDegrees == 0.0
        }
        if (values.size > 2 || now < 0 || placeholder) {
            mutableWaypoints.value = unavailable(DataStatus.FAILED)
            awaitingWaypoints = false
            abortStop(StopRequestProblem.REQUEST_FAILED)
            finishRead()
            return
        }
        mutableWaypoints.value = DataSnapshot(origin, DataStatus.READY, values.toList(), now)
        awaitingWaypoints = false
        if (previous != null && previous != values && stopRequest.value.phase == StopRequestPhase.AWAITING_CONFIRMATION) {
            abortStop(StopRequestProblem.ROUTE_CHANGED)
        }
        resolveSubmission(values)
        finishRead()
    }

    private fun finishRead() {
        if (awaitingState || awaitingDestination || awaitingWaypoints) return
        requestedAt = null
        if (stopRequest.value.phase != StopRequestPhase.CHECKING) return
        val candidate = stopRequest.value.candidate ?: return
        val journey = snapshots.value.readyValue
        val slots = waypoints.value.readyValue
        if (!usableJourney() || journey == null || slots == null) {
            abortStop(StopRequestProblem.ROUTE_UNAVAILABLE)
            return
        }
        val confirmed = checkingConfirmation
        if (confirmed != null) {
            checkingConfirmation = null
            if (confirmed.journeyId != journey.id || confirmationRevision != routeRevision ||
                confirmed.existingWaypoints != slots ||
                expired(confirmed.createdAtEpochMillis, clock(), CONFIRMATION_TTL_MILLIS)
            ) {
                abortStop(StopRequestProblem.ROUTE_CHANGED)
                return
            }
            submittedAt = clock()
            setStop(StopRequestPhase.SUBMITTING, candidate, confirmed)
            callClient { addWaypoint(candidate, confirmed.slot) }
            // Returning from addWaypoint is not a successful registration. Read back the resulting list.
            if (stopRequest.value.phase == StopRequestPhase.SUBMITTING) callClient { requestWaypoints() }
            return
        }
        if (slots.any { matches(it, candidate) }) {
            setStop(StopRequestPhase.CONFIRMED, candidate) // Already present: no duplicate IPC write.
            return
        }
        if (slots.size >= 2) {
            abortStop(StopRequestProblem.WAYPOINTS_FULL)
            return
        }
        val confirmation = StopConfirmation(newId(), candidate, journey.id, slots.size + 1, slots.toList(), clock())
        confirmationRevision = routeRevision
        setStop(StopRequestPhase.AWAITING_CONFIRMATION, candidate, confirmation)
    }

    private fun resolveSubmission(values: List<NavigationWaypoint>) {
        val state = stopRequest.value
        if (state.phase != StopRequestPhase.SUBMITTING && state.phase != StopRequestPhase.OUTCOME_UNKNOWN) return
        val confirmation = state.confirmation ?: return
        if (values.size == confirmation.slot && values.take(confirmation.slot - 1) == confirmation.existingWaypoints &&
            matches(values.last(), confirmation.candidate)
        ) {
            submittedAt = null
            setStop(StopRequestPhase.CONFIRMED, confirmation.candidate, confirmation)
        }
    }

    private fun invalidateRoute() {
        activeRoute = false
        routeRevision++
        mutableSnapshots.value = unavailable(DataStatus.LOADING)
        mutableWaypoints.value = unavailable(DataStatus.LOADING)
        abortStop(StopRequestProblem.ROUTE_CHANGED)
    }

    private fun clearRoute() {
        activeRoute = false
        destinationKey = null
        journeyId = null
        routeRevision++
        requestedAt = null
        awaitingState = false
        awaitingDestination = false
        awaitingWaypoints = false
        mutableSnapshots.value = unavailable(DataStatus.EMPTY)
        mutableWaypoints.value = unavailable(DataStatus.EMPTY)
        abortStop(StopRequestProblem.ROUTE_UNAVAILABLE)
    }

    private fun fail(status: DataStatus) {
        val failure = status.takeIf { it == DataStatus.PERMISSION_DENIED || it == DataStatus.UNSUPPORTED } ?: DataStatus.FAILED
        mutableConnection.value = failure
        activeRoute = false
        requestedAt = null
        awaitingState = false
        awaitingDestination = false
        awaitingWaypoints = false
        mutableSnapshots.value = unavailable(failure)
        mutableWaypoints.value = unavailable(failure)
        abortStop(if (failure == DataStatus.PERMISSION_DENIED) StopRequestProblem.PERMISSION_DENIED else StopRequestProblem.REQUEST_FAILED)
        generation++
        val old = client
        client = null
        try { old?.release() } catch (_: Exception) { /* Do not replace the original failure. */ }
    }

    private fun callClient(action: NavigationClient.() -> Unit) {
        val current = client ?: return
        try {
            current.action()
        } catch (_: SecurityException) {
            fail(DataStatus.PERMISSION_DENIED)
        } catch (_: Exception) {
            fail(DataStatus.FAILED)
        } catch (_: LinkageError) {
            fail(DataStatus.UNSUPPORTED)
        }
    }

    private fun abortStop(problem: StopRequestProblem) {
        val state = stopRequest.value
        when (state.phase) {
            StopRequestPhase.SUBMITTING, StopRequestPhase.OUTCOME_UNKNOWN ->
                setStop(StopRequestPhase.OUTCOME_UNKNOWN, state.candidate, state.confirmation, problem)
            StopRequestPhase.CHECKING, StopRequestPhase.AWAITING_CONFIRMATION ->
                setStop(StopRequestPhase.FAILED, state.candidate, state.confirmation, problem)
            else -> Unit
        }
        checkingConfirmation = null
    }

    private fun setStop(phase: StopRequestPhase, candidate: StopCandidate? = null, confirmation: StopConfirmation? = null, problem: StopRequestProblem? = null) {
        mutableStop.value = StopRequestState(phase, candidate, confirmation, problem, origin == DataOrigin.DEMO)
    }

    private fun usableJourney(): Boolean = connection.value == DataStatus.READY && activeRoute &&
        snapshots.value.readyValue != null && snapshots.value.observedAtEpochMillis?.let {
            !expired(it, clock(), FRESHNESS_MILLIS)
        } == true

    private fun expireSnapshots(now: Long) {
        if (snapshots.value.status == DataStatus.READY && expired(snapshots.value.observedAtEpochMillis!!, now, FRESHNESS_MILLIS)) {
            mutableSnapshots.value = staleOrFailed(snapshots.value, DataStatus.STALE)
            abortStop(StopRequestProblem.ROUTE_CHANGED)
        }
        if (waypoints.value.status == DataStatus.READY && expired(waypoints.value.observedAtEpochMillis!!, now, FRESHNESS_MILLIS)) {
            mutableWaypoints.value = staleOrFailed(waypoints.value, DataStatus.STALE)
        }
    }

    private fun <T> unavailable(status: DataStatus) = DataSnapshot<T>(origin, status)
    private fun <T> staleOrFailed(previous: DataSnapshot<T>, fallback: DataStatus): DataSnapshot<T> =
        if (previous.value != null) previous.copy(status = DataStatus.STALE) else unavailable(fallback)

    private fun matches(waypoint: NavigationWaypoint, candidate: StopCandidate) =
        waypoint.location == candidate.facility.location

    private fun expired(since: Long, now: Long, ttl: Long) = now < since || now - since >= ttl

    companion object {
        const val FRESHNESS_MILLIS = 60_000L
        const val REQUEST_TIMEOUT_MILLIS = 10_000L
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
        const val CONFIRMATION_TTL_MILLIS = 30_000L
    }
}
