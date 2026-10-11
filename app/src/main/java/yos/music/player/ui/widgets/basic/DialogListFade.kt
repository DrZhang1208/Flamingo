package yos.music.player.ui.widgets.basic

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Fade into the opaque sheet surface without an offscreen texture on every scroll frame. */
internal fun Modifier.dialogListFade(background: Color, depth: Dp = 18.dp): Modifier = drawWithCache {
    val fraction = minOf(depth.toPx(), size.height / 2f) / size.height.coerceAtLeast(1f)
    val fade = Brush.verticalGradient(
        0f to background, fraction to Color.Transparent,
        (1f - fraction) to Color.Transparent, 1f to background
    )
    onDrawWithContent {
        drawContent()
        drawRect(fade)
    }
}
