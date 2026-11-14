@file:Suppress("PropertyName")

package com.hmgchallenge.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Pleos Connect 색 토큰(주간 Light / 야간 Dark).
 *
 * 출처(모든 섹션 공통): https://document.pleos.ai/docs/connect/guide/docs-design/foundations/colors
 * - 원문 본문에는 hex가 없고 섹션별 표 이미지(PNG 25장)에만 있다. 아래 값은 그 표 이미지에서 읽은 값이다(2026-10-25 확인).
 * - 이름은 문서 토큰명(snake_case)을 그대로 쓴다. 문서 규칙상 실제 토큰에는 `ivi` 접두가 붙는다(design-token 페이지).
 *   예외: Climate 표 토큰은 이름에 섹션 접두가 없어(`button_enabled` 등) 다른 섹션과 헷갈리지 않게 `climate_`를 붙였다.
 *   Driving 표의 `gradient_start/end`도 같은 이유로 `driving_`을 붙였다.
 * - System Color 표는 Basic 토큰을 참조한다(예: `background_primary` = `basic_50`). 참조는 그대로 코드로 옮겼다.
 * - 알파 색은 ARGB(0xAARRGGBB)로 적는다. 5% = 0x0D, 10% = 0x1A, 20% = 0x33, 30% = 0x4D, 40% = 0x66, 50% = 0x80,
 *   84% = 0xD6, 64% = 0xA3, 32% = 0x52(255 × 비율을 반올림).
 *
 * 표 글자와 견본 색이 다를 때: 표에 적힌 토큰 참조·hex 글자를 따른다. 견본 픽셀은 PNG에서 직접 샘플링해 비교했다.
 * 예외는 Alpha 표 하나다(아래 Alpha 절 주석). 읽은 값이 의심스러운 곳에는 `// 불확실:` 주석을 달았다.
 */
@Immutable
class PleosColors private constructor(val isDark: Boolean) {

    private fun hex(light: Long, dark: Long) = Color(if (isDark) dark else light)
    private fun ref(light: Color, dark: Color) = if (isDark) dark else light

    // ── Basic > Grayscale (표: Grayscale) ──
    val basic_00 = hex(0xFFFFFFFF, 0xFF131417)
    val basic_50 = hex(0xFFF7F8FA, 0xFF1D1E21)
    val basic_100 = hex(0xFFEDEEF2, 0xFF313236)
    val basic_200 = hex(0xFFE0E1E6, 0xFF44464E)
    val basic_300 = hex(0xFFC7C9CE, 0xFF686A72)
    val basic_400 = hex(0xFFA4A8AE, 0xFFA4A8AE)
    val basic_500 = hex(0xFF686A72, 0xFFC7C9CE)
    val basic_600 = hex(0xFF44464E, 0xFFE0E1E6)
    val basic_700 = hex(0xFF313236, 0xFFEDEEF2)
    val basic_800 = hex(0xFF1D1E21, 0xFFF7F8FA)
    val basic_900 = hex(0xFF131417, 0xFFFFFFFF)

