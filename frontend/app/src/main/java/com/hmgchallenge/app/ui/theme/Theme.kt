package com.hmgchallenge.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Pleos 토큰을 직접 읽을 때: `LocalPleosColors.current.basic_600` */
val LocalPleosColors = staticCompositionLocalOf { PleosColors.Dark }

/** Pleos 타이포(Strong 굵기 등 M3에 없는 스타일 포함): `LocalPleosTypography.current.bodyLargeStrong` */
val LocalPleosTypography = staticCompositionLocalOf { PleosTypography() }

private val DefaultPleosTypography = PleosTypography()
private val MaterialTypography = DefaultPleosTypography.toMaterial()

/**
 * M3 ColorScheme에는 화면에서 실제로 쓰는 역할만 연결한다(매핑과 대비는 [PleosRoles] 표).
 * 연결하지 않은 슬롯은 M3 기본값이며 화면에서 쓰지 않는다.
 * - 배경·면·글자는 문서 System Color(Background, Surface, Text)로 잇는다.
 * - 반전 배너(inverse*), outline, onPrimary, onError는 문서에 대응 토큰이 없어 Grayscale/Static을 쓴다(팀 판단).
 */
private fun PleosColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = button_filled_enabled,
        onPrimary = basic_00,
        background = background_primary,
        onBackground = text_dark_primary,
        surface = surface_basic,
        onSurface = text_dark_primary,
        surfaceVariant = surface_high,
        onSurfaceVariant = text_dark_secondary,
        surfaceContainer = surface_high,
        inverseSurface = basic_700,
        inverseOnSurface = basic_00,
        outline = basic_500,
        error = informative_negative,
        // basic_static_dark_100(#131417): 주간 5.17, 야간 5.54
        onError = basic_static_dark_100,
    )
}

/**
 * 주간/야간 테마. 시스템 야간 모드(Configuration.uiMode night)를 따른다.
 * - Pleos 문서에는 주야간 신호 전달 방식이 없다. Android 표준 uiMode night 기준으로 가정한다(docs/guides/pleos-ui.md 10장).
 * - MainActivity는 `configChanges`에 uiMode가 있어 재생성되지 않는다. Compose의 LocalConfiguration이 바뀌어
 *   [isSystemInDarkTheme]가 다시 계산되므로 화면은 바로 바뀐다(에뮬레이터 `cmd uimode night yes|no`로 확인).
 * - 글꼴은 Asta Sans, 글자 크기는 Pleos 위계([PleosTypography]).
 */
@Composable
fun VehicleTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) PleosColors.Dark else PleosColors.Light
    CompositionLocalProvider(
        LocalPleosColors provides colors,
        LocalPleosTypography provides DefaultPleosTypography,
        LocalPleosDimens provides PleosDimens(),
        LocalPleosShapes provides PleosShapes(),
    ) {
        MaterialTheme(colorScheme = colors.toColorScheme(dark), typography = MaterialTypography, content = content)
    }
}
