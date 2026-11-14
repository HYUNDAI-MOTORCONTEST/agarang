package com.hmgchallenge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.driving.UxRestrictionsMonitor
import com.hmgchallenge.app.ui.components.AdaptivePanes
import com.hmgchallenge.app.ui.components.ForecastBadge
import com.hmgchallenge.app.ui.components.ForecastLevel
import com.hmgchallenge.app.ui.components.GlanceBar
import com.hmgchallenge.app.ui.components.MetricTile
import com.hmgchallenge.app.ui.components.PageNavigation
import com.hmgchallenge.app.ui.components.ParkedOnly
import com.hmgchallenge.app.ui.components.PleosButton
import com.hmgchallenge.app.ui.components.PleosButtonSize
import com.hmgchallenge.app.ui.components.PleosButtonVariant
import com.hmgchallenge.app.ui.components.PleosCard
import com.hmgchallenge.app.ui.components.PleosCheckRow
import com.hmgchallenge.app.ui.components.PleosPopup
import com.hmgchallenge.app.ui.components.PleosStepper
import com.hmgchallenge.app.ui.components.PleosSwitchRow
import com.hmgchallenge.app.ui.components.PleosTabs
import com.hmgchallenge.app.ui.components.PleosTileButton
import com.hmgchallenge.app.ui.components.PleosToastHost
import com.hmgchallenge.app.ui.components.PleosToggleButton
import com.hmgchallenge.app.ui.components.SectionTitle
import com.hmgchallenge.app.ui.components.SelectableOptionCard
import com.hmgchallenge.app.ui.components.TimelineRow
import com.hmgchallenge.app.ui.components.rememberPleosToastState
import com.hmgchallenge.app.ui.theme.VehicleTheme

/**
 * 공용 컴포넌트 견본 화면 — **debug 빌드에만 있다**(src/debug). 제출 APK(release)에는 들어가지 않는다.
 * 실행: `adb shell am start -n com.hmgchallenge.app/.ComponentGalleryActivity`
 * 주행 상태(Virtual Console 기어 D)로 바꾸면 운전 중 축약 동작도 볼 수 있다.
 * 견본 문구는 개발 확인용이라 strings.xml에 넣지 않았다. 실제 화면 문구는 strings.xml에 둔다.
 */
class ComponentGalleryActivity : ComponentActivity() {
    private lateinit var ux: UxRestrictionsMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ux = UxRestrictionsMonitor(this).also { it.start() }
        setContent {
            VehicleTheme {
                val state by ux.state.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalUxRestrictions provides state) {
                    Gallery(onBack = ::finish)
                }
            }
        }
    }

    override fun onDestroy() {
        ux.stop()
        super.onDestroy()
    }
}

@Composable
private fun Gallery(onBack: () -> Unit) {
    val toast = rememberPleosToastState()
    var showPopup by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
                PageNavigation(title = "컴포넌트 견본", subtitle = "debug 전용", onBack = onBack)
                AdaptivePanes(
                    modifier = Modifier.fillMaxSize(),
                    secondaryInPortrait = true,
                    bottomPadding = 40.dp,
                    primary = { PrimarySamples(onToast = toast::show, onPopup = { showPopup = true }) },
                    secondary = { SecondarySamples() },
                )
            }
            PleosToastHost(toast)
        }
    }
    if (showPopup) {
        PleosPopup(
            title = "경유지에 추가할까요?",
            description = "OO휴게소(수유실 있음) · 약 6분 뒤 도착",
            confirmLabel = "추가",
            cancelLabel = "취소",
            onConfirm = { showPopup = false; toast.show("경유지에 추가했어요") },
            onDismiss = { showPopup = false },
        )
    }
}

