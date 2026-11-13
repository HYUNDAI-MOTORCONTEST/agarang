package com.hmgchallenge.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.hmgchallenge.app.care.AtomicCareRecordStore
import com.hmgchallenge.app.departure.DepartureController
import com.hmgchallenge.app.departure.DepartureUiState
import com.hmgchallenge.app.driving.UxRestrictionsState
import com.hmgchallenge.app.facility.AtomicFacilityCatalogStore
import com.hmgchallenge.app.facility.AtomicRouteAccessCatalogStore
import com.hmgchallenge.app.facility.HttpFacilityCatalogClient
import com.hmgchallenge.app.facility.HttpRouteAccessCatalogClient
import com.hmgchallenge.app.navigation.PleosNavigationClient
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareRepository
import com.hmgchallenge.shared.care.CareStoreStatus
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.departure.PackingScope
import com.hmgchallenge.shared.departure.PackingSettings
import com.hmgchallenge.shared.facility.AmenityStatus
import com.hmgchallenge.shared.facility.CareFacility
import com.hmgchallenge.shared.facility.FacilityCatalog
import com.hmgchallenge.shared.facility.FacilityDataSource
import com.hmgchallenge.shared.facility.FacilityListing
import com.hmgchallenge.shared.facility.FacilityProblem
import com.hmgchallenge.shared.facility.FacilityRepository
import com.hmgchallenge.shared.facility.FacilityType
import com.hmgchallenge.shared.facility.RouteAccess
import com.hmgchallenge.shared.facility.RouteAccessCatalog
import com.hmgchallenge.shared.facility.RouteAccessRepository
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.facility.StopPlanType
import com.hmgchallenge.shared.facility.StopPlanningProblem
import com.hmgchallenge.shared.facility.VerifiedCorridorApproach
import com.hmgchallenge.shared.facility.VerifiedRouteCorridor
import com.hmgchallenge.shared.forecast.CareNeed
import com.hmgchallenge.shared.forecast.ForecastStage
import com.hmgchallenge.shared.journey.GeoPoint
import com.hmgchallenge.shared.journey.JourneySnapshot
import com.hmgchallenge.shared.journey.NavigationClient
import com.hmgchallenge.shared.journey.NavigationDestination
import com.hmgchallenge.shared.journey.NavigationRepository
import com.hmgchallenge.shared.journey.NavigationWaypoint
import com.hmgchallenge.shared.journey.StopRequestPhase
import com.hmgchallenge.shared.journey.StopRequestProblem
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

/**
 * Issue #12 runtime verification across Android stores, HTTP adapters, offline caches, permission
 * checks, zero-record startup, process recreation, and real DepartureController Scenario flows.
 * Operates exclusively in an isolated temporary directory without modifying live user files.
 */
class ScenarioVerificationInstrumentation : Instrumentation() {
    private var liveBackendUrl: String? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        liveBackendUrl = arguments?.getString("liveBackendUrl")?.takeIf { it.isNotBlank() }
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val protectedFiles = listOf("care-record.json", "departure.json", "facility-catalog.json", "route-access-catalog.json")
        fun originalHashes() = protectedFiles.map { name ->
            File(targetContext.filesDir, name).takeIf { it.exists() }?.readBytes()?.let {
                MessageDigest.getInstance("SHA-256").digest(it).toList()
            }
        }
        val original = originalHashes()
        val directory = File(targetContext.filesDir, "issue12-qa-${System.nanoTime()}")
        val scopes = mutableListOf<CoroutineScope>()
        var code = Activity.RESULT_OK
        var stage = "init"

