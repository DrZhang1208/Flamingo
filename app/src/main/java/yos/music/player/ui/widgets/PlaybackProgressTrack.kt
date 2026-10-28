package yos.music.player.ui.widgets

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun DrawScope.drawPlaybackProgress(progress: Float) {
    val fraction = progress.coerceIn(0f, 1f)
    val corners = CornerRadius(size.height / 2f)
    // 未播轨道提亮到 0.85（原 0.5 看着灰暗）；不能到 1.0——与已播填充同为纯白
    // 会失去进度可辨性。音量条与此共用。
    drawRoundRect(Color.White.copy(alpha = 0.85f), cornerRadius = corners)
    // Map progress between the end-cap centres. Zero progress stays circular;
    // the fill grows continuously without a squeezed cap or an early plateau.
    val diameter = size.height.coerceAtMost(size.width)
    val width = diameter + (size.width - diameter) * fraction
    drawRoundRect(Color.White, size = Size(width, size.height), cornerRadius = corners)
}
