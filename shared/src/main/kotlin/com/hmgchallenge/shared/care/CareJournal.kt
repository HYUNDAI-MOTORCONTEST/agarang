package com.hmgchallenge.shared.care

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** User-facing categories; storage paths and SDK exception text never belong here. */
enum class CareProblem {
    READ_FAILED, INVALID_DATA, UNSUPPORTED_SCHEMA, WRITE_FAILED,
    PROFILE_REQUIRED, INVALID_PROFILE, DUPLICATE_RECORD, INVALID_SLEEP_ORDER,
    CLOCK_BEHIND, INVALID_TIME, RECORD_NOT_FOUND, PARK_REQUIRED,
}

class CareException(val problem: CareProblem) : Exception(problem.name)

/** Newest first. Stable ties preserve the order of successive taps with identical timestamps. */
object CareJournal {
    private const val DUPLICATE_WINDOW_MILLIS = 1_000L

    fun normalize(record: CareRecord): CareRecord {
        if (record.profile.source != CareEntrySource.CAREGIVER ||
            record.events.any { it.source != CareEntrySource.CAREGIVER }
        ) throw CareException(CareProblem.INVALID_DATA)
        val sorted = record.events.sortedByDescending { it.occurredAtEpochMillis }
        checkSleepOrder(sorted)
        return record.copy(events = sorted)
    }

    fun isSleeping(record: CareRecord): Boolean =
        record.events.firstOrNull { it.type == CareEventType.SLEEP_START || it.type == CareEventType.SLEEP_END }
            ?.type == CareEventType.SLEEP_START

    fun append(record: CareRecord, event: CareEvent): CareRecord {
        if (event.babyId != record.profile.id || event.source != CareEntrySource.CAREGIVER) {
            throw CareException(CareProblem.INVALID_DATA)
        }
        if (record.events.any { it.id == event.id }) throw CareException(CareProblem.DUPLICATE_RECORD)
        val latestTime = record.events.firstOrNull()?.occurredAtEpochMillis
        if (latestTime != null && event.occurredAtEpochMillis < latestTime) {
            throw CareException(CareProblem.CLOCK_BEHIND)
        }
        val previous = record.events.firstOrNull { it.type == event.type }
        if (previous != null && event.occurredAtEpochMillis - previous.occurredAtEpochMillis < DUPLICATE_WINDOW_MILLIS) {
            throw CareException(CareProblem.DUPLICATE_RECORD)
        }
        return normalize(record.copy(events = listOf(event) + record.events))
    }

    fun edit(record: CareRecord, eventId: String, atEpochMillis: Long, amountMl: Int?, nowEpochMillis: Long): CareRecord {
        if (atEpochMillis < 0 || atEpochMillis > nowEpochMillis) throw CareException(CareProblem.INVALID_TIME)
        val event = record.events.firstOrNull { it.id == eventId } ?: throw CareException(CareProblem.RECORD_NOT_FOUND)
        if (amountMl != null && (amountMl <= 0 || event.type != CareEventType.FEED)) {
            throw CareException(CareProblem.INVALID_DATA)
        }
        val updated = event.copy(occurredAtEpochMillis = atEpochMillis, feedAmountMl = amountMl)
        return normalize(record.copy(events = record.events.map { if (it.id == eventId) updated else it }))
    }

    fun undoLatest(record: CareRecord): CareRecord {
        if (record.events.isEmpty()) throw CareException(CareProblem.RECORD_NOT_FOUND)
        return normalize(record.copy(events = record.events.drop(1)))
    }

    private fun checkSleepOrder(newestFirst: List<CareEvent>) {
        var sleeping = false
        for (event in newestFirst.asReversed()) {
            when (event.type) {
                CareEventType.SLEEP_START -> {
                    if (sleeping) throw CareException(CareProblem.INVALID_SLEEP_ORDER)
                    sleeping = true
                }
                CareEventType.SLEEP_END -> {
                    if (!sleeping) throw CareException(CareProblem.INVALID_SLEEP_ORDER)
                    sleeping = false
                }
                else -> Unit
            }
        }
    }
}

/** Inspect the version first. A future or damaged record must never become an empty writable record. */
object CareRecordCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(record: CareRecord): String = json.encodeToString(CareRecord.serializer(), CareJournal.normalize(record))

    fun decode(text: String): CareRecord {
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull
            if ("schemaVersion" in root && version == null) throw CareException(CareProblem.INVALID_DATA)
            if (version != null && version != 1) throw CareException(CareProblem.UNSUPPORTED_SCHEMA)
            return CareJournal.normalize(json.decodeFromString<CareRecord>(text))
        } catch (e: CareException) {
            throw e
        } catch (_: Exception) {
            throw CareException(CareProblem.INVALID_DATA)
        }
    }
}
