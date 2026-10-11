package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class NativeLineHighlightTest {
    @Test fun playbackHighlightAndFollowStartOnTheSamePrerollFrame() {
        val start = 2000f
        val boundary = nativeLyricVisualStartMs(start)
        val previous = nativePlaybackLineHighlight(1000f, start)
        val current = nativePlaybackLineHighlight(start, 3000f)
        assertFalse(current.activeAt(boundary - 1f))
        assertTrue(previous.activeAt(boundary - 1f))
        assertTrue(current.activeAt(boundary))
        assertFalse(previous.activeAt(boundary))
        assertEquals(1760f, boundary, 0f)
        // Seeking backward and forward evaluates the same boundary directly.
        assertFalse(current.activeAt(1500f))
        assertTrue(current.activeAt(2100f))
    }
    @Test fun enteringCountdownDoesNotPrematurelyDimThePreviousLine() {
        val previous = nativePlaybackLineHighlight(1000f, 4000f, nextIsCountdown = true)
        assertTrue(previous.activeAt(3999f))
        assertFalse(previous.activeAt(4000f))
        assertEquals(4000f, nativeLyricVisualStartMs(4000f, countdown = true), 0f)
    }

    @Test fun lineLightsAtItsTimestampWhileScrollingIsStillInFlight() {
        val motion = NativeLyricScrollMotion()
        val params = WavySpringParams(0.9f, 24f, 170f)
        motion.register("line", 4, params, 0f, 0)
        motion.retarget(900f, 0, 4, 0L)
        motion.frame(100_000_000L)
        assertTrue(motion.displacement("line", 900f, 0) > 1f)
        val highlight = NativeLineHighlight(1000f, 2000f)
        assertFalse(highlight.activeAt(999.9f))
        assertTrue(highlight.activeAt(1000f))
    }

    @Test fun prerollDoesNotDimTheCurrentLineAndSeeksApplyImmediatelyBothWays() {
        val current = NativeLineHighlight(1000f, 2000f)
        val next = NativeLineHighlight(2000f, 3000f)
        assertTrue(current.activeAt(1760f))
        assertFalse(next.activeAt(1760f))
        assertFalse(current.activeAt(2000f))
        assertTrue(next.activeAt(2000f))
        assertTrue(current.activeAt(1500f))
        assertFalse(next.activeAt(1500f))
    }

    @Test fun cachedRowsStillDimAtTheNextTimestampAcrossALongGap() {
        val line = NativeLineHighlight(1000f, 10000f)
        val window = 0f to 2000f
        assertEquals(2000f, line.renderTime(9000f, window), 0f)
        assertTrue(line.activeAt(line.renderTime(9999f, window)))
        assertFalse(line.activeAt(line.renderTime(10000f, window)))
        assertTrue(line.activeAt(line.renderTime(1500f, window)))
    }

    @Test fun lastLineRemainsHighlightedWithoutInventingAnEndTime() {
        val last = NativeLineHighlight(1000f, null)
        assertTrue(last.activeAt(1000f))
        assertTrue(last.activeAt(300000f))
        assertFalse(last.activeAt(0f))
        assertTrue(NativeLineHighlight(1000f, 1000f).activeAt(1500f))
    }
}
