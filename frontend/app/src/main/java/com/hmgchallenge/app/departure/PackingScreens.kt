package com.hmgchallenge.app.departure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import com.hmgchallenge.app.R
import com.hmgchallenge.app.care.CareButton
import com.hmgchallenge.app.care.CareCard
import com.hmgchallenge.app.ui.theme.PleosMotion
import com.hmgchallenge.app.ui.theme.PleosRoles.cardBorder
import com.hmgchallenge.app.ui.theme.PleosRoles.cardContainer
import com.hmgchallenge.app.ui.theme.PleosRoles.nestedContainer
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.shared.departure.DepartureRecord
import com.hmgchallenge.shared.departure.PackingDurationSource
import com.hmgchallenge.shared.departure.PackingItem
import com.hmgchallenge.shared.departure.PackingPlan
import com.hmgchallenge.shared.departure.PackingProblem
import com.hmgchallenge.shared.departure.PackingScope
import com.hmgchallenge.shared.departure.PackingSettings
import com.hmgchallenge.shared.departure.SupplyKind

@Composable
internal fun PackingChecklist(state: DepartureUiState, actions: DepartureController, onSettings: () -> Unit, onAdd: () -> Unit) {
    val dimens = PleosTheme.dimens
    val record = state.preparation.record ?: return
    val evaluation = state.evaluation ?: return
    val plan = evaluation.packing
    CareCard(stringResource(R.string.packing_summary_title)) {
        DepartureText(stringResource(R.string.packing_preview_title, evaluation.checkedItems, evaluation.totalItems), style = PleosTheme.typography.titleLarge)
        PackingBasis(plan)
        CareButton(stringResource(R.string.packing_settings_open), onSettings, Modifier.fillMaxWidth(), primary = false, tag = "packing-settings")
    }
    record.items.forEach { item ->
        val checked = plan.isChecked(item, evaluation.evaluatedAtEpochMillis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing), verticalAlignment = Alignment.CenterVertically) {
            PackingCheckRow(item, plan, checked, state.preparation.canWrite, { actions.toggleItem(item.id) }, Modifier.weight(1f))
            CareButton(stringResource(R.string.packing_remove), { actions.removeItem(item.id) }, enabled = state.preparation.canWrite,
                primary = false, tag = "packing-remove-${item.id}")
        }
    }
    CareButton(stringResource(R.string.packing_add), onAdd, Modifier.fillMaxWidth(), enabled = state.preparation.canWrite && record.items.size < 30, tag = "packing-add")
    CareButton(stringResource(R.string.packing_restore), actions::restoreDefaults, Modifier.fillMaxWidth(),
        enabled = state.preparation.canWrite && record.items.size <= 25, primary = false)
}

@Composable
internal fun PackingBasis(plan: PackingPlan) {
    val type = PleosTheme.typography
    DepartureText(stringResource(if (plan.scope == PackingScope.OUTBOUND) R.string.packing_scope_outbound else R.string.packing_scope_outing),
        style = type.bodyLargeStrong)
    DepartureText(plan.durationMinutes?.let { stringResource(R.string.packing_duration, it) } ?: stringResource(when (plan.problem) {
        PackingProblem.OUTING_RANGE_UNCONFIRMED -> R.string.packing_extra_required
        PackingProblem.TRIP_TOO_LONG -> R.string.packing_duration_too_long
        else -> R.string.packing_duration_unknown
    }))
    DepartureText(stringResource(when (plan.source) {
        PackingDurationSource.NAVIGATION -> R.string.packing_source_navi
        PackingDurationSource.MANUAL -> R.string.packing_source_manual
        PackingDurationSource.DEMO -> R.string.packing_source_demo
        PackingDurationSource.UNKNOWN -> R.string.packing_source_unknown
    }), secondary = true)
    if (plan.needsQuantityConfirmation) DepartureText(stringResource(R.string.packing_intervals_needed), secondary = true)
    else DepartureText(stringResource(R.string.packing_counts, plan.feedServings!!, plan.diapers!!), style = type.titleSmall)
}

