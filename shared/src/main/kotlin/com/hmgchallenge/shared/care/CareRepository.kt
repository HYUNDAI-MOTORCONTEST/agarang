package com.hmgchallenge.shared.care

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** The app owns the local adapter. This contract has no dependency on Android or a filesystem. */
interface CareRecordStore {
    fun read(): CareRecord?
    fun write(record: CareRecord)
}

enum class CareStoreStatus { LOADING, READY, NEEDS_RELOAD }
enum class CareNotice { PROFILE_SAVED, RECORDED, UNDONE, EDITED }

data class CareState(
    val status: CareStoreStatus = CareStoreStatus.LOADING,
    val record: CareRecord? = null,
    val isSaving: Boolean = false,
    val problem: CareProblem? = null,
    val notice: CareNotice? = null,
    val revision: Long = 0,
) {
    val canWrite get() = status == CareStoreStatus.READY && !isSaving
}

/** Only confirmed writes reach observers. Operations are serialized; overlapping taps are not queued. */
class CareRepository(
    private val store: CareRecordStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(CareState())
    val state: StateFlow<CareState> = mutableState.asStateFlow()

    /** #4 can consume the same confirmed record stream as the UI, without inventing care events. */
    val records = state.map { it.record }.distinctUntilChanged()

    suspend fun load() {
        if (!mutex.tryLock()) return
        try {
            mutableState.value = state.value.copy(status = CareStoreStatus.LOADING, problem = null, notice = null)
            val record = withContext(ioDispatcher) { store.read()?.let(CareJournal::normalize) }
            mutableState.value = CareState(status = CareStoreStatus.READY, record = record, revision = state.value.revision + 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val problem = when (e) {
                is CareException -> if (e.problem == CareProblem.UNSUPPORTED_SCHEMA) e.problem else CareProblem.INVALID_DATA
                else -> CareProblem.READ_FAILED
            }
            mutableState.value = state.value.copy(status = CareStoreStatus.NEEDS_RELOAD, problem = problem)
        } finally {
            mutex.unlock()
        }
    }

    fun requireParking() {
        mutableState.value = state.value.copy(problem = CareProblem.PARK_REQUIRED, notice = null)
    }

    suspend fun saveProfile(monthAge: Int, preferredAmountMl: Int?) = change(CareNotice.PROFILE_SAVED) { current ->
        if (monthAge !in 0..24 || (preferredAmountMl != null && preferredAmountMl <= 0)) {
            throw CareException(CareProblem.INVALID_PROFILE)
        }
        val existing = current?.profile
        val preferences = (existing?.preferences ?: CarePreferences()).copy(preferredFeedAmountMl = preferredAmountMl)
        val profile = BabyProfile(id = existing?.id ?: newId(), monthAge = monthAge, preferences = preferences)
        (current ?: CareRecord(profile)).copy(profile = profile)
    }

    suspend fun record(type: CareEventType) = change(CareNotice.RECORDED) { current ->
        val record = current ?: throw CareException(CareProblem.PROFILE_REQUIRED)
        val now = nowMillis()
        if (now < 0) throw CareException(CareProblem.INVALID_TIME)
        CareJournal.append(record, CareEvent(
            id = newId(), babyId = record.profile.id, type = type, occurredAtEpochMillis = now,
            feedAmountMl = if (type == CareEventType.FEED) record.profile.preferences.preferredFeedAmountMl else null,
        ))
    }

    suspend fun undoLatest() = change(CareNotice.UNDONE) { current ->
        CareJournal.undoLatest(current ?: throw CareException(CareProblem.PROFILE_REQUIRED))
    }

    suspend fun editEvent(id: String, atEpochMillis: Long, amountMl: Int?) = change(CareNotice.EDITED) { current ->
        CareJournal.edit(current ?: throw CareException(CareProblem.PROFILE_REQUIRED), id, atEpochMillis, amountMl, nowMillis())
    }

    private suspend fun change(notice: CareNotice, transform: (CareRecord?) -> CareRecord) {
        if (!mutex.tryLock()) return
        try {
            if (!state.value.canWrite) return
            mutableState.value = state.value.copy(isSaving = true, problem = null, notice = null)
            val next = try {
                CareJournal.normalize(transform(state.value.record))
            } catch (e: CareException) {
                mutableState.value = state.value.copy(isSaving = false, problem = e.problem)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = state.value.copy(isSaving = false, problem = CareProblem.INVALID_DATA)
                return
            }
            try {
                withContext(ioDispatcher) { store.write(next) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A write may have committed before confirmation failed. Reload before any further mutation.
                mutableState.value = state.value.copy(
                    status = CareStoreStatus.NEEDS_RELOAD, isSaving = false, problem = CareProblem.WRITE_FAILED,
                )
                return
            }
            mutableState.value = state.value.copy(record = next, isSaving = false, notice = notice, revision = state.value.revision + 1)
        } finally {
            mutableState.value = state.value.copy(isSaving = false)
            mutex.unlock()
        }
    }
}
