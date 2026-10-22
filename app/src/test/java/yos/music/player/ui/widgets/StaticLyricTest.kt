package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken

class StaticLyricTest {
    @Test fun singleIntroductionKeepsItsTextWithoutWordAnimation() {
        val text = "这是一首纯音乐的完整介绍。".repeat(1000)
        val line = YosLyricLine(0f, 180000f, listOf(YosLyricToken(0f, 180000f, text)))
        assertTrue(shouldRenderStaticLyric(line, 1))
        assertEquals(text, line.text)
        assertTrue(shouldRenderStaticLyric(line, 20))
    }
    @Test fun realWordTimingAndCountdownArePreserved() {
        val line = YosLyricLine(0f, 2000f, listOf(YosLyricToken(0f, 1000f, "一句"), YosLyricToken(1000f, 2000f, "歌词")))
        assertFalse(shouldRenderStaticLyric(line, 1))
        assertFalse(shouldRenderStaticLyric(line.copy(agent = "countdown"), 1))
        assertFalse(shouldRenderStaticLyric(line.copy(tokens = listOf(YosLyricToken(0f, 0f, "普通行歌词"))), 20))
    }
}
