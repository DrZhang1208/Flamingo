package yos.music.player.code.utils.lrc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricPublishingTest {
    private val entries = listOf(
        YosLyricLine(
            startMs = 1_250f,
            endMs = 2_000f,
            tokens = listOf(YosLyricToken(1_250f, 2_000f, "Hello")),
            translation = "你好"
        ),
        YosLyricLine(
            startMs = 2_000f,
            endMs = 8_000f,
            tokens = emptyList(),
            agent = YosLyricLine.COUNTDOWN_AGENT
        )
    )

    @Test
    fun `publishes millisecond lrc and omits countdown`() {
        assertEquals("[00:01.250]Hello", buildTimedLyrics(entries, translation = false))
        assertEquals("[00:01.250]你好", buildTimedLyrics(entries, translation = true))
    }

    @Test
    fun `coloros payload escapes metadata and carries generation`() {
        val json = buildColorOsLyricInfo(
            LyricTrackInfo("A \"song\"", "Artist", "id", "file:///song", 12_000L),
            entries,
            generation = 7L
        )

        assertTrue(json.contains("A \\\"song\\\""))
        assertTrue(json.contains("\"sessionGeneration\":7"))
        assertTrue(json.contains("\\n").not())
        assertFalse(json.contains("countdown"))
    }

    @Test
    fun `lyricon payload preserves words translation background and countdown slot`() {
        val background = YosLyricLine(
            startMs = 1_250f,
            endMs = 2_000f,
            tokens = listOf(YosLyricToken(1_250f, 2_000f, "Backing")),
            agent = "bg"
        )
        val song = buildLyriconSong(
            LyricTrackInfo("Song", "Artist", "media-id", "file:///song", 12_000L),
            listOf(
                entries.first().copy(
                    tokens = listOf(YosLyricToken(1_250f, 2_000f, "Hello", hasSpace = true)),
                    otherSide = true,
                    otherLrcEntries = listOf(background)
                ),
                entries.last()
            )
        )

        val lyrics = requireNotNull(song.lyrics)
        assertEquals("media-id", song.id)
        assertEquals(2, lyrics.size)
        assertTrue(lyrics.first().isAlignedRight)
        assertEquals("Hello ", requireNotNull(lyrics.first().words).single().text)
        assertEquals("Backing", lyrics.first().secondary)
        assertEquals("你好", lyrics.first().translation)
        assertNull(lyrics.last().text)
    }
}
