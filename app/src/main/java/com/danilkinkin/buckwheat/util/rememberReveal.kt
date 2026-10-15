package com.danilkinkin.buckwheat.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Goes from 0 to 1 once, when a card first appears: numbers count up and fills grow to their
 * values, so the data reads as computed now. Read `.value` in a draw block where possible,
 * then only drawing repeats while it runs.
 */
@Composable
fun rememberReveal(): State<Float> {
    val reveal = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // let the sheet start opening first
        delay(150)
        // M3 emphasized easing: a gentle start, so the count is visible and not over in the
        // first few frames (emphasized decelerate felt too fast on the phone)
        reveal.animateTo(1f, tween(1600, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
    }

    return reveal.asState()
}

/**
 * [value] scaled by a reveal fraction, with only as many decimals as the final value shows:
 * otherwise kopecks appear while counting and vanish at the end.
 */
fun revealed(value: BigDecimal, fraction: Float): BigDecimal {
    if (fraction >= 1f) return value

    val decimals = value.stripTrailingZeros().scale().coerceAtLeast(0)

    return value
        .multiply(fraction.coerceIn(0f, 1f).toBigDecimal())
        .setScale(decimals, RoundingMode.HALF_EVEN)
}
