package com.hmgchallenge.shared.departure

import com.hmgchallenge.shared.care.CareEntrySource
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DepartureRepositoryTest {
    private val now = 1_800_000_000_000L
    private class Store(var saved: DepartureRecord? = null) : DepartureStore {
        var failRead = false
        var failWrite = false
        var ambiguousWrite = false
        override fun read() = if (failRead) throw IOException() else saved
        override fun write(record: DepartureRecord) {
            if (failWrite) throw IOException()
            saved = record
            if (ambiguousWrite) throw IOException()
        }
    }
    private fun repository(store: Store, edit: () -> Boolean = { true }): DepartureRepository {
        var id = 0
        return DepartureRepository(store, edit, clock = { now }, newId = { "id-${id++}" }, io = Dispatchers.Unconfined)
    }
    private fun plan(record: DepartureRecord, quantity: Int = 2) = PackingPlan("${record.outingId}:trip", PackingScope.OUTBOUND,
        PackingDurationSource.MANUAL, 60, quantity, 3)

    @Test
    fun initialChecklistWorksWithoutProfileAndDoesNotInventCareData() = runBlocking {
        val store = Store();val repo = repository(store);repo.load()
        assertEquals(5, repo.state.value.record!!.items.size)
        assertNull(store.saved)
        assertNull(repo.state.value.record!!.driveSession)
    }

    @Test
    fun confirmedSettingsCustomItemAndCheckSurviveRecreation() = runBlocking {
        val store = Store();val repo = repository(store);repo.load()
        repo.saveSettings(PackingSettings(manualTravelMinutes = 90, feedIntervalMinutes = 180))
        repo.addItem("  손수건  ")
        val before = repo.state.value.record!!
        repo.toggleItem(before.items.first().id, plan(before))
        val again = repository(store);again.load()
        assertEquals(repo.state.value.record, again.state.value.record)
        assertEquals("손수건", again.state.value.record!!.items.last().customLabel)
        assertTrue(plan(again.state.value.record!!).isChecked(again.state.value.record!!.items.first(), now))
        assertEquals(store.saved, DepartureRecordCodec.decode(DepartureRecordCodec.encode(store.saved!!)))
    }

    @Test
    fun emptyItemNamesAreRejectedWithoutChangingConfirmedData() = runBlocking {
        val repo = repository(Store());repo.load();val before = repo.state.value.record
        repo.addItem("   ")
        assertEquals(before, repo.state.value.record)
        assertEquals(DepartureProblem.INVALID_INPUT, repo.state.value.problem)
    }

    @Test
    fun parkingIsCheckedAtActionAndImmediatelyBeforePersistence() = runBlocking {
        val store = Store();var calls = 0
        val repo = repository(store) { ++calls == 1 };repo.load()
        repo.addItem("손수건")
        assertNull(store.saved)
        assertEquals(DepartureProblem.PARK_REQUIRED, repo.state.value.problem)
        assertEquals(5, repo.state.value.record!!.items.size)
    }

    @Test
    fun uncertainWriteRequiresReloadAndDoesNotPublishUnconfirmedSuccess() = runBlocking {
        val store = Store();val repo = repository(store);repo.load();val before = repo.state.value.record
        store.ambiguousWrite = true;repo.addItem("손수건")
        assertEquals(DepartureStoreStatus.NEEDS_RELOAD, repo.state.value.status)
        assertEquals(before, repo.state.value.record)
        repo.addItem("여벌옷")
        assertEquals(6, store.saved!!.items.size)
        store.ambiguousWrite = false;repo.load()
        assertEquals(6, repo.state.value.record!!.items.size)
    }

    @Test
    fun damagedOrDemoCacheIsNotSilentlyResetAsCaregiverData() = runBlocking {
        val damaged = repository(Store().apply { failRead = true });damaged.load()
        assertEquals(DepartureStoreStatus.NEEDS_RELOAD, damaged.state.value.status)
        val demo = repository(Store(DepartureRecord("demo", origin = CareEntrySource.DEMO)));demo.load()
        assertEquals(DepartureStoreStatus.NEEDS_RELOAD, demo.state.value.status)
    }

    @Test
    fun newOutingClearsChecksAndSessionButKeepsSuppliesAndSettings() = runBlocking {
        val repo = repository(Store());repo.load()
        repo.saveSettings(PackingSettings(manualTravelMinutes = 90))
        val initial = repo.state.value.record!!
        repo.toggleItem(initial.items.first().id, plan(initial))
        repo.choose(DepartureChoice.NOW, plan(initial).tripKey, "baby")
        assertEquals(now, repo.state.value.record!!.driveSession?.startedAtEpochMillis)
        repo.newOuting()
        val next = repo.state.value.record!!
        assertTrue(initial.outingId != next.outingId)
        assertEquals(initial.settings, next.settings)
        assertTrue(next.items.all { it.checkedAtEpochMillis == null })
        assertNull(next.decision);assertNull(next.driveSession)
    }

    @Test
    fun choosingDepartureWithoutExplicitSeatingDoesNotInventDriveSession() = runBlocking {
        val repo = repository(Store());repo.load()
        repo.choose(DepartureChoice.NOW, plan(repo.state.value.record!!).tripKey)
        assertNull(repo.state.value.record!!.driveSession)
        assertEquals(DepartureChoice.NOW, repo.state.value.record!!.decision?.choice)
    }
}
