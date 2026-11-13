package com.hmgchallenge.shared.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot

/** Explicitly synthetic coordinates and road access. Never a fallback for a failed public provider. */
data class DemoStopScenario(
    val journey: DataSnapshot<JourneySnapshot>,
    val facilities: DataSnapshot<FacilityCatalog>,
    val evidence: DataSnapshot<RouteFacilityEvidence>,
) {
    companion object {
        fun create(now: Long): DemoStopScenario {
            require(now in 0..Long.MAX_VALUE - 3_600_000)
            val source = FacilityDataSource("demo-source", "아가랑 데모", "고정 경로 합성 시설",
                "https://example.invalid/agarang/demo", "개발용 합성 데이터", "데모 실행 시 생성")
            fun entry(id: String, label: String, type: FacilityType, nursing: AmenityStatus = AmenityStatus.UNKNOWN) = FacilityListing(
                CareFacility("demo-$id", "데모 $label", GeoPoint(0.0, 0.0), type, nursing, verifiedAtEpochMillis = now), source.id,
                roadRouteName = "데모 도로", directionLabel = "데모 목적지 방향",
            )
            val entries = listOf(
                entry("shelter", "졸음쉼터", FacilityType.DROWSINESS_SHELTER),
                entry("full-care", "수유실 휴게소", FacilityType.REST_AREA, AmenityStatus.AVAILABLE),
                entry("last-rest", "마지막 휴게소", FacilityType.REST_AREA),
                entry("opposite", "반대 방향 휴게소", FacilityType.REST_AREA, AmenityStatus.AVAILABLE),
                entry("inaccessible", "진입 불가 휴게소", FacilityType.REST_AREA, AmenityStatus.AVAILABLE),
            )
            val approaches = listOf(
                FacilityApproach("demo-shelter", RouteAccess.CONFIRMED_ON_ROUTE, 180, 2_500, GeoPoint(0.0, 0.0)),
                FacilityApproach("demo-full-care", RouteAccess.CONFIRMED_ON_ROUTE, 360, 5_000, GeoPoint(0.0, 0.0)),
                FacilityApproach("demo-last-rest", RouteAccess.CONFIRMED_ON_ROUTE, 480, 8_000, GeoPoint(0.0, 0.0)),
                FacilityApproach("demo-opposite", RouteAccess.OPPOSITE_DIRECTION, 10, 10, GeoPoint(0.0, 0.0)),
                FacilityApproach("demo-inaccessible", RouteAccess.INACCESSIBLE, 20, 20, GeoPoint(0.0, 0.0)),
            )
            val route = JourneySnapshot("demo-facility-route", "데모 목적지", now + 3_600_000, 50_000)
            return DemoStopScenario(
                DataSnapshot(DataOrigin.DEMO, DataStatus.READY, route, now),
                DataSnapshot(DataOrigin.DEMO, DataStatus.READY, FacilityCatalog(DataOrigin.DEMO, now, listOf(source), entries), now),
                DataSnapshot(DataOrigin.DEMO, DataStatus.READY, RouteFacilityEvidence(route.id, approaches, true), now),
            )
        }

        fun routeAccessCatalog(now: Long): DataSnapshot<RouteAccessCatalog> {
            require(now in 0..Long.MAX_VALUE - 3_600_000)
            val approaches = listOf(
                VerifiedCorridorApproach("demo-shelter", RouteAccess.CONFIRMED_ON_ROUTE, 47_500, 3_420, GeoPoint(0.0, 0.0)),
                VerifiedCorridorApproach("demo-full-care", RouteAccess.CONFIRMED_ON_ROUTE, 45_000, 3_240, GeoPoint(0.0, 0.0)),
                VerifiedCorridorApproach("demo-last-rest", RouteAccess.CONFIRMED_ON_ROUTE, 42_000, 3_120, GeoPoint(0.0, 0.0)),
                VerifiedCorridorApproach("demo-opposite", RouteAccess.OPPOSITE_DIRECTION, 49_990, 3_590, GeoPoint(0.0, 0.0)),
                VerifiedCorridorApproach("demo-inaccessible", RouteAccess.INACCESSIBLE, 49_980, 3_580, GeoPoint(0.0, 0.0)),
            )
            val corridor = VerifiedRouteCorridor(
                id = "demo-corridor",
                destinationLabel = "데모 목적지",
                destinationLocation = GeoPoint(0.0, 0.0),
                totalDistanceMeters = 50_000,
                totalDurationSeconds = 3_600,
                coversRemainingRestAreas = true,
                approaches = approaches,
            )
            return DataSnapshot(
                origin = DataOrigin.DEMO,
                status = DataStatus.READY,
                value = RouteAccessCatalog(DataOrigin.DEMO, now, listOf(corridor)),
                observedAtEpochMillis = now,
            )
        }
    }
}
