package com.hmgchallenge.shared.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

fun interface FacilityCatalogClient { fun fetch(): DataSnapshot<FacilityCatalog> }
interface FacilityCatalogStore {
    fun read(): DataSnapshot<FacilityCatalog>?
    fun write(snapshot: DataSnapshot<FacilityCatalog>)
}

enum class FacilityProblem { NONE, OFFLINE, NOT_CONFIGURED, INVALID_RESPONSE, STALE, CACHE_UNAVAILABLE }
data class FacilityState(
    val snapshot: DataSnapshot<FacilityCatalog> = DataSnapshot(DataOrigin.PUBLIC_DATA, DataStatus.LOADING),
    val refreshing: Boolean = false,
    val problem: FacilityProblem = FacilityProblem.NONE,
    val fromCache: Boolean = false,
)

/** A failed refresh keeps the cache for display, but never promotes it to current route evidence. */
class FacilityRepository(
    private val client: FacilityCatalogClient,
    private val store: FacilityCatalogStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val policy: FacilityPolicy = FacilityPolicy(),
) {
    private val mutable = MutableStateFlow(FacilityState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private var loaded = false

    suspend fun refresh() {
        if (!mutex.tryLock()) return
        try {
            mutable.value = mutable.value.copy(refreshing = true)
            if (!loaded) {
                loaded = true
                val cached = try { withContext(io) { store.read() } } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                if (cached?.origin == DataOrigin.PUBLIC_DATA && cached.value?.origin == DataOrigin.PUBLIC_DATA) {
                    mutable.value = FacilityState(cached.copy(status = DataStatus.STALE), true, fromCache = true)
                }
            }
            val received = try { withContext(io) { client.fetch() } } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    fail(FacilityProblem.OFFLINE)
                    return
                }
            // Server wall clocks are not the app's cache clock. This timestamps a successful
            // receipt only; the catalog's download date and each published source date stay intact.
            val response = if (received.value != null) received.copy(observedAtEpochMillis = clock()) else received
            val catalog = response.value
            if (response.origin != DataOrigin.PUBLIC_DATA || catalog?.origin?.let { it != DataOrigin.PUBLIC_DATA } == true ||
                (catalog != null && (catalog.fetchedAtEpochMillis > clock() || response.observedAtEpochMillis == null ||
                    response.observedAtEpochMillis > clock()))
            ) {
                fail(FacilityProblem.INVALID_RESPONSE)
                return
            }
            if (response.status != DataStatus.READY) {
                fail(if (response.status == DataStatus.UNSUPPORTED) FacilityProblem.NOT_CONFIGURED
                    else if (response.status == DataStatus.STALE) FacilityProblem.STALE else FacilityProblem.OFFLINE)
                return
            }
            if (catalog == null || !policy.catalogFresh(catalog, clock()) || !policy.cacheFresh(response, clock())) {
                mutable.value = FacilityState(response.copy(status = DataStatus.STALE), problem = FacilityProblem.STALE)
                return
            }
            var cacheFailed = false
            try { withContext(io) { store.write(response) } } catch (e: CancellationException) { throw e }
                catch (_: Exception) { cacheFailed = true }
            mutable.value = FacilityState(response, problem = if (cacheFailed) FacilityProblem.CACHE_UNAVAILABLE else FacilityProblem.NONE)
        } finally {
            mutable.value = mutable.value.copy(refreshing = false)
            mutex.unlock()
        }
    }

    fun tick() {
        val snapshot = state.value.snapshot
        val catalog = snapshot.readyValue ?: return
        if (!policy.catalogFresh(catalog, clock()) || !policy.cacheFresh(snapshot, clock())) {
            mutable.value = mutable.value.copy(snapshot = snapshot.copy(status = DataStatus.STALE), problem = FacilityProblem.STALE)
        }
    }

    private fun fail(problem: FacilityProblem) {
        val previous = mutable.value.snapshot
        val stale = previous.value?.let { previous.copy(status = DataStatus.STALE) }
        mutable.value = FacilityState(stale ?: DataSnapshot(DataOrigin.PUBLIC_DATA,
            if (problem == FacilityProblem.NOT_CONFIGURED) DataStatus.UNSUPPORTED else DataStatus.FAILED),
            problem = problem, fromCache = stale != null)
    }
}
