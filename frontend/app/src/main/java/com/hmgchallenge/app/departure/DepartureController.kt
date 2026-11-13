package com.hmgchallenge.app.departure

import com.hmgchallenge.app.driving.UxRestrictionsState
import com.hmgchallenge.shared.care.CareEntrySource
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareJournal
import com.hmgchallenge.shared.care.CareNotice
import com.hmgchallenge.shared.care.CareRepository
import com.hmgchallenge.shared.care.CareState
import com.hmgchallenge.shared.care.CareStoreStatus
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.departure.DepartureChoice
import com.hmgchallenge.shared.departure.DepartureDecision
import com.hmgchallenge.shared.departure.DepartureDemo
import com.hmgchallenge.shared.departure.DepartureDemoSituation
import com.hmgchallenge.shared.departure.DepartureEvaluation
import com.hmgchallenge.shared.departure.DeparturePlanner
import com.hmgchallenge.shared.departure.DepartureRecord
import com.hmgchallenge.shared.departure.DepartureRepository
import com.hmgchallenge.shared.departure.DepartureState
import com.hmgchallenge.shared.departure.DepartureStoreStatus
import com.hmgchallenge.shared.departure.PackingItem
import com.hmgchallenge.shared.departure.PackingSettings
import com.hmgchallenge.shared.departure.SupplyKind
import com.hmgchallenge.shared.facility.DemoStopScenario
import com.hmgchallenge.shared.facility.FacilityState
import com.hmgchallenge.shared.facility.RouteFacilityEvidence
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.facility.StopPlanner
import com.hmgchallenge.shared.facility.StopPlanningResult
import com.hmgchallenge.shared.facility.StopPlanType
import com.hmgchallenge.shared.response.CryResponseCoordinator
import com.hmgchallenge.shared.response.ResponseAssessor
import com.hmgchallenge.shared.response.ResponseAssessment
import com.hmgchallenge.shared.response.ResponseContext
import com.hmgchallenge.shared.response.ResponseNotice
import com.hmgchallenge.shared.response.ResponseState
import com.hmgchallenge.shared.journey.DriveSession
import com.hmgchallenge.shared.journey.JourneySnapshot
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DepartureUiState(
    val preparation: DepartureState = DepartureState(),
    val care: CareState = CareState(),
    val journey: DataSnapshot<JourneySnapshot> = DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.EMPTY),
    val evaluation: DepartureEvaluation? = null,
    val stops: StopPlanningResult? = null,
    val isDemo: Boolean = false,
    val demoDelayMinutes: Int = 0,
    val demoProblem: Boolean = false,
    val demoSituation: DepartureDemoSituation = DepartureDemoSituation.NORMAL,
    val response: ResponseState = ResponseState(),
    val responseAssessment: ResponseAssessment? = null,
    val responseRecording: Boolean = false,
)

