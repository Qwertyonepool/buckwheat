package com.danilkinkin.buckwheat.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.ui.BuckwheatTheme
import com.danilkinkin.buckwheat.util.countDays
import com.danilkinkin.buckwheat.util.countDaysToToday
import com.danilkinkin.buckwheat.util.rememberReveal
import com.danilkinkin.buckwheat.util.toDate
import java.time.LocalDate
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DaysLeftCard(
    modifier: Modifier = Modifier,
    startDate: Date,
    finishDate: Date?,
) {
    val localDensity = LocalDensity.current

    var size by remember { mutableStateOf(0.dp) }

    val days = countDays(finishDate!!, startDate)
    val restDays = countDaysToToday(finishDate)

    // The ring shows the days left, like the label. It sweeps in and the number counts up when
    // the card appears. The wave itself stays still: the indicator resets its phase when
    // waveSpeed changes, so stopping a moving wave made it jump at the end of the sweep.
    val reveal = rememberReveal()
    val left = (restDays / days.toFloat()).coerceIn(0f, 1f)
    val ringStroke = with(localDensity) { Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round) }

    Box(
        Modifier
            .widthIn(max = 120.dp)
            .fillMaxHeight()
            .onGloballyPositioned {
                size = with(localDensity) { it.size.height.toDp() }
            }
    ) {
        Card(
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            shape = CircleShape,
        ) {
            val textColor = LocalContentColor.current

            Box(Modifier.fillMaxSize()) {
                CircularWavyProgressIndicator(
                    progress = { left * reveal.value },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    stroke = ringStroke,
                    trackStroke = ringStroke,
                    // the default flattens the wave below 10% and above 95%: a full ring on the
                    // first days of a period would be a plain circle again
                    amplitude = { if (it > 0f) 1f else 0f },
                    waveSpeed = 0.dp,
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = (restDays * reveal.value).roundToInt().toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = MaterialTheme.typography.titleLarge.fontSize
                        )
                    )
                    Text(
                        text = stringResource(R.string.days_left),
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(size * 0.1f))
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    BuckwheatTheme {
        Box(
            modifier = Modifier
                .height(200.dp)
                .width(900.dp)
        ) {
            DaysLeftCard(
                startDate = LocalDate.now().minusDays(18).toDate(),
                finishDate = LocalDate.now().plusDays(10).toDate(),
            )
        }
    }
}