package com.hmgchallenge.app.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.hmgchallenge.app.R
import com.hmgchallenge.app.care.CareButton
import com.hmgchallenge.app.care.CareCard
import com.hmgchallenge.app.care.CareText
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.journey.JourneySnapshot
import com.hmgchallenge.shared.journey.StopRequestPhase
import com.hmgchallenge.shared.journey.StopRequestProblem
import com.hmgchallenge.shared.journey.StopRequestState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One-column content also fits narrow Pleos windows. Reuses the verified care tokens and targets. */
@Composable
fun NavigationStatusCard(
    journey: DataSnapshot<JourneySnapshot>,
    connection: DataStatus,
    stopRequest: StopRequestState,
    onRefresh: () -> Unit,
    onConfirmStop: (String) -> Unit,
    onCancelStop: () -> Unit,
) {
    CareCard(stringResource(R.string.navi_title), Modifier.testTag("navi-status")) {
        CareText(stringResource(when {
            journey.origin == DataOrigin.DEMO -> R.string.navi_demo
            journey.value?.isSimulation == true -> R.string.navi_simulation
            else -> R.string.navi_actual
        }))
        val current = journey.readyValue
        if (current != null) {
            CareText(current.destinationLabel, maxLines = 2)
            val arrival = current.estimatedArrivalAtEpochMillis
            val text = if (arrival == null) stringResource(R.string.navi_eta_unknown) else {
                val pattern = stringResource(R.string.navi_time_pattern)
                val time = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(arrival))
                stringResource(R.string.navi_arrival, time)
            }
            CareText(text, style = PleosTheme.typography.titleLarge, maxLines = 2)
        } else CareText(stringResource(when (journey.status) {
            DataStatus.LOADING -> R.string.navi_loading
            DataStatus.EMPTY -> R.string.navi_no_route
            DataStatus.UNSUPPORTED -> R.string.navi_unsupported
            DataStatus.PERMISSION_DENIED -> R.string.navi_permission_denied
            DataStatus.FAILED -> R.string.navi_failed
            DataStatus.STALE -> R.string.navi_stale
            DataStatus.READY -> R.string.navi_eta_unknown
        }))
        CareButton(
            stringResource(R.string.action_recheck), onRefresh, Modifier.fillMaxWidth(),
            enabled = connection != DataStatus.LOADING && journey.status != DataStatus.LOADING,
            tag = "navi-refresh",
        )
        when (stopRequest.phase) {
            StopRequestPhase.IDLE -> Unit
            StopRequestPhase.CHECKING -> CareText(stringResource(R.string.navi_checking_stop))
            StopRequestPhase.AWAITING_CONFIRMATION -> {
                val confirmation = stopRequest.confirmation
                if (confirmation != null) {
                    CareText(stringResource(R.string.navi_confirm_stop, confirmation.candidate.facility.name))
                    CareButton(stringResource(R.string.navi_add_stop), { onConfirmStop(confirmation.id) }, Modifier.fillMaxWidth(), tag = "navi-confirm-stop")
                    CareButton(stringResource(R.string.navi_cancel_stop), onCancelStop, Modifier.fillMaxWidth(), primary = false)
                }
            }
            StopRequestPhase.SUBMITTING -> CareText(stringResource(R.string.navi_submitting))
            StopRequestPhase.CONFIRMED -> CareText(stringResource(if (stopRequest.isDemo) R.string.navi_demo_confirmed else R.string.navi_confirmed))
            StopRequestPhase.OUTCOME_UNKNOWN -> CareText(stringResource(R.string.navi_outcome_unknown))
            StopRequestPhase.FAILED -> CareText(stringResource(when (stopRequest.problem) {
                StopRequestProblem.WAYPOINTS_FULL -> R.string.navi_waypoints_full
                StopRequestProblem.ROUTE_CHANGED -> R.string.navi_route_changed
                StopRequestProblem.PERMISSION_DENIED -> R.string.navi_permission_denied
                StopRequestProblem.INVALID_CANDIDATE -> R.string.navi_invalid_candidate
                StopRequestProblem.REQUEST_FAILED -> R.string.navi_waypoints_unknown
                else -> R.string.navi_stop_failed
            }))
        }
    }
}
