package com.hmgchallenge.shared.journey

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.CareFacility
import com.hmgchallenge.shared.facility.FacilityType
import com.hmgchallenge.shared.facility.RouteAccess
import com.hmgchallenge.shared.facility.StopCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationRepositoryTest {
    private var now = 1_000_000L
    private var sequence = 0
    private val destination = NavigationDestination("destination", "목적지", GeoPoint(37.5, 127.0), 90, 1_234)
    private val existing = NavigationWaypoint("기존 경유지", GeoPoint(37.4, 127.0))
    private val candidate = StopCandidate(
        CareFacility("stop", "돌봄 시설", GeoPoint(37.45, 127.01), FacilityType.REST_AREA),
        DataOrigin.PUBLIC_DATA, RouteAccess.CONFIRMED_ON_ROUTE,
    )

    private inner class Client : NavigationClient {
        lateinit var listener: NavigationClient.Listener
        var autoConnect = true
        var autoRead = true
        var active = true
        var reading = destination
        var slots = emptyList<NavigationWaypoint>()
        var reads = 0
        var releases = 0
        var permissionFailure = false
        val writes = mutableListOf<Pair<StopCandidate, Int>>()
        override fun connect(listener: NavigationClient.Listener) {
            this.listener = listener
            if (autoConnect) listener.onConnected()
        }
        override fun requestRouteState() {
            reads++
            if (permissionFailure) throw SecurityException("must never reach UI")
            if (autoRead) listener.onRouteState(active)
        }
        override fun requestDestination() { if (autoRead) listener.onDestination(reading) }
        override fun requestWaypoints() { if (autoRead) listener.onWaypoints(slots) }
        override fun addWaypoint(candidate: StopCandidate, slot: Int) { writes += candidate to slot }
        override fun release() { releases++ }
    }

    private fun source(client: Client, origin: DataOrigin = DataOrigin.NAVI_SDK) = NavigationRepository(
        { client }, origin, { now }, { "id-${sequence++}" },
    )

    private fun prepared(repo: NavigationRepository): StopConfirmation {
        repo.prepareStop(candidate)
        assertEquals(StopRequestPhase.AWAITING_CONFIRMATION, repo.stopRequest.value.phase)
        return assertNotNull(repo.stopRequest.value.confirmation)
    }

    @Test
    fun secondsAreDurationsAndDistancesStayInMeters() {
        val journey = destination.toJourney("route", now)
        assertEquals(now + 90_000, journey.estimatedArrivalAtEpochMillis)
        assertEquals(1_234, journey.remainingDistanceMeters)
        assertEquals(GeoPoint(37.5, 127.0), journey.destination)
        assertNull(destination.copy(remainingDurationSeconds = null).toJourney("r", now).estimatedArrivalAtEpochMillis)
    }

    @Test
    fun zeroRemainingTimeIsValidOnlyWhenAnActiveDestinationWasReported() {
        val client = Client().apply { reading = destination.copy(remainingDurationSeconds = 0, remainingDistanceMeters = 0) }
        val repo = source(client)
        repo.start()
        assertEquals(now, repo.snapshots.value.readyValue?.estimatedArrivalAtEpochMillis)
        client.active = false
        repo.refresh()
        assertEquals(DataStatus.EMPTY, repo.snapshots.value.status)
        assertNull(repo.snapshots.value.value)
    }

    @Test
    fun negativeAndOverflowDurationsAreNeverWrappedIntoUsableEta() {
        assertFailsWith<IllegalArgumentException> { destination.copy(remainingDurationSeconds = -1).toJourney("r", now) }
        assertFailsWith<ArithmeticException> { destination.copy(remainingDurationSeconds = Long.MAX_VALUE).toJourney("r", now) }
        val client = Client().apply { reading = destination.copy(remainingDurationSeconds = Long.MAX_VALUE) }
        val repo = source(client)
        repo.start()
        assertEquals(DataStatus.FAILED, repo.snapshots.value.status)
    }

    @Test
    fun initializationMustCompleteBeforeAnyReadAndStartIsIdempotent() {
        val client = Client().apply { autoConnect = false }
        val repo = source(client)
        repo.start()
        repo.start()
        assertEquals(0, client.reads)
        assertEquals(DataStatus.LOADING, repo.snapshots.value.status)
        client.listener.onConnected()
        assertEquals(1, client.reads)
        assertEquals(DataStatus.READY, repo.snapshots.value.status)
    }

    @Test
    fun etaRefreshKeepsJourneyIdentityAndUsesNewObservationTime() {
        val client = Client()
        val repo = source(client)
        repo.start()
        val first = assertNotNull(repo.snapshots.value.readyValue)
        now += 5_000
        client.reading = destination.copy(remainingDurationSeconds = 600)
        repo.refresh()
        assertEquals(first.id, repo.snapshots.value.readyValue?.id)
        assertEquals(now + 600_000, repo.snapshots.value.readyValue?.estimatedArrivalAtEpochMillis)
        assertEquals(now, repo.snapshots.value.observedAtEpochMillis)
    }

    @Test
    fun pushedTrafficUpdateDoesNotWaitForNextPoll() {
        val client = Client()
        val repo = source(client)
        repo.start()
        val id = repo.snapshots.value.readyValue?.id
        val readCount = client.reads
        now += 5_000
        client.listener.onDestination(destination.copy(remainingDurationSeconds = 900))
        assertEquals(readCount, client.reads)
        assertEquals(id, repo.snapshots.value.readyValue?.id)
        assertEquals(now + 900_000, repo.snapshots.value.readyValue?.estimatedArrivalAtEpochMillis)
    }

    @Test
    fun nativeSimulationFlagSurvivesNormalization() {
        val client = Client().apply { reading = destination.copy(isSimulation = true) }
        val repo = source(client)
        repo.start()
        assertEquals(DataOrigin.NAVI_SDK, repo.snapshots.value.origin)
        assertEquals(true, repo.snapshots.value.readyValue?.isSimulation)
    }

    @Test
    fun destinationChangeImmediatelyInvalidatesPreviousEta() {
        val client = Client()
        val repo = source(client)
        repo.start()
        val originalId = repo.snapshots.value.readyValue?.id
        client.autoRead = false
        client.listener.onRouteInvalidated()
        assertEquals(DataStatus.LOADING, repo.snapshots.value.status)
        assertNull(repo.snapshots.value.value)
        client.listener.onDestination(destination) // Older response before the new route-state barrier.
        assertNull(repo.snapshots.value.value)
        client.listener.onRouteState(true)
        client.listener.onDestination(destination.copy(key = "new", label = "새 목적지"))
        client.listener.onWaypoints(emptyList())
        assertNotEquals(originalId, repo.snapshots.value.readyValue?.id)
    }

    @Test
    fun cancelledRouteDoesNotAcceptLateDestinationResponses() {
        val client = Client()
        val repo = source(client)
        repo.start()
        client.listener.onRouteCleared()
        client.listener.onDestination(destination)
        assertEquals(DataStatus.EMPTY, repo.snapshots.value.status)
        assertNull(repo.snapshots.value.readyValue)
    }

    @Test
    fun pushedModeLossAndRecoveryDoNotWaitForAPoll() {
        val client = Client()
        val repo = source(client)
        repo.start()
        client.listener.onRouteState(false)
        assertEquals(DataStatus.EMPTY, repo.snapshots.value.status)
        client.listener.onDestination(destination)
        assertNull(repo.snapshots.value.readyValue)
        client.listener.onRouteState(true)
        assertEquals(DataStatus.READY, repo.snapshots.value.status)
    }

    @Test
    fun initialConnectionTimeoutReleasesAndIgnoresLateConnect() {
        val client = Client().apply { autoConnect = false }
        val repo = source(client)
        repo.start()
        now += NavigationRepository.CONNECT_TIMEOUT_MILLIS
        repo.tick()
        client.listener.onConnected()
        assertEquals(DataStatus.FAILED, repo.connection.value)
        assertEquals(0, client.reads)
        assertEquals(1, client.releases)
    }

    @Test
    fun permissionDenialIsDistinctAndDoesNotRetryOnTicks() {
        val client = Client().apply { permissionFailure = true }
        val repo = source(client)
        repo.start()
        assertEquals(DataStatus.PERMISSION_DENIED, repo.snapshots.value.status)
        now += 60_000
        repo.tick()
        assertEquals(1, client.reads)
        assertNull(repo.snapshots.value.value)
    }

    @Test
    fun missingReadCallbackTimesOutAndCachedEtaBecomesUnusable() {
        val client = Client()
        val repo = source(client)
        repo.start()
        client.autoRead = false
        repo.refresh()
        now += NavigationRepository.REQUEST_TIMEOUT_MILLIS
        repo.tick()
        assertEquals(DataStatus.STALE, repo.snapshots.value.status)
        assertNotNull(repo.snapshots.value.value)
        assertNull(repo.snapshots.value.readyValue)
    }

    @Test
    fun freshnessAndClockReversalBothInvalidateReadyValues() {
        for (time in listOf(now + NavigationRepository.FRESHNESS_MILLIS, now - 1)) {
            now = 1_000_000
            val repo = source(Client())
            repo.start()
            now = time
            repo.tick()
            assertEquals(DataStatus.STALE, repo.snapshots.value.status)
        }
    }

    @Test
    fun stoppedAndReplacedSessionsIgnoreOldBinderCallbacks() {
        val first = Client()
        val second = Client()
        var current = first
        val repo = NavigationRepository({ current }, clock = { now })
        repo.start()
        repo.stop()
        assertEquals(DataStatus.STALE, repo.snapshots.value.status)
        current = second
        repo.start()
        val next = repo.snapshots.value
        first.listener.onRouteCleared()
        first.listener.onFailure(DataStatus.FAILED)
        assertEquals(next, repo.snapshots.value)
        assertEquals(1, first.releases)
    }

    @Test
    fun demoAndUnconfirmedCandidatesNeverReachRealClient() {
        val client = Client()
        val repo = source(client)
        repo.start()
        for (invalid in listOf(candidate.copy(origin = DataOrigin.DEMO), candidate.copy(routeAccess = RouteAccess.UNKNOWN))) {
            repo.prepareStop(invalid)
            assertEquals(StopRequestProblem.INVALID_CANDIDATE, repo.stopRequest.value.problem)
        }
        assertTrue(client.writes.isEmpty())
    }

    @Test
    fun twoExistingWaypointsAreNeverReplaced() {
        val client = Client().apply { slots = listOf(existing, existing.copy(label = "두 번째", location = GeoPoint(37.3, 127.0))) }
        val repo = source(client)
        repo.start()
        repo.prepareStop(candidate)
        assertEquals(StopRequestProblem.WAYPOINTS_FULL, repo.stopRequest.value.problem)
        assertTrue(client.writes.isEmpty())
    }

    @Test
    fun placeholderWaypointBlocksWritesWithoutDiscardingEta() {
        val client = Client().apply { slots = listOf(NavigationWaypoint("경유지", GeoPoint(0.0, 0.0))) }
        val repo = source(client)
        repo.start()
        assertEquals(DataStatus.READY, repo.snapshots.value.status)
        assertEquals(DataStatus.FAILED, repo.waypoints.value.status)
        repo.prepareStop(candidate)
        assertEquals(StopRequestProblem.REQUEST_FAILED, repo.stopRequest.value.problem)
        assertTrue(client.writes.isEmpty())
        assertEquals(DataStatus.READY, repo.snapshots.value.status)
    }

    @Test
    fun selectingAndCancellingStopDoesNotModifyNativeRoute() {
        val client = Client()
        val repo = source(client)
        repo.start()
        prepared(repo)
        repo.cancelStop()
        assertEquals(StopRequestPhase.IDLE, repo.stopRequest.value.phase)
        assertTrue(client.writes.isEmpty())
    }

    @Test
    fun explicitConfirmationDoesNotReportSuccessUntilReadback() {
        val client = Client()
        val repo = source(client)
        repo.start()
        val confirmation = prepared(repo)
        repo.confirmStop(confirmation.id)
        repo.confirmStop(confirmation.id)
        assertEquals(listOf(candidate to 1), client.writes)
        assertEquals(StopRequestPhase.SUBMITTING, repo.stopRequest.value.phase)
        client.listener.onWaypoints(listOf(NavigationWaypoint(candidate.facility.name, candidate.facility.location)))
        assertEquals(StopRequestPhase.CONFIRMED, repo.stopRequest.value.phase)
    }

    @Test
    fun secondSlotPreservesFirstAndSuccessRequiresBoth() {
        val client = Client().apply { slots = listOf(existing) }
        val repo = source(client)
        repo.start()
        repo.confirmStop(prepared(repo).id)
        assertEquals(listOf(candidate to 2), client.writes)
        val added = NavigationWaypoint(candidate.facility.name, candidate.facility.location)
        client.listener.onWaypoints(listOf(added))
        assertEquals(StopRequestPhase.SUBMITTING, repo.stopRequest.value.phase)
        client.listener.onWaypoints(listOf(existing, added))
        assertEquals(StopRequestPhase.CONFIRMED, repo.stopRequest.value.phase)
    }

    @Test
    fun slotChangeDuringConfirmationReadbackCancelsWrite() {
        val client = Client()
        val repo = source(client)
        repo.start()
        val confirmation = prepared(repo)
        client.slots = listOf(existing)
        repo.confirmStop(confirmation.id)
        assertEquals(StopRequestProblem.ROUTE_CHANGED, repo.stopRequest.value.problem)
        assertTrue(client.writes.isEmpty())
    }

    @Test
    fun destinationChangeAfterConfirmationWasShownCancelsWrite() {
        val client = Client()
        val repo = source(client)
        repo.start()
        val confirmation = prepared(repo)
        client.reading = destination.copy(key = "other-destination")
        repo.confirmStop(confirmation.id)
        assertEquals(StopRequestProblem.ROUTE_CHANGED, repo.stopRequest.value.problem)
        assertTrue(client.writes.isEmpty())
    }

    @Test
    fun expiredOrWrongConfirmationCannotSendRequest() {
        val client = Client()
        val repo = source(client)
        repo.start()
        val confirmation = prepared(repo)
        repo.confirmStop("another-token")
        assertTrue(client.writes.isEmpty())
        now += NavigationRepository.CONFIRMATION_TTL_MILLIS
        repo.confirmStop(confirmation.id)
        assertEquals(StopRequestPhase.FAILED, repo.stopRequest.value.phase)
        assertTrue(client.writes.isEmpty())
    }

    @Test
    fun ambiguousTimeoutIsNotRetriedAndLateReadbackCanResolveIt() {
        val client = Client()
        val repo = source(client)
        repo.start()
        repo.confirmStop(prepared(repo).id)
        now += NavigationRepository.REQUEST_TIMEOUT_MILLIS
        repo.tick()
        assertEquals(StopRequestPhase.OUTCOME_UNKNOWN, repo.stopRequest.value.phase)
        repo.cancelStop()
        repo.prepareStop(candidate)
        assertEquals(1, client.writes.size)
        assertEquals(StopRequestPhase.OUTCOME_UNKNOWN, repo.stopRequest.value.phase)
        client.listener.onWaypoints(listOf(NavigationWaypoint(candidate.facility.name, candidate.facility.location)))
        assertEquals(StopRequestPhase.CONFIRMED, repo.stopRequest.value.phase)
    }

    @Test
    fun alreadyRegisteredStopDoesNotCreateDuplicate() {
        val client = Client().apply { slots = listOf(NavigationWaypoint(candidate.facility.name, candidate.facility.location)) }
        val repo = source(client)
        repo.start()
        repo.prepareStop(candidate)
        assertEquals(StopRequestPhase.CONFIRMED, repo.stopRequest.value.phase)
        assertTrue(client.writes.isEmpty())
    }

    @Test
    fun writePermissionFailureKeepsOutcomeUncertainRatherThanInventingSuccess() {
        val base = Client()
        val client = object : NavigationClient by base {
            override fun addWaypoint(candidate: StopCandidate, slot: Int) { throw SecurityException("private") }
        }
        val repo = NavigationRepository({ client }, clock = { now })
        repo.start()
        repo.confirmStop(prepared(repo).id)
        assertEquals(DataStatus.PERMISSION_DENIED, repo.snapshots.value.status)
        assertEquals(StopRequestPhase.OUTCOME_UNKNOWN, repo.stopRequest.value.phase)
        assertEquals(StopRequestProblem.PERMISSION_DENIED, repo.stopRequest.value.problem)
    }

    @Test
    fun demoClientUsesSameContractAndCannotConsumeRealCandidate() {
        val client = DemoNavigationClient(destination)
        val repo = NavigationRepository({ client }, DataOrigin.DEMO, { now })
        repo.start()
        assertEquals(DataOrigin.DEMO, repo.snapshots.value.origin)
        repo.prepareStop(candidate)
        assertEquals(StopRequestProblem.INVALID_CANDIDATE, repo.stopRequest.value.problem)
        repo.prepareStop(candidate.copy(origin = DataOrigin.DEMO))
        repo.confirmStop(assertNotNull(repo.stopRequest.value.confirmation).id)
        assertEquals(StopRequestPhase.CONFIRMED, repo.stopRequest.value.phase)
        assertTrue(repo.stopRequest.value.isDemo)
        client.publish(null)
        assertEquals(DataStatus.EMPTY, repo.snapshots.value.status)
    }
}
