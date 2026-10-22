package yos.music.player.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.layout.layout

/** Owned above page/track content so image replacement cannot restart the motion. */
@Composable
internal fun rememberArtworkPauseProgress(isPlaying: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (isPlaying) 0f else 1f,
    animationSpec = spring(stiffness = 300f, dampingRatio = if (isPlaying) 0.72f else 1f, visibilityThreshold = 0.001f),
    label = "ArtworkPlaybackScale"
)

/** Match the former 7..44dp padding visually without relaying out the image each frame. */
internal fun Modifier.artworkPlaybackTransform(progress: () -> Float, layoutProgress: Float = 0f): Modifier = graphicsLayer {
    val paused = progress().coerceIn(-0.08f, 1f)
    val shrink = if (size.width > 0f) (74.dp.toPx() / size.width).coerceIn(0f, 1f) else 0f
    scaleX = 1f + shrink * (layoutProgress - paused)
    scaleY = scaleX
    transformOrigin = TransformOrigin.Center
}

/** Shared bounds always measure the visible cover; no page-keyed size rebasing. */
internal fun Modifier.artworkSharedFrame(sharedElement: Modifier, progress: () -> Float): Modifier =
    layout { measurable, constraints ->
        val base = 7.dp.roundToPx()
        val extra = minOf(37.dp.toPx(), ((constraints.maxWidth - 2 * base).coerceAtLeast(0)) * 0.125f)
        val inset = base + (extra * progress().coerceIn(0f, 1f)).toInt()
        val child = measurable.measure(constraints.offset(horizontal = -2 * inset, vertical = -2 * inset))
        layout(child.width + 2 * inset, child.height + 2 * inset) {
            child.placeRelative(inset, inset)
        }
    }.then(sharedElement)