    // ── Basic > Alpha Color (표: White Alpha = basic_alpha_light_*, Gray Alpha = basic_alpha_dark_*) ──
    // 불확실: 두 표의 hex 글자는 Light/Dark 열이 서로 바뀐 것으로 판단해 바꿔 읽었다.
    //   - 표 글자: White Alpha Light #131417 n% / Dark #FFFFFF n%, Gray Alpha Light #FFFFFF n% / Dark #131417 n%.
    //   - 두 표의 견본 12쌍은 모두 반대 값과 픽셀 단위로 일치한다(예: White Alpha Light 5% 견본 #1F2023 = 흰색 5% over #131417).
    //   - 다른 System 표의 alpha 참조 견본 58개 중 46개가 바꿔 읽은 값과 맞고 10개만 표 글자와 맞는다.
    //     Dropdown 표(Dark 열 배경이 실제 #131417)에서 basic_alpha_dark_100 견본 #2B2C2E = 흰색 10%로 확인된다.
    //   - 표 글자대로면 button_basic_enabled(alpha_dark_50)가 주간 흰 배경 위 흰색 5%, overlay_default가 주간 흰색 20%가 되어
    //     보이지 않는다. Figma design assets로 다시 확인할 것.
    val basic_alpha_light_50 = hex(0x0DFFFFFF, 0x0D131417)
    val basic_alpha_light_100 = hex(0x1AFFFFFF, 0x1A131417)
    val basic_alpha_light_200 = hex(0x33FFFFFF, 0x33131417)
    val basic_alpha_light_300 = hex(0x4DFFFFFF, 0x4D131417)
    val basic_alpha_light_400 = hex(0x66FFFFFF, 0x66131417)
    val basic_alpha_light_500 = hex(0x80FFFFFF, 0x80131417)
    val basic_alpha_dark_50 = hex(0x0D131417, 0x0DFFFFFF)
    val basic_alpha_dark_100 = hex(0x1A131417, 0x1AFFFFFF)
    val basic_alpha_dark_200 = hex(0x33131417, 0x33FFFFFF)
    val basic_alpha_dark_300 = hex(0x4D131417, 0x4DFFFFFF)
    val basic_alpha_dark_400 = hex(0x66131417, 0x66FFFFFF)
    val basic_alpha_dark_500 = hex(0x80131417, 0x80FFFFFF)

    // ── Basic > Static Color (표: Light, Dark). 테마와 무관(Common 열 하나) ──
    val basic_static_light_100 = Color(0xFFFFFFFF)
    val basic_static_light_200 = Color(0xD6FFFFFF)
    val basic_static_light_300 = Color(0xA3FFFFFF)
    val basic_static_light_400 = Color(0x52FFFFFF)
    val basic_static_dark_100 = Color(0xFF131417)
    val basic_static_dark_200 = Color(0xD6131417)
    val basic_static_dark_300 = Color(0xA3131417)
    val basic_static_dark_400 = Color(0x52131417)

    // ── System > Background (표: Background) ──
    // 표 글자를 따랐다. 견본은 행이 한 칸씩 밀려 있다(견본 primary = 글자 popup, secondary = 글자 primary, popup = 글자 secondary).
    // 견본 hex 자체는 Grayscale 값과 정확히 같아 Grayscale 표와의 충돌은 아니다. pleos-ui.md 5장.
    val background_primary = basic_50
    val background_secondary = basic_00
    val background_popup = ref(basic_00, basic_100)

    // ── System > Button (표: Button) ──
    val button_basic_enabled = basic_alpha_dark_50
    val button_basic_pressed = basic_alpha_dark_100
    val button_basic_disabled = basic_alpha_light_200
    val button_switch_enabled = ref(basic_00, basic_alpha_dark_200) // 옛 이름 button-basic-active-enabled
    // 불확실: Dark 견본은 alpha_dark_100 농도(#E7E7E7)로 보인다. 글자(alpha_dark_200)를 따랐다.
    val button_switch_pressed = ref(basic_alpha_light_500, basic_alpha_dark_200) // 옛 이름 button-basic-active-pressed
    val button_switch_disabled = ref(basic_alpha_light_300, basic_alpha_dark_300) // 옛 이름 button-basic-active-disabled
    val button_filled_enabled = basic_600
    val button_filled_pressed = ref(basic_700, basic_500)
    val button_filled_disabled = basic_alpha_dark_50

    // ── System > Controller (표: Controller) ──
    val controller_slider_normal = ref(basic_alpha_dark_200, basic_alpha_dark_100)
    val controller_slider_pressed = ref(basic_alpha_dark_300, basic_alpha_dark_200)
    val controller_slider_knob = ref(basic_00, basic_alpha_dark_300)
    val controller_tab_background = basic_alpha_dark_50
    val controller_stepper_background = ref(basic_00, basic_alpha_light_100)

