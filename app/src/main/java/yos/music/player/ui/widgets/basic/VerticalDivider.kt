package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import yos.music.player.ui.theme.withNight

/** 横屏分栏之间的竖向分割线：与横向分隔线同源的黑/白 alpha 细线 */
@Composable
@NonRestartableComposable
fun VerticalDivider(modifier: Modifier = Modifier) =
    Spacer(
        modifier = modifier
            .fillMaxHeight()
            .width(1.dp)
            .alpha(0.12f)
            .background(Color.Black withNight Color.White)
    )

/**
 * 分栏竖线（画在自身左缘外侧 8dp，即 16dp 栏间距的中点）。
 * 用 drawBehind 而非子项：含 LazyList 的分栏不能走 intrinsic 测量，
 * fillMaxHeight 子项在无界高度约束下也不可靠。
 */
@Composable
fun Modifier.paneDivider(): Modifier {
    val color = (Color.Black withNight Color.White).copy(alpha = 0.12f)
    val offsetPx = with(LocalDensity.current) { 8.dp.toPx() }
    val strokePx = with(LocalDensity.current) { 1.dp.toPx() }
    return drawBehind {
        val x = -offsetPx
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokePx)
    }
}
