package com.hmgchallenge.shared.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class DataOrigin { CAREGIVER, VEHICLE_SDK, NAVI_SDK, PUBLIC_DATA, DEMO }

@Serializable
enum class DataStatus { READY, LOADING, EMPTY, UNSUPPORTED, PERMISSION_DENIED, FAILED, STALE }

/**
 * null은 0이나 빈 경로가 아니다. READY만 사용 가능한 최신 값이다.
 * STALE에는 마지막 캐시를 둘 수 있지만 현재 값으로 판단에 사용하지 않는다.
 * 오류 메시지·토큰·SDK 예외 전문 대신 status를 화면에서 현지화한다.
 */
@Serializable
data class DataSnapshot<T>(
    val origin: DataOrigin,
    val status: DataStatus,
    val value: T? = null,
    val observedAtEpochMillis: Long? = null,
) {
    init {
        require(observedAtEpochMillis == null || observedAtEpochMillis >= 0)
        require(status != DataStatus.READY || (value != null && observedAtEpochMillis != null))
        require(value == null || status == DataStatus.READY || status == DataStatus.STALE)
        require(value == null || observedAtEpochMillis != null)
    }

    val readyValue: T? get() = value.takeIf { status == DataStatus.READY }
}

/** SDK 콜백은 앱 어댑터가 StateFlow로 바꾼다. 엔진은 SDK·Android 객체를 알지 못한다. */
interface SnapshotSource<T> {
    val snapshots: StateFlow<DataSnapshot<T>>
}

/** 생성·갱신 모두 DEMO 출처를 강제해 실데이터로 잘못 표시하지 않는 시연 공급자. */
class DemoSnapshotSource<T>(initialValue: T, observedAtEpochMillis: Long) : SnapshotSource<T> {
    private val mutable = MutableStateFlow(
        DataSnapshot(DataOrigin.DEMO, DataStatus.READY, initialValue, observedAtEpochMillis),
    )
    override val snapshots: StateFlow<DataSnapshot<T>> = mutable.asStateFlow()

    fun publish(value: T, observedAtEpochMillis: Long) {
        mutable.value = DataSnapshot(DataOrigin.DEMO, DataStatus.READY, value, observedAtEpochMillis)
    }
}
