package com.hmgchallenge.shared.departure

import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.journey.DriveSession
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

interface DepartureStore {
    fun read(): DepartureRecord?
    fun write(record: DepartureRecord)
}
enum class DepartureStoreStatus { LOADING, READY, NEEDS_RELOAD }
enum class DepartureProblem { READ_FAILED, WRITE_FAILED, INVALID_INPUT, PARK_REQUIRED }
data class DepartureState(
    val status: DepartureStoreStatus = DepartureStoreStatus.LOADING,
    val record: DepartureRecord? = null,
    val saving: Boolean = false,
    val revision: Long = 0,
    val problem: DepartureProblem? = null,
) {
    val canWrite get() = status == DepartureStoreStatus.READY && !saving
}

/** Confirmed state only. A failed/uncertain write requires reload before further changes. */
class DepartureRepository(
    private val store: DepartureStore,
    private val canEdit: () -> Boolean = { true },
    private val origin: CareEntrySource = CareEntrySource.CAREGIVER,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutable = MutableStateFlow(DepartureState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()

    suspend fun load() {
        if (!mutex.tryLock()) return
        try {
            val record = withContext(io) { store.read() } ?: DepartureRecord(newId(), origin = origin)
            require(record.origin == origin)
            mutable.value = DepartureState(DepartureStoreStatus.READY, record, revision = state.value.revision + 1)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutable.value = state.value.copy(status = DepartureStoreStatus.NEEDS_RELOAD, problem = DepartureProblem.READ_FAILED) }
        finally { mutex.unlock() }
    }

    suspend fun saveSettings(settings: PackingSettings) = change { it.copy(settings = settings) }

    suspend fun addItem(label: String) = change { record ->
        val trimmed = label.trim().replace(Regex("\\s+"), " ")
        record.copy(items = record.items + PackingItem(newId(), SupplyKind.CUSTOM, trimmed))
    }

    suspend fun removeItem(id: String) = change { record -> record.copy(items = record.items.filterNot { it.id == id }) }

    suspend fun restoreDefaults() = change { record -> record.copy(items = record.items +
        DepartureRecord.defaultItems().filter { preset -> record.items.none { it.kind == preset.kind } }) }

    suspend fun toggleItem(id: String, plan: PackingPlan) = change { record ->
        require(plan.tripKey.startsWith("${record.outingId}:"))
        record.copy(items = record.items.map { item ->
            if (item.id != id) item else if (plan.isChecked(item, clock())) item.unchecked()
            else item.copy(checkedAtEpochMillis = clock(), checkedForTrip = plan.tripKey, checkedQuantity = plan.quantity(item.kind))
        })
    }

    suspend fun choose(choice: DepartureChoice, tripKey: String, seatedBabyId: String? = null, facilityId: String? = null) = change { record ->
        require(tripKey.startsWith("${record.outingId}:"))
        val now = clock()
        record.copy(decision = DepartureDecision(choice, tripKey, now, facilityId), driveSession =
            if (choice == DepartureChoice.NOW && seatedBabyId != null) DriveSession(newId(), seatedBabyId, now)
            else record.driveSession)
    }

    suspend fun newOuting() = change { record -> record.copy(outingId = newId(),
        items = record.items.map { it.unchecked() }, decision = null, driveSession = null) }

    suspend fun endSession() = change { record -> record.copy(driveSession = record.driveSession?.copy(endedAtEpochMillis = clock())) }

    private suspend fun change(transform: (DepartureRecord) -> DepartureRecord) {
        if (!mutex.tryLock()) return
        try {
            if (!state.value.canWrite) return
            if (!canEdit()) { mutable.value = state.value.copy(problem = DepartureProblem.PARK_REQUIRED); return }
            mutable.value = state.value.copy(saving = true, problem = null)
            val next = try { transform(requireNotNull(state.value.record)).also { require(it.origin == origin) } }
                catch (_: IllegalArgumentException) { mutable.value = state.value.copy(problem = DepartureProblem.INVALID_INPUT); return }
            val written = withContext(io) {
                if (!canEdit()) false else { store.write(next); true }
            }
            if (!written) { mutable.value = state.value.copy(problem = DepartureProblem.PARK_REQUIRED); return }
            mutable.value = state.value.copy(record = next, revision = state.value.revision + 1)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutable.value = state.value.copy(status = DepartureStoreStatus.NEEDS_RELOAD, problem = DepartureProblem.WRITE_FAILED) }
        finally { mutable.value = state.value.copy(saving = false); mutex.unlock() }
    }
}
