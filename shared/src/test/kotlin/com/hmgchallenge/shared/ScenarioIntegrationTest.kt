package com.hmgchallenge.shared

import com.hmgchallenge.shared.care.BabyProfile
import com.hmgchallenge.shared.care.CareEvent
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareJournal
import com.hmgchallenge.shared.care.CarePreferences
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.departure.DepartureChoice
import com.hmgchallenge.shared.departure.DeparturePlanner
import com.hmgchallenge.shared.departure.DepartureRecord
import com.hmgchallenge.shared.departure.PackingScope
import com.hmgchallenge.shared.departure.PackingSettings
import com.hmgchallenge.shared.departure.SupplyKind
import com.hmgchallenge.shared.facility.AmenityStatus
import com.hmgchallenge.shared.facility.CareFacility
import com.hmgchallenge.shared.facility.FacilityCatalog
import com.hmgchallenge.shared.facility.FacilityCatalogClient
import com.hmgchallenge.shared.facility.FacilityCatalogStore
import com.hmgchallenge.shared.facility.FacilityDataSource
import com.hmgchallenge.shared.facility.FacilityListing
import com.hmgchallenge.shared.facility.FacilityProblem
import com.hmgchallenge.shared.facility.FacilityRepository
import com.hmgchallenge.shared.facility.FacilityType
import com.hmgchallenge.shared.facility.LastRestAreaAlerts
import com.hmgchallenge.shared.facility.LastRestAreaStatus
import com.hmgchallenge.shared.facility.RouteAccess
import com.hmgchallenge.shared.facility.RouteAccessCatalog
import com.hmgchallenge.shared.facility.RouteAccessCatalogClient
import com.hmgchallenge.shared.facility.RouteAccessCatalogStore
import com.hmgchallenge.shared.facility.RouteAccessRepository
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.facility.StopPlanStatus
import com.hmgchallenge.shared.facility.StopPlanType
import com.hmgchallenge.shared.facility.StopPlanner
import com.hmgchallenge.shared.facility.StopPlanningProblem
import com.hmgchallenge.shared.facility.VerifiedCorridorApproach
import com.hmgchallenge.shared.facility.VerifiedRouteCorridor
import com.hmgchallenge.shared.forecast.CareNeed
import com.hmgchallenge.shared.forecast.ForecastEngine
import com.hmgchallenge.shared.forecast.ForecastInput
import com.hmgchallenge.shared.forecast.ForecastReasonCode
import com.hmgchallenge.shared.forecast.ForecastStage
import com.hmgchallenge.shared.forecast.SuggestedAction
import com.hmgchallenge.shared.journey.DriveSession
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot
import com.hmgchallenge.shared.journey.NavigationClient
import com.hmgchallenge.shared.journey.NavigationDestination
import com.hmgchallenge.shared.journey.NavigationRepository
import com.hmgchallenge.shared.journey.NavigationWaypoint
import com.hmgchallenge.shared.journey.StopRequestPhase
import com.hmgchallenge.shared.response.CryResponseCoordinator
import com.hmgchallenge.shared.response.ResponseAssessor
import com.hmgchallenge.shared.response.ResponseContext
import com.hmgchallenge.shared.response.ResponseNotice
import com.hmgchallenge.shared.response.ResponsePhase
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixed-clock, fixed-journey, fixed-care-record integration tests reproducing core MVP Scenarios A, B, C
 * and offline/unverified edge states across ForecastEngine, DeparturePlanner, RouteAccessRepository,
 * StopPlanner, CryResponseCoordinator, and NavigationRepository.
 */
class ScenarioIntegrationTest {
    private val baseClock = 1_794_450_000_000L
    private val minuteMs = 60_000L
    private val sourceMeta = FacilityDataSource(
        "data-go-kr-15025446",
        "한국도로공사",
        "전국휴게소정보표준데이터",
        "https://www.data.go.kr/data/15025446/standard.do",
        "이용허락범위 제한 없음",
        "반기",
    )

