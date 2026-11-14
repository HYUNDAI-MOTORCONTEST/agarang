package com.hmgchallenge.shared.response

import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareJournal
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.facility.RouteAccess
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.facility.StopPlanType
import com.hmgchallenge.shared.facility.StopPlanningResult
import com.hmgchallenge.shared.facility.StopPlanningProblem
import com.hmgchallenge.shared.forecast.CareNeed
import com.hmgchallenge.shared.forecast.CareTimeEstimate
import com.hmgchallenge.shared.forecast.ForecastBasis
import com.hmgchallenge.shared.forecast.ForecastReasonCode
import com.hmgchallenge.shared.forecast.ForecastResult
import com.hmgchallenge.shared.forecast.ForecastStage
import com.hmgchallenge.shared.journey.DriveSession
import java.util.UUID

data class ResponseContext(val outingId: String, val babyId: String?, val isDemo: Boolean) {
    init { require(outingId.isNotBlank() && babyId?.isNotBlank() != false) }
}

/** Evidence to check, not ranked diagnoses, probabilities or a microphone detection result. */
data class ResponseEvidence(
    val need: CareNeed,
    val stage: ForecastStage,
    val basis: ForecastBasis,
    val lastAtEpochMillis: Long?,
    val elapsedMinutes: Long?,
    val estimate: CareTimeEstimate?,
    val reasons: List<ForecastReasonCode>,
    val recordedSleeping: Boolean = false,
)

data class ResponseAssessment(
    val context: ResponseContext,
    val evidence: List<ResponseEvidence>,
    val imminentTokens: Set<String>,
    val arrivalAtEpochMillis: Long?,
    val careBeforeArrival: Boolean,
    val limited: Boolean,
)

object ResponseAssessor {
    fun assess(context: ResponseContext, care: CareRecord?, forecast: ForecastResult?, session: DriveSession?, now: Long): ResponseAssessment {
        require(now >= 0)
        val expected = if (context.isDemo) CareEntrySource.DEMO else CareEntrySource.CAREGIVER
        val validCare = care?.takeIf { it.profile.id == context.babyId }?.let {
            try { CareJournal.normalize(it, expected) } catch (_: Exception) { null }
        }
        // Reject stale/foreign engine output rather than attaching a new timestamp to old evidence.
        val validForecast = forecast?.takeIf { validCare != null && it.isDemo == context.isDemo &&
            it.evaluatedAtEpochMillis <= now && now - it.evaluatedAtEpochMillis < 60_000 }
        val evidence = listOf(CareNeed.FEED, CareNeed.SLEEP, CareNeed.SEATED_BREAK).map { need ->
            val value = validForecast?.needs?.firstOrNull { it.need == need }
            val invalid = value?.reasons.orEmpty().any { it in setOf(ForecastReasonCode.FUTURE_CARE_RECORD,
                ForecastReasonCode.STALE_CARE_RECORD, ForecastReasonCode.INVALID_SLEEP_SEQUENCE,
                ForecastReasonCode.FUTURE_DRIVE_SESSION, ForecastReasonCode.MIXED_CARE_SOURCES) }
            val anchor = if (invalid || validCare == null) null else when (need) {
                CareNeed.FEED -> validCare?.events?.firstOrNull { it.type == CareEventType.FEED }?.occurredAtEpochMillis
                CareNeed.SLEEP -> validCare?.events?.firstOrNull { it.type in listOf(CareEventType.SLEEP_START, CareEventType.SLEEP_END) }?.occurredAtEpochMillis
                CareNeed.SEATED_BREAK -> session?.takeIf { it.babyId == context.babyId && it.endedAtEpochMillis == null }?.startedAtEpochMillis
                else -> null
            }?.takeIf { it <= now && now - it <= 86_400_000 }
            ResponseEvidence(need, value?.stage ?: ForecastStage.UNKNOWN, value?.basis ?: ForecastBasis.INSUFFICIENT_DATA,
                anchor, anchor?.let { (now - it) / 60_000 }, value?.estimate,
                value?.reasons ?: listOf(ForecastReasonCode.NO_TIMING_BASIS),
                need == CareNeed.SLEEP && validForecast?.journey?.recordedSleeping == true)
        }
        val tokens = evidence.filter { it.stage == ForecastStage.IMMINENT && it.estimate?.anchorAtEpochMillis != null }
            .map { "${it.need}:${it.estimate!!.anchorAtEpochMillis}:${it.estimate.earliestAtEpochMillis}" }.toSet()
        return ResponseAssessment(context, evidence, tokens, validForecast?.journey?.estimatedArrivalAtEpochMillis,
            validForecast?.journey?.careBeforeArrival?.isNotEmpty() == true, validForecast?.isLimited != false)
    }
}

enum class ResponsePhase { IDLE, SUGGESTED, ACTIVE, STOP_SELECTED }
enum class ResponseNotice { CANCELLED, CARE_RECORDED, CARE_CONFIRMED, RECORD_FAILED, STOP_EXPIRED }
data class ResponseStop(val type: StopPlanType, val candidate: StopCandidate)
data class ResponseState(
    val context: ResponseContext? = null,
    val phase: ResponsePhase = ResponsePhase.IDLE,
    val episodeId: String? = null,
    val offeredTokens: Set<String> = emptySet(),
    val consumedTokens: Set<String> = emptySet(),
    val quietUntilEpochMillis: Long? = null,
    val selected: ResponseStop? = null,
    val notice: ResponseNotice? = null,
) {
    val active get() = phase == ResponsePhase.ACTIVE || phase == ResponsePhase.STOP_SELECTED
}

