package com.hmgchallenge.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
 * 정보 카드. 바탕 [cardSurface] + 그림자 Level 1(SHADOW, 팀 판단: 카드 = Level 1).
 * [onClick]이 있으면 눌러서 상세로 가는 카드가 된다(95% 누름 효과, 누르면 바탕이 한 단계 바뀜 — [cardSurface]).
 */
@Composable
fun PleosCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPleosColors.current
    val shape = PleosTheme.shapes.card
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = if (onClick != null) modifier.pleosPressable(interaction, enabled = true, onClick = onClick) else modifier
    Column(
        modifier = base
            .pleosShadow(PleosElevation.level1, shape)
            .clip(shape)
            .background(colors.cardSurface(pressed))
            .padding(PleosTheme.dimens.bannerHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(PleosTheme.dimens.tightSpacing),
        content = content,
    )
}

/**
 * Page navigation(PAGENAV) — 앱 상단 바.
 * - [onBack]이 없으면 Main 타입(최상위 화면): Back 버튼을 그리지 않고, 시스템 Back은 MainActivity의
 *   `moveTaskToBack(true)`가 받는다(pleos-platform.md 0장).
 * - [onBack]이 있으면 Depth 타입(하위 화면): 왼쪽 Back 버튼 + 시스템 Back도 [onBack]으로 보낸다([BackHandler]).
 * - [actions]는 오른쪽 Suffix 영역(아이콘 버튼 1~2개 권장). 드롭다운 메뉴는 두지 않는다.
 * - window bar(시스템 UI)와 겹치지 않게 위쪽 여백을 둔다(BAR, 앱 상단 가장자리에 중요한 버튼을 붙이지 않음).
 */
@Composable
fun PageNavigation(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (onBack != null) BackHandler(onBack = onBack)
    val colors = LocalPleosColors.current
    val type = LocalPleosTypography.current
    val restricted = LocalUxRestrictions.current.requiresDistractionOptimization
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .padding(top = PleosTheme.dimens.microSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.itemSpacing),
    ) {
        if (onBack != null) {
            PleosIconButton(
                icon = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                style = PleosIconButtonStyle.Plain,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.titleMedium,
                color = colors.text_dark_primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null && !restricted) {
                Text(text = subtitle, style = type.labelMedium, color = colors.text_dark_secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.sectionSpacing), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * Tabs(TABS) Box 타입, Fitted(너비 균등). 화면 안 2~4개 보기 전환용(팀 판단: 라벨이 잘리지 않는 4개 이하).
 * 선택 표시는 바탕색 + 굵은 글자로 함께 바꿔 색만으로 구분하지 않는다. 전환 애니메이션은 없다.
 * 선택 바탕 button_switch_enabled는 문서에 연결 표기가 없어 추정이다(pleos-components.md 5.1 Toggle).
 */
@Composable
fun PleosTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPleosColors.current
    val type = LocalPleosTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PleosTheme.shapes.card)
            .background(colors.controller_tab_background)
            // 세그먼트형 탭(Box/Fitted)은 탭끼리 붙어 있는 한 덩어리 컨트롤이라 터치 간격 24dp 대신 6dp를 둔다.
            // 각 탭은 64dp 이상이고 경계가 바탕색으로 구분된다(팀 판단, Pleos TAB 형태).
            .padding(6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val container = when {
                selected -> colors.button_switch_enabled
                pressed -> colors.button_basic_pressed
                else -> Color.Transparent
            }
            Text(
                text = label,
                style = if (selected) type.bodyMediumStrong else type.bodyMedium,
                color = if (selected) colors.text_dark_primary else colors.text_dark_secondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction)
                    .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = { onSelect(i) })
                    .sizeIn(minHeight = PleosTheme.dimens.touchMinSize)
                    .clip(PleosTheme.shapes.control)
                    .background(container)
                    .padding(horizontal = PleosTheme.dimens.itemSpacing, vertical = 14.dp),
            )
        }
    }
}

/**
 * portrait(window 1개)·landscape(window 2개) 배치(pleos-ui.md 3·10장).
 * - 가로가 세로보다 길면 landscape로 보고 [primary](핵심)·[secondary](부가 정보)를 2단으로 나란히 놓는다.
 * - portrait에서는 [primary]만 그린다. [secondaryInPortrait]가 true면 아래에 이어 붙인다.
 * - [scrollable]이면 이 컴포넌트가 직접 수동 스크롤을 단다: landscape는 단마다, portrait는 두 단을 합친 하나의 열에.
 *   호출한 쪽은 단 안에 스크롤을 또 달지 않는다(portrait에서 첫 단이 높이를 다 차지해 두 번째 단이 사라진다).
 *   자동 스크롤은 하지 않는다(운전 중 금지). [bottomPadding]은 마지막 항목 아래 여백이다.
 */
