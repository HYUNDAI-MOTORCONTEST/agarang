package com.hmgchallenge.app.departure

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.hmgchallenge.app.R
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.ui.theme.LocalPleosTypography
import com.hmgchallenge.shared.departure.DepartureChoice
import com.hmgchallenge.shared.departure.PackingItem
import com.hmgchallenge.shared.departure.SupplyKind
import com.hmgchallenge.shared.forecast.CareTimeEstimate
import com.hmgchallenge.shared.forecast.ForecastBasis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DepartureText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalPleosTypography.current.labelMedium,
    secondary: Boolean = false,
    inverse: Boolean = false,
    maxLines: Int = 2,
) {
    Text(LocalUxRestrictions.current.limit(text), modifier, style = style,
        color = when { inverse -> MaterialTheme.colorScheme.inverseOnSurface; secondary -> MaterialTheme.colorScheme.onSurfaceVariant; else -> MaterialTheme.colorScheme.onSurface },
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun timeText(at: Long?): String = at?.let {
    DateTimeFormatter.ofPattern(stringResource(R.string.departure_time_pattern), Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
} ?: stringResource(R.string.departure_unknown)

@Composable
internal fun estimateText(estimate: CareTimeEstimate?): String {
    if (estimate == null) return stringResource(R.string.departure_unknown)
    return if (estimate.earliestAtEpochMillis == estimate.latestAtEpochMillis) timeText(estimate.expectedAtEpochMillis)
        else stringResource(R.string.departure_time_range, timeText(estimate.earliestAtEpochMillis), timeText(estimate.latestAtEpochMillis))
}

@Composable
internal fun basisText(basis: ForecastBasis?) = stringResource(when (basis) {
    ForecastBasis.CAREGIVER_SETTING -> R.string.departure_basis_setting
    ForecastBasis.PERSONAL_HISTORY -> R.string.departure_basis_history
    ForecastBasis.DEMO_REFERENCE -> R.string.departure_basis_demo
    ForecastBasis.REFERENCE_MODEL -> R.string.departure_basis_reference
    else -> R.string.departure_basis_insufficient
})

@Composable
internal fun choiceText(choice: DepartureChoice) = stringResource(when (choice) {
    DepartureChoice.NOW -> R.string.departure_now
    DepartureChoice.AFTER_CARE -> R.string.departure_after_care
    DepartureChoice.STOP_ON_WAY -> R.string.departure_stop_plan
})

@Composable
internal fun itemTitle(item: PackingItem): String = item.customLabel ?: stringResource(when (item.kind) {
    SupplyKind.FEEDING -> R.string.packing_item_feeding
    SupplyKind.DIAPERS -> R.string.packing_item_diapers
    SupplyKind.WIPES -> R.string.packing_item_wipes
    SupplyKind.CLOTHES -> R.string.packing_item_clothes
    SupplyKind.CAR_SEAT -> R.string.packing_item_seat
    SupplyKind.CUSTOM -> R.string.packing_item_custom
})
