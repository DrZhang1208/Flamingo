package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class NativeLineHighlightTest {
    @Test fun adjacentLinesCrossfadeWithoutAFirstFrameBrightnessJump() {
        val previous = nativePlaybackLineHighlight(1000f, 4000f)
        val next = nativePlaybackLineHighlight(4000f, 7000f)
        val boundary = next.startMs
        assertEquals(1f, previous.strengthAt(boundary), 0f)
        assertEquals(0f, next.strengthAt(boundary), 0f)
        var last = 0f
        for (elapsed in 0..280 step 4) {
            val time = boundary + elapsed
            val incoming = next.strengthAt(time)
            assertTrue(incoming >= last)
            assertTrue("No single frame may flash to full brightness", incoming - last < 0.025f)
            assertEquals(1f, previous.strengthAt(time) + incoming, 0.0001f)
            last = incoming
        }
        assertEquals(0.5f, next.strengthAt(boundary + 140f), 0.0001f)
        assertEquals(1f, last, 0f)
    }

    @Test fun cachedLongGapStillRendersEveryOutgoingFadeFrame() {
        val line = NativeLineHighlight(1000f, 10000f)
        val window = 0f to 2000f
        assertEquals(1f, line.strengthAt(line.renderTime(10000f, window)), 0f)
        assertEquals(0.5f, line.strengthAt(line.renderTime(10140f, window)), 0.0001f)
        assertEquals(0f, line.strengthAt(line.renderTime(10280f, window)), 0f)
        assertEquals(10280f, line.renderTime(300000f, window), 0f)
        // Direct time evaluation must also restore the fade correctly on reverse scrubs.
        assertEquals(0.5f, line.strengthAt(line.renderTime(10140f, window)), 0.0001f)
        assertEquals(1f, line.strengthAt(line.renderTime(10000f, window)), 0f)
    }

    @Test fun shortLinesAndMissingEndTimesHaveBoundedContinuousBrightness() {
        val short = NativeLineHighlight(1000f, 1100f)
        var previous = short.strengthAt(999f)
        for (time in 1000..1500) {
            val strength = short.strengthAt(time.toFloat())
            assertTrue(strength in 0f..1f)
            assertTrue(kotlin.math.abs(strength - previous) < 0.01f)
            previous = strength
        }
        assertEquals(0f, previous, 0f)
        for (end in listOf(null, 1000f, 500f)) {
            val last = NativeLineHighlight(1000f, end)
            assertEquals(0f, last.strengthAt(1000f), 0f)
            assertEquals(0.5f, last.strengthAt(1140f), 0.0001f)
            assertEquals(1f, last.strengthAt(300000f), 0f)
        }
    }

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