    // ── System > Climate (표: Climate). 문서 이름에 climate_ 접두를 붙였다(위 설명) ──
    val climate_button_enabled = ref(basic_200, basic_50)
    // 불확실: Light 견본이 basic_200(#E0E1E6)으로 보인다. 글자(basic_00)를 따랐다.
    val climate_button_selected = ref(basic_00, basic_200)
    val climate_button_border_enabled = ref(basic_alpha_dark_200, basic_alpha_dark_100)
    val climate_button_border_disabled = basic_alpha_dark_50
    val climate_toggle_selected = ref(basic_alpha_dark_100, basic_alpha_light_300)
    val climate_toggle_pressed = ref(basic_alpha_dark_200, basic_alpha_light_400)
    val climate_slider_background = ref(basic_00, basic_100)
    val climate_airvent_button_opened = ref(basic_00, basic_600)
    // 불확실: Light 견본은 #131417 40%(표 글자 기준 알파 해석)와 같다. 위 Alpha 절의 해석(흰색 40%)을 따랐다.
    val climate_airvent_button_closed = ref(basic_alpha_light_400, basic_alpha_dark_200)
    val climate_airvent_symbol_opened = basic_static_dark_200
    val climate_airvent_symbol_closed = ref(basic_static_dark_400, basic_static_dark_300)
    val climate_airvent_opened = ref(basic_static_light_300, basic_static_light_400)
    // 불확실: Dark 열 글자는 "alpha_dark100"(밑줄 빠짐)이고 견본은 #131417 10%로 보인다. alpha_dark_100으로 읽었다.
    val climate_airvent_closed = ref(basic_alpha_light_200, basic_alpha_dark_100)

    // ── System > Dropdown (표: Dropdown). Dark 열 글자는 "basic_alpha_dark100/300"(밑줄 빠짐) ──
    val dropdown_normal = ref(basic_00, basic_alpha_dark_100)
    val dropdown_pressed = ref(basic_100, basic_alpha_dark_300)

    // ── System > Dim (표: Dim) ──
    val dim_dark_primary = basic_alpha_dark_50
    val dim_dark_secondary = basic_alpha_dark_100
    val dim_dark_tertiary = basic_alpha_dark_200
    val dim_light_primary = basic_alpha_light_500
    val dim_light_secondary = basic_alpha_light_400
    val dim_light_tertiary = basic_alpha_light_300

    // ── System > Driving (표: Driving). gradient_* 에 driving_ 접두를 붙였다 ──
    val gearshitft_background = ref(basic_900, basic_100) // 문서 표기 그대로(gearshift 오타로 보임)
    val charging_normal = hex(0xFF00DB25, 0xFF00DB25)
    // 불확실: Dark 글자는 #0082FC(파랑)인데 견본은 Light와 같은 #00B975(초록)다. 글자를 따랐다.
    val charging_deep = hex(0xFF00B975, 0xFF0082FC)
    val driving_gradient_end = basic_00
    val driving_gradient_start = hex(0x00FFFFFF, 0x00131417) // 표: "FFFFFF, 0%" / "131417, 0%"

    // ── System > Fields (표: Fields) ──
    val field_background = basic_00
    val field_focused = basic_700 // 견본은 테두리(외곽선) 색으로 그려져 있다

    // ── System > Gleo AI (표: Gleo AI). 옛 이름 ambient_ai_* ──
    val gleo_surface_primary = ref(basic_00, basic_200)
    val gleo_surface_secondary = ref(basic_alpha_dark_50, basic_alpha_light_200)
    val gleo_bubble_primary = hex(0xFF9BA8E2, 0xFF545F93)
    val gleo_bubble_secondary = hex(0xFF8896D3, 0xFF3E4976)
    val gleo_bubble_tertiary = ref(basic_00, basic_100)
    val gleo_bubble_speaker = hex(0xFF3F4B81, 0xFFB6C1F3)

    // ── System > Icon (표: Icon) ──
    val icon_dark_primary = ref(basic_static_dark_100, basic_static_light_100)
    val icon_dark_secondary = ref(basic_static_dark_200, basic_static_light_200)
    val icon_dark_tertiary = ref(basic_static_dark_300, basic_static_light_300)
    val icon_dark_quaternary = ref(basic_static_dark_400, basic_static_light_400)
    // 불확실: 표 아래 4행도 이름이 icon_dark_*로 적혀 있다(위 4행과 중복). 값이 위 4행의 반대라 icon_light_*의 오기로 보고 이름을 붙였다.
    val icon_light_primary = ref(basic_static_light_100, basic_static_dark_100)
    val icon_light_secondary = ref(basic_static_light_200, basic_static_dark_200)
    val icon_light_tertiary = ref(basic_static_light_300, basic_static_dark_300)
    val icon_light_quaternary = ref(basic_static_light_400, basic_static_dark_400)

