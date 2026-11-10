package com.hmgchallenge.shared.forecast

import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.journey.DriveSession
import com.hmgchallenge.shared.journey.JourneySnapshot
import kotlinx.serialization.Serializable

@Serializable
enum class ForecastStage { UNKNOWN, RELAXED, CAUTION, IMMINENT }

@Serializable
enum class ForecastBasis { CAREGIVER_SETTING, PERSONAL_HISTORY, DEMO_REFERENCE, INSUFFICIENT_DATA }

@Serializable
enum class CareNeed { FEED, DIAPER, SLEEP, SEATED_BREAK }

@Serializable
enum class ForecastReasonCode {
    NO_CARE_RECORDS, NO_TIMING_BASIS, ROUTE_UNAVAILABLE, STALE_ROUTE,
    FUTURE_CARE_RECORD, CARE_TIME_APPROACHING, CARE_TIME_PASSED, CARE_BEFORE_ARRIVAL,
}

@Serializable
enum class SuggestedAction { CHECK_BEFORE_DEPARTURE, CARE_THEN_DEPART, PLAN_STOP, CHECK_CARE, CONTINUE_JOURNEY }

@Serializable
data class CareTimeEstimate(
    val need: CareNeed,
    val expectedAtEpochMillis: Long,
    val basis: ForecastBasis,
) {
    init {
        require(expectedAtEpochMillis >= 0)
        require(basis != ForecastBasis.INSUFFICIENT_DATA)
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
) {
    init {
        require(evaluatedAtEpochMillis >= 0 && modelVersion.isNotBlank())
        require(reasons.isNotEmpty() && suggestedActions.isNotEmpty())
        require(isDemo || estimates.none { it.basis == ForecastBasis.DEMO_REFERENCE })
    }
}
