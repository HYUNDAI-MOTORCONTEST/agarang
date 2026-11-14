package com.hmgchallenge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.ui.theme.LocalPleosColors
import com.hmgchallenge.app.ui.theme.LocalPleosTypography
import com.hmgchallenge.app.ui.theme.PleosElevation
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.app.ui.theme.pleosShadow
import kotlinx.coroutines.delay

/**
 * 앱 안 토스트 상태. `val toast = rememberPleosToastState()` → `toast.show("분유 120ml 기록했어요")`.
 * 같은 메시지를 다시 보내도 표시 시간이 처음부터 다시 잰다.
 */
@Stable
class PleosToastState {
    var message by mutableStateOf<String?>(null)
        private set
    internal var serial by mutableStateOf(0)
        private set

    fun show(text: String) {
        message = text
        serial++
    }

    fun dismiss() {
        message = null
    }
}

@Composable
fun rememberPleosToastState(): PleosToastState = remember { PleosToastState() }

/**
 * Toast(TOAST, TOASTP). 화면 아래 가운데에 잠깐 뜬다. 부모 Box 안에 둔다(`BoxScope`).
 * - 수치[문서]: 좌우·아래 여백 24dp, 전체 최대 너비 792dp, 글자 최대 너비 712dp.
 * - [문서에 없음] 표시 시간·색·줄 수 → 팀 판단: 3.5초, 2줄, 바탕 basic_700·글자 basic_00(inverseSurface와 같음).
 * - 등장·퇴장 애니메이션은 쓰지 않는다(운전 중 움직임 금지와 같은 기준).
 */
@Composable
fun BoxScope.PleosToastHost(state: PleosToastState, modifier: Modifier = Modifier, durationMillis: Long = 3_500) {
    val message = state.message ?: return
    val ux = LocalUxRestrictions.current
    LaunchedEffect(state.serial) {
        delay(durationMillis)
        state.dismiss()
    }
    val colors = LocalPleosColors.current
    val shape = PleosTheme.shapes.card
    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(start = PleosTheme.dimens.toastMargin, end = PleosTheme.dimens.toastMargin, bottom = PleosTheme.dimens.toastMargin)
            .widthIn(max = PleosTheme.dimens.toastMaxWidth)
            .pleosShadow(PleosElevation.level2, shape)
            .clip(shape)
            .background(colors.basic_700)
            .padding(horizontal = 40.dp, vertical = PleosTheme.dimens.sectionSpacing)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ux.limit(message),
            style = LocalPleosTypography.current.bodyMedium,
            color = colors.basic_00,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = PleosTheme.dimens.toastTextMaxWidth),
        )
    }
}

/**
 * System popup(POPUP). 확인이 꼭 필요한 순간에만 쓴다(예: 경유지 추가 확인, 기록 삭제).
 * - 바탕 background_popup + 그림자 Level 3, 제목 1줄·설명 3줄 이하.
 * - 버튼 2개면 가로(Horizontal), [verticalButtons]면 세로. 확인 버튼이 오른쪽(가로)·위(세로).
 * - 시스템 Back과 바깥 터치는 [onDismiss]로 간다(팝업이 Back을 먼저 받는다, pleos-platform.md 0장).
 */
@Composable
fun PleosPopup(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    cancelLabel: String? = null,
    verticalButtons: Boolean = false,
    body: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPleosColors.current
    val type = LocalPleosTypography.current
    val ux = LocalUxRestrictions.current
    val shape = PleosTheme.shapes.card
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(PleosTheme.dimens.sectionSpacing)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .pleosShadow(PleosElevation.level3, shape)
                .clip(shape)
                .background(colors.background_popup)
                .padding(40.dp)
                .semantics { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(PleosTheme.dimens.itemSpacing),
        ) {
            Text(text = title, style = type.titleSmall, color = colors.text_dark_primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (description != null) {
                Text(text = ux.limit(description), style = type.bodyMedium, color = colors.text_dark_secondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            body?.invoke()
            val confirm = @Composable { m: Modifier -> PleosButton(label = confirmLabel, onClick = onConfirm, modifier = m) }
            val cancel = cancelLabel?.let {
                @Composable { m: Modifier -> PleosButton(label = it, onClick = onDismiss, modifier = m, style = PleosButtonVariant.Basic) }
            }
            Box(Modifier.padding(top = PleosTheme.dimens.microSpacing)) {
                if (verticalButtons || cancel == null) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosTheme.dimens.itemSpacing)) {
                        confirm(Modifier.fillMaxWidth())
                        cancel?.invoke(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosTheme.dimens.sectionSpacing)) {
                        cancel(Modifier.weight(1f))
                        confirm(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
