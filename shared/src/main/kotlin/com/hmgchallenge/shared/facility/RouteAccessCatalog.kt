package com.hmgchallenge.shared.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Verified approach metadata on a specific route corridor. Coordinates and directions are
 * explicitly curated per corridor rather than inferred from catalog centroids or highway names.
 */
@Serializable
data class VerifiedCorridorApproach(
    val facilityId: String,
    val access: RouteAccess,
    val remainingDistanceToDestinationMeters: Long,
    val remainingDurationToDestinationSeconds: Long,
    val navigationLocation: GeoPoint? = null,
    val navigationPoiId: String? = null,
) {
    init {
        require(facilityId.isNotBlank())
        require(remainingDistanceToDestinationMeters > 0)
        require(remainingDurationToDestinationSeconds > 0)
        require(navigationPoiId == null || navigationPoiId.isNotBlank())
        require(access != RouteAccess.CONFIRMED_ON_ROUTE || navigationLocation != null)
    }
}

@Serializable
data class VerifiedRouteCorridor(
    val id: String,
    val destinationLabel: String,
    val destinationLocation: GeoPoint,
    val destinationPoiId: String? = null,
    val destinationToleranceMeters: Double = 250.0,
    val totalDistanceMeters: Long,
    val totalDurationSeconds: Long,
    val coversRemainingRestAreas: Boolean = false,
    val approaches: List<VerifiedCorridorApproach>,
) {
    init {
        require(id.isNotBlank() && destinationLabel.isNotBlank())
        require(destinationPoiId == null || destinationPoiId.isNotBlank())
        require(destinationToleranceMeters.isFinite() && destinationToleranceMeters in 1.0..2_000.0)
        require(totalDistanceMeters > 0 && totalDurationSeconds > 0)
        require(approaches.map { it.facilityId }.distinct().size == approaches.size)
        require(approaches.all {
            it.remainingDistanceToDestinationMeters < totalDistanceMeters &&
                it.remainingDurationToDestinationSeconds < totalDurationSeconds
        })
    }

    fun matches(route: JourneySnapshot, destination: GeoPoint, remainingDistanceMeters: Long): Boolean {
        if (remainingDistanceMeters !in 1..totalDistanceMeters) return false
        if (haversineDistanceMeters(destination, destinationLocation) > destinationToleranceMeters) return false
        return when {
            destinationPoiId != null && route.destinationPoiId != null -> route.destinationPoiId == destinationPoiId
            destinationPoiId != null && route.destinationPoiId == null -> route.destinationLabel == destinationLabel
            else -> route.destinationPoiId == null && route.destinationLabel == destinationLabel
        }
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0

        fun haversineDistanceMeters(a: GeoPoint, b: GeoPoint): Double {
            val dLat = Math.toRadians(b.latitudeDegrees - a.latitudeDegrees)
            val dLon = Math.toRadians(b.longitudeDegrees - a.longitudeDegrees)
            val lat1 = Math.toRadians(a.latitudeDegrees)
            val lat2 = Math.toRadians(b.latitudeDegrees)
            val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
            return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt((1.0 - h).coerceAtLeast(0.0)))
        }
    }
}

