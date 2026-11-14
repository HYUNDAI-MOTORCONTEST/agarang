package com.hmgchallenge.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.ui.theme.LocalPleosColors
import com.hmgchallenge.app.ui.theme.LocalPleosTypography
import com.hmgchallenge.app.ui.theme.PleosColors
import com.hmgchallenge.app.ui.theme.PleosElevation
import com.hmgchallenge.app.ui.theme.PleosMotion
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.app.ui.theme.pleosShadow
import com.hmgchallenge.app.ui.theme.pleosTween

/**
 * Pleos 버튼 인터랙션(INTER): 누르면 95%로 줄고 배경색이 바뀐다. 물결(ripple)은 쓰지 않는다.
 * 누름 피드백은 조작 확인용이라 운전 중에도 유지한다(pleos-components.md 4장, 팀 판단).
 * 터치 영역 밖으로 손가락이 나가면 취소되는 것은 Compose `clickable` 기본 동작과 같다.
 *
 * 반환한 Modifier는 크기·배경보다 앞에 둔다. 배경색은 [pressed]를 보고 호출한 쪽이 고른다.
 */
@Composable
fun Modifier.pleosPressable(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = this
    .pressScale(interaction, enabled)
    .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)

/**
 * 누름 축소 효과만 준다. `selectable`·`toggleable`처럼 클릭을 따로 다는 곳에서 같은 [interaction]을 넘겨 쓴다.
 */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PleosMotion.pressedScale else 1f,
        animationSpec = pleosTween(PleosMotion.short1),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

enum class PleosButtonVariant {
    /** 주요 동작. 진한 바탕 + 흰 글자(button_filled_*) */
    Filled,
    /** 보조 동작. 옅은 바탕 + 기본 글자색(button_basic_*) */
    Basic,
}

enum class PleosButtonSize {
    /** 주요 버튼 72dp(dimens.buttonHeight) */
    Large,
    /** 그 밖의 버튼 64dp(dimens.touchMinSize, 터치 최소값) */
    Medium,
}

/**
 * 카드·타일 바탕. 팀 공통 역할 PleosRoles.cardContainer(surface_high, 주간·야간 모두 배경과 구분됨)를 따른다.
 * 누르면 한 단계 위 surface_accent(basic_200). 야간에 그 위 보조 글자 대비 7.21:1(팀 계산).
 * basic_300은 야간 보조 글자 대비가 4.36:1이라 쓰지 않는다.
 */
internal fun PleosColors.cardSurface(pressed: Boolean): Color = if (pressed) surface_accent else surface_high

private data class ButtonColors(val container: Color, val content: Color)

private fun PleosColors.button(style: PleosButtonVariant, enabled: Boolean, pressed: Boolean): ButtonColors = when (style) {
    PleosButtonVariant.Filled -> ButtonColors(
        container = when {
            !enabled -> button_filled_disabled
            pressed -> button_filled_pressed
            else -> button_filled_enabled
        },
        // disabled 라벨은 문서에 없어 text_dark_tertiary(팀 판단, 카드 위 대비 주간 4.98, 야간 5.60 — PleosRoles 표)
        content = if (enabled) basic_00 else text_dark_tertiary,
    )
    PleosButtonVariant.Basic -> ButtonColors(
        container = when {
            !enabled -> button_basic_disabled
            pressed -> button_basic_pressed
            else -> button_basic_enabled
        },
        content = if (enabled) text_dark_primary else text_dark_tertiary,
    )
}

/**
 * Pleos Basic button(pleos-ui.md 9장 BUTTON).
 * - [loading]이면 로더를 보이고 조작을 막는다(BUTTON "Loading 상태는 조작 불가"). 운전 중에는 회전 로더 대신 라벨만 둔다.
 * - [icon]은 라벨 앞에 붙는다(아이콘만 있는 버튼은 [PleosIconButton]).
 */
