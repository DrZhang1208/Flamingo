package yos.music.player.ui.widgets

/** A tentative seek: moving into the cancel area never changes the player. */
internal class SeekDragSession(positionMs: Float, private val durationMs: Float, private val widthPx: Float) {
    var positionMs = positionMs.coerceIn(0f, durationMs)
        private set
    var cancelled = false
        private set

    fun move(deltaX: Float, inCancelArea: Boolean, livePositionMs: Float) {
        positionMs = when {
            // Discard both the old tentative seek and all travel inside the arc.
            // The exit event also becomes an anchor, not a horizontal seek delta:
            // it can contain movement sampled on either side of the boundary.
            inCancelArea || cancelled -> livePositionMs.coerceIn(0f, durationMs)
            else -> (positionMs + deltaX / widthPx.coerceAtLeast(1f) * durationMs).coerceIn(0f, durationMs)
        }
        cancelled = inCancelArea
    }

    fun release(): Float? = positionMs.takeUnless { cancelled }
}
