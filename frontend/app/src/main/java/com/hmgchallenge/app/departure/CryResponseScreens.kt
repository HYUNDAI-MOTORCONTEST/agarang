package com.hmgchallenge.app.departure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hmgchallenge.app.R
import com.hmgchallenge.app.care.CareButton
import com.hmgchallenge.app.care.CareCard
import com.hmgchallenge.app.ui.theme.PleosAccentTone
import com.hmgchallenge.app.ui.theme.PleosRoles.accentColor
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareJournal
import com.hmgchallenge.shared.facility.StopPlanType
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.forecast.CareNeed
import com.hmgchallenge.shared.forecast.ForecastReasonCode
import com.hmgchallenge.shared.forecast.ForecastStage
import com.hmgchallenge.shared.journey.StopRequestPhase
import com.hmgchallenge.shared.journey.StopRequestState
import com.hmgchallenge.shared.response.ResponseEvidence
import com.hmgchallenge.shared.response.ResponseNotice
import com.hmgchallenge.shared.response.ResponsePhase

@Composable
internal fun CryResponseEntry(state: DepartureUiState, actions: DepartureController) {
    val dimens = PleosTheme.dimens
    val colors = PleosTheme.colors
    if (state.response.phase == ResponsePhase.SUGGESTED) {
        CareCard(stringResource(R.string.response_offer_title), accentColor = colors.accentColor(PleosAccentTone.WARNING)) {
            DepartureText(stringResource(R.string.response_offer_detail))
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
                CareButton(stringResource(R.string.response_offer_confirm), { state.response.episodeId?.let(actions::confirmResponse) }, Modifier.weight(1f), tag = "response-confirm-offer")
                CareButton(stringResource(R.string.response_later), { actions.finishResponse() }, Modifier.weight(1f), primary = false, tag = "response-dismiss-offer")
            }
        }
    } else {
        when (state.response.notice) {
            ResponseNotice.CARE_RECORDED -> DepartureText(stringResource(R.string.response_recorded))
            ResponseNotice.CARE_CONFIRMED -> DepartureText(stringResource(R.string.response_care_confirmed))
            ResponseNotice.CANCELLED -> DepartureText(stringResource(R.string.response_cancelled), secondary = true)
            else -> Unit
        }
        CareButton(stringResource(R.string.response_start), actions::startResponse,
            Modifier.fillMaxWidth().heightIn(min = dimens.heroButtonHeight), tag = "response-start")
    }
}

@Composable
internal fun CryResponseScreen(
    state: DepartureUiState,
    actions: DepartureController,
    restricted: Boolean,
    stopRequest: StopRequestState,
    onSelectNavigation: (StopCandidate) -> Unit,
    onPrepareNavigation: () -> Unit,
    onConfirmNavigation: (String) -> Unit,
    onCancelNavigation: () -> Unit,
    onProfile: () -> Unit,
) {
    val assessment = state.responseAssessment
    val type = PleosTheme.typography
    val dimens = PleosTheme.dimens
    val colors = PleosTheme.colors
    CareCard(stringResource(R.string.response_title), accentColor = colors.accentColor(PleosAccentTone.WARNING)) {
        DepartureText(stringResource(R.string.response_safe_stop), style = type.titleLarge)
        if (!restricted) DepartureText(stringResource(R.string.response_limit), secondary = true)
        else {
            val imminent = assessment?.evidence?.firstOrNull { it.stage == ForecastStage.IMMINENT }
            DepartureText(stringResource(when (imminent?.need) {
                CareNeed.FEED -> R.string.response_feed_short
                CareNeed.SLEEP -> R.string.response_sleep_short
                CareNeed.SEATED_BREAK -> R.string.response_seat_short
                else -> R.string.response_unknown_short
            }), maxLines = 1)
        }
        if (assessment?.careBeforeArrival == true) DepartureText(stringResource(R.string.response_before_arrival))
        if (!restricted) DepartureText(stringResource(R.string.departure_arrival) + " · " + timeText(assessment?.arrivalAtEpochMillis))
        else DepartureText(stringResource(R.string.response_park_for_details), secondary = true)
    }
    if (state.response.notice == ResponseNotice.STOP_EXPIRED) DepartureText(stringResource(R.string.response_stop_expired))
    if (state.response.notice == ResponseNotice.RECORD_FAILED) DepartureText(stringResource(R.string.response_record_failed))
    val evidence: @Composable () -> Unit = {
        if (!restricted) {
            DepartureText(stringResource(R.string.response_evidence_title), style = type.titleSmall)
            assessment?.evidence?.forEach { ResponseEvidenceCard(it) }
            if (assessment?.limited != false) DepartureText(stringResource(R.string.response_insufficient), secondary = true)
        }
    }
    val plans: @Composable () -> Unit = {
        ResponseStopChoices(state, actions, restricted, stopRequest, onCancelNavigation, onSelectNavigation)
        state.response.selected?.let { selection ->
            CareCard(stringResource(R.string.response_selected_stop)) {
                DepartureText(selection.candidate.facility.name, style = type.titleSmall)
                if (state.isDemo) DepartureText(stringResource(R.string.response_demo_selected))
                else {
                    DepartureText(stringResource(R.string.response_navigation_separate), secondary = true)
                    val matching = stopRequest.candidate?.facility?.id == selection.candidate.facility.id &&
                        stopRequest.candidate?.journeyId == selection.candidate.journeyId
                    if (!matching || stopRequest.phase == StopRequestPhase.IDLE || stopRequest.phase == StopRequestPhase.FAILED) {
                        CareButton(stringResource(R.string.response_prepare_navigation), onPrepareNavigation, Modifier.fillMaxWidth(),
                            enabled = stopRequest.phase !in listOf(StopRequestPhase.SUBMITTING, StopRequestPhase.OUTCOME_UNKNOWN), tag = "response-prepare-navigation")
                    }
                }
            }
        }
        ResponseNavigationStatus(stopRequest, restricted, onConfirmNavigation, onCancelNavigation)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (!restricted && maxWidth >= dimens.breakpointTwoColumn) Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) { evidence() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) { plans(); ResponseCareCompletion(state, actions, onProfile) }
        } else Column(verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
            if (!restricted) evidence()
            plans()
            if (!restricted) ResponseCareCompletion(state, actions, onProfile)
        }
    }
    DepartureText(stringResource(R.string.response_emergency), secondary = true)
    CareButton(stringResource(R.string.response_cancel), { onCancelNavigation(); actions.finishResponse() }, Modifier.fillMaxWidth(),
        primary = false, enabled = !state.responseRecording, tag = "response-cancel")
}