    // ── System > Informative (표: Informative) ──
    val informative_active = hex(0xFF02C265, 0xFF00E074)
    val informative_positive = hex(0xFF0064FF, 0xFF0082FF)
    val informative_negative = hex(0xFFFE3D16, 0xFFFF4C28)
    val informative_autonomous_driving = hex(0xFF5A46FA, 0xFF6A57FF)

    // ── System > Keyboard > Normal (표: Keyboard 첫 번째 이미지) ──
    val keyboard_normal_primary_normal = ref(basic_00, basic_300)
    val keyboard_normal_primary_pressed = ref(basic_100, basic_200)
    val keyboard_normal_secondary_normal = ref(basic_300, basic_200)
    // 불확실: Dark 견본은 basic_300(#686A72)으로 보인다. 글자(basic_200)를 따랐다.
    val keyboard_normal_secondary_pressed = ref(basic_400, basic_200)
    val keyboard_normal_active_normal = ref(basic_600, basic_300)
    val keyboard_normal_active_pressed = ref(basic_700, basic_200)
    val keyboard_normal_accents_normal = ref(basic_600, basic_500)
    val keyboard_normal_accents_pressed = ref(basic_700, basic_400)
    val keyboard_normal_background = ref(basic_200, basic_100)

    // ── System > Keyboard > Static (표: Keyboard 두 번째 이미지) ──
    val keyboard_static_primary_normal = ref(basic_500, basic_300)
    val keyboard_static_primary_pressed = ref(basic_600, basic_200)
    val keyboard_static_secondary_normal = ref(basic_600, basic_200)
    // 불확실: Dark 견본은 basic_300(#686A72)으로 보인다. 글자(basic_200)를 따랐다.
    val keyboard_static_secondary_pressed = ref(basic_500, basic_200)
    val keyboard_static_active_normal = ref(basic_500, basic_300)
    val keyboard_static_active_pressed = ref(basic_700, basic_200)
    val keyboard_static_accents_normal = ref(basic_300, basic_500)
    val keyboard_static_accents_pressed = basic_400
    // 불확실: Dark 견본은 #131417로 보인다(basic_100 Dark = #313236). 글자를 따랐다.
    val keyboard_static_background = ref(basic_900, basic_100)
    // 불확실: icon_light/icon_dark/text의 견본은 각각 흰색/#131417/흰색이라 글자(basic_400, basic_700)와 다르다. 글자를 따랐다.
    val keyboard_static_icon_light = ref(basic_00, basic_400)
    val keyboard_static_icon_dark = ref(basic_900, basic_400)
    val keyboard_static_icon_disabled = ref(basic_alpha_light_100, basic_alpha_dark_100)
    val keyboard_static_text = ref(basic_700, basic_400)

    // ── System > Media (표: Media) ──
    // 표의 8자리 hex는 표기가 섞여 있다: primary 2개는 RRGGBBAA(#C911E7FF → 불투명 #C911E7),
    // gradient 2개는 AARRGGBB(#00FFFFFF → 투명 흰색)로 읽어야 견본 픽셀과 맞는다.
    val radio_primary = hex(0xFFC911E7, 0xFFC911E7)
    val music_primary = hex(0xFF4781FF, 0xFF4781FF)
    val radio_gradient_start = hex(0x00FFFFFF, 0x001D1E21)
    val radio_gradient_end = hex(0xFFFFFFFF, 0xFF1D1E21)

    // ── System > Overlay (표: Overlay) ──
    val overlay_default = ref(basic_alpha_dark_200, basic_alpha_light_500)

