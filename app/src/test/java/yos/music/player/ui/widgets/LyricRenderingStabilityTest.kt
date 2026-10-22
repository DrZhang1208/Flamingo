package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class LyricRenderingStabilityTest {
    private fun glyph(start: Float, end: Float, left: Float, right: Float, space: Boolean = false) =
        CharLayoutInfo(0, '字', space, start, end, left, right, 0, start, end, left, right)

    @Test fun pauseAndSpaceNeverLightThenEraseTheNextGlyph() {
        for (withSpace in listOf(false, true)) {
            val nextLeft = if (withSpace) 90f else 64f
            val chars = buildList {
                add(glyph(0f, 500f, 0f, 64f))
                if (withSpace) add(glyph(500f, 800f, 64f, 90f, true))
                add(glyph(800f, 1500f, nextLeft, nextLeft + 64f))
            }
            for (x in listOf(20f, 60f, nextLeft + 2f, nextLeft + 20f, nextLeft + 60f)) {
                var previous = 0f
                for (time in -240..1600 step 2) {
                    val mask = calculateHighlightMask(chars, time.toFloat())
                    val alpha = when {
                        mask.completed -> 1f
                        x >= mask.highlightLimitPx -> 0f
                        x <= mask.maskRightPx -> 1f
                        mask.glowWidthPx <= 0f -> 0f
                        else -> (1f - (x - mask.maskRightPx) / mask.glowWidthPx).coerceIn(0f, 1f)
                    }
                    assertTrue("brightness reversed at $time, x=$x, space=$withSpace: $previous -> $alpha", alpha + 0.0001f >= previous)
                    previous = alpha
                }
            }
        }
    }

    @Test fun clockIgnoresSmallBackwardReportsButAllowsRealSeeking() {
        assertEquals(1000f, reconcileLyricClock(1000f, 950f), 0f)
        assertEquals(1100f, reconcileLyricClock(1000f, 1100f), 0f)
        assertEquals(100f, reconcileLyricClock(1000f, 100f), 0f)
    }

    @Test fun batchDrawingIsOnlyUsedForEqualGlyphPositions() {
        val chars = listOf(glyph(1000f, 1500f, 0f, 64f), glyph(1500f, 2000f, 64f, 128f))
        assertEquals(0f, uniformLyricUplift(chars, 0f, true)!!, 0f)
        assertNull(uniformLyricUplift(chars, 1200f, true))
        assertNotNull(uniformLyricUplift(chars, 3000f, true))
        assertEquals(0f, uniformLyricUplift(chars, 1200f, false)!!, 0f)
    }
}
