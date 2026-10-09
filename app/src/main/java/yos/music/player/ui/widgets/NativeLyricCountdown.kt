package yos.music.player.ui.widgets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Playback-driven breathing freezes on pause and resumes without a second animation clock. */
internal fun DrawScope.drawNativeCountdown(timeMs: Float, startMs: Float, endMs: Float, color: Color) {
    val envelope = countdownEmphasis(timeMs, startMs, endMs)
    if (envelope <= 0f) return
    val elapsed = timeMs - startMs
    val progress = (elapsed / (endMs - startMs).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val center = Offset(36.dp.toPx(), size.height / 2f)
    // One envelope controls size, opacity and occupied row height at both boundaries.
    scale(envelope, pivot = center) {
        repeat(3) { dot ->
            val fill = nativeSmoothStep(progress * 3f - dot)
            val breath = (0.5f - 0.5f * sin(2.0 * PI * (elapsed - dot * 150f) / 1800f)).toFloat()
            val radius = (5.5f + 0.7f * fill) * (0.91f + 0.18f * breath)
            val opacity = envelope * (0.42f + 0.58f * fill) * (0.82f + 0.18f * breath)
            drawCircle(color.copy(alpha = color.alpha * opacity), radius.dp.toPx(),
                Offset((12f + 24f * dot).dp.toPx(), center.y))
        }
    }
}