    // ── System > Phone (표: Phone) ──
    // 불확실: 4행 모두 Dark 견본이 Light hex와 같은 색이다. Dark 글자 hex를 따랐다.
    val phone_call_normal = hex(0xFF32B957, 0xFF3BD665)
    val phone_call_pressed = hex(0xFF279E47, 0xFF2DB752)
    val phone_end_normal = hex(0xFFFE3D16, 0xFFFF4C28)
    val phone_end_pressed = hex(0xFFE73612, 0xFFDF4323)

    // ── System > Regulation (표: Regulation) ──
    val regulation_blue = hex(0xFF0064FF, 0xFF0082FF)
    val regulation_green = hex(0xFF00BA13, 0xFF55F165)
    val regulation_yellow = hex(0xFFFFC224, 0xFFFFC224)
    val regulation_orange = hex(0xFFFF8A00, 0xFFFF8A00)
    val regulation_red = hex(0xFFF62E24, 0xFFFF4339)
    val regulation_bluetooth = hex(0xFF0082FC, 0xFF0082FC)

    // ── System > Surface (표: Surface) ──
    // 불확실: surface_basic Dark 견본은 #131417(basic_00)로 보인다. 글자(basic_50 = #1D1E21)를 따랐다.
    val surface_basic = ref(basic_00, basic_50)
    val surface_low = ref(basic_50, basic_00)
    val surface_high = basic_100
    val surface_accent = basic_200

    // ── System > Switch (표: Switch) ──
    val switch_on = informative_active
    val switch_off = ref(basic_alpha_dark_300, basic_alpha_dark_100)
    val switch_disabled = basic_alpha_dark_100
    val switch_knob_on = ref(basic_00, basic_900)
    val switch_knob_off = ref(basic_00, basic_400)
    val switch_knob_disabled = ref(basic_00, basic_alpha_dark_200)

    // ── System > Text (표: Text) ──
    val text_dark_primary = ref(basic_static_dark_100, basic_static_light_100)
    val text_dark_secondary = ref(basic_static_dark_200, basic_static_light_200)
    val text_dark_tertiary = ref(basic_static_dark_300, basic_static_light_300)
    val text_dark_quaternary = ref(basic_static_dark_400, basic_static_light_400)
    // 불확실: 표 아래 4행도 이름이 text_dark_*로 적혀 있다(위 4행과 중복). 값이 위 4행의 반대라 text_light_*의 오기로 보고 이름을 붙였다.
    val text_light_primary = ref(basic_static_light_100, basic_static_dark_100)
    val text_light_secondary = ref(basic_static_light_200, basic_static_dark_200)
    val text_light_tertiary = ref(basic_static_light_300, basic_static_dark_300)
    val text_light_quaternary = ref(basic_static_light_400, basic_static_dark_400)

    companion object {
        val Light = PleosColors(isDark = false)
        val Dark = PleosColors(isDark = true)
    }
}

/**
 * Semantic accent tones mapped to Pleos status/brand tokens for decorative leading bars or borders.
 * Never use these as small-text foreground colors unless contrast exceeds 4.5:1 against the backing surface.
 */
enum class PleosAccentTone {
    NEUTRAL,
    POSITIVE,
    ACTIVE,
    WARNING,
    DANGER,
}

/**
 * Resolved visual styling for interactive Pleos controls across Light/Dark themes and nested card surfaces.
 */
@Immutable
data class PleosButtonStyle(
    val container: Color,
    val content: Color,
    val secondaryContent: Color,
    val border: Color? = null,
    val indicator: Color? = null,
)