    private fun restArea(id: String, name: String, lat: Double, lon: Double, dir: String) = FacilityListing(
        CareFacility(id, name, GeoPoint(lat, lon), FacilityType.REST_AREA, nursingRoom = AmenityStatus.AVAILABLE, verifiedAtEpochMillis = baseClock - 86_400_000L),
        sourceMeta.id,
        dir,
    )

    private fun shelter(id: String, name: String, lat: Double, lon: Double, dir: String) = FacilityListing(
        CareFacility(id, name, GeoPoint(lat, lon), FacilityType.DROWSINESS_SHELTER, nursingRoom = AmenityStatus.UNKNOWN, verifiedAtEpochMillis = baseClock - 86_400_000L),
        sourceMeta.id,
        dir,
    )

    private fun gyeongbuCatalog(at: Long): DataSnapshot<FacilityCatalog> = DataSnapshot(
        DataOrigin.PUBLIC_DATA,
        DataStatus.READY,
        FacilityCatalog(
            DataOrigin.PUBLIC_DATA,
            at - 600_000L,
            listOf(sourceMeta),
            listOf(
                restArea("public-4ce7e3532f4f371c529563e3", "죽전(서울)", 37.331, 127.104, "상행"),
                restArea("public-a9c9db4f76ef7bec2486d011", "기흥(부산)", 37.235, 127.104, "하행"),
                shelter("public-6b47faeb0db871b24f624671", "오산", 37.156, 127.087, "서울기점 + 부산종점"),
                shelter("public-45ef5cddee2d2fbea16bf6de", "오산", 37.155, 127.088, "부산기점 + 서울종점"),
                shelter("public-0cae9d7b0e3a1032f39a5270", "남사", 37.096, 127.120, "서울기점 + 부산종점"),
                restArea("public-c7b5fbf04b0ab94ff0ae3ca1", "안성(부산)", 37.013, 127.144, "하행"),
                restArea("public-67bcbddc62c7c0b9a99d498f", "안성(서울)", 37.022, 127.143, "상행"),
                restArea("public-e3b13fd2544560bf9f2eeb32", "망향(부산)", 36.855, 127.180, "하행"),
                restArea("public-7f6ef4dc555d4a50dba1e0c2", "천안호두(부산)", 36.730, 127.263, "하행"),
            ),
        ),
        at,
    )

    private fun gyeongbuRouteAccess(at: Long): DataSnapshot<RouteAccessCatalog> = DataSnapshot(
        DataOrigin.NAVI_SDK,
        DataStatus.READY,
        RouteAccessCatalog(
            DataOrigin.NAVI_SDK,
            at - 600_000L,
            listOf(
                VerifiedRouteCorridor(
                    id = "gyeongbu-busan-cheonan",
                    destinationLabel = "천안분기점",
                    destinationLocation = GeoPoint(36.72, 127.27),
                    destinationPoiId = "gyeongbu-cheonan-jc",
                    totalDistanceMeters = 80_000L,
                    totalDurationSeconds = 3_600L,
                    coversRemainingRestAreas = true,
                    approaches = listOf(
                        VerifiedCorridorApproach("public-4ce7e3532f4f371c529563e3", RouteAccess.OPPOSITE_DIRECTION, 68_000L, 3_060L),
                        VerifiedCorridorApproach("public-a9c9db4f76ef7bec2486d011", RouteAccess.CONFIRMED_ON_ROUTE, 58_000L, 2_610L, GeoPoint(37.23541, 127.10421), "36khp2hdj2cfcm5nqhe3"),
                        VerifiedCorridorApproach("public-6b47faeb0db871b24f624671", RouteAccess.CONFIRMED_ON_ROUTE, 49_000L, 2_200L, GeoPoint(37.15691, 127.08702), "36khp2hdj2cfcm5nqhe3"),
                        VerifiedCorridorApproach("public-45ef5cddee2d2fbea16bf6de", RouteAccess.OPPOSITE_DIRECTION, 48_800L, 2_190L),
                        VerifiedCorridorApproach("public-0cae9d7b0e3a1032f39a5270", RouteAccess.CONFIRMED_ON_ROUTE, 42_000L, 1_890L, GeoPoint(37.09635, 127.12058), "36khp2hdj2cfcm5nqhe3"),
                        VerifiedCorridorApproach("public-67bcbddc62c7c0b9a99d498f", RouteAccess.OPPOSITE_DIRECTION, 39_500L, 1_780L),
                        VerifiedCorridorApproach("public-c7b5fbf04b0ab94ff0ae3ca1", RouteAccess.CONFIRMED_ON_ROUTE, 32_000L, 1_440L, GeoPoint(37.01362, 127.14455), "36khp2hdj2cfcm5nqhe3"),
                        VerifiedCorridorApproach("public-e3b13fd2544560bf9f2eeb32", RouteAccess.CONFIRMED_ON_ROUTE, 15_000L, 675L, GeoPoint(36.85582, 127.18071), "36khp2hdj2cfcm5nqhe3"),
                        VerifiedCorridorApproach("public-7f6ef4dc555d4a50dba1e0c2", RouteAccess.CONFIRMED_ON_ROUTE, 2_500L, 120L, GeoPoint(36.73041, 127.26365), "36khp2hdj2cfcm5nqhe3"),
                    ),
                ),
            ),
        ),
        at,
    )

