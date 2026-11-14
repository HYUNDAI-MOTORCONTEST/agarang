package com.hmgchallenge.shared.departure

import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.journey.DriveSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class PackingScope { OUTBOUND, WHOLE_OUTING }

/** These are caregiver-confirmed packing inputs, not infant feeding/diaper recommendations. */
@Serializable
data class PackingSettings(
    val scope: PackingScope = PackingScope.OUTBOUND,
    val manualTravelMinutes: Int? = null,
    val extraOutingMinutes: Int = 0,
    val feedIntervalMinutes: Int? = null,
    val diaperIntervalMinutes: Int? = null,
    val spareFeedServings: Int = 1,
    val spareDiapers: Int = 2,
) {
    init {
        require(manualTravelMinutes == null || manualTravelMinutes in 1..MAX_MINUTES)
        require(extraOutingMinutes in 0..MAX_MINUTES)
        require(listOf(feedIntervalMinutes, diaperIntervalMinutes).all { it == null || it in 30..720 })
        require(spareFeedServings in 0..10 && spareDiapers in 0..20)
    }
    companion object { const val MAX_MINUTES = 72 * 60 }
}

@Serializable
enum class SupplyKind { FEEDING, DIAPERS, WIPES, CLOTHES, CAR_SEAT, CUSTOM }

@Serializable
data class PackingItem(
    val id: String,
    val kind: SupplyKind,
    val customLabel: String? = null,
    val checkedAtEpochMillis: Long? = null,
    val checkedForTrip: String? = null,
    val checkedQuantity: Int? = null,
) {
    init {
        require(id.isNotBlank())
        require((kind == SupplyKind.CUSTOM) == (customLabel != null))
        require(customLabel == null || (customLabel.isNotBlank() && customLabel.length <= 30 && customLabel.none { it.isISOControl() }))
        require(checkedAtEpochMillis == null || checkedAtEpochMillis >= 0)
        require((checkedAtEpochMillis != null) == (checkedForTrip != null))
        require(checkedForTrip == null || checkedForTrip.isNotBlank())
        require(checkedQuantity == null || (checkedQuantity >= 0 && checkedAtEpochMillis != null))
    }
    fun unchecked() = copy(checkedAtEpochMillis = null, checkedForTrip = null, checkedQuantity = null)
}

@Serializable
enum class DepartureChoice { NOW, AFTER_CARE, STOP_ON_WAY }

@Serializable
data class DepartureDecision(
    val choice: DepartureChoice,
    val tripKey: String,
    val chosenAtEpochMillis: Long,
    val facilityId: String? = null,
) {
    init {
        require(tripKey.isNotBlank() && chosenAtEpochMillis >= 0)
        require(facilityId == null || (choice == DepartureChoice.STOP_ON_WAY && facilityId.isNotBlank()))
    }
}

@Serializable
data class DepartureRecord(
    val outingId: String,
    val settings: PackingSettings = PackingSettings(),
    val items: List<PackingItem> = defaultItems(),
    val decision: DepartureDecision? = null,
    val driveSession: DriveSession? = null,
    val origin: CareEntrySource = CareEntrySource.CAREGIVER,
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion == 1 && outingId.isNotBlank())
        require(items.size <= 30 && items.map { it.id }.distinct().size == items.size)
        require(items.filter { it.kind != SupplyKind.CUSTOM }.map { it.kind }.distinct().size == items.count { it.kind != SupplyKind.CUSTOM })
    }
    companion object {
        fun defaultItems() = SupplyKind.entries.filter { it != SupplyKind.CUSTOM }.map { PackingItem("supply-${it.name.lowercase()}", it) }
    }
}

object DepartureRecordCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    const val MAX_BYTES = 64 * 1_024
    fun encode(record: DepartureRecord) = json.encodeToString(DepartureRecord.serializer(), record)
    fun decode(text: String): DepartureRecord {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        return json.decodeFromString(DepartureRecord.serializer(), text)
    }
}

enum class PackingDurationSource { NAVIGATION, MANUAL, DEMO, UNKNOWN }
enum class PackingProblem { NONE, DURATION_UNKNOWN, OUTING_RANGE_UNCONFIRMED, TRIP_TOO_LONG }

data class PackingPlan(
    val tripKey: String,
    val scope: PackingScope,
    val source: PackingDurationSource,
    val durationMinutes: Long?,
    val feedServings: Int?,
    val diapers: Int?,
    val problem: PackingProblem = PackingProblem.NONE,
) {
    val needsQuantityConfirmation get() = feedServings == null || diapers == null
    fun quantity(kind: SupplyKind): Int? = when (kind) {
        SupplyKind.FEEDING -> feedServings
        SupplyKind.DIAPERS -> diapers
        else -> null
    }

    /** Increasing quantities invalidate only items whose previously checked amount is insufficient. */
    fun isChecked(item: PackingItem, now: Long): Boolean {
        val at = item.checkedAtEpochMillis ?: return false
        val quantity = quantity(item.kind)
        return item.checkedForTrip == tripKey && now >= at && now - at < CHECK_VALIDITY_MILLIS &&
            (quantity == null || item.checkedQuantity?.let { it >= quantity } == true)
    }
    companion object { const val CHECK_VALIDITY_MILLIS = 24 * 60 * 60 * 1_000L }
}