/** 96dp rows/48dp glyph, 24dp gaps. Touch minima supplement Pleos with Google car-app-quality UX-1. */
@Composable
private fun PackingCheckRow(item: PackingItem, plan: PackingPlan, checked: Boolean, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val colors = PleosTheme.colors
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val description = stringResource(if (checked) R.string.packing_checked else R.string.packing_unchecked)
    val rowBackground = when {
        pressed -> colors.surface_accent
        checked -> colors.cardContainer
        else -> colors.nestedContainer
    }
    val rowBorder = if (checked) colors.informative_active else colors.cardBorder
    Row(
        modifier
            .sizeIn(minHeight = dimens.rowMinHeight)
            .semantics { stateDescription = description }
            .toggleable(checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .graphicsLayer {
                val scale = if (pressed && enabled) PleosMotion.pressedScale else 1f
                scaleX = scale; scaleY = scale
            }
            .clip(shapes.card)
            .background(rowBackground)
            .border(dimens.borderThin, rowBorder, shapes.card)
            .padding(dimens.cardPaddingCompact)
            .testTag("packing-check-${item.id}"),
        horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckGlyph(checked)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.microSpacing)) {
            DepartureText(itemTitle(item), style = PleosTheme.typography.bodyMediumStrong)
            val quantity = plan.quantity(item.kind)
            if (quantity != null) DepartureText(stringResource(if (item.kind == SupplyKind.FEEDING) R.string.packing_feed_count else R.string.packing_diaper_count, quantity), secondary = true)
            else if (item.kind == SupplyKind.FEEDING || item.kind == SupplyKind.DIAPERS) DepartureText(stringResource(R.string.packing_count_unknown), secondary = true)
            if (!checked && item.checkedAtEpochMillis != null) DepartureText(stringResource(R.string.packing_recheck), secondary = true)
        }
    }
}

@Composable
internal fun CheckGlyph(checked: Boolean) {
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(dimens.iconSize)) {
        val radius = shapes.checkboxRadius.toPx()
        drawRoundRect(if (checked) ink else paper, cornerRadius = CornerRadius(radius))
        if (!checked) drawRoundRect(ink, cornerRadius = CornerRadius(radius), style = Stroke(dimens.strokeCheck.toPx()))
        else {
            drawLine(paper, Offset(size.width * .23f, size.height * .5f), Offset(size.width * .43f, size.height * .7f), dimens.strokeCheckMark.toPx())
            drawLine(paper, Offset(size.width * .43f, size.height * .7f), Offset(size.width * .78f, size.height * .28f), dimens.strokeCheckMark.toPx())
        }
    }
}

@Composable
internal fun PackingSettingsScreen(record: DepartureRecord, enabled: Boolean, onSave: (PackingSettings) -> Unit) {
    val dimens = PleosTheme.dimens
    var scope by rememberSaveable(record.outingId) { mutableStateOf(record.settings.scope.name) }
    var manual by rememberSaveable(record.outingId) { mutableStateOf(record.settings.manualTravelMinutes) }
    var extra by rememberSaveable(record.outingId) { mutableIntStateOf(record.settings.extraOutingMinutes) }
    var feed by rememberSaveable(record.outingId) { mutableStateOf(record.settings.feedIntervalMinutes) }
    var diaper by rememberSaveable(record.outingId) { mutableStateOf(record.settings.diaperIntervalMinutes) }
    var spareFeed by rememberSaveable(record.outingId) { mutableIntStateOf(record.settings.spareFeedServings) }
    var spareDiaper by rememberSaveable(record.outingId) { mutableIntStateOf(record.settings.spareDiapers) }
    DepartureText(stringResource(R.string.packing_calculation_detail), secondary = true)
    CareCard(stringResource(R.string.packing_scope_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
            PackingScope.entries.forEach { value -> CareButton(stringResource(if (value == PackingScope.OUTBOUND) R.string.packing_scope_outbound else R.string.packing_scope_outing),
                { scope = value.name }, Modifier.weight(1f), enabled = enabled, primary = false, selected = scope == value.name) }
        }
        if (scope == PackingScope.WHOLE_OUTING.name) {
            DepartureText(stringResource(R.string.packing_extra_detail))
            QuantityStepper(extra, 0..PackingSettings.MAX_MINUTES, 30, R.string.departure_minutes, enabled) { extra = it }
        }
    }
    CareCard(stringResource(R.string.packing_manual_title)) {
        DepartureText(stringResource(R.string.packing_manual_detail), secondary = true)
        MinuteChoices(manual, enabled, listOf(null, 30, 60, 90, 120, 180, 240)) { manual = it }
        if (manual != null) QuantityStepper(manual!!, 1..PackingSettings.MAX_MINUTES, 30, R.string.departure_minutes, enabled) { manual = it }
    }
    CareCard(stringResource(R.string.packing_feed_interval)) { MinuteChoices(feed, enabled) { feed = it } }
    CareCard(stringResource(R.string.packing_diaper_interval)) { MinuteChoices(diaper, enabled) { diaper = it } }
    CareCard(stringResource(R.string.packing_spares)) {
        DepartureText(stringResource(R.string.packing_feed_label))
        QuantityStepper(spareFeed, 0..10, 1, R.string.packing_feed_count, enabled) { spareFeed = it }
        DepartureText(stringResource(R.string.packing_diaper_label))
        QuantityStepper(spareDiaper, 0..20, 1, R.string.packing_diaper_count, enabled) { spareDiaper = it }
    }
    CareButton(stringResource(R.string.departure_save), { onSave(PackingSettings(PackingScope.valueOf(scope), manual, extra, feed, diaper, spareFeed, spareDiaper)) },
        Modifier.fillMaxWidth(), enabled = enabled, tag = "packing-save-settings")
}

