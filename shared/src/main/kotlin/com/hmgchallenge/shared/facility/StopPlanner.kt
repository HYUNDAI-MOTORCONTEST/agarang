package com.hmgchallenge.shared.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot
import kotlinx.serialization.Serializable

/** Only a route provider may supply this evidence. Catalog coordinates do not prove access. */
@Serializable
data class FacilityApproach(
    val facilityId: String,
    val access: RouteAccess,
    val travelDurationSeconds: Long? = null,
    val distanceAlongRouteMeters: Long? = null,
    val navigationLocation: GeoPoint? = null,
    val navigationPoiId: String? = null,
) {
    init {
        require(facilityId.isNotBlank())
        require(travelDurationSeconds == null || travelDurationSeconds >= 0)
        require(distanceAlongRouteMeters == null || distanceAlongRouteMeters >= 0)
        require(navigationPoiId == null || navigationPoiId.isNotBlank())
    }
}

@Serializable
data class RouteFacilityEvidence(
    val journeyId: String,
    val approaches: List<FacilityApproach>,
    /** True only if the provider covered ALL remaining rest areas, including absent catalog entries. */
    val coversRemainingRestAreas: Boolean = false,
) {
    init {
        require(journeyId.isNotBlank())
        require(approaches.map { it.facilityId }.distinct().size == approaches.size)
    }
}

enum class StopPlanningProblem { NONE, ROUTE_UNAVAILABLE, CATALOG_UNAVAILABLE, CATALOG_STALE, ACCESS_UNVERIFIED }
enum class LastRestAreaStatus { AVAILABLE, NO_REST_AREA, UNVERIFIED }

data class LastRestArea(val status: LastRestAreaStatus, val candidate: StopCandidate? = null) {
    init {
        require((status == LastRestAreaStatus.AVAILABLE) == (candidate != null))
        require(candidate == null || candidate.facility.type == FacilityType.REST_AREA)
    }
}

data class StopPlanningResult(
    val journeyId: String?,
    val fullCare: StopPlan,
    val nearestSafeStop: StopPlan,
    val lastRestArea: LastRestArea,
    val problem: StopPlanningProblem,
    val isDemo: Boolean,
)

/** Product freshness policy, not a guarantee that a facility is open or safe at this instant. */
data class FacilityPolicy(
    val routeFreshnessMillis: Long = 60_000,
    val catalogCacheMillis: Long = 86_400_000,
    val restAreaDataAgeMillis: Long = 200L * 86_400_000,
    val shelterDataAgeMillis: Long = 400L * 86_400_000,
) {
    init { require(listOf(routeFreshnessMillis, catalogCacheMillis, restAreaDataAgeMillis, shelterDataAgeMillis).all { it > 0 }) }

    fun catalogFresh(catalog: FacilityCatalog, now: Long): Boolean =
        catalog.fetchedAtEpochMillis <= now && (catalog.entries.isEmpty() || catalog.entries.any { facilityFresh(it.facility, now) })

    fun cacheFresh(snapshot: DataSnapshot<FacilityCatalog>, now: Long): Boolean = snapshot.observedAtEpochMillis?.let {
        fresh(it, now, catalogCacheMillis)
    } ?: false

    fun facilityFresh(facility: CareFacility, now: Long): Boolean = facility.verifiedAtEpochMillis?.let {
        fresh(it, now, if (facility.type == FacilityType.REST_AREA) restAreaDataAgeMillis else shelterDataAgeMillis)
    } ?: false

    companion object {
        fun fresh(observed: Long, now: Long, ttl: Long) = observed >= 0 && now >= observed && now - observed < ttl
    }
}

