package yos.music.player.ui.widgets

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/** System volume is discrete; the pointer's visual position is continuous. */
@Stable
internal class VolumeSliderState(initialStep: Int, val maxVolume: Int) {
    private var actualStep = initialStep.coerceIn(0, maxVolume.coerceAtLeast(0))
    private var requestedStep = actualStep
    private val motion = SeekProgressReturnMotion()
    var position by mutableFloatStateOf(normalized(actualStep))
        private set
    var dragging by mutableStateOf(false)
        private set
    var animating by mutableStateOf(false)
        private set

    fun begin() {
        motion.reset()
        animating = false
        dragging = true
        requestedStep = actualStep
    }

    fun dragBy(fraction: Float): Int? {
        if (!dragging || maxVolume <= 0) return null
        position = (position + fraction).coerceIn(0f, 1f)
        val step = (position * maxVolume).roundToInt().coerceIn(0, maxVolume)
        if (step == requestedStep) return null
        requestedStep = step
        return step
    }

    fun report(step: Int, nowMs: Long) {
        val next = step.coerceIn(0, maxVolume.coerceAtLeast(0))
        if (next == actualStep) return
        actualStep = next
        if (!dragging) startMotion(nowMs)
    }

    fun finish(currentSystemStep: Int, nowMs: Long) {
        actualStep = currentSystemStep.coerceIn(0, maxVolume.coerceAtLeast(0))
        dragging = false
        startMotion(nowMs)
    }

    fun frame(nowMs: Long) {
        if (dragging) return
        position = motion.frame(nowMs, normalized(actualStep))
        animating = motion.active
    }

    private fun startMotion(nowMs: Long) {
        motion.start(position, nowMs)
        animating = true
    }

    private fun normalized(step: Int) = if (maxVolume > 0) step / maxVolume.toFloat() else 0f
}
