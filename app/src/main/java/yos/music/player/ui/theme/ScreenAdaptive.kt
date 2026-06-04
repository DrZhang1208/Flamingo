package yos.music.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 设计基准屏幕宽度（dp） */
private const val BASE_WIDTH = 360f

/** 最小缩放比例，防止在极小屏幕上元素过小 */
private const val MIN_SCALE = 0.75f

/**
 * 自适应 dp 值。
 * 在窄于 360dp 的屏幕上按比例缩小，不小于 0.75x，不放大（最大 1x）。
 */
@Composable
fun rememberAdaptive(units: Int): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val scale = (widthDp / BASE_WIDTH).coerceIn(MIN_SCALE, 1f)
    return remember(units, widthDp) { (units * scale).dp }
}

/**
 * 自适应 dp 值，返回 Float 方便计算。
 */
@Composable
fun rememberAdaptive(units: Float): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val scale = (widthDp / BASE_WIDTH).coerceIn(MIN_SCALE, 1f)
    return remember(units, widthDp) { (units * scale).dp }
}

/**
 * 在组合上下文中缓存缩放比例和自适应计算，避免重复读取 LocalConfiguration。
 */
@Stable
class AdaptiveScope(private val scale: Float) {
    fun dp(units: Int): Dp = (units * scale).dp
    fun dp(units: Float): Dp = (units * scale).dp
    fun px(units: Int): Float = units * scale
    fun px(units: Float): Float = units * scale
}

/**
 * 需要在多处使用自适应值时，先调用此函数获取 [AdaptiveScope]，
 * 然后通过 scope.dp(...) 计算，避免重复读取 LocalConfiguration。
 */
@Composable
fun rememberAdaptiveScope(): AdaptiveScope {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val scale = (widthDp / BASE_WIDTH).coerceIn(MIN_SCALE, 1f)
    return remember(widthDp) { AdaptiveScope(scale) }
}
