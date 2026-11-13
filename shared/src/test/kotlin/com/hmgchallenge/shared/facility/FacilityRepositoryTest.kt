package com.hmgchallenge.shared.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FacilityRepositoryTest {
    private var now = 1_800_000_000_000L
    private val demo = DemoStopScenario.create(now)
    private val public = demo.facilities.copy(origin = DataOrigin.PUBLIC_DATA, value = demo.facilities.readyValue!!.copy(
        origin = DataOrigin.PUBLIC_DATA, entries = demo.facilities.readyValue!!.entries.map {
            it.copy(facility = it.facility.copy(id = it.facility.id.removePrefix("demo-")))
        }))
    private class Store(var value: DataSnapshot<FacilityCatalog>? = null) : FacilityCatalogStore {
        var failRead = false
        var failWrite = false
        override fun read() = if (failRead) throw IOException("private") else value
        override fun write(snapshot: DataSnapshot<FacilityCatalog>) { if (failWrite) throw IOException("private"); value = snapshot }
    }
    private fun repo(store: Store = Store(), client: FacilityCatalogClient = FacilityCatalogClient { public }) =
        FacilityRepository(client, store, { now }, Dispatchers.Unconfined)

    @Test
    fun successfulFetchPersistsPublicCatalogAndRoundTripsUnitsAndUnknowns() = runBlocking {
        val store = Store()
        val repository = repo(store)
        repository.refresh()
        assertEquals(public, store.value)
        assertEquals(public, repository.state.value.snapshot)
        assertEquals(public, FacilityCatalogCodec.decode(FacilityCatalogCodec.encode(public)))
        assertEquals(AmenityStatus.UNKNOWN, public.readyValue!!.entries.first().facility.diaperChanging)
    }

    @Test
    fun failedFetchRestoresCacheOnlyForDisplay() = runBlocking {
        val repository = repo(Store(public), FacilityCatalogClient { throw IOException("secret") })
        repository.refresh()
        val state = repository.state.value
        assertEquals(DataStatus.STALE, state.snapshot.status)
        assertEquals(public.value, state.snapshot.value)
        assertEquals(FacilityProblem.OFFLINE, state.problem)
        assertTrue(state.fromCache)
        assertFalse(state.refreshing)
    }

    @Test
    fun offlineWithoutCacheAndMissingBackendDatasetHaveDifferentStates() = runBlocking {
        val offline = repo(client = FacilityCatalogClient { throw IOException() })
        offline.refresh()
        assertEquals(FacilityProblem.OFFLINE, offline.state.value.problem)
        val missing = repo(client = FacilityCatalogClient { DataSnapshot(DataOrigin.PUBLIC_DATA, DataStatus.UNSUPPORTED) })
        missing.refresh()
        assertEquals(FacilityProblem.NOT_CONFIGURED, missing.state.value.problem)
    }

    @Test
    fun demoResponseCannotReplacePublicCache() = runBlocking {
        val store = Store(public)
        val repository = repo(store, FacilityCatalogClient { demo.facilities })
        repository.refresh()
        assertEquals(FacilityProblem.INVALID_RESPONSE, repository.state.value.problem)
        assertEquals(public, store.value)
        assertEquals(DataStatus.STALE, repository.state.value.snapshot.status)
    }

    @Test
    fun corruptedCacheDoesNotPreventNetworkRecovery() = runBlocking {
        val repository = repo(Store().apply { failRead = true })
        repository.refresh()
        assertEquals(DataStatus.READY, repository.state.value.snapshot.status)
    }

    @Test
    fun cacheWriteFailureRetainsFreshNetworkDataAndReportsNonDurability() = runBlocking {
        val repository = repo(Store().apply { failWrite = true })
        repository.refresh()
        assertEquals(DataStatus.READY, repository.state.value.snapshot.status)
        assertEquals(FacilityProblem.CACHE_UNAVAILABLE, repository.state.value.problem)
    }

    @Test
    fun clockReversalAndCacheExpiryInvalidateReadySnapshot() = runBlocking {
        val repository = repo()
        repository.refresh()
        now += 86_400_000
        repository.tick()
        assertEquals(DataStatus.STALE, repository.state.value.snapshot.status)
        now -= 86_400_001
        val future = repo()
        future.refresh()
        assertEquals(FacilityProblem.INVALID_RESPONSE, future.state.value.problem)
    }

    @Test
    fun emptyCatalogRemainsNormalSuccessfulData() = runBlocking {
        val repository = repo(client = FacilityCatalogClient { public.copy(value = public.readyValue!!.copy(entries = emptyList())) })
        repository.refresh()
        assertEquals(DataStatus.READY, repository.state.value.snapshot.status)
        assertTrue(repository.state.value.snapshot.readyValue!!.entries.isEmpty())
    }

    @Test
    fun serverClockSkewUsesLocalReceiptWithoutRenewingPublishedDates() = runBlocking {
        val repository = repo(client = FacilityCatalogClient { public.copy(observedAtEpochMillis = now + 86_400_000) })
        repository.refresh()
        val snapshot = repository.state.value.snapshot
        assertEquals(DataStatus.READY, snapshot.status)
        assertEquals(now, snapshot.observedAtEpochMillis)
        assertEquals(public.value, snapshot.value)
    }
}
