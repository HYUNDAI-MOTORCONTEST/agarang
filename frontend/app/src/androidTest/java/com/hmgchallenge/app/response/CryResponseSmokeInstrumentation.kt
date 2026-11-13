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
import com.hmgchallenge.shared.facility.FacilityCatalog
import com.hmgchallenge.shared.facility.FacilityState
import com.hmgchallenge.shared.facility.StopPlanType
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
                val ticks = MutableStateFlow(System.currentTimeMillis())
                suspend fun createController(repository: CareRepository, folder: File): DepartureController = withContext(Dispatchers.Main) {
                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scopes += it }
                    DepartureController(folder, repository, route, catalog, ticks, ux, scope).also { it.load() }
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
