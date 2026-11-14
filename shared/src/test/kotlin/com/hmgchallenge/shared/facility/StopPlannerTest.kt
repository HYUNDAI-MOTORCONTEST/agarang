package com.hmgchallenge.shared.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.journey.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StopPlannerTest {
    private val now = 1_800_000_000_000L
    private val demo = DemoStopScenario.create(now)
    private val planner = StopPlanner()
    private val route get() = demo.journey
    private val catalog get() = demo.facilities
    private val evidence get() = demo.evidence
    private fun plan(
        facilities: DataSnapshot<FacilityCatalog> = catalog,
        access: DataSnapshot<RouteFacilityEvidence> = evidence,
        journey: DataSnapshot<com.hmgchallenge.shared.journey.JourneySnapshot> = route,
        at: Long = now,
    ) = planner.plan(journey, facilities, access, at)
    private fun approaches(values: List<FacilityApproach>, complete: Boolean = true) =
        evidence.copy(value = evidence.readyValue!!.copy(approaches = values, coversRemainingRestAreas = complete))

    @Test
    fun fixedRouteSeparatesFullCareNearestAndLastRestArea() {
        val result = plan()
        assertEquals("demo-full-care", result.fullCare.candidates.single().facility.id)
        assertEquals("demo-shelter", result.nearestSafeStop.candidates.first().facility.id)
        assertEquals("demo-last-rest", result.lastRestArea.candidate?.facility?.id)
        assertEquals(480, result.lastRestArea.candidate?.travelDurationSeconds)
        assertEquals(StopPlanningProblem.NONE, result.problem)
        assertTrue(result.isDemo)
        assertTrue(result.nearestSafeStop.candidates.none { it.canRequestNavigation })
    }

    @Test
    fun oppositeDirectionAndInaccessibleFacilitiesAreExcludedEvenWhenCloser() {
        assertEquals(listOf("demo-shelter", "demo-full-care", "demo-last-rest"),
            plan().nearestSafeStop.candidates.map { it.facility.id })
    }

    @Test
    fun unknownNursingRoomDoesNotBecomeFullCareButMayBeASafeStop() {
        val entries = catalog.readyValue!!.entries.map { it.copy(facility = it.facility.copy(nursingRoom = AmenityStatus.UNKNOWN)) }
        val result = plan(facilities = catalog.copy(value = catalog.readyValue!!.copy(entries = entries)))
        assertEquals(StopPlanStatus.NO_CONFIRMED_CANDIDATE, result.fullCare.status)
        assertEquals(StopPlanStatus.AVAILABLE, result.nearestSafeStop.status)
    }

    @Test
    fun unknownAccessCannotBeInferredFromCoordinatesOrRoadLabels() {
        val result = plan(access = approaches(evidence.readyValue!!.approaches.map { it.copy(access = RouteAccess.UNKNOWN) }))
        assertTrue(result.nearestSafeStop.candidates.isEmpty())
        assertEquals(LastRestAreaStatus.UNVERIFIED, result.lastRestArea.status)
    }

    @Test
    fun missingTimeDistanceOrNavigationEntranceIsNotAnEligibleCandidate() {
        val first = evidence.readyValue!!.approaches.first()
        for (item in listOf(first.copy(travelDurationSeconds = null), first.copy(distanceAlongRouteMeters = null),
            first.copy(navigationLocation = null), first.copy(travelDurationSeconds = 0), first.copy(distanceAlongRouteMeters = 0))) {
            assertTrue(plan(access = approaches(listOf(item))).nearestSafeStop.candidates.isEmpty())
        }
    }

    @Test
    fun facilityAtOrBeyondDestinationIsNotAnOnwardStop() {
        val first = evidence.readyValue!!.approaches.first()
        for (item in listOf(first.copy(travelDurationSeconds = 3_600), first.copy(travelDurationSeconds = Long.MAX_VALUE),
            first.copy(distanceAlongRouteMeters = 50_000), first.copy(distanceAlongRouteMeters = Long.MAX_VALUE))) {
            assertTrue(plan(access = approaches(listOf(item))).nearestSafeStop.candidates.isEmpty())
        }
    }

    @Test
    fun staleFutureOrChangedRouteEvidenceDoesNotUseOldCandidates() {
        for (access in listOf(evidence.copy(status = DataStatus.STALE),
            evidence.copy(observedAtEpochMillis = now - 60_000), evidence.copy(observedAtEpochMillis = now + 1),
            evidence.copy(value = evidence.readyValue!!.copy(journeyId = "old-route")), evidence.copy(origin = DataOrigin.PUBLIC_DATA))) {
            assertEquals(StopPlanningProblem.ACCESS_UNVERIFIED, plan(access = access).problem)
        }
    }

    @Test
    fun unavailableExpiredOrArrivedRouteIsExplicit() {
        for (journey in listOf(route.copy(status = DataStatus.STALE), route.copy(observedAtEpochMillis = now - 60_000),
            route.copy(observedAtEpochMillis = now + 1), route.copy(value = route.readyValue!!.copy(estimatedArrivalAtEpochMillis = now)),
            route.copy(value = route.readyValue!!.copy(remainingDistanceMeters = null)), route.copy(origin = DataOrigin.CAREGIVER))) {
            assertEquals(StopPlanStatus.ROUTE_UNAVAILABLE, plan(journey = journey).fullCare.status)
        }
    }

    @Test
    fun offlineCatalogIsDisplayOnlyAndNeverUsedToRecommend() {
        assertEquals(StopPlanningProblem.CATALOG_STALE, plan(facilities = catalog.copy(status = DataStatus.STALE)).problem)
        assertTrue(plan(facilities = catalog.copy(status = DataStatus.STALE)).nearestSafeStop.candidates.isEmpty())
    }

    @Test
    fun oldPublishedDataCannotBeRenewedByANewHttpObservation() {
        val old = catalog.copy(value = catalog.readyValue!!.copy(entries = catalog.readyValue!!.entries.map {
            it.copy(facility = it.facility.copy(verifiedAtEpochMillis = now - 401L * 86_400_000))
        }), observedAtEpochMillis = now)
        assertEquals(StopPlanningProblem.CATALOG_STALE, plan(facilities = old).problem)
    }

    @Test
    fun oldOrUnverifiedFacilityIsExcludedWithoutRewritingItsAmenityStatus() {
        val entries = catalog.readyValue!!.entries.map { it.copy(facility = it.facility.copy(verifiedAtEpochMillis = null)) }
        val result = plan(facilities = catalog.copy(value = catalog.readyValue!!.copy(entries = entries)))
        assertTrue(result.fullCare.candidates.isEmpty())
        assertEquals(LastRestAreaStatus.UNVERIFIED, result.lastRestArea.status)
    }

    @Test
    fun dataAgeUsesPublishedDateNotDownloadDateAndDifferentUpdateCycles() {
        val policy = FacilityPolicy()
        val rest = catalog.readyValue!!.entries[1].facility
        val shelter = catalog.readyValue!!.entries.first().facility
        assertFalse(policy.facilityFresh(rest.copy(verifiedAtEpochMillis = now - 200L * 86_400_000), now))
        assertTrue(policy.facilityFresh(shelter.copy(verifiedAtEpochMillis = now - 200L * 86_400_000), now))
        assertFalse(policy.facilityFresh(shelter.copy(verifiedAtEpochMillis = now - 400L * 86_400_000), now))
    }

    @Test
    fun incompleteCoverageDoesNotAssertTheLastKnownRestAreaIsTheLast() {
        assertEquals(LastRestAreaStatus.UNVERIFIED,
            plan(access = approaches(evidence.readyValue!!.approaches, complete = false)).lastRestArea.status)
        val missing = evidence.readyValue!!.approaches + FacilityApproach("missing", RouteAccess.CONFIRMED_ON_ROUTE, 900, 9_000, GeoPoint(1.0, 1.0))
        assertEquals(LastRestAreaStatus.UNVERIFIED, plan(access = approaches(missing)).lastRestArea.status)
    }

    @Test
    fun emptyCatalogAndNoRestAreaAreNormalResults() {
        val result = plan(facilities = catalog.copy(value = catalog.readyValue!!.copy(entries = emptyList())), access = approaches(emptyList()))
        assertEquals(StopPlanStatus.NO_CONFIRMED_CANDIDATE, result.fullCare.status)
        assertEquals(LastRestAreaStatus.NO_REST_AREA, result.lastRestArea.status)
    }

    @Test
    fun demoCatalogCannotMixIntoRealRouteOrRealCatalogIntoDemo() {
        assertEquals(StopPlanningProblem.CATALOG_UNAVAILABLE, plan(journey = route.copy(origin = DataOrigin.NAVI_SDK)).problem)
        assertEquals(StopPlanningProblem.CATALOG_UNAVAILABLE, plan(facilities = catalog.copy(origin = DataOrigin.PUBLIC_DATA)).problem)
    }

    @Test
    fun lastRestAreaUsesDistanceAlongRouteRatherThanEtaOrdering() {
        val swapped = evidence.readyValue!!.approaches.map {
            if (it.facilityId == "demo-full-care") it.copy(travelDurationSeconds = 900) else it
        }
        assertEquals("demo-last-rest", plan(access = approaches(swapped)).lastRestArea.candidate?.facility?.id)
    }

    @Test
    fun realCandidateCarriesRouteBindingAndProviderEntranceRatherThanCentroid() {
        val publicCatalog = catalog.readyValue!!.copy(origin = DataOrigin.PUBLIC_DATA,
            entries = catalog.readyValue!!.entries.map { it.copy(facility = it.facility.copy(id = it.facility.id.removePrefix("demo-"))) })
        val realAccess = evidence.copy(origin = DataOrigin.NAVI_SDK, value = evidence.readyValue!!.copy(
            approaches = evidence.readyValue!!.approaches.map { it.copy(facilityId = it.facilityId.removePrefix("demo-"),
                navigationLocation = GeoPoint(37.1, 127.1), navigationPoiId = "provider-${it.facilityId}") }))
        val result = plan(facilities = catalog.copy(origin = DataOrigin.PUBLIC_DATA, value = publicCatalog),
            access = realAccess, journey = route.copy(origin = DataOrigin.NAVI_SDK))
        val stop = result.nearestSafeStop.candidates.first()
        assertTrue(stop.canRequestNavigation)
        assertEquals(route.readyValue!!.id, stop.journeyId)
        assertEquals(now, stop.accessVerifiedAtEpochMillis)
        assertEquals(GeoPoint(37.1, 127.1), stop.navigationLocation)
        assertEquals("provider-demo-shelter", stop.navigationPoiId)
        assertEquals(GeoPoint(0.0, 0.0), stop.facility.location)
        assertFalse(result.isDemo)
    }

    @Test
    fun finalRestAreaBannerAppearsOnceWithinTenMinutesAndResetsForNewJourney() {
        val alerts = LastRestAreaAlerts()
        val result = plan()
        assertEquals("demo-last-rest", alerts.poll(result)?.candidate?.facility?.id)
        assertNull(alerts.poll(result))
        assertNull(alerts.poll(result.copy(lastRestArea = LastRestArea(LastRestAreaStatus.UNVERIFIED))))
        assertNull(alerts.poll(result))
        assertEquals("another", alerts.poll(result.copy(journeyId = "another"))?.journeyId)
    }

    @Test
    fun lastAreaTooFarDoesNotConsumeItsFutureBanner() {
        val alerts = LastRestAreaAlerts()
        val result = plan()
        val later = result.copy(lastRestArea = result.lastRestArea.copy(candidate = result.lastRestArea.candidate!!.copy(travelDurationSeconds = 601)))
        assertNull(alerts.poll(later))
        assertTrue(alerts.poll(result) != null)
    }

    @Test
    fun verifiedRouteAccessCatalogComputesRemainingStopsAndRejectsUnregisteredRoutes() {
        val sources = listOf(FacilityDataSource("src", "한국도로공사", "휴게소", "https://example.invalid/r", "무료", "반기"))
        val entries = listOf(
            FacilityListing(CareFacility("public-shelter", "오산", GeoPoint(37.1567, 127.0871), FacilityType.DROWSINESS_SHELTER, AmenityStatus.UNKNOWN, verifiedAtEpochMillis = now), "src"),
            FacilityListing(CareFacility("public-giheung", "기흥(부산)", GeoPoint(37.2351, 127.1045), FacilityType.REST_AREA, AmenityStatus.AVAILABLE, verifiedAtEpochMillis = now), "src"),
            FacilityListing(CareFacility("public-anseong", "안성(부산)", GeoPoint(37.0134, 127.1448), FacilityType.REST_AREA, AmenityStatus.AVAILABLE, verifiedAtEpochMillis = now), "src"),
            FacilityListing(CareFacility("public-jukjeon", "죽전(서울)", GeoPoint(37.3323, 127.1047), FacilityType.REST_AREA, AmenityStatus.AVAILABLE, verifiedAtEpochMillis = now), "src"),
        )
        val publicCatalog = DataSnapshot(DataOrigin.PUBLIC_DATA, DataStatus.READY, FacilityCatalog(DataOrigin.PUBLIC_DATA, now, sources, entries), now)
        val accessCatalog = RouteAccessCatalog(
            origin = DataOrigin.NAVI_SDK,
            fetchedAtEpochMillis = now,
            corridors = listOf(
                VerifiedRouteCorridor(
                    id = "gyeongbu-busan-cheonan",
                    destinationLabel = "천안분기점",
                    destinationLocation = GeoPoint(36.72, 127.27),
                    destinationPoiId = "dest-cheonan",
                    totalDistanceMeters = 80_000,
                    totalDurationSeconds = 3_600,
                    coversRemainingRestAreas = true,
                    approaches = listOf(
                        VerifiedCorridorApproach("public-jukjeon", RouteAccess.OPPOSITE_DIRECTION, 40_000, 1_800),
                        VerifiedCorridorApproach("public-giheung", RouteAccess.CONFIRMED_ON_ROUTE, 58_000, 2_610, GeoPoint(37.2354, 127.1042), "poi-giheung"),
                        VerifiedCorridorApproach("public-shelter", RouteAccess.CONFIRMED_ON_ROUTE, 49_000, 2_200, GeoPoint(37.1569, 127.0870), "poi-osan"),
                        VerifiedCorridorApproach("public-anseong", RouteAccess.CONFIRMED_ON_ROUTE, 32_000, 1_440, GeoPoint(37.0136, 127.1445), "poi-anseong"),
                    ),
                ),
            ),
        )
        // Remaining 55 km < Giheung (58 km to destination -> already passed), but Osan (49 km) & Anseong (32 km) are ahead.
        val activeRoute = DataSnapshot(
            DataOrigin.NAVI_SDK,
            DataStatus.READY,
            com.hmgchallenge.shared.journey.JourneySnapshot(
                id = "route-gyeongbu",
                destinationLabel = "천안분기점",
                estimatedArrivalAtEpochMillis = now + 2_400_000,
                remainingDistanceMeters = 55_000,
                destination = GeoPoint(36.7205, 127.2705),
                destinationPoiId = "dest-cheonan",
            ),
            now,
        )
        val evidenceSnapshot = accessCatalog.evaluate(activeRoute, now)
        assertEquals(DataStatus.READY, evidenceSnapshot.status)
        val planned = planner.plan(activeRoute, publicCatalog, evidenceSnapshot, now)
        assertEquals(StopPlanningProblem.NONE, planned.problem)
        assertEquals(listOf("public-shelter", "public-anseong"), planned.nearestSafeStop.candidates.map { it.facility.id })
        assertEquals(listOf("public-anseong"), planned.fullCare.candidates.map { it.facility.id })
        assertEquals("public-anseong", planned.lastRestArea.candidate?.facility?.id)
        val stop = planned.fullCare.candidates.single()
        assertTrue(stop.canRequestNavigation)
        assertEquals(GeoPoint(37.0136, 127.1445), stop.navigationLocation)
        assertEquals("poi-anseong", stop.navigationPoiId)
        assertEquals(23_000L, stop.distanceAlongRouteMeters)

        // Unregistered destination stays UNSUPPORTED -> ACCESS_UNVERIFIED.
        val unknownRoute = activeRoute.copy(value = activeRoute.readyValue!!.copy(
            destination = GeoPoint(35.1796, 129.0756), destinationPoiId = "dest-unknown"))
        val unsupportedEvidence = accessCatalog.evaluate(unknownRoute, now)
        assertEquals(DataStatus.UNSUPPORTED, unsupportedEvidence.status)
        assertEquals(StopPlanningProblem.ACCESS_UNVERIFIED, planner.plan(unknownRoute, publicCatalog, unsupportedEvidence, now).problem)
    }
}