@Composable
private fun PrimarySamples(onToast: (String) -> Unit, onPopup: () -> Unit) {
    SectionTitle("F4 주행 중 한 줄 안내")
    GlanceBar(message = "수유 임박 · 다음 휴게소 약 6분", level = ForecastLevel.Imminent, actionLabel = "경유지 추가", onAction = onPopup)
    GlanceBar(message = "300m 앞 방지턱", level = ForecastLevel.Relaxed)

    SectionTitle("F1 원탭 기록")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        PleosTileButton("수유", onClick = { onToast("분유 120ml 기록했어요") }, caption = "2시간 30분 전", modifier = Modifier.weight(1f))
        PleosTileButton("기저귀", onClick = { onToast("기저귀 기록했어요") }, caption = "1시간 전", modifier = Modifier.weight(1f))
        PleosTileButton("잠듦", onClick = { onToast("수면 시작을 기록했어요") }, caption = "깨어난 지 1시간", modifier = Modifier.weight(1f))
    }

    SectionTitle("F2 출발안 비교")
    var pick by remember { mutableIntStateOf(2) }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SelectableOptionCard(
            title = "지금 출발", summary = "도착 20분 전 배고플 가능성 높음", detail = "휴게소 1곳 필요",
            selected = pick == 0, onClick = { pick = 0 }, badge = { ForecastBadge(ForecastLevel.Imminent) },
        )
        SelectableOptionCard(
            title = "10분 뒤", summary = "도착 10분 전 배고플 수 있음",
            selected = pick == 1, onClick = { pick = 1 }, badge = { ForecastBadge(ForecastLevel.Caution) },
        )
        SelectableOptionCard(
            title = "수유 후 출발", summary = "도착까지 여유", detail = "출발 40분 늦어짐",
            selected = pick == 2, onClick = { pick = 2 }, badge = { ForecastBadge(ForecastLevel.Relaxed) },
        )
    }

    SectionTitle("버튼")
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        PleosButton("팝업 열기", onClick = onPopup)
        PleosButton("토스트", onClick = { onToast("기록했어요") }, style = PleosButtonVariant.Basic)
        PleosButton("불러오는 중", onClick = {}, loading = true, size = PleosButtonSize.Medium)
        PleosButton("비활성", onClick = {}, enabled = false, size = PleosButtonSize.Medium)
    }
}

@Composable
private fun SecondarySamples() {
    SectionTitle("요약 수치")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        MetricTile("다음 수유", "40분 뒤", Modifier.weight(1f))
        MetricTile("착석", "1시간 20분", Modifier.weight(1f))
    }

    var tab by remember { mutableIntStateOf(0) }
    PleosTabs(options = listOf("오늘", "이번 주", "전체"), selectedIndex = tab, onSelect = { tab = it })

    SectionTitle("F2 준비물")
    PleosCard {
        val items = remember { mutableStateListOf(true, false, false) }
        PleosCheckRow("분유 2회분", items[0], { items[0] = it }, detail = "여정 2시간 기준")
        PleosCheckRow("기저귀 3장", items[1], { items[1] = it })
        PleosCheckRow("물티슈", items[2], { items[2] = it })
    }

    SectionTitle("F3 케어 모드")
    var noise by remember { mutableStateOf(true) }
    var recirc by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        PleosToggleButton("백색소음", noise, { noise = it })
        PleosToggleButton("내기순환", recirc, { recirc = it })
    }
    var ml by remember { mutableIntStateOf(120) }
    PleosStepper(value = ml, onValueChange = { ml = it }, range = 40..240, step = 20, format = { "${it}ml" })
    var voice by remember { mutableStateOf(true) }
    PleosSwitchRow("아기가 자면 음성 안내 끄기", voice, { voice = it }, detail = "화면으로만 알려요")

    SectionTitle("F5 회고")
    PleosCard {
        TimelineRow("10:05", "출발", detail = "수유 후 출발")
        TimelineRow("10:40", "잠듦", level = ForecastLevel.Relaxed)
        TimelineRow("11:52", "울음 · 휴게소 정차", detail = "수유 120ml", level = ForecastLevel.Imminent)
        TimelineRow("12:30", "도착", last = true)
    }

    SectionTitle("운전 중 막는 영역")
    ParkedOnly {
        PleosButton("아기 정보 편집", onClick = {}, style = PleosButtonVariant.Basic, modifier = Modifier.fillMaxWidth())
    }
}
