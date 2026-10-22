package yos.music.player.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricRenderTimelineTest {
    @Test
    fun `graphemes do not time surrogate halves or combining marks independently`() {
        val text = "A\uD83D\uDE03e\u0301中"
        val glyphs = lyricGraphemeRanges(text).map { text.substring(it.first, it.last + 1) }
        assertEquals(listOf("A", "\uD83D\uDE03", "e\u0301", "中"), glyphs)
        assertEquals(text, glyphs.joinToString(""))
    }

    @Test
    fun `empty lyrics have no mask or grapheme offsets`() {
        assertTrue(lyricGraphemeRanges("").isEmpty())
        val mask = calculateHighlightMask(emptyList(), 1000f)
        assertFalse(mask.completed)
        assertEquals(-1, mask.activeVisualLine)
    }

    @Test
    fun `word timing continues through a next-row boundary`() {
        // The next row may start at 1000ms while this source syllable ends at 2000ms.
        // Changing the focused row must not force the remaining syllable complete.
        val chars = listOf(glyph(0f, 2000f, 0f, 100f))
        val before = calculateHighlightMask(chars, 999f, smoothVelocity = false)
        val after = calculateHighlightMask(chars, 1001f, smoothVelocity = false)
        assertFalse(after.completed)
        assertEquals(49.95f, before.maskRightPx, 0.001f)
        assertEquals(50.05f, after.maskRightPx, 0.001f)
    }

    @Test
    fun `wrapped mask completes at final wrapped line edge not longest line width`() {
        val chars = listOf(
            glyph(0f, 1000f, 0f, 240f),
            glyph(1000f, 2000f, 0f, 40f, visualLine = 1),
        )
        val before = calculateHighlightMask(chars, 1999f, smoothVelocity = false)
        val completed = calculateHighlightMask(chars, 2000f)
        assertEquals(1, before.activeVisualLine)
        assertEquals(39.96f, before.maskRightPx, 0.001f)
        assertTrue(completed.completed)
        assertEquals(40f, completed.maskRightPx, 0f)
        assertEquals(0..1, completed.fullyDoneVisualLines)
    }

    @Test
    fun `seeking backward reconstructs highlight from playback time`() {
        val chars = listOf(glyph(1000f, 2000f, 20f, 120f))
        assertTrue(calculateHighlightMask(chars, 2500f).completed)
        val sought = calculateHighlightMask(chars, 1500f, smoothVelocity = false)
        assertFalse(sought.completed)
        assertEquals(70f, sought.maskRightPx, 0f)
        val preroll = calculateHighlightMask(chars, 500f)
        assertFalse(preroll.completed)
        assertTrue(preroll.maskRightPx < 20f)
    }

    private fun glyph(start: Float, end: Float, left: Float, right: Float, visualLine: Int = 0) =
        CharLayoutInfo(
            textOffset = 0,
            debugChar = 'a',
            isWhitespace = false,
            startMs = start,
            endMs = end,
            xStartPx = left,
            xEndPx = right,
            visualLine = visualLine,
            upliftStartMs = start,
            upliftEndMs = end,
            upliftXStartPx = left,
            upliftXEndPx = right,
        )
}