@Serializable
data class RouteAccessCatalog(
    val origin: DataOrigin,
    val fetchedAtEpochMillis: Long,
    val corridors: List<VerifiedRouteCorridor>,
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion == 1 && fetchedAtEpochMillis >= 0)
        require(origin == DataOrigin.NAVI_SDK || origin == DataOrigin.DEMO)
        require(corridors.size <= MAX_CORRIDORS && corridors.map { it.id }.distinct().size == corridors.size)
        require(corridors.all { corridor ->
            corridor.approaches.all { (it.facilityId.startsWith("demo-")) == (origin == DataOrigin.DEMO) }
        })
    }

    fun evaluate(
        journey: DataSnapshot<JourneySnapshot>,
        nowEpochMillis: Long,
        policy: FacilityPolicy = FacilityPolicy(),
    ): DataSnapshot<RouteFacilityEvidence> {
        require(nowEpochMillis >= 0)
        if (journey.status == DataStatus.EMPTY) return DataSnapshot(origin, DataStatus.EMPTY)
        val route = journey.readyValue ?: return DataSnapshot(origin, DataStatus.UNSUPPORTED)
        if (journey.origin != origin) return DataSnapshot(origin, DataStatus.UNSUPPORTED)
        val observedAt = journey.observedAtEpochMillis ?: return DataSnapshot(origin, DataStatus.STALE)
        if (!FacilityPolicy.fresh(observedAt, nowEpochMillis, policy.routeFreshnessMillis)) {
            return DataSnapshot(origin, DataStatus.STALE)
        }
        if (!FacilityPolicy.fresh(fetchedAtEpochMillis, nowEpochMillis, policy.restAreaDataAgeMillis)) {
            return DataSnapshot(origin, DataStatus.STALE)
        }
        val dest = route.destination ?: return DataSnapshot(origin, DataStatus.UNSUPPORTED)
        if (dest.latitudeDegrees == 0.0 && dest.longitudeDegrees == 0.0) {
            return DataSnapshot(origin, DataStatus.UNSUPPORTED)
        }
        val remainingDistance = route.remainingDistanceMeters ?: return DataSnapshot(origin, DataStatus.UNSUPPORTED)
        val eta = route.estimatedArrivalAtEpochMillis ?: return DataSnapshot(origin, DataStatus.UNSUPPORTED)
        if (remainingDistance <= 0L || eta <= nowEpochMillis) return DataSnapshot(origin, DataStatus.UNSUPPORTED)

        val matches = corridors.filter { it.matches(route, dest, remainingDistance) }
        if (matches.size != 1) return DataSnapshot(origin, DataStatus.UNSUPPORTED)
        val corridor = matches.single()
        val remainingSeconds = (eta - nowEpochMillis) / 1_000L
        val approaches = if (remainingSeconds < 2L || remainingDistance <= 1L) {
            emptyList()
        } else {
            corridor.approaches.mapNotNull { item ->
                val distanceAhead = remainingDistance - item.remainingDistanceToDestinationMeters
                if (distanceAhead <= 0L || distanceAhead >= remainingDistance) {
                    null
                } else {
                    val durationAhead = ((remainingSeconds * distanceAhead) / remainingDistance)
                        .coerceIn(1L, remainingSeconds - 1L)
                    FacilityApproach(
                        facilityId = item.facilityId,
                        access = item.access,
                        travelDurationSeconds = durationAhead,
                        distanceAlongRouteMeters = distanceAhead,
                        navigationLocation = item.navigationLocation,
                        navigationPoiId = item.navigationPoiId,
                    )
                }
            }
        }
        return DataSnapshot(
            origin = origin,
            status = DataStatus.READY,
            value = RouteFacilityEvidence(route.id, approaches, corridor.coversRemainingRestAreas),
            observedAtEpochMillis = nowEpochMillis,
        )
    }

    companion object {
        const val PATH = "/api/facilities/route-access"
        const val MAX_CORRIDORS = 500
        const val MAX_JSON_BYTES = 1_000_000
    }
}

object RouteAccessCatalogCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = DataSnapshot.serializer(RouteAccessCatalog.serializer())

    fun encode(snapshot: DataSnapshot<RouteAccessCatalog>): String = json.encodeToString(serializer, snapshot)

    fun decode(text: String): DataSnapshot<RouteAccessCatalog> {
        require(text.toByteArray(Charsets.UTF_8).size <= RouteAccessCatalog.MAX_JSON_BYTES)
        return json.decodeFromString(serializer, text).also { snapshot ->
            require(snapshot.origin == DataOrigin.NAVI_SDK || snapshot.origin == DataOrigin.DEMO)
            require(snapshot.value?.origin?.let { it == snapshot.origin } != false)
        }
    }
}

fun interface RouteAccessCatalogClient { fun fetch(): DataSnapshot<RouteAccessCatalog> }
interface RouteAccessCatalogStore {
    fun read(): DataSnapshot<RouteAccessCatalog>?
    fun write(snapshot: DataSnapshot<RouteAccessCatalog>)
}

/**
 * Provides route-bound facility access evidence by matching active journeys against a verified
 * route-access catalog. Unverified routes and stale/failed catalogs never fabricate candidates.
 */
