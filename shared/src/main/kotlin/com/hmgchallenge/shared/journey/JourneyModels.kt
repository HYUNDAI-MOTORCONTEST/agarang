package com.hmgchallenge.shared.journey

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val latitudeDegrees: Double, val longitudeDegrees: Double) {
    init {
        require(latitudeDegrees.isFinite() && latitudeDegrees in -90.0..90.0)
        require(longitudeDegrees.isFinite() && longitudeDegrees in -180.0..180.0)
    }
}

/** 어댑터에서 단위를 확인한 뒤 변환한다. SDK remainTime/eta 원시 숫자를 그대로 넣지 않는다. */
@Serializable
data class JourneySnapshot(
    val id: String,
    val destinationLabel: String,
    val estimatedArrivalAtEpochMillis: Long? = null,
    val remainingDistanceMeters: Long? = null,
    val destination: GeoPoint? = null,
    val isSimulation: Boolean = false,
) {
    init {
        require(id.isNotBlank() && destinationLabel.isNotBlank())
        require(estimatedArrivalAtEpochMillis == null || estimatedArrivalAtEpochMillis >= 0)
        require(remainingDistanceMeters == null || remainingDistanceMeters >= 0)
    }
}

/** 착석 시작은 보호자가 확인한 세션 기준이다. 차량 시동·좌석 센서 감지로 간주하지 않는다. */
@Serializable
data class DriveSession(
    val id: String,
    val babyId: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
) {
    init {
        require(id.isNotBlank() && babyId.isNotBlank())
        require(startedAtEpochMillis >= 0)
        require(endedAtEpochMillis == null || endedAtEpochMillis >= startedAtEpochMillis)
    }
}

/** 권한/capability가 확인된 어댑터만 채운다. 알 수 없는 값은 null이다. */
@Serializable
data class VehicleContext(
    val cabinTemperatureCelsius: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val steeringAngleDegrees: Double? = null,
) {
    init {
        require(cabinTemperatureCelsius == null || cabinTemperatureCelsius.isFinite())
        require(speedMetersPerSecond == null || (speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0))
        require(steeringAngleDegrees == null || steeringAngleDegrees.isFinite())
    }
}