@Composable
internal fun MinuteChoices(value: Int?, enabled: Boolean, presets: List<Int?> = listOf(null, 90, 120, 150, 180, 240, 360), onChange: (Int?) -> Unit) {
    val dimens = PleosTheme.dimens
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val options = if (value !in presets) presets + value else presets
        val columns = if (maxWidth >= dimens.breakpointMinuteGrid) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
            options.chunked(columns).forEach { values ->
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
                    values.forEach { minutes -> CareButton(if (minutes == null) stringResource(R.string.departure_unset)
                        else stringResource(R.string.departure_minutes, minutes), { onChange(minutes) }, Modifier.weight(1f), enabled = enabled,
                        primary = false, selected = minutes == value) }
                    repeat(columns - values.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun QuantityStepper(value: Int, range: IntRange, step: Int, format: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val dimens = PleosTheme.dimens
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing), verticalAlignment = Alignment.CenterVertically) {
        CareButton(stringResource(R.string.departure_less, step), { onChange((value - step).coerceAtLeast(range.first)) },
            enabled = enabled && value > range.first, primary = false)
        DepartureText(stringResource(format, value), Modifier.weight(1f), PleosTheme.typography.titleSmall)
        CareButton(stringResource(R.string.departure_more, step), { onChange((value + step).coerceAtMost(range.last)) },
            enabled = enabled && value < range.last, primary = false)
    }
}

@Composable
internal fun AddPackingItem(enabled: Boolean, onAdd: (String) -> Unit) {
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val type = PleosTheme.typography
    var value by rememberSaveable { mutableStateOf("") }
    val label = stringResource(R.string.packing_name_label)
    DepartureText(stringResource(R.string.packing_add_detail), secondary = true)
    DepartureText(label, style = type.bodyLargeStrong)
    BasicTextField(value, { text -> if (text.length <= 30 && text.none { it.isISOControl() }) value = text },
        Modifier.fillMaxWidth().sizeIn(minHeight = dimens.heroButtonHeight).clip(shapes.container).background(MaterialTheme.colorScheme.surface)
            .border(dimens.borderMedium, MaterialTheme.colorScheme.outline, shapes.container).padding(dimens.cardPadding)
            .semantics { contentDescription = label }.testTag("packing-name"),
        enabled = enabled, singleLine = true, textStyle = type.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank() && enabled) onAdd(value) }),
        decorationBox = { inner -> Box { if (value.isEmpty()) DepartureText(stringResource(R.string.packing_name_hint), secondary = true); inner() } })
    CareButton(stringResource(R.string.packing_add_confirm), { onAdd(value) }, Modifier.fillMaxWidth(), enabled = enabled && value.isNotBlank(), tag = "packing-add-confirm")
}
