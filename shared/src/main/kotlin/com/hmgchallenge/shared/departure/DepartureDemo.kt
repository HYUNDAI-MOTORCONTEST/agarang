package com.hmgchallenge.shared.departure

import com.hmgchallenge.shared.care.BabyProfile
import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.care.CareEvent
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareJournal
import com.hmgchallenge.shared.care.CareNotice
import com.hmgchallenge.shared.care.CarePreferences
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.DemoStopScenario
import com.hmgchallenge.shared.facility.RouteFacilityEvidence
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot
import com.hmgchallenge.shared.journey.DriveSession

enum class DepartureDemoSituation { NORMAL, NO_FACILITIES, NAVIGATION_FAILED }

/** The anchor stays fixed throughout a demo; ticks never invent fresh care events. */
data class DepartureDemo(
    val anchorEpochMillis: Long,
    val care: CareRecord,
    val preparation: DepartureRecord,
    val delayMinutes: Int = 0,
    val revision: Long = 0,
    val careNotice: CareNotice? = null,
    val situation: DepartureDemoSituation = DepartureDemoSituation.NORMAL,
) {
    init {
        require(anchorEpochMillis in 1_800_000..Long.MAX_VALUE - 86_400_000 && delayMinutes in 0..180)
        require(care.profile.source == CareEntrySource.DEMO && care.events.all { it.source == CareEntrySource.DEMO })
        require(preparation.origin == CareEntrySource.DEMO)
    }
    fun journey(now: Long): DataSnapshot<JourneySnapshot> = if (situation == DepartureDemoSituation.NAVIGATION_FAILED)
        DataSnapshot(DataOrigin.DEMO, DataStatus.FAILED) else DataSnapshot(DataOrigin.DEMO, DataStatus.READY,
        JourneySnapshot("demo-departure-$anchorEpochMillis", "데모 목적지", anchorEpochMillis + (60L + delayMinutes) * 60_000,
            50_000, GeoPoint(0.0, 0.0)), now)

    fun evidence(now: Long): DataSnapshot<RouteFacilityEvidence> {
        val value = DemoStopScenario.create(now).evidence
        return value.copy(value = value.readyValue!!.copy(journeyId = journey(now).readyValue!!.id))
    }

    fun record(type: CareEventType, now: Long, id: String): DepartureDemo = copy(care = CareJournal.append(care,
        CareEvent(id, care.profile.id, type, now,
            feedAmountMl = if (type == CareEventType.FEED) care.profile.preferences.preferredFeedAmountMl else null,
            source = CareEntrySource.DEMO), expectedSource = CareEntrySource.DEMO),
        revision = revision + 1, careNotice = CareNotice.RECORDED)

    companion object {
        fun response(now: Long, situation: DepartureDemoSituation = DepartureDemoSituation.NORMAL): DepartureDemo {
            require(now >= 180 * 60_000L)
            val base = create(now)
            val profile = base.care.profile.copy(preferences = CarePreferences(feedIntervalMinutes = 120, awakeWindowMinutes = 120, seatedBreakMinutes = 120))
            val events = listOf(
                CareEvent("demo-response-wake", profile.id, CareEventType.SLEEP_END, now - 90 * 60_000L, source = CareEntrySource.DEMO),
                CareEvent("demo-response-feed", profile.id, CareEventType.FEED, now - 110 * 60_000L, source = CareEntrySource.DEMO),
                CareEvent("demo-response-sleep", profile.id, CareEventType.SLEEP_START, now - 150 * 60_000L, source = CareEntrySource.DEMO),
            )
            return base.copy(care = CareRecord(profile, events), preparation = base.preparation.copy(
                driveSession = DriveSession("demo-response-session", profile.id, now - 100 * 60_000L)), situation = situation)
        }

        fun create(now: Long, firstDay: Boolean = false): DepartureDemo {
            val profile = BabyProfile("demo-departure-baby", 2,
                if (firstDay) CarePreferences() else CarePreferences(feedIntervalMinutes = 120), CareEntrySource.DEMO)
            val events = if (firstDay) emptyList() else listOf(
                CareEvent("demo-departure-feed", profile.id, CareEventType.FEED, now - 1_800_000, source = CareEntrySource.DEMO),
            )
            return DepartureDemo(now, CareRecord(profile, events), DepartureRecord("demo-outing-$now",
                settings = if (firstDay) PackingSettings() else PackingSettings(feedIntervalMinutes = 120, diaperIntervalMinutes = 120), origin = CareEntrySource.DEMO))
        }
    }
}
