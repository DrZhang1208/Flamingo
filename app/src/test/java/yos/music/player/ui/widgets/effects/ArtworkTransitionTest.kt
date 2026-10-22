package yos.music.player.ui.widgets.effects

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkTransitionTest {
    @Test fun openingAndClosingFreezeAtEveryIntermediateProgress() {
        for (playing in listOf(false, true)) {
            for (progress in listOf(0.01f, 0.4f, 0.99f)) {
                assertEquals(ArtworkTransitionMode.HOLD, artworkTransitionMode(progress, playing, true))
            }
        }
    }

    @Test fun endpointsAcceptNewArtworkEvenWhenPlaybackIsPaused() {
        assertEquals(ArtworkTransitionMode.HIDDEN, artworkTransitionMode(0f, false, true))
        assertEquals(ArtworkTransitionMode.HIDDEN, artworkTransitionMode(0f, true, true))
        assertEquals(ArtworkTransitionMode.STILL, artworkTransitionMode(1f, false, true))
        assertEquals(ArtworkTransitionMode.ANIMATE, artworkTransitionMode(1f, true, true))
    }

    @Test fun backgroundingNeverStartsABlend() {
        for (progress in listOf(0f, 0.4f, 1f)) {
            assertEquals(ArtworkTransitionMode.HOLD, artworkTransitionMode(progress, true, false))
        }
    }
}
