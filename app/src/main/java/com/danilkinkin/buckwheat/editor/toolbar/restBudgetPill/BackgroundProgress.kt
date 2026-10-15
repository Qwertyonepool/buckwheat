package com.danilkinkin.buckwheat.editor.toolbar.restBudgetPill

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.base.WavyShape
import com.danilkinkin.buckwheat.util.HarmonizedColorPalette
import com.danilkinkin.buckwheat.util.clamp
import kotlin.math.ceil


@Composable
fun BackgroundProgress(
    harmonizedColor: HarmonizedColorPalette,
    restBudgetPillViewModel: RestBudgetPillViewModel = hiltViewModel(),
) {
    val percentWithNewSpent by restBudgetPillViewModel.percentWithNewSpent.observeAsState(1f)
    val percentWithoutNewSpent by restBudgetPillViewModel.percentWithoutNewSpent.observeAsState(1f)
    val budgetState by restBudgetPillViewModel.state.observeAsState(DaileBudgetState.NOT_SET)
    val ready = budgetState != DaileBudgetState.NOT_SET

    val percentWithoutNewSpentAnimated = animateFloatAfterFirstValue(
        target = percentWithoutNewSpent,
        ready = ready,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 90f),
    )
    val percentWithNewSpentAnimated = animateFloatAfterFirstValue(
        target = percentWithNewSpent,
        ready = ready,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 90f),
    )

    // Move the wavy edge for a few seconds after the bar changes, then let it rest. It used to
    // loop forever, which redrew the screen continuously even while the app sat idle.
    val shift = remember { Animatable(0f) }
    var lastReadyValues by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    LaunchedEffect(percentWithNewSpent, percentWithoutNewSpent, ready) {
        if (!ready) return@LaunchedEffect

        val values = percentWithNewSpent to percentWithoutNewSpent
        val previous = lastReadyValues

        lastReadyValues = values

        // the first real values arrive at start: nothing changed for the user yet
        if (previous == null || previous == values) return@LaunchedEffect

        shift.animateTo(shift.value + 1f, tween(3200, easing = LinearOutSlowInEasing))
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = percentWithNewSpent != percentWithoutNewSpent,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .drawBehind {
                        drawPath(
                            path = Path().apply {
                                val halfPeriod = 30.dp.toPx() / 2
                                val amplitude = (percentWithNewSpentAnimated.clamp(0.96f, 1f) * 2.dp).toPx()

                                moveTo(
                                    size.width - amplitude,
                                    -halfPeriod * 2.5f + halfPeriod * 2 * shift.value
                                )
                                repeat(ceil(size.height / halfPeriod + 3).toInt()) { i ->
                                    relativeQuadraticBezierTo(
                                        dx1 = 2 * amplitude * (if (i % 2 == 0) 1 else -1),
                                        dy1 = halfPeriod / 2,
                                        dx2 = 0f,
                                        dy2 = halfPeriod,
                                    )
                                }
                            },
                            color = harmonizedColor.main.copy(alpha = 0.3f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(
                                        15f,
                                        15f
                                    )
                                )
                            )
                        )
                    }
                    .fillMaxHeight()
                    .fillMaxWidth(percentWithoutNewSpentAnimated.coerceIn(0f, 1f)),
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .background(
                    harmonizedColor.main.copy(alpha = 0.7f),
                    shape = WavyShape(
                        period = 30.dp,
                        amplitude = percentWithNewSpentAnimated.clamp(0.96f, 1f) * 2.dp,
                        shift = shift.value,
                    ),
                )
                .fillMaxHeight()
                .fillMaxWidth(percentWithNewSpentAnimated.coerceIn(0f, 1f)),
        )
    }
}