        try {
            check(directory.mkdir())
            runBlocking {
                val now = System.currentTimeMillis()
                val sourceMeta = FacilityDataSource(
                    "data-go-kr-15025446",
                    "한국도로공사",
                    "전국휴게소정보표준데이터",
                    "https://www.data.go.kr/data/15025446/standard.do",
                    "이용허락범위 제한 없음",
                    "반기",
                )
                val shelter = CareFacility("public-osan-qa", "오산", GeoPoint(37.156, 127.087), FacilityType.DROWSINESS_SHELTER, nursingRoom = AmenityStatus.UNKNOWN, verifiedAtEpochMillis = now - 86_400_000L)
                val nursingRestArea = CareFacility("public-anseong-qa", "안성(부산)", GeoPoint(37.013, 127.144), FacilityType.REST_AREA, nursingRoom = AmenityStatus.AVAILABLE, verifiedAtEpochMillis = now - 86_400_000L)
                val oppositeRestArea = CareFacility("public-jukjeon-qa", "죽전(서울)", GeoPoint(37.331, 127.104), FacilityType.REST_AREA, nursingRoom = AmenityStatus.AVAILABLE, verifiedAtEpochMillis = now - 86_400_000L)

                val seededFacilities = DataSnapshot(
                    DataOrigin.PUBLIC_DATA,
                    DataStatus.READY,
                    FacilityCatalog(
                        DataOrigin.PUBLIC_DATA,
                        now - 600_000L,
                        listOf(sourceMeta),
                        listOf(
                            FacilityListing(shelter, sourceMeta.id, "서울기점 + 부산종점"),
                            FacilityListing(nursingRestArea, sourceMeta.id, "하행"),
                            FacilityListing(oppositeRestArea, sourceMeta.id, "상행"),
                        ),
                    ),
                    now,
                )
                val seededAccess = DataSnapshot(
                    DataOrigin.NAVI_SDK,
                    DataStatus.READY,
                    RouteAccessCatalog(
                        DataOrigin.NAVI_SDK,
                        now - 600_000L,
                        listOf(
                            VerifiedRouteCorridor(
                                id = "qa-gyeongbu",
                                destinationLabel = "천안분기점",
                                destinationLocation = GeoPoint(36.72, 127.27),
                                destinationPoiId = "qa-cheonan-jc",
                                totalDistanceMeters = 80_000L,
                                totalDurationSeconds = 3_600L,
                                coversRemainingRestAreas = true,
                                approaches = listOf(
                                    VerifiedCorridorApproach(oppositeRestArea.id, RouteAccess.OPPOSITE_DIRECTION, 68_000L, 3_060L),
                                    VerifiedCorridorApproach(shelter.id, RouteAccess.CONFIRMED_ON_ROUTE, 49_000L, 2_200L, GeoPoint(37.15691, 127.08702), "36khp2hdj2cfcm5nqhe3"),
                                    VerifiedCorridorApproach(nursingRestArea.id, RouteAccess.CONFIRMED_ON_ROUTE, 32_000L, 1_440L, GeoPoint(37.01362, 127.14455), "36khp2hdj2cfcm5nqhe3"),
                                ),
                            ),
                        ),
                    ),
                    now,
                )

                // 1. Optional live HTTP backend verification + guaranteed offline STALE fallback verification.
                val liveUrl = liveBackendUrl
                if (liveUrl != null) {
                    val liveDir = File(directory, "live-http").also { check(it.mkdir()) }
                    val liveFacilities = FacilityRepository(HttpFacilityCatalogClient(liveUrl), AtomicFacilityCatalogStore(File(liveDir, "facility.json")))
                    val liveAccess = RouteAccessRepository(HttpRouteAccessCatalogClient(liveUrl), AtomicRouteAccessCatalogStore(File(liveDir, "access.json")))
                    liveFacilities.refresh()
                    liveAccess.refresh()
                    check(liveFacilities.state.value.snapshot.status == DataStatus.READY) { "liveFacilities=${liveFacilities.state.value}" }
                    check(liveFacilities.state.value.snapshot.readyValue!!.entries.size == 468)
                    check(liveAccess.state.value.status == DataStatus.READY) { "liveAccess=${liveAccess.state.value}" }
                    check(liveAccess.state.value.readyValue!!.corridors.size >= 2)
                    result.putString("liveHttpBackendSync", "passed")
                } else {
                    result.putString("liveHttpBackendSync", "skipped-no-url")
                }

                val offlineDir = File(directory, "offline").also { check(it.mkdir()) }
                val offlineFacilityStore = AtomicFacilityCatalogStore(File(offlineDir, "facility.json")).also { it.write(seededFacilities) }
                val offlineAccessStore = AtomicRouteAccessCatalogStore(File(offlineDir, "access.json")).also { it.write(seededAccess) }
                val offlineFacilityRepo = FacilityRepository(HttpFacilityCatalogClient("http://127.0.0.1:19999"), offlineFacilityStore)
                val offlineAccessRepo = RouteAccessRepository(HttpRouteAccessCatalogClient("http://127.0.0.1:19999"), offlineAccessStore)
                offlineFacilityRepo.refresh()
                offlineAccessRepo.refresh()
                check(offlineFacilityRepo.state.value.problem == FacilityProblem.OFFLINE && offlineFacilityRepo.state.value.fromCache)
                check(offlineFacilityRepo.state.value.snapshot.status == DataStatus.STALE)
                check(offlineAccessRepo.state.value.status == DataStatus.STALE)
                result.putString("offlineCacheFallback", "passed")

                // 2. Permission failure path in actual PleosNavigationClient without mutating global permissions.
                val deniedContext = object : ContextWrapper(targetContext) {
                    override fun getApplicationContext(): Context = this
                    override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_DENIED
                }
                val deniedNavRepo = NavigationRepository({ PleosNavigationClient(deniedContext) })
                withContext(Dispatchers.Main) { deniedNavRepo.start() }
                val deniedConn = withTimeout(5_000) { deniedNavRepo.connection.first { it == DataStatus.PERMISSION_DENIED || it == DataStatus.FAILED } }
                val deniedRoute = withTimeout(5_000) { deniedNavRepo.snapshots.first { it.status == DataStatus.PERMISSION_DENIED || it.status == DataStatus.FAILED } }
                check(deniedConn == DataStatus.PERMISSION_DENIED && deniedRoute.status == DataStatus.PERMISSION_DENIED) {
                    "deniedConn=$deniedConn deniedRoute=${deniedRoute.status}"
                }
                val candidateForPermCheck = StopCandidate(
                    nursingRestArea, DataOrigin.PUBLIC_DATA, RouteAccess.CONFIRMED_ON_ROUTE, 600, 12_000,
                    GeoPoint(37.01362, 127.14455), "j-perm", now, "36khp2hdj2cfcm5nqhe3",
                )
                withContext(Dispatchers.Main) {
                    val permClient = PleosNavigationClient(deniedContext)
                    val preflight = permClient.waypointPreflight(candidateForPermCheck)
                    check(preflight == StopRequestProblem.PERMISSION_DENIED) { "preflight=$preflight" }
                    permClient.release()
                    deniedNavRepo.stop()
                }
                result.putString("permissionDeniedSafe", "passed")

                // 3. Zero care records + navigation empty/failed + process recreation persistence round-trip.
                val persistDir = File(directory, "persist").also { check(it.mkdir()) }
                val emptyNav = MutableStateFlow(DataSnapshot<JourneySnapshot>(DataOrigin.NAVI_SDK, DataStatus.EMPTY))
                val ux = MutableStateFlow(UxRestrictionsState.Parked)
                val ticks = MutableStateFlow(System.currentTimeMillis())

                suspend fun makeController(folder: File, careRepo: CareRepository, navFlow: MutableStateFlow<DataSnapshot<JourneySnapshot>>): DepartureController =
                    withContext(Dispatchers.Main) {
                        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scopes += it }
                        DepartureController(folder, careRepo, navFlow, offlineFacilityRepo.state, ticks, ux, scope, MutableStateFlow(seededAccess)).also { it.load() }
                    }

                val careStore1 = AtomicCareRecordStore(File(persistDir, "care-record.json"))
                val care1 = CareRepository(careStore1)
                care1.load()
                val zeroController = makeController(persistDir, care1, emptyNav)
                val zeroState = withTimeout(5_000) { zeroController.state.first { it.care.status == CareStoreStatus.READY && it.preparation.record != null } }
                check(zeroState.care.record == null && zeroState.evaluation?.forecast == null)
                check(zeroState.stops?.problem == StopPlanningProblem.ROUTE_UNAVAILABLE)

                // Write profile + records + settings + checklist item -> recreate repositories & controller -> verify exact restore.
                care1.saveProfile(3, 140)
                care1.saveTimingPreferences(120, 120, 120)
                care1.record(CareEventType.FEED)
                withTimeout(5_000) { zeroController.state.first { it.care.record?.events?.size == 1 } }
                withContext(Dispatchers.Main) {
                    zeroController.saveSettings(PackingSettings(PackingScope.WHOLE_OUTING, manualTravelMinutes = 90, extraOutingMinutes = 90, feedIntervalMinutes = 120, diaperIntervalMinutes = 120))
                }
                withTimeout(5_000) {
                    zeroController.state.first { it.preparation.record?.settings?.extraOutingMinutes == 90 }
                }
                withContext(Dispatchers.Main) { zeroController.addItem("휴대용 손수건") }
                val beforeRecreate = withTimeout(5_000) {
                    zeroController.state.first { it.preparation.record?.items?.any { item -> item.customLabel == "휴대용 손수건" } == true }
                }
                val toggleId = beforeRecreate.preparation.record!!.items.first().id
                withContext(Dispatchers.Main) { zeroController.toggleItem(toggleId) }
                withTimeout(5_000) {
                    zeroController.state.first { s -> s.evaluation?.packing?.isChecked(s.preparation.record!!.items.first(), System.currentTimeMillis()) == true }
                }

                // Recreate repositories and controller from disk ("process recreation").
                val care2 = CareRepository(AtomicCareRecordStore(File(persistDir, "care-record.json")))
                care2.load()
                val navFailed = MutableStateFlow(DataSnapshot<JourneySnapshot>(DataOrigin.NAVI_SDK, DataStatus.FAILED))
                val restoredController = makeController(persistDir, care2, navFailed)
                val restoredState: DepartureUiState = withTimeout(5_000) {
                    restoredController.state.first { it.care.status == CareStoreStatus.READY && it.preparation.record?.items?.any { i -> i.customLabel == "휴대용 손수건" } == true }
                }
                check(restoredState.care.record!!.profile.monthAge == 3) { "restoredAge=${restoredState.care.record!!.profile.monthAge}" }
                check(restoredState.care.record!!.events.single().type == CareEventType.FEED) { "restoredEvents=${restoredState.care.record!!.events}" }
                check(restoredState.evaluation!!.packing.feedServings == 3) { "feedServings=${restoredState.evaluation!!.packing.feedServings}" }
                check(restoredState.evaluation!!.packing.diapers == 4) { "diapers=${restoredState.evaluation!!.packing.diapers}" }
                check(restoredState.evaluation!!.packing.isChecked(restoredState.preparation.record!!.items.first(), System.currentTimeMillis())) { "checkedNotRestored" }
                result.putString("zeroRecordNavFailAndRecreation", "passed")

                // 4. Live Scenario B/C multi-stop controller + NavigationRepository flow (two-stage + waypoint confirmation).
                stage = "4.init"
                val liveNavDir = File(directory, "nav-flow").also { check(it.mkdir()) }
                val liveNow = System.currentTimeMillis()
                val fakeAccess = MutableStateFlow(
                    DataSnapshot(
                        DataOrigin.NAVI_SDK,
                        DataStatus.READY,
                        seededAccess.readyValue!!.copy(fetchedAtEpochMillis = liveNow - 600_000L),
                        liveNow,
                    ),
                )
                val fakeFacilities = MutableStateFlow(
                    com.hmgchallenge.shared.facility.FacilityState(
                        DataSnapshot(
                            DataOrigin.PUBLIC_DATA,
                            DataStatus.READY,
                            seededFacilities.readyValue!!.copy(fetchedAtEpochMillis = liveNow - 600_000L),
                            liveNow,
                        ),
                    ),
                )
                val mainHandler = Handler(Looper.getMainLooper())
                var dest = NavigationDestination("qa-cheonan-jc:36.72:127.27", "천안분기점", GeoPoint(36.72, 127.27), 7_800L, 55_000L, destinationPoiId = "qa-cheonan-jc")
                val addedStops = mutableListOf<StopCandidate>()
                lateinit var navListener: NavigationClient.Listener
                val mockClient = object : NavigationClient {
                    override fun connect(listener: NavigationClient.Listener) { navListener = listener; listener.onConnected() }
                    override fun requestRouteState() { navListener.onRouteState(true) }
                    override fun requestDestination() { navListener.onDestination(dest) }
                    override fun requestWaypoints() { navListener.onWaypoints(addedStops.map { NavigationWaypoint(it.facility.name, it.navigationLocation!!, it.navigationPoiId) }) }
                    override fun addWaypoint(candidate: StopCandidate, slot: Int) {
                        addedStops += candidate
                        mainHandler.post {
                            navListener.onRouteState(true)
                            navListener.onDestination(dest)
                            navListener.onWaypoints(addedStops.map { NavigationWaypoint(it.facility.name, it.navigationLocation!!, it.navigationPoiId) })
                        }
                    }
                    override fun release() {}
                }
                val mockNavRepo = NavigationRepository({ mockClient })
                withContext(Dispatchers.Main) { mockNavRepo.start() }
                stage = "4.awaitReadyRoute"
                val readyRoute = withTimeout(5_000) { mockNavRepo.snapshots.first { it.status == DataStatus.READY } }
                val routeFlow = MutableStateFlow(readyRoute)

                var scenarioClock = liveNow - 600_000L
                val scenarioCare = CareRepository(
                    store = AtomicCareRecordStore(File(liveNavDir, "care-record.json")),
                    nowMillis = { scenarioClock },
                )
                scenarioCare.load()
                scenarioCare.saveProfile(2, 120)
                scenarioCare.saveTimingPreferences(30, null, null) // 30m feed interval so feed 10m ago is due before 130m arrival
                scenarioCare.record(CareEventType.FEED)
                val scenarioController = withContext(Dispatchers.Main) {
                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scopes += it }
                    DepartureController(liveNavDir, scenarioCare, routeFlow, fakeFacilities, MutableStateFlow(liveNow), ux, scope, fakeAccess).also { it.load() }
                }
                stage = "4.awaitInitialStops"
                val initialStops = withTimeout(5_000) {
                    scenarioController.state.first { it.stops?.problem == StopPlanningProblem.NONE && CareNeed.FEED in it.evaluation?.forecast?.journey?.careBeforeArrival.orEmpty() }
                }
                check(initialStops.stops!!.nearestSafeStop.candidates.first().facility.id == shelter.id)
                check(initialStops.stops!!.fullCare.candidates.first().facility.id == nursingRestArea.id)

