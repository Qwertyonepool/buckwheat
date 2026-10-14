package com.danilkinkin.buckwheat.editor

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.util.*
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal

class FocusController {
    var onFocus: MutableState<(() -> Unit)?> = mutableStateOf(null)
    var onBlur: MutableState<(() -> Unit)?> = mutableStateOf(null)

    fun focus() {
        onFocus.value?.let { it() }
    }

    fun blur() {
        onBlur.value?.let { it() }
    }
}

@Composable
fun CurrentSpendEditor(
    modifier: Modifier = Modifier,
    focusController: FocusController = remember { FocusController() },
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
    editorViewModel: EditorViewModel = hiltViewModel(),
) {
    val localDensity = LocalDensity.current
    val focusManager = LocalFocusManager.current

    val currency by spendsViewModel.currency.observeAsState(ExtendCurrency.none())
    val mode by editorViewModel.mode.observeAsState(EditMode.ADD)

    var spentValue by remember { mutableStateOf("0") }
    var stage by remember { mutableStateOf(AnimState.IDLE) }
    var currState by remember { mutableStateOf<AnimState?>(null) }
    var requestFocus by remember { mutableStateOf(false) }
    var hide by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val rawValue by editorViewModel.rawSpentValue.observeAsState("")
    val amountPop = remember { Animatable(1f) }

    LaunchedEffect(rawValue) {
        if (rawValue == "" || rawValue == "0") {
            amountPop.snapTo(1f)
            return@LaunchedEffect
        }
        amountPop.snapTo(1.07f)
        amountPop.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        )
    }

    fun calculateValues() {
        spentValue = editorViewModel.rawSpentValue.value!!
        requestFocus = true
    }

    observeLiveData(appViewModel.sheetStates) {
        if (it.isEmpty()) {
            requestFocus = true
            hide = false
        } else {
            hide = editorViewModel.rawSpentValue.value!! == ""
        }
    }

    observeLiveData(spendsViewModel.dailyBudget) {
        calculateValues()
    }

    observeLiveData(spendsViewModel.spentFromDailyBudget) {
        calculateValues()
    }

    observeLiveData(editorViewModel.stage) {
        when (it) {
            EditStage.IDLE -> {
                if (currState === AnimState.EDITING) {
                    stage = AnimState.RESET
                }
                calculateValues()
            }
            EditStage.CREATING_SPENT -> {
                calculateValues()

                stage = AnimState.EDITING
            }
            EditStage.EDIT_SPENT -> {
                calculateValues()

                stage = AnimState.EDITING
            }
            EditStage.COMMITTING_SPENT -> {
                stage = AnimState.COMMIT
            }
        }

        currState = stage
    }

    LaunchedEffect(focusController) {
        focusController.onFocus.value = {
            if (currState != AnimState.EDITING) {
                requestFocus = true
                calculateValues()

                editorViewModel.startCreatingSpent()
                editorViewModel.modifyEditingSpent(BigDecimal.ZERO)
            }
        }
        focusController.onBlur.value = {
            focusManager.clearFocus()
        }
    }

    val spendDrop = LocalSpendDrop.current

    BoxWithConstraints(
        modifier.onGloballyPositioned {
            val origin = it.positionInRoot()

            spendDrop.amountAnchor = Offset(
                origin.x + it.size.width - with(localDensity) { 72.dp.toPx() },
                origin.y + it.size.height / 2f,
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (!hide) {
                EditableTextWithLabel(
                    modifier = Modifier.graphicsLayer {
                        scaleX = amountPop.value
                        scaleY = amountPop.value
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    },
                    value = spentValue,
                    onChangeValue = {
                        val fixed = fixedNumberString(it)
                        val converted = tryConvertStringToNumber(fixed)

                        editorViewModel.rawSpentValue.value = fixed
                        editorViewModel.modifyEditingSpent(converted.join().toBigDecimal())

                        if (fixed === "") {
                            if (mode === EditMode.ADD) runBlocking {
                                editorViewModel.resetEditingSpent()
                            }
                        }
                    },
                    currency = currency,
                    focusRequester = focusRequester,
                )

                DisposableEffect(requestFocus) {
                    focusRequester.requestFocus()
                    requestFocus = false

                    onDispose {}
                }
            }
        }
    }
}
