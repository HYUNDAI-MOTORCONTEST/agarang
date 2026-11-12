package com.hmgchallenge.shared.forecast

import com.hmgchallenge.shared.care.BabyProfile
import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.care.CareEvent
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CarePreferences
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.demo.DemoJourney
import com.hmgchallenge.shared.journey.DriveSession
import com.hmgchallenge.shared.journey.JourneySnapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForecastEngineTest {
    private val now = 1_800_000_000_000L
    private val engine = ForecastEngine()
    private fun minutes(n: Int) = n.toLong() * 60_000
    private fun event(type: CareEventType, minutesAgo: Int, id: String = "$type-$minutesAgo") =
        CareEvent(id, "baby", type, now - minutes(minutesAgo))
    private fun care(events: List<CareEvent> = emptyList(), prefs: CarePreferences = CarePreferences(), age: Int = 2) =
        CareRecord(BabyProfile("baby", age, prefs), events)
    private fun emptyRoute() = DataSnapshot<JourneySnapshot>(DataOrigin.NAVI_SDK, DataStatus.EMPTY)
    private fun route(inMinutes: Int = 60, observed: Long = now) = DataSnapshot(
        DataOrigin.NAVI_SDK, DataStatus.READY, JourneySnapshot("route", "Destination", now + minutes(inMinutes)), observed,
    )
    private fun input(record: CareRecord, journey: DataSnapshot<JourneySnapshot> = emptyRoute(), session: DriveSession? = null) =
        ForecastInput(record, now, journey, session)
    private fun ForecastResult.need(need: CareNeed) = needs.single { it.need == need }
    private fun feedRecord(remainingMinutes: Int) = care(
        listOf(event(CareEventType.FEED, 120 - remainingMinutes)), CarePreferences(feedIntervalMinutes = 120),
    )
    private fun historyRecord() = care(listOf(0, 180, 360, 510).map { event(CareEventType.FEED, it) })

    @Test
    fun firstDayForEverySupportedAgeDoesNotInventEventsAnchorsOrPersonalTimes() {
        for (age in 0..24) {
            val record = care(age = age)
            val result = engine.evaluate(input(record))
            assertEquals(ForecastStage.UNKNOWN, result.stage)
            assertTrue(result.estimates.isEmpty())
            assertTrue(result.isLimited)
            assertEquals(listOf(SuggestedAction.CHECK_BEFORE_DEPARTURE), result.suggestedActions)
            assertTrue(ForecastReasonCode.NO_CARE_RECORDS in result.reasons)
            assertNotNull(result.need(CareNeed.FEED).reference)
            assertTrue(record.events.isEmpty())
            assertNull(record.profile.preferences.feedIntervalMinutes)
        }
    }

    @Test
    fun ageReferenceSeparatesEarlyMonthsSolidsAndIndividualRoutines() {
        assertEquals(IntervalRangeMinutes(60, 240), AgeReferenceModel.feeding(0).interval)
        for (age in 1..5) assertEquals(IntervalRangeMinutes(120, 240), AgeReferenceModel.feeding(age).interval)
        assertEquals(ReferenceGuidance.RESPONSIVE_FEEDING_WITH_SOLIDS, AgeReferenceModel.feeding(6).guidance)
        assertNull(AgeReferenceModel.feeding(11).interval)
        assertEquals(ReferenceGuidance.INDIVIDUAL_ROUTINE, AgeReferenceModel.feeding(12).guidance)
        assertNull(AgeReferenceModel.feeding(24).interval)
        assertFailsWith<IllegalArgumentException> { AgeReferenceModel.feeding(25) }
    }

    @Test
    fun publicFeedingRangeNeverBecomesAPersonalCountdownAfterOneRecord() {
        val result = engine.evaluate(input(care(listOf(event(CareEventType.FEED, 120)))))
        val feed = result.need(CareNeed.FEED)
        assertEquals(ForecastBasis.REFERENCE_MODEL, feed.basis)
        assertEquals(IntervalRangeMinutes(120, 240), feed.interval)
        assertNull(feed.estimate)
        assertTrue(ForecastReasonCode.REFERENCE_RANGE_ONLY in feed.reasons)
        assertEquals(ForecastStage.UNKNOWN, result.stage)
    }

    @Test
    fun settingWithoutAnAnchorDoesNotUseNowAsLastCareTime() {
        val result = engine.evaluate(input(care(prefs = CarePreferences(feedIntervalMinutes = 180, awakeWindowMinutes = 90))))
        assertTrue(result.estimates.isEmpty())
        assertTrue(ForecastReasonCode.MISSING_ANCHOR in result.need(CareNeed.FEED).reasons)
        assertEquals(ForecastBasis.CAREGIVER_SETTING, result.need(CareNeed.FEED).basis)
    }

    @Test
    fun caregiverSettingTakesPriorityOverObservedIntervals() {
        val record = historyRecord().copy(profile = historyRecord().profile.copy(preferences = CarePreferences(feedIntervalMinutes = 240)))
        val feed = engine.evaluate(input(record)).need(CareNeed.FEED)
        val estimate = assertNotNull(feed.estimate)
        assertEquals(ForecastBasis.CAREGIVER_SETTING, feed.basis)
        assertEquals(now + minutes(240), estimate.expectedAtEpochMillis)
        assertEquals(estimate.earliestAtEpochMillis, estimate.latestAtEpochMillis)
        assertEquals(0, estimate.historyIntervalCount)
    }

    @Test
    fun historyKeepsObservedRangeMedianAnchorAndIntervalCount() {
        val feed = engine.evaluate(input(historyRecord())).need(CareNeed.FEED)
        val estimate = assertNotNull(feed.estimate)
        assertEquals(ForecastBasis.PERSONAL_HISTORY, feed.basis)
        assertEquals(IntervalRangeMinutes(150, 180), feed.interval)
        assertEquals(now + minutes(150), estimate.earliestAtEpochMillis)
        assertEquals(now + minutes(180), estimate.expectedAtEpochMillis)
        assertEquals(now + minutes(180), estimate.latestAtEpochMillis)
        assertEquals(now, estimate.anchorAtEpochMillis)
        assertEquals(3, estimate.historyIntervalCount)
    }

    @Test
    fun fewerThanThreeIntervalsDoNotPretendToBePersonalHistory() {
        val record = care(listOf(0, 180, 360).map { event(CareEventType.FEED, it) })
        assertNull(engine.evaluate(input(record)).need(CareNeed.FEED).estimate)
    }

    @Test
    fun historyUsesOnlyRecentSamplesAndAtMostSixIntervals() {
        val many = care((0..10).map { event(CareEventType.FEED, it * 180) })
        assertEquals(6, engine.evaluate(input(many)).need(CareNeed.FEED).estimate?.historyIntervalCount)
        val old = event(CareEventType.FEED, 14 * 24 * 60 + 1)
        val fewRecent = care(listOf(0, 180, 360).map { event(CareEventType.FEED, it) } + old)
        assertNull(engine.evaluate(input(fewRecent)).need(CareNeed.FEED).estimate)
    }

    @Test
    fun subMinuteDuplicateIntervalsCannotSupplyEnoughHistory() {
        val events = (0..3).map { CareEvent("feed-$it", "baby", CareEventType.FEED, now - it * 500L) }
        assertNull(engine.evaluate(input(care(events))).need(CareNeed.FEED).estimate)
    }

    @Test
    fun unsortedDistinctEventsHaveTheSameMeaningAndInputIsNotMutated() {
        val original = historyRecord()
        val shuffled = original.copy(events = original.events.reversed())
        assertEquals(engine.evaluate(input(original)), engine.evaluate(input(shuffled)))
        assertEquals(listOf(0, 180, 360, 510).map { event(CareEventType.FEED, it) }, original.events)
    }

    @Test
    fun thresholdComparisonsUseMillisecondsWithoutRoundingAwayTheBoundary() {
        for ((remaining, expected) in listOf(61 to ForecastStage.RELAXED, 60 to ForecastStage.CAUTION, 16 to ForecastStage.CAUTION, 15 to ForecastStage.IMMINENT, 0 to ForecastStage.IMMINENT, -1 to ForecastStage.IMMINENT)) {
            assertEquals(expected, engine.evaluate(input(feedRecord(remaining))).stage)
        }
        for ((remaining, expected) in listOf(60 to ForecastStage.RELAXED, 15 to ForecastStage.CAUTION)) {
            val record = feedRecord(remaining)
            val justAfter = record.copy(events = record.events.map { it.copy(occurredAtEpochMillis = it.occurredAtEpochMillis + 1) })
            assertEquals(expected, engine.evaluate(input(justAfter)).stage)
        }
        assertTrue(ForecastReasonCode.CARE_TIME_PASSED in engine.evaluate(input(feedRecord(0))).reasons)
    }

    @Test
    fun injectedClockIsUsedOnlyByTheConvenienceEntryPoint() {
        var current = now
        val clocked = ForecastEngine(clock = { current })
        val record = feedRecord(16)
        assertEquals(ForecastStage.CAUTION, clocked.evaluate(record, emptyRoute()).stage)
        current += minutes(1)
        assertEquals(ForecastStage.IMMINENT, clocked.evaluate(record, emptyRoute()).stage)
        val explicit = ForecastEngine(clock = { error("Explicit evaluation must not read the clock") })
        assertEquals(engine.evaluate(input(record)), explicit.evaluate(input(record)))
    }

    @Test
    fun futureFeedingDoesNotFallBackToAnOlderCountdown() {
        val record = feedRecord(90).copy(events = feedRecord(90).events + event(CareEventType.FEED, -1, "future"))
        val result = engine.evaluate(input(record))
        assertNull(result.need(CareNeed.FEED).estimate)
        assertTrue(ForecastReasonCode.FUTURE_CARE_RECORD in result.reasons)
        assertTrue(result.isLimited)
        assertFalse(SuggestedAction.CONTINUE_JOURNEY in result.suggestedActions)
    }

    @Test
    fun futureNonPredictedCareAlsoMarksTheResultLimited() {
        val record = care(listOf(event(CareEventType.FEED, 0), event(CareEventType.SLEEP_START, 5), event(CareEventType.DIAPER, -1)), CarePreferences(feedIntervalMinutes = 180))
        val result = engine.evaluate(input(record, route()))
        assertTrue(result.isLimited)
        assertTrue(ForecastReasonCode.FUTURE_CARE_RECORD in result.reasons)
        assertFalse(SuggestedAction.CONTINUE_JOURNEY in result.suggestedActions)
    }

    @Test
    fun veryOldAnchorIsUnknownInsteadOfAnEternallyOverdueNeed() {
        val record = care(listOf(event(CareEventType.FEED, 24 * 60 + 1)), CarePreferences(feedIntervalMinutes = 120))
        val feed = engine.evaluate(input(record)).need(CareNeed.FEED)
        assertNull(feed.estimate)
        assertTrue(ForecastReasonCode.STALE_CARE_RECORD in feed.reasons)
    }

    @Test
    fun timestampOverflowProducesAnExplicitUnavailableResult() {
        val extreme = care(listOf(CareEvent("last", "baby", CareEventType.FEED, Long.MAX_VALUE - 1_000)), CarePreferences(feedIntervalMinutes = Int.MAX_VALUE))
        val result = engine.evaluate(ForecastInput(extreme, Long.MAX_VALUE - 500, emptyRoute()))
        assertTrue(result.estimates.isEmpty())
        assertTrue(ForecastReasonCode.TIME_OUT_OF_RANGE in result.reasons)
    }

    @Test
    fun wakeRecordAndCaregiverWindowProduceASleepPlanningTime() {
        val record = care(listOf(event(CareEventType.SLEEP_END, 30)), CarePreferences(awakeWindowMinutes = 90))
        assertEquals(now + minutes(60), engine.evaluate(input(record)).need(CareNeed.SLEEP).estimate?.expectedAtEpochMillis)
    }

    @Test
    fun personalAwakeHistoryUsesWakeToSleepPairsNotSleepDuration() {
        val sleeps = listOf(
            event(CareEventType.SLEEP_END, 600), event(CareEventType.SLEEP_START, 510),
            event(CareEventType.SLEEP_END, 450), event(CareEventType.SLEEP_START, 330),
            event(CareEventType.SLEEP_END, 270), event(CareEventType.SLEEP_START, 120),
            event(CareEventType.SLEEP_END, 30),
        )
        val sleep = engine.evaluate(input(care(sleeps))).need(CareNeed.SLEEP)
        assertEquals(ForecastBasis.PERSONAL_HISTORY, sleep.basis)
        assertEquals(IntervalRangeMinutes(90, 150), sleep.interval)
        assertEquals(now + minutes(90), sleep.estimate?.expectedAtEpochMillis)
        assertEquals(3, sleep.estimate?.historyIntervalCount)
    }

    @Test
    fun noValidatedAgeOnlyAwakeWindowIsInvented() {
        val record = care(listOf(event(CareEventType.SLEEP_END, 30)))
        for (age in 0..24) {
            val sleep = engine.evaluate(input(record.copy(profile = record.profile.copy(monthAge = age)))).need(CareNeed.SLEEP)
            assertNull(sleep.estimate)
            assertNull(sleep.interval)
        }
    }

    @Test
    fun recordedSleepIsNotAPredictionOfTheNextWakeTime() {
        val record = care(listOf(event(CareEventType.SLEEP_START, 30)), CarePreferences(awakeWindowMinutes = 90))
        val result = engine.evaluate(input(record))
        assertTrue(result.journey.recordedSleeping)
        assertNull(result.need(CareNeed.SLEEP).estimate)
        assertTrue(ForecastReasonCode.SLEEP_IN_PROGRESS in result.reasons)
    }

    @Test
    fun invalidAndFutureSleepSequencesAreNotUsedForSleepAdvice() {
        val duplicate = care(listOf(event(CareEventType.SLEEP_START, 10), event(CareEventType.SLEEP_START, 20)))
        assertTrue(ForecastReasonCode.INVALID_SLEEP_SEQUENCE in engine.evaluate(input(duplicate)).reasons)
        val future = care(listOf(event(CareEventType.SLEEP_START, -1)))
        val result = engine.evaluate(input(future, route(10)))
        assertFalse(result.journey.recordedSleeping)
        assertFalse(SuggestedAction.CHECK_SLEEP_AT_ARRIVAL in result.suggestedActions)
    }

    @Test
    fun seatedTimeRequiresAnActiveCaregiverSession() {
        val record = care(prefs = CarePreferences(seatedBreakMinutes = 120))
        assertNull(engine.evaluate(input(record)).need(CareNeed.SEATED_BREAK).estimate)
        val ended = DriveSession("trip", "baby", now - minutes(100), now)
        assertNull(engine.evaluate(input(record, session = ended)).need(CareNeed.SEATED_BREAK).estimate)
        val future = DriveSession("trip", "baby", now + 1)
        assertTrue(ForecastReasonCode.FUTURE_DRIVE_SESSION in engine.evaluate(input(record, session = future)).reasons)
    }

    @Test
    fun seatedSettingOverridesGeneralTravelContext() {
        val record = care(prefs = CarePreferences(seatedBreakMinutes = 60))
        val result = engine.evaluate(input(record, session = DriveSession("trip", "baby", now - minutes(50))))
        val seat = result.need(CareNeed.SEATED_BREAK)
        assertEquals(ForecastBasis.CAREGIVER_SETTING, seat.basis)
        assertEquals(now + minutes(10), seat.estimate?.expectedAtEpochMillis)
        assertEquals(ForecastStage.IMMINENT, seat.stage)
        assertTrue(SuggestedAction.PLAN_STOP in result.suggestedActions)
        assertFalse(SuggestedAction.CARE_THEN_DEPART in result.suggestedActions)
    }

    @Test
    fun generalTravelBreakHasAWideWindowAndRemainsLimited() {
        val result = engine.evaluate(input(care(), route(180), DriveSession("trip", "baby", now)))
        val seat = result.need(CareNeed.SEATED_BREAK)
        assertEquals(ForecastBasis.REFERENCE_MODEL, seat.basis)
        assertEquals(now + minutes(120), seat.estimate?.earliestAtEpochMillis)
        assertEquals(now + minutes(180), seat.estimate?.latestAtEpochMillis)
        assertEquals(listOf(AgeReferenceModel.AAP_TRAVEL), seat.reference?.sourceIds)
        assertTrue(result.isLimited)
        assertNull(AgeReferenceModel.travelBreak(12))
    }

    @Test
    fun missingOrDeniedNavigationDoesNotDisableCareForecasts() {
        for (status in DataStatus.entries.filter { it != DataStatus.READY && it != DataStatus.STALE }) {
            val result = engine.evaluate(input(feedRecord(90), DataSnapshot(DataOrigin.NAVI_SDK, status)))
            assertNotNull(result.need(CareNeed.FEED).estimate)
            assertNull(result.journey.estimatedArrivalAtEpochMillis)
            assertTrue(result.isLimited)
            assertFalse(SuggestedAction.CONTINUE_JOURNEY in result.suggestedActions)
        }
    }

    @Test
    fun staleExpiredAndFutureObservedRoutesNeverDriveStopPlanning() {
        val variants = listOf(
            route(180).copy(status = DataStatus.STALE), route(180, now - minutes(5) - 1), route(180, now + 1),
            route(180).copy(origin = DataOrigin.PUBLIC_DATA),
        )
        for (journey in variants) {
            val result = engine.evaluate(input(feedRecord(120), journey))
            assertNull(result.journey.estimatedArrivalAtEpochMillis)
            assertTrue(result.journey.careBeforeArrival.isEmpty())
            assertFalse(SuggestedAction.PLAN_STOP in result.suggestedActions)
        }
        assertNotNull(engine.evaluate(input(feedRecord(120), route(180, now - minutes(5)))).journey.estimatedArrivalAtEpochMillis)
    }

    @Test
    fun absentOrPastEtaIsNotTurnedIntoAnArrivalNow() {
        val missing = route().copy(value = JourneySnapshot("route", "Destination"))
        assertTrue(ForecastReasonCode.ETA_UNAVAILABLE in engine.evaluate(input(feedRecord(90), missing)).reasons)
        val past = route().copy(value = JourneySnapshot("route", "Destination", now - 1))
        assertTrue(ForecastReasonCode.ETA_BEFORE_NOW in engine.evaluate(input(feedRecord(90), past)).reasons)
        assertEquals(now, engine.evaluate(input(feedRecord(90), route(0))).journey.estimatedArrivalAtEpochMillis)
    }

    @Test
    fun arrivalDelayChangesPlanningStageAndActionsWithoutChangingCareEstimate() {
        val record = care(listOf(event(CareEventType.FEED, 0), event(CareEventType.SLEEP_START, 5)), CarePreferences(feedIntervalMinutes = 120))
        val early = engine.evaluate(input(record, route(60)))
        val delayed = engine.evaluate(input(record, route(180)))
        assertEquals(early.estimates, delayed.estimates)
        assertEquals(ForecastStage.RELAXED, early.stage)
        assertFalse(early.isLimited)
        assertTrue(SuggestedAction.CONTINUE_JOURNEY in early.suggestedActions)
        assertEquals(ForecastStage.CAUTION, delayed.stage)
        assertEquals(listOf(CareNeed.FEED), delayed.journey.careBeforeArrival)
        assertTrue(SuggestedAction.PLAN_STOP in delayed.suggestedActions)
        assertFalse(SuggestedAction.CONTINUE_JOURNEY in delayed.suggestedActions)
    }

    @Test
    fun routeComparisonUsesEarliestBoundRatherThanMedianAndKeepsEqualityDistinct() {
        val overlaps = engine.evaluate(input(historyRecord(), route(165)))
        assertEquals(listOf(CareNeed.FEED), overlaps.journey.careBeforeArrival)
        val atArrival = engine.evaluate(input(historyRecord(), route(150)))
        assertTrue(atArrival.journey.careBeforeArrival.isEmpty())
        assertTrue(ForecastReasonCode.CARE_AT_ARRIVAL in atArrival.reasons)
        assertTrue(SuggestedAction.CHECK_CARE_AT_ARRIVAL in atArrival.suggestedActions)
    }

    @Test
    fun sleepArrivalCandidateDoesNotHideANeedBeforeArrival() {
        val asleep = care(listOf(event(CareEventType.SLEEP_START, 20)))
        val near = engine.evaluate(input(asleep, route(10)))
        assertTrue(SuggestedAction.CHECK_SLEEP_AT_ARRIVAL in near.suggestedActions)
        val needsFeed = asleep.copy(
            profile = asleep.profile.copy(preferences = CarePreferences(feedIntervalMinutes = 120)),
            events = asleep.events + event(CareEventType.FEED, 115),
        )
        val urgent = engine.evaluate(input(needsFeed, route(10), DriveSession("trip", "baby", now)))
        assertTrue(SuggestedAction.PLAN_STOP in urgent.suggestedActions)
        assertFalse(SuggestedAction.CHECK_SLEEP_AT_ARRIVAL in urgent.suggestedActions)
    }

    @Test
    fun imminentCareBeforeAConfirmedSessionSuggestsCareThenDeparture() {
        val result = engine.evaluate(input(feedRecord(10)))
        assertTrue(SuggestedAction.CARE_THEN_DEPART in result.suggestedActions)
        assertTrue(SuggestedAction.CHECK_CARE in result.suggestedActions)
    }

    @Test
    fun demoScenarioIsDeterministicAndNeverLosesItsDemoLabel() {
        val demo = DemoJourney.create(now)
        val request = ForecastInput(demo.care, now, demo.journey.snapshots.value, demo.driveSession)
        val first = engine.evaluate(request)
        assertEquals(first, engine.evaluate(request))
        assertTrue(first.isDemo)
        assertTrue(first.estimates.all { it.basis == ForecastBasis.DEMO_REFERENCE })
        assertTrue(first.journey.careBeforeArrival.isNotEmpty())
    }

    @Test
    fun mixedDemoRecordsAreFlaggedAndExcludedFromPersonalLearning() {
        val mixed = historyRecord().copy(events = historyRecord().events.mapIndexed { index, e ->
            if (index == 0) e else e.copy(source = CareEntrySource.DEMO)
        })
        val result = engine.evaluate(input(mixed))
        assertTrue(result.isDemo)
        assertTrue(result.isLimited)
        assertTrue(ForecastReasonCode.MIXED_CARE_SOURCES in result.reasons)
        assertNull(result.need(CareNeed.FEED).estimate)
        val demoRoute = engine.evaluate(input(feedRecord(90), route().copy(origin = DataOrigin.DEMO)))
        assertTrue(demoRoute.isDemo)
    }

    @Test
    fun demoLabelDoesNotEraseTheUseOfAGeneralTravelRange() {
        val record = care(
            listOf(event(CareEventType.FEED, 0), event(CareEventType.SLEEP_START, 5)),
            CarePreferences(feedIntervalMinutes = 180),
        ).let { it.copy(profile = it.profile.copy(source = CareEntrySource.DEMO), events = it.events.map { event -> event.copy(source = CareEntrySource.DEMO) }) }
        val result = engine.evaluate(input(record, route(60), DriveSession("trip", "baby", now)))
        assertTrue(result.isDemo)
        assertTrue(result.need(CareNeed.SEATED_BREAK).usesGeneralTiming)
        assertEquals(ForecastBasis.DEMO_REFERENCE, result.need(CareNeed.SEATED_BREAK).basis)
        assertTrue(result.isLimited)
        assertFalse(SuggestedAction.CONTINUE_JOURNEY in result.suggestedActions)
    }

    @Test
    fun resultsRoundTripWithRangesExplanationsAndAllVersions() {
        val result = engine.evaluate(input(historyRecord(), route(165)))
        val json = Json
        assertEquals(result, json.decodeFromString<ForecastResult>(json.encodeToString(ForecastResult.serializer(), result)))
        assertEquals(ForecastEngine.MODEL_VERSION, result.modelVersion)
        assertEquals(ForecastPolicy().version, result.policyVersion)
        assertEquals(AgeReferenceModel.VERSION, result.referenceModelVersion)
    }

    @Test
    fun policyIsVersionedAndItsThresholdsAreReplaceable() {
        val custom = ForecastEngine(ForecastPolicy(version = "test-policy", cautionMinutes = 20, imminentMinutes = 0))
        val result = custom.evaluate(input(feedRecord(15)))
        assertEquals(ForecastStage.CAUTION, result.stage)
        assertEquals("test-policy", result.policyVersion)
        assertFailsWith<IllegalArgumentException> { ForecastPolicy(imminentMinutes = 61) }
        assertFailsWith<IllegalArgumentException> { ForecastPolicy(minimumHistoryIntervals = 7) }
        assertFailsWith<IllegalArgumentException> { IntervalRangeMinutes(0, 10) }
        assertFailsWith<IllegalArgumentException> { CareTimeEstimate(CareNeed.FEED, now, ForecastBasis.CAREGIVER_SETTING, now + 1, now + 2) }
    }
}
