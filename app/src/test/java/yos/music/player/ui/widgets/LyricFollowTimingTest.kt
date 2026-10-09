package yos.music.player.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine

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
}
