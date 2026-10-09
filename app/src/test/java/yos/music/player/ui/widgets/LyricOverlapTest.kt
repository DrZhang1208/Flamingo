package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class LyricOverlapTest {
    @Test fun glowAfterWhitespaceOrPauseEntersWithoutAFullWidthJump() {
        val first = glyph(0f, 500f)
        val space = glyph(500f, 800f).copy(isWhitespace = true, xStartPx = 64f, xEndPx = 90f)
        val next = glyph(800f, 1500f).copy(xStartPx = 90f, xEndPx = 154f)
        val contiguousSpace = space.copy(endMs = 500f)
        val contiguousNext = next.copy(startMs = 500f, endMs = 1200f)
        for (chars in listOf(listOf(first, space, next), listOf(first, next),
            listOf(first, contiguousSpace, contiguousNext))) {
            val start = chars.last().startMs
            val at = calculateHighlightMask(chars, start)
            val after = calculateHighlightMask(chars, start + 1f)
            val middle = calculateHighlightMask(chars, start + 120f)
            val full = calculateHighlightMask(chars, start + 240f)
            assertEquals(0f, at.glowWidthPx, 0f)
            assertTrue(after.glowWidthPx < 0.01f)
            assertEquals(full.glowWidthPx / 2f, middle.glowWidthPx, 0.001f)
            assertTrue(after.maskRightPx >= first.xEndPx)
        }
    }
    @Test fun everyWrappedLineHasAContinuousFeatherEntryBeforeItsFirstGlyph() {
        val chars = listOf(glyph(0f, 1000f), glyph(1000f, 1800f, 1), glyph(1800f, 2600f, 2))
        for ((line, run) in chars.groupBy { it.visualLine }) {
            if (line == 0) continue
            val start = run.first().startMs
            val before = calculateHighlightMask(run, start - 240f)
            val middle = calculateHighlightMask(run, start - 120f)
            val boundary = calculateHighlightMask(run, start)
            val after = calculateHighlightMask(run, start + 1f)
            assertEquals(0f, before.maskRightPx + before.glowWidthPx, 0.001f)
            assertTrue(middle.maskRightPx > before.maskRightPx)
            assertTrue(middle.maskRightPx < boundary.maskRightPx)
            assertTrue(middle.maskRightPx + middle.glowWidthPx > 0f)
            assertEquals(boundary.maskRightPx, after.maskRightPx, 1f)
            assertEquals(boundary.glowWidthPx, after.glowWidthPx, 0f)
        }
    }
    @Test fun adjacentGlyphsAreBothMovingEvenAfterALongNote() {
        for (duration in listOf(300f, 800f, 5000f)) {
            val chars = staggerUpliftTiming(listOf(glyph(0f, duration), glyph(duration, duration + 700f)))
            val nextStart = chars[1].upliftStartMs
            val sample = nextStart + 60f
            fun progress(index: Int, time: Float) = chars[index].let {
                lyricUpliftProgress(time, it.upliftStartMs, it.upliftEndMs)
            }
            assertTrue("previous completed too early for $duration", progress(0, sample) < 1f)
            assertTrue(progress(1, sample) > 0f)
            assertTrue("previous must still move", progress(0, sample + 16f) > progress(0, sample))
            assertTrue("next must already move", progress(1, sample + 16f) > progress(1, sample))
            assertEquals(duration, chars[0].endMs, 0f)
            assertEquals(duration, chars[1].startMs, 0f)
        }
    }

    @Test fun overlapSurvivesVisualWrappingButDoesNotBridgeARealPause() {
        val wrapped = staggerUpliftTiming(listOf(glyph(0f, 800f), glyph(800f, 1500f, 1)))
        assertTrue(wrapped[1].upliftStartMs < wrapped[0].upliftEndMs)
        val paused = staggerUpliftTiming(listOf(glyph(0f, 800f), glyph(2000f, 2700f)))
        assertEquals(800f, paused[0].upliftEndMs, 0f)
        assertEquals(2000f, paused[1].upliftStartMs, 0f)
    }

    @Test fun gradientSpansMoreThanAGlyphAndScalesWithFontSize() {
        val normal = calculateHighlightMask(listOf(glyph(0f, 300f)), 150f)
        val large = calculateHighlightMask(listOf(glyph(0f, 300f).copy(xEndPx = 128f)), 150f)
        assertTrue(normal.glowWidthPx > 64f)
        assertEquals(normal.glowWidthPx * 2f, large.glowWidthPx, 0.001f)
        assertEquals(0f, calculateHighlightMask(listOf(glyph(0f, 300f)), 150f, enableGlow = false).glowWidthPx, 0f)
    }

    private fun glyph(start: Float, end: Float, line: Int = 0) = CharLayoutInfo(
        textOffset = 0, debugChar = '字', isWhitespace = false,
        startMs = start, endMs = end, xStartPx = 0f, xEndPx = 64f,
        visualLine = line, upliftStartMs = start, upliftEndMs = end,
        upliftXStartPx = 0f, upliftXEndPx = 64f
    )
}
