package yos.music.player.ui.widgets.basic

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln

/** Continuous resistance: no hard stop, and no layout work during a drag. */
internal fun dialogElasticOffset(pull: Float, limit: Float): Float =
    -limit * (1f - exp(-pull.coerceAtLeast(0f) * 0.32f / limit.coerceAtLeast(1f)))

internal class DialogElasticMotion(private val scope: CoroutineScope, private val limit: Float) {
    var offset by mutableFloatStateOf(0f)
        private set
    private var pull = 0f
    private var settling: Job? = null

    fun startGesture() {
        settling?.cancel()
        // An interrupted return starts exactly at its current visual position.
        pull = -limit / 0.32f * ln((1f + offset / limit).coerceAtLeast(0.0001f))
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
