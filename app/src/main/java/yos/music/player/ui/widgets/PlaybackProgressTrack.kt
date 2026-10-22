package yos.music.player.ui.widgets

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun DrawScope.drawPlaybackProgress(progress: Float) {
    val fraction = progress.coerceIn(0f, 1f)
    val corners = CornerRadius(size.height / 2f)
    drawRoundRect(Color.White.copy(alpha = 0.5f), cornerRadius = corners)
    // Map progress between the end-cap centres. Zero progress stays circular;
    // the fill grows continuously without a squeezed cap or an early plateau.
    val diameter = size.height.coerceAtMost(size.width)
    val width = diameter + (size.width - diameter) * fraction
    drawRoundRect(Color.White, size = Size(width, size.height), cornerRadius = corners)
}
