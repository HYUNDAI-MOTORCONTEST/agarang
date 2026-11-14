package com.hmgchallenge.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * [PleosMotion]·[PleosElevation] 토큰(PleosTokens.kt)을 Compose에 적용하는 도우미.
 */

/** Pleos easing을 쓴 tween. [animate]가 false(운전 중 장식 애니메이션 등)면 바로 바뀐다. */
fun <T> pleosTween(durationMillis: Int, animate: Boolean = true): AnimationSpec<T> =
    if (animate) tween(durationMillis = durationMillis, easing = PleosMotion.easingStandard) else snap()

/**
 * Pleos 그림자(SHADOW 3단계)를 그린다. clip·background보다 **앞에** 둔다(그림자는 모양 바깥에 그려진다).
 * - Figma blur → Compose radius 환산은 문서에 없어 같은 값으로 넣었다(팀 판단, 캡처로 비교).
 * - 쓰임(팀 판단): 카드 = level1, 떠 있는 패널·한 줄 안내 = level2, 팝업 = level3. 야간도 같은 값.
 */
fun Modifier.pleosShadow(spec: PleosElevation.ShadowSpec, shape: Shape = PleosShapes().card): Modifier {
    val primary = layer(shape, spec.offsetY, spec.blur, spec.alpha)
    val y2 = spec.secondaryOffsetY
    val blur2 = spec.secondaryBlur
    val alpha2 = spec.secondaryAlpha
    return if (y2 != null && blur2 != null && alpha2 != null) primary.layer(shape, y2, blur2, alpha2) else primary
}

private fun Modifier.layer(shape: Shape, y: Dp, blur: Dp, alpha: Float) =
    dropShadow(shape, Shadow(radius = blur, color = Color.Black, offset = DpOffset(0.dp, y), alpha = alpha))
