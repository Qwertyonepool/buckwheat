package com.danilkinkin.buckwheat.editor

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the amount field and the budget bar share so a committed amount can fly into the bar.
 * Positions are in root coordinates. Nothing here is snapshot state: it is only read at the
 * moment a spend is committed.
 */
class SpendFlightState {
    /** Root bounds of the amount field. */
    var amountBounds: Rect? = null

    /**
     * The amount as it was last drawn, captured while typing. Capturing takes a few frames,
     * so doing it at commit time made the amount blink out before it started flying.
     */
    var captured: CapturedAmount? = null

    /** Right edge and vertical center of the value in the budget bar; null when it is hidden. */
    var barTarget: Offset? = null

    /** Height of the bar's value text, so the amount lands at the same size. */
    var barValueHeight: Float = 0f
}

class CapturedAmount(
    /** The raw editor value this image shows. */
    val rawValue: String,
    /** The amount alone, cropped from the field, without the text cursor. */
    val image: ImageBitmap,
    /** Root position of [image]'s top left corner. */
    val topLeft: Offset,
)

val LocalSpendFlight = compositionLocalOf { SpendFlightState() }

class SpendFlight(
    val amount: CapturedAmount,
    val target: Offset,
    val endScale: Float,
)

/**
 * Captures the amount as the field currently draws it. The layer capture is backed by a buffer
 * that is released shortly after, so it is copied right away and cropped to the amount: the
 * bounding box of the drawn pixels, minus a thin trailing run (the text cursor) when present.
 */
suspend fun captureAmount(
    layer: GraphicsLayer,
    rawValue: String,
    fieldTopLeft: Offset,
    maxCursorWidthPx: Int,
): CapturedAmount? {
    val captured = layer.toImageBitmap().asAndroidBitmap()

    return withContext(Dispatchers.Default) {
        val software = captured.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext null

        try {
            val rect = findInkRect(software, maxCursorWidthPx) ?: return@withContext null
            val cropped = Bitmap.createBitmap(software, rect.left, rect.top, rect.width, rect.height)

            CapturedAmount(
                rawValue = rawValue,
                image = cropped.asImageBitmap(),
                topLeft = fieldTopLeft + Offset(rect.left.toFloat(), rect.top.toFloat()),
            )
        } finally {
            software.recycle()
        }
    }
}

private fun findInkRect(bitmap: Bitmap, maxCursorWidthPx: Int): IntRect? {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)

    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val columnHasInk = BooleanArray(width)

    for (y in 0 until height) {
        val row = y * width

        for (x in 0 until width) {
            if ((pixels[row + x] ushr 24) > 32) columnHasInk[x] = true
        }
    }

    var left = 0
    while (left < width && !columnHasInk[left]) left++
    if (left == width) return null

    var right = width - 1
    while (!columnHasInk[right]) right--

    var runStart = right
    while (runStart > left && columnHasInk[runStart - 1]) runStart--

    val runIsThin = right - runStart + 1 <= maxCursorWidthPx
    val hasGapBefore = runStart > left && !columnHasInk[runStart - 1]

    if (runIsThin && hasGapBefore) {
        right = runStart - 1
        while (right > left && !columnHasInk[right]) right--
    }

    var top = -1
    var bottom = -1

    for (y in 0 until height) {
        val row = y * width

        for (x in left..right) {
            if ((pixels[row + x] ushr 24) > 32) {
                if (top < 0) top = y
                bottom = y
                break
            }
        }
    }

    if (top < 0) return null

    return IntRect(left, top, right + 1, bottom + 1)
}

private const val FLIGHT_DURATION_MS = 480

// Material 3 "emphasized" easing: leaves quickly, settles softly into the bar
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Draws the committed amount travelling into the budget bar, shrinking to the size of the
 * bar's value and fading as it arrives. Purely visual: it never intercepts touches and the
 * spend is already saved before it starts.
 */
@Composable
fun SpendFlightOverlay(
    flight: SpendFlight?,
    rootOffset: Offset,
) {
    val progress = remember { Animatable(0f) }
    var active by remember { mutableStateOf(false) }

    LaunchedEffect(flight) {
        if (flight == null) return@LaunchedEffect

        active = true
        progress.snapTo(0f)
        progress.animateTo(1f, tween(FLIGHT_DURATION_MS, easing = EmphasizedEasing))
        active = false
    }

    if (!active || flight == null) return

    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        val image = flight.amount.image
        val topLeft = flight.amount.topLeft - rootOffset

        // right-aligned amount and value: travel by the right edge, centered vertically
        val rightCenter = topLeft + Offset(image.width.toFloat(), image.height / 2f)
        val travel = (flight.target - rootOffset) - rightCenter
        val scale = lerp(1f, flight.endScale, t)
        val alpha = if (t < 0.6f) 1f else 1f - (t - 0.6f) / 0.4f

        translate(travel.x * t, travel.y * t) {
            scale(scale, scale, pivot = rightCenter) {
                drawImage(image = image, topLeft = topLeft, alpha = alpha.coerceIn(0f, 1f))
            }
        }
    }
}
