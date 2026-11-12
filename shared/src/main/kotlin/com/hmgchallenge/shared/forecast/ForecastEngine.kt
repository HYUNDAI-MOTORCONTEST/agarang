package com.hmgchallenge.shared.forecast

import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.care.CareEvent
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.journey.DriveSession
import com.hmgchallenge.shared.journey.JourneySnapshot

/** Pure planning rules. Explicit-input evaluation never reads the clock, network, or SDK. */
class ForecastEngine(
    private val policy: ForecastPolicy = ForecastPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun evaluate(care: CareRecord, journey: DataSnapshot<JourneySnapshot>, driveSession: DriveSession? = null): ForecastResult =
        evaluate(ForecastInput(care, clock(), journey, driveSession))

    fun evaluate(input: ForecastInput): ForecastResult {
        val now = input.evaluatedAtEpochMillis
        val care = input.care
        val reasons = linkedSetOf<ForecastReasonCode>()
        if (care.events.isEmpty()) reasons += ForecastReasonCode.NO_CARE_RECORDS
        if (care.events.any { it.source != care.profile.source }) reasons += ForecastReasonCode.MIXED_CARE_SOURCES
        // Do not learn from a demo event inserted into a caregiver's journal (or vice versa).
        val events = care.events.filter { it.source == care.profile.source }.sortedByDescending { it.occurredAtEpochMillis }
        if (events.any { it.occurredAtEpochMillis > now }) reasons += ForecastReasonCode.FUTURE_CARE_RECORD
        val demoCare = care.profile.source == CareEntrySource.DEMO
        val feedEvents = events.filter { it.type == CareEventType.FEED }
        val feeds = feedEvents.filter { it.occurredAtEpochMillis <= now }
        val feed = if (feedEvents.any { it.occurredAtEpochMillis > now }) {
            unknown(CareNeed.FEED, ForecastReasonCode.FUTURE_CARE_RECORD)
        } else {
            timedNeed(
                CareNeed.FEED, feeds.firstOrNull()?.occurredAtEpochMillis,
                care.profile.preferences.feedIntervalMinutes, historyDurations(feeds.asReversed(), now),
                AgeReferenceModel.feeding(care.profile.monthAge), false, now, demoCare,
            )
        }
        val sleepEvents = events.filter { it.type == CareEventType.SLEEP_START || it.type == CareEventType.SLEEP_END }
        val sleep = sleepForecast(sleepEvents, input, demoCare)
        val seat = seatForecast(input, demoCare)
        val needs = listOf(feed, sleep, seat)
        needs.forEach { reasons += it.reasons }
        val estimates = needs.mapNotNull { it.estimate }
        val eta = usableArrival(input, reasons)
        val beforeArrival = estimates.filter { eta != null && it.earliestAtEpochMillis < eta }.map { it.need }
        if (beforeArrival.isNotEmpty()) reasons += ForecastReasonCode.CARE_BEFORE_ARRIVAL
        else if (eta != null && estimates.isNotEmpty()) {
            reasons += if (estimates.any { it.earliestAtEpochMillis == eta }) ForecastReasonCode.CARE_AT_ARRIVAL
                else ForecastReasonCode.ARRIVAL_BEFORE_CARE
        }

        val sleeping = ForecastReasonCode.SLEEP_IN_PROGRESS in sleep.reasons
        val arrivingSoon = eta != null && eta - now <= minutes(policy.arrivalSoonMinutes)
        val stage = if (estimates.isEmpty()) ForecastStage.UNKNOWN else {
            val current = needs.maxBy { it.stage.ordinal }.stage
            if (beforeArrival.isNotEmpty() && current == ForecastStage.RELAXED) ForecastStage.CAUTION else current
        }
        val limited = eta == null || ForecastReasonCode.MIXED_CARE_SOURCES in reasons ||
            ForecastReasonCode.FUTURE_CARE_RECORD in reasons || needs.any {
            (it.stage == ForecastStage.UNKNOWN &&
                ForecastReasonCode.SLEEP_IN_PROGRESS !in it.reasons &&
                ForecastReasonCode.SESSION_NOT_ACTIVE !in it.reasons) || it.basis == ForecastBasis.REFERENCE_MODEL || it.usesGeneralTiming
        }
        val sessionActive = input.driveSession?.let { it.startedAtEpochMillis <= now && it.endedAtEpochMillis == null } == true
        val actions = linkedSetOf<SuggestedAction>()
        if (limited || stage == ForecastStage.UNKNOWN) {
            actions += if (sessionActive) SuggestedAction.CHECK_CARE else SuggestedAction.CHECK_BEFORE_DEPARTURE
        }
        if (estimates.any { it.earliestAtEpochMillis - now <= minutes(policy.imminentMinutes) }) {
            actions += SuggestedAction.CHECK_CARE
            actions += if (sessionActive) SuggestedAction.PLAN_STOP else SuggestedAction.CARE_THEN_DEPART
        }
        if (beforeArrival.isNotEmpty()) actions += SuggestedAction.PLAN_STOP
        if (eta != null && beforeArrival.isEmpty() && (estimates.isNotEmpty() || sleeping)) {
            actions += SuggestedAction.CHECK_CARE_AT_ARRIVAL
        }
        if (arrivingSoon && sleeping && beforeArrival.isEmpty()) {
            reasons += ForecastReasonCode.ARRIVAL_SOON_WHILE_SLEEPING
            actions += SuggestedAction.CHECK_SLEEP_AT_ARRIVAL
        }
        if (stage == ForecastStage.RELAXED && !limited) actions += SuggestedAction.CONTINUE_JOURNEY
        if (actions.isEmpty()) actions += SuggestedAction.CHECK_CARE

        return ForecastResult(
            evaluatedAtEpochMillis = now, stage = stage, estimates = estimates,
            reasons = reasons.toList(), suggestedActions = actions.toList(), modelVersion = MODEL_VERSION,
            isDemo = demoCare || care.events.any { it.source == CareEntrySource.DEMO } ||
                input.journey.origin == DataOrigin.DEMO || input.journey.value?.isSimulation == true,
            needs = needs, journey = JourneyAssessment(eta, beforeArrival, arrivingSoon, sleeping),
            isLimited = limited, policyVersion = policy.version, referenceModelVersion = AgeReferenceModel.VERSION,
        )
    }

    private fun sleepForecast(events: List<CareEvent>, input: ForecastInput, demo: Boolean): NeedForecast {
        val now = input.evaluatedAtEpochMillis
        if (events.any { it.occurredAtEpochMillis > now }) return unknown(CareNeed.SLEEP, ForecastReasonCode.FUTURE_CARE_RECORD)
        if (events.zipWithNext().any { (a, b) -> a.type == b.type }) {
            return unknown(CareNeed.SLEEP, ForecastReasonCode.INVALID_SLEEP_SEQUENCE)
        }
        val latest = events.firstOrNull()
        if (latest != null && now - latest.occurredAtEpochMillis > minutes(policy.maxAnchorAgeMinutes)) {
            return unknown(CareNeed.SLEEP, ForecastReasonCode.STALE_CARE_RECORD)
        }
        if (latest?.type == CareEventType.SLEEP_START) return unknown(CareNeed.SLEEP, ForecastReasonCode.SLEEP_IN_PROGRESS)
        val chronological = events.asReversed()
        val awakeDurations = chronological.zipWithNext().mapNotNull { (wake, sleep) ->
            if (wake.type == CareEventType.SLEEP_END && sleep.type == CareEventType.SLEEP_START &&
                wake.occurredAtEpochMillis >= historyStart(now)
            ) (sleep.occurredAtEpochMillis - wake.occurredAtEpochMillis).takeIf { it >= MINUTE_MILLIS } else null
        }.takeLast(policy.maximumHistoryIntervals)
        return timedNeed(
            CareNeed.SLEEP, latest?.occurredAtEpochMillis, input.care.profile.preferences.awakeWindowMinutes,
            awakeDurations, null, false, now, demo,
        )
    }

    private fun seatForecast(input: ForecastInput, demo: Boolean): NeedForecast {
        val session = input.driveSession ?: return unknown(CareNeed.SEATED_BREAK, ForecastReasonCode.SESSION_NOT_ACTIVE)
        if (session.startedAtEpochMillis > input.evaluatedAtEpochMillis ||
            (session.endedAtEpochMillis ?: 0) > input.evaluatedAtEpochMillis
        ) return unknown(CareNeed.SEATED_BREAK, ForecastReasonCode.FUTURE_DRIVE_SESSION)
        if (session.endedAtEpochMillis != null) return unknown(CareNeed.SEATED_BREAK, ForecastReasonCode.SESSION_NOT_ACTIVE)
        return timedNeed(
            CareNeed.SEATED_BREAK, session.startedAtEpochMillis, input.care.profile.preferences.seatedBreakMinutes,
            emptyList(), AgeReferenceModel.travelBreak(input.care.profile.monthAge), true,
            input.evaluatedAtEpochMillis, demo,
        )
    }

    private data class Timing(
        val interval: IntervalRangeMinutes,
        val typicalMillis: Long,
        val basis: ForecastBasis,
        val count: Int = 0,
    )

    private fun timedNeed(
        need: CareNeed,
        anchor: Long?,
        configuredMinutes: Int?,
        history: List<Long>,
        reference: ReferenceSummary?,
        allowReferenceTiming: Boolean,
        now: Long,
        demo: Boolean,
    ): NeedForecast {
        val timing = when {
            configuredMinutes != null -> Timing(IntervalRangeMinutes(configuredMinutes, configuredMinutes), minutes(configuredMinutes), ForecastBasis.CAREGIVER_SETTING)
            history.size >= policy.minimumHistoryIntervals -> {
                val sorted = history.sorted()
                val lower = (sorted.first() / MINUTE_MILLIS).toInt()
                val upper = (sorted.last() / MINUTE_MILLIS + if (sorted.last() % MINUTE_MILLIS > 0) 1 else 0).toInt()
                val midpoint = sorted.size / 2
                val median = if (sorted.size % 2 == 1) sorted[midpoint] else sorted[midpoint - 1] + (sorted[midpoint] - sorted[midpoint - 1]) / 2
                Timing(IntervalRangeMinutes(lower, upper), median, ForecastBasis.PERSONAL_HISTORY, sorted.size)
            }
            allowReferenceTiming && reference?.interval != null -> Timing(
                reference.interval, (minutes(reference.interval.minimum) + minutes(reference.interval.maximum)) / 2,
                ForecastBasis.REFERENCE_MODEL,
            )
            else -> null
        }
        val basis = if (demo && timing != null) ForecastBasis.DEMO_REFERENCE else timing?.basis
            ?: if (reference != null) ForecastBasis.REFERENCE_MODEL else ForecastBasis.INSUFFICIENT_DATA
        val interval = timing?.interval ?: reference?.interval
        fun missing(vararg why: ForecastReasonCode) = NeedForecast(need, ForecastStage.UNKNOWN, basis, why.toList(), interval, reference = reference)
        if (anchor == null) return missing(ForecastReasonCode.MISSING_ANCHOR, ForecastReasonCode.NO_TIMING_BASIS)
        if (now - anchor > minutes(policy.maxAnchorAgeMinutes)) return missing(ForecastReasonCode.STALE_CARE_RECORD)
        if (timing == null) return missing(
            ForecastReasonCode.NO_TIMING_BASIS,
            if (reference != null) ForecastReasonCode.REFERENCE_RANGE_ONLY else ForecastReasonCode.INSUFFICIENT_HISTORY,
        )
        val earliest = addTime(anchor, minutes(timing.interval.minimum)) ?: return missing(ForecastReasonCode.TIME_OUT_OF_RANGE)
        val expected = addTime(anchor, timing.typicalMillis) ?: return missing(ForecastReasonCode.TIME_OUT_OF_RANGE)
        val latest = addTime(anchor, minutes(timing.interval.maximum)) ?: return missing(ForecastReasonCode.TIME_OUT_OF_RANGE)
        val stage = when {
            earliest - now <= minutes(policy.imminentMinutes) -> ForecastStage.IMMINENT
            earliest - now <= minutes(policy.cautionMinutes) -> ForecastStage.CAUTION
            else -> ForecastStage.RELAXED
        }
        val reason = when {
            earliest <= now -> ForecastReasonCode.CARE_TIME_PASSED
            stage != ForecastStage.RELAXED -> ForecastReasonCode.CARE_TIME_APPROACHING
            else -> ForecastReasonCode.CARE_TIME_LATER
        }
        return NeedForecast(
            need, stage, basis, listOf(reason), timing.interval,
            CareTimeEstimate(need, expected, basis, earliest, latest, anchor, timing.count), reference,
            usesGeneralTiming = timing.basis == ForecastBasis.REFERENCE_MODEL,
        )
    }

    private fun usableArrival(input: ForecastInput, reasons: MutableSet<ForecastReasonCode>): Long? {
        val snapshot = input.journey
        fun unavailable(reason: ForecastReasonCode): Long? { reasons += reason; return null }
        if (snapshot.status == DataStatus.STALE) return unavailable(ForecastReasonCode.STALE_ROUTE)
        if (snapshot.status != DataStatus.READY || snapshot.origin !in setOf(DataOrigin.NAVI_SDK, DataOrigin.DEMO)) {
            return unavailable(ForecastReasonCode.ROUTE_UNAVAILABLE)
        }
        val observed = snapshot.observedAtEpochMillis!!
        if (observed > input.evaluatedAtEpochMillis) return unavailable(ForecastReasonCode.FUTURE_ROUTE_OBSERVATION)
        if (input.evaluatedAtEpochMillis - observed > minutes(policy.maxRouteAgeMinutes)) return unavailable(ForecastReasonCode.STALE_ROUTE)
        val eta = snapshot.readyValue?.estimatedArrivalAtEpochMillis ?: return unavailable(ForecastReasonCode.ETA_UNAVAILABLE)
        if (eta < input.evaluatedAtEpochMillis) return unavailable(ForecastReasonCode.ETA_BEFORE_NOW)
        return eta
    }

    private fun historyStart(now: Long) = (now - minutes(policy.historyLookbackMinutes)).coerceAtLeast(0)

    private fun historyDurations(chronological: List<CareEvent>, now: Long): List<Long> = chronological
        .filter { it.occurredAtEpochMillis >= historyStart(now) }.zipWithNext()
        .map { (a, b) -> b.occurredAtEpochMillis - a.occurredAtEpochMillis }
        .filter { it >= MINUTE_MILLIS }.takeLast(policy.maximumHistoryIntervals)

    private fun unknown(need: CareNeed, reason: ForecastReasonCode) =
        NeedForecast(need, ForecastStage.UNKNOWN, ForecastBasis.INSUFFICIENT_DATA, listOf(reason))

    private fun addTime(anchor: Long, duration: Long): Long? = if (anchor > Long.MAX_VALUE - duration) null else anchor + duration

    private fun minutes(value: Int) = value.toLong() * MINUTE_MILLIS

    companion object {
        const val MODEL_VERSION = "care-forecast-v1"
        private const val MINUTE_MILLIS = 60_000L
    }
}
