package yos.music.player.ui.widgets

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/** Use actual player constraints, including freeform and split-screen windows. */
internal data class PlayerWindowLayout(val compact: Boolean, val split: Boolean) {
    val tabletLandscape: Boolean get() = split && !compact
}
internal fun playerWindowLayout(widthDp: Float, heightDp: Float) = PlayerWindowLayout(
    compact = heightDp < 600f,
    split = widthDp > heightDp
)

/** Keep the host coordinate space so shared-element bounds remain comparable. */
internal fun Modifier.playerWindowPane(start: Float, fraction: Float): Modifier = layout { measurable, constraints ->
    val width = (constraints.maxWidth * fraction).roundToInt().coerceAtLeast(1)
    val child = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, child.height) {
        child.place((constraints.maxWidth * start).roundToInt(), 0)
    }
}

/** 横屏歌词面板的顶/底渐变：歌词滚出容器边缘时淡出而非硬切。 */
internal fun Modifier.verticalEdgeFade(topFade: Dp = 24.dp, bottomFade: Dp = 24.dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val height = size.height.coerceAtLeast(1f)
            val top = (topFade.toPx() / height).coerceIn(0f, 0.5f)
            val bottom = (bottomFade.toPx() / height).coerceIn(0f, 0.5f)
            val brush = Brush.verticalGradient(
                0f to Color.Transparent,
                top to Color.Black,
                (1f - bottom).coerceAtLeast(top) to Color.Black,
                1f to Color.Transparent
            )
            onDrawWithContent {
                drawContent()
                drawRect(brush, blendMode = BlendMode.DstIn)
            }
        }

/** 横屏歌词面板顶/底渐变（saveLayer+DstIn，与竖屏 clipPlayerLyrics 同机制——
 *  Offscreen 图层变体在该面板上不生效）。顶部先让过 Lyric 的 16dp 固定垫高
 *  （insetsHandled），渐变从内容真实顶缘起 32dp 淡入；底部在面板底缘上方
 *  48dp 淡出，与竖屏底缘过渡同宽。 */
internal fun Modifier.lyricPaneEdgeFade(
    contentTopInset: Dp = 16.dp,
    topFade: Dp = 32.dp,
    bottomFade: Dp = 48.dp
): Modifier = drawWithCache {
    val height = size.height.coerceAtLeast(1f)
    val contentTop = contentTopInset.toPx().coerceAtMost(height * 0.5f)
    val topEnd = (contentTop + topFade.toPx()).coerceAtMost(height)
    val bottomStart = (height - bottomFade.toPx()).coerceAtLeast(topEnd)
    val brush = Brush.verticalGradient(
        0f to Color.Transparent,
        contentTop / height to Color.Transparent,
        topEnd / height to Color.Black,
        bottomStart / height to Color.Black,
        1f to Color.Transparent
    )
    val paint = Paint()
    val bounds = Rect(0f, 0f, size.width, size.height)
    onDrawWithContent {
        val canvas = drawContext.canvas
        canvas.saveLayer(bounds, paint)
        try {
            drawContent()
            drawRect(brush, blendMode = BlendMode.DstIn)
        } finally {
            canvas.restore()
        }
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
