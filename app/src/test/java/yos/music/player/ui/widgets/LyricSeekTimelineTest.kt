package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine

class LyricSeekTimelineTest {
    @Test fun timeInsideALineProducesAllIntermediatePositionsBothWays() {
        val timeline = timeline(listOf(line(1000f, 5000f), line(5000f, 9000f), line(9000f, 12000f)))
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f, 0.5f, 0.25f)) {
            val target = timeline.resolve(1000f + 4000f * fraction)!!
            assertEquals(fraction, target.fromIndex + (target.toIndex - target.fromIndex) * target.fraction, 0.0001f)
        }
    }

    @Test fun lyricEndReachesTheNextRowAndAGapDoesNotJumpAtTheNextStart() {
        val timeline = timeline(listOf(line(1000f, 3000f), line(5000f, 7000f), line(8000f, 9000f)))
        assertEquals(0.5f, timeline.resolve(2000f)!!.fraction, 0f)
        assertEquals(1f, timeline.resolve(3000f)!!.fraction, 0f)
        assertEquals(1f, timeline.resolve(4999f)!!.fraction, 0f)
        assertEquals(LyricSeekScrollTarget(1, 2, 0f), timeline.resolve(5000f))
    }

    @Test fun overlappingAndUntimedLinesUseTheNextStartAsTheirBoundary() {
        val overlap = timeline(listOf(line(1000f, 8000f), line(5000f, 9000f)))
        assertEquals(0.5f, overlap.resolve(3000f)!!.fraction, 0f)
        val untimed = timeline(listOf(line(1000f, 1000f), line(5000f, 5000f)))
        assertEquals(0.5f, untimed.resolve(3000f)!!.fraction, 0f)
    }

    @Test fun simultaneousLinesAndSplitParagraphsResolveToDocumentCoordinates() {
        val timeline = LyricSeekTimeline(listOf(line(1000f, 3000f), line(3000f, 5000f),
            line(3000f, 5000f), line(5000f, 9000f)), mapOf(0 to 0, 1 to 4, 2 to 5, 3 to 6))
        assertEquals(LyricSeekScrollTarget(0, 5, 0.5f), timeline.resolve(2000f))
        assertEquals(LyricSeekScrollTarget(5, 6, 0f), timeline.resolve(3000f))
    }

    @Test fun introductionCountdownAndSongEndsRemainBounded() {
        val countdown = line(2000f, 6000f).copy(agent = YosLyricLine.COUNTDOWN_AGENT)
        val timeline = timeline(listOf(countdown, line(6000f, 9000f)))
        assertEquals(LyricSeekScrollTarget(0, 1, 0f), timeline.resolve(0f))
        assertEquals(LyricSeekScrollTarget(0, 1, 0.5f), timeline.resolve(4000f))
        assertEquals(LyricSeekScrollTarget(1, 1, 0f), timeline.resolve(100000f))
        assertNull(timeline(emptyList()).resolve(0f))
    }

    private fun timeline(lines: List<YosLyricLine>) = LyricSeekTimeline(lines, lines.indices.associateWith { it })
    private fun line(start: Float, end: Float) = YosLyricLine(start, end, emptyList())
}
