package com.hmgchallenge.backend

import com.hmgchallenge.backend.facility.FacilityController
import com.hmgchallenge.backend.facility.PublicFacilityCatalog
import com.hmgchallenge.backend.facility.VerifiedRouteAccessCatalog
import com.hmgchallenge.shared.care.BabyProfile
import com.hmgchallenge.shared.care.CareEvent
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareJournal
import com.hmgchallenge.shared.care.CarePreferences
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.departure.DeparturePlanner
import com.hmgchallenge.shared.departure.DepartureRecord
import com.hmgchallenge.shared.departure.PackingScope
import com.hmgchallenge.shared.departure.PackingSettings
import com.hmgchallenge.shared.facility.FacilityCatalogCodec
import com.hmgchallenge.shared.facility.LastRestAreaAlerts
import com.hmgchallenge.shared.facility.LastRestAreaStatus
import com.hmgchallenge.shared.facility.RouteAccessCatalogCodec
import com.hmgchallenge.shared.facility.StopPlanStatus
import com.hmgchallenge.shared.facility.StopPlanType
import com.hmgchallenge.shared.facility.StopPlanner
import com.hmgchallenge.shared.facility.StopPlanningProblem
import com.hmgchallenge.shared.forecast.CareNeed
import com.hmgchallenge.shared.forecast.ForecastStage
import com.hmgchallenge.shared.journey.DriveSession
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot
import com.hmgchallenge.shared.response.CryResponseCoordinator
import com.hmgchallenge.shared.response.ResponseAssessor
import com.hmgchallenge.shared.response.ResponseContext
import com.hmgchallenge.shared.response.ResponseNotice
import com.hmgchallenge.shared.response.ResponsePhase
import org.springframework.core.io.DefaultResourceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies Scenarios A, C, and the Pleos SDK corridor against the actual bundled 468-entry public
 * catalog and curated route-access corridors using fixed clocks.
 */
class ScenarioBundleIntegrationTest {
    private val fixedNow = 1_794_450_000_000L
    private val minuteMs = 60_000L
    private val controller = FacilityController(
        PublicFacilityCatalog("classpath:facilities/public-catalog.json", DefaultResourceLoader()),
        VerifiedRouteAccessCatalog("classpath:facilities/route-access.json", DefaultResourceLoader()),
    )

    private fun bundledFacilities(at: Long = fixedNow) =
        FacilityCatalogCodec.decode(controller.facilities("public").body!!).copy(observedAtEpochMillis = at)

    private fun bundledAccess(at: Long = fixedNow) =
        RouteAccessCatalogCodec.decode(controller.routeAccess("public").body!!).copy(observedAtEpochMillis = at)

    @Test
    fun bundledPublicDatasetsDriveLongDistanceCongestionAndTwoStageStopsWithFixedClock() {
        var now = fixedNow
        val profile = BabyProfile("bundle-baby", monthAge = 2, preferences = CarePreferences(feedIntervalMinutes = 120, seatedBreakMinutes = 120))
        var care = CareRecord(profile, listOf(CareEvent("feed-0", profile.id, CareEventType.FEED, now - 45 * minuteMs, 120)))
        val preparation = DepartureRecord(
            outingId = "bundle-outing",
            settings = PackingSettings(PackingScope.WHOLE_OUTING, extraOutingMinutes = 120, feedIntervalMinutes = 120, diaperIntervalMinutes = 120),
            driveSession = DriveSession("bundle-drive", profile.id, now - 15 * minuteMs),
        )

        // Initially short uncongested remaining travel (40m ETA, 55km remaining -> ARRIVAL_BEFORE_CARE).
        val normalJourney = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            JourneySnapshot("bundle-j1", "천안분기점", now + 40 * minuteMs, 55_000L, GeoPoint(36.72, 127.27), destinationPoiId = "gyeongbu-cheonan-jc"),
            now,
        )
        val planner = DeparturePlanner()
        val normalEval = planner.evaluate(care, preparation, normalJourney, now)
        assertEquals(ForecastStage.RELAXED, normalEval.forecast?.stage)
        // 40m + 120m outing = 160m -> ceil(160/120)+1 = 3 feedings, ceil(160/120)+2 = 4 diapers.
        assertEquals(3, normalEval.packing.feedServings)
        assertEquals(4, normalEval.packing.diapers)

