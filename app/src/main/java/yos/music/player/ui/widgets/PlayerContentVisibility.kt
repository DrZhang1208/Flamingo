package yos.music.player.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
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
    AnimatedVisibility(
        visible = visible, modifier = modifier,
        enter = fadeIn(tween(210, easing = FastOutSlowInEasing)) +
            scaleIn(tween(210, easing = FastOutSlowInEasing), initialScale = 0.97f, transformOrigin = origin),
        exit = fadeOut(tween(210, easing = FastOutSlowInEasing)) +
            scaleOut(tween(210, easing = FastOutSlowInEasing), targetScale = 0.97f, transformOrigin = origin),
        content = content
    )
}
