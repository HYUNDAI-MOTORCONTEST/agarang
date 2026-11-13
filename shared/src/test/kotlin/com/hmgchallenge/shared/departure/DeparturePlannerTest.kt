package com.hmgchallenge.shared.departure

import com.hmgchallenge.shared.care.BabyProfile
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.care.CareRecordCodec
import com.hmgchallenge.shared.care.CareJournal
import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareException
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.forecast.CareNeed
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeparturePlannerTest {
    private val now = 1_800_000_000_000L
    private val planner = DeparturePlanner()
    private val settings = PackingSettings(feedIntervalMinutes = 120, diaperIntervalMinutes = 90)
    private val record = DepartureRecord("outing", settings)
    private fun route(minutes: Long) = DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.READY,
        JourneySnapshot("route", "목적지", now + minutes * 60_000, destination = GeoPoint(37.5, 127.0)), now)
    private val absent = DataSnapshot<JourneySnapshot>(DataOrigin.NAVI_SDK, DataStatus.EMPTY)

    @Test
    fun journeyDurationIsRoundedUpBeforeAddingExplicitReserves() {
        val plan = planner.packing(record, "baby", route(121), now)
        assertEquals(3, plan.feedServings) // ceil(121 / 120) + 1
        assertEquals(4, plan.diapers) // ceil(121 / 90) + 2
        assertEquals(121, plan.durationMinutes)
    }

    @Test
    fun millisecondBoundaryDoesNotDisappearWhenDisplayMinutesAreRounded() {
        val exact = route(120)
        val oneMore = exact.copy(value = exact.readyValue!!.copy(estimatedArrivalAtEpochMillis = now + 120 * 60_000 + 1))
        assertEquals(2, planner.packing(record, "baby", exact, now).feedServings)
        assertEquals(3, planner.packing(record, "baby", oneMore, now).feedServings)
    }

    @Test
    fun ageOnlyDoesNotInventIntervalsOrQuantities() {
        val evaluation = planner.evaluate(CareRecord(BabyProfile("baby", 2)), record.copy(settings = PackingSettings()), route(180), now)
        assertNull(evaluation.packing.feedServings)
        assertNull(evaluation.packing.diapers)
        assertTrue(evaluation.forecast!!.needs.first { it.need == CareNeed.FEED }.estimate == null)
        assertEquals(5, evaluation.totalItems)
        assertTrue(evaluation.packing.needsQuantityConfirmation)
    }

    @Test
    fun manualTripIsExplicitFallbackAndValidNavigationTakesPriority() {
        val manual = record.copy(settings = settings.copy(manualTravelMinutes = 180))
        assertEquals(PackingDurationSource.MANUAL, planner.packing(manual, null, absent, now).source)
        assertEquals(180, planner.packing(manual, null, absent, now).durationMinutes)
        assertEquals(60, planner.packing(manual, null, route(60), now).durationMinutes)
        assertEquals(PackingDurationSource.NAVIGATION, planner.packing(manual, null, route(60), now).source)
    }

    @Test
    fun missingRouteAndMissingManualDurationLeaveQuantitiesUnknown() {
        val plan = planner.packing(record, null, absent, now)
        assertEquals(PackingProblem.DURATION_UNKNOWN, plan.problem)
        assertNull(plan.durationMinutes)
        assertNull(plan.feedServings)
    }

    @Test
    fun staleFutureAndWrongOriginRouteCannotSupplyAnEta() {
        for (snapshot in listOf(route(60).copy(status = DataStatus.STALE), route(60).copy(observedAtEpochMillis = now - 60_000),
            route(60).copy(observedAtEpochMillis = now + 1), route(60).copy(origin = DataOrigin.CAREGIVER), route(-1))) {
            assertEquals(PackingDurationSource.UNKNOWN, planner.packing(record, null, snapshot, now).source)
        }
    }

    @Test
    fun wholeOutingAddsOnlyCaregiverEnteredStayAndReturnTime() {
        val extended = record.copy(settings = settings.copy(scope = PackingScope.WHOLE_OUTING, extraOutingMinutes = 120))
        assertEquals(210, planner.packing(extended, null, route(90), now).durationMinutes)
        assertEquals(90, planner.packing(record.copy(settings = settings.copy(extraOutingMinutes = 120)), null, route(90), now).durationMinutes)
    }

    @Test
    fun excessiveJourneysAndLongMaxEtaDoNotOverflowQuantities() {
        assertEquals(PackingProblem.TRIP_TOO_LONG, planner.packing(record, null, route(4_321), now).problem)
        val huge = route(60).copy(value = route(60).readyValue!!.copy(estimatedArrivalAtEpochMillis = Long.MAX_VALUE))
        assertEquals(PackingProblem.TRIP_TOO_LONG, planner.packing(record, null, huge, now).problem)
    }

    @Test
    fun increasingEtaInvalidatesInsufficientCheckedAmountsButKeepsOtherItems() {
        val short = planner.packing(record, null, route(60), now)
        val feeding = record.items.first().copy(checkedAtEpochMillis = now, checkedForTrip = short.tripKey, checkedQuantity = short.feedServings)
        val wipes = record.items.first { it.kind == SupplyKind.WIPES }.copy(checkedAtEpochMillis = now, checkedForTrip = short.tripKey)
        val delayed = planner.packing(record, null, route(180), now)
        assertTrue(short.isChecked(feeding, now))
        assertFalse(delayed.isChecked(feeding, now))
        assertTrue(delayed.isChecked(wipes, now))
    }

    @Test
    fun decreasedRequirementKeepsAlreadyPackedLargerAmount() {
        val long = planner.packing(record, null, route(240), now)
        val item = record.items.first().copy(checkedAtEpochMillis = now, checkedForTrip = long.tripKey, checkedQuantity = long.feedServings)
        assertTrue(planner.packing(record, null, route(60), now).isChecked(item, now))
    }

    @Test
    fun newDestinationOutingBabyOrScopeInvalidatesOldChecks() {
        val initial = planner.packing(record, "baby", route(60), now)
        val item = record.items.first().copy(checkedAtEpochMillis = now, checkedForTrip = initial.tripKey, checkedQuantity = 10)
        val changedDestination = route(60).copy(value = route(60).readyValue!!.copy(destinationLabel = "다른 목적지"))
        for (plan in listOf(planner.packing(record, "baby", changedDestination, now),
            planner.packing(record.copy(outingId = "new"), "baby", route(60), now),
            planner.packing(record, "other-baby", route(60), now),
            planner.packing(record.copy(settings = settings.copy(scope = PackingScope.WHOLE_OUTING)), "baby", route(60), now))) {
            assertFalse(plan.isChecked(item, now))
        }
    }

    @Test
    fun processRestartWithSameDestinationKeepsSavedChecksDespiteNewSdkJourneyId() {
        val first = planner.packing(record, "baby", route(60), now)
        val item = record.items.first().copy(checkedAtEpochMillis = now, checkedForTrip = first.tripKey, checkedQuantity = 10)
        val restarted = route(60).copy(value = route(60).readyValue!!.copy(id = "new-sdk-session"))
        assertTrue(planner.packing(record, "baby", restarted, now).isChecked(item, now))
    }

    @Test
    fun oldChecksAndClockReversalRequireReconfirmation() {
        val plan = planner.packing(record, null, route(60), now)
        val item = record.items.first().copy(checkedAtEpochMillis = now, checkedForTrip = plan.tripKey, checkedQuantity = 10)
        assertFalse(plan.isChecked(item, now - 1))
        assertFalse(plan.isChecked(item, now + PackingPlan.CHECK_VALIDITY_MILLIS))
    }

    @Test
    fun demoDelayUpdatesBothForecastAndPackingWhileCareAnchorStaysFixed() {
        val demo = DepartureDemo.create(now)
        val initial = planner.evaluate(demo.care, demo.preparation, demo.journey(now), now)
        val delayed = demo.copy(delayMinutes = 90)
        val after = planner.evaluate(delayed.care, delayed.preparation, delayed.journey(now), now)
        assertTrue(initial.forecast!!.journey.careBeforeArrival.isEmpty())
        assertTrue(CareNeed.FEED in after.forecast!!.journey.careBeforeArrival)
        assertEquals(2, initial.packing.feedServings)
        assertEquals(3, after.packing.feedServings)
        assertEquals(demo.care, delayed.care)
        assertTrue(after.isDemo)
    }

    @Test
    fun firstDayDemoHasNoInventedCareEvents() {
        val demo = DepartureDemo.create(now, true)
        assertTrue(demo.care.events.isEmpty())
        assertNull(planner.evaluate(demo.care, demo.preparation, demo.journey(now), now).forecast!!.needs.first { it.need == CareNeed.FEED }.estimate)
    }

    @Test
    fun demoRecordingEditingAndUndoRemainDemoAndCannotEnterRealJournalCodec() {
        val initial = DepartureDemo.create(now)
        val before = initial.copy(care = initial.care.copy(profile = initial.care.profile.copy(
            preferences = initial.care.profile.preferences.copy(preferredFeedAmountMl = 150))))
        val recorded = before.record(CareEventType.FEED, now, "demo-new-feed")
        assertEquals(2, recorded.care.events.size)
        assertTrue(recorded.care.events.all { it.source == CareEntrySource.DEMO })
        assertEquals(150, recorded.care.events.first().feedAmountMl)
        assertEquals(now + 120 * 60_000, planner.evaluate(recorded.care, recorded.preparation, recorded.journey(now), now)
            .forecast!!.estimates.first { it.need == CareNeed.FEED }.expectedAtEpochMillis)
        val edited = CareJournal.edit(recorded.care, "demo-new-feed", now - 300_000, null, now, CareEntrySource.DEMO)
        assertEquals(now - 300_000, edited.events.first().occurredAtEpochMillis)
        assertEquals(before.care, CareJournal.undoLatest(recorded.care, CareEntrySource.DEMO))
        assertFailsWith<CareException> { CareRecordCodec.encode(recorded.care) }
        assertFailsWith<CareException> { CareJournal.normalize(recorded.care) }
    }
}