/**
 * 화면에서 쓰는 역할 → 토큰 매핑. 토큰은 문서 System Color를 쓰고, 문서에 해당 역할이 없는 곳만 팀 판단(표에 [팀]).
 * 대비는 WCAG 2.x 상대 휘도 공식으로 계산했다(Pleos 기준 4.5:1 이상, docs/guides/pleos-ui.md 5장).
 * 알파 토큰은 아래 배경 위에 합성한 색으로 계산했다.
 *
 * | 역할 | 글자 / 바탕 토큰 | 주간 대비 | 야간 대비 |
 * | --- | --- | --- | --- |
 * | 본문 글자 / 화면 배경 | text_dark_primary / background_primary | 17.33 | 16.67 |
 * | 본문 글자 / 카드 | text_dark_primary / surface_high | 15.89 | 12.80 |
 * | 보조 글자 / 카드 | text_dark_secondary / surface_high | 10.27 | 9.52 |
 * | 버튼 라벨 / 버튼(enabled) [팀: 라벨 basic_00] | basic_00 / button_filled_enabled | 9.41 | 14.11 |
 * | 버튼 라벨 / 버튼(pressed) | basic_00 / button_filled_pressed | 12.80 | 11.12 |
 * | 보조 버튼 라벨 / 보조 버튼(카드 위) [팀] | text_dark_primary / basic_00(주간)·surface_accent(야간) | 19.16 | 9.41 |
 * | 버튼 라벨 / 버튼(disabled, 카드 위) [팀: 라벨] | text_dark_tertiary / button_filled_disabled | 4.98 | 5.60 |
 * | 주행 배너 글자 / 배너 [팀: 반전 배너] | basic_00 / basic_700 | 12.80 | 15.89 |
 * | 오류 표시 위 글자(onError) [팀] | basic_static_dark_100 / informative_negative | 5.17 | 5.54 |
 *
 * 카드(surface_high)와 화면 배경(background_primary)의 대비는 주간 1.09, 야간 1.30이다(면 구분용이라 4.5:1 대상 아님).
 * Informative 색은 글자색으로 쓰지 않는다: 카드 위에서 active 2.03/7.26, positive 4.25/3.44, negative 3.07/3.85(주간/야간)로
 * 4.5:1에 못 미치는 경우가 있다. 오류 카드의 왼쪽 막대처럼 글자가 같은 뜻을 이미 전하는 보조 표시에만 쓴다.
 */
object PleosRoles {
    val PleosColors.isDarkPalette: Boolean
        get() = isDark

    val PleosColors.cardContainer: Color
        get() = surface_high

    val PleosColors.cardBorder: Color
        get() = climate_button_border_disabled

    val PleosColors.heroContainer: Color
        get() = surface_basic

    val PleosColors.heroBorder: Color
        get() = climate_button_border_enabled

    val PleosColors.nestedContainer: Color
        get() = surface_basic

    val PleosColors.nestedOnHeroContainer: Color
        get() = surface_high

    fun PleosColors.accentColor(tone: PleosAccentTone): Color = when (tone) {
        PleosAccentTone.NEUTRAL -> basic_500
        PleosAccentTone.POSITIVE -> informative_positive
        PleosAccentTone.ACTIVE -> informative_active
        PleosAccentTone.WARNING -> regulation_orange
        PleosAccentTone.DANGER -> informative_negative
    }

    /**
     * Resolves distinct container, text, border, and selection-indicator tokens for buttons both on
     * primary screen backgrounds and nested inside [cardContainer] cards.
     */
    fun PleosColors.resolveButtonStyle(
        enabled: Boolean,
        primary: Boolean,
        selected: Boolean?,
        pressed: Boolean,
    ): PleosButtonStyle = when {
        !enabled -> PleosButtonStyle(
            container = button_filled_disabled,
            content = text_dark_tertiary,
            secondaryContent = text_dark_tertiary,
            border = climate_button_border_disabled,
            indicator = null,
        )

        primary || selected == true -> PleosButtonStyle(
            container = if (pressed) button_filled_pressed else button_filled_enabled,
            content = basic_00,
            secondaryContent = basic_100,
            border = if (selected == true) informative_positive else null,
            indicator = if (selected == true) informative_active else null,
        )

        !isDark -> PleosButtonStyle(
            container = if (pressed) surface_accent else button_switch_enabled,
            content = text_dark_primary,
            secondaryContent = text_dark_secondary,
            border = climate_button_border_enabled,
            indicator = null,
        )

        else -> PleosButtonStyle(
            container = if (pressed) basic_300 else surface_accent,
            content = text_dark_primary,
            secondaryContent = text_dark_secondary,
            border = climate_button_border_enabled,
            indicator = null,
        )
    }
}
