package com.danilkinkin.buckwheat.editor.toolbar.restBudgetPill

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import com.danilkinkin.buckwheat.editor.LocalSpendFlight
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.BigIconButton
import com.danilkinkin.buckwheat.base.balloon.BalloonScope
import com.danilkinkin.buckwheat.base.balloon.rememberBalloonState
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.PathState
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.di.TUTORIAL_STAGE
import com.danilkinkin.buckwheat.di.TUTORS
import com.danilkinkin.buckwheat.editor.EditorViewModel
import com.danilkinkin.buckwheat.ui.*
import com.danilkinkin.buckwheat.util.*
import com.danilkinkin.buckwheat.wallet.WALLET_SHEET
import kotlinx.coroutines.delay
import java.math.BigDecimal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.RestBudgetPill(
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
    editorViewModel: EditorViewModel = hiltViewModel(),
    restBudgetPillViewModel: RestBudgetPillViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current

    val hideOverspendingWarn by spendsViewModel.hideOverspendingWarn.observeAsState(false)
    val currency by spendsViewModel.currency.observeAsState(ExtendCurrency.none())
    val budgetState by restBudgetPillViewModel.state.observeAsState(DaileBudgetState.NOT_SET)
    val editorIsNotVisible by appViewModel.topSheetDown
    val percentWithNewSpent by restBudgetPillViewModel.percentWithNewSpent.observeAsState(1f)
    val tutorial by appViewModel.getTutorialStage(TUTORS.OPEN_WALLET).observeAsState(TUTORIAL_STAGE.NONE)

    // Right after a commit the editor resets and refocuses before the saved spend reaches
    // spentFromDailyBudget. Recalculating in between shows the budget without the spend for a
    // moment, and the animated bar visibly jumps back and forth. Hold the shown result until
    // the saved spend arrives, unless a new amount is being typed.
    var awaitingSavedSpend by remember { mutableStateOf(false) }
    var spentAtCommit by remember { mutableStateOf<BigDecimal?>(null) }

    fun recalculate() = restBudgetPillViewModel.calculateValues(context, editorViewModel.currentSpent)
    fun holdResult() = awaitingSavedSpend && editorViewModel.currentSpent.signum() == 0

    observeLiveData(spendsViewModel.dailyBudget) {
        if (!holdResult()) recalculate()
    }

    observeLiveData(spendsViewModel.spentFromDailyBudget) {
        // the previous total re-emitted is not the saved spend yet
        if (awaitingSavedSpend && it == spentAtCommit) return@observeLiveData

        awaitingSavedSpend = false
        recalculate()
    }

    val spendFlight = LocalSpendFlight.current
    val bump = remember { Animatable(1f) }
    val bumpScope = rememberCoroutineScope()

    observeLiveData(editorViewModel.stage) {
        if (!holdResult()) recalculate()
    }

    var lastSeenCommit by remember { mutableIntStateOf(editorViewModel.spentCommitted.value ?: 0) }

    observeLiveData(editorViewModel.spentCommitted) {
        if (it > lastSeenCommit) {
            lastSeenCommit = it
            spentAtCommit = spendsViewModel.spentFromDailyBudget.value
            awaitingSavedSpend = true

            // an edit can leave today's total unchanged: never hold longer than this
            bumpScope.launch {
                delay(1000)

                if (awaitingSavedSpend) {
                    awaitingSavedSpend = false
                    recalculate()
                }
            }

            // the flying amount reaches the bar about 200 ms in (emphasized easing)
            if (spendFlight.barTarget != null) {
                bumpScope.launch {
                    delay(200)
                    bump.snapTo(1.04f)
                    bump.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f))
                }
            }
        }
    }

    DisposableEffect(currency) {
        restBudgetPillViewModel.calculateValues(context, editorViewModel.currentSpent)

        onDispose { }
    }

    val shake = remember { Animatable(0f) }
    var previousState by remember { mutableStateOf(budgetState) }

    LaunchedEffect(budgetState) {
        val isOver = budgetState == DaileBudgetState.OVERDRAFT || budgetState == DaileBudgetState.BUDGET_END

        // Only a change made right now: NOT_SET means the values are still loading at start
        val crossedIntoOver = isOver && previousState == DaileBudgetState.NORMAL

        previousState = budgetState

        if (crossedIntoOver) {
            shake.snapTo(1f)
            shake.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 500f))
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Slow and soft while the budget is fine; when it goes over, the color has to arrive
    // together with the shake, otherwise the bar stays green under an overspend label
    val isOverBudget = budgetState == DaileBudgetState.OVERDRAFT || budgetState == DaileBudgetState.BUDGET_END
    val percentWithNewSpentAnimated = animateFloatAfterFirstValue(
        target = percentWithNewSpent,
        ready = budgetState != DaileBudgetState.NOT_SET,
        animationSpec = tween(
            durationMillis = if (isOverBudget) 450 else 1500,
            easing = FastOutSlowInEasing,
        ),
    )

    val harmonizedColor = toPalette(
        harmonize(
            combineColors(
                listOf(
                    colorBad,
                    colorNotGood,
                    colorGood,
                ),
                percentWithNewSpentAnimated.coerceIn(0f, 1f),
            ),
            colorEditor
        )
    )

    if (
        (hideOverspendingWarn && budgetState == DaileBudgetState.BUDGET_END)
        || budgetState == DaileBudgetState.NOT_SET
    ) {
        BigIconButton(
            icon = painterResource(R.drawable.ic_balance_wallet),
            contentDescription = null,
            onClick = { appViewModel.openSheet(PathState(WALLET_SHEET)) },
        )
    } else {
        val balloonState = rememberBalloonState()

        DisposableEffect(Unit) {
            onDispose { spendFlight.barTarget = null }
        }

        BalloonScope(
            modifier = Modifier.weight(1F),
            balloonState = balloonState,
            content = {
                Text(
                    text = stringResource(R.string.tutorial_open_wallet),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            onClose = {
                appViewModel.passTutorial(TUTORS.OPEN_WALLET)
            }
        ) {
            Card(
                modifier = Modifier
                    .weight(1F)
                    .height(50.dp)
                    .onGloballyPositioned {
                        val bounds = it.boundsInRoot()

                        spendFlight.barTarget = Offset(
                            bounds.right - with(localDensity) { 20.dp.toPx() },
                            bounds.center.y,
                        )
                        // digit height of the value label, measured on screen: ~37% of the bar
                        spendFlight.barValueHeight = bounds.height * 0.37f
                    }
                    .graphicsLayer {
                        translationX = shake.value * 10.dp.toPx()
                        scaleX = bump.value
                        scaleY = bump.value
                    },
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = harmonizedColor.container,
                    contentColor = harmonizedColor.onContainer,
                ),
                onClick = {
                    balloonState.hide()
                    appViewModel.openSheet(PathState(WALLET_SHEET))
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    BackgroundProgress(harmonizedColor)
                    Row(
                        modifier = Modifier.fillMaxSize().drawWithLayer {
                            drawContent()
                            val leftOffset = size.width - 20.dp.toPx()
                            drawRect(
                                topLeft = Offset(leftOffset, 0f),
                                size = Size(
                                    20.dp.toPx(),
                                    size.height,
                                ),
                                blendMode = BlendMode.SrcIn,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black,
                                        Color.Black.copy(alpha = 0f),
                                    ),
                                    startX = leftOffset,
                                    endX = leftOffset + 14.dp.toPx()
                                )
                            )
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        StatusLabel(harmonizedColor)
                        Spacer(modifier = Modifier.weight(1f))
                        ValueLabel(harmonizedColor)
                    }
                }
            }
        }

        DisposableEffect(budgetState, editorIsNotVisible) {
            if (tutorial === TUTORIAL_STAGE.READY_TO_SHOW && !editorIsNotVisible) {
                coroutineScope.launch {
                    delay(2000)
                    balloonState.show()
                }
            }

            onDispose { }
        }
    }
}

