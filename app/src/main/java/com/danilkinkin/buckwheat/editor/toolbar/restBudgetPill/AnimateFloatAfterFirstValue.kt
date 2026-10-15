package com.danilkinkin.buckwheat.editor.toolbar.restBudgetPill

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Like animateFloatAsState, but jumps straight to the first real value.
 *
 * The budget values load asynchronously after the app starts. Animating from the
 * placeholder made the bar sweep from full and green to its real state on every open,
 * which misreads as money being spent right now.
 */
@Composable
fun animateFloatAfterFirstValue(
    target: Float,
    ready: Boolean,
    animationSpec: AnimationSpec<Float>,
): Float {
    val animatable = remember { Animatable(target) }
    var hasRealValue by remember { mutableStateOf(ready) }

    LaunchedEffect(target, ready) {
        if (!ready || !hasRealValue) {
            animatable.snapTo(target)
            hasRealValue = ready
        } else {
            animatable.animateTo(target, animationSpec)
        }
    }

    // The effect above runs after this frame is composed, so return the target directly
    // until it has snapped; otherwise the first frame still shows the placeholder.
    return if (!ready || !hasRealValue) target else animatable.value
}
