package yos.music.player.ui.widgets.basic

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Fade into the opaque sheet surface without an offscreen texture on every scroll frame. */
internal fun Modifier.dialogListFade(background: Color, depth: Dp = 36.dp): Modifier = drawWithCache {
    val fraction = minOf(depth.toPx().coerceAtLeast(0f), size.height / 3f) / size.height.coerceAtLeast(1f)
    // Cached multi-stop quintic falloff has flat slope/curvature at both ends.
    // Preserve the surface RGB even at alpha zero to avoid a dark interpolation fringe.
    val stops = buildList {
        for (index in 0..32) {
            val t = index / 32f
            add(t * fraction to background.copy(alpha = background.alpha * dialogFadeOpacity(t)))
        }
        for (index in 0..32) {
            val t = index / 32f
            add(1f - fraction + t * fraction to background.copy(alpha = background.alpha * dialogFadeOpacity(1f - t)))
        }
    }
    val fade = Brush.verticalGradient(*stops.toTypedArray())
    onDrawWithContent {
        drawContent()
        drawRect(fade)
    }
}

internal fun dialogFadeOpacity(fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return (1f - t * t * t * (t * (t * 6f - 15f) + 10f)).coerceIn(0f, 1f)
}
