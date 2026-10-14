package com.danilkinkin.buckwheat.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Positions (in root coordinates) shared between the amount field and the top budget bar,
 * so a drop can flow from the first to the second when a spend is committed.
 */
class SpendDropState {
    var amountAnchor by mutableStateOf<Offset?>(null)
    var barTarget by mutableStateOf<Offset?>(null)
    var color by mutableStateOf(Color.Unspecified)
}

val LocalSpendDrop = compositionLocalOf { SpendDropState() }

private const val DROP_DURATION_MS = 480

/** Purely visual, never intercepts touches and never delays committing a spend. */
@Composable
fun SpendDropOverlay(
    state: SpendDropState,
    dropKey: Int,
    rootOffset: Offset,
) {
    val progress = remember { Animatable(0f) }
    var active by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(Offset.Zero) }
    var to by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(dropKey) {
        if (dropKey == 0) return@LaunchedEffect

        val start = state.amountAnchor ?: return@LaunchedEffect
        val end = state.barTarget ?: return@LaunchedEffect

        from = start
        to = end
        active = true
        progress.snapTo(0f)
        progress.animateTo(1f, tween(DROP_DURATION_MS, easing = FastOutSlowInEasing))
        active = false
    }

    if (!active) return

    val color = if (state.color == Color.Unspecified) Color.White else state.color

    Canvas(Modifier.fillMaxSize()) {
        val control = Offset(maxOf(from.x, to.x) + 48.dp.toPx(), (from.y + to.y) / 2f)

        // head plus a short fading trail so the motion reads as a drop travelling
        for (k in 0..3) {
            val t = (progress.value - 0.07f * k).coerceIn(0f, 1f)

            if (t <= 0f && k > 0) continue

            val inv = 1f - t
            val point = Offset(
                inv * inv * from.x + 2f * inv * t * control.x + t * t * to.x,
                inv * inv * from.y + 2f * inv * t * control.y + t * t * to.y,
            ) - rootOffset
            val scale = 1f - 0.18f * k

            drawCircle(
                color = color.copy(alpha = (1f - t * 0.3f) * (1f - 0.27f * k)),
                radius = (18.dp.toPx() * (1f - t) + 7.dp.toPx() * t) * scale,
                center = point,
            )
        }
    }
}
