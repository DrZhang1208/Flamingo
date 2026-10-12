package yos.music.player.code.utils.player

/** Remembers only a focus interruption, never a normal pause or a persisted pause. */
internal class AudioFocusResumePolicy {
    private var requested = false
    private var interruptedTrack: String? = null

    fun explicitCommand(play: Boolean) {
        requested = play
        interruptedTrack = null
    }

    fun playWhenReadyChanged(ready: Boolean, focusLoss: Boolean, track: String?, pausePending: Boolean) {
        interruptedTrack = if (!ready && focusLoss && requested && !pausePending) track else null
        requested = ready
    }

    fun trackChanged(track: String?) {
        if (interruptedTrack != track) interruptedTrack = null
    }

    fun consumeForegroundResume(track: String?, enabled: Boolean, ready: Boolean, ended: Boolean): Boolean {
        val pending = interruptedTrack
        interruptedTrack = null
        return pending != null && pending == track && enabled && !ready && !ended
    }
}