@Composable
fun AdaptivePanes(
    modifier: Modifier = Modifier,
    secondaryInPortrait: Boolean = false,
    primaryWeight: Float = 3f,
    secondaryWeight: Float = 2f,
    scrollable: Boolean = true,
    bottomPadding: Dp = PleosTheme.dimens.sectionSpacing,
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit,
) {
    val gap = Arrangement.spacedBy(PleosTheme.dimens.sectionSpacing)
    @Composable
    fun Modifier.scrollIf(): Modifier = if (scrollable) verticalScroll(rememberScrollState()) else this
    BoxWithConstraints(modifier) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = gap) {
                Column(Modifier.weight(primaryWeight).scrollIf().padding(bottom = bottomPadding), verticalArrangement = gap, content = primary)
                Column(Modifier.weight(secondaryWeight).scrollIf().padding(bottom = bottomPadding), verticalArrangement = gap, content = secondary)
            }
        } else {
            Column(Modifier.fillMaxSize().scrollIf().padding(bottom = bottomPadding), verticalArrangement = gap) {
                primary()
                if (secondaryInPortrait) secondary()
            }
        }
    }
}

/**
 * 운전 중에 막아야 하는 영역(키보드 입력·설정·긴 목록·영상)을 감싼다(pleos-dev.md 2.4장).
 * [blocked]가 참이면 [content] 대신 "주차 후 이용" 안내 한 줄을 보인다. 기본값은 주행 규제 여부.
 * 키보드만 막을 때는 `blocked = LocalUxRestrictions.current.noKeyboard`처럼 플래그를 넘긴다.
 */
@Composable
fun ParkedOnly(
    modifier: Modifier = Modifier,
    blocked: Boolean = LocalUxRestrictions.current.requiresDistractionOptimization,
    message: String = stringResource(R.string.parked_only),
    content: @Composable () -> Unit,
) {
    if (!blocked) {
        content()
        return
    }
    val colors = LocalPleosColors.current
    Text(
        text = message,
        style = LocalPleosTypography.current.bodyLarge,
        color = colors.text_dark_secondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .clip(PleosTheme.shapes.card)
            .background(colors.surface_high)
            .padding(horizontal = PleosTheme.dimens.bannerHorizontalPadding, vertical = PleosTheme.dimens.sectionSpacing),
    )
}

/**
 * 고를 수 있는 카드. 출발안 비교(F2: 지금 / 10분 뒤 / 수유 후), 정차 플랜(F4: 휴게소 / 졸음쉼터)처럼 하나를 고를 때.
 * 묶음은 호출한 쪽에서 `Modifier.selectableGroup()`으로 감싼다.
 * 선택은 테두리 + 체크 아이콘으로 함께 보여 색만으로 구분하지 않는다.
 * [badge]는 제목 옆([ForecastBadge] 등), [detail]은 운전 중 숨긴다.
 */
@Composable
fun SelectableOptionCard(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    badge: @Composable (() -> Unit)? = null,
) {
    val colors = LocalPleosColors.current
    val type = LocalPleosTypography.current
    val ux = LocalUxRestrictions.current
    val shape = PleosTheme.shapes.card
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .pressScale(interaction)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .fillMaxWidth()
            .pleosShadow(PleosElevation.level1, shape)
            .clip(shape)
            .background(colors.cardSurface(pressed))
            .border(width = 4.dp, color = if (selected) colors.button_filled_enabled else Color.Transparent, shape = shape)
            .padding(PleosTheme.dimens.bannerHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.cardPaddingCompact),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.tightSpacing)) {
                Text(
                    text = title,
                    style = type.bodyLargeStrong,
                    color = colors.text_dark_primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                badge?.invoke()
            }
            Text(text = ux.limit(summary), style = type.bodyMedium, color = colors.text_dark_primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null && !ux.requiresDistractionOptimization) {
                Text(text = detail, style = type.labelMedium, color = colors.text_dark_secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        CheckMark(checked = selected, round = true)
    }
}

/** 선택·체크 표시(팀 체크박스 토큰 checkboxSize 32dp, 터치 영역은 감싼 행이 64dp+로 받는다). 켜짐은 진한 바탕 + 흰 체크, 꺼짐은 테두리만. */
@Composable
internal fun CheckMark(checked: Boolean, round: Boolean = false, enabled: Boolean = true) {
    val colors = LocalPleosColors.current
    val shape = if (round) CircleShape else PleosTheme.shapes.checkbox
    val on = if (enabled) colors.button_filled_enabled else colors.button_filled_disabled
    val m = Modifier
        .size(PleosTheme.dimens.checkboxSize)
        .clip(shape)
        .then(
            if (checked) Modifier.background(on)
            else Modifier.border(PleosTheme.dimens.strokeCheck, if (enabled) colors.icon_dark_secondary else colors.icon_dark_quaternary, shape),
        )
    Box(m, contentAlignment = Alignment.Center) {
        if (checked) {
            Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.basic_00, modifier = Modifier.size(PleosTheme.dimens.checkboxSize - PleosTheme.dimens.microSpacing))
        }
    }
}

/** 섹션 제목(카드 묶음 위 한 줄). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LocalPleosTypography.current.bodyMediumStrong,
        color = LocalPleosColors.current.text_dark_secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}
