package com.hmgchallenge.shared.data

import com.hmgchallenge.shared.demo.DemoJourney
import com.hmgchallenge.shared.facility.AmenityStatus
import com.hmgchallenge.shared.facility.RouteAccess
import com.hmgchallenge.shared.facility.StopPlan
import com.hmgchallenge.shared.facility.StopPlanStatus
import com.hmgchallenge.shared.facility.StopPlanType
import com.hmgchallenge.shared.forecast.CareNeed
import com.hmgchallenge.shared.forecast.CareTimeEstimate
import com.hmgchallenge.shared.forecast.ForecastBasis
import com.hmgchallenge.shared.forecast.ForecastInput
import com.hmgchallenge.shared.forecast.ForecastReasonCode
import com.hmgchallenge.shared.forecast.ForecastResult
import com.hmgchallenge.shared.forecast.ForecastStage
import com.hmgchallenge.shared.forecast.SuggestedAction
import com.hmgchallenge.shared.journey.DriveSession
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataContractsTest {
    private val now = 1_800_000_000_000L
    private val json = Json { ignoreUnknownKeys = true }
    private val journey = JourneySnapshot("route", "Destination", now + 3_600_000, 25_000)

    @Test
    fun readyRequiresBothValueAndObservationTime() {
        assertFailsWith<IllegalArgumentException> { DataSnapshot<JourneySnapshot>(DataOrigin.NAVI_SDK, DataStatus.READY) }
        assertFailsWith<IllegalArgumentException> { DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.READY, journey) }
    }

    @Test
    fun staleRouteIsDisplayableButNotUsableForCurrentJudgment() {
        val stale = DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.STALE, journey, now)
        assertEquals(journey, stale.value)
        assertNull(stale.readyValue)
    }

    @Test
    fun deniedAndUnsupportedValuesAreNotReplacedWithZeros() {
        for (status in listOf(DataStatus.PERMISSION_DENIED, DataStatus.UNSUPPORTED, DataStatus.FAILED, DataStatus.EMPTY)) {
            val unavailable = DataSnapshot<JourneySnapshot>(DataOrigin.NAVI_SDK, status)
            assertNull(unavailable.readyValue)
            assertFailsWith<IllegalArgumentException> { unavailable.copy(value = journey, observedAtEpochMillis = now) }
        }
        assertEquals(0, DataSnapshot(DataOrigin.VEHICLE_SDK, DataStatus.READY, 0, now).readyValue)
    }

    @Test
    fun genericSnapshotRoundTripPreservesExplicitUnitsAndSource() {
        val snapshot = DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.READY, journey, now)
        val serializer = DataSnapshot.serializer(JourneySnapshot.serializer())
        assertEquals(snapshot, json.decodeFromString(serializer, json.encodeToString(serializer, snapshot)))
    }

    @Test
    fun invalidReadyJsonDoesNotBecomeAValidRoute() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(DataSnapshot.serializer(JourneySnapshot.serializer()),
                """{"origin":"NAVI_SDK","status":"READY","observedAtEpochMillis":1000}""",
            )
        }
    }

    @Test
    fun demoSourceRemainsDemoAfterUpdates() {
        val source = DemoSnapshotSource(journey, now)
        val changed = journey.copy(estimatedArrivalAtEpochMillis = now + 7_200_000)
        source.publish(changed, now + 60_000)
        assertEquals(DataOrigin.DEMO, source.snapshots.value.origin)
        assertEquals(changed, source.snapshots.value.readyValue)
        assertEquals(now + 60_000, source.snapshots.value.observedAtEpochMillis)
    }

    @Test
    fun injectedClockMakesDemoScenarioReproducibleAndSeparateFromRealData() {
        val first = DemoJourney.create(now)
        val second = DemoJourney.create(now)
        assertEquals(first.care, second.care)
        assertEquals(first.journey.snapshots.value, second.journey.snapshots.value)
        assertEquals(first.stops.snapshots.value, second.stops.snapshots.value)
        assertEquals(DataOrigin.DEMO, first.journey.snapshots.value.origin)
        assertEquals("DEMO", first.care.profile.source.name)
        assertTrue(first.care.events.all { it.source.name == "DEMO" })
        assertTrue(first.stops.snapshots.value.readyValue!!.all { it.origin == DataOrigin.DEMO })
    }

    @Test
    fun demoClockBoundsPreventNegativeOrOverflowingTimes() {
        assertFailsWith<IllegalArgumentException> { DemoJourney.create(0) }
        assertFailsWith<IllegalArgumentException> { DemoJourney.create(Long.MAX_VALUE) }
    }

    @Test
    fun coordinateSwapAndNonFiniteCoordinatesAreRejected() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(127.0, 37.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(Double.NaN, 127.0) }
        assertEquals(37.0, GeoPoint(37.0, 127.0).latitudeDegrees)
    }

    @Test
    fun unknownEtaRemainsUnknownAndNegativeDistanceIsRejected() {
        assertNull(JourneySnapshot("route", "Destination").estimatedArrivalAtEpochMillis)
        assertFailsWith<IllegalArgumentException> { journey.copy(remainingDistanceMeters = -1) }
    }

    @Test
    fun sessionCannotEndBeforeItStartsOrBelongToAnotherBaby() {
        assertFailsWith<IllegalArgumentException> { DriveSession("session", "baby", now, now - 1) }
        val demo = DemoJourney.create(now)
        assertFailsWith<IllegalArgumentException> {
            ForecastInput(demo.care, now, demo.journey.snapshots.value, demo.driveSession.copy(babyId = "other"))
        }
    }

    @Test
    fun stopPlanRejectsOppositeDirectionAndUnverifiedNursingRoom() {
        val candidates = DemoJourney.create(now).stops.snapshots.value.readyValue!!
        val fullCare = candidates.first()
        assertFailsWith<IllegalArgumentException> {
            StopPlan(StopPlanType.FULL_CARE, StopPlanStatus.AVAILABLE,
                listOf(fullCare.copy(routeAccess = RouteAccess.OPPOSITE_DIRECTION)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            StopPlan(StopPlanType.FULL_CARE, StopPlanStatus.AVAILABLE,
                listOf(fullCare.copy(facility = fullCare.facility.copy(nursingRoom = AmenityStatus.UNKNOWN))),
            )
        }
    }

    @Test
    fun noStopCandidateIsAnExplicitNormalResult() {
        val noStops = StopPlan(StopPlanType.NEAREST_SAFE_STOP, StopPlanStatus.NO_CONFIRMED_CANDIDATE)
        assertEquals(emptyList(), noStops.candidates)
        assertFailsWith<IllegalArgumentException> { noStops.copy(status = StopPlanStatus.AVAILABLE) }
    }

    @Test
    fun demoAndUnconfirmedFacilitiesCannotRequestRealNavigation() {
        val demo = DemoJourney.create(now).stops.snapshots.value.readyValue!!.first()
        assertTrue(!demo.canRequestNavigation)
        assertTrue(!demo.copy(origin = DataOrigin.PUBLIC_DATA).canRequestNavigation)
        assertTrue(demo.copy(origin = DataOrigin.PUBLIC_DATA, journeyId = "route",
            accessVerifiedAtEpochMillis = now, navigationLocation = GeoPoint(37.0, 127.0)).canRequestNavigation)
        assertTrue(!demo.copy(origin = DataOrigin.PUBLIC_DATA, routeAccess = RouteAccess.UNKNOWN).canRequestNavigation)
    }

    @Test
    fun demoEstimateCannotBeMarkedAsRealForecast() {
        assertFailsWith<IllegalArgumentException> {
            ForecastResult(now, ForecastStage.CAUTION,
                listOf(CareTimeEstimate(CareNeed.FEED, now + 1_000, ForecastBasis.DEMO_REFERENCE)),
                listOf(ForecastReasonCode.CARE_TIME_APPROACHING), listOf(SuggestedAction.PLAN_STOP), "demo-v1", false,
            )
        }
    }

    @Test
    fun firstDayCanReturnLimitedGuidanceWithoutInventingAFeedingTime() {
        val result = ForecastResult(now, ForecastStage.UNKNOWN, emptyList(),
            listOf(ForecastReasonCode.NO_CARE_RECORDS), listOf(SuggestedAction.CHECK_BEFORE_DEPARTURE), "rules-v1", false,
        )
        assertEquals(result, json.decodeFromString<ForecastResult>(json.encodeToString(ForecastResult.serializer(), result)))
        assertTrue(result.estimates.isEmpty())
    }
}
