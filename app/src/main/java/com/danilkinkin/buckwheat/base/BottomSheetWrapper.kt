package com.danilkinkin.buckwheat.base

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.SystemBarState
import com.danilkinkin.buckwheat.ui.isNightMode
import com.danilkinkin.buckwheat.util.observeLiveData
import com.danilkinkin.buckwheat.util.setSystemStyle
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

data class BottomSheetScrollState(
    val topPadding: Dp,
)

val LocalBottomSheetScrollState = compositionLocalOf { BottomSheetScrollState(0.dp) }

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BottomSheetWrapper(
    name: String,
    appViewModel: AppViewModel = viewModel(),
    cancelable: Boolean = true,
    state: ModalBottomSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f),
    ),
    content: @Composable (state: ModalBottomSheetState) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    observeLiveData(appViewModel.sheetStates) { sheets ->
        if (sheets.containsKey(name)) {
            state.bindCallback(sheets[name]!!.callback)
            coroutineScope.launch {
                val wasRendered = state.render

                state.show(sheets[name]!!.args)

                // `render` does not change for a sheet that is still rendered but hidden,
                // so the effect below would not start the show animation again
                if (
                    wasRendered
                    && !state.isAnimationRunning
                    && state.currentValue === ModalBottomSheetValue.Hidden
                ) {
                    state.realShow()
                }
            }
        } else if (state.targetValue !== ModalBottomSheetValue.Hidden) {
            coroutineScope.launch { state.hide() }
        }
    }

    DisposableEffect(state.render) {
        if (state.render) coroutineScope.launch { state.realShow() }

        onDispose { }
    }

    DisposableEffect(state.currentValue) {
        if (state.currentValue === ModalBottomSheetValue.Hidden) {
            state.render = false
            appViewModel.closeSheet(name)
        }

        onDispose { }
    }

    // If the opening is interrupted (a tap on the scrim, back) before the sheet reaches
    // Expanded, currentValue never leaves Hidden and the effect above never fires: the sheet
    // stays rendered and registered as open, and later opens are ignored. Clean up whenever
    // the sheet settles hidden after having moved.
    LaunchedEffect(state) {
        var hasMoved = false

        snapshotFlow {
            state.isAnimationRunning
                    || state.currentValue !== ModalBottomSheetValue.Hidden
                    || state.targetValue !== ModalBottomSheetValue.Hidden
        }.collect { moving ->
            if (moving) {
                hasMoved = true
            } else if (hasMoved) {
                hasMoved = false

                if (state.render) {
                    state.render = false
                    appViewModel.closeSheet(name)
                }
            }
        }
    }

    if (!state.render) return

    val localDensity = LocalDensity.current

    val statusBarHeight = LocalWindowInsets.current.calculateTopPadding()
    val isNightModeNow = isNightMode()

    setSystemStyle(
        style = {
            SystemBarState(
                statusBarColor = Color.Transparent,
                statusBarDarkIcons = false,
                navigationBarDarkIcons = !isNightModeNow,
                navigationBarColor = Color.Transparent,
            )
        },
        key = state.targetValue,
        confirmChange = { state.targetValue !== ModalBottomSheetValue.Hidden },
    )

    val statusBarFillProgress = if (statusBarHeight == 0.dp) {
        0F
    } else {
        with(localDensity) {
            max(
                statusBarHeight - state.offset.value.roundToInt().toDp(),
                0.toDp(),
            )
        } / statusBarHeight
    }.coerceIn(0f, 1f)

    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    var predictiveBackProgress by remember {
        mutableFloatStateOf(0f)
    }

    ModalBottomSheetLayout(
        cancelable = cancelable,
        sheetBackgroundColor = MaterialTheme.colorScheme.surface,
        sheetState = state,
        predictiveBackProgress = predictiveBackProgress,
        sheetShape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
            topStart = CornerSize(28.dp * (1F - statusBarFillProgress)),
            topEnd = CornerSize(28.dp * (1F - statusBarFillProgress)),
        ),
        sheetContent = {
            setSystemStyle(
                style = {
                    SystemBarState(
                        statusBarColor = Color.Transparent,
                        statusBarDarkIcons = !isNightModeNow,
                        navigationBarDarkIcons = !isNightModeNow,
                        navigationBarColor = Color.Transparent,
                    )
                },
                key = statusBarFillProgress,
                confirmChange = { statusBarFillProgress > 0.5F },
            )

            val enter = remember { Animatable(0f) }

            LaunchedEffect(Unit) {
                enter.animateTo(
                    1f,
                    spring(dampingRatio = 0.85f, stiffness = 800f),
                )
            }

            Box(
                Modifier.graphicsLayer {
                    alpha = enter.value.coerceIn(0f, 1f)
                    translationY = (1f - enter.value) * 20.dp.toPx()
                }
            ) {
                CompositionLocalProvider(
                    LocalBottomSheetScrollState provides BottomSheetScrollState(
                        topPadding = statusBarHeight * statusBarFillProgress,
                    )
                ) {
                    content(state)
                }
            }

            if (cancelable) {
                Box(
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                ) {
                    Box(
                        Modifier
                            .height(4.dp)
                            .width(30.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .align(Alignment.Center)
                    )
                }
            }
        }
    ) {}

    PredictiveBackHandler(state.isVisible) { progress ->
        try {
            progress.collect { backEvent ->
                predictiveBackProgress = backEvent.progress
            }

            coroutineScope.launch {
                appViewModel.closeSheet(name = name)
            }
        } catch (e: CancellationException) {
            predictiveBackProgress = 0f
        }
    }
}