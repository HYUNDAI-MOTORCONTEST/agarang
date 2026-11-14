package com.hmgchallenge.shared.care

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CareContractsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val profile = BabyProfile("baby", 2)

    @Test
    fun recordRoundTripPreservesIdsAmountsSourcesAndTime() {
        val record = CareRecord(
            profile.copy(preferences = CarePreferences(feedIntervalMinutes = 180, preferredFeedAmountMl = 120)),
            listOf(CareEvent("event", profile.id, CareEventType.FEED, 1_000, 120)),
        )
        val encoded = json.encodeToString(CareRecord.serializer(), record)
        assertEquals(record, json.decodeFromString<CareRecord>(encoded))
    }

    @Test
    fun zeroRecordsAndUnconfiguredIntervalsRemainExplicit() {
        val decoded = json.decodeFromString<CareRecord>(
            """{"profile":{"id":"baby","monthAge":0},"futureField":true}""",
        )
        assertEquals(emptyList(), decoded.events)
        assertNull(decoded.profile.preferences.feedIntervalMinutes)
        assertNull(decoded.profile.preferences.awakeWindowMinutes)
        assertNull(decoded.profile.preferences.seatedBreakMinutes)
    }

    @Test
    fun invalidMonthAgeIsRejectedDuringDecode() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<BabyProfile>("""{"id":"baby","monthAge":25}""")
        }
        assertEquals(0, BabyProfile("newborn", 0).monthAge)
        assertEquals(24, BabyProfile("last-month", 24).monthAge)
    }

    @Test
    fun unsupportedSchemaIsNotSilentlyLoadedAsCurrent() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<CareRecord>(
                """{"profile":{"id":"baby","monthAge":2},"schemaVersion":2}""",
            )
        }
    }

    @Test
    fun recordsCannotMixBabiesOrDuplicateStableIds() {
        val event = CareEvent("event", profile.id, CareEventType.DIAPER, 1_000)
        assertFailsWith<IllegalArgumentException> { CareRecord(profile, listOf(event, event)) }
        assertFailsWith<IllegalArgumentException> { CareRecord(profile, listOf(event.copy(babyId = "other"))) }
    }

    @Test
    fun quantityOnlyBelongsToFeedingAndMustBePositive() {
        assertFailsWith<IllegalArgumentException> { CareEvent("event", profile.id, CareEventType.DIAPER, 1_000, 120) }
        assertFailsWith<IllegalArgumentException> { CareEvent("event", profile.id, CareEventType.FEED, 1_000, 0) }
        assertNull(CareEvent("event", profile.id, CareEventType.FEED, 1_000).feedAmountMl)
    }

    @Test
    fun invalidTimeAndPreferencesAreRejected() {
        assertFailsWith<IllegalArgumentException> { CareEvent("event", profile.id, CareEventType.FEED, -1) }
        assertFailsWith<IllegalArgumentException> { CarePreferences(feedIntervalMinutes = 0) }
        assertFailsWith<IllegalArgumentException> { CarePreferences(awakeWindowMinutes = -1) }
        assertFailsWith<IllegalArgumentException> { CarePreferences(seatedBreakMinutes = 0) }
    }
}
