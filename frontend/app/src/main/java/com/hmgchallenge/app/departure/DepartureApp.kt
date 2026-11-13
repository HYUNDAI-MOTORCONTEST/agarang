package com.hmgchallenge.app.departure

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmgchallenge.app.R
import com.hmgchallenge.app.care.CareButton
import com.hmgchallenge.app.care.CareCard
import com.hmgchallenge.app.care.CareScreen
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.driving.UxRestrictionsState
import com.hmgchallenge.app.ui.theme.LocalPleosTypography
import com.hmgchallenge.shared.care.CareNotice
import com.hmgchallenge.shared.care.CareStoreStatus
import com.hmgchallenge.shared.departure.DepartureChoice
import com.hmgchallenge.shared.departure.DepartureProblem
import com.hmgchallenge.shared.departure.DepartureStoreStatus
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.forecast.CareNeed
import com.hmgchallenge.shared.journey.StopRequestState

private enum class DeparturePage { HOME, CHECKLIST, RECORDS, TIMING, PACKING, ADD_ITEM, NOW, CARE_FIRST, STOPS, TOOLS, CONNECTIONS, NEW_OUTING }

/** Parked F2 shell. Driving immediately removes forms and shows a stable, short summary. */
@Composable
fun DepartureApp(
    state: DepartureUiState,
    actions: DepartureController,
    onReloadCare: () -> Unit,
    onNavigateStop: (StopCandidate) -> Unit,
    stopRequest: StopRequestState,
    onConfirmStop: (String) -> Unit,
    onCancelStop: () -> Unit,
    connections: @Composable () -> Unit,
) {
    val ux = LocalUxRestrictions.current
    val restricted = ux.requiresDistractionOptimization || ux.noSetup || ux.noKeyboard
    var pageName by rememberSaveable { mutableStateOf(DeparturePage.HOME.name) }
    val page = if (restricted) DeparturePage.HOME else DeparturePage.valueOf(pageName)
    var waitingPreparation by rememberSaveable { mutableStateOf<Long?>(null) }
    var waitingCare by rememberSaveable { mutableStateOf<Long?>(null) }
    var afterSave by rememberSaveable { mutableStateOf(DeparturePage.HOME.name) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val activity = LocalContext.current as? Activity
    val responding = state.response.active
    val shownStopRequest = if (state.isDemo) StopRequestState(isDemo = true) else stopRequest
    fun cancelNavigation() { if (!state.isDemo) onCancelStop() }
    fun closeResponse() { cancelNavigation(); actions.finishResponse() }
    fun open(value: DeparturePage) { pageName = value.name }
    fun back() { open(if (page in listOf(DeparturePage.PACKING, DeparturePage.ADD_ITEM)) DeparturePage.CHECKLIST else DeparturePage.HOME) }
    fun awaitPreparation(destination: DeparturePage = DeparturePage.HOME, action: () -> Unit) {
        waitingPreparation = state.preparation.revision; afterSave = destination.name; action()
    }
    LaunchedEffect(restricted) {
        if (restricted) {
            pageName = DeparturePage.HOME.name; waitingPreparation = null; waitingCare = null
            focus.clearFocus(force = true); keyboard?.hide()
        }
    }
    LaunchedEffect(responding) {
        if (responding) {
            pageName = DeparturePage.HOME.name; waitingPreparation = null; waitingCare = null
            focus.clearFocus(force = true); keyboard?.hide()
        }
    }
    DisposableEffect(activity) {
        val original = activity?.window?.attributes?.softInputMode
        onDispose { if (original != null) activity?.window?.setSoftInputMode(original) }
    }
    LaunchedEffect(page, restricted) {
        // Pleos IME policy: only the actual input page resizes. Other windows' keyboards do not move this app.
        activity?.window?.setSoftInputMode(if (page == DeparturePage.ADD_ITEM && !restricted)
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        if (page != DeparturePage.ADD_ITEM) { focus.clearFocus(force = true); keyboard?.hide() }
    }
    LaunchedEffect(state.preparation.revision, state.preparation.problem, waitingPreparation) {
        waitingPreparation?.let { previous ->
            if (state.preparation.revision > previous) { pageName = afterSave; waitingPreparation = null }
            else if (state.preparation.problem != null) waitingPreparation = null
        }
    }
    LaunchedEffect(state.care.revision, state.care.problem, waitingCare) {
        waitingCare?.let { previous ->
            if (state.care.revision > previous) { pageName = DeparturePage.HOME.name; waitingCare = null }
            else if (state.care.problem != null) waitingCare = null
        }
    }
    BackHandler(enabled = responding || (!restricted && page != DeparturePage.HOME)) { if (responding) closeResponse() else back() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        DepartureHeader(state, page, restricted, responding, { if (responding) closeResponse() else back() }, { open(DeparturePage.TOOLS) })
        if (responding) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                CryResponseScreen(state, actions, restricted, shownStopRequest, onNavigateStop,
                    { actions.responseNavigationCandidate()?.let(onNavigateStop) }, onConfirmStop, ::cancelNavigation,
                    { actions.finishResponse(); open(DeparturePage.RECORDS) })
            }
        } else if (restricted) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                CryResponseEntry(state, actions)
                ResponseNavigationStatus(shownStopRequest, true, onConfirmStop, ::cancelNavigation)
                DrivingDeparture(state, actions)
            }
        } else {
            val scroller = key(page) { rememberScrollState() }
            Box(Modifier.weight(1f).then(if (page == DeparturePage.ADD_ITEM) Modifier.imePadding() else Modifier)) {
                when (page) {
                    DeparturePage.RECORDS -> CareScreen(state.care, actions::recordCare, actions::undoCare, onReloadCare,
                        actions::saveProfile, actions::editCare) {
                        if (state.isDemo) Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            DepartureText(stringResource(R.string.departure_demo_detail))
                            CareButton(stringResource(R.string.departure_real_connections), { actions.endDemo(); open(DeparturePage.CONNECTIONS) })
                        } else connections()
                    }
                    DeparturePage.CONNECTIONS -> connections()
                    else -> Column(Modifier.fillMaxSize().verticalScroll(scroller).padding(8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        DepartureMessage(state) { actions.load(); onReloadCare() }
                        ResponseNavigationStatus(shownStopRequest, false, onConfirmStop, ::cancelNavigation)
                        if (page == DeparturePage.HOME) CryResponseEntry(state, actions)
                        when (page) {
                            DeparturePage.HOME -> DepartureHome(state, { choice -> open(when (choice) {
                                DepartureChoice.NOW -> DeparturePage.NOW
                                DepartureChoice.AFTER_CARE -> DeparturePage.CARE_FIRST
                                DepartureChoice.STOP_ON_WAY -> DeparturePage.STOPS
                            }) }, { open(DeparturePage.CHECKLIST) }, { open(DeparturePage.RECORDS) }, actions::recordCare, actions::undoCare)
                            DeparturePage.CHECKLIST -> PackingChecklist(state, actions, { open(DeparturePage.PACKING) }, { open(DeparturePage.ADD_ITEM) })
                            DeparturePage.PACKING -> state.preparation.record?.let { record -> PackingSettingsScreen(record, state.preparation.canWrite) { settings ->
                                awaitPreparation(DeparturePage.CHECKLIST) { actions.saveSettings(settings) }
                            } }
                            DeparturePage.ADD_ITEM -> AddPackingItem(state.preparation.canWrite) { label ->
                                awaitPreparation(DeparturePage.CHECKLIST) { actions.addItem(label) }
                            }
                            DeparturePage.TIMING -> RhythmSettings(state.care.record, state.care.canWrite, { feed, awake, seated ->
                                waitingCare = state.care.revision; actions.saveTiming(feed, awake, seated)
                            }, { open(DeparturePage.RECORDS) })
                            DeparturePage.NOW -> DepartureReview(state) { seated -> awaitPreparation { actions.choose(DepartureChoice.NOW, seated) } }
                            DeparturePage.CARE_FIRST -> CareFirstPlan(state, actions) { awaitPreparation { actions.choose(DepartureChoice.AFTER_CARE) } }
                            DeparturePage.STOPS -> DepartureStopPlans(state, { candidate ->
                                if (state.isDemo) awaitPreparation { actions.choose(DepartureChoice.STOP_ON_WAY, candidate = candidate) }
                                else { actions.choose(DepartureChoice.STOP_ON_WAY, candidate = candidate); onNavigateStop(candidate); open(DeparturePage.CONNECTIONS) }
                            }) { awaitPreparation { actions.choose(DepartureChoice.STOP_ON_WAY) } }
                            DeparturePage.TOOLS -> DepartureTools(state, actions, { open(DeparturePage.TIMING) },
                                { actions.endDemo(); open(DeparturePage.CONNECTIONS) }, { open(DeparturePage.NEW_OUTING) }, { open(DeparturePage.HOME) })
                            DeparturePage.NEW_OUTING -> CareCard(stringResource(R.string.departure_new_outing)) {
                                DepartureText(stringResource(R.string.departure_new_outing_detail))
                                CareButton(stringResource(R.string.departure_new_outing_confirm), { awaitPreparation { actions.newOuting() } },
                                    Modifier.fillMaxWidth(), enabled = state.preparation.canWrite)
                            }
                            else -> Unit
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(DeparturePage.HOME to R.string.departure_nav_home, DeparturePage.CHECKLIST to R.string.departure_nav_packing,
                    DeparturePage.RECORDS to R.string.departure_nav_records).forEach { (destination, label) ->
                    CareButton(stringResource(label), { open(destination) }, Modifier.weight(1f), primary = false,
                        selected = page == destination, tag = "departure-tab-${destination.name}")
                }
            }
        }
    }
}

@Composable
private fun DepartureHeader(state: DepartureUiState, page: DeparturePage, restricted: Boolean, responding: Boolean, onBack: () -> Unit, onTools: () -> Unit) {
    val type = LocalPleosTypography.current
    val profile = state.care.record?.profile
    val ux = LocalUxRestrictions.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!restricted && (responding || page != DeparturePage.HOME)) CareButton(stringResource(R.string.care_back), onBack, Modifier.width(96.dp), primary = false)
        Column(Modifier.weight(1f)) {
            DepartureText(stringResource(if (responding) R.string.response_title else when (page) {
                DeparturePage.HOME -> R.string.app_name
                DeparturePage.CHECKLIST -> R.string.departure_nav_packing
                DeparturePage.RECORDS -> R.string.departure_nav_records
                DeparturePage.PACKING -> R.string.packing_settings_open
                DeparturePage.ADD_ITEM -> R.string.packing_add
                DeparturePage.TIMING -> R.string.departure_timing_action
                DeparturePage.NOW -> R.string.departure_now
                DeparturePage.CARE_FIRST -> R.string.departure_after_care
                DeparturePage.STOPS -> R.string.departure_stop_plan
                DeparturePage.CONNECTIONS -> R.string.departure_connections
                else -> R.string.departure_tools
            }), style = type.titleLarge, maxLines = 1)
            val context = when {
                state.isDemo -> stringResource(R.string.departure_demo_badge)
                state.journey.readyValue?.isSimulation == true -> stringResource(R.string.departure_native_simulation)
                ux.source != UxRestrictionsState.Source.CAR_SERVICE -> stringResource(R.string.departure_drive_unknown)
                restricted -> stringResource(R.string.departure_driving_badge)
                profile != null -> stringResource(R.string.departure_baby_context, profile.monthAge)
                else -> stringResource(R.string.departure_welcome)
            }
            DepartureText(context, secondary = true, maxLines = 1)
        }
        if (!restricted && !responding) CareButton(stringResource(R.string.departure_tools), onTools, primary = false, tag = "departure-tools")
    }
}

