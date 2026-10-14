package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class NativeCountdownExitTest {
    private val start = 1000f
    private val end = 12000f
    private val handoff = nativeLyricVisualStartMs(end)

    @Test fun markerIsInvisibleBeforeTheLastPixelsOfItsSlotCollapse() {
        val fadeEnd = handoff - 120f
        assertEquals(0f, countdownEmphasis(fadeEnd, start, end), 0f)
        assertTrue(countdownLayoutFraction(fadeEnd, start, end) > 0f)
        assertTrue(countdownScale(fadeEnd, start, end) > 0.5f)
        assertEquals(0f, countdownLayoutFraction(handoff, start, end), 0f)
    }

    @Test fun exitNeverReversesAndDoesNotRestartWhenTheNextLyricTakesFocus() {
        val state = WavyLyricsColumnState().apply {
            updateItemCount(4)
            updateViewport(1000)
            updateRowHeights(mapOf(0 to 100, 1 to 64, 2 to 100, 3 to 100))
            jumpTo(1, false)
        }
        var previousY = Float.POSITIVE_INFINITY
        var previousAlpha = 1f
        var previousScale = 1f
        for (time in (end.toInt() - 1100)..(end.toInt() + 100) step 4) {
            val ms = time.toFloat()
            state.updateRowHeight(1, (64f * countdownLayoutFraction(ms, start, end)).toInt())
            if (ms >= handoff) state.jumpTo(2, true)
            val y = state.rowTop(2) - state.scrollOffsetPx
            val alpha = countdownEmphasis(ms, start, end)
            val scale = countdownScale(ms, start, end)
            assertTrue("Next lyric moved down at $ms: $previousY -> $y", y <= previousY + 0.001f)
            assertTrue(alpha <= previousAlpha)
            assertTrue(scale <= previousScale)
            previousY = y; previousAlpha = alpha; previousScale = scale
        }
        assertEquals("Slot removal must not cause a second scroll wave", 0f, state.waveTravelPx, 0f)
    }

    @Test fun seekAndReentryResolveFromPlaybackWithoutRememberingAnExit() {
        assertEquals(0f, countdownLayoutFraction(end + 100f, start, end), 0f)
        assertEquals(1f, countdownLayoutFraction(5000f, start, end), 0f)
        assertEquals(1f, countdownEmphasis(5000f, start, end), 0f)
        for (time in listOf(start - 1f, start, end, end + 1f)) {
            assertEquals(0f, countdownEmphasis(time, start, end), 0f)
            assertEquals(0f, countdownLayoutFraction(time, start, end), 0f)
        }
        assertEquals(0f, countdownLayoutFraction(5000f, start, start), 0f)
    }

    @Test fun simultaneousMeasurementsPreserveOneAnchorForTheEntireBatch() {
        val state = WavyLyricsColumnState().apply {
            updateItemCount(20)
            updateViewport(1000)
            jumpTo(10, false)
        }
        val y = state.rowTop(10) - state.scrollOffsetPx
        val generation = state.waveGeneration
        state.updateRowHeights(mapOf(2 to 160, 3 to 0, 8 to 128))
        assertEquals(y, state.rowTop(10) - state.scrollOffsetPx, 0.001f)
        assertEquals(generation, state.waveGeneration)
    }

    @Test fun seekingOutOfAnActiveCountdownRetainsBothForwardAndBackwardTravel() {
        for (target in listOf(2, 4)) {
            val state = WavyLyricsColumnState().apply {
                updateItemCount(8); updateViewport(1000)
                updateRowHeights((0..7).associateWith { if (it == 3) 64 else 100 })
                jumpTo(3, false)
            }
            val before = state.rowTop(target) - state.scrollOffsetPx
            val oldTravel = state.waveCumulativeTravelPx
            val oldReset = state.waveResetGeneration
            state.jumpTo(target, true)
            state.updateRowHeight(3, 0)
            val displacement = resolveWaveOffset(0f, oldTravel, oldReset,
                state.waveCumulativeTravelPx, state.waveResetGeneration)
            assertTrue("Seek must retain nonzero motion", kotlin.math.abs(displacement) > 1f)
            assertEquals("Collapsing dots must not consume the target lyric's animation",
                before, state.rowTop(target) - state.scrollOffsetPx + displacement, 0.001f)
        }
    }

    @Test fun openingCountdownSeekRetainsRenderedMotionAfterTheSlotCollapses() {
        val state = WavyLyricsColumnState(setOf(0)).apply {
            updateItemCount(4); updateViewport(1000)
            updateRowHeights(mapOf(0 to 64, 1 to 100, 2 to 100, 3 to 100))
            jumpTo(0, false)
        }
        val before = state.rowTop(1) - state.scrollOffsetPx
        val travel = state.waveCumulativeTravelPx
        val reset = state.waveResetGeneration
        state.jumpTo(1, true)
        state.updateRowHeight(0, 0)
        val raw = resolveWaveOffset(0f, travel, reset,
            state.waveCumulativeTravelPx, state.waveResetGeneration)
        assertEquals("The first-row bound must retain the removed countdown's visual space",
            before, state.rowTop(1) - state.scrollOffsetPx + state.boundedDisplacement(raw), 0.001f)
        // A finger taking over still gets the ordinary hard start boundary.
        state.offsetRowBy(-100_000f)
        assertEquals(0f, state.boundedDisplacement(raw), 0f)
    }
}