class StopPlanner(private val policy: FacilityPolicy = FacilityPolicy()) {
    fun plan(
        journey: DataSnapshot<JourneySnapshot>,
        facilities: DataSnapshot<FacilityCatalog>,
        evidence: DataSnapshot<RouteFacilityEvidence>,
        nowEpochMillis: Long,
    ): StopPlanningResult {
        require(nowEpochMillis >= 0)
        val route = journey.readyValue
        val isDemo = journey.origin == DataOrigin.DEMO || route?.isSimulation == true || facilities.origin == DataOrigin.DEMO
        fun unavailable(problem: StopPlanningProblem): StopPlanningResult {
            val status = if (problem == StopPlanningProblem.ROUTE_UNAVAILABLE) StopPlanStatus.ROUTE_UNAVAILABLE
                else StopPlanStatus.NO_CONFIRMED_CANDIDATE
            return StopPlanningResult(route?.id, StopPlan(StopPlanType.FULL_CARE, status),
                StopPlan(StopPlanType.NEAREST_SAFE_STOP, status), LastRestArea(LastRestAreaStatus.UNVERIFIED), problem, isDemo)
        }
        val routeOriginValid = journey.origin == DataOrigin.NAVI_SDK || journey.origin == DataOrigin.DEMO
        if (route == null || !routeOriginValid || !fresh(journey, nowEpochMillis) ||
            route.estimatedArrivalAtEpochMillis == null || route.estimatedArrivalAtEpochMillis <= nowEpochMillis ||
            route.remainingDistanceMeters == null || route.remainingDistanceMeters <= 0
        ) return unavailable(StopPlanningProblem.ROUTE_UNAVAILABLE)

        val catalog = facilities.readyValue ?: return unavailable(
            if (facilities.status == DataStatus.STALE) StopPlanningProblem.CATALOG_STALE else StopPlanningProblem.CATALOG_UNAVAILABLE,
        )
        if (catalog.origin != facilities.origin ||
            (journey.origin == DataOrigin.DEMO) != (catalog.origin == DataOrigin.DEMO)
        ) return unavailable(StopPlanningProblem.CATALOG_UNAVAILABLE)
        if (!policy.catalogFresh(catalog, nowEpochMillis) || !policy.cacheFresh(facilities, nowEpochMillis)) {
            return unavailable(StopPlanningProblem.CATALOG_STALE)
        }
        val access = evidence.readyValue
        if (access == null || evidence.origin != journey.origin || !fresh(evidence, nowEpochMillis) ||
            access.journeyId != route.id
        ) return unavailable(StopPlanningProblem.ACCESS_UNVERIFIED)

        val byId = catalog.entries.associateBy { it.facility.id }
        val remainingSeconds = (route.estimatedArrivalAtEpochMillis - nowEpochMillis) / 1_000
        fun candidate(approach: FacilityApproach): StopCandidate? {
            val facility = byId[approach.facilityId]?.facility ?: return null
            val seconds = approach.travelDurationSeconds ?: return null
            val meters = approach.distanceAlongRouteMeters ?: return null
            if (!policy.facilityFresh(facility, nowEpochMillis) || approach.access != RouteAccess.CONFIRMED_ON_ROUTE ||
                seconds <= 0 || seconds >= remainingSeconds || meters <= 0 || meters >= route.remainingDistanceMeters ||
                approach.navigationLocation == null
            ) return null
            return StopCandidate(facility, catalog.origin, approach.access, seconds, meters,
                approach.navigationLocation, route.id, evidence.observedAtEpochMillis, approach.navigationPoiId)
        }
        val candidates = access.approaches.mapNotNull(::candidate)
        val nearest = candidates.sortedWith(compareBy<StopCandidate> { it.travelDurationSeconds }.thenBy { it.facility.id })
        fun stopPlan(type: StopPlanType, values: List<StopCandidate>) = StopPlan(type,
            if (values.isEmpty()) StopPlanStatus.NO_CONFIRMED_CANDIDATE else StopPlanStatus.AVAILABLE, values)
        // Unknown coverage, stale/missing rows or unknown access preclude a "last" assertion.
        val incomplete = !access.coversRemainingRestAreas || access.approaches.any {
            val f = byId[it.facilityId]?.facility
            f == null || (f.type == FacilityType.REST_AREA &&
                (it.access == RouteAccess.UNKNOWN || (it.access == RouteAccess.CONFIRMED_ON_ROUTE && candidate(it) == null)))
        }
        val last = if (incomplete) LastRestArea(LastRestAreaStatus.UNVERIFIED) else {
            val rest = candidates.filter { it.facility.type == FacilityType.REST_AREA }
                .maxWithOrNull(compareBy<StopCandidate> { it.distanceAlongRouteMeters }.thenBy { it.facility.id })
            if (rest == null) LastRestArea(LastRestAreaStatus.NO_REST_AREA) else LastRestArea(LastRestAreaStatus.AVAILABLE, rest)
        }
        val fullCare = nearest.filter { it.facility.nursingRoom == AmenityStatus.AVAILABLE }
        return StopPlanningResult(route.id, stopPlan(StopPlanType.FULL_CARE, fullCare),
            stopPlan(StopPlanType.NEAREST_SAFE_STOP, nearest), last, StopPlanningProblem.NONE, isDemo)
    }

    private fun fresh(snapshot: DataSnapshot<*>, now: Long) = snapshot.observedAtEpochMillis?.let {
        FacilityPolicy.fresh(it, now, policy.routeFreshnessMillis)
    } ?: false
}

data class LastRestAreaAlert(val journeyId: String, val candidate: StopCandidate, val isDemo: Boolean)

/** One foreground banner per journey + last facility. No sound, popup or automatic navigation. */
class LastRestAreaAlerts(private val leadTimeSeconds: Long = 600) {
    init { require(leadTimeSeconds > 0) }
    private var journeyId: String? = null
    private val shown = mutableSetOf<String>()

    fun poll(result: StopPlanningResult): LastRestAreaAlert? {
        val id = result.journeyId ?: return null
        if (journeyId != id) { journeyId = id; shown.clear() }
        val candidate = result.lastRestArea.candidate ?: return null
        val seconds = candidate.travelDurationSeconds ?: return null
        if (seconds !in 1..leadTimeSeconds || !shown.add(candidate.facility.id)) return null
        return LastRestAreaAlert(id, candidate, result.isDemo)
    }
}
