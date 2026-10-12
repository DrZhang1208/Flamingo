package yos.music.player.code.utils.player

import org.junit.Assert.*
import org.junit.Test

class AudioFocusResumePolicyTest {
    private fun interrupted() = AudioFocusResumePolicy().apply {
        explicitCommand(true)
        playWhenReadyChanged(true, false, "song", false)
        playWhenReadyChanged(false, true, "song", false)
    }

    @Test fun returningAfterFocusLossResumesSameSongOnce() {
        val policy = interrupted()
        assertTrue(policy.consumeForegroundResume("song", true, false, false))
        assertFalse(policy.consumeForegroundResume("song", true, false, false))
    }

    @Test fun manualPauseOrStopEvenWhileAlreadyPausedCancelsRecovery() {
        val policy = interrupted()
        policy.explicitCommand(false)
        assertFalse(policy.consumeForegroundResume("song", true, false, false))
    }

    @Test fun aNormalPauseAndColdStartNeverResumeOnForeground() {
        val policy = AudioFocusResumePolicy()
        assertFalse(policy.consumeForegroundResume("song", true, false, false))
        policy.explicitCommand(true)
        policy.playWhenReadyChanged(false, false, "song", false)
        assertFalse(policy.consumeForegroundResume("song", true, false, false))
    }

    @Test fun pauseFadeCannotBeOverriddenByFocusRecovery() {
        val policy = AudioFocusResumePolicy().apply { explicitCommand(true) }
        policy.playWhenReadyChanged(false, true, "song", true)
        assertFalse(policy.consumeForegroundResume("song", true, false, false))
    }

    @Test fun changedTrackDoesNotResumeAnOldInterruption() {
        val policy = interrupted()
        policy.trackChanged("other")
        policy.trackChanged("song")
        assertFalse(policy.consumeForegroundResume("song", true, false, false))
    }

    @Test fun disablingFocusEndedTrackAndAlreadyPlayingDoNotResume() {
        assertFalse(interrupted().consumeForegroundResume("song", false, false, false))
        assertFalse(interrupted().consumeForegroundResume("song", true, false, true))
        assertFalse(interrupted().consumeForegroundResume("song", true, true, false))
    }

    @Test fun transientSuppressionKeepsPlaybackIntentUnderMedia3Control() {
        val policy = AudioFocusResumePolicy().apply {
            explicitCommand(true)
            playWhenReadyChanged(true, false, "song", false)
        }
        assertFalse(policy.consumeForegroundResume("song", true, true, false))
    }
}
