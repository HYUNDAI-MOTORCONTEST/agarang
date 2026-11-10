package com.hmgchallenge.shared.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.journey.GeoPoint
import kotlinx.serialization.Serializable

@Serializable
enum class FacilityType { REST_AREA, DROWSINESS_SHELTER }

@Serializable
enum class AmenityStatus { AVAILABLE, UNAVAILABLE, UNKNOWN }

@Serializable
data class CareFacility(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val type: FacilityType,
    val nursingRoom: AmenityStatus = AmenityStatus.UNKNOWN,
    val diaperChanging: AmenityStatus = AmenityStatus.UNKNOWN,
    val verifiedAtEpochMillis: Long? = null,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(verifiedAtEpochMillis == null || verifiedAtEpochMillis >= 0)
    }
}

@Serializable
enum class RouteAccess { CONFIRMED_ON_ROUTE, UNKNOWN, OPPOSITE_DIRECTION, INACCESSIBLE }

/** 직선거리를 주행 거리/도착 시간으로 바꾸지 않는다. 경로 제공자가 확인하지 못하면 null/UNKNOWN. */
@Serializable
data class StopCandidate(
    val facility: CareFacility,
    val origin: DataOrigin,
    val routeAccess: RouteAccess = RouteAccess.UNKNOWN,
    val travelDurationSeconds: Long? = null,
    val distanceAlongRouteMeters: Long? = null,
) {
    init {
        require(travelDurationSeconds == null || travelDurationSeconds >= 0)
        require(distanceAlongRouteMeters == null || distanceAlongRouteMeters >= 0)
    }

    /** Demo facilities must not be sent to real navigation services. */
    val canRequestNavigation: Boolean
        get() = origin != DataOrigin.DEMO && routeAccess == RouteAccess.CONFIRMED_ON_ROUTE
}

@Serializable
enum class StopPlanType { FULL_CARE, NEAREST_SAFE_STOP }

@Serializable
enum class StopPlanStatus { AVAILABLE, ROUTE_UNAVAILABLE, NO_CONFIRMED_CANDIDATE }

@Serializable
data class StopPlan(
    val type: StopPlanType,
    val status: StopPlanStatus,
    val candidates: List<StopCandidate> = emptyList(),
) {
    init {
        require((status == StopPlanStatus.AVAILABLE) == candidates.isNotEmpty())
        require(candidates.all { it.routeAccess == RouteAccess.CONFIRMED_ON_ROUTE })
        require(type != StopPlanType.FULL_CARE || candidates.all { it.facility.nursingRoom == AmenityStatus.AVAILABLE })
    }
}
