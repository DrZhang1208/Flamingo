package yos.music.player.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken

class NativeLyricPreparationTest {
    @Test fun warmupOrderStartsAtTheAnchorAndAlternatesSides() {
        assertEquals(listOf(10, 11, 9, 12, 8), nativeLyricWarmupOrder(10, 2, 20))
        assertEquals(listOf(0, 1, 2), nativeLyricWarmupOrder(0, 2, 20))
        assertEquals(listOf(19, 18, 17), nativeLyricWarmupOrder(19, 2, 20))
        assertTrue(nativeLyricWarmupOrder(0, 4, 0).isEmpty())
        // 滚动半径大时覆盖前置窗口所需的两侧行数
        assertEquals(17, nativeLyricWarmupOrder(8, 8, 17).size)
        assertEquals(8, nativeLyricWarmupOrder(8, 8, 17).first())
    }

    @Test fun splitIntroductionsDoNotConfuseSourceIndexesWithDocumentRows() {
        val introduction = YosLyricLine(0f, 1000f,
            listOf(YosLyricToken(0f, 1000f, "介绍文字。".repeat(400))))
        val next = YosLyricLine(1000f, 3000f, listOf(
            YosLyricToken(1000f, 2000f, "下一行"), YosLyricToken(2000f, 3000f, "歌词")))
        val document = PreparedLyricDocument(listOf(introduction, next))
        val nextIndex = document.rows.indexOfFirst { it.sourceIndex == 1 }
        assertTrue(nextIndex > 1)
        assertEquals(nextIndex, document.followTargets[1])
    }
}
