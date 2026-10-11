package yos.music.player.code.utils.lrc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedLyricsParserTest {
    private val parser = YosLrcFactory()

    @Test
    fun `preserves word timing from yrc`() {
        val lines = parser.formatLrcEntries("[1000,1200](1000,400,0)Hello (1400,800,0)world")

        assertEquals(1, lines.size)
        assertEquals(2, lines.single().tokens.size)
        assertEquals(1000f, lines.single().tokens.first().startMs)
        assertEquals(2200f, lines.single().tokens.last().endMs)
    }

    @Test
    fun `parses multiple lrc timestamps without losing text`() {
        val lines = parser.formatLrcEntries("[00:01.00][00:03.00]Same line")

        assertEquals(listOf(1000f, 3000f), lines.map { it.startMs })
        assertTrue(lines.all { it.text == "Same line" })
    }

    @Test
    fun `merges timed translation onto source line`() {
        val source = parser.formatLrcEntries("[00:01.00]Hello")
        val merged = parser.mergeTranslation(source, "[00:01.00]你好")

        assertEquals("你好", merged.single().translation)
    }

    @Test
    fun `keeps long timed blank line as instrumental countdown`() {
        val lines = parser.formatLrcEntries(
            "[00:01.00]First\n[00:05.00]\n[00:10.00]Second"
        )

        assertEquals(3, lines.size)
        assertTrue(lines[1].text.isBlank())
        assertEquals(5_000f, lines[1].startMs)
    }

    @Test
    fun `drops short timed blank line`() {
        val lines = parser.formatLrcEntries(
            "[00:01.00]First\n[00:05.00]\n[00:09.00]Second"
        )

        assertEquals(2, lines.size)
        assertTrue(lines.none { it.text.isBlank() })
    }

    @Test
    fun `inserts countdown for a long gap after word timed line`() {
        val lines = parser.formatLrcEntries(
            "[1000,1000](1000,1000,0)First\n[10000,1000](10000,1000,0)Second"
        )

        assertEquals(3, lines.size)
        assertTrue(lines[1].isCountdown)
        assertEquals(2_000f, lines[1].startMs)
        assertEquals(10_000f, lines[1].endMs)
    }

    @Test
    fun `inserts countdown before a late first line`() {
        val lines = parser.formatLrcEntries("[00:06.00]First")

        assertEquals(2, lines.size)
        assertTrue(lines.first().isCountdown)
        assertEquals(0f, lines.first().startMs)
        assertEquals(6_000f, lines.first().endMs)
    }

    @Test
    fun `preserves ttml agent translation and background line`() {
        val ttml = """
            <tt xmlns:ttm="urn:ttm">
              <body><div>
                <p begin="1s" end="4s" ttm:agent="v2">
                  <span begin="1s" end="2s">Hello </span>
                  <span begin="2s" end="4s">world</span>
                  <span ttm:role="x-translation">你好世界</span>
                  <span ttm:role="x-bg" begin="2.5s" end="3.5s">echo</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val line = parser.formatLrcEntries(ttml).single()

        assertTrue(line.ttmlEffects)
        assertTrue(line.otherLrcEntries.single().ttmlEffects)
        assertEquals("v2", line.agent)
        assertTrue(line.otherSide)
        assertEquals("你好世界", line.translation)
        assertEquals("echo", line.otherLrcEntries.single().text)
        assertEquals("bg", line.otherLrcEntries.single().agent)
    }

    @Test
    fun `groups different lrc agents at the same timestamp`() {
        val lines = parser.formatLrcEntries(
            "[00:01.00]v1:Lead\n[00:01.00]v2:Reply"
        )

        assertEquals(1, lines.size)
        assertEquals("Lead", lines.single().text)
        assertEquals("Reply", lines.single().otherLrcEntries.single().text)
        assertEquals("v2", lines.single().otherLrcEntries.single().agent)
    }

    @Test
    fun `attaches extended lrc background vocal to previous line`() {
        val lines = parser.formatLrcEntries(
            "[00:01.00]v1:Lead\n[00:01.30][bg: <00:01.30>echo<00:02.00> back]"
        )

        assertEquals(1, lines.size)
        val background = lines.single().otherLrcEntries.single()
        assertEquals("bg", background.agent)
        assertEquals("echo back", background.text)
        assertEquals(1_300f, background.startMs)
        assertEquals(2_000f, background.tokens.last().startMs)
    }
}
