package com.hmgchallenge.app.departure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hmgchallenge.app.R
import com.hmgchallenge.app.care.CareButton
import com.hmgchallenge.app.care.CareCard
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.departure.DepartureChoice
import com.hmgchallenge.shared.departure.DepartureDemoSituation
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.facility.StopPlanningProblem
import com.hmgchallenge.shared.facility.LastRestAreaStatus
import com.hmgchallenge.shared.forecast.CareNeed

@Composable
internal fun DepartureReview(state: DepartureUiState, onConfirm: (Boolean) -> Unit) {
    var seated by rememberSaveable { mutableStateOf(false) }
    CareCard(stringResource(R.string.departure_review_title)) {
        DepartureText(stringResource(R.string.departure_review_detail), style = PleosTheme.typography.bodyLargeStrong)
        state.evaluation?.packing?.let { PackingBasis(it) }
        val careBefore = state.evaluation?.forecast?.journey?.careBeforeArrival?.isNotEmpty() == true
        DepartureText(stringResource(if (careBefore) R.string.departure_review_stop_needed else R.string.departure_review_uncertain), secondary = true)
    }
    CareCard(stringResource(R.string.departure_seating_title)) {
        DepartureText(stringResource(R.string.departure_seating_detail), secondary = true)
        CareButton(stringResource(R.string.departure_seated_now), { seated = !seated }, Modifier.fillMaxWidth(),
            enabled = state.care.record != null, primary = false, selected = seated, tag = "departure-seated")
    }
    CareButton(stringResource(R.string.departure_confirm_now), { onConfirm(seated) }, Modifier.fillMaxWidth(),
        enabled = state.preparation.canWrite, tag = "departure-confirm-now")
}

@Composable
internal fun CareFirstPlan(state: DepartureUiState, actions: DepartureController, onConfirm: () -> Unit) {
    val type = PleosTheme.typography
    CareCard(stringResource(R.string.departure_care_first_title)) {
        DepartureText(stringResource(R.string.departure_care_first_detail), style = type.bodyLargeStrong)
        DepartureText(stringResource(R.string.departure_care_first_eta), secondary = true)
        val feed = state.evaluation?.forecast?.needs?.firstOrNull { it.need == CareNeed.FEED }
        DepartureText(stringResource(R.string.departure_next_feed) + " · " + estimateText(feed?.estimate), style = type.titleSmall)
        DepartureText(basisText(feed?.basis), secondary = true)
    }
    QuickCareRow(state, actions::recordCare, actions::undoCare)
    CareButton(stringResource(R.string.departure_confirm_after_care), onConfirm, Modifier.fillMaxWidth(),
        enabled = state.preparation.canWrite, tag = "departure-confirm-care-first")
}

@Composable
internal fun RhythmSettings(record: CareRecord?, enabled: Boolean, onSave: (Int?, Int?, Int?) -> Unit, onProfile: () -> Unit) {
    if (record == null) {
        DepartureText(stringResource(R.string.departure_first_detail))
        CareButton(stringResource(R.string.departure_profile_action), onProfile, Modifier.fillMaxWidth())
        return
    }
    var feed by rememberSaveable(record.profile.id) { mutableStateOf(record.profile.preferences.feedIntervalMinutes) }
    var awake by rememberSaveable(record.profile.id) { mutableStateOf(record.profile.preferences.awakeWindowMinutes) }
    var seated by rememberSaveable(record.profile.id) { mutableStateOf(record.profile.preferences.seatedBreakMinutes) }
    DepartureText(stringResource(R.string.departure_rhythm_detail), secondary = true)
    CareCard(stringResource(R.string.departure_feed_rhythm)) { MinuteChoices(feed, enabled) { feed = it } }
    CareCard(stringResource(R.string.departure_awake_rhythm)) { MinuteChoices(awake, enabled, listOf(null, 30, 60, 90, 120, 150, 180)) { awake = it } }
    CareCard(stringResource(R.string.departure_break_rhythm)) { MinuteChoices(seated, enabled, listOf(null, 60, 90, 120, 150, 180)) { seated = it } }
    CareButton(stringResource(R.string.departure_save), { onSave(feed, awake, seated) }, Modifier.fillMaxWidth(), enabled = enabled, tag = "departure-save-rhythm")
}

