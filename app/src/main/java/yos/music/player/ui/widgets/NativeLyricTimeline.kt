package yos.music.player.ui.widgets

import kotlin.math.abs

/** Presentation time is independent of recomposition and sampled player callbacks. */
internal class NativeLyricClock(initialPositionMs: Float) {
    var positionMs = initialPositionMs
        private set
    private var previousFrameMs = -1L
    private var seekTarget: Float? = null
    private var seekDeadline = 0L

    fun restartFrames() { previousFrameMs = -1L }

    fun seek(position: Float, nowMs: Long) {
        positionMs = position
        seekTarget = position
        seekDeadline = nowMs + 2000L
        previousFrameMs = -1L
    }

    fun frame(nowMs: Long, playing: Boolean, speed: Float, reportedPositionMs: Float?): Float {
        val elapsed = if (previousFrameMs < 0L) 0f else
            (nowMs - previousFrameMs).coerceIn(0L, 100L).toFloat()
        previousFrameMs = nowMs
        val target = seekTarget
        if (target != null) {
            if (reportedPositionMs != null && abs(reportedPositionMs - target) <= 250f || nowMs >= seekDeadline) {
                seekTarget = null
            } else return positionMs
        }
        if (playing) positionMs += elapsed * speed.coerceAtLeast(0f)
        if (reportedPositionMs != null) {
            val error = reportedPositionMs - positionMs
            when {
                abs(error) > 500f -> positionMs = reportedPositionMs
                error > 0f -> positionMs += if (playing) minOf(error * 0.2f, 12f) else error
            }
        }
        return positionMs
    }
}

internal data class NativeWordMotion(val startMs: Float, val endMs: Float, val leadMs: Float) {
    // Adjacent notes overlap even when the first note lasts several seconds.
    // The subtle continuation overlaps the main rise even for a held final word.
    // Waiting until endMs - 180 would leave a visibly stationary middle plateau.
    fun uplift(timeMs: Float): Float {
        val duration = (endMs - startMs + leadMs).coerceIn(360f, 650f)
        val riseStart = startMs - leadMs
        val rise = nativeSmoothStep((timeMs - riseStart) / duration)
        val finishStart = minOf(endMs - 180f, riseStart + duration - 160f)
        val finishDuration = (endMs + 240f - finishStart).coerceAtLeast(420f)
        val finish = nativeSmoothStep((timeMs - finishStart) / finishDuration)
        return 0.72f * rise + 0.28f * finish
    }

    fun highlight(timeMs: Float): Float =
        ((timeMs - startMs) / (endMs - startMs).coerceAtLeast(1f)).coerceIn(0f, 1f)
}

internal fun nativeSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Feather enters from outside the glyph, so wrap/space boundaries cannot flash bright. */
internal fun nativeHighlightCoverage(progress: Float, relativeX: Float, feather: Float): Float {
    val width = feather.coerceAtLeast(0.01f)
    val edge = -width + progress.coerceIn(0f, 1f) * (1f + 2f * width)
    return nativeSmoothStep((edge + width - relativeX) / (2f * width))
}

/** One continuous field spans a visual line, including the front's entry and exit. */
internal fun nativeLineHighlightEdge(left: Float, right: Float, cursor: Float, feather: Float): Float {
    val fraction = ((cursor - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
    return left - feather + fraction * (right - left + 2f * feather)
}

/** Line emphasis follows playback time, independently of focus/scroll spring state. */
internal data class NativeLineHighlight(val startMs: Float, val nextStartMs: Float?) {
    fun activeAt(timeMs: Float): Boolean = timeMs >= startMs &&
        (nextStartMs == null || nextStartMs <= startMs || timeMs < nextStartMs)

    // Keep quiet rows cached, but never clamp away the next line's time boundary.
    fun renderTime(timeMs: Float, window: Pair<Float, Float>): Float {
        val clamped = timeMs.coerceIn(window.first, window.second)
        val end = nextStartMs
        return if (end != null && end > startMs && timeMs >= end) maxOf(clamped, end) else clamped
    }
}

/** Scrolling, focus and line colour share this boundary. Countdown has no preroll. */
internal fun nativeLyricVisualStartMs(startMs: Float, countdown: Boolean = false): Float =
    startMs - if (countdown) 0f else LYRIC_LINE_PREROLL_MS

internal fun nativePlaybackLineHighlight(startMs: Float, nextStartMs: Float?, nextIsCountdown: Boolean = false): NativeLineHighlight =
    NativeLineHighlight(nativeLyricVisualStartMs(startMs), nextStartMs?.let { nativeLyricVisualStartMs(it, nextIsCountdown) })
