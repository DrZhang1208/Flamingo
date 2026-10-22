package yos.music.player.ui.widgets

/** Animate either way across the cancel boundary, following a moving destination. */
internal class SeekProgressReturnMotion {
    private var fromMs = 0f
    private var startMs = 0L
    var active = false
        private set

    fun start(displayedMs: Float, nowMs: Long) {
        fromMs = displayedMs
        startMs = nowMs
        active = true
    }

    fun reset() { active = false }

    fun frame(nowMs: Long, targetMs: Float): Float {
        if (!active) return targetMs
        val progress = ((nowMs - startMs) / 280f).coerceIn(0f, 1f)
        if (progress >= 1f) { active = false; return targetMs }
        // Blend toward the latest destination rather than adding its delta to every frame.
        // Pointer movement during re-entry must not cause an immediate visual jump.
        return fromMs + (targetMs - fromMs) * nativeSmoothStep(progress)
    }
}

/** Keeps the tentative seek separate from both the live clock and displayed progress. */
internal class SeekProgressPresentation(initialLiveMs: Float) {
    private val motion = SeekProgressReturnMotion()
    val isTransitioning: Boolean get() = motion.active
    private var liveMs = initialLiveMs
    private var previewMs = initialLiveMs
    private var dragging = false
    private var cancelled = false

    fun begin() {
        motion.reset()
        dragging = true
        cancelled = false
    }

    fun preview(displayedMs: Float, positionMs: Float, inCancelArea: Boolean, nowMs: Long): Float {
        previewMs = positionMs
        if (inCancelArea != cancelled) motion.start(displayedMs, nowMs)
        cancelled = inCancelArea
        return motion.frame(nowMs, if (cancelled) liveMs else previewMs)
    }

    fun finish(displayedMs: Float, committedMs: Float?, nowMs: Long): Float {
        if (committedMs == null) {
            if (!cancelled) motion.start(displayedMs, nowMs)
        } else {
            // Keep an unfinished re-entry moving to the committed seek, even after UP.
            liveMs = committedMs
        }
        dragging = false
        cancelled = false
        return motion.frame(nowMs, liveMs)
    }

    fun frame(nowMs: Long, livePositionMs: Float): Float {
        liveMs = livePositionMs
        return motion.frame(nowMs, if (dragging && !cancelled) previewMs else liveMs)
    }
}
