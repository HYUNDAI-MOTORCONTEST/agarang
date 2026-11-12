package com.hmgchallenge.shared.forecast

import com.hmgchallenge.shared.care.BabyProfile
import com.hmgchallenge.shared.care.CareEvent
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CarePreferences
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.journey.JourneySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JourneyForecastTest {
    @Test
    fun nativeSimulationIsNeverReportedAsARealJourneyForecast() {
        val now = 20_000_000L
        val care = CareRecord(BabyProfile("baby", 2))
        val route = JourneySnapshot("route", "모의 목적지", now + 60_000, isSimulation = true)
        val engine = ForecastEngine()
        for (status in listOf(DataStatus.READY, DataStatus.STALE)) {
            val result = engine.evaluate(ForecastInput(care, now, DataSnapshot(DataOrigin.NAVI_SDK, status, route, now)))
            assertTrue(result.isDemo)
        }
    }

    @Test
    fun etaChangesReevaluateForecastAndNavigationFailurePreservesCareEstimates() = runBlocking {
        var now = 20_000_000L
        val profile = BabyProfile("baby", 2, CarePreferences(feedIntervalMinutes = 180))
        val care = CareRecord(profile, listOf(CareEvent("feed", profile.id, CareEventType.FEED, now - 150 * 60_000)))
        val records = MutableStateFlow<CareRecord?>(care)
        val journeys = MutableStateFlow(DataSnapshot(
            DataOrigin.NAVI_SDK, DataStatus.READY, JourneySnapshot("route", "목적지", now + 10 * 60_000), now,
        ))
        val ticks = MutableStateFlow(now)
        val results = Channel<ForecastResult?>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Unconfined) {
            journeyForecasts(records, journeys, ticks, clock = { now }).collect { results.send(it) }
        }
        suspend fun next() = assertNotNull(withTimeout(2_000) { results.receive() })
        try {
            assertTrue(next().journey.careBeforeArrival.isEmpty())
            now += 1_000 // A callback after the last timer tick must not appear to be from the future.
            journeys.value = DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.READY,
                JourneySnapshot("route", "목적지", now + 60 * 60_000), now)
            val delayed = next()
            assertEquals(now, delayed.evaluatedAtEpochMillis)
            assertTrue(CareNeed.FEED in delayed.journey.careBeforeArrival)
            assertTrue(SuggestedAction.PLAN_STOP in delayed.suggestedActions)
            journeys.value = DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.PERMISSION_DENIED)
            val offline = next()
            assertEquals(delayed.estimates, offline.estimates)
            assertNull(offline.journey.estimatedArrivalAtEpochMillis)
            assertTrue(offline.isLimited)
            records.value = null
            assertNull(withTimeout(2_000) { results.receive() })
        } finally { collector.cancel(); results.close() }
    }
}
