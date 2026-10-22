package yos.music.player.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WavyLyricContinuityTest {
    @Test fun fractionalPreviewMovesWithinOneLineImmediatelyAndReversibly() {
        val state = column()
        val anchor = state.viewportHeightPx * 0.0618f
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f, 0.75f, 0.25f, 0f)) {
            state.previewAt(LyricSeekScrollTarget(3, 4, fraction))
            assertEquals(state.rowTop(3) + state.rowHeight(3) * fraction - anchor, state.scrollOffsetPx, 0.001f)
            assertEquals(0f, state.waveCumulativeTravelPx, 0f)
        }
        state.previewAt(LyricSeekScrollTarget(3, 4, 1f))
        val boundary = state.scrollOffsetPx
        state.previewAt(LyricSeekScrollTarget(4, 5, 0f))
        assertEquals(boundary, state.scrollOffsetPx, 0f)
    }

    @Test fun fractionalPreviewUsesMeasuredHeightIncludingTranslationAndCountdownChanges() {
        val state = column()
        val target = LyricSeekScrollTarget(3, 4, 0.5f)
        state.previewAt(target)
        state.updateRowHeight(3, 260)
        assertEquals(state.rowTop(3) + 130f - state.viewportHeightPx * 0.0618f, state.scrollOffsetPx, 0.001f)
        state.updateRowHeight(1, 0)
        assertEquals(state.rowTop(3) + 130f - state.viewportHeightPx * 0.0618f, state.scrollOffsetPx, 0.001f)
        val held = state.scrollOffsetPx
        repeat(10) { state.previewAt(target) }
        assertEquals(held, state.scrollOffsetPx, 0f)
    }

    @Test fun releasingFractionalPreviewHandsBackToPlaybackWithoutASnap() {
        val state = column()
        state.previewAt(LyricSeekScrollTarget(7, 8, 0.75f))
        val atRelease = state.scrollOffsetPx
        state.settleSeekPreviewTo(7, 30L)
        assertEquals(atRelease, state.scrollOffsetPx, 0f)
        assertTrue(state.isSeekScrollActive)
        state.settleSeekPreviewTo(7, 75L)
        assertTrue(state.scrollOffsetPx < atRelease)
        state.settleSeekPreviewTo(7, 120L)
        assertFalse(state.isSeekScrollActive)
        val settled = state.scrollOffsetPx
        state.followTo(7, animateRows = true)
        assertEquals(settled, state.scrollOffsetPx, 0f)
        assertEquals(0f, state.waveCumulativeTravelPx, 0f)
    }

    @Test fun directSeekToTheCurrentAnchorClearsAnUnfinishedWaveAndLaterPlaybackCanAnimate() {
        val state = column()
        state.jumpTo(4, animateRows = true)
        val consumedTravel = state.waveCumulativeTravelPx
        val consumedReset = state.waveResetGeneration
        assertTrue(consumedTravel != 0f)
        state.jumpTo(4, animateRows = false)
        assertEquals(0f, state.waveTravelPx, 0f)
        assertEquals(0f, state.waveCumulativeTravelPx, 0f)
        assertEquals(0f, resolveWaveOffset(18f, consumedTravel, consumedReset,
            state.waveCumulativeTravelPx, state.waveResetGeneration), 0f)
        assertEquals(state.viewportHeightPx * 0.0618f,
            state.rowTop(4) - state.scrollOffsetPx, 0.001f)
        state.jumpTo(5, animateRows = true)
        assertTrue(state.waveTravelPx > 0f)
    }

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
