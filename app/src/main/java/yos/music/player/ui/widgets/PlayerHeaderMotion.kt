package yos.music.player.ui.widgets

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/** Typography is laid out at its endpoint, then scaled; never reflow at in-flight widths. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SharedTransitionScope.playerHeaderModifier(
    key: String, visibilityScope: AnimatedVisibilityScope
): Modifier {
    val state = rememberSharedContentState("player-header-$key")
    return if (key == "title" || key == "artist") Modifier.sharedBounds(
        state, visibilityScope,
        enter = fadeIn(), exit = fadeOut(),
        boundsTransform = { _, _ -> spring(dampingRatio = 1f, stiffness = 360f) },
        resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Fit, Alignment.CenterStart),
        zIndexInOverlay = 1f
    ) else Modifier.sharedElement(
        state, visibilityScope,
        boundsTransform = { _, _ -> spring(dampingRatio = 1f, stiffness = 360f) },
        zIndexInOverlay = 1f
    )
}
