package com.hmgchallenge.shared.forecast

import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.journey.DriveSession
import com.hmgchallenge.shared.journey.JourneySnapshot
import kotlinx.serialization.Serializable

@Serializable
enum class ForecastStage { UNKNOWN, RELAXED, CAUTION, IMMINENT }

@Serializable
enum class ForecastBasis { CAREGIVER_SETTING, PERSONAL_HISTORY, DEMO_REFERENCE, INSUFFICIENT_DATA, REFERENCE_MODEL }

@Serializable
enum class CareNeed { FEED, DIAPER, SLEEP, SEATED_BREAK }

@Serializable
enum class ForecastReasonCode {
    NO_CARE_RECORDS, NO_TIMING_BASIS, ROUTE_UNAVAILABLE, STALE_ROUTE,
    FUTURE_CARE_RECORD, CARE_TIME_APPROACHING, CARE_TIME_PASSED, CARE_BEFORE_ARRIVAL,
    CARE_TIME_LATER, REFERENCE_RANGE_ONLY, MISSING_ANCHOR, INSUFFICIENT_HISTORY,
    STALE_CARE_RECORD, INVALID_SLEEP_SEQUENCE, SLEEP_IN_PROGRESS, SESSION_NOT_ACTIVE,
    FUTURE_DRIVE_SESSION, FUTURE_ROUTE_OBSERVATION, ETA_UNAVAILABLE, ETA_BEFORE_NOW,
    TIME_OUT_OF_RANGE, MIXED_CARE_SOURCES, ARRIVAL_SOON_WHILE_SLEEPING, ARRIVAL_BEFORE_CARE, CARE_AT_ARRIVAL,
}

@Serializable
enum class SuggestedAction {
    CHECK_BEFORE_DEPARTURE, CARE_THEN_DEPART, PLAN_STOP, CHECK_CARE, CONTINUE_JOURNEY,
    CHECK_CARE_AT_ARRIVAL, CHECK_SLEEP_AT_ARRIVAL,
}

/** A planning range, never a medically prescribed interval. */
@Serializable
data class IntervalRangeMinutes(val minimum: Int, val maximum: Int) {
    init { require(minimum > 0 && maximum >= minimum) }
}

@Serializable
enum class ReferenceGuidance { EARLY_MILK_FEEDING, RESPONSIVE_FEEDING_WITH_SOLIDS, INDIVIDUAL_ROUTINE, TRAVEL_BREAK }

@Serializable
data class ReferenceSummary(
    val guidance: ReferenceGuidance,
    val interval: IntervalRangeMinutes? = null,
    val sourceIds: List<String>,
) {
    init { require(sourceIds.isNotEmpty() && sourceIds.all { it.isNotBlank() }) }
}

@Serializable
data class CareTimeEstimate(
    val need: CareNeed,
    val expectedAtEpochMillis: Long,
    val basis: ForecastBasis,
    val earliestAtEpochMillis: Long = expectedAtEpochMillis,
    val latestAtEpochMillis: Long = expectedAtEpochMillis,
    val anchorAtEpochMillis: Long? = null,
    val historyIntervalCount: Int = 0,
) {
    init {
        require(expectedAtEpochMillis >= 0)
        require(basis != ForecastBasis.INSUFFICIENT_DATA)
        require(earliestAtEpochMillis in 0..expectedAtEpochMillis && latestAtEpochMillis >= expectedAtEpochMillis)
        require(anchorAtEpochMillis == null || anchorAtEpochMillis in 0..earliestAtEpochMillis)
        require(historyIntervalCount >= 0)
    }
}

/** Every need carries its own uncertainty; a global stage does not certify unobserved needs. */
@Serializable
data class NeedForecast(
    val need: CareNeed,
    val stage: ForecastStage,
    val basis: ForecastBasis,
    val reasons: List<ForecastReasonCode>,
    val interval: IntervalRangeMinutes? = null,
    val estimate: CareTimeEstimate? = null,
    val reference: ReferenceSummary? = null,
    val usesGeneralTiming: Boolean = false,
) {
    init {
        require(reasons.isNotEmpty())
        require(estimate == null || (estimate.need == need && estimate.basis == basis))
        require(estimate != null || stage == ForecastStage.UNKNOWN)
        require(!usesGeneralTiming || (estimate != null && reference?.interval != null))
    }
}

@Serializable
data class JourneyAssessment(
    val estimatedArrivalAtEpochMillis: Long? = null,
    val careBeforeArrival: List<CareNeed> = emptyList(),
    val arrivingSoon: Boolean = false,
    val recordedSleeping: Boolean = false,
) {
    init {
        require(estimatedArrivalAtEpochMillis == null || estimatedArrivalAtEpochMillis >= 0)
        require(estimatedArrivalAtEpochMillis != null || (careBeforeArrival.isEmpty() && !arrivingSoon))
    }
}

@Serializable
data class ForecastInput(
    val care: CareRecord,
    val evaluatedAtEpochMillis: Long,
    val journey: DataSnapshot<JourneySnapshot>,
    val driveSession: DriveSession? = null,
) {
    init {
        require(evaluatedAtEpochMillis >= 0)
        require(driveSession == null || driveSession.babyId == care.profile.id)
    }
}

/** UI·SDK와 독립적인 추정 결과다. 원인 확진이나 의학적인 발생 확률을 나타내지 않는다. */
@Serializable
data class ForecastResult(
    val evaluatedAtEpochMillis: Long,
    val stage: ForecastStage,
    val estimates: List<CareTimeEstimate>,
    val reasons: List<ForecastReasonCode>,
    val suggestedActions: List<SuggestedAction>,
    val modelVersion: String,
    val isDemo: Boolean,
    val needs: List<NeedForecast> = emptyList(),
    val journey: JourneyAssessment = JourneyAssessment(),
    val isLimited: Boolean = true,
    val policyVersion: String = "legacy",
    val referenceModelVersion: String = "legacy",
) {
    init {
        require(evaluatedAtEpochMillis >= 0 && modelVersion.isNotBlank())
        require(reasons.isNotEmpty() && suggestedActions.isNotEmpty())
        require(isDemo || estimates.none { it.basis == ForecastBasis.DEMO_REFERENCE })
        require(policyVersion.isNotBlank() && referenceModelVersion.isNotBlank())
        require(needs.map { it.need }.distinct().size == needs.size)
        require(needs.isEmpty() || estimates == needs.mapNotNull { it.estimate })
    }
}