/** Foreground, silent, in-app suggestions. One offer per care anchor; no ETA-dependent reminder loop. */
class CryResponseCoordinator(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private val history = linkedMapOf<ResponseContext, ResponseState>()
    var state = ResponseState()
        private set

    fun update(assessment: ResponseAssessment, plans: StopPlanningResult?, now: Long): ResponseState {
        require(now >= 0)
        if (state.context != assessment.context) {
            state.context?.let { old ->
                history[old] = state.copy(phase = ResponsePhase.IDLE, episodeId = null, selected = null,
                    consumedTokens = consumed(state.offeredTokens), offeredTokens = emptySet(), notice = null)
                while (history.size > 8) history.remove(history.keys.first())
            }
            state = history[assessment.context] ?: ResponseState(context = assessment.context)
        }
        if (state.selected != null && !selectionCurrent(state.selected!!, plans, assessment.context, now)) {
            state = state.copy(phase = ResponsePhase.ACTIVE, selected = null, notice = ResponseNotice.STOP_EXPIRED)
        }
        if (state.phase == ResponsePhase.SUGGESTED && state.offeredTokens.intersect(assessment.imminentTokens).isEmpty()) {
            state = state.copy(phase = ResponsePhase.IDLE, episodeId = null, consumedTokens = consumed(state.offeredTokens), offeredTokens = emptySet())
        }
        val quiet = state.quietUntilEpochMillis?.let { now < it } == true
        val fresh = assessment.imminentTokens - state.consumedTokens
        if (state.phase == ResponsePhase.IDLE && !quiet && fresh.isNotEmpty()) {
            state = state.copy(phase = ResponsePhase.SUGGESTED, episodeId = newId(), offeredTokens = assessment.imminentTokens, notice = null)
        }
        return state
    }

    fun start(assessment: ResponseAssessment, now: Long): ResponseState {
        if (state.context == assessment.context && state.active) return state
        update(assessment, null, now)
        state = state.copy(phase = ResponsePhase.ACTIVE, episodeId = newId(), offeredTokens = emptySet(),
            consumedTokens = consumed(assessment.imminentTokens), selected = null, notice = null)
        return state
    }

    fun confirm(episodeId: String): ResponseState {
        if (state.phase == ResponsePhase.SUGGESTED && state.episodeId == episodeId) {
            state = state.copy(phase = ResponsePhase.ACTIVE, consumedTokens = consumed(state.offeredTokens), offeredTokens = emptySet(), notice = null)
        }
        return state
    }

    fun select(type: StopPlanType, plans: StopPlanningResult?, now: Long): ResponseState {
        if (!state.active || plans == null) return state
        val plan = if (type == StopPlanType.FULL_CARE) plans.fullCare else plans.nearestSafeStop
        val selection = plan.candidates.firstOrNull()?.let { ResponseStop(type, it) } ?: return state
        val context = state.context ?: return state
        if (!selectionCurrent(selection, plans, context, now)) return state
        state = state.copy(phase = ResponsePhase.STOP_SELECTED, selected = selection, notice = null)
        return state
    }

    fun finish(assessment: ResponseAssessment, now: Long, notice: ResponseNotice): ResponseState {
        require(now >= 0)
        if (state.context != assessment.context || state.phase == ResponsePhase.IDLE) return state
        require(notice in listOf(ResponseNotice.CANCELLED, ResponseNotice.CARE_RECORDED, ResponseNotice.CARE_CONFIRMED))
        val quietUntil = if (now > Long.MAX_VALUE - QUIET_MILLIS) Long.MAX_VALUE else now + QUIET_MILLIS
        state = state.copy(phase = ResponsePhase.IDLE, episodeId = null, selected = null,
            consumedTokens = consumed(state.offeredTokens + assessment.imminentTokens), offeredTokens = emptySet(),
            quietUntilEpochMillis = quietUntil, notice = notice)
        return state
    }

    fun recordFailed(): ResponseState {
        if (state.active) state = state.copy(notice = ResponseNotice.RECORD_FAILED)
        return state
    }

    private fun consumed(tokens: Set<String>) = (state.consumedTokens + tokens).toList().takeLast(64).toSet()

    companion object {
        // Product reminder policy, not a medical waiting period. Manual opening remains available.
        const val QUIET_MILLIS = 20 * 60_000L
        fun selectionCurrent(selection: ResponseStop, plans: StopPlanningResult?, context: ResponseContext, now: Long): Boolean {
            if (plans == null || plans.problem != StopPlanningProblem.NONE || plans.isDemo != context.isDemo) return false
            val candidates = if (selection.type == StopPlanType.FULL_CARE) plans.fullCare.candidates else plans.nearestSafeStop.candidates
            val current = candidates.firstOrNull { it.facility.id == selection.candidate.facility.id } ?: return false
            val verified = current.accessVerifiedAtEpochMillis ?: return false
            return current.journeyId == plans.journeyId && current.journeyId == selection.candidate.journeyId &&
                current.navigationLocation == selection.candidate.navigationLocation && current.origin == selection.candidate.origin &&
                current.navigationPoiId == selection.candidate.navigationPoiId &&
                current.routeAccess == RouteAccess.CONFIRMED_ON_ROUTE && now >= verified && now - verified < 60_000 &&
                (if (context.isDemo) current.origin == com.hmgchallenge.shared.data.DataOrigin.DEMO else current.canRequestNavigation)
        }
    }
}
