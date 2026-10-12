package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class WavyLyricScrollBoundsTest {
    private fun column(vararg heights: Int) = WavyLyricsColumnState().apply {
        updateItemCount(heights.size)
        updateViewport(800)
        heights.forEachIndexed { index, height -> updateRowHeight(index, height) }
    }

    @Test fun repeatedDraggingAtEitherEndCannotMoveTheLyricsOffscreen() {
        val state = column(*IntArray(20) { 100 })
        state.offsetRowBy(100_000f)
        assertEquals(800f * 0.0618f, state.rowTop(19) - state.scrollOffsetPx, 0.001f)
        repeat(30) { assertFalse(state.offsetRowBy(1000f)) }
        state.offsetRowBy(-100_000f)
        assertEquals(800f * 0.0618f, state.rowTop(0) - state.scrollOffsetPx, 0.001f)
        repeat(30) { assertFalse(state.offsetRowBy(-1000f)) }
    }

    @Test fun shortSingleLineHasNoEmptyScrollRange() {
        val state = column(100)
        val initial = state.scrollOffsetPx
        repeat(10) {
            assertFalse(state.offsetRowBy(1000f))
            assertFalse(state.offsetRowBy(-1000f))
            assertEquals(initial, state.scrollOffsetPx, 0f)
        }
    }

    @Test fun draggingToTheStartDuringAForwardWaveCannotPushTheFirstRowBelowTheScreen() {
        val state = column(*IntArray(60) { 100 })
        val motion = NativeLyricScrollMotion()
        val params = WavySpringParams(0.9f, 15f, 90f)
        state.jumpTo(0, false)
        motion.settle(state.waveCumulativeTravelPx, state.waveResetGeneration, 0)
        motion.register("first", 0, params, state.waveCumulativeTravelPx, state.waveResetGeneration)
        state.jumpTo(40, true)
        motion.retarget(state.waveCumulativeTravelPx, state.waveResetGeneration, 40, 0L)
        motion.frame(80_000_000L)
        state.offsetRowBy(-100_000f)
        val raw = motion.displacement("first", state.waveCumulativeTravelPx, state.waveResetGeneration)
        assertTrue("Old logical-only bound misses a whole-screen displacement", raw > state.viewportHeightPx)
        repeat(30) { frame ->
            assertFalse(state.offsetRowBy(-1000f))
            motion.frame(80_000_000L + frame * 8_333_333L)
            val shift = state.boundedDisplacement(motion.displacement("first",
                state.waveCumulativeTravelPx, state.waveResetGeneration))
            assertEquals(800f * 0.0618f, state.rowTop(0) - state.scrollOffsetPx + shift, 0.001f)
        }
    }

    @Test fun renderedBoundsAlsoContainBackwardWavesAtTheEndWithoutChangingInteriorMotion() {
        val state = column(*IntArray(60) { 100 })
        state.offsetRowBy(2000f)
        assertEquals(150f, state.boundedDisplacement(150f), 0.001f)
        assertEquals(-150f, state.boundedDisplacement(-150f), 0.001f)
        state.offsetRowBy(100_000f)
        assertEquals(0f, state.boundedDisplacement(-5000f), 0.001f)
        state.offsetRowBy(-100_000f)
        assertEquals(0f, state.boundedDisplacement(5000f), 0.001f)
    }

    @Test fun longParagraphCanStillBeReadThroughItsLastVisualLine() {
        for (heights in listOf(intArrayOf(2000), intArrayOf(100, 2000))) {
            val state = column(*heights)
            state.offsetRowBy(100_000f)
            val bottom = state.rowTop(heights.size) - state.scrollOffsetPx
            assertEquals(800f * (1f - 0.0618f), bottom, 0.001f)
            assertFalse(state.offsetRowBy(1000f))
        }
    }

    @Test fun collapsedTrailingRowsDoNotAllowScrollingPastTheLastVisibleLyric() {
        val state = column(100, 100, 100, 0, 0)
        state.offsetRowBy(100_000f)
        assertEquals(800f * 0.0618f, state.rowTop(2) - state.scrollOffsetPx, 0.001f)
        assertFalse(state.offsetRowBy(1000f))
        state.updateRowHeight(2, 0)
        assertEquals(800f * 0.0618f, state.rowTop(1) - state.scrollOffsetPx, 0.001f)
        assertFalse(state.offsetRowBy(1000f))
    }

    @Test fun resizingOrReplacingTheDocumentReclampsTheBoundary() {
        val state = column(*IntArray(20) { 100 })
        state.offsetRowBy(100_000f)
        state.updateViewport(400)
        state.offsetRowBy(100_000f)
        assertEquals(400f * 0.0618f, state.rowTop(19) - state.scrollOffsetPx, 0.001f)
        state.updateItemCount(2)
        assertEquals(400f * 0.0618f, state.rowTop(1) - state.scrollOffsetPx, 0.001f)
        state.updateRowHeight(0, 0)
        state.updateRowHeight(1, 0)
        assertFalse(state.offsetRowBy(1000f))
        state.updateItemCount(0)
        assertFalse(state.offsetRowBy(1000f))
    }
}