    private class FakeNavClient(var destination: NavigationDestination) : NavigationClient {
        lateinit var listener: NavigationClient.Listener
        var waypoints = listOf<NavigationWaypoint>()
        val addedWaypoints = mutableListOf<Pair<StopCandidate, Int>>()
        var active = true

        override fun connect(listener: NavigationClient.Listener) {
            this.listener = listener
            listener.onConnected()
        }

        override fun requestRouteState() { listener.onRouteState(active) }
        override fun requestDestination() { if (active) listener.onDestination(destination) }
        override fun requestWaypoints() { if (active) listener.onWaypoints(waypoints) }
        override fun addWaypoint(candidate: StopCandidate, slot: Int) {
            addedWaypoints += candidate to slot
            waypoints = waypoints + NavigationWaypoint(candidate.facility.name, candidate.navigationLocation!!, candidate.navigationPoiId)
            listener.onRouteInvalidated()
        }

        override fun release() {}
    }

    @Test
    fun scenarioALongDistanceForecastPackingStopPlanningAndLastRestAreaAlert() {
        var now = baseClock
        val profile = BabyProfile("baby-a", monthAge = 2, preferences = CarePreferences(feedIntervalMinutes = 120, seatedBreakMinutes = 120))
        // Last feed was 70 minutes ago -> 50 minutes left (< 100m trip duration => care before arrival).
        var care = CareRecord(profile, listOf(CareEvent("feed-a1", profile.id, CareEventType.FEED, now - 70 * minuteMs, 120)))
        val preparation = DepartureRecord(
            outingId = "outing-a",
            settings = PackingSettings(
                scope = PackingScope.WHOLE_OUTING,
                extraOutingMinutes = 120,
                feedIntervalMinutes = 120,
                diaperIntervalMinutes = 120,
                spareFeedServings = 1,
                spareDiapers = 2,
            ),
        )
        val journey = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            JourneySnapshot("route-a", "천안분기점", now + 100 * minuteMs, 80_000L, GeoPoint(36.72, 127.27), destinationPoiId = "gyeongbu-cheonan-jc"),
            now,
        )

        val evaluation = DeparturePlanner().evaluate(care, preparation, journey, now)
        assertEquals(220, evaluation.packing.durationMinutes)
        assertEquals(3, evaluation.packing.feedServings) // ceil(220 / 120) + 1 spare
        assertEquals(4, evaluation.packing.diapers)      // ceil(220 / 120) + 2 spare
        val forecast = assertNotNull(evaluation.forecast)
        assertEquals(ForecastStage.CAUTION, forecast.stage)
        assertTrue(CareNeed.FEED in forecast.journey.careBeforeArrival)
        assertTrue(SuggestedAction.PLAN_STOP in forecast.suggestedActions)

        val catalog = gyeongbuCatalog(now)
        val routeAccess = gyeongbuRouteAccess(now)
        val evidence = routeAccess.readyValue!!.evaluate(journey, now)
        val stopPlanner = StopPlanner()
        val stops = stopPlanner.plan(journey, catalog, evidence, now)