class RouteAccessRepository(
    private val client: RouteAccessCatalogClient,
    private val store: RouteAccessCatalogStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val policy: FacilityPolicy = FacilityPolicy(),
) {
    private val mutable = MutableStateFlow(DataSnapshot<RouteAccessCatalog>(DataOrigin.NAVI_SDK, DataStatus.LOADING))
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private var loaded = false

    suspend fun refresh() {
        if (!mutex.tryLock()) return
        try {
            if (!loaded) {
                loaded = true
                val cached = try { withContext(io) { store.read() } } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                if (cached?.origin == DataOrigin.NAVI_SDK && cached.value?.origin == DataOrigin.NAVI_SDK) {
                    mutable.value = cached.copy(status = DataStatus.STALE)
                }
            }
            val received = try { withContext(io) { client.fetch() } } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    fail(DataStatus.FAILED)
                    return
                }
            val now = clock()
            val response = if (received.value != null) received.copy(observedAtEpochMillis = now) else received
            val catalog = response.value
            if (response.origin != DataOrigin.NAVI_SDK || catalog?.origin?.let { it != DataOrigin.NAVI_SDK } == true ||
                (catalog != null && (catalog.fetchedAtEpochMillis > now || response.observedAtEpochMillis == null ||
                    response.observedAtEpochMillis > now))
            ) {
                fail(DataStatus.FAILED)
                return
            }
            if (response.status != DataStatus.READY) {
                fail(
                    when (response.status) {
                        DataStatus.UNSUPPORTED -> DataStatus.UNSUPPORTED
                        DataStatus.STALE -> DataStatus.STALE
                        else -> DataStatus.FAILED
                    },
                )
                return
            }
            if (catalog == null || !FacilityPolicy.fresh(catalog.fetchedAtEpochMillis, now, policy.restAreaDataAgeMillis) ||
                !cacheFresh(response, now)
            ) {
                mutable.value = response.copy(status = DataStatus.STALE)
                return
            }
            try { withContext(io) { store.write(response) } } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Non-fatal: keep fresh in-memory snapshot for this session. */ }
            mutable.value = response
        } finally {
            mutex.unlock()
        }
    }

    fun tick() {
        val snapshot = state.value
        val catalog = snapshot.readyValue ?: return
        val now = clock()
        if (!FacilityPolicy.fresh(catalog.fetchedAtEpochMillis, now, policy.restAreaDataAgeMillis) ||
            !cacheFresh(snapshot, now)
        ) {
            mutable.value = snapshot.copy(status = DataStatus.STALE)
        }
    }

    fun evaluate(
        journey: DataSnapshot<JourneySnapshot>,
        nowEpochMillis: Long = clock(),
    ): DataSnapshot<RouteFacilityEvidence> {
        if (journey.status == DataStatus.EMPTY) return DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.EMPTY)
        val snapshot = state.value
        return when (snapshot.status) {
            DataStatus.READY -> {
                val catalog = snapshot.readyValue ?: return DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.UNSUPPORTED)
                if (!cacheFresh(snapshot, nowEpochMillis)) {
                    DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.STALE)
                } else {
                    catalog.evaluate(journey, nowEpochMillis, policy)
                }
            }
            DataStatus.STALE -> DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.STALE)
            DataStatus.FAILED -> DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.FAILED)
            DataStatus.UNSUPPORTED, DataStatus.LOADING, DataStatus.EMPTY ->
                DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.UNSUPPORTED)
            DataStatus.PERMISSION_DENIED -> DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.PERMISSION_DENIED)
        }
    }

    private fun cacheFresh(snapshot: DataSnapshot<RouteAccessCatalog>, nowEpochMillis: Long): Boolean =
        snapshot.observedAtEpochMillis?.let {
            FacilityPolicy.fresh(it, nowEpochMillis, policy.catalogCacheMillis)
        } ?: false

    private fun fail(status: DataStatus) {
        val previous = mutable.value
        val stale = previous.value?.let { previous.copy(status = DataStatus.STALE) }
        mutable.value = stale ?: DataSnapshot(DataOrigin.NAVI_SDK, status)
    }
}
