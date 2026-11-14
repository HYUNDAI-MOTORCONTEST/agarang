package com.hmgchallenge.shared.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Source dates describe the published data, not a visit or a live availability check. */
@Serializable
data class FacilityDataSource(
    val id: String,
    val provider: String,
    val title: String,
    val url: String,
    val license: String,
    val updateCycle: String,
) {
    init {
        require(listOf(id, provider, title, license, updateCycle).all { it.isNotBlank() })
        require(url.startsWith("https://"))
    }
}

@Serializable
data class FacilityListing(
    val facility: CareFacility,
    val sourceId: String,
    val roadRouteNumber: String? = null,
    val roadRouteName: String? = null,
    /** Provider's original label. Up/down and endpoint names are not interchangeable. */
    val directionLabel: String? = null,
) {
    init {
        require(sourceId.isNotBlank())
        require(listOf(roadRouteNumber, roadRouteName, directionLabel).all { it == null || it.isNotBlank() })
    }
}

@Serializable
data class FacilityCatalog(
    val origin: DataOrigin,
    val fetchedAtEpochMillis: Long,
    val sources: List<FacilityDataSource>,
    val entries: List<FacilityListing>,
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion == 1 && fetchedAtEpochMillis >= 0)
        require(origin == DataOrigin.PUBLIC_DATA || origin == DataOrigin.DEMO)
        require(sources.map { it.id }.distinct().size == sources.size)
        require(entries.size <= MAX_ENTRIES && entries.map { it.facility.id }.distinct().size == entries.size)
        val sourceIds = sources.map { it.id }.toSet()
        require(entries.all { it.sourceId in sourceIds })
        require(entries.all { (it.facility.id.startsWith("demo-")) == (origin == DataOrigin.DEMO) })
        require(entries.all { it.facility.verifiedAtEpochMillis?.let { date -> date <= fetchedAtEpochMillis } != false })
    }

    companion object {
        const val PATH = "/api/facilities"
        const val MAX_ENTRIES = 5_000
        const val MAX_JSON_BYTES = 2_000_000
    }
}

object FacilityCatalogCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = DataSnapshot.serializer(FacilityCatalog.serializer())
    fun encode(snapshot: DataSnapshot<FacilityCatalog>): String = json.encodeToString(serializer, snapshot)
    fun decode(text: String): DataSnapshot<FacilityCatalog> {
        require(text.toByteArray(Charsets.UTF_8).size <= FacilityCatalog.MAX_JSON_BYTES)
        return json.decodeFromString(serializer, text).also { snapshot ->
            require(snapshot.origin == DataOrigin.PUBLIC_DATA || snapshot.origin == DataOrigin.DEMO)
            require(snapshot.value?.origin?.let { it == snapshot.origin } != false)
        }
    }
}