@Composable
private fun DepartureMessage(state: DepartureUiState, onReload: () -> Unit) {
    val message = when {
        state.preparation.saving || state.care.isSaving -> R.string.departure_saving
        state.preparation.status == DepartureStoreStatus.NEEDS_RELOAD || state.care.status == CareStoreStatus.NEEDS_RELOAD -> R.string.departure_storage_error
        state.preparation.problem == DepartureProblem.PARK_REQUIRED -> R.string.departure_park_required
        state.preparation.problem != null || state.care.problem != null || state.demoProblem -> R.string.departure_input_error
        state.care.notice == CareNotice.RECORDED && state.response.notice != com.hmgchallenge.shared.response.ResponseNotice.CARE_RECORDED ->
            if (state.isDemo) R.string.departure_demo_record_saved else R.string.departure_record_saved
        state.care.notice == CareNotice.UNDONE -> R.string.departure_record_undone
        else -> null
    }
    if (message != null) DepartureText(stringResource(message))
    if (state.preparation.status == DepartureStoreStatus.NEEDS_RELOAD || state.care.status == CareStoreStatus.NEEDS_RELOAD) {
        CareButton(stringResource(R.string.departure_reload), onReload, Modifier.fillMaxWidth())
    }
}

@Composable
private fun DrivingDeparture(state: DepartureUiState, actions: DepartureController) {
    val forecast = state.evaluation?.forecast
    val ux = LocalUxRestrictions.current
    CareCard(stringResource(R.string.departure_driving_title)) {
        DepartureText(stringResource(if (forecast?.journey?.careBeforeArrival?.isNotEmpty() == true) R.string.departure_driving_stop else R.string.departure_driving_check),
            style = LocalPleosTypography.current.titleLarge)
        DepartureText(stringResource(R.string.departure_arrival) + " · " + timeText(forecast?.journey?.estimatedArrivalAtEpochMillis),
            style = LocalPleosTypography.current.titleSmall)
        val feed = forecast?.needs?.firstOrNull { it.need == CareNeed.FEED }
        DepartureText(stringResource(R.string.departure_next_feed) + " · " + estimateText(feed?.estimate))
        DepartureText(stringResource(if (ux.source == UxRestrictionsState.Source.CAR_SERVICE) R.string.departure_driving_setup
            else R.string.departure_unknown_setup), secondary = true)
    }
    QuickCareRow(state, actions::recordCare, actions::undoCare)
}