        assertEquals(StopPlanningProblem.NONE, stops.problem)
        assertEquals(StopPlanStatus.AVAILABLE, stops.fullCare.status)
        // 죽전(서울)(OPPOSITE_DIRECTION) must never appear; nearest on-route nursing rest area is 기흥(부산).
        assertEquals("public-a9c9db4f76ef7bec2486d011", stops.fullCare.candidates.first().facility.id)
        assertTrue(stops.nearestSafeStop.candidates.none { it.facility.id in setOf("public-4ce7e3532f4f371c529563e3", "public-45ef5cddee2d2fbea16bf6de", "public-67bcbddc62c7c0b9a99d498f") })
        assertEquals(LastRestAreaStatus.AVAILABLE, stops.lastRestArea.status)
        assertEquals("public-7f6ef4dc555d4a50dba1e0c2", stops.lastRestArea.candidate?.facility?.id)

        // Advance along route to 10 km remaining (10 min left): final rest area 천안호두(부산) alert fires once.
        now += 75 * minuteMs
        val approachingJourney = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            JourneySnapshot("route-a", "천안분기점", now + 10 * minuteMs, 10_000L, GeoPoint(36.72, 127.27), destinationPoiId = "gyeongbu-cheonan-jc"),
            now,
        )
        val finalStops = stopPlanner.plan(approachingJourney, gyeongbuCatalog(now), gyeongbuRouteAccess(now).readyValue!!.evaluate(approachingJourney, now), now)
        val alerts = LastRestAreaAlerts(leadTimeSeconds = 600)
        assertEquals("public-7f6ef4dc555d4a50dba1e0c2", alerts.poll(finalStops)?.candidate?.facility?.id)
        assertNull(alerts.poll(finalStops))

        // Care before departure scenario clears care-before-arrival when remaining trip fits inside new horizon.
        care = CareJournal.append(care, CareEvent("feed-a2", profile.id, CareEventType.FEED, now, 130))
        val relaxedEval = DeparturePlanner().evaluate(care, preparation, approachingJourney, now)
        assertTrue(relaxedEval.forecast!!.journey.careBeforeArrival.isEmpty())
        assertEquals(ForecastStage.RELAXED, relaxedEval.forecast.stage)
    }

    @Test
    fun scenarioBSoloDriverShortDriveSingleConfirmationStopAndCooldown() {
        var now = baseClock
        val profile = BabyProfile("baby-b", monthAge = 2, preferences = CarePreferences(feedIntervalMinutes = 120, awakeWindowMinutes = 120, seatedBreakMinutes = 120))
        val care = CareRecord(
            profile,
            listOf(
                CareEvent("feed-b1", profile.id, CareEventType.FEED, now - 110 * minuteMs, 120),
                CareEvent("wake-b1", profile.id, CareEventType.SLEEP_END, now - 90 * minuteMs),
                CareEvent("sleep-b0", profile.id, CareEventType.SLEEP_START, now - 150 * minuteMs),
            ),
        )
        val preparation = DepartureRecord(
            outingId = "outing-b",
            settings = PackingSettings(scope = PackingScope.OUTBOUND, feedIntervalMinutes = 120, diaperIntervalMinutes = 120),
            driveSession = DriveSession("drive-b", profile.id, now - 20 * minuteMs),
        )
        // 25 min remaining at 55km along Gyeongbu southbound corridor -> nearest safe stop is 오산 DROWSINESS_SHELTER (49km rem).
        val shortRoute = NavigationDestination("gyeongbu-cheonan-jc:36.72:127.27", "천안분기점", GeoPoint(36.72, 127.27), 25 * 60L, 55_000L, destinationPoiId = "gyeongbu-cheonan-jc")
        val client = FakeNavClient(shortRoute)
        var routeSeq = 0
        val navRepo = NavigationRepository({ client }, DataOrigin.NAVI_SDK, { now }, { "nav-b-${++routeSeq}" })
        navRepo.start()

        val journey = navRepo.snapshots.value
        assertEquals(DataStatus.READY, journey.status)
        val forecast = DeparturePlanner().evaluate(care, preparation, journey, now).forecast
        assertEquals(ForecastStage.IMMINENT, forecast?.stage)

        val context = ResponseContext(preparation.outingId, profile.id, isDemo = false)
        val coordinator = CryResponseCoordinator { "ep-b-1" }
        val assessment = ResponseAssessor.assess(context, care, forecast, preparation.driveSession, now)
        val stops = StopPlanner().plan(journey, gyeongbuCatalog(now), gyeongbuRouteAccess(now).readyValue!!.evaluate(journey, now), now)

        val suggested = coordinator.update(assessment, stops, now)
        assertEquals(ResponsePhase.SUGGESTED, suggested.phase)
        assertFalse(suggested.active)

        // Single tap confirmation opens response and selects nearest safe stop (오산 shelter, closer than 안성 rest area).
        val activeState = coordinator.confirm(suggested.episodeId!!)
        assertEquals(ResponsePhase.ACTIVE, activeState.phase)
        val selectedState = coordinator.select(StopPlanType.NEAREST_SAFE_STOP, stops, now)
        assertEquals(ResponsePhase.STOP_SELECTED, selectedState.phase)
        val nearest = assertNotNull(selectedState.selected).candidate
        assertEquals("public-6b47faeb0db871b24f624671", nearest.facility.id)
        assertTrue(nearest.canRequestNavigation)

        // Single explicit navigation confirmation adds real waypoint through NavigationRepository.
        navRepo.prepareStop(nearest)
        assertEquals(StopRequestPhase.AWAITING_CONFIRMATION, navRepo.stopRequest.value.phase)
        navRepo.confirmStop(navRepo.stopRequest.value.confirmation!!.id)
        assertEquals(StopRequestPhase.CONFIRMED, navRepo.stopRequest.value.phase)
        assertEquals("36khp2hdj2cfcm5nqhe3", client.addedWaypoints.single().first.navigationPoiId)

        // Solo driver confirms care completion -> IDLE with 20m cooldown blocking re-offer on next ticks.
        val finished = coordinator.finish(assessment, now, ResponseNotice.CARE_CONFIRMED)
        assertEquals(ResponsePhase.IDLE, finished.phase)
        assertEquals(ResponseNotice.CARE_CONFIRMED, finished.notice)
        now += 5 * minuteMs
        client.destination = shortRoute.copy(remainingDurationSeconds = 20 * 60L, remainingDistanceMeters = 45_000L)
        client.listener.onDestination(client.destination)
        val ticked = coordinator.update(
            ResponseAssessor.assess(context, care, DeparturePlanner().evaluate(care, preparation, navRepo.snapshots.value, now).forecast, preparation.driveSession, now),
            StopPlanner().plan(navRepo.snapshots.value, gyeongbuCatalog(now), gyeongbuRouteAccess(now).readyValue!!.evaluate(navRepo.snapshots.value, now), now),
            now,
        )
        assertEquals(ResponsePhase.IDLE, ticked.phase)
        navRepo.stop()
    }

    @Test
    fun scenarioCCongestionDelayTwoStageStopExpirationAndCareReturn() {
        var now = baseClock
        val profile = BabyProfile("baby-c", monthAge = 3, preferences = CarePreferences(feedIntervalMinutes = 120, seatedBreakMinutes = 120))
        // Initially last fed 45m ago -> next feed in 75m. Uncongested ETA = 40m -> ARRIVAL_BEFORE_CARE / RELAXED.
        var care = CareRecord(profile, listOf(CareEvent("feed-c1", profile.id, CareEventType.FEED, now - 45 * minuteMs, 140)))
        val preparation = DepartureRecord(
            outingId = "outing-c",
            settings = PackingSettings(scope = PackingScope.OUTBOUND, feedIntervalMinutes = 120, diaperIntervalMinutes = 120),
            driveSession = DriveSession("drive-c", profile.id, now - 15 * minuteMs),
        )
        val initialJourney = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            JourneySnapshot("route-c", "천안분기점", now + 40 * minuteMs, 55_000L, GeoPoint(36.72, 127.27), destinationPoiId = "gyeongbu-cheonan-jc"),
            now,
        )
        val initialEval = DeparturePlanner().evaluate(care, preparation, initialJourney, now)
        val initialForecast = assertNotNull(initialEval.forecast)
        assertEquals(ForecastStage.RELAXED, initialForecast.stage)
        assertTrue(ForecastReasonCode.ARRIVAL_BEFORE_CARE in initialForecast.reasons)

        // Congestion adds +90 minutes (ETA jumps to +130m) at the same 55km position.
        now += 65 * minuteMs // Now 110m after feed (10m until feed -> IMMINENT), still 125m ETA due to jam.
        val delayedJourney = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            JourneySnapshot("route-c", "천안분기점", now + 125 * minuteMs, 55_000L, GeoPoint(36.72, 127.27), destinationPoiId = "gyeongbu-cheonan-jc"),
            now,
        )
        val delayedEval = DeparturePlanner().evaluate(care, preparation, delayedJourney, now)
        assertEquals(ForecastStage.IMMINENT, delayedEval.forecast?.stage)
        assertTrue(CareNeed.FEED in delayedEval.forecast!!.journey.careBeforeArrival)

        val planner = StopPlanner()
        val delayedEvidence = gyeongbuRouteAccess(now).readyValue!!.evaluate(delayedJourney, now)
        val delayedStops = planner.plan(delayedJourney, gyeongbuCatalog(now), delayedEvidence, now)
        val stage1Nearest = delayedStops.nearestSafeStop.candidates.first()
        val stage2FullCare = delayedStops.fullCare.candidates.first()
        // At 55km rem, Stage 1 is 오산 shelter (49km rem); Stage 2 is 안성(부산) nursing rest area (32km rem).
        assertEquals("public-6b47faeb0db871b24f624671", stage1Nearest.facility.id)
        assertEquals("public-c7b5fbf04b0ab94ff0ae3ca1", stage2FullCare.facility.id)

        val ctx = ResponseContext(preparation.outingId, profile.id, isDemo = false)
        val coordinator = CryResponseCoordinator { "ep-c-1" }
        val assessement1 = ResponseAssessor.assess(ctx, care, delayedEval.forecast, preparation.driveSession, now)
        coordinator.confirm(coordinator.update(assessement1, delayedStops, now).episodeId!!)
        val selectedStage1 = coordinator.select(StopPlanType.NEAREST_SAFE_STOP, delayedStops, now)
        assertEquals("public-6b47faeb0db871b24f624671", selectedStage1.selected?.candidate?.facility?.id)

        // If 61 seconds elapse without fresh route-access verification, selected candidate expires safely (STOP_EXPIRED).
        val expiredNow = now + 61_000L
        val refreshedAfterStage1Journey = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            JourneySnapshot("route-c", "천안분기점", expiredNow + 90 * minuteMs, 48_000L, GeoPoint(36.72, 127.27), destinationPoiId = "gyeongbu-cheonan-jc"),
            expiredNow,
        )
        val stage2Stops = planner.plan(
            refreshedAfterStage1Journey,
            gyeongbuCatalog(expiredNow),
            gyeongbuRouteAccess(expiredNow).readyValue!!.evaluate(refreshedAfterStage1Journey, expiredNow),
            expiredNow,
        )
        val expiredCheck = coordinator.update(
            ResponseAssessor.assess(ctx, care, DeparturePlanner().evaluate(care, preparation, refreshedAfterStage1Journey, expiredNow).forecast, preparation.driveSession, expiredNow),
            stage2Stops,
            expiredNow,
        )
        assertEquals(ResponseNotice.STOP_EXPIRED, expiredCheck.notice)
        assertEquals(ResponsePhase.ACTIVE, expiredCheck.phase)

        // Proceed to Stage 2 Full Care (안성(부산)), record feeding at rest area -> returns to IDLE & pushes feed forecast +120m.
        val stage2Selected = coordinator.select(StopPlanType.FULL_CARE, stage2Stops, expiredNow)
        assertEquals("public-c7b5fbf04b0ab94ff0ae3ca1", stage2Selected.selected?.candidate?.facility?.id)
        care = CareJournal.append(care, CareEvent("feed-c2", profile.id, CareEventType.FEED, expiredNow, 150))
        val postStopEval = DeparturePlanner().evaluate(care, preparation, refreshedAfterStage1Journey, expiredNow)
        val afterAssessment = ResponseAssessor.assess(ctx, care, postStopEval.forecast, preparation.driveSession, expiredNow)
        val completed = coordinator.finish(afterAssessment, expiredNow, ResponseNotice.CARE_RECORDED)
        assertEquals(ResponsePhase.IDLE, completed.phase)
        assertTrue(afterAssessment.imminentTokens.isEmpty())
        assertEquals(expiredNow + 120 * minuteMs, postStopEval.forecast?.needs?.single { it.need == CareNeed.FEED }?.estimate?.expectedAtEpochMillis)
    }

    @Test
    fun offlineCacheStaleFallbackZeroRecordsAndUnverifiedNavigationStaySafe() = runBlocking {
        var now = baseClock
        val emptyCare = CareRecord(BabyProfile("zero-baby", monthAge = 1), events = emptyList())
        val emptyRoute = DataSnapshot<JourneySnapshot>(DataOrigin.NAVI_SDK, DataStatus.EMPTY)
        val eval = DeparturePlanner().evaluate(emptyCare, DepartureRecord("zero-outing"), emptyRoute, now)
        assertEquals(ForecastStage.UNKNOWN, eval.forecast?.stage)
        assertTrue(eval.forecast?.isLimited == true)
        assertEquals(emptyList(), emptyCare.events)
        assertNull(eval.packing.feedServings)

        val facilityStore = object : FacilityCatalogStore {
            var cached: DataSnapshot<FacilityCatalog>? = gyeongbuCatalog(now)
            override fun read() = cached
            override fun write(snapshot: DataSnapshot<FacilityCatalog>) { cached = snapshot }
        }
        val accessStore = object : RouteAccessCatalogStore {
            var cached: DataSnapshot<RouteAccessCatalog>? = gyeongbuRouteAccess(now)
            override fun read() = cached
            override fun write(snapshot: DataSnapshot<RouteAccessCatalog>) { cached = snapshot }
        }
        val offlineFacilityRepo = FacilityRepository(FacilityCatalogClient { throw IOException("offline") }, facilityStore, { now }, Dispatchers.Unconfined)
        val offlineAccessRepo = RouteAccessRepository(RouteAccessCatalogClient { throw IOException("offline") }, accessStore, { now }, Dispatchers.Unconfined)
        offlineFacilityRepo.refresh()
        offlineAccessRepo.refresh()

        assertEquals(FacilityProblem.OFFLINE, offlineFacilityRepo.state.value.problem)
        assertEquals(DataStatus.STALE, offlineFacilityRepo.state.value.snapshot.status)
        assertEquals(DataStatus.STALE, offlineAccessRepo.state.value.status)

        // Stale facility catalog keeps StopPlanner safely degraded without fabricating candidates.
        val readyRoute = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            JourneySnapshot("route-offline", "천안분기점", now + 60 * minuteMs, 80_000L, GeoPoint(36.72, 127.27), destinationPoiId = "gyeongbu-cheonan-jc"),
            now,
        )
        val degradedStops = StopPlanner().plan(readyRoute, offlineFacilityRepo.state.value.snapshot, gyeongbuRouteAccess(now).readyValue!!.evaluate(readyRoute, now), now)
        assertEquals(StopPlanningProblem.CATALOG_STALE, degradedStops.problem)
        assertTrue(degradedStops.fullCare.candidates.isEmpty())
        assertTrue(degradedStops.nearestSafeStop.candidates.isEmpty())

        // Even with fresh forecast inputs + clock advance, missing care + empty nav still works deterministically.
        now += 30 * minuteMs
        val laterEval = ForecastEngine().evaluate(ForecastInput(emptyCare, now, emptyRoute))
        assertEquals(ForecastStage.UNKNOWN, laterEval.stage)
    }
}
