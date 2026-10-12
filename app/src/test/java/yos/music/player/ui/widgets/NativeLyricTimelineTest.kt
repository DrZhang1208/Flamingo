package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class NativeLyricTimelineTest {
    @Test fun returningFromShareSynchronizesSmallAndLargePlaybackGapsOnTheFirstFrame() {
        for (gap in listOf(80f, 250f, 499f, 5000f)) {
            val clock = NativeLyricClock(1000f)
            clock.frame(0, true, 1f, 1000f)
            clock.restartFrames()
            assertEquals(1000f + gap, clock.frame(10_000, true, 1f, 1000f + gap), 0.001f)
            assertEquals(1008f + gap, clock.frame(10_008, true, 1f, null), 0.001f)
        }
    }

    @Test fun restartWaitsForAReportAndDoesNotOverrideAnExplicitSeek() {
        val clock = NativeLyricClock(1000f)
        clock.restartFrames()
        clock.frame(10_000, true, 1f, null)
        assertEquals(1250f, clock.frame(10_008, true, 1f, 1250f), 0f)
        clock.restartFrames()
        clock.seek(500f, 20_000)
        assertEquals(500f, clock.frame(20_008, true, 1f, 1500f), 0f)
        assertEquals(508f, clock.frame(20_016, true, 1f, 500f), 0f)
    }
    @Test fun standaloneTimedSpaceSurvivesBothDisplayModes() {
        val line = yos.music.player.code.utils.lrc.YosLrcFactory().formatLrcEntries(
            "[00:01.000]Hello[00:02.000] [00:02.000]world[00:03.000]"
        ).single()
        for (wordByWord in listOf(true, false)) {
            val displayed = nativeDisplayTokens(line, wordByWord)
            assertEquals("Hello world", displayed.joinToString("") { it.text })
        }
        assertSame(line.tokens, nativeDisplayTokens(line, true))
    }

    @Test fun wordModeUsesRealTimingAndLineModeNeverInventsTiming() {
        val timed = yos.music.player.code.utils.lrc.YosLyricLine(1000f, 2200f, listOf(
            yos.music.player.code.utils.lrc.YosLyricToken(1000f, 1600f, "Hello "),
            yos.music.player.code.utils.lrc.YosLyricToken(1600f, 2200f, "world")))
        assertSame(timed.tokens, nativeDisplayTokens(timed, true))
        val lineOnly = nativeDisplayTokens(timed, false).single()
        assertEquals("Hello world", lineOnly.text)
        assertEquals(1000f, lineOnly.startMs, 0f)
        assertEquals(lineOnly.startMs, lineOnly.endMs, 0f)
        val untimed = timed.copy(tokens = listOf(
            yos.music.player.code.utils.lrc.YosLyricToken(1000f, 1000f, "Hello world")))
        for (enabled in listOf(true, false)) {
            val displayed = nativeDisplayTokens(untimed, enabled).single()
            assertEquals(untimed.text, displayed.text)
            assertEquals(displayed.startMs, displayed.endMs, 0f)
        }
    }
    @Test fun progressAdvancesEveryFrameBetweenSparsePlayerSamples() {
        val clock = NativeLyricClock(1000f)
        clock.frame(0L, true, 1f, 1000f)
        var previous = 1000f
        for (frame in 1..120) {
            val now = frame * 8L
            val position = clock.frame(now, true, 1f, if (frame % 10 == 0) 1000f + now else null)
            assertEquals(8f, position - previous, 0.001f)
            previous = position
        }
        assertEquals(previous, clock.frame(968L, false, 1f, previous), 0f)
        clock.seek(400f, 968L)
        assertEquals(400f, clock.frame(976L, true, 1f, previous), 0f)
    }
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
    @Test fun heldFinalWordKeepsMovingBetweenInitialRiseAndEnd() {
        for (duration in listOf(900f, 1200f, 2000f, 5000f)) {
            val word = NativeWordMotion(1000f, 1000f + duration, 160f)
            // Previously these frames all stayed at exactly 72% of the rise.
            val middle = word.startMs + 650f
            assertTrue("Held word must keep moving after its initial rise: $duration",
                word.uplift(middle + 80f) > word.uplift(middle))
            var previous = word.uplift(800f)
            for (frame in 808..(word.endMs + 260f).toInt() step 8) {
                val next = word.uplift(frame.toFloat())
                assertTrue(next >= previous)
                assertTrue("No frame may jump through the continuation", next - previous < 0.025f)
                previous = next
            }
            assertEquals(1f, word.uplift(word.endMs + 240f), 0.00001f)
        }
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
        // The moving viewport keeps virtualization alive even between visible rows.
        assertTrue(motion.frame(140_000_000L))
        assertFalse(motion.frame(3_000_000_000L))
    }
}
