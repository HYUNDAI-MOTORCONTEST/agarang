package com.hmgchallenge.shared.care

import kotlinx.serialization.Serializable

/** 보호자가 정하는 간격이다. 미설정(null)을 월령별 의학적 권장값으로 채우지 않는다. */
@Serializable
data class CarePreferences(
    val feedIntervalMinutes: Int? = null,
    val awakeWindowMinutes: Int? = null,
    val seatedBreakMinutes: Int? = null,
    val preferredFeedAmountMl: Int? = null,
) {
    init {
        require(feedIntervalMinutes == null || feedIntervalMinutes > 0)
        require(awakeWindowMinutes == null || awakeWindowMinutes > 0)
        require(seatedBreakMinutes == null || seatedBreakMinutes > 0)
        require(preferredFeedAmountMl == null || preferredFeedAmountMl > 0)
    }
}

@Serializable
data class BabyProfile(
    val id: String,
    val monthAge: Int,
    val preferences: CarePreferences = CarePreferences(),
    val source: CareEntrySource = CareEntrySource.CAREGIVER,
) {
    init {
        require(id.isNotBlank())
        require(monthAge in 0..24)
    }
}

@Serializable
enum class CareEventType { FEED, DIAPER, SLEEP_START, SLEEP_END }

@Serializable
enum class CareEntrySource { CAREGIVER, DEMO }

/** 아기 상태의 센서 감지 결과가 아니라 보호자 기록(또는 표시된 데모 기록)이다. */
@Serializable
data class CareEvent(
    val id: String,
    val babyId: String,
    val type: CareEventType,
    val occurredAtEpochMillis: Long,
    val feedAmountMl: Int? = null,
    val source: CareEntrySource = CareEntrySource.CAREGIVER,
) {
    init {
        require(id.isNotBlank() && babyId.isNotBlank())
        require(occurredAtEpochMillis >= 0)
        require(feedAmountMl == null || (type == CareEventType.FEED && feedAmountMl > 0))
    }
}

/** 저장/전송 계약 v1. 읽기 전용 프로필 하나와 그 프로필에 속한 이벤트를 묶는다. */
@Serializable
data class CareRecord(
    val profile: BabyProfile,
    val events: List<CareEvent> = emptyList(),
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion == 1) { "Unsupported care record schema" }
        require(events.all { it.babyId == profile.id })
        require(events.map { it.id }.distinct().size == events.size) { "Duplicate care event ID" }
    }
}
