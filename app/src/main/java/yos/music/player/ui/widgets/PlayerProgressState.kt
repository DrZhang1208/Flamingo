package yos.music.player.ui.widgets

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** Retained above hidden controls; only automatic updates use a catch-up animation. */
@Stable
internal class PlayerProgressState(initialPositionMs: Float, initialDurationMs: Long) {
    private val clock = NativeLyricClock(initialPositionMs)
    private val seek = SeekProgressPresentation(initialPositionMs)
    private val liveMotion = SeekProgressReturnMotion()
    private var resumePending = true
    private var lastLiveMs = initialPositionMs
    var positionMs by mutableFloatStateOf(initialPositionMs)
        private set
    var durationMs by mutableLongStateOf(initialDurationMs)
    var dragging by mutableStateOf(false)
        private set
    val livePositionMs: Float get() = clock.positionMs
    val isSeekTransitioning: Boolean get() = seek.isTransitioning

    fun restartFrames() {
        clock.restartFrames()
        resumePending = true
    }

    fun frame(nowMs: Long, playing: Boolean, speed: Float, report: Float?): Float {
        val live = bounded(clock.frame(nowMs, playing, speed, report))
        val target = seek.frame(nowMs, live)
        if (dragging || seek.isTransitioning) {
            liveMotion.reset()
            positionMs = bounded(target)
        } else {
            if (resumePending || abs(live - lastLiveMs) > 500f) liveMotion.start(positionMs, nowMs)
            positionMs = bounded(liveMotion.frame(nowMs, target))
        }
        resumePending = false
        lastLiveMs = live
        return positionMs
    }

    fun begin() {
        liveMotion.reset()
        seek.begin()
        dragging = true
    }

    fun preview(position: Float, cancelled: Boolean, nowMs: Long): Float {
        positionMs = seek.preview(positionMs, position, cancelled, nowMs)
        return positionMs
    }

    fun finish(committedMs: Float?, nowMs: Long): Float {
        positionMs = seek.finish(positionMs, committedMs, nowMs)
        if (committedMs != null) {
            clock.seek(committedMs, nowMs)
            lastLiveMs = committedMs
        }
        dragging = false
        resumePending = false
        liveMotion.reset()
        return positionMs
    }

    private fun bounded(value: Float) = if (durationMs > 0L) value.coerceIn(0f, durationMs.toFloat()) else value.coerceAtLeast(0f)

    companion object {
        val Saver = listSaver<PlayerProgressState, Any>(
            save = { listOf(it.positionMs, it.durationMs) },
            restore = { PlayerProgressState((it[0] as Number).toFloat(), (it[1] as Number).toLong()) }
        )
    }
}
