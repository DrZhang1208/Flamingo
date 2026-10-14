package yos.music.player.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WavyLyricContinuityTest {
    @Test
    fun consecutiveTargetsPreservePositionUntilTheRowConsumesThem() {
        val state = column()
        val consumedTravel = state.waveCumulativeTravelPx
        val consumedReset = state.waveResetGeneration
        val springOffset = 18f
        val before = state.rowTop(5) - state.scrollOffsetPx + springOffset

        state.jumpTo(4, animateRows = true)
        state.jumpTo(5, animateRows = true)
        val pendingOffset = resolveWaveOffset(
            springOffset, consumedTravel, consumedReset,
            state.waveCumulativeTravelPx, state.waveResetGeneration
        )
        assertEquals(before, state.rowTop(5) - state.scrollOffsetPx + pendingOffset, 0.001f)

        // Consuming the events does not change the displayed position either.
        val consumedOffset = resolveWaveOffset(
            pendingOffset, state.waveCumulativeTravelPx, state.waveResetGeneration,
            state.waveCumulativeTravelPx, state.waveResetGeneration
        )
        assertEquals(pendingOffset, consumedOffset, 0.001f)
    }

    @Test
    fun resetFollowedByAnotherJumpDoesNotDiscardTheNewAnimation() {
        val state = column()
        val oldReset = state.waveResetGeneration
        state.jumpTo(4, animateRows = true)
        val oldTravel = state.waveCumulativeTravelPx
        state.jumpTo(7, animateRows = false)
        val snappedPosition = state.scrollOffsetPx
        state.jumpTo(8, animateRows = true)

        val pendingOffset = resolveWaveOffset(
            18f, oldTravel, oldReset, state.waveCumulativeTravelPx, state.waveResetGeneration
        )
        assertEquals(state.scrollOffsetPx - snappedPosition, pendingOffset, 0.001f)
    }

    @Test
    fun aNewlyVisibleRowDoesNotReplayPreviousWaves() {
        val state = column()
        state.jumpTo(5, animateRows = true)
        assertEquals(0f, resolveWaveOffset(
            0f, state.waveCumulativeTravelPx, state.waveResetGeneration,
            state.waveCumulativeTravelPx, state.waveResetGeneration
        ), 0f)
    }

    @Test
    fun aRepeatedTargetKeepsAnInFlightWave() {
        val state = column()
        state.jumpTo(4, animateRows = true)
        val generation = state.waveGeneration
        val travel = state.waveCumulativeTravelPx
        state.jumpTo(4, animateRows = true)
        assertEquals(generation, state.waveGeneration)
        assertEquals(travel, state.waveCumulativeTravelPx, 0f)
    }

    @Test
    fun touchingAnAnimatingListDoesNotEraseItsDisplacement() {
        val state = column()
        state.jumpTo(4, animateRows = true)
        val generation = state.waveGeneration
        val travel = state.waveCumulativeTravelPx
        state.startUserScroll()
        assertTrue(state.isUserScrolling)
        assertEquals(generation, state.waveGeneration)
        assertEquals(travel, state.waveCumulativeTravelPx, 0f)
        state.finishUserScroll()
        assertFalse(state.isUserScrolling)
    }

    @Test
    fun measuringDuringDragPreservesTheVisibleRowInsteadOfThePlaybackAnchor() {
        val state = column()
        state.offsetRowBy(200f)
        val visibleY = state.rowTop(4) - state.scrollOffsetPx
        state.updateRowHeight(1, 180)
        assertEquals(visibleY, state.rowTop(4) - state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun removingTheAnchorResetsPendingAnimationForTheNewDocumentSize() {
        val state = column()
        state.jumpTo(8, animateRows = true)
        val oldReset = state.waveResetGeneration
        val oldTravel = state.waveCumulativeTravelPx
        state.updateItemCount(2)
        assertEquals(-1, state.waveAnchorIndex)
        assertEquals(0f, resolveWaveOffset(
            25f, oldTravel, oldReset, state.waveCumulativeTravelPx, state.waveResetGeneration
        ), 0f)
    }

    private fun column() = WavyLyricsColumnState().apply {
        updateItemCount(20)
        updateViewport(1000)
        jumpTo(3, animateRows = false)
    }
}