fun ContentDrawScope.drawWithLayer(block: ContentDrawScope.() -> Unit) {
    with(drawContext.canvas.nativeCanvas) {
        val checkPoint = saveLayer(null, null)
        block()
        restoreToCount(checkPoint)
    }
}

fun Modifier.drawWithLayer(block: ContentDrawScope.() -> Unit) = this.then(
    Modifier.drawWithContent {
        drawWithLayer {
            block()
        }
    }
)

@Preview(name = "The budget is almost completely spent")
@Composable
private fun Preview() {
    BuckwheatTheme {
        Row {
            RestBudgetPill()
            BigIconButton(
                icon = painterResource(R.drawable.ic_settings),
                contentDescription = null,
                onClick = { },
            )
        }
    }
}

@Preview(name = "Budget half spent")
@Composable
private fun PreviewHalf() {
    BuckwheatTheme {
        Row {
            RestBudgetPill()
        }
    }
}

@Preview(name = "Almost no budget")
@Composable
private fun PreviewFull() {
    BuckwheatTheme {
        Row {
            RestBudgetPill()
        }
    }
}

@Preview(name = "Overspending budget")
@Composable
private fun PreviewOverspending() {
    BuckwheatTheme {
        Row {
            RestBudgetPill()
        }
    }
}

@Preview(name = "Might mode", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNightMode() {
    BuckwheatTheme {
        Row {
            RestBudgetPill()
        }
    }
}