        // Advance clock by 65m (last feed now 110m ago -> IMMINENT) + congestion stretches ETA to 130m.
        now += 65 * minuteMs
        val congestedJourney = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            JourneySnapshot("bundle-j1", "천안분기점", now + 130 * minuteMs, 55_000L, GeoPoint(36.72, 127.27), destinationPoiId = "gyeongbu-cheonan-jc"),
            now,
        )
        val congestedEval = planner.evaluate(care, preparation, congestedJourney, now)
        assertEquals(ForecastStage.IMMINENT, congestedEval.forecast?.stage)
        assertTrue(CareNeed.FEED in congestedEval.forecast!!.journey.careBeforeArrival)

        val facilities = bundledFacilities(now)
        val accessCatalog = bundledAccess(now)
        val evidence = accessCatalog.readyValue!!.evaluate(congestedJourney, now)
        val stopResult = StopPlanner().plan(congestedJourney, facilities, evidence, now)

        assertEquals(StopPlanningProblem.NONE, stopResult.problem)
        assertEquals(StopPlanStatus.AVAILABLE, stopResult.nearestSafeStop.status)
        assertEquals(StopPlanStatus.AVAILABLE, stopResult.fullCare.status)
        // Bundled public names verified against 468-entry dataset:
        val stage1 = stopResult.nearestSafeStop.candidates.first()
        val stage2 = stopResult.fullCare.candidates.first()
        assertEquals("public-6b47faeb0db871b24f624671", stage1.facility.id)
        assertTrue(stage1.facility.name.contains("오산"))
        assertEquals("public-c7b5fbf04b0ab94ff0ae3ca1", stage2.facility.id)
        assertEquals("안성(부산)", stage2.facility.name)
        assertEquals(LastRestAreaStatus.AVAILABLE, stopResult.lastRestArea.status)
        assertEquals("천안호두(부산)", stopResult.lastRestArea.candidate?.facility?.name)

        // Coordinate with CryResponseCoordinator and finish at Stage 2 rest area after recording care.
        val coordinator = CryResponseCoordinator { "bundle-ep-1" }
        val ctx = ResponseContext(preparation.outingId, profile.id, isDemo = false)
        val assessment1 = ResponseAssessor.assess(ctx, care, congestedEval.forecast, preparation.driveSession, now)
        val ep = coordinator.update(assessment1, stopResult, now).episodeId!!
        coordinator.confirm(ep)
        coordinator.select(StopPlanType.FULL_CARE, stopResult, now)
        care = CareJournal.append(care, CareEvent("feed-stop", profile.id, CareEventType.FEED, now, 140))
        val afterAssessment = ResponseAssessor.assess(ctx, care, planner.evaluate(care, preparation, congestedJourney, now).forecast, preparation.driveSession, now)
        val idle = coordinator.finish(afterAssessment, now, ResponseNotice.CARE_RECORDED)
        assertEquals(ResponsePhase.IDLE, idle.phase)
        assertTrue(afterAssessment.imminentTokens.isEmpty())

        // Approaching 천안호두(부산) triggers LastRestAreaAlerts once within 10 minutes.
        now += 110 * minuteMs
        val finalLeg = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            JourneySnapshot("bundle-j1", "천안분기점", now + 8 * minuteMs, 8_000L, GeoPoint(36.72, 127.27), destinationPoiId = "gyeongbu-cheonan-jc"),
            now,
        )
        val finalStops = StopPlanner().plan(finalLeg, bundledFacilities(now), bundledAccess(now).readyValue!!.evaluate(finalLeg, now), now)
        val alerts = LastRestAreaAlerts(600)
        assertEquals("천안호두(부산)", assertNotNull(alerts.poll(finalStops)).candidate.facility.name)
        assertNull(alerts.poll(finalStops))
    }

    @Test
    fun bundledPleosGuideCorridorMatchesVerifiedNavigationPoiAndRejectsUnknownDestination() {
        val facilities = bundledFacilities(fixedNow)
        val accessCatalog = bundledAccess(fixedNow)
        val pleosRoute = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            JourneySnapshot("pleos-j1", "42dot", fixedNow + 60_000L, 50L, GeoPoint(37.41289678, 127.09383464), destinationPoiId = "36khp37hh8gqcm5nqhe5"),
            fixedNow,
        )
        val evidence = accessCatalog.readyValue!!.evaluate(pleosRoute, fixedNow)
        val stops = StopPlanner().plan(pleosRoute, facilities, evidence, fixedNow)
        assertEquals(StopPlanningProblem.NONE, stops.problem)
        assertTrue(stops.fullCare.candidates.any { it.facility.id == "public-1416d8b56c2be67278db9e9f" && it.navigationPoiId == "36khp2hdj2cfcm5nqhe3" })
        // Opposite-direction 죽전(서울) at 40m rem is excluded.
        assertTrue(stops.nearestSafeStop.candidates.none { it.facility.id == "public-4ce7e3532f4f371c529563e3" })

        val unknownRoute = pleosRoute.copy(
            value = JourneySnapshot("unknown-j", "미등록 목적지", fixedNow + 600_000L, 5_000L, GeoPoint(35.1796, 129.0756), destinationPoiId = "unknown-poi"),
        )
        val unverifiedStops = StopPlanner().plan(unknownRoute, facilities, accessCatalog.readyValue!!.evaluate(unknownRoute, fixedNow), fixedNow)
        assertEquals(StopPlanningProblem.ACCESS_UNVERIFIED, unverifiedStops.problem)
        assertTrue(unverifiedStops.fullCare.candidates.isEmpty())
    }
}
