package yos.music.player.code.utils.lrc

import org.junit.Assert.*
import org.junit.Test
import yos.music.player.code.utils.player.shouldPublishPlaybackState

class PlaybackVisualStateTest {
    @Test fun bufferCompletionCannotUndoAnExplicitPauseWhileVolumeFadesOut() {
        assertFalse(shouldPublishPlaybackState(true, true, pausePending = true))
        assertTrue(shouldPublishPlaybackState(false, false, pausePending = false))
    }

    @Test fun switchingTracksDoesNotShrinkArtworkButResumingStillExpandsIt() {
        assertFalse(shouldPublishPlaybackState(false, true, pausePending = false))
        assertTrue(shouldPublishPlaybackState(true, true, pausePending = false))
    }
}