@Composable
internal fun DepartureStopPlans(state: DepartureUiState, onCandidate: (StopCandidate) -> Unit, onPending: () -> Unit) {
    val plans = state.stops
    DepartureText(stringResource(if (state.isDemo) R.string.departure_demo_detail else R.string.departure_stop_plan_detail), secondary = true)
    if (plans == null || plans.problem != StopPlanningProblem.NONE) {
        CareCard(stringResource(R.string.departure_stop_unknown_title)) {
            DepartureText(stringResource(R.string.departure_stop_unknown_detail))
            CareButton(stringResource(R.string.departure_stop_pending), onPending, Modifier.fillMaxWidth(),
                enabled = state.preparation.canWrite, primary = false, tag = "departure-stop-pending")
        }
    }
    listOf(R.string.facility_full_care to plans?.fullCare, R.string.facility_nearest to plans?.nearestSafeStop).forEach { (title, plan) ->
        CareCard(stringResource(title)) {
            val candidate = plan?.candidates?.firstOrNull()
            if (candidate == null) DepartureText(stringResource(R.string.facility_no_candidate)) else {
                DepartureText(candidate.facility.name, style = PleosTheme.typography.titleSmall)
                val seconds = candidate.travelDurationSeconds!!
                DepartureText(stringResource(R.string.departure_stop_minutes, seconds / 60 + if (seconds % 60 > 0) 1 else 0))
                DepartureText(stringResource(R.string.facility_diaper_unknown), secondary = true)
                CareButton(stringResource(if (state.isDemo) R.string.departure_demo_select_stop else R.string.facility_select),
                    { onCandidate(candidate) }, Modifier.fillMaxWidth(), enabled = state.preparation.canWrite && (state.isDemo || candidate.canRequestNavigation),
                    tag = "departure-select-${candidate.facility.id}")
            }
        }
    }
    if (plans?.lastRestArea?.status == LastRestAreaStatus.AVAILABLE) {
        DepartureText(stringResource(R.string.facility_last, plans.lastRestArea.candidate!!.facility.name))
    }
}

@Composable
internal fun DepartureTools(state: DepartureUiState, actions: DepartureController, onTiming: () -> Unit, onConnections: () -> Unit, onNewOuting: () -> Unit, onHome: () -> Unit) {
    val dimens = PleosTheme.dimens
    CareCard(stringResource(R.string.departure_tools_personal)) {
        CareButton(stringResource(R.string.departure_timing_action), onTiming, Modifier.fillMaxWidth(), primary = false)
        CareButton(stringResource(R.string.departure_new_outing), onNewOuting, Modifier.fillMaxWidth(), primary = false)
        if (state.preparation.record?.driveSession?.endedAtEpochMillis == null && state.preparation.record?.driveSession != null) {
            CareButton(stringResource(R.string.departure_end_seating), actions::endSession, Modifier.fillMaxWidth(), primary = false)
        }
        CareButton(stringResource(R.string.departure_connections), onConnections, Modifier.fillMaxWidth(), primary = false)
    }
    CareCard(stringResource(R.string.departure_demo_title)) {
        DepartureText(stringResource(R.string.departure_demo_detail), secondary = true)
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
            CareButton(stringResource(R.string.departure_demo_records), { actions.startDemo(false); onHome() }, Modifier.weight(1f), primary = false, tag = "departure-demo-records")
            CareButton(stringResource(R.string.departure_demo_first_day), { actions.startDemo(true); onHome() }, Modifier.weight(1f), primary = false, tag = "departure-demo-first-day")
        }
        if (state.isDemo) {
            CareButton(stringResource(if (state.demoDelayMinutes == 0) R.string.departure_demo_delay else R.string.departure_demo_clear_delay),
                { actions.changeDemoDelay(); onHome() }, Modifier.fillMaxWidth(), primary = false, tag = "departure-demo-delay")
            CareButton(stringResource(R.string.departure_demo_end), { actions.endDemo(); onHome() }, Modifier.fillMaxWidth(), primary = false, tag = "departure-demo-end")
        }
    }
    CareCard(stringResource(R.string.response_demo_title)) {
        DepartureText(stringResource(R.string.departure_demo_detail), secondary = true)
        listOf(DepartureDemoSituation.NORMAL to R.string.response_demo_imminent,
            DepartureDemoSituation.NO_FACILITIES to R.string.response_demo_no_stops,
            DepartureDemoSituation.NAVIGATION_FAILED to R.string.response_demo_navi_failed).forEach { (situation, label) ->
            CareButton(stringResource(label), { actions.startResponseDemo(situation); onHome() }, Modifier.fillMaxWidth(),
                primary = false, tag = "response-demo-${situation.name}")
        }
    }
}
