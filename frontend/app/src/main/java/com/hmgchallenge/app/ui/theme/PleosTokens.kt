package com.hmgchallenge.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pleos Connect design tokens for spacing, sizing, responsive breakpoints, shapes, motion, and elevation.
 *
 * Guidelines and token origins:
 * - Pleos Connect UI specs (48dp icons, 8px baseline grid, 3-level shadow, INTER cubic-bezier & durations, 792dp toast):
 *   docs/guides/pleos-ui.md, docs/guides/pleos-components.md
 * - Touch targets (64dp min, 72dp primary action, 96dp hero action):
 *   docs/guides/pleos-ui.md §10 (Android for Cars UX-1 fallback)
 */
@Immutable
data class PleosDimens(
    // Spacing & Padding
    val screenPadding: Dp = 24.dp,
    val shellGap: Dp = 16.dp,
    val contentInset: Dp = 8.dp,
    val cardPadding: Dp = 24.dp,
    val cardPaddingCompact: Dp = 20.dp,
    val sectionSpacing: Dp = 24.dp,
    val cardContentSpacing: Dp = 20.dp,
    val itemSpacing: Dp = 16.dp,
    val tightSpacing: Dp = 12.dp,
    val microSpacing: Dp = 8.dp,
    val nanoSpacing: Dp = 4.dp,
    val mainScreenVerticalPadding: Dp = 40.dp,
    val infoCardVerticalPadding: Dp = 28.dp,
    val bannerHorizontalPadding: Dp = 28.dp,
    val bannerVerticalPadding: Dp = 20.dp,
    val buttonHorizontalPadding: Dp = 16.dp,
    val buttonHorizontalPaddingCompact: Dp = 12.dp,
    val buttonVerticalPadding: Dp = 16.dp,
    val buttonVerticalPaddingCompact: Dp = 12.dp,

    // Touch Targets & Component Sizing
    val touchMinSize: Dp = 64.dp,
    val buttonHeight: Dp = 72.dp,
    val heroButtonHeight: Dp = 96.dp,
    val rowMinHeight: Dp = 88.dp,
    val headerBackWidth: Dp = 96.dp,
    val compactActionWidth: Dp = 96.dp,
    val stepperButtonWidth: Dp = 72.dp,
    val quantityMinWidth: Dp = 64.dp,
    val iconSize: Dp = 48.dp,
    val progressSize: Dp = 32.dp,
    val checkboxSize: Dp = 32.dp,
    val tabIndicatorWidth: Dp = 6.dp,
    val tabIndicatorHeight: Dp = 24.dp,

    // Borders & Strokes
    val accentBarWidth: Dp = 6.dp,
    val accentBarHeight: Dp = 28.dp,
    val errorBarWidth: Dp = 8.dp,
    val borderThin: Dp = 1.5.dp,
    val borderMedium: Dp = 2.dp,
    val strokeCheck: Dp = 3.dp,
    val strokeCheckMark: Dp = 4.dp,

    // Overlay / Toast Specs (docs/guides/pleos-components.md §6)
    val toastMaxWidth: Dp = 792.dp,
    val toastTextMaxWidth: Dp = 712.dp,
    val toastMargin: Dp = 24.dp,

    // Responsive Window Breakpoints (width-driven for Pleos 1-window portrait & 2-window landscape)
    val breakpointTwoColumn: Dp = 1_000.dp,
    val breakpointWideCard: Dp = 850.dp,
    val breakpointThreeGrid: Dp = 700.dp,
    val breakpointHorizontalChoices: Dp = 650.dp,
    val breakpointCareWide: Dp = 600.dp,
    val breakpointMinuteGrid: Dp = 540.dp,
    val breakpointTwoGrid: Dp = 360.dp,
    val breakpointQuickRecordTwoCol: Dp = 244.dp,
)

@Immutable
data class PleosShapes(
    val hero: RoundedCornerShape = RoundedCornerShape(28.dp),
    val card: RoundedCornerShape = RoundedCornerShape(24.dp),
    val container: RoundedCornerShape = RoundedCornerShape(20.dp),
    val control: RoundedCornerShape = RoundedCornerShape(16.dp),
    val badge: RoundedCornerShape = RoundedCornerShape(12.dp),
    val checkboxRadius: Dp = 8.dp,
    val checkbox: RoundedCornerShape = RoundedCornerShape(8.dp),
    val pillBar: RoundedCornerShape = RoundedCornerShape(4.dp),
)

/**
 * Pleos motion & interaction tokens (docs/guides/pleos-components.md §4).
 * Keep touch press shrink subtle; avoid decorative transition motion when driving restrictions apply.
 */
object PleosMotion {
    val easingStandard = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    const val short1 = 100
    const val short2 = 150
    const val medium1 = 250
    const val medium2 = 300
    const val long1 = 400
    const val long2 = 450
    const val pressedScale = 0.95f
}

/**
 * Pleos 3-level elevation/shadow specifications (docs/guides/pleos-components.md §4).
 */
object PleosElevation {
    @Immutable
    data class ShadowSpec(
        val offsetY: Dp,
        val blur: Dp,
        val alpha: Float,
        val secondaryOffsetY: Dp? = null,
        val secondaryBlur: Dp? = null,
        val secondaryAlpha: Float? = null,
    )

    val level1 = ShadowSpec(offsetY = 6.dp, blur = 24.dp, alpha = 0.08f)
    val level2 = ShadowSpec(
        offsetY = 8.dp,
        blur = 32.dp,
        alpha = 0.16f,
        secondaryOffsetY = 2.dp,
        secondaryBlur = 2.dp,
        secondaryAlpha = 0.08f,
    )
    val level3 = ShadowSpec(
        offsetY = 20.dp,
        blur = 40.dp,
        alpha = 0.24f,
        secondaryOffsetY = 2.dp,
        secondaryBlur = 4.dp,
        secondaryAlpha = 0.20f,
    )
}

val LocalPleosDimens = staticCompositionLocalOf { PleosDimens() }
val LocalPleosShapes = staticCompositionLocalOf { PleosShapes() }

/**
 * Unified accessor for all Pleos design tokens inside composable functions.
 */
object PleosTheme {
    val colors: PleosColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPleosColors.current

    val typography: PleosTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalPleosTypography.current

    val dimens: PleosDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalPleosDimens.current

    val shapes: PleosShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalPleosShapes.current
}
