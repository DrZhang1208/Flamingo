package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class NativeLyricTimelineTest {
    @Test fun essayIsSplitLosslesslyIntoBoundedLazyRows() {
        val text = "介绍正文和组合字符😀。\n".repeat(2000)
        val line = yos.music.player.code.utils.lrc.YosLyricLine(0f, 200000f,
            listOf(yos.music.player.code.utils.lrc.YosLyricToken(0f, 200000f, text)))
        val rows = nativeLyricDocument(listOf(line))
        assertTrue(rows.size > 1)
        assertTrue(rows.all { it.line.text.length <= 512 && it.staticText && it.sourceIndex == 0 })
        assertEquals(text, rows.joinToString("") { it.line.text })
        assertTrue(rows.none { it.line.text.last().isHighSurrogate() })
    }
    @Test fun sparseReportsDoNotMakeClockStepBackward() {
        val clock = NativeLyricClock(1000f)
        clock.frame(0L, true, 1f, 1000f)
        var previous = clock.positionMs
        for (frame in 1..120) {
            val now = frame * 8L
            val report = if (frame % 10 == 0) 1000f + now - 35f else null
            val position = clock.frame(now, true, 1f, report)
            assertTrue(position >= previous)
            previous = position
        }
        assertEquals(1960f, clock.positionMs, 0.01f)
    }
    @Test fun pauseBufferingSpeedAndExplicitBackwardSeek() {
        val clock = NativeLyricClock(3000f)
        clock.frame(0L, true, 2f, 3000f)
        assertEquals(3100f, clock.frame(50L, true, 2f, null), 0.01f)
        assertEquals(3100f, clock.frame(100L, false, 2f, 3090f), 0.01f)
        clock.seek(1000f, 100L)
        assertEquals(1000f, clock.frame(150L, true, 1f, 3000f), 0.01f)
        assertEquals(1050f, clock.frame(200L, true, 1f, 1000f), 0.01f)
    }
    @Test fun adjacentUpliftsOverlapIncludingLongNotes() {
        for (duration in listOf(250f, 800f, 5000f)) {
            val first = NativeWordMotion(1000f, 1000f + duration, 160f)
            val second = NativeWordMotion(first.endMs, first.endMs + 500f, 160f)
            val now = first.endMs - 50f
            assertTrue(second.uplift(now) > 0f)
            assertTrue(first.uplift(now) < first.uplift(now + 20f))
            assertTrue(second.uplift(now) < second.uplift(now + 20f))
        }
        val longNote = NativeWordMotion(0f, 10000f, 0f)
        assertTrue(longNote.uplift(700f) >= 0.72f)
    }
    @Test fun eachWrappedOrSpaceSeparatedGlyphStartsDimAndNeverRegresses() {
        for (x in listOf(0f, 0.2f, 0.5f, 0.9f, 1f)) {
            assertEquals(0f, nativeHighlightCoverage(0f, x, 0.7f), 0f)
            var previous = 0f
            for (step in 0..1000) {
                val coverage = nativeHighlightCoverage(step / 1000f, x, 0.7f)
                assertTrue(coverage >= previous)
                previous = coverage
            }
            assertEquals(1f, previous, 0.0001f)
        }
    }
    @Test fun sharedScrollDriverRetargetsWithoutLosingPosition() {
        val motion = NativeLyricScrollMotion()
        val params = WavySpringParams(0.9f, 15f, 90f)
        motion.register("row", 1, params, 0f, 0)
        motion.retarget(100f, 0, 1, 0L)
        motion.frame(100_000_000L)
        val before = motion.displacement("row", 100f, 0)
        val immediate = motion.displacement("row", 140f, 0)
        motion.retarget(140f, 0, 1, 100_000_000L)
        assertEquals(before + 40f, immediate, 0.001f)
        assertEquals(immediate, motion.displacement("row", 140f, 0), 0.001f)
        motion.frame(120_000_000L)
        assertTrue(motion.displacement("row", 140f, 0) < immediate)
        motion.unregister("row")
        assertFalse(motion.frame(140_000_000L))
    }
}
