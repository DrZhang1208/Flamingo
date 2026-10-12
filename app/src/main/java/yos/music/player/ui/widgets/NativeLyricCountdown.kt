package yos.music.player.ui.widgets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

// Release the slot before the next lyric's preroll takes over the scroll anchor.
// Opacity reaches zero slightly earlier, so collapsing the last pixels never
// clips a visible dot. These envelopes are playback based, including after seek.
private fun countdownExitEnd(startMs: Float, endMs: Float): Float =
    maxOf(startMs, nativeLyricVisualStartMs(endMs))

internal fun countdownEmphasis(timeMs: Float, startMs: Float, endMs: Float): Float {
    if (endMs <= startMs || timeMs <= startMs) return 0f
    val exitEnd = countdownExitEnd(startMs, endMs)
    return minOf(nativeSmoothStep((timeMs - startMs) / 360f),
        nativeSmoothStep((exitEnd - 120f - timeMs) / 520f))
}

internal fun countdownLayoutFraction(timeMs: Float, startMs: Float, endMs: Float): Float {
    if (endMs <= startMs || timeMs <= startMs) return 0f
    return minOf(nativeSmoothStep((timeMs - startMs) / 360f),
        nativeSmoothStep((countdownExitEnd(startMs, endMs) - timeMs) / 500f))
}

internal fun countdownScale(timeMs: Float, startMs: Float, endMs: Float): Float {
    val enter = nativeSmoothStep((timeMs - startMs) / 360f)
    val exit = nativeSmoothStep((countdownExitEnd(startMs, endMs) - 120f - timeMs) / 520f)
    return enter * (0.65f + 0.35f * exit)
}

/** Playback-driven breathing freezes on pause and resumes without a second animation clock. */
internal fun DrawScope.drawNativeCountdown(timeMs: Float, startMs: Float, endMs: Float, color: Color) {
    val envelope = countdownEmphasis(timeMs, startMs, endMs)
    if (envelope <= 0f) return
    val elapsed = timeMs - startMs
    val progress = (elapsed / (endMs - startMs).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val center = Offset(36.dp.toPx(), size.height / 2f)
    // Visual scale and alpha finish independently of the slot's layout height.
    val breath = (0.5f - 0.5f * sin(2.0 * PI * elapsed / 2800f)).toFloat()
    val radius = 6f * (0.91f + 0.18f * breath)
    scale(countdownScale(timeMs, startMs, endMs), pivot = center) {
        repeat(3) { dot ->
            val fill = nativeSmoothStep(progress * 3f - dot)
            val opacity = envelope * (0.42f + 0.58f * fill) * (0.82f + 0.18f * breath)
            drawCircle(color.copy(alpha = color.alpha * opacity), radius.dp.toPx(),
                Offset((12f + 24f * dot).dp.toPx(), center.y))
        }
    }
}
