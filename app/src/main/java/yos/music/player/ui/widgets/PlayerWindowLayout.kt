package yos.music.player.ui.widgets

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/** Use actual player constraints, including freeform and split-screen windows. */
internal data class PlayerWindowLayout(val compact: Boolean, val split: Boolean)
internal fun playerWindowLayout(widthDp: Float, heightDp: Float) = PlayerWindowLayout(
    compact = heightDp < 600f,
    split = heightDp < 600f && widthDp > heightDp
)

/** Keep the host coordinate space so shared-element bounds remain comparable. */
internal fun Modifier.playerWindowPane(start: Float, fraction: Float): Modifier = layout { measurable, constraints ->
    val width = (constraints.maxWidth * fraction).roundToInt().coerceAtLeast(1)
    val child = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, child.height) {
        child.place((constraints.maxWidth * start).roundToInt(), 0)
    }
}

/** One fixed-coordinate mask replaces nested full-viewport offscreen passes. */
internal fun Modifier.clipPlayerLyrics(
    enabled: () -> Boolean,
    controlsTop: () -> Float?,
    lyricsTop: () -> Float? = { null }
): Modifier = drawWithCache {
    val controls = controlsTop().takeIf { enabled() }
    val bottom = controls?.let { (it - 8.dp.toPx()).coerceIn(0f, size.height) } ?: size.height
    val fadeStart = if (controls != null) (bottom - 48.dp.toPx()).coerceAtLeast(0f) else bottom
    val top = lyricsTop()?.coerceIn(0f, fadeStart)
    val topEnd = top?.let { (it + 32.dp.toPx()).coerceAtMost(fadeStart) }
    val maskHeight = size.height.coerceAtLeast(1f)
    val stops = buildList {
        add(0f to if (top == null) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.Transparent)
        if (top != null && topEnd != null) {
            add(top / maskHeight to androidx.compose.ui.graphics.Color.Transparent)
            add(topEnd / maskHeight to androidx.compose.ui.graphics.Color.Black)
        }
        add(fadeStart / maskHeight to androidx.compose.ui.graphics.Color.Black)
        add(bottom / maskHeight to if (controls == null) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.Transparent)
        add(1f to if (controls == null) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.Transparent)
    }
    val brush = androidx.compose.ui.graphics.Brush.verticalGradient(colorStops = stops.toTypedArray())
    val paint = androidx.compose.ui.graphics.Paint()
    val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, bottom)
    onDrawWithContent {
        if (controls == null && top == null) drawContent() else {
            val canvas = drawContext.canvas
            canvas.saveLayer(bounds, paint)
            try {
                clipRect(bottom = bottom) { this@onDrawWithContent.drawContent() }
                drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            } finally { canvas.restore() }
        }
    }
}
