package com.hmgchallenge.app.departure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.hmgchallenge.app.R
import com.hmgchallenge.app.care.CareButton
import com.hmgchallenge.app.care.CareCard
import com.hmgchallenge.app.care.CareText
import com.hmgchallenge.app.ui.theme.PleosAccentTone
import com.hmgchallenge.app.ui.theme.PleosRoles.accentColor
import com.hmgchallenge.app.ui.theme.PleosRoles.cardBorder
import com.hmgchallenge.app.ui.theme.PleosRoles.cardContainer
import com.hmgchallenge.app.ui.theme.PleosRoles.heroBorder
import com.hmgchallenge.app.ui.theme.PleosRoles.heroContainer
import com.hmgchallenge.app.ui.theme.PleosRoles.nestedOnHeroContainer
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareJournal
import com.hmgchallenge.shared.departure.DepartureChoice
import com.hmgchallenge.shared.forecast.CareNeed
import com.hmgchallenge.shared.forecast.ForecastReasonCode
import com.hmgchallenge.shared.forecast.ForecastStage

/** Large quiet surfaces; evidence and uncertainty remain visible beside the headline. */
@Composable
internal fun DepartureHome(
    state: DepartureUiState,
    onChoice: (DepartureChoice) -> Unit,
    onChecklist: () -> Unit,
    onProfile: () -> Unit,
    onRecord: (CareEventType) -> Unit,
    onUndo: () -> Unit,
) {
    val dimens = PleosTheme.dimens
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < dimens.breakpointTwoColumn
        val hero: @Composable () -> Unit = { ForecastHero(state, compact, onProfile) }
        val choices: @Composable () -> Unit = { DepartureChoices(state, onChoice) }
        val packing: @Composable () -> Unit = { PackingPreview(state, onChecklist) }
        if (!compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
                    hero()
                    QuickCareRow(state, onRecord, onUndo)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) { choices(); packing() }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
            hero(); choices(); packing(); QuickCareRow(state, onRecord, onUndo)
        }
    }
}

@Composable
private fun ForecastHero(state: DepartureUiState, compact: Boolean, onProfile: () -> Unit) {
    val type = PleosTheme.typography
    val colors = PleosTheme.colors
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val record = state.care.record
    val forecast = state.evaluation?.forecast
    val feed = forecast?.needs?.firstOrNull { it.need == CareNeed.FEED }
    val accentTone = when (forecast?.stage) {
        ForecastStage.IMMINENT -> PleosAccentTone.WARNING
        ForecastStage.CAUTION -> PleosAccentTone.WARNING
        ForecastStage.RELAXED -> PleosAccentTone.ACTIVE
        ForecastStage.UNKNOWN, null -> PleosAccentTone.POSITIVE
    }
    val headline = when {
        record == null -> R.string.departure_first_heading
        record.events.isEmpty() -> R.string.departure_first_day_heading
        forecast?.stage == ForecastStage.IMMINENT -> R.string.departure_care_first_heading
        forecast?.journey?.careBeforeArrival?.isNotEmpty() == true -> R.string.departure_stop_heading
        forecast?.estimates?.isEmpty() != false -> R.string.departure_need_unknown_heading
        forecast?.journey?.estimatedArrivalAtEpochMillis == null -> R.string.departure_route_heading
        else -> R.string.departure_arrival_heading
    }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("departure-forecast")
            .clip(shapes.hero)
            .background(colors.heroContainer)
            .border(dimens.borderThin, colors.heroBorder, shapes.hero)
            .padding(if (compact) dimens.cardPaddingCompact else dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.tightSpacing)) {
            Box(
                Modifier
                    .width(dimens.accentBarWidth)
                    .height(dimens.accentBarHeight)
                    .clip(shapes.pillBar)
                    .background(colors.accentColor(accentTone)),
            )
            DepartureText(stringResource(R.string.departure_forecast_title), secondary = true)
        }
        DepartureText(stringResource(headline), style = if (compact) type.titleLarge else type.headlineSmall)
        DepartureText(stringResource(when {
            record == null -> R.string.departure_first_detail
            feed?.reasons?.contains(ForecastReasonCode.STALE_CARE_RECORD) == true -> R.string.departure_stale_care_detail
            feed?.reasons?.contains(ForecastReasonCode.FUTURE_CARE_RECORD) == true -> R.string.departure_future_care_detail
            forecast?.reasons?.contains(ForecastReasonCode.NO_CARE_RECORDS) == true -> R.string.departure_first_day_detail
            feed?.estimate == null -> R.string.departure_need_unknown_detail
            forecast?.journey?.careBeforeArrival?.isNotEmpty() == true -> R.string.departure_stop_detail
            forecast?.journey?.estimatedArrivalAtEpochMillis == null -> R.string.departure_route_detail
            else -> R.string.departure_forecast_detail
        }), secondary = true)
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
            ForecastMetric(stringResource(R.string.departure_next_feed), estimateText(feed?.estimate), basisText(feed?.basis), Modifier.weight(1f))
            ForecastMetric(stringResource(R.string.departure_arrival), timeText(forecast?.journey?.estimatedArrivalAtEpochMillis),
                state.journey.readyValue?.destinationLabel ?: stringResource(R.string.departure_route_missing), Modifier.weight(1f))
        }
        DepartureText(stringResource(if (forecast?.isLimited != false) R.string.departure_limited else R.string.departure_record_based), secondary = true)
        if (record == null) CareButton(stringResource(R.string.departure_profile_action), onProfile,
            Modifier.fillMaxWidth(), enabled = state.care.canWrite, tag = "departure-profile")
    }
}

