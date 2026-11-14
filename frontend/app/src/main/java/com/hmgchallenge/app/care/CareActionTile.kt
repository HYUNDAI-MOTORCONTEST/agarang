package com.hmgchallenge.app.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.ui.components.CareGlyph
import com.hmgchallenge.app.ui.components.CareSymbol
import com.hmgchallenge.app.ui.theme.PleosMotion
import com.hmgchallenge.app.ui.theme.PleosRoles.resolveButtonStyle
import com.hmgchallenge.app.ui.theme.PleosTheme

/** A labelled, whole-surface action. Press and confirmed selection have distinct static feedback. */
@Composable
internal fun CareActionTile(
    title: String,
    detail: String,
    icon: CareSymbol,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    stacked: Boolean = false,
    tag: String,
    selectionRole: Role = Role.RadioButton,
) {
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val style = PleosTheme.colors.resolveButtonStyle(enabled = enabled, primary = false, selected = selected, pressed = pressed)
    val input = if (selected != null) Modifier.selectable(selected, interactionSource = interaction, indication = null,
        enabled = enabled, role = selectionRole, onClick = onClick)
        else Modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    val surface = modifier
        .heightIn(min = if (stacked) dimens.actionChoiceHeight else dimens.actionTileHeight)
        .testTag(tag)
        .then(input)
        .graphicsLayer {
            val scale = if (pressed && enabled) PleosMotion.pressedScale else 1f
            scaleX = scale; scaleY = scale
        }
        .clip(shapes.container)
        .background(style.container)
        .border(if (selected == true) dimens.borderMedium else dimens.borderThin, style.border ?: style.content, shapes.container)
        .padding(dimens.cardPaddingCompact)
    val text: @Composable () -> Unit = {
        Text(LocalUxRestrictions.current.limit(title), style = PleosTheme.typography.titleSmall,
            color = style.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(LocalUxRestrictions.current.limit(detail), style = PleosTheme.typography.labelMedium,
            color = style.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (stacked) Column(surface, verticalArrangement = Arrangement.spacedBy(dimens.microSpacing)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CareGlyph(icon, tint = style.content, selected = selected == true)
            if (selected == true) CareGlyph(CareSymbol.CHECK, tint = style.content, selected = true)
        }
        text()
    } else Row(surface, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
        CareGlyph(icon, tint = style.content, selected = selected == true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.microSpacing)) { text() }
        CareGlyph(if (selected == true) CareSymbol.CHECK else CareSymbol.CHEVRON, tint = style.content, selected = selected == true)
    }
}
