package yos.music.player.ui.widgets

import kotlin.math.abs

/** Non-overshooting handoff after the pointer releases; dragging never uses this tween. */
internal class LyricSeekScrollMotion {
    private var start = 0f
    private var target = 0f
    private var startedAt = -1L
    var finished = true
        private set

    fun frame(currentOffset: Float, targetOffset: Float, nowMs: Long): Float {
        if (startedAt < 0 || abs(targetOffset - target) > 0.5f) {
            start = currentOffset
            target = targetOffset
            startedAt = nowMs
        }
        val progress = ((nowMs - startedAt) / 90f).coerceIn(0f, 1f)
        finished = progress >= 1f || abs(target - start) < 0.5f
        return if (finished) target else start + (target - start) * nativeSmoothStep(progress)
    }

    fun shiftBy(delta: Float) {
        start += delta
        target += delta
    }
}
