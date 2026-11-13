package com.hmgchallenge.app.facility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmgchallenge.app.R
import com.hmgchallenge.app.care.CareButton
import com.hmgchallenge.app.care.CareCard
import com.hmgchallenge.app.care.CareText
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.ui.theme.LocalPleosTypography
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.FacilityProblem
import com.hmgchallenge.shared.facility.FacilityState
import com.hmgchallenge.shared.facility.LastRestAreaAlert
import com.hmgchallenge.shared.facility.LastRestAreaStatus
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.facility.StopPlanningProblem
import com.hmgchallenge.shared.facility.StopPlanningResult
import java.text.DateFormat
import java.util.Date

data class FacilityUiState(val data: FacilityState, val plans: StopPlanningResult, val isDemo: Boolean)

/** Same token contrast/72dp actions as CareComponents. No motion, popup or automatic navigation. */
@Composable
internal fun FacilityStatusCard(
    state: FacilityUiState,
    alert: LastRestAreaAlert?,
    onRefresh: () -> Unit,
    onToggleDemo: () -> Unit,
    onDismissAlert: () -> Unit,
    onSelect: (StopCandidate) -> Unit,
) {
    val ux = LocalUxRestrictions.current
    val restricted = ux.requiresDistractionOptimization || ux.noSetup
    CareCard(stringResource(R.string.facility_title)) {
        if (state.isDemo) {
            CareText(stringResource(R.string.facility_demo), style = LocalPleosTypography.current.titleSmall)
            CareText(stringResource(R.string.facility_demo_detail))
        } else {
            val status = when {
                state.data.refreshing -> R.string.facility_loading
                state.data.problem == FacilityProblem.OFFLINE && state.data.fromCache -> R.string.facility_offline_cached
                state.data.problem == FacilityProblem.OFFLINE -> R.string.facility_offline_empty
                state.data.problem == FacilityProblem.NOT_CONFIGURED -> R.string.facility_not_configured
                state.data.snapshot.status == DataStatus.STALE -> R.string.facility_stale
                state.data.snapshot.status == DataStatus.FAILED -> R.string.facility_failed
                state.data.snapshot.readyValue?.entries?.isEmpty() == true -> R.string.facility_empty
                state.plans.problem == StopPlanningProblem.ROUTE_UNAVAILABLE -> R.string.facility_no_route
                state.plans.problem == StopPlanningProblem.ACCESS_UNVERIFIED -> R.string.facility_access_unknown
                else -> R.string.facility_ready
            }
            CareText(stringResource(status), style = LocalPleosTypography.current.titleSmall)
            if (state.plans.problem != StopPlanningProblem.NONE) CareText(stringResource(R.string.facility_fallback))
            if (!restricted) {
                state.data.snapshot.value?.let { catalog ->
                    val date = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(catalog.fetchedAtEpochMillis))
                    CareText(stringResource(R.string.facility_attribution, catalog.entries.size, date))
                }
                if (state.data.problem == FacilityProblem.CACHE_UNAVAILABLE) CareText(stringResource(R.string.facility_cache_failed))
            }
        }
        if (alert != null) {
            CareText(stringResource(R.string.facility_last_alert, alert.candidate.facility.name),
                style = LocalPleosTypography.current.titleLarge)
            CareButton(stringResource(R.string.facility_acknowledge), onDismissAlert, tag = "facility-alert-dismiss")
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val full: @Composable () -> Unit = {
                PlanSummary(stringResource(R.string.facility_full_care), state.plans.fullCare.candidates.firstOrNull(), state.isDemo, onSelect)
            }
            val nearest: @Composable () -> Unit = {
                PlanSummary(stringResource(R.string.facility_nearest), state.plans.nearestSafeStop.candidates.firstOrNull(), state.isDemo, onSelect)
            }
            if (maxWidth >= 1_000.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f)) { full() }
                    Column(Modifier.weight(1f)) { nearest() }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { full(); nearest() }
        }
        CareText(when (state.plans.lastRestArea.status) {
            LastRestAreaStatus.AVAILABLE -> stringResource(R.string.facility_last, state.plans.lastRestArea.candidate!!.facility.name)
            LastRestAreaStatus.NO_REST_AREA -> stringResource(R.string.facility_last_none)
            LastRestAreaStatus.UNVERIFIED -> stringResource(R.string.facility_last_unknown)
        })
        if (!state.isDemo) CareButton(stringResource(R.string.facility_refresh), onRefresh,
            enabled = !state.data.refreshing, tag = "facility-refresh")
        CareButton(stringResource(if (restricted) R.string.facility_demo_parked else if (state.isDemo) R.string.facility_demo_end else R.string.facility_demo_start),
            onToggleDemo, primary = false, enabled = !restricted, tag = "facility-demo-toggle")
    }
}

@Composable
private fun PlanSummary(title: String, candidate: StopCandidate?, isDemo: Boolean, onSelect: (StopCandidate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        CareText(title, style = LocalPleosTypography.current.bodyLargeStrong)
        if (candidate == null) CareText(stringResource(R.string.facility_no_candidate)) else {
            // Round up a display duration only; no distance-to-time conversion.
            val seconds = candidate.travelDurationSeconds!!
            val minutes = seconds / 60 + if (seconds % 60 > 0) 1 else 0
            CareText(stringResource(R.string.facility_candidate, candidate.facility.name, minutes))
            CareText(stringResource(R.string.facility_diaper_unknown))
            if (!isDemo && candidate.canRequestNavigation) CareButton(stringResource(R.string.facility_select), { onSelect(candidate) })
        }
    }
}