@Composable
private fun ForecastMetric(title: String, value: String, basis: String, modifier: Modifier) {
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val colors = PleosTheme.colors
    Column(
        modifier
            .clip(shapes.container)
            .background(colors.nestedOnHeroContainer)
            .padding(dimens.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(dimens.microSpacing),
    ) {
        DepartureText(title, secondary = true, maxLines = 1)
        DepartureText(value, style = PleosTheme.typography.titleSmall, maxLines = 2)
        DepartureText(basis, secondary = true, maxLines = 1)
    }
}

@Composable
private fun DepartureChoices(state: DepartureUiState, onChoice: (DepartureChoice) -> Unit) {
    val dimens = PleosTheme.dimens
    val decision = state.evaluation?.validDecision
    CareCard(stringResource(R.string.departure_choose_title)) {
        if (decision != null) DepartureText(stringResource(R.string.departure_selected, choiceText(decision.choice)))
        if (decision?.facilityId != null) {
            val candidate = (state.stops?.fullCare?.candidates.orEmpty() + state.stops?.nearestSafeStop?.candidates.orEmpty())
                .firstOrNull { it.facility.id == decision.facilityId }
            DepartureText(candidate?.facility?.name ?: stringResource(R.string.departure_selected_stop_unknown), secondary = true)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val horizontal = maxWidth < dimens.breakpointWideCard && maxWidth >= dimens.breakpointHorizontalChoices
            val buttons: @Composable (Modifier) -> Unit = { modifier ->
                DepartureChoice.entries.forEach { choice ->
                    CareButton(choiceText(choice), { onChoice(choice) }, modifier,
                        enabled = state.preparation.canWrite, primary = choice == DepartureChoice.NOW,
                        tag = "departure-choice-${choice.name}")
                }
            }
            if (horizontal) Row(horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) { buttons(Modifier.weight(1f)) }
            else Column(verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) { buttons(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
internal fun PackingPreview(state: DepartureUiState, onOpen: () -> Unit) {
    val dimens = PleosTheme.dimens
    val type = PleosTheme.typography
    val evaluation = state.evaluation
    val plan = evaluation?.packing
    CareCard(stringResource(R.string.packing_preview_title, evaluation?.checkedItems ?: 0, evaluation?.totalItems ?: 0)) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
            Column(Modifier.weight(1f)) {
                DepartureText(stringResource(R.string.packing_feed_label), secondary = true)
                DepartureText(plan?.feedServings?.let { stringResource(R.string.packing_feed_count, it) }
                    ?: stringResource(R.string.packing_count_unknown), style = type.titleSmall)
            }
            Column(Modifier.weight(1f)) {
                DepartureText(stringResource(R.string.packing_diaper_label), secondary = true)
                DepartureText(plan?.diapers?.let { stringResource(R.string.packing_diaper_count, it) }
                    ?: stringResource(R.string.packing_count_unknown), style = type.titleSmall)
            }
        }
        CareButton(stringResource(R.string.packing_open), onOpen, Modifier.fillMaxWidth(), primary = false, tag = "packing-open")
    }
}

@Composable
internal fun QuickCareRow(state: DepartureUiState, onRecord: (CareEventType) -> Unit, onUndo: () -> Unit) {
    val colors = PleosTheme.colors
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val type = PleosTheme.typography
    val sleeping = state.care.record?.let(CareJournal::isSleeping) == true
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shapes.card)
            .background(colors.cardContainer)
            .border(dimens.borderThin, colors.cardBorder, shapes.card)
            .padding(dimens.cardPaddingCompact),
        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing), verticalAlignment = Alignment.CenterVertically) {
            CareText(stringResource(R.string.departure_quick_record), Modifier.weight(1f), type.titleSmall)
            CareButton(stringResource(R.string.departure_undo), onUndo, enabled = state.care.canWrite && state.care.record?.events?.isNotEmpty() == true,
                primary = false, compact = true, tag = "departure-undo")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
            val types = listOf(CareEventType.FEED, CareEventType.DIAPER, if (sleeping) CareEventType.SLEEP_END else CareEventType.SLEEP_START)
            types.forEach { kind ->
                CareButton(stringResource(when (kind) {
                    CareEventType.FEED -> R.string.departure_record_feed
                    CareEventType.DIAPER -> R.string.departure_record_diaper
                    CareEventType.SLEEP_START -> R.string.departure_record_sleep
                    CareEventType.SLEEP_END -> R.string.departure_record_awake
                }), { onRecord(kind) }, Modifier.weight(1f), enabled = state.care.canWrite && state.care.record != null,
                    primary = false, compact = true, tag = "departure-record-${kind.name}")
            }
        }
    }
}
