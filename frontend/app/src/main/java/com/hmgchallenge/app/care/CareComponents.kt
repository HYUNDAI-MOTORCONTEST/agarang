package com.hmgchallenge.app.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.ui.components.CareGlyph
import com.hmgchallenge.app.ui.components.CareSymbol
import com.hmgchallenge.app.ui.theme.PleosMotion
import com.hmgchallenge.app.ui.theme.PleosRoles.cardBorder
import com.hmgchallenge.app.ui.theme.PleosRoles.cardContainer
import com.hmgchallenge.app.ui.theme.PleosRoles.resolveButtonStyle
import com.hmgchallenge.app.ui.theme.PleosTheme

@Composable
internal fun CareText(text: String, modifier: Modifier = Modifier, style: TextStyle = PleosTheme.typography.labelMedium, maxLines: Int = 3) {
    Text(
        text = LocalUxRestrictions.current.limit(text),
        modifier = modifier,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun CareCard(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PleosTheme.colors
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val type = PleosTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.card)
            .background(colors.cardContainer)
            .border(dimens.borderThin, colors.cardBorder, shapes.card)
            .padding(if (compact) dimens.cardPaddingCompact else dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(if (compact) dimens.itemSpacing else dimens.cardContentSpacing),
    ) {
        if (accentColor != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.tightSpacing),
            ) {
                Box(
                    Modifier
                        .width(dimens.accentBarWidth)
                        .height(dimens.accentBarHeight)
                        .clip(shapes.pillBar)
                        .background(accentColor),
                )
                CareText(title, style = type.titleSmall)
            }
        } else {
            CareText(title, style = type.titleSmall)
        }
        content()
    }
}

/** Same Pleos token pairs and contrast as PleosRoles. No ripple or animated transitions while driving.
 * Pressed content shrinks to 95% per pleos-components.md; the outer touch target keeps its full size.
 * 72dp actions / 64dp minimum width: Pleos has no numeric minimum; Google car-app-quality UX-1 is supplementary.
 */
@Composable
internal fun CareButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    selected: Boolean? = null,
    tag: String? = null,
    compact: Boolean = false,
    description: String? = null,
    leadingIcon: CareSymbol? = null,
    selectionRole: Role = Role.RadioButton,
) {
    val colors = PleosTheme.colors
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val style = colors.resolveButtonStyle(
        enabled = enabled,
        primary = primary,
        selected = selected,
        pressed = pressed,
    )
    val input = if (selected != null) {
        Modifier.selectable(selected, interactionSource = interaction, indication = null, enabled = enabled, role = selectionRole, onClick = onClick)
    } else {
        Modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    }
    val borderModifier = style.border?.let { borderColor ->
        Modifier.border(
            width = if (selected == true) dimens.borderMedium else dimens.borderThin,
            color = borderColor,
            shape = shapes.control,
        )
    } ?: Modifier
    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = dimens.touchMinSize,
                minHeight = if (compact) dimens.touchMinSize else dimens.buttonHeight,
            )
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .then(input)
            .then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })
            .graphicsLayer {
                val scale = if (pressed && enabled) PleosMotion.pressedScale else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(shapes.control)
            .background(style.container)
            .then(borderModifier)
            .padding(
                horizontal = if (compact) dimens.buttonHorizontalPaddingCompact else dimens.buttonHorizontalPadding,
                vertical = if (compact) dimens.nanoSpacing else dimens.buttonVerticalPaddingCompact,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (style.indicator != null || leadingIcon != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.microSpacing),
            ) {
                if (style.indicator != null) Box(
                    Modifier
                        .width(dimens.tabIndicatorWidth)
                        .height(dimens.tabIndicatorHeight)
                        .clip(shapes.pillBar)
                        .background(style.indicator),
                )
                if (leadingIcon != null) CareGlyph(leadingIcon, tint = style.content, selected = selected == true)
                Text(
                    text = LocalUxRestrictions.current.limit(label),
                    modifier = Modifier.weight(1f, fill = false),
                    style = PleosTheme.typography.labelMedium,
                    color = style.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = LocalUxRestrictions.current.limit(label),
                style = PleosTheme.typography.labelMedium,
                color = style.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
