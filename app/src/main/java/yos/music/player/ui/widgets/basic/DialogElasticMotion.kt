package yos.music.player.ui.widgets.basic

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Continuous resistance: no hard stop, and no layout work during a drag. */
internal fun dialogElasticOffset(pull: Float, limit: Float): Float {
    val distance = pull.coerceAtLeast(0f)
    val range = limit.coerceAtLeast(1f)
    // A responsive start, with resistance that increases gradually instead of
    // saturating after a short pull. The inverse below preserves interruptions.
    return -range * distance / (distance + range / 0.5f)
}

internal class DialogElasticMotion(private val scope: CoroutineScope, private val limit: Float) {
    var offset by mutableFloatStateOf(0f)
        private set
    private var pull = 0f
    private var settling: Job? = null

    fun startGesture() {
        settling?.cancel()
        // An interrupted return starts exactly at its current visual position.
        pull = -offset * (limit / 0.5f) / (limit + offset).coerceAtLeast(0.0001f)
    }

    fun drag(delta: Float): Float {
        val before = pull
        pull = (pull - delta).coerceAtLeast(0f)
        offset = dialogElasticOffset(pull, limit)
        return before - pull
    }

    fun release() {
        if (offset == 0f) return
        settling?.cancel()
        settling = scope.launch {
            animate(offset, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = 380f)) { value, _ ->
                offset = value
            }
            offset = 0f
            pull = 0f
        }
    }
}
