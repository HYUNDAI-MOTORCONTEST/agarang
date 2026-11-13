package com.hmgchallenge.shared.care

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CareRepositoryTest {
    private class MemoryStore : CareRecordStore {
        var text: String? = null
        var readFailure = false
        var writeFailure = false
        var failAfterCommit = false
        var writes = 0
        var beforeWrite: (() -> Unit)? = null
        override fun read(): CareRecord? {
            if (readFailure) throw IOException("private storage error")
            return text?.let(CareRecordCodec::decode)
        }
        override fun write(record: CareRecord) {
            beforeWrite?.invoke()
            writes++
            if (writeFailure) throw IOException("private storage error")
            text = CareRecordCodec.encode(record)
            if (failAfterCommit) throw IOException("confirmation failed")
        }
    }

    private var now = 1_000_000L
    private var sequence = 0
    private fun repository(store: MemoryStore, io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined) =
        CareRepository(store, io, nowMillis = { now }, newId = { "id-" + sequence++ })

    @Test
    fun explicitTimingPreferencesPreserveEventsAmountAndProfileIdentity() = runBlocking {
        val store = MemoryStore();val repo = repository(store);repo.load();repo.saveProfile(3, 150)
        repo.record(CareEventType.FEED)
        val before = assertNotNull(repo.state.value.record)
        repo.saveTimingPreferences(180, null, 120)
        val after = assertNotNull(repo.state.value.record)
        assertEquals(before.profile.id, after.profile.id)
        assertEquals(before.events, after.events)
        assertEquals(150, after.profile.preferences.preferredFeedAmountMl)
        assertEquals(180, after.profile.preferences.feedIntervalMinutes)
        repo.saveTimingPreferences(null, null, null)
        assertNull(repo.state.value.record!!.profile.preferences.feedIntervalMinutes)
    }

    @Test
    fun firstUseDoesNotInventProfileHistoryOrIntervals() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store)
        repo.load()
        assertTrue(repo.state.value.canWrite)
        assertNull(repo.state.value.record)
        repo.record(CareEventType.FEED)
        assertEquals(CareProblem.PROFILE_REQUIRED, repo.state.value.problem)
        assertEquals(0, store.writes)
        repo.saveProfile(0, null)
        val record = assertNotNull(repo.state.value.record)
        assertTrue(record.events.isEmpty())
        assertNull(record.profile.preferences.feedIntervalMinutes)
        assertNull(record.profile.preferences.preferredFeedAmountMl)
    }

    @Test
    fun processRecreationRestoresStableIdsAmountsAndSleepState() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store)
        repo.load()
        repo.saveProfile(2, 120)
        repo.record(CareEventType.FEED)
        now += 2_000
        repo.record(CareEventType.DIAPER)
        now += 2_000
        repo.record(CareEventType.SLEEP_START)
        val before = assertNotNull(repo.state.value.record)
        val restored = repository(store)
        restored.load()
        assertEquals(before, restored.state.value.record)
        assertTrue(CareJournal.isSleeping(assertNotNull(restored.state.value.record)))
        assertEquals(120, before.events.last().feedAmountMl)
        assertTrue(before.events.all { it.source == CareEntrySource.CAREGIVER })
    }

    @Test
    fun profileChangesPreserveIdentityHistoryAndOtherPreferences() = runBlocking {
        val store = MemoryStore()
        val profile = BabyProfile("baby", 2, CarePreferences(feedIntervalMinutes = 180, preferredFeedAmountMl = 90))
        val event = CareEvent("old-feed", "baby", CareEventType.FEED, 1_000, 90)
        store.text = CareRecordCodec.encode(CareRecord(profile, listOf(event)))
        val repo = repository(store)
        repo.load()
        repo.saveProfile(3, 150)
        repo.record(CareEventType.FEED)
        val record = assertNotNull(repo.state.value.record)
        assertEquals("baby", record.profile.id)
        assertEquals(180, record.profile.preferences.feedIntervalMinutes)
        assertEquals(90, record.events.last().feedAmountMl)
        assertEquals(150, record.events.first().feedAmountMl)
    }

    @Test
    fun duplicateTapAndBackwardsClockDoNotWrite() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store)
        repo.load()
        repo.saveProfile(24, null)
        repo.record(CareEventType.FEED)
        val before = repo.state.value.record
        val count = store.writes
        repo.record(CareEventType.FEED)
        assertEquals(CareProblem.DUPLICATE_RECORD, repo.state.value.problem)
        now--
        repo.record(CareEventType.DIAPER)
        assertEquals(CareProblem.CLOCK_BEHIND, repo.state.value.problem)
        assertEquals(before, repo.state.value.record)
        assertEquals(count, store.writes)
        now += 1_001
        repo.record(CareEventType.FEED)
        assertEquals(2, repo.state.value.record?.events?.size)
    }

    @Test
    fun sleepSequenceAndUndoRemainConsistent() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store)
        repo.load()
        repo.saveProfile(2, null)
        repo.record(CareEventType.SLEEP_END)
        assertEquals(CareProblem.INVALID_SLEEP_ORDER, repo.state.value.problem)
        repo.record(CareEventType.SLEEP_START)
        now += 2_000
        repo.record(CareEventType.SLEEP_START)
        assertEquals(CareProblem.INVALID_SLEEP_ORDER, repo.state.value.problem)
        repo.record(CareEventType.FEED)
        now += 2_000
        repo.record(CareEventType.SLEEP_END)
        assertFalse(CareJournal.isSleeping(assertNotNull(repo.state.value.record)))
        repo.undoLatest()
        assertTrue(CareJournal.isSleeping(assertNotNull(repo.state.value.record)))
        assertEquals(2, repo.state.value.record?.events?.size)
        val restored = repository(store)
        restored.load()
        assertEquals(repo.state.value.record, restored.state.value.record)
    }

    @Test
    fun rapidDifferentEventsWithSameTimestampKeepSleepOrder() = runBlocking {
        val repo = repository(MemoryStore())
        repo.load()
        repo.saveProfile(2, null)
        repo.record(CareEventType.SLEEP_START)
        repo.record(CareEventType.SLEEP_END)
        assertEquals(listOf(CareEventType.SLEEP_END, CareEventType.SLEEP_START), repo.state.value.record?.events?.map { it.type })
        assertFalse(CareJournal.isSleeping(assertNotNull(repo.state.value.record)))
    }

    @Test
    fun editingTimeCannotReverseSleepOrderOrCreateFutureRecords() = runBlocking {
        val repo = repository(MemoryStore())
        repo.load()
        repo.saveProfile(2, 120)
        repo.record(CareEventType.SLEEP_START)
        now += 2_000
        repo.record(CareEventType.SLEEP_END)
        val before = assertNotNull(repo.state.value.record)
        val endId = before.events.first().id
        repo.editEvent(endId, before.events.last().occurredAtEpochMillis - 1, null)
        assertEquals(CareProblem.INVALID_SLEEP_ORDER, repo.state.value.problem)
        repo.editEvent(endId, now + 1, null)
        assertEquals(CareProblem.INVALID_TIME, repo.state.value.problem)
        assertEquals(before, repo.state.value.record)
        repo.editEvent(endId, now - 1_000, null)
        assertEquals(endId, repo.state.value.record?.events?.first()?.id)
        assertEquals(now - 1_000, repo.state.value.record?.events?.first()?.occurredAtEpochMillis)
    }

    @Test
    fun feedingDetailsCanBeEditedWithoutChangingIdOrProfilePreset() = runBlocking {
        val repo = repository(MemoryStore())
        repo.load()
        repo.saveProfile(2, 120)
        repo.record(CareEventType.FEED)
        val event = assertNotNull(repo.state.value.record).events.single()
        repo.editEvent(event.id, now - 300_000, 90)
        val record = assertNotNull(repo.state.value.record)
        assertEquals(event.id, record.events.single().id)
        assertEquals(90, record.events.single().feedAmountMl)
        assertEquals(120, record.profile.preferences.preferredFeedAmountMl)
    }

    @Test
    fun malformedFutureAndDemoDataAreNotOverwritten() = runBlocking {
        val inputs = listOf(
            "not-json" to CareProblem.INVALID_DATA,
            """{"schemaVersion":2,"profile":{"id":"baby","monthAge":2}}""" to CareProblem.UNSUPPORTED_SCHEMA,
            """{"profile":{"id":"baby","monthAge":25}}""" to CareProblem.INVALID_DATA,
            CareRecordCodec.encode(CareRecord(BabyProfile("baby", 2))).replace("CAREGIVER", "DEMO") to CareProblem.INVALID_DATA,
        )
        for ((text, expected) in inputs) {
            val store = MemoryStore().also { it.text = text }
            val repo = repository(store)
            repo.load()
            assertEquals(expected, repo.state.value.problem)
            assertFalse(repo.state.value.canWrite)
            repo.saveProfile(2, null)
            repo.record(CareEventType.FEED)
            assertEquals(text, store.text)
            assertEquals(0, store.writes)
        }
    }

    @Test
    fun codecRejectsDuplicateIdsAndNormalizesUnorderedHistory() {
        val profile = BabyProfile("baby", 2)
        val first = CareEvent("a", "baby", CareEventType.FEED, 1_000)
        val second = CareEvent("b", "baby", CareEventType.DIAPER, 2_000)
        val unordered = kotlinx.serialization.json.Json.encodeToString(CareRecord.serializer(), CareRecord(profile, listOf(first, second)))
        val record = CareRecordCodec.decode(unordered)
        assertEquals(listOf("b", "a"), record.events.map { it.id })
        val text = CareRecordCodec.encode(CareRecord(profile, listOf(first, second))).replace("\"b\"", "\"a\"")
        assertEquals(CareProblem.INVALID_DATA, assertFailsWith<CareException> { CareRecordCodec.decode(text) }.problem)
    }

    @Test
    fun failedWriteKeepsConfirmedStateAndRequiresReload() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store)
        repo.load()
        repo.saveProfile(2, null)
        val before = repo.state.value.record
        store.writeFailure = true
        repo.record(CareEventType.FEED)
        assertEquals(before, repo.state.value.record)
        assertEquals(CareProblem.WRITE_FAILED, repo.state.value.problem)
        assertFalse(repo.state.value.isSaving)
        val count = store.writes
        repo.record(CareEventType.DIAPER)
        assertEquals(count, store.writes)
        store.writeFailure = false
        repo.load()
        repo.record(CareEventType.DIAPER)
        assertEquals(CareEventType.DIAPER, repo.state.value.record?.events?.single()?.type)
    }

    @Test
    fun confirmationFailureAfterCommitIsReconciledByReload() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store)
        repo.load()
        repo.saveProfile(2, null)
        store.failAfterCommit = true
        repo.record(CareEventType.FEED)
        assertTrue(assertNotNull(repo.state.value.record).events.isEmpty())
        store.failAfterCommit = false
        repo.load()
        assertEquals(1, repo.state.value.record?.events?.size)
    }

    @Test
    fun failedUndoDoesNotRemoveARecordFromTheScreen() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store)
        repo.load()
        repo.saveProfile(2, null)
        repo.record(CareEventType.FEED)
        val before = repo.state.value.record
        store.writeFailure = true
        repo.undoLatest()
        assertEquals(before, repo.state.value.record)
        assertEquals(CareProblem.WRITE_FAILED, repo.state.value.problem)
    }

    @Test
    fun readFailureCanRecoverWithoutReplacingStoredData() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store)
        repo.load()
        repo.saveProfile(2, null)
        val before = store.text
        store.readFailure = true
        repo.load()
        assertEquals(CareProblem.READ_FAILED, repo.state.value.problem)
        assertFalse(repo.state.value.canWrite)
        assertEquals(before, store.text)
        store.readFailure = false
        repo.load()
        assertTrue(repo.state.value.canWrite)
        assertEquals(2, repo.state.value.record?.profile?.monthAge)
    }

    @Test
    fun observersReceiveOnlyConfirmedRecordChanges() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store)
        val observed = mutableListOf<CareRecord?>()
        val collector = launch(Dispatchers.Unconfined) { repo.records.collect { observed.add(it) } }
        try {
            repo.load()
            repo.saveProfile(2, null)
            repo.record(CareEventType.FEED)
            now += 2_000
            store.writeFailure = true
            repo.record(CareEventType.DIAPER)
            assertEquals(3, observed.size)
            assertNull(observed.first())
            assertTrue(assertNotNull(observed[1]).events.isEmpty())
            assertEquals(CareEventType.FEED, assertNotNull(observed.last()).events.single().type)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun invalidProfileEditsLeaveThePreviousProfileAndStorageIntact() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store)
        repo.load()
        repo.saveProfile(24, 120)
        val before = store.text
        repo.saveProfile(25, 120)
        assertEquals(CareProblem.INVALID_PROFILE, repo.state.value.problem)
        repo.saveProfile(-1, 120)
        assertEquals(CareProblem.INVALID_PROFILE, repo.state.value.problem)
        repo.saveProfile(2, 0)
        assertEquals(CareProblem.INVALID_PROFILE, repo.state.value.problem)
        assertEquals(before, store.text)
        assertEquals(24, repo.state.value.record?.profile?.monthAge)
    }

    @Test
    fun overlappingTapsDuringSlowStorageAreNotQueued() = runBlocking {
        val store = MemoryStore()
        val repo = repository(store, Dispatchers.IO)
        repo.load()
        repo.saveProfile(2, null)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        store.beforeWrite = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val first = launch { repo.record(CareEventType.FEED) }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            assertTrue(repo.state.value.isSaving)
            assertTrue(assertNotNull(repo.state.value.record).events.isEmpty())
            repo.record(CareEventType.DIAPER)
        } finally {
            release.countDown()
        }
        first.join()
        assertEquals(listOf(CareEventType.FEED), repo.state.value.record?.events?.map { it.type })
    }
}