/** Owns persistence and demo isolation. UI events cannot write to a real journal while demoing. */
class DepartureController(
    filesDir: File,
    private val careRepository: CareRepository,
    journeys: StateFlow<DataSnapshot<JourneySnapshot>>,
    facilities: StateFlow<FacilityState>,
    ticks: StateFlow<Long>,
    private val ux: StateFlow<UxRestrictionsState>,
    private val scope: CoroutineScope,
) {
    private fun canEdit() = !ux.value.requiresDistractionOptimization && !ux.value.noSetup && !ux.value.noKeyboard
    private val repository = DepartureRepository(AtomicDepartureStore(File(filesDir, "departure.json")), ::canEdit)
    private val demo = MutableStateFlow<DepartureDemo?>(null)
    private val demoProblem = MutableStateFlow(false)
    private val planner = DeparturePlanner()
    private val stopPlanner = StopPlanner()
    private val responseCoordinator = CryResponseCoordinator()
    private val responseVersion = MutableStateFlow(0L)
    private var responseRecording = false
    private val inputs = combine(repository.state, careRepository.state, journeys, ticks, demo) { preparation, care, journey, _, example ->
        val now = System.currentTimeMillis()
        val shownCare = example?.let { CareState(CareStoreStatus.READY, it.care, revision = it.revision, notice = it.careNotice) } ?: care
        val shownPreparation = example?.let { DepartureState(DepartureStoreStatus.READY, it.preparation, revision = it.revision) } ?: preparation
        val shownJourney = example?.journey(now) ?: journey
        val evaluation = shownPreparation.record?.let { planner.evaluate(shownCare.record, it, shownJourney, now) }
        DepartureUiState(shownPreparation, shownCare, shownJourney, evaluation, isDemo = example != null, demoDelayMinutes = example?.delayMinutes ?: 0,
            demoSituation = example?.situation ?: DepartureDemoSituation.NORMAL)
    }
    val state = combine(inputs, facilities, demoProblem, responseVersion) { input, catalog, failed, _ ->
        val now = System.currentTimeMillis()
        val scenario = if (input.isDemo) DemoStopScenario.create(now) else null
        val facilitiesSnapshot = scenario?.facilities?.let { snapshot ->
            if (input.demoSituation == DepartureDemoSituation.NO_FACILITIES) snapshot.copy(value = snapshot.readyValue!!.copy(entries = emptyList())) else snapshot
        } ?: catalog.snapshot
        val evidence = if (scenario != null && input.journey.readyValue != null) scenario.evidence.let { snapshot ->
            snapshot.copy(value = snapshot.readyValue!!.copy(journeyId = input.journey.readyValue!!.id))
        }
            else DataSnapshot<RouteFacilityEvidence>(DataOrigin.NAVI_SDK, DataStatus.UNSUPPORTED)
        val stops = stopPlanner.plan(input.journey, facilitiesSnapshot, evidence, now)
        val context = ResponseContext(input.preparation.record?.outingId ?: "unavailable", input.care.record?.profile?.id, input.isDemo)
        val assessment = ResponseAssessor.assess(context, input.care.record, input.evaluation?.forecast,
            input.preparation.record?.driveSession, now)
        input.copy(stops = stops, demoProblem = failed, responseAssessment = assessment,
            response = responseCoordinator.update(assessment, stops, now), responseRecording = responseRecording)
    }.stateIn(scope, SharingStarted.Eagerly, DepartureUiState())

    fun load() { scope.launch { repository.load() } }
    fun startDemo(firstDay: Boolean) {
        if (!canEdit()) return
        demoProblem.value = false
        demo.value = DepartureDemo.create(System.currentTimeMillis(), firstDay)
    }
    fun endDemo() { if (canEdit()) { demo.value = null; demoProblem.value = false } }
    fun changeDemoDelay() {
        if (!canEdit()) return
        demo.value = demo.value?.let { it.copy(delayMinutes = if (it.delayMinutes == 0) 90 else 0) }
    }

    fun startResponseDemo(situation: DepartureDemoSituation) {
        if (!canEdit()) return
        demoProblem.value = false
        demo.value = DepartureDemo.response(System.currentTimeMillis(), situation)
    }

    private fun responseChanged() { responseVersion.value += 1 }
    fun startResponse() {
        state.value.responseAssessment?.let { responseCoordinator.start(it, System.currentTimeMillis()); responseChanged() }
    }
    fun confirmResponse(episodeId: String) {
        responseCoordinator.confirm(episodeId); responseChanged()
    }
    fun finishResponse(confirmedCare: Boolean = false) {
        if (responseRecording || (confirmedCare && !canEdit())) return
        state.value.responseAssessment?.let {
            responseCoordinator.finish(it, System.currentTimeMillis(), if (confirmedCare) ResponseNotice.CARE_CONFIRMED else ResponseNotice.CANCELLED)
            responseChanged()
        }
    }
    fun selectResponseStop(type: StopPlanType): StopCandidate? {
        val current = state.value
        val now = System.currentTimeMillis()
        responseCoordinator.select(type, current.stops, now); responseChanged()
        val selected = responseCoordinator.state.selected ?: return null
        val context = current.responseAssessment?.context ?: return null
        return selected.candidate.takeIf { !context.isDemo && selected.type == type &&
            CryResponseCoordinator.selectionCurrent(selected, current.stops, context, now) }
    }
    fun responseNavigationCandidate(): StopCandidate? {
        val current = state.value
        val selected = current.response.selected ?: return null
        val context = current.responseAssessment?.context ?: return null
        if (context.isDemo || !CryResponseCoordinator.selectionCurrent(selected, current.stops, context, System.currentTimeMillis())) return null
        val plan = if (selected.type == StopPlanType.FULL_CARE) current.stops?.fullCare else current.stops?.nearestSafeStop
        return plan?.candidates?.firstOrNull { it.facility.id == selected.candidate.facility.id }
    }

    /** A confirmed caregiver record ends the response; a failed or overlapping write never does. */
    fun recordResponseCare(type: CareEventType) {
        if (!canEdit() || responseRecording || !state.value.response.active || !state.value.care.canWrite) return
        val episodeId = state.value.response.episodeId
        val assessment = state.value.responseAssessment ?: return
        val example = demo.value
        if (example != null) {
            try {
                demo.value = example.record(type, System.currentTimeMillis(), "demo-response-${UUID.randomUUID()}")
                responseCoordinator.finish(assessment, System.currentTimeMillis(), ResponseNotice.CARE_RECORDED)
            } catch (_: Exception) { responseCoordinator.recordFailed() }
            responseChanged()
        } else {
            responseRecording = true; responseChanged()
            scope.launch {
                try {
                    val before = careRepository.state.value
                    careRepository.record(type)
                    val after = careRepository.state.value
                    if (responseCoordinator.state.episodeId == episodeId && responseCoordinator.state.context == assessment.context) {
                        val newEvent = after.record?.events?.firstOrNull { event -> before.record?.events?.none { it.id == event.id } == true }
                        if (after.revision > before.revision && after.notice == CareNotice.RECORDED && newEvent?.type == type)
                            responseCoordinator.finish(assessment, System.currentTimeMillis(), ResponseNotice.CARE_RECORDED)
                        else responseCoordinator.recordFailed()
                    }
                } finally { responseRecording = false; responseChanged() }
            }
        }
    }

    fun saveSettings(settings: PackingSettings) = changePreparation({ it.copy(settings = settings) }) { saveSettings(settings) }
    fun addItem(label: String) = changePreparation({ it.copy(items = it.items +
        PackingItem(UUID.randomUUID().toString(), SupplyKind.CUSTOM, label.trim().replace(Regex("\\s+"), " "))) }) { addItem(label) }
    fun removeItem(id: String) = changePreparation({ it.copy(items = it.items.filterNot { item -> item.id == id }) }) { removeItem(id) }
    fun restoreDefaults() = changePreparation({ record -> record.copy(items = record.items +
        DepartureRecord.defaultItems().filter { preset -> record.items.none { it.kind == preset.kind } }) }) { restoreDefaults() }
    fun newOuting() = changePreparation({ it.copy(outingId = "demo-outing-${UUID.randomUUID()}",
        items = it.items.map(PackingItem::unchecked), decision = null, driveSession = null) }) { newOuting() }
    fun endSession() = changePreparation({ it.copy(driveSession = it.driveSession?.copy(endedAtEpochMillis = System.currentTimeMillis())) }) { endSession() }

    fun toggleItem(id: String) {
        val plan = state.value.evaluation?.packing ?: return
        changePreparation({ record ->
            record.copy(items = record.items.map { item -> if (item.id != id) item else if (plan.isChecked(item, System.currentTimeMillis())) item.unchecked()
                else item.copy(checkedAtEpochMillis = System.currentTimeMillis(), checkedForTrip = plan.tripKey, checkedQuantity = plan.quantity(item.kind)) })
        }) { toggleItem(id, plan) }
    }

    fun choose(choice: DepartureChoice, seated: Boolean = false, candidate: StopCandidate? = null) {
        val current = state.value
        val plan = current.evaluation?.packing ?: return
        val babyId = current.care.record?.profile?.id.takeIf { seated }
        changePreparation({ record ->
            val now = System.currentTimeMillis()
            record.copy(decision = DepartureDecision(choice, plan.tripKey, now, candidate?.facility?.id),
                driveSession = if (choice == DepartureChoice.NOW && babyId != null)
                    DriveSession("demo-session-${UUID.randomUUID()}", babyId, now) else record.driveSession)
        }) { choose(choice, plan.tripKey, babyId, candidate?.facility?.id) }
    }

    fun recordCare(type: CareEventType) {
        val example = demo.value
        if (example != null) {
            try { demo.value = example.record(type, System.currentTimeMillis(), "demo-event-${UUID.randomUUID()}"); demoProblem.value = false }
            catch (_: Exception) { demoProblem.value = true }
        } else scope.launch { careRepository.record(type) }
    }
    fun undoCare() {
        val example = demo.value
        if (example != null) {
            try { demo.value = example.copy(care = CareJournal.undoLatest(example.care, expectedSource = CareEntrySource.DEMO), revision = example.revision + 1, careNotice = CareNotice.UNDONE) }
            catch (_: Exception) { demoProblem.value = true }
        } else scope.launch { careRepository.undoLatest() }
    }
    fun saveProfile(month: Int, amount: Int?) {
        if (!canEdit()) return
        val example = demo.value
        if (example != null) demo.value = example.copy(care = example.care.copy(profile = example.care.profile.copy(
            monthAge = month, preferences = example.care.profile.preferences.copy(preferredFeedAmountMl = amount))),
            revision = example.revision + 1, careNotice = CareNotice.PROFILE_SAVED)
        else scope.launch { if (canEdit()) careRepository.saveProfile(month, amount) }
    }
    fun saveTiming(feed: Int?, awake: Int?, seatedBreak: Int?) {
        if (!canEdit()) return
        val example = demo.value
        if (example != null) demo.value = example.copy(care = example.care.copy(profile = example.care.profile.copy(
            preferences = example.care.profile.preferences.copy(feedIntervalMinutes = feed, awakeWindowMinutes = awake, seatedBreakMinutes = seatedBreak))),
            revision = example.revision + 1, careNotice = CareNotice.PROFILE_SAVED)
        else scope.launch { if (canEdit()) careRepository.saveTimingPreferences(feed, awake, seatedBreak) }
    }
    fun editCare(id: String, at: Long, amount: Int?) {
        if (!canEdit()) return
        val example = demo.value
        if (example != null) {
            try { demo.value = example.copy(care = CareJournal.edit(example.care, id, at, amount, System.currentTimeMillis(), expectedSource = CareEntrySource.DEMO),
                revision = example.revision + 1, careNotice = CareNotice.EDITED) }
            catch (_: Exception) { demoProblem.value = true }
        } else scope.launch { if (canEdit()) careRepository.editEvent(id, at, amount) }
    }

    private fun changePreparation(transform: (DepartureRecord) -> DepartureRecord, actual: suspend DepartureRepository.() -> Unit) {
        if (!canEdit()) return
        val example = demo.value
        if (example != null) {
            try { demo.value = example.copy(preparation = transform(example.preparation), revision = example.revision + 1); demoProblem.value = false }
            catch (_: IllegalArgumentException) { demoProblem.value = true }
        } else scope.launch { if (canEdit()) repository.actual() }
    }
}
