package com.hmgchallenge.app.response

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.hmgchallenge.app.care.AtomicCareRecordStore
import com.hmgchallenge.app.departure.DepartureController
import com.hmgchallenge.app.departure.DepartureUiState
import com.hmgchallenge.app.driving.UxRestrictionsState
import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.care.CareRecordCodec
import com.hmgchallenge.shared.care.CareRecordStore
import com.hmgchallenge.shared.care.CareRepository
import com.hmgchallenge.shared.care.CareStoreStatus
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.departure.DepartureDemoSituation
import com.hmgchallenge.shared.facility.AmenityStatus
import com.hmgchallenge.shared.facility.CareFacility
import com.hmgchallenge.shared.facility.FacilityCatalog
import com.hmgchallenge.shared.facility.FacilityDataSource
import com.hmgchallenge.shared.facility.FacilityListing
import com.hmgchallenge.shared.facility.FacilityState
import com.hmgchallenge.shared.facility.FacilityType
import com.hmgchallenge.shared.facility.RouteAccess
import com.hmgchallenge.shared.facility.RouteAccessCatalog
import com.hmgchallenge.shared.facility.StopPlanType
import com.hmgchallenge.shared.facility.StopPlanningProblem
import com.hmgchallenge.shared.facility.VerifiedCorridorApproach
import com.hmgchallenge.shared.facility.VerifiedRouteCorridor
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot
import com.hmgchallenge.shared.response.ResponseNotice
import com.hmgchallenge.shared.response.ResponsePhase
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Runs the real Android controller with private QA stores; never mutates the user's journal. */
class CryResponseSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        fun originalHashes() = listOf("care-record.json", "departure.json").map { name ->
            File(targetContext.filesDir, name).takeIf { it.exists() }?.readBytes()?.let { MessageDigest.getInstance("SHA-256").digest(it).toList() }
        }
        val original = originalHashes()
        val directory = File(targetContext.filesDir, "issue8-qa-${System.nanoTime()}")
        val scopes = mutableListOf<CoroutineScope>()
        var code = Activity.RESULT_OK
        try {
            check(directory.mkdir())
            runBlocking {
                val careFile = File(directory, "care.json")
                val store = AtomicCareRecordStore(careFile)
                val care = CareRepository(store)
                care.load(); care.saveProfile(2, 120)
                val realBeforeDemo = careFile.readBytes().toList()
                val ux = MutableStateFlow(UxRestrictionsState.Parked)
                val route = MutableStateFlow(DataSnapshot<JourneySnapshot>(DataOrigin.NAVI_SDK, DataStatus.FAILED))
                val catalog = MutableStateFlow(FacilityState(DataSnapshot<FacilityCatalog>(DataOrigin.PUBLIC_DATA, DataStatus.FAILED)))
                val routeAccess = MutableStateFlow(DataSnapshot<RouteAccessCatalog>(DataOrigin.NAVI_SDK, DataStatus.UNSUPPORTED))
                val ticks = MutableStateFlow(System.currentTimeMillis())
                suspend fun createController(
                    repository: CareRepository,
                    folder: File,
                    access: MutableStateFlow<DataSnapshot<RouteAccessCatalog>> = routeAccess,
                ): DepartureController = withContext(Dispatchers.Main) {
                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scopes += it }
                    DepartureController(folder, repository, route, catalog, ticks, ux, scope, access).also { it.load() }
                }
                suspend fun await(controller: DepartureController, predicate: (DepartureUiState) -> Boolean) =
                    withTimeout(8_000) { controller.state.first { predicate(it) } }
                val controller = createController(care, directory)
                await(controller) { it.preparation.record != null && it.care.record != null }
                withContext(Dispatchers.Main) { controller.startResponseDemo(DepartureDemoSituation.NORMAL) }
                val offered = await(controller) { it.isDemo && it.response.phase == ResponsePhase.SUGGESTED }
                withContext(Dispatchers.Main) { controller.confirmResponse(offered.response.episodeId!!) }
                await(controller) { it.response.active }
                withContext(Dispatchers.Main) { controller.selectResponseStop(StopPlanType.FULL_CARE) }
                val selected = await(controller) { it.response.selected != null }
                check(selected.response.selected!!.candidate.facility.id == "demo-full-care")
                check(withContext(Dispatchers.Main) { controller.responseNavigationCandidate() } == null)
                result.putString("demoCannotRequestRealNavigation", "passed")
                val eventsBeforeDriving = selected.care.record!!.events
                withContext(Dispatchers.Main) {
                    ux.value = UxRestrictionsState(UxRestrictionsState.Source.CAR_SERVICE, true, UxRestrictionsState.FLAG_FULLY_RESTRICTED, 120)
                    controller.recordResponseCare(CareEventType.FEED)
                }
                check(controller.state.value.care.record!!.events == eventsBeforeDriving)
                check(controller.state.value.response.active)
                result.putString("drivingRecordGuard", "passed")
                withContext(Dispatchers.Main) { ux.value = UxRestrictionsState.Parked; controller.recordResponseCare(CareEventType.FEED) }
                val completed = await(controller) { it.response.phase == ResponsePhase.IDLE && it.response.notice == ResponseNotice.CARE_RECORDED }
                check(completed.care.record!!.events.size == eventsBeforeDriving.size + 1)
                check(completed.care.record!!.events.all { it.source == CareEntrySource.DEMO })
                check(careFile.readBytes().toList() == realBeforeDemo)
                check(!File(directory, "departure.json").exists())
                result.putString("demoRecordingAndIsolation", "passed")
                withContext(Dispatchers.Main) { controller.endDemo() }
                await(controller) { !it.isDemo && it.care.record != null && it.response.phase == ResponsePhase.IDLE }
                withContext(Dispatchers.Main) { controller.startResponse() }
                await(controller) { it.response.active }
                withContext(Dispatchers.Main) { controller.recordResponseCare(CareEventType.DIAPER) }
                await(controller) { it.response.notice == ResponseNotice.CARE_RECORDED && !it.response.active && !it.responseRecording }
                check(CareRecordCodec.decode(careFile.readText()).events.single().type == CareEventType.DIAPER)
                result.putString("actualAtomicRecordThenReturn", "passed")

                val uncertainFolder = File(directory, "uncertain").also { check(it.mkdir()) }
                val uncertainStore = AtomicCareRecordStore(File(uncertainFolder, "care.json"))
                var failConfirmation = false
                val uncertainCare = CareRepository(object : CareRecordStore {
                    override fun read() = uncertainStore.read()
                    override fun write(record: CareRecord) {
                        uncertainStore.write(record)
                        if (failConfirmation) throw IllegalStateException("QA confirmation failure")
                    }
                })
                uncertainCare.load(); uncertainCare.saveProfile(2, null)
                failConfirmation = true
                val uncertain = createController(uncertainCare, uncertainFolder)
                await(uncertain) { it.care.record != null && it.preparation.record != null }
                withContext(Dispatchers.Main) { uncertain.startResponse() }
                await(uncertain) { it.response.active }
                withContext(Dispatchers.Main) { uncertain.recordResponseCare(CareEventType.FEED) }
                val failed = await(uncertain) { it.response.notice == ResponseNotice.RECORD_FAILED && !it.responseRecording }
                check(failed.response.active && failed.care.status == CareStoreStatus.NEEDS_RELOAD)
                check(uncertainStore.read()!!.events.single().type == CareEventType.FEED)
                result.putString("uncertainWriteKeepsResponseOpen", "passed")

                // Verify route-access provider integration: registered corridor enables a real navigable
                // StopCandidate; unregistered route keeps ACCESS_UNVERIFIED and returns null candidate.
                val accessFolder = File(directory, "route-access").also { check(it.mkdir()) }
                val accessNow = System.currentTimeMillis()
                val sourceMeta = FacilityDataSource("src", "한국도로공사", "휴게소", "https://example.invalid/r", "무료", "반기")
                val realFacility = CareFacility(
                    id = "public-real-stop",
                    name = "안성(부산)",
                    location = GeoPoint(37.013407, 127.1448001),
                    type = FacilityType.REST_AREA,
                    nursingRoom = AmenityStatus.AVAILABLE,
                    verifiedAtEpochMillis = accessNow,
                )
                catalog.value = FacilityState(DataSnapshot(
                    DataOrigin.PUBLIC_DATA,
                    DataStatus.READY,
                    FacilityCatalog(DataOrigin.PUBLIC_DATA, accessNow, listOf(sourceMeta), listOf(FacilityListing(realFacility, "src"))),
                    accessNow,
                ))
                val verifiedAccess = MutableStateFlow(DataSnapshot(
                    DataOrigin.NAVI_SDK,
                    DataStatus.READY,
                    RouteAccessCatalog(
                        DataOrigin.NAVI_SDK,
                        accessNow,
                        listOf(
                            VerifiedRouteCorridor(
                                id = "qa-corridor",
                                destinationLabel = "천안분기점",
                                destinationLocation = GeoPoint(36.72, 127.27),
                                destinationPoiId = "qa-dest-poi",
                                totalDistanceMeters = 80_000,
                                totalDurationSeconds = 3_600,
                                coversRemainingRestAreas = true,
                                approaches = listOf(
                                    VerifiedCorridorApproach(
                                        facilityId = realFacility.id,
                                        access = RouteAccess.CONFIRMED_ON_ROUTE,
                                        remainingDistanceToDestinationMeters = 32_000,
                                        remainingDurationToDestinationSeconds = 1_440,
                                        navigationLocation = GeoPoint(37.01362, 127.14455),
                                        navigationPoiId = "36khp2hdj2cfcm5nqhe3",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    accessNow,
                ))
                val accessController = createController(care, accessFolder, verifiedAccess)
                await(accessController) { it.care.record != null && it.preparation.record != null }
                route.value = DataSnapshot(
                    DataOrigin.NAVI_SDK,
                    DataStatus.READY,
                    JourneySnapshot("qa-journey-1", "천안분기점", accessNow + 2_400_000, 55_000, GeoPoint(36.7201, 127.2701), destinationPoiId = "qa-dest-poi"),
                    accessNow,
                )
                val readyPlan = await(accessController) { it.stops?.problem == StopPlanningProblem.NONE && it.stops?.fullCare?.candidates?.isNotEmpty() == true }
                check(readyPlan.stops!!.fullCare.candidates.single().canRequestNavigation)
                withContext(Dispatchers.Main) { accessController.startResponse() }
                await(accessController) { it.response.active }
                val selectedReal = withContext(Dispatchers.Main) { accessController.selectResponseStop(StopPlanType.FULL_CARE) }
                check(selectedReal?.facility?.id == realFacility.id && selectedReal?.navigationPoiId == "36khp2hdj2cfcm5nqhe3")
                check(withContext(Dispatchers.Main) { accessController.responseNavigationCandidate()?.facility?.id } == realFacility.id)

                route.value = DataSnapshot(
                    DataOrigin.NAVI_SDK,
                    DataStatus.READY,
                    JourneySnapshot("qa-journey-2", "미등록 목적지", accessNow + 2_400_000, 55_000, GeoPoint(35.1796, 129.0756), destinationPoiId = "unknown-poi"),
                    accessNow,
                )
                val unverifiedPlan = await(accessController) { it.stops?.journeyId == "qa-journey-2" && it.stops?.problem == StopPlanningProblem.ACCESS_UNVERIFIED }
                check(unverifiedPlan.stops!!.fullCare.candidates.isEmpty())
                check(withContext(Dispatchers.Main) { accessController.responseNavigationCandidate() } == null)
                result.putString("routeAccessProviderIntegration", "passed")
                check(originalHashes() == original)
            }
            result.putString("result", "passed")
        } catch (e: Exception) {
            code = Activity.RESULT_CANCELED
            result.putString("result", "failed")
            result.putString("problem", "${e.javaClass.simpleName}:${e.message}")
        } finally {
            scopes.forEach { it.cancel() }
            result.putBoolean("fixtureCleaned", !directory.exists() || directory.deleteRecursively())
            result.putBoolean("personalDataPreserved", originalHashes() == original)
        }
        finish(code, result)
    }
}