@Composable
fun PleosButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PleosButtonVariant = PleosButtonVariant.Filled,
    size: PleosButtonSize = PleosButtonSize.Large,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: Painter? = null,
) {
    val colors = LocalPleosColors.current
    val restricted = LocalUxRestrictions.current.requiresDistractionOptimization
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !loading
    val c = colors.button(style, active, pressed)
    Row(
        modifier = modifier
            .pleosPressable(interaction, enabled = active, onClick = onClick)
            .sizeIn(minWidth = PleosTheme.dimens.touchMinSize, minHeight = if (size == PleosButtonSize.Large) PleosTheme.dimens.buttonHeight else PleosTheme.dimens.touchMinSize)
            .clip(PleosTheme.shapes.control)
            .background(c.container)
            .padding(horizontal = PleosTheme.dimens.bannerHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.tightSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading && !restricted) {
            CircularProgressIndicator(modifier = Modifier.size(PleosTheme.dimens.progressSize), color = c.content, strokeWidth = PleosTheme.dimens.strokeCheck)
        } else if (icon != null) {
            // 라벨 옆 아이콘은 글자 높이에 맞춰 36dp(팀 판단). 단독 아이콘은 PleosIconButton(48dp)을 쓴다.
            Icon(painter = icon, contentDescription = null, tint = c.content, modifier = Modifier.size(36.dp))
        }
        Text(text = label, style = LocalPleosTypography.current.labelMedium, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

enum class PleosIconButtonStyle {
    /** 옅은 바탕(button_basic_*) */
    Basic,
    /** 바탕 없음 */
    Plain,
    /** 원형 옅은 바탕 */
    Circle,
}

/**
 * Pleos Icon button(ICONBTN). 누구나 아는 아이콘에만 쓴다. [contentDescription]은 필수(화면 읽기·음성 조작).
 * 터치 영역 64dp 이상, 아이콘 48dp(ICON 기준).
 */
@Composable
fun PleosIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PleosIconButtonStyle = PleosIconButtonStyle.Basic,
    enabled: Boolean = true,
    buttonSize: Dp = PleosTheme.dimens.buttonHeight,
) {
    val colors = LocalPleosColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = if (style == PleosIconButtonStyle.Circle) CircleShape else PleosTheme.shapes.control
    val container = when {
        style == PleosIconButtonStyle.Plain -> if (pressed && enabled) colors.button_basic_pressed else Color.Transparent
        !enabled -> colors.button_basic_disabled
        pressed -> colors.button_basic_pressed
        else -> colors.button_basic_enabled
    }
    Box(
        modifier = modifier
            .pleosPressable(interaction, enabled = enabled, onClick = onClick)
            .size(buttonSize.coerceAtLeast(PleosTheme.dimens.touchMinSize))
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.icon_dark_primary else colors.icon_dark_tertiary,
            modifier = Modifier.size(PleosTheme.dimens.iconSize),
        )
    }
}

/**
 * 큰 타일 버튼. 원탭 기록(F1: 수유·기저귀·수면), 울음 대응 시작(F4)처럼 한 번에 눌러야 하는 동작용.
 * 카드 바탕([cardSurface]) + 그림자 Level 1. 최소 높이 [minHeight](기본 144dp).
 * [caption]은 "마지막 2시간 전"처럼 보조 한 줄이며 운전 중에는 숨긴다.
 */
@Composable
fun PleosTileButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: Painter? = null,
    enabled: Boolean = true,
    minHeight: Dp = 144.dp,
) {
    val colors = LocalPleosColors.current
    val type = LocalPleosTypography.current
    val restricted = LocalUxRestrictions.current.requiresDistractionOptimization
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = PleosTheme.shapes.card
    Column(
        modifier = modifier
            .pleosPressable(interaction, enabled = enabled, onClick = onClick)
            .sizeIn(minWidth = PleosTheme.dimens.touchMinSize, minHeight = minHeight)
            .pleosShadow(PleosElevation.level1, shape)
            .clip(shape)
            .background(colors.cardSurface(pressed && enabled))
            .padding(horizontal = PleosTheme.dimens.cardPaddingCompact, vertical = PleosTheme.dimens.cardPaddingCompact),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PleosTheme.dimens.microSpacing, Alignment.CenterVertically),
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (enabled) colors.icon_dark_primary else colors.icon_dark_tertiary,
                modifier = Modifier.size(PleosTheme.dimens.iconSize),
            )
        }
        Text(
            text = label,
            style = type.bodyLargeStrong,
            color = if (enabled) colors.text_dark_primary else colors.text_dark_tertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null && !restricted) {
            Text(
                text = caption,
                style = type.labelMedium,
                color = colors.text_dark_secondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
