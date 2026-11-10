package com.hmgchallenge.shared.demo

import com.hmgchallenge.shared.care.BabyProfile
import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.care.CareEvent
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CarePreferences
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DemoSnapshotSource
import com.hmgchallenge.shared.facility.AmenityStatus
import com.hmgchallenge.shared.facility.CareFacility
import com.hmgchallenge.shared.facility.FacilityType
import com.hmgchallenge.shared.facility.RouteAccess
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.journey.DriveSession
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot

/**
 * F2·F4 첫 동선을 위한 합성 데이터. 의학적 기준·실제 시설·실제 경로가 아니다.
 * now를 주입하므로 기기 시간·네트워크 없이 시나리오가 재현된다.
 */
data class DemoJourney(
    val care: CareRecord,
    val driveSession: DriveSession,
    val journey: DemoSnapshotSource<JourneySnapshot>,
    val stops: DemoSnapshotSource<List<StopCandidate>>,
) {
    companion object {
        fun create(nowEpochMillis: Long): DemoJourney {
            // 합성 시나리오의 과거/미래 시각을 안전하게 만들 수 있는 범위.
            require(nowEpochMillis in 7_200_000..Long.MAX_VALUE - 7_200_000)
            val profile = BabyProfile(
                id = "demo-baby",
                monthAge = 2,
                preferences = CarePreferences(feedIntervalMinutes = 180, seatedBreakMinutes = 120),
                source = CareEntrySource.DEMO,
            )
            val care = CareRecord(
                profile,
                listOf(
                    CareEvent("demo-feed", profile.id, CareEventType.FEED, nowEpochMillis - 5_400_000, 120, CareEntrySource.DEMO),
                ),
            )
            val journey = JourneySnapshot("demo-route", "데모 목적지", nowEpochMillis + 7_200_000, 100_000)
            val restArea = CareFacility(
                "demo-full-care", "데모 수유시설", GeoPoint(0.0, 0.0), FacilityType.REST_AREA,
                nursingRoom = AmenityStatus.AVAILABLE,
                diaperChanging = AmenityStatus.AVAILABLE,
            )
            val shelter = CareFacility(
                "demo-safe-stop", "데모 안전 정차시설", GeoPoint(0.0, 0.0), FacilityType.DROWSINESS_SHELTER,
            )
            return DemoJourney(
                care = care,
                driveSession = DriveSession("demo-session", profile.id, nowEpochMillis - 3_600_000),
                journey = DemoSnapshotSource(journey, nowEpochMillis),
                stops = DemoSnapshotSource(
                    listOf(
                        StopCandidate(restArea, DataOrigin.DEMO, RouteAccess.CONFIRMED_ON_ROUTE, 360, 5_000),
                        StopCandidate(shelter, DataOrigin.DEMO, RouteAccess.CONFIRMED_ON_ROUTE, 180, 2_500),
                    ),
                    nowEpochMillis,
                ),
            )
        }
    }
}