@Composable
private fun ResponseEvidenceCard(evidence: ResponseEvidence) {
    val colors = PleosTheme.colors
    val accent = if (evidence.stage == ForecastStage.IMMINENT || evidence.stage == ForecastStage.CAUTION) {
        colors.accentColor(PleosAccentTone.WARNING)
    } else {
        colors.accentColor(PleosAccentTone.POSITIVE)
    }
    val title = stringResource(when (evidence.need) {
        CareNeed.FEED -> R.string.response_feed
        CareNeed.SLEEP -> R.string.response_sleep
        else -> R.string.response_seat
    })
    CareCard(title, accentColor = accent) {
        val problem = when {
            ForecastReasonCode.FUTURE_CARE_RECORD in evidence.reasons || ForecastReasonCode.FUTURE_DRIVE_SESSION in evidence.reasons -> R.string.response_future
            ForecastReasonCode.STALE_CARE_RECORD in evidence.reasons -> R.string.response_stale
            evidence.recordedSleeping -> R.string.response_sleeping
            evidence.lastAtEpochMillis == null -> R.string.response_no_anchor
            else -> null
        }
        if (problem != null) DepartureText(stringResource(problem))
        evidence.elapsedMinutes?.let { DepartureText(stringResource(R.string.response_elapsed, it)) }
        DepartureText(basisText(evidence.basis), secondary = true)
        if (evidence.estimate != null) {
            DepartureText(stringResource(R.string.response_planning_time, estimateText(evidence.estimate)))
            DepartureText(stringResource(when (evidence.stage) {
                ForecastStage.IMMINENT -> R.string.response_imminent
                ForecastStage.CAUTION -> R.string.response_approaching
                else -> R.string.response_not_urgent
            }), secondary = true)
        } else if (!evidence.recordedSleeping) DepartureText(stringResource(R.string.response_no_estimate), secondary = true)
    }
}

