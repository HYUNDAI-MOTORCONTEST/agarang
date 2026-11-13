package com.hmgchallenge.shared.response

import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.departure.DepartureDemo
import com.hmgchallenge.shared.departure.DepartureDemoSituation
import com.hmgchallenge.shared.departure.DeparturePlanner
import com.hmgchallenge.shared.facility.DemoStopScenario
import com.hmgchallenge.shared.facility.StopPlanType
import com.hmgchallenge.shared.facility.StopPlanner
import com.hmgchallenge.shared.forecast.CareNeed
import com.hmgchallenge.shared.forecast.ForecastStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryResponseTest {
    private val now = 1_800_000_000_000L
    private val demo = DepartureDemo.response(now)
    private val context = ResponseContext(demo.preparation.outingId, demo.care.profile.id, true)
    private var ids = 0
    private fun coordinator() = CryResponseCoordinator { "episode-${++ids}" }
    private fun assessment(value: DepartureDemo = demo, at: Long = now, ctx: ResponseContext = context): ResponseAssessment {
        val forecast = DeparturePlanner().evaluate(value.care, value.preparation, value.journey(at), at).forecast
        return ResponseAssessor.assess(ctx, value.care, forecast, value.preparation.driveSession, at)
    }
    private fun plans(at: Long = now) = StopPlanner().plan(demo.journey(at), DemoStopScenario.create(at).facilities, demo.evidence(at), at)

    @Test fun imminentOfferDoesNotOpenResponseBeforeConfirmation() {
        val flow = coordinator()
        val offered = flow.update(assessment(), plans(), now)
        assertEquals(ResponsePhase.SUGGESTED, offered.phase)
        assertFalse(offered.active)
        assertTrue(assessment().imminentTokens.isNotEmpty())
        assertEquals(ResponsePhase.SUGGESTED, flow.confirm("old-unseen-episode").phase)
        assertEquals(ResponsePhase.ACTIVE, flow.confirm(offered.episodeId!!).phase)
    }

    @Test fun ticksAndEtaDelayDoNotRepeatAnOfferOrReplaceActiveEpisode() {
        val flow = coordinator()
        val first = flow.update(assessment(), plans(), now)
        repeat(6) { offset ->
            assertEquals(first.episodeId, flow.update(assessment(at = now + offset * 1_000), plans(), now + offset * 1_000).episodeId)
        }
        flow.confirm(first.episodeId!!)
        val active = flow.state.episodeId
        val delayed = assessment(demo.copy(delayMinutes = 90))
        assertTrue(delayed.careBeforeArrival)
        assertEquals(active, flow.update(delayed, plans(), now).episodeId)
    }

    @Test fun dismissAndCooldownNeverReofferSameCareAnchor() {
        val flow = coordinator()
        val first = assessment()
        flow.update(first, plans(), now)
        flow.finish(first, now, ResponseNotice.CANCELLED)
        // At +30m sleep/seat become new imminent needs; isolate the already-seen feed anchor here.
        val laterFeed = assessment(at = now + 30 * 60_000).copy(imminentTokens = assessment(at = now + 30 * 60_000).imminentTokens.filter { it.startsWith("FEED:") }.toSet())
        assertEquals(first.imminentTokens, laterFeed.imminentTokens)
        assertEquals(ResponsePhase.IDLE, flow.update(laterFeed, plans(), now + 30 * 60_000).phase)
        assertTrue(flow.state.consumedTokens.isNotEmpty())
    }

    @Test fun newCareAnchorWaitsForQuietPeriodButManualStartStillWorks() {
        val flow = coordinator()
        flow.update(assessment(), plans(), now)
        flow.finish(assessment(), now, ResponseNotice.CANCELLED)
        val newAnchor = assessment().copy(imminentTokens = setOf("FEED:new-anchor"))
        assertEquals(ResponsePhase.IDLE, flow.update(newAnchor, plans(), now + 1).phase)
        assertEquals(ResponsePhase.ACTIVE, flow.start(newAnchor, now + 1).phase)
        flow.finish(newAnchor, now + 1, ResponseNotice.CANCELLED)
        val another = newAnchor.copy(imminentTokens = setOf("FEED:another-anchor"))
        assertEquals(ResponsePhase.SUGGESTED, flow.update(another, plans(), now + 1 + CryResponseCoordinator.QUIET_MILLIS).phase)
    }

    @Test fun disappearingForecastWithdrawsOfferWithoutEnteringResponse() {
        val flow = coordinator()
        val offered = flow.update(assessment(), plans(), now)
        val missing = assessment().copy(imminentTokens = emptySet())
        assertEquals(ResponsePhase.IDLE, flow.update(missing, plans(), now).phase)
        assertEquals(ResponsePhase.IDLE, flow.confirm(offered.episodeId!!).phase)
        assertEquals(ResponsePhase.IDLE, flow.update(assessment(), plans(), now).phase)
    }

    @Test fun firstDayAndMissingProfileSupportManualResponseWithoutInventingTimes() {
        val firstDay = assessment(DepartureDemo.create(now, true))
        assertTrue(firstDay.imminentTokens.isEmpty())
        assertTrue(firstDay.evidence.all { it.estimate == null && it.elapsedMinutes == null })
        val flow = coordinator()
        assertEquals(ResponsePhase.IDLE, flow.update(firstDay, plans(), now).phase)
        assertEquals(ResponsePhase.ACTIVE, flow.start(firstDay, now).phase)
        val empty = ResponseAssessor.assess(context.copy(babyId = null), null, null, null, now)
        assertTrue(empty.evidence.all { it.stage == ForecastStage.UNKNOWN })
        assertTrue(empty.limited)
    }

    @Test fun evidenceRetainsExactCareAnchorsAndCaregiverConfirmedSession() {
        val values = assessment().evidence.associateBy { it.need }
        assertEquals(110L, values[CareNeed.FEED]!!.elapsedMinutes)
        assertEquals(90L, values[CareNeed.SLEEP]!!.elapsedMinutes)
        assertEquals(100L, values[CareNeed.SEATED_BREAK]!!.elapsedMinutes)
        assertEquals(ForecastStage.IMMINENT, values[CareNeed.FEED]!!.stage)
        assertEquals(now - 110 * 60_000, values[CareNeed.FEED]!!.estimate!!.anchorAtEpochMillis)
    }

    @Test fun futureStaleMixedAndForeignDataCannotBecomePersonalEvidence() {
        val future = demo.copy(care = demo.care.copy(events = demo.care.events.map {
            if (it.type == CareEventType.FEED) it.copy(occurredAtEpochMillis = now + 1) else it
        }))
        assertNull(assessment(future).evidence.first { it.need == CareNeed.FEED }.lastAtEpochMillis)
        val stale = assessment(at = now + 2 * 86_400_000)
        assertTrue(stale.imminentTokens.isEmpty())
        assertTrue(stale.evidence.all { it.lastAtEpochMillis == null })
        val forecast = DeparturePlanner().evaluate(demo.care, demo.preparation, demo.journey(now), now).forecast
        val foreign = ResponseAssessor.assess(context.copy(isDemo = false), demo.care, forecast, demo.preparation.driveSession, now)
        assertTrue(foreign.evidence.all { it.estimate == null && it.lastAtEpochMillis == null })
        val mixed = demo.care.copy(events = demo.care.events.map { it.copy(source = CareEntrySource.CAREGIVER) })
        assertTrue(ResponseAssessor.assess(context, mixed, forecast, demo.preparation.driveSession, now).imminentTokens.isEmpty())
        assertTrue(ResponseAssessor.assess(context.copy(babyId = "other-baby"), demo.care, forecast, null, now).imminentTokens.isEmpty())
    }

    @Test fun staleEngineOutputIsNotRenewedByNewScreenTimestamp() {
        val forecast = DeparturePlanner().evaluate(demo.care, demo.preparation, demo.journey(now), now).forecast
        val old = ResponseAssessor.assess(context, demo.care, forecast, demo.preparation.driveSession, now + 60_000)
        assertTrue(old.imminentTokens.isEmpty())
        assertTrue(old.evidence.all { it.estimate == null })
    }

    @Test fun fullCareAndNearestSelectionsHaveDifferentFacilitiesAndRemainDemo() {
        val flow = coordinator()
        flow.start(assessment(), now)
        assertEquals("demo-full-care", flow.select(StopPlanType.FULL_CARE, plans(), now).selected!!.candidate.facility.id)
        assertEquals("demo-shelter", flow.select(StopPlanType.NEAREST_SAFE_STOP, plans(), now).selected!!.candidate.facility.id)
        assertFalse(flow.state.selected!!.candidate.canRequestNavigation)
        assertEquals(ResponsePhase.STOP_SELECTED, flow.start(assessment(), now).phase)
        assertNotNull(flow.state.selected)
    }

    @Test fun expiredFutureForeignOrMissingApproachCannotBeSelected() {
        for (at in listOf(now + 60_000, now - 1)) {
            val flow = coordinator(); flow.start(assessment(), now)
            assertNull(flow.select(StopPlanType.FULL_CARE, plans(), at).selected)
        }
        val flow = coordinator(); flow.start(assessment(), now)
        assertNull(flow.select(StopPlanType.FULL_CARE, plans().copy(isDemo = false), now).selected)
        assertNull(flow.select(StopPlanType.FULL_CARE, null, now).selected)
    }

    @Test fun changedRouteOrMissingFacilityClearsSelectionButKeepsResponseOpen() {
        val flow = coordinator(); flow.start(assessment(), now)
        flow.select(StopPlanType.FULL_CARE, plans(), now)
        val result = flow.update(assessment(), plans().copy(journeyId = "other-route"), now)
        assertEquals(ResponsePhase.ACTIVE, result.phase)
        assertNull(result.selected)
        assertEquals(ResponseNotice.STOP_EXPIRED, result.notice)
    }

    @Test fun newOutingBabyOrRealDemoContextEndsOldResponse() {
        for (ctx in listOf(context.copy(outingId = "new-outing"), context.copy(babyId = "other-baby"), context.copy(isDemo = false))) {
            val flow = coordinator(); flow.start(assessment(), now)
            val next = assessment().copy(context = ctx, imminentTokens = emptySet())
            assertEquals(ResponsePhase.IDLE, flow.update(next, null, now).phase)
            assertNull(flow.state.episodeId)
        }
    }

    @Test fun changedProviderPoiAtTheSameRoadPointInvalidatesOldSelection() {
        val flow = coordinator(); flow.start(assessment(), now)
        val before = plans()
        flow.select(StopPlanType.FULL_CARE, before, now)
        val changed = before.copy(fullCare = before.fullCare.copy(candidates = before.fullCare.candidates.map {
            it.copy(navigationPoiId = "new-provider-identity")
        }))
        val result = flow.update(assessment(), changed, now)
        assertEquals(ResponsePhase.ACTIVE, result.phase)
        assertNull(result.selected)
        assertEquals(ResponseNotice.STOP_EXPIRED, result.notice)
    }

    @Test fun recordingFailureKeepsResponseOpenAndConfirmedCareReturnsToIdle() {
        val flow = coordinator(); flow.start(assessment(), now)
        assertTrue(flow.recordFailed().active)
        assertEquals(ResponseNotice.RECORD_FAILED, flow.state.notice)
        assertEquals(ResponsePhase.IDLE, flow.finish(assessment(), now, ResponseNotice.CARE_RECORDED).phase)
        assertEquals(ResponseNotice.CARE_RECORDED, flow.state.notice)
        assertEquals(ResponsePhase.IDLE, flow.update(assessment(), plans(), now).phase)
    }

    @Test fun visitingDemoOrAnotherContextDoesNotForgetAlreadyDismissedRealEpisode() {
        val flow = coordinator()
        flow.update(assessment(), plans(), now)
        flow.finish(assessment(), now, ResponseNotice.CANCELLED)
        val other = assessment().copy(context = context.copy(outingId = "another-context"), imminentTokens = emptySet())
        flow.update(other, null, now)
        assertEquals(ResponsePhase.IDLE, flow.update(assessment(), plans(), now + CryResponseCoordinator.QUIET_MILLIS).phase)
    }

    @Test fun manualCompletionDoesNotCreateCareEventsOrChangeTheirSource() {
        val original = demo.care
        val flow = coordinator(); flow.start(assessment(), now)
        flow.finish(assessment(), now, ResponseNotice.CARE_CONFIRMED)
        assertEquals(ResponseNotice.CARE_CONFIRMED, flow.state.notice)
        assertEquals(original, demo.care)
        assertTrue(demo.care.events.all { it.source == CareEntrySource.DEMO })
    }

    @Test fun noFacilitiesAndFailedNavigationRemainUsableWithoutCandidates() {
        val catalog = DemoStopScenario.create(now).facilities
        val empty = StopPlanner().plan(demo.journey(now), catalog.copy(value = catalog.readyValue!!.copy(entries = emptyList())), demo.evidence(now), now)
        val failedDemo = DepartureDemo.response(now, DepartureDemoSituation.NAVIGATION_FAILED)
        val failed = StopPlanner().plan(failedDemo.journey(now), catalog, demo.evidence(now), now)
        assertNull(assessment(failedDemo).arrivalAtEpochMillis)
        for (result in listOf(empty, failed)) {
            val flow = coordinator(); flow.start(assessment(), now)
            assertTrue(flow.select(StopPlanType.NEAREST_SAFE_STOP, result, now).active)
            assertNull(flow.state.selected)
            assertEquals(ResponsePhase.IDLE, flow.finish(assessment(), now, ResponseNotice.CANCELLED).phase)
        }
    }
}
