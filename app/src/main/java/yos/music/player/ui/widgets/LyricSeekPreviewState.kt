package yos.music.player.ui.widgets

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A visual seek preview; it never changes the player or the published lyric position. */
@Stable
class LyricSeekPreviewState {
    var positionMs: Float? by mutableStateOf(null)
        private set
    internal var committedSeek: LyricSeekCommit? by mutableStateOf(null)
        private set

    fun update(positionMs: Float?) { this.positionMs = positionMs }

    fun commit(positionMs: Float) {
        // Retain the command after the pointer preview clears, even when UP and the
        // next playback frame arrive before the lyrics renderer gets a frame.
        committedSeek = LyricSeekCommit(positionMs, (committedSeek?.revision ?: 0) + 1)
    }
}

internal data class LyricSeekCommit(val positionMs: Float, val revision: Int)
