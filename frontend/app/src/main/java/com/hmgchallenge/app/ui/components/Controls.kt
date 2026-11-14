package com.hmgchallenge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmgchallenge.app.R
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.ui.theme.LocalPleosColors
import com.hmgchallenge.app.ui.theme.LocalPleosTypography
import com.hmgchallenge.app.ui.theme.PleosElevation
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.app.ui.theme.pleosShadow

/**
 * 체크 행(CHECK). 출발 전 준비물(F2: 분유 2회분·기저귀 3장), 도착 챙김 체크(F5)용.
 * 행 전체가 터치 영역(최소 72dp)이고, 체크 표시는 40dp. [detail]은 운전 중 숨긴다.
 * 색: 체크 바탕 button_filled_enabled, 테두리 icon_dark_secondary(문서에 전용 토큰이 없어 팀 판단).
 */
@Composable
fun PleosCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
) {
    val colors = LocalPleosColors.current
    val type = LocalPleosTypography.current
    val restricted = LocalUxRestrictions.current.requiresDistractionOptimization
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, enabled)
            .toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .sizeIn(minHeight = PleosTheme.dimens.buttonHeight)
            .clip(PleosTheme.shapes.control)
            .background(if (pressed && enabled) colors.button_basic_pressed else Color.Transparent)
            .padding(horizontal = PleosTheme.dimens.itemSpacing, vertical = PleosTheme.dimens.tightSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.cardPaddingCompact),
    ) {
        CheckMark(checked = checked, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = type.bodyLarge,
                color = if (enabled) colors.text_dark_primary else colors.text_dark_tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null && !restricted) {
                Text(text = detail, style = type.labelMedium, color = colors.text_dark_secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 스위치 행(SWITCH). 음성 안내 켜기, 아기 잘 때 소리 끄기 같은 On/Off 설정.
 * 행 전체를 `toggleable`로 감싸 72dp 터치 영역을 만들고, M3 Switch는 표시만 한다(onCheckedChange = null).
 */
@Composable
fun PleosSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
) {
    val colors = LocalPleosColors.current
    val type = LocalPleosTypography.current
    val restricted = LocalUxRestrictions.current.requiresDistractionOptimization
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, enabled)
            .toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .sizeIn(minHeight = PleosTheme.dimens.buttonHeight)
            .padding(horizontal = PleosTheme.dimens.itemSpacing, vertical = PleosTheme.dimens.tightSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.cardPaddingCompact),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = type.bodyLarge,
                color = if (enabled) colors.text_dark_primary else colors.text_dark_tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null && !restricted) {
                Text(text = detail, style = type.labelMedium, color = colors.text_dark_secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.switch_on,
                checkedThumbColor = colors.switch_knob_on,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = colors.switch_off,
                uncheckedThumbColor = colors.switch_knob_off,
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedTrackColor = colors.switch_disabled,
                disabledCheckedThumbColor = colors.switch_knob_disabled,
                disabledUncheckedTrackColor = colors.switch_disabled,
                disabledUncheckedThumbColor = colors.switch_knob_disabled,
                disabledUncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/**
 * 토글 버튼(TOGGLE). 여러 옵션의 켜짐을 한눈에 보여 줄 때(F3: 백색소음, 내기순환, 아기 잠듦).
 * 켜짐은 바탕색 + 체크 아이콘 + 상태 설명(stateDescription)으로 함께 표시해 색만으로 구분하지 않는다.
 * 켜짐 바탕 button_switch_*는 문서에 연결 표기가 없어 추정이다. [floating]이면 그림자 Level 2.
 */
@Composable
fun PleosToggleButton(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
    floating: Boolean = false,
) {
    val colors = LocalPleosColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = PleosTheme.shapes.control
    val container = when {
        !enabled -> if (checked) colors.button_switch_disabled else colors.button_basic_disabled
        checked -> if (pressed) colors.button_switch_pressed else colors.button_switch_enabled
        else -> if (pressed) colors.button_basic_pressed else colors.button_basic_enabled
    }
    val content = if (enabled) colors.text_dark_primary else colors.text_dark_tertiary
    val stateText = stringResource(if (checked) R.string.state_on else R.string.state_off)
    Row(
        modifier = modifier
            .pressScale(interaction, enabled)
            .toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { stateDescription = stateText }
            .sizeIn(minWidth = PleosTheme.dimens.touchMinSize, minHeight = PleosTheme.dimens.buttonHeight)
            .then(if (floating) Modifier.pleosShadow(PleosElevation.level2, shape) else Modifier)
            .clip(shape)
            .background(container)
            .padding(horizontal = PleosTheme.dimens.sectionSpacing),
        horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.tightSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lead = if (checked) painterResource(R.drawable.ic_check) else icon
        if (lead != null) Icon(painter = lead, contentDescription = null, tint = content, modifier = Modifier.size(PleosTheme.dimens.progressSize)) // 라벨 옆 보조 아이콘(팀 판단)
        Text(
            text = label,
            style = if (checked) LocalPleosTypography.current.bodyMediumStrong else LocalPleosTypography.current.bodyMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Stepper(CTRL). 범위가 정해진 값(분유 ml, 기저귀 장수, 공조 온도 단계)을 버튼으로 바꾼다.
 * 주행 중 기본 조작은 슬라이더 드래그 대신 이 버튼이다(팀 판단, pleos-components.md 5.3).
 * 끝값에 닿으면 그 방향 버튼을 Disabled로 한다. 값이 바뀌면 화면 읽기에 바로 알린다(liveRegion).
 */
@Composable
fun PleosStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..10,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    val colors = LocalPleosColors.current
    Row(
        modifier = modifier
            .clip(PleosTheme.shapes.card)
            .background(colors.controller_stepper_background)
            .padding(PleosTheme.dimens.nanoSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.microSpacing),
    ) {
        PleosIconButton(
            icon = painterResource(R.drawable.ic_minus),
            contentDescription = stringResource(R.string.action_decrease),
            onClick = { onValueChange((value - step).coerceAtLeast(range.first)) },
            enabled = enabled && value > range.first,
            style = PleosIconButtonStyle.Plain,
        )
        Text(
            text = format(value),
            style = LocalPleosTypography.current.titleSmall,
            color = if (enabled) colors.text_dark_primary else colors.text_dark_tertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = 120.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        PleosIconButton(
            icon = painterResource(R.drawable.ic_plus),
            contentDescription = stringResource(R.string.action_increase),
            onClick = { onValueChange((value + step).coerceAtMost(range.last)) },
            enabled = enabled && value < range.last,
            style = PleosIconButtonStyle.Plain,
        )
    }
}
