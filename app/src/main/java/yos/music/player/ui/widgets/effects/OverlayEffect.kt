package yos.music.player.ui.widgets.effects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 在 Compose 上实现 Add 的效果。
 *
 * 注意：如果要使用 .alpha() 设置透明度，则必须在该 Modifier 之后，或者使用 .graphicLayer { this.alpha = 0.5f } 类此。
 *
 * —— By Yos-X
 */
@Composable
fun Modifier.overlayEffect(outset: Dp = 0.dp) = this.drawWithCache {
    val overlayPaint = Paint().apply {
        blendMode = BlendMode.Plus
    }
    val padding = outset.toPx().coerceAtLeast(0f)
    val rect = Rect(-padding, -padding, size.width + padding, size.height + padding)

    onDrawWithContent {
        val canvas = this.drawContext.canvas

        canvas.saveLayer(rect, overlayPaint)

        drawContent()

        canvas.restore()
    }
}
