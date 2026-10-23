package yos.music.player.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin

/** Keep outgoing content alive until its fade/scale completes; reversals stay continuous. */
@Composable
internal fun PlayerContentVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    val origin = TransformOrigin(0.5f, 0.15f)
    // AnimatedVisibility 首次组合即 visible=true 时不播进入动画，队列页恰会走到
    // 这条路径（封面页直接进队列时随分支整体组合）；先按不可见组合再翻转一帧，
    // 保证进入动画（与歌词页同款的淡入+缩放，外加自下轻滑）总会播放。
    var composed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { composed = true }
    AnimatedVisibility(
        visible = visible && composed, modifier = modifier,
        enter = fadeIn(tween(210, easing = FastOutSlowInEasing)) +
            scaleIn(tween(210, easing = FastOutSlowInEasing), initialScale = 0.97f, transformOrigin = origin) +
            slideInVertically(tween(210, easing = FastOutSlowInEasing)) { it / 20 },
        exit = fadeOut(tween(210, easing = FastOutSlowInEasing)) +
            scaleOut(tween(210, easing = FastOutSlowInEasing), targetScale = 0.97f, transformOrigin = origin) +
            slideOutVertically(tween(210, easing = FastOutSlowInEasing)) { it / 20 },
        content = content
    )
}