                // Stage 1: nearest safe stop -> Stage 2: full care stop -> add waypoint -> record FEED -> return to IDLE.
                stage = "4.startResponse"
                withContext(Dispatchers.Main) { scenarioController.startResponse() }
                withTimeout(5_000) { scenarioController.state.first { it.response.active } }
                val c1 = withContext(Dispatchers.Main) { scenarioController.selectResponseStop(StopPlanType.NEAREST_SAFE_STOP) }
                check(c1?.facility?.id == shelter.id) { "c1=${c1?.facility?.id}" }
                val c2 = withContext(Dispatchers.Main) { scenarioController.selectResponseStop(StopPlanType.FULL_CARE) }
                check(c2?.facility?.id == nursingRestArea.id && c2.canRequestNavigation) { "c2=$c2" }

                stage = "4.prepareStop"
                withContext(Dispatchers.Main) { mockNavRepo.prepareStop(c2!!) }
                val awaiting = withTimeout(5_000) { mockNavRepo.stopRequest.first { it.phase == StopRequestPhase.AWAITING_CONFIRMATION } }
                stage = "4.confirmStop"
                withContext(Dispatchers.Main) { mockNavRepo.confirmStop(awaiting.confirmation!!.id) }
                withTimeout(5_000) { mockNavRepo.stopRequest.first { it.phase == StopRequestPhase.CONFIRMED } }
                check(addedStops.single().facility.id == nursingRestArea.id)

                stage = "4.recordCare"
                scenarioClock = liveNow
                withContext(Dispatchers.Main) { scenarioController.recordResponseCare(CareEventType.FEED) }
                val completed = try {
                    withTimeout(5_000) { scenarioController.state.first { it.response.phase == ResponsePhase.IDLE && it.response.notice == ResponseNotice.CARE_RECORDED } }
                } catch (t: Exception) {
                    throw IllegalStateException("response=${scenarioController.state.value.response} careProblem=${scenarioCare.state.value.problem}", t)
                }
                check(completed.care.record!!.events.size == 2)
                withContext(Dispatchers.Main) { mockNavRepo.stop() }
                stage = "4.done"
                result.putString("controllerMultiStageFlow", "passed")
                check(originalHashes() == original)
            }
            result.putString("result", "passed")
        } catch (e: Exception) {
            code = Activity.RESULT_CANCELED
            result.putString("stage", stage)
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
