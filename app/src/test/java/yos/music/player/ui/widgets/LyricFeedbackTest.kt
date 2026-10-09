package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class LyricFeedbackTest {
    @Test fun releasingPreviewDoesNotAllowPlaybackToPullItBack() {
        val state = WavyLyricsColumnState().apply {
            updateItemCount(30)
            updateViewport(1000)
            jumpTo(3, false)
            offsetRowBy(500f)
            finishUserScroll()
        }
        val position = state.scrollOffsetPx
        val interaction = state.previewInteractionGeneration
        assertTrue(state.isPreviewing)
        assertFalse(state.isUserScrolling)
        state.jumpTo(4, true)
        state.jumpTo(5, true)
        assertEquals(position, state.scrollOffsetPx, 0f)
        assertEquals(interaction, state.previewInteractionGeneration)
        state.resumeFollowing()
        state.jumpTo(5, true)
        assertEquals(5, state.waveAnchorIndex)
        assertFalse(state.isPreviewing)
    }

    @Test fun aSecondGestureRestartsTheIdlePeriod() {
        val state = WavyLyricsColumnState().apply { updateItemCount(20); updateViewport(1000) }
        state.startUserScroll()
        state.finishUserScroll()
        val generation = state.previewInteractionGeneration
        state.startUserScroll()
        assertTrue(state.previewInteractionGeneration > generation)
        assertTrue(state.isUserScrolling)
        state.finishUserScroll()
        assertTrue(state.isPreviewing)
    }

    @Test fun longNoteRisesThenHoldsAndReservesItsFinalMotionForTheNextGlyph() {
        assertEquals(0f, lyricUpliftProgress(1000f, 1000f, 6000f), 0f)
        assertEquals(0.4f, lyricUpliftProgress(1325f, 1000f, 6000f), 0.001f)
        assertEquals(0.8f, lyricUpliftProgress(1650f, 1000f, 6000f), 0f)
        assertEquals(0.8f, lyricUpliftProgress(5000f, 1000f, 6000f), 0f)
        assertEquals(0.9f, lyricUpliftProgress(5840f, 1000f, 6000f), 0.001f)
        assertEquals(1f, lyricUpliftProgress(6000f, 1000f, 6000f), 0f)
    }

    @Test fun shortNotesCanRiseTogetherAcrossTheirBoundary() {
        val previous = lyricUpliftProgress(160f, 0f, 150f)
        val next = lyricUpliftProgress(160f, 150f, 300f)
        assertTrue(previous in 0f..1f && previous < 1f)
        assertTrue(next > 0f)
        var last = 0f
        for (frame in 0..50) {
            val value = lyricUpliftProgress(frame * 16f, 0f, 5000f)
            assertTrue(value >= last)
            assertTrue(value - last < 0.04f)
            last = value
        }
    }

    @Test fun interludeIsHiddenOutsideItsIntervalAndFadesAtBothBoundaries() {
        assertEquals(0f, countdownEmphasis(999f, 1000f, 8000f), 0f)
        assertEquals(0f, countdownEmphasis(1000f, 1000f, 8000f), 0f)
        assertTrue(countdownEmphasis(1001f, 1000f, 8000f) in 0f..0.001f)
        assertEquals(0.5f, countdownEmphasis(1180f, 1000f, 8000f), 0.001f)
        assertEquals(1f, countdownEmphasis(2000f, 1000f, 8000f), 0f)
        assertEquals(0.5f, countdownEmphasis(7840f, 1000f, 8000f), 0.001f)
        assertTrue(countdownEmphasis(7999f, 1000f, 8000f) in 0f..0.001f)
        assertEquals(0f, countdownEmphasis(8000f, 1000f, 8000f), 0f)
        assertEquals(0f, countdownEmphasis(9000f, 1000f, 8000f), 0f)
        assertEquals(0f, countdownEmphasis(1000f, 1000f, 1000f), 0f)
    }
}
