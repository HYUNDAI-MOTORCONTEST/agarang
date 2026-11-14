package com.hmgchallenge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmgchallenge.app.R
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.ui.theme.LocalPleosColors
import com.hmgchallenge.app.ui.theme.LocalPleosTypography
import com.hmgchallenge.app.ui.theme.PleosColors
import com.hmgchallenge.app.ui.theme.PleosElevation
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.app.ui.theme.pleosShadow

/**
 * 아기 상태 예보 단계(기획: 여유 → 주의 → 임박). 판단 엔진이 정하고 화면은 이 값만 그린다.
 */
enum class ForecastLevel { Relaxed, Caution, Imminent }

/**
 * 단계 색. 모두 짙은 글자(basic_static_dark_100)를 올려 대비 4.5:1 이상(팀 계산, 상대 휘도 기준):
 * 여유 informative_active 약 7.8 · 주의 regulation_yellow 약 11.4 · 임박 informative_negative 약 5.2(주간 값).
 * 색은 보조이고, 단계 이름 글자가 같은 뜻을 전한다(색만으로 알리지 않음).
 */
private fun PleosColors.forecastColor(level: ForecastLevel): Color = when (level) {
    ForecastLevel.Relaxed -> informative_active
    ForecastLevel.Caution -> regulation_yellow
    ForecastLevel.Imminent -> informative_negative
}

@Composable
private fun forecastLabel(level: ForecastLevel): String = stringResource(
    when (level) {
        ForecastLevel.Relaxed -> R.string.forecast_relaxed
        ForecastLevel.Caution -> R.string.forecast_caution
        ForecastLevel.Imminent -> R.string.forecast_imminent
    },
)

/** 예보 단계 배지("주의" 등). 눌리지 않는 표시 요소다. [label]을 주면 단계 이름 대신 쓴다(예: "수유 임박"). */
@Composable
fun ForecastBadge(level: ForecastLevel, modifier: Modifier = Modifier, label: String? = null) {
    val colors = LocalPleosColors.current
    Text(
        text = label ?: forecastLabel(level),
        style = LocalPleosTypography.current.bodyMediumStrong,
        color = colors.basic_static_dark_100,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(PleosTheme.shapes.badge)
            .background(colors.forecastColor(level))
            .padding(horizontal = PleosTheme.dimens.cardPaddingCompact, vertical = 6.dp),
    )
}

/**
 * 주행 중 한 줄 안내(F4 울음 대응·F3 케어 모드). 예: "수유 임박 · 다음 휴게소 약 6분" + [경유지 추가].
 * - 문장은 한 줄(한국어 40자 안팎 권장, pleos-ui.md 7장)이고 LIMIT_STRING_LENGTH면 플랫폼 길이로 자른다.
 * - 핵심 정보라 32sp(titleSmall)로 그린다. 버튼은 1개만(운전 중 조작 단순화).
 * - 왼쪽 막대 색은 [level]이고, 단계 이름은 [ForecastBadge]로 함께 보인다. 내용이 바뀌면 화면 읽기에 알린다.
 * - 그림자 Level 2(떠 있는 패널, 팀 판단).
 */
@Composable
fun GlanceBar(
    message: String,
    modifier: Modifier = Modifier,
    level: ForecastLevel? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalPleosColors.current
    val ux = LocalUxRestrictions.current
    val shape = PleosTheme.shapes.card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = PleosTheme.dimens.actionTileHeight)
            .pleosShadow(PleosElevation.level2, shape)
            .clip(shape)
            .background(colors.cardSurface(pressed = false))
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(12.dp)
                .fillMaxHeight()
                .background(if (level != null) colors.forecastColor(level) else colors.surface_accent),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = PleosTheme.dimens.sectionSpacing, end = PleosTheme.dimens.cardPaddingCompact, top = PleosTheme.dimens.itemSpacing, bottom = PleosTheme.dimens.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.cardPaddingCompact),
        ) {
            if (level != null) ForecastBadge(level)
            Text(
                text = ux.limit(message),
                style = LocalPleosTypography.current.titleSmall,
                color = colors.text_dark_primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                PleosButton(label = actionLabel, onClick = onAction)
            }
        }
    }
}

/**
 * 회고 타임라인 한 줄(F5: 수면·울음·정차·기록). 시각 | 점 | 제목·설명.
 * [detail]은 운전 중 숨긴다. [last]가 아니면 다음 줄로 잇는 세로선을 그린다.
 */
@Composable
fun TimelineRow(
    time: String,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    level: ForecastLevel? = null,
    last: Boolean = false,
) {
    val colors = LocalPleosColors.current
    val type = LocalPleosTypography.current
    val restricted = LocalUxRestrictions.current.requiresDistractionOptimization
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.cardPaddingCompact),
    ) {
        Text(
            text = time,
            style = type.labelMedium,
            color = colors.text_dark_secondary,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = 96.dp)
                .padding(top = PleosTheme.dimens.nanoSpacing),
        )
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (level != null) colors.forecastColor(level) else colors.basic_500),
            )
            if (!last) {
                Box(
                    Modifier
                        .weight(1f)
                        .width(3.dp)
                        .background(colors.basic_300),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (last) 0.dp else PleosTheme.dimens.sectionSpacing),
        ) {
            Text(text = title, style = type.bodyLarge, color = colors.text_dark_primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null && !restricted) {
                Text(text = detail, style = type.labelMedium, color = colors.text_dark_secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 값 한 개를 크게 보이는 요약 칸(예: "다음 수유 40분 뒤", "착석 1시간 20분", "배터리 62%").
 * 값은 핵심 수치라 40sp(titleLarge), 이름은 24sp. 주행 중에도 그대로 보인다.
 */
@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalPleosColors.current
    val type = LocalPleosTypography.current
    Column(
        modifier = modifier
            .clip(PleosTheme.shapes.card)
            .background(colors.surface_high)
            .padding(horizontal = PleosTheme.dimens.bannerHorizontalPadding, vertical = PleosTheme.dimens.cardPaddingCompact)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(PleosTheme.dimens.nanoSpacing),
    ) {
        Text(text = label, style = type.labelMedium, color = colors.text_dark_secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = value, style = type.titleLarge, color = colors.text_dark_primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
