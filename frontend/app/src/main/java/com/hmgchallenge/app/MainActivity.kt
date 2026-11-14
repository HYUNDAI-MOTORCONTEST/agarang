package com.hmgchallenge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import com.hmgchallenge.app.departure.DepartureApp
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.driving.UxRestrictionsState
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.app.ui.theme.VehicleTheme
import com.hmgchallenge.shared.HealthResponse
import com.hmgchallenge.app.navigation.NavigationStatusCard
import com.hmgchallenge.app.facility.FacilityStatusCard
import com.hmgchallenge.app.facility.FacilityUiState
import com.hmgchallenge.shared.facility.LastRestAreaAlert
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.journey.JourneySnapshot
import com.hmgchallenge.shared.journey.StopRequestState

class MainActivity : ComponentActivity() {
    private val mainViewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }

    override fun onStart() {
        super.onStart()
        mainViewModel.startNavigation()
    }

    override fun onStop() {
        mainViewModel.stopNavigation()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pleos Back 정책(docs/guides/pleos-platform.md 9장): 최상위 화면에서 Back이 오면 앱을 끝내지 않고 숨긴다.
        // CareScreen의 프로필·기록 상세 BackHandler가 먼저 받고, 최상위에서만 이 콜백으로 숨긴다.
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        setContent {
            VehicleTheme {
                val vm = mainViewModel
                val state by vm.state.collectAsStateWithLifecycle()
                val ux by vm.uxRestrictions.collectAsStateWithLifecycle()
                val departure by vm.departure.state.collectAsStateWithLifecycle()
                val journey by vm.navigation.snapshots.collectAsStateWithLifecycle()
                val navigationConnection by vm.navigation.connection.collectAsStateWithLifecycle()
                val stopRequest by vm.navigation.stopRequest.collectAsStateWithLifecycle()
                val facilities by vm.facilities.collectAsStateWithLifecycle()
                val facilityAlert by vm.facilityAlert.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalUxRestrictions provides ux) {
                    DepartureApp(
                        state = departure,
                        actions = vm.departure,
                        onReloadCare = vm::reloadCareRecords,
                        onNavigateStop = vm::prepareStopNavigation,
                        stopRequest = stopRequest,
                        onConfirmStop = vm::confirmStopNavigation,
                        onCancelStop = vm::cancelStopNavigation,
                        connections = {
                            MainScreen(
                                state = state, onRefreshHealth = vm::refreshHealth, onRefreshBattery = vm::refreshBattery,
                                journey = journey, navigationConnection = navigationConnection, stopRequest = stopRequest,
                                onRefreshNavigation = vm::refreshNavigation, onConfirmStop = vm::confirmStopNavigation,
                                onCancelStop = vm::cancelStopNavigation,
                                facilities = facilities, facilityAlert = facilityAlert,
                                onRefreshFacilities = vm::refreshFacilities, onToggleFacilityDemo = vm::toggleFacilityDemo,
                                onDismissFacilityAlert = vm::dismissFacilityAlert, onSelectFacility = vm::prepareStopNavigation,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * 운전 중 제한 처리 (근거: docs/guides/pleos-dev.md 2.2·2.4·3장, docs/guides/pleos-ui.md 1·10장)
 * - 주행 규제 중(`isRequiresDistractionOptimization`)이면 상세 정보(출처·버전·시각)를 숨기고 핵심 값만 보인다
 *   ("한눈에 보기", 핵심 정보만). 로딩 스피너 같은 움직이는 그래픽은 정적 텍스트로 바꾼다(동영상·애니메이션 금지).
 * - LIMIT_STRING_LENGTH가 오면 플랫폼이 준 길이로 자르고, 메시지는 2줄 이하로 줄인다.
 * - 버튼(다시 확인·다시 읽기)은 필수 조작이라 그대로 둔다.
 * - 수동 스크롤은 막지 않는다: 문서가 금지한 것은 "자동 스크롤"이고, 작은 window에서 스크롤을 막으면
 *   필수 버튼에 닿지 못할 수 있다. 대신 제한 중에는 내용을 줄여 스크롤이 거의 필요 없게 한다.
 * - 이 연결 화면에는 영상·키보드 입력이 없다. 프로필·기록 설정은 CareScreen에서 주행 중 차단한다.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    onRefreshHealth: () -> Unit,
    onRefreshBattery: () -> Unit,
    journey: DataSnapshot<JourneySnapshot>,
    navigationConnection: DataStatus,
    stopRequest: StopRequestState,
    onRefreshNavigation: () -> Unit,
    onConfirmStop: (String) -> Unit,
    onCancelStop: () -> Unit,
    facilities: FacilityUiState,
    facilityAlert: LastRestAreaAlert?,
    onRefreshFacilities: () -> Unit,
    onToggleFacilityDemo: () -> Unit,
    onDismissFacilityAlert: () -> Unit,
    onSelectFacility: (StopCandidate) -> Unit,
) {
    val type = PleosTheme.typography
    val dimens = PleosTheme.dimens
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding, vertical = dimens.mainScreenVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = type.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DrivingStatusBanner()
            NavigationStatusCard(journey, navigationConnection, stopRequest, onRefreshNavigation, onConfirmStop, onCancelStop)
            FacilityStatusCard(facilities, facilityAlert, onRefreshFacilities, onToggleFacilityDemo, onDismissFacilityAlert, onSelectFacility)
            // 내장 데이터 빌드(release)는 서버를 쓰지 않으므로 서버 상태 카드를 보이지 않는다.
            if (!BuildConfig.BUNDLED_FACILITY_DATA) InfoCard(
                title = stringResource(R.string.health_title),
                detail = stringResource(R.string.health_source, HealthResponse.PATH),
                state = state.health,
                summary = { it.status },
                fullText = { "${it.status} · ${it.service} v${it.version}\n${it.timestamp}" },
                actionLabel = stringResource(R.string.action_recheck),
                onAction = onRefreshHealth,
            )
            val batteryText = @Composable { p: Int -> stringResource(R.string.battery_value, p) }
            InfoCard(
                title = stringResource(R.string.battery_title),
                detail = stringResource(R.string.battery_source),
                state = state.battery,
                summary = { batteryText(it) },
                fullText = { batteryText(it) },
                actionLabel = stringResource(R.string.action_reread),
                onAction = onRefreshBattery,
                valueIsKeyMetric = true,
            )
        }
    }
}

/** 현재 주차/주행 제한 상태를 한 줄 제목 + 한 줄 설명으로 보인다. 주행 중에는 색을 반전해 바로 구분되게 한다. */
@Composable
private fun DrivingStatusBanner() {
    val ux = LocalUxRestrictions.current
    val type = PleosTheme.typography
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val scheme = MaterialTheme.colorScheme
    val restricted = ux.requiresDistractionOptimization
    val (title, detail) = when (ux.source) {
        UxRestrictionsState.Source.CONNECTING -> stringResource(R.string.drive_connecting) to stringResource(R.string.drive_unknown_detail)
        UxRestrictionsState.Source.UNAVAILABLE -> stringResource(R.string.drive_unavailable) to stringResource(R.string.drive_unknown_detail)
        UxRestrictionsState.Source.CAR_SERVICE ->
            if (restricted) stringResource(R.string.drive_restricted) to restrictionDetail(ux)
            else stringResource(R.string.drive_parked) to stringResource(R.string.drive_parked_detail)
    }
    // 반전 배너: inverseSurface(basic_700) / inverseOnSurface(basic_00) 대비 주간 12.80, 야간 15.89
    val container = if (restricted) scheme.inverseSurface else scheme.surfaceVariant
    val titleColor = if (restricted) scheme.inverseOnSurface else scheme.onSurface
    val detailColor = if (restricted) scheme.inverseOnSurface else scheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.container)
            .background(container)
            .padding(horizontal = dimens.bannerHorizontalPadding, vertical = dimens.bannerVerticalPadding),
    ) {
        Text(text = title, style = type.bodyLargeStrong, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = detail, style = type.labelMedium, color = detailColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun restrictionDetail(ux: UxRestrictionsState): String {
    val names = buildList {
        if (ux.noVideo) add(stringResource(R.string.flag_video))
        if (ux.noSetup) add(stringResource(R.string.flag_setup))
        if (ux.noKeyboard) add(stringResource(R.string.flag_keyboard))
        if (ux.limitStringLength || ux.limitContent) add(stringResource(R.string.flag_long_text))
    }
    val detail = stringResource(R.string.drive_restricted_detail)
    return if (names.isEmpty()) detail else stringResource(R.string.drive_flags, names.joinToString("·")) + " · " + detail
}

@Composable
private fun <T> InfoCard(
    title: String,
    detail: String,
    state: LoadState<T>,
    summary: @Composable (T) -> String,
    fullText: @Composable (T) -> String,
    actionLabel: String,
    onAction: () -> Unit,
    valueIsKeyMetric: Boolean = false,
) {
    val ux = LocalUxRestrictions.current
    val restricted = ux.requiresDistractionOptimization
    val type = PleosTheme.typography
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val colors = PleosTheme.colors
    val scheme = MaterialTheme.colorScheme
    val isError = state is LoadState.Error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shapes.card)
            .background(scheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 오류 표시 막대: 글자("오류: …")가 같은 뜻을 전하는 보조 표시라 Informative 색을 쓴다(PleosRoles 주석).
        Box(
            Modifier
                .width(dimens.errorBarWidth)
                .fillMaxHeight()
                .background(if (isError) colors.informative_negative else scheme.surfaceVariant),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = dimens.cardPaddingCompact, top = dimens.infoCardVerticalPadding, bottom = dimens.infoCardVerticalPadding),
        ) {
            Text(text = title, style = type.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!restricted) {
                Text(text = detail, style = type.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val valueStyle = if (valueIsKeyMetric) type.titleLarge else type.bodyLarge
            when (state) {
                LoadState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!restricted) {
                        // 주행 규제 중에는 회전 애니메이션을 그리지 않는다(정적 텍스트만).
                        CircularProgressIndicator(modifier = Modifier.size(dimens.progressSize), color = scheme.onSurface, strokeWidth = dimens.strokeCheckMark)
                        Box(Modifier.width(dimens.itemSpacing))
                    }
                    Text(text = stringResource(R.string.loading), style = type.bodyLarge, color = scheme.onSurface, maxLines = 1)
                }
                is LoadState.Success -> Text(
                    text = ux.limit(if (restricted) summary(state.value) else fullText(state.value)),
                    style = valueStyle,
                    color = scheme.onSurface,
                    maxLines = if (restricted) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                is LoadState.Error -> Text(
                    text = ux.limit(stringResource(R.string.error_prefix) + ": " + state.message),
                    style = type.bodyMedium,
                    color = scheme.onSurface,
                    maxLines = if (restricted) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.width(dimens.sectionSpacing))
        PleosFilledButton(
            label = actionLabel,
            onClick = onAction,
            enabled = state !is LoadState.Loading, // BUTTON 문서: Loading 상태는 조작 불가
            modifier = Modifier.padding(end = dimens.bannerHorizontalPadding),
        )
    }
}

/**
 * Pleos Button(filled). 색: button_filled_enabled / button_filled_pressed 토큰(PleosColors).
 * - 누르는 즉시 pressed 색으로 바뀌어 0.25초 응답 기준을 지킨다(pleos-ui.md 1장). 물결(ripple) 애니메이션은 쓰지 않는다.
 * - 크기: 최소 높이 72dp(주요 버튼), 최소 너비 64dp — Pleos 수치 없음, Google car-app-quality UX-1(pleos-ui.md 10장).
 * - disabled: 바탕은 문서 토큰 button_filled_disabled(alpha_dark_50). 라벨은 문서에 없어 text_dark_tertiary를 쓴다(팀 판단,
 *   카드 위 대비 주간 4.98, 야간 5.60 — PleosRoles 표).
 */
@Composable
private fun PleosFilledButton(label: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = PleosTheme.colors
    val dimens = PleosTheme.dimens
    val shapes = PleosTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container = when {
        !enabled -> colors.button_filled_disabled
        pressed -> colors.button_filled_pressed
        else -> colors.button_filled_enabled
    }
    Box(
        modifier = modifier
            .sizeIn(minWidth = dimens.touchMinSize, minHeight = dimens.buttonHeight)
            .clip(shapes.control)
            .background(container)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.bannerHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PleosTheme.typography.labelMedium,
            color = if (enabled) colors.basic_00 else colors.text_dark_tertiary,
            maxLines = 1,
        )
    }
}
