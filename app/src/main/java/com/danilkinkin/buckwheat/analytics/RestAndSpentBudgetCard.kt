package com.danilkinkin.buckwheat.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.WavyShape
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.ui.BuckwheatTheme
import com.danilkinkin.buckwheat.ui.colorBad
import com.danilkinkin.buckwheat.ui.colorGood
import com.danilkinkin.buckwheat.ui.colorNotGood
import com.danilkinkin.buckwheat.util.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

@Composable
fun RestAndSpentBudgetCard(
    modifier: Modifier = Modifier,
    bigVariant: Boolean = false,
    appViewModel: AppViewModel = hiltViewModel(),
    spendsViewModel: SpendsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val currency by spendsViewModel.currency.observeAsState(ExtendCurrency.none())
    val showSpentCard by appViewModel.showSpentCardByDefault.observeAsState(false)

    val wholeBudget = spendsViewModel.budget.value!!
    val restBudget by spendsViewModel.howMuchBudgetRest().observeAsState(BigDecimal.ZERO)

    val percent = remember (restBudget) { restBudget.divide(wholeBudget, 4, RoundingMode.HALF_EVEN) }

    val overString = stringResource(R.string.over)
    val coroutineScope = rememberCoroutineScope()

    fun formatPercent(isSpent: Boolean): String {
        val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
        formatter.maximumFractionDigits = 2
        formatter.minimumFractionDigits = 0

        val percentCalculated = if (isSpent) {
            BigDecimal(1).minus(percent).multiply(BigDecimal(100))
        } else {
            percent.multiply(BigDecimal(100))
        }

        return if (percentCalculated.abs() > BigDecimal(1000)) {
            "$overString ${formatter.format(percentCalculated.coerceIn(BigDecimal(-1000), BigDecimal(1000)))}"
        } else {
            formatter.format(percentCalculated)
        }
    }

    // Two pages, rest and spent, as the dots in the corner say: switch by tapping or by
    // swiping towards the other page. The text follows the finger a little (bounded), so the
    // card shows it can be swiped; past the threshold it switches, otherwise it springs back.
    val density = LocalDensity.current
    val switchThreshold = with(density) { 40.dp.toPx() }
    val maxPull = with(density) { 24.dp.toPx() }
    val drag = remember { Animatable(0f) }
    val isSpentNow by rememberUpdatedState(showSpentCard)

    fun releaseDrag() {
        coroutineScope.launch { drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f)) }
    }

    val shift = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        fun anim() {
            coroutineScope.launch {
                shift.animateTo(
                    1f,
                    animationSpec = FloatTweenSpec(if (bigVariant) 6000 else 5000, 0, LinearEasing)
                )
                shift.snapTo(0f)
                anim()
            }
        }

        anim()
    }

    val harmonizedColor = toPalette(
        harmonize(
            combineColors(
                listOf(
                    colorBad,
                    colorNotGood,
                    colorGood,
                ),
                percent.coerceIn(BigDecimal.ZERO, BigDecimal(1)).toFloat(),
            )
        )
    )

    Box(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.extraLarge)
            .clickable { appViewModel.setShowSpentCardByDefault(!showSpentCard) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        // rest is the first page, spent the second: left goes forward
                        val switch = if (isSpentNow) {
                            drag.value > switchThreshold
                        } else {
                            drag.value < -switchThreshold
                        }

                        if (switch) appViewModel.setShowSpentCardByDefault(!isSpentNow)
                        releaseDrag()
                    },
                    onDragCancel = { releaseDrag() },
                ) { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch { drag.snapTo(drag.value + dragAmount) }
                }
            }
    ) {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = harmonizedColor.container,
                contentColor = harmonizedColor.onContainer,
            ),
        ) {
            val textColor = LocalContentColor.current

            Box(
                Modifier
                    .height(IntrinsicSize.Min)
                    .fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                harmonizedColor.main,
                                shape = WavyShape(
                                    period = if (bigVariant) 70.dp else 40.dp,
                                    amplitude = percent
                                        .toFloat()
                                        .clamp(0.96f, 1f) * (if (bigVariant) 3.5.dp else 2.dp),
                                    shift = shift.value,
                                ),
                            )
                            .fillMaxHeight()
                            .fillMaxWidth(percent.toFloat()),
                    )
                }

                AnimatedContent(
                    targetState = showSpentCard,
                    modifier = Modifier.graphicsLayer {
                        val towardsOther = if (showSpentCard) drag.value > 0 else drag.value < 0

                        translationX = (drag.value * if (towardsOther) 0.4f else 0.12f)
                            .coerceIn(-maxPull, maxPull)
                    },
                    transitionSpec = {
                        // the spent page lies to the right of the rest page
                        val direction = if (targetState) 1 else -1
                        val slideSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = 500f,
                            visibilityThreshold = IntOffset.VisibilityThreshold,
                        )

                        (slideInHorizontally(slideSpec) { direction * it / 3 } + fadeIn(tween(180)))
                            .togetherWith(
                                slideOutHorizontally(slideSpec) { -direction * it / 3 } + fadeOut(tween(120))
                            )
                            .using(SizeTransform(clip = false))
                    },
                    label = "restOrSpent",
                ) { isSpent ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(PaddingValues(vertical = 16.dp, horizontal = 24.dp)),
                    horizontalAlignment = if (bigVariant) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    if (bigVariant) Spacer(modifier = Modifier.height(36.dp))
                    Text(
                        text = numberFormat(
                            context,
                            if (isSpent) {
                                wholeBudget - restBudget
                            } else {
                                restBudget
                            },
                            currency = currency,
                        ),
                        style = MaterialTheme.typography.displayMedium,
                        fontSize = if (bigVariant) MaterialTheme.typography.headlineLarge.fontSize else MaterialTheme.typography.titleLarge.fontSize,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        lineHeight = TextUnit(0.2f, TextUnitType.Em)
                    )
                    Text(
                        text = if (isSpent) {
                            stringResource(R.string.spent_budget)
                        } else {
                            stringResource(R.string.rest_budget)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.6f),
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                    if (bigVariant) {
                        Spacer(modifier = Modifier.height(24.dp))
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    CompositionLocalProvider(
                        LocalContentColor provides textColor,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.rest_budget_percent,
                                    formatPercent(isSpent)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = if (bigVariant) MaterialTheme.typography.labelMedium.fontSize else MaterialTheme.typography.labelSmall.fontSize,
                            )
                        }
                    }
                }
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) {
            // the current page's dot is longer, like a page indicator
            val restDotWidth by animateDpAsState(if (showSpentCard) 4.dp else 10.dp, label = "restDot")
            val spentDotWidth by animateDpAsState(if (showSpentCard) 10.dp else 4.dp, label = "spentDot")
            val restDotAlpha by animateFloatAsState(if (showSpentCard) 0.3f else 1f, label = "restDotAlpha")
            val spentDotAlpha by animateFloatAsState(if (showSpentCard) 1f else 0.3f, label = "spentDotAlpha")

            Box(
                Modifier
                    .size(width = restDotWidth, height = 4.dp)
                    .background(
                        color = harmonizedColor.onContainer.copy(alpha = restDotAlpha),
                        shape = CircleShape,
                    )
            )
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(width = spentDotWidth, height = 4.dp)
                    .background(
                        color = harmonizedColor.onContainer.copy(alpha = spentDotAlpha),
                        shape = CircleShape,
                    )
            )
        }
    }
}

@Preview(name = "The budget is almost completely spent")
@Composable
private fun Preview() {
    BuckwheatTheme {
        RestAndSpentBudgetCard()
    }
}

@Preview(name = "Budget half spent")
@Composable
private fun PreviewHalf() {
    BuckwheatTheme {
        RestAndSpentBudgetCard()
    }
}

@Preview(name = "Almost no budget")
@Composable
private fun PreviewFull() {
    BuckwheatTheme {
        RestAndSpentBudgetCard()
    }
}
