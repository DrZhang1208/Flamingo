package yos.music.player.ui.widgets.effects

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal enum class ArtworkTransitionMode { HIDDEN, HOLD, STILL, ANIMATE }

internal fun artworkTransitionMode(progress: Float, playing: Boolean, foreground: Boolean): ArtworkTransitionMode = when {
    !foreground || progress > 0f && progress < 1f -> ArtworkTransitionMode.HOLD
    progress <= 0f -> ArtworkTransitionMode.HIDDEN
    !playing -> ArtworkTransitionMode.STILL
    else -> ArtworkTransitionMode.ANIMATE
}

@Composable
internal fun rememberArtworkTransitionMode(
    isPlaying: () -> Boolean,
    showMiniPlayer: () -> Boolean,
    expansionProgress: (() -> Float)?
): ArtworkTransitionMode {
    val owner = LocalLifecycleOwner.current
    var foreground by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val mode by remember(isPlaying, showMiniPlayer, expansionProgress, owner) {
        derivedStateOf(structuralEqualityPolicy()) {
            val progress = expansionProgress?.invoke() ?: if (showMiniPlayer()) 0f else 1f
            artworkTransitionMode(progress, isPlaying(), foreground)
        }
    }
    return mode
}

internal data class ArtworkLayers(
    val previous: Bitmap,
    val next: Bitmap,
    val progress: Animatable<Float, AnimationVector1D> = Animatable(1f)
)

@Composable
internal fun rememberArtworkLayers(texture: Bitmap, mode: ArtworkTransitionMode, durationMs: Int): ArtworkLayers {
    var layers by remember { mutableStateOf(ArtworkLayers(texture, texture)) }
    LaunchedEffect(texture, mode) {
        if (mode == ArtworkTransitionMode.HOLD) return@LaunchedEffect
        val old = layers
        if (mode == ArtworkTransitionMode.HIDDEN) {
            if (texture !== old.next || old.previous !== old.next) layers = ArtworkLayers(texture, texture)
            return@LaunchedEffect
        }
        if (mode == ArtworkTransitionMode.STILL) {
            if (texture !== old.next) layers = ArtworkLayers(texture, texture)
            return@LaunchedEffect
        }
        if (texture !== old.next) {
            val progress = old.progress.value
            val visible = when {
                progress >= 1f -> old.next
                progress <= 0f -> old.previous
                else -> withContext(Dispatchers.Default) {
                    freezeArtworkBlend(old.previous, old.next, progress).also { it.prepareToDraw() }
                }
            }
            layers = ArtworkLayers(visible, texture, Animatable(0f))
        }
        val current = layers
        if (current.progress.value < 1f) {
            val remainingMs = (durationMs * (1f - current.progress.value)).roundToInt().coerceAtLeast(1)
            current.progress.animateTo(1f, tween(remainingMs, easing = FastOutSlowInEasing))
            layers = ArtworkLayers(texture, texture)
        }
    }
    return layers
}
