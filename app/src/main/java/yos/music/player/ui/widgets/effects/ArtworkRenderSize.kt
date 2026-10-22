package yos.music.player.ui.widgets.effects

import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal const val ARTWORK_RENDER_LONG_EDGE = 384

/** Cap the expensive colour/blur surface, preserving physical aspect ratio. */
internal fun artworkRenderSize(width: Int, height: Int, limit: Int = ARTWORK_RENDER_LONG_EDGE): IntSize {
    require(limit > 0)
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val scale = minOf(1f, limit.toFloat() / maxOf(w, h))
    return IntSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
}