@Composable
private fun ResponseStopChoices(state: DepartureUiState, actions: DepartureController, restricted: Boolean,
    stopRequest: StopRequestState, onCancelNavigation: () -> Unit, onSelectNavigation: (StopCandidate) -> Unit) {
    val plans = state.stops
    CareCard(stringResource(R.string.response_stop_title)) {
        if (!restricted) DepartureText(stringResource(R.string.response_stop_detail), secondary = true)
        listOf(StopPlanType.FULL_CARE to plans?.fullCare, StopPlanType.NEAREST_SAFE_STOP to plans?.nearestSafeStop).forEach { (kind, plan) ->
            val candidate = plan?.candidates?.firstOrNull()
            val title = stringResource(if (kind == StopPlanType.FULL_CARE) R.string.response_full_care else R.string.response_nearest)
            if (candidate == null) DepartureText(title + " · " + stringResource(R.string.response_no_stop)) else {
                val seconds = candidate.travelDurationSeconds!!
                val minutes = seconds / 60 + if (seconds % 60 > 0) 1 else 0
                if (!restricted) DepartureText(title + " · " + candidate.facility.name)
                CareButton(stringResource(R.string.response_select_stop, title, minutes), {
                    onCancelNavigation(); actions.selectResponseStop(kind)?.let(onSelectNavigation)
                }, Modifier.fillMaxWidth(), enabled = stopRequest.phase !in listOf(StopRequestPhase.SUBMITTING, StopRequestPhase.OUTCOME_UNKNOWN),
                    primary = kind == StopPlanType.NEAREST_SAFE_STOP, tag = "response-select-${kind.name}")
            }
        }
        if (plans?.fullCare?.candidates.isNullOrEmpty() && plans?.nearestSafeStop?.candidates.isNullOrEmpty()) {
            DepartureText(stringResource(R.string.response_stop_fallback), secondary = true)
        }
    }
}

@Composable
private fun ResponseCareCompletion(state: DepartureUiState, actions: DepartureController, onProfile: () -> Unit) {
    CareCard(stringResource(R.string.response_care_title)) {
        DepartureText(stringResource(R.string.response_care_detail), secondary = true)
        if (state.responseRecording) DepartureText(stringResource(R.string.departure_saving))
        if (state.care.status == com.hmgchallenge.shared.care.CareStoreStatus.NEEDS_RELOAD) {
            CareButton(stringResource(R.string.response_check_records), onProfile, Modifier.fillMaxWidth())
        }
        if (state.care.record == null) {
            CareButton(stringResource(R.string.departure_profile_action), onProfile, Modifier.fillMaxWidth(), enabled = !state.responseRecording)
        } else {
            val sleeping = state.care.record?.let(CareJournal::isSleeping) == true
            listOf(CareEventType.FEED to R.string.departure_record_feed, CareEventType.DIAPER to R.string.departure_record_diaper,
                (if (sleeping) CareEventType.SLEEP_END else CareEventType.SLEEP_START) to
                    (if (sleeping) R.string.departure_record_awake else R.string.departure_record_sleep)).forEach { (kind, label) ->
                CareButton(stringResource(label), { actions.recordResponseCare(kind) }, Modifier.fillMaxWidth(),
                    enabled = state.care.canWrite && !state.responseRecording, primary = false, tag = "response-record-${kind.name}")
            }
        }
        CareButton(stringResource(R.string.response_complete_without_record), { actions.finishResponse(true) }, Modifier.fillMaxWidth(),
            enabled = !state.responseRecording, primary = false, tag = "response-care-confirmed")
    }
}

/** Navigation outcome stays visible after closing response, including uncertain submitted requests. */
@Composable
internal fun ResponseNavigationStatus(request: StopRequestState, restricted: Boolean, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    if (request.phase == StopRequestPhase.IDLE) return
    CareCard(stringResource(R.string.navi_title)) {
        if (!restricted) request.candidate?.facility?.name?.let { DepartureText(it) }
        when (request.phase) {
            StopRequestPhase.CHECKING -> DepartureText(stringResource(R.string.navi_checking_stop))
            StopRequestPhase.AWAITING_CONFIRMATION -> {
                request.confirmation?.let { confirmation ->
                    DepartureText(stringResource(R.string.navi_confirm_stop, confirmation.candidate.facility.name))
                    CareButton(stringResource(R.string.navi_add_stop), { onConfirm(confirmation.id) }, Modifier.fillMaxWidth(), tag = "response-confirm-navigation")
                    CareButton(stringResource(R.string.navi_cancel_stop), onCancel, Modifier.fillMaxWidth(), primary = false)
                }
            }
            StopRequestPhase.SUBMITTING -> DepartureText(stringResource(R.string.navi_submitting))
            StopRequestPhase.CONFIRMED -> DepartureText(stringResource(if (request.isDemo) R.string.navi_demo_confirmed else R.string.navi_confirmed))
            StopRequestPhase.OUTCOME_UNKNOWN -> DepartureText(stringResource(R.string.navi_outcome_unknown))
            StopRequestPhase.FAILED -> DepartureText(stringResource(R.string.navi_stop_failed))
            StopRequestPhase.IDLE -> Unit
        }
    }
}
