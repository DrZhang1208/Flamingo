package yos.music.player.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken

class LyricFollowTimingTest {
    @Test fun movementStartsWithHighlightPrerollAndSeekingResolvesBothDirections() {
        val lines = listOf(
            YosLyricLine(1000f, 2000f, emptyList()),
            YosLyricLine(2000f, 6000f, emptyList(), agent = "countdown"),
            YosLyricLine(6000f, 8000f, emptyList())
        )
        assertEquals(-1, lyricVisualFollowIndex(lines, 759f))
        assertEquals(0, lyricVisualFollowIndex(lines, 760f))
        assertEquals(0, lyricVisualFollowIndex(lines, 1999f))
        assertEquals(1, lyricVisualFollowIndex(lines, 2000f))
        assertEquals(1, lyricVisualFollowIndex(lines, 5759f))
        assertEquals(2, lyricVisualFollowIndex(lines, 5760f))
        assertEquals(0, lyricVisualFollowIndex(lines, 1200f))
    }

    @Test fun interleavedLineTakesTheViewportOnlyAfterItEnds() {
        val lines = listOf(
            YosLyricLine(1000f, 3000f, emptyList()),
            YosLyricLine(2000f, 4000f, emptyList())
        )
        assertEquals(0, lyricVisualFollowIndex(lines, 1999f))
        assertEquals("An interleaved line must not scroll the sung line away",
            0, lyricVisualFollowIndex(lines, 2999f))
        assertEquals("The held line ending is still not enough: wait for the interleaved line",
            0, lyricVisualFollowIndex(lines, 3000f))
        assertEquals(1, lyricVisualFollowIndex(lines, 4000f))
    }

    @Test fun interleavedLineWaitsForItsOwnDisplayTailToo() {
        val lines = listOf(
            YosLyricLine(1000f, 3000f, listOf(YosLyricToken(1000f, 3000f, "啊"))),
            YosLyricLine(2000f, 4000f, emptyList())
        )
        assertEquals(0, lyricVisualFollowIndex(lines, 3000f))
        assertEquals(0, lyricVisualFollowIndex(lines, 3499f))
        assertEquals(0, lyricVisualFollowIndex(lines, 3999f))
        assertEquals(1, lyricVisualFollowIndex(lines, 4000f))
    }

    @Test fun linesAfterARealGapKeepTheirPreroll() {
        val lines = listOf(
            YosLyricLine(1000f, 3000f, emptyList()),
            YosLyricLine(3400f, 5000f, emptyList())
        )
        assertEquals(0, lyricVisualFollowIndex(lines, 3159f))
        assertEquals(1, lyricVisualFollowIndex(lines, 3160f))
    }

    @Test fun otherSideVocalsNeverHoldTheViewport() {
        val lines = listOf(
            YosLyricLine(1000f, 3000f, emptyList()),
            YosLyricLine(1500f, 5000f, emptyList(), agent = "bg", otherSide = true),
            YosLyricLine(2000f, 4000f, emptyList())
        )
        assertEquals("A main vocal line keeps the viewport while it is sung",
            0, lyricVisualFollowIndex(lines, 2000f))
        assertEquals("Interleaved lines still wait for their own end",
            0, lyricVisualFollowIndex(lines, 3000f))
        assertEquals("Background vocals hand over to the finished main line, never blocking it",
            2, lyricVisualFollowIndex(lines, 4000f))
        assertEquals(2, lyricVisualFollowIndex(lines, 4500f))
    }

    @Test fun countdownTickersHandOverAtTheNextPreroll() {
        val lines = listOf(
            YosLyricLine(1000f, 2000f, emptyList(), agent = "countdown"),
            YosLyricLine(1500f, 4000f, emptyList())
        )
        assertEquals(0, lyricVisualFollowIndex(lines, 1259f))
        assertEquals(1, lyricVisualFollowIndex(lines, 1260f))
    }
}
