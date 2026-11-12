package com.hmgchallenge.app.care

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.ui.theme.LocalPleosColors
import com.hmgchallenge.app.ui.theme.LocalPleosTypography

@Composable
internal fun CareText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalPleosTypography.current.labelMedium, maxLines: Int = 3) {
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
internal fun CareCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        CareText(title, style = LocalPleosTypography.current.titleSmall)
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
) {
    val colors = LocalPleosColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val filled = primary || selected == true
    val container = when {
        !enabled -> colors.button_filled_disabled
        pressed && filled -> colors.button_filled_pressed
        filled -> colors.button_filled_enabled
        pressed -> colors.surface_accent
        else -> colors.surface_high
    }
    val input = if (selected != null) {
        Modifier.selectable(selected, interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier.sizeIn(minWidth = 64.dp, minHeight = 72.dp)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .then(input)
            .then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })
            .graphicsLayer {
                val scale = if (pressed && enabled) 0.95f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp)).background(container)
            .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = if (compact) 0.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = LocalUxRestrictions.current.limit(label),
            style = LocalPleosTypography.current.labelMedium,
            color = when { !enabled -> colors.text_dark_tertiary; filled -> colors.basic_00; else -> colors.text_dark_primary },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
