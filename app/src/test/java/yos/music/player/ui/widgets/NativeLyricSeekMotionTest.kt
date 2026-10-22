package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class NativeLyricSeekMotionTest {
    private val params = WavySpringParams(0.9f, 24f, 170f)

    @Test fun coveringThePageSettlesOldMotionAndOnlyTheNextFollowIsAnimated() {
        val motion = NativeLyricScrollMotion()
        motion.register("row", 4, params, 0f, 0)
        motion.retarget(900f, 0, 4, 0L)
        motion.frame(120_000_000L)
        assertTrue(motion.displacement("row", 900f, 0) > 0f)
        motion.settle(900f, 0, 4)
        assertFalse(motion.frame(160_000_000L))
        assertEquals(0f, motion.displacement("row", 900f, 0), 0f)
        motion.register("new", 5, params, 900f, 0)
        assertEquals(0f, motion.displacement("new", 900f, 0), 0f)
        motion.retarget(1000f, 0, 5, 200_000_000L)
        assertEquals(100f, motion.displacement("row", 1000f, 0), 0f)
        motion.frame(216_666_666L)
        assertTrue(motion.displacement("row", 1000f, 0) in 0f..99.99f)
    }

    @Test fun rowsEnteringDuringForwardSeekJoinTheMovingViewport() {
        val motion = NativeLyricScrollMotion()
        motion.retarget(900f, 0, 4, 0L)
        motion.frame(120_000_000L)
        motion.register("new", 5, params, 900f, 0)
        val offset = motion.displacement("new", 900f, 0)
        assertTrue("New rows must not appear at their final position", offset > 1f)
        assertTrue(offset <= 900f)
        motion.frame(180_000_000L)
        assertTrue(motion.displacement("new", 900f, 0) < offset)
        motion.frame(3_000_000_000L)
        assertEquals(0f, motion.displacement("new", 900f, 0), 0.001f)
    }

    @Test fun rowsEnteringDuringBackwardSeekMoveInTheOppositeDirection() {
        val motion = NativeLyricScrollMotion()
        motion.retarget(900f, 0, 10, 0L)
        motion.frame(3_000_000_000L)
        motion.retarget(200f, 0, 3, 3_000_000_000L)
        motion.frame(3_120_000_000L)
        motion.register("new", 4, params, 200f, 0)
        val offset = motion.displacement("new", 200f, 0)
        assertTrue(offset < -1f)
        motion.frame(3_180_000_000L)
        assertTrue(motion.displacement("new", 200f, 0) > offset)
    }

    @Test fun firstLayerReadJoinsMotionBeforeRegistrationEffectsRun() {
        val motion = NativeLyricScrollMotion()
        motion.retarget(900f, 0, 4, 0L)
        motion.frame(120_000_000L)
        val firstRead = motion.displacement("first", 900f, 0, 4, params)
        assertTrue("First frame must already be animated", firstRead > 1f)
        motion.register("first", 4, params, 900f, 0)
        assertEquals(firstRead, motion.displacement("first", 900f, 0), 0.001f)
    }

    @Test fun aNewRowKeepsItsPositionWhenASecondSeekIsConsumed() {
        val motion = NativeLyricScrollMotion()
        motion.retarget(900f, 0, 4, 0L)
        motion.frame(120_000_000L)
        val pending = motion.displacement("first", 1200f, 0, 4, params)
        motion.retarget(1200f, 0, 4, 120_000_000L)
        assertEquals(pending, motion.displacement("first", 1200f, 0), 0.001f)
    }

    @Test fun rowsCreatedAfterSettlingDoNotReplayAnOldSeek() {
        val motion = NativeLyricScrollMotion()
        motion.retarget(900f, 0, 4, 0L)
        motion.frame(3_000_000_000L)
        motion.register("late", 5, params, 900f, 0)
        assertEquals(0f, motion.displacement("late", 900f, 0), 0f)
    }

    @Test fun virtualizationIncludesTheOriginAndIntermediateViewportOfALongSeek() {
        val column = WavyLyricsColumnState().apply {
            updateItemCount(100); updateViewport(700); jumpTo(3, false)
        }
        val motion = NativeLyricScrollMotion()
        motion.retarget(column.waveCumulativeTravelPx, column.waveResetGeneration, 3, 0L)
        column.jumpTo(40, true)
        motion.retarget(column.waveCumulativeTravelPx, column.waveResetGeneration, 40, 0L)
        fun visible() = column.visibleIndexes(0f, column.scrollOffsetPx -
            motion.visibleDisplacements(column.waveCumulativeTravelPx, column.waveResetGeneration).first())
        assertTrue(3 in visible())
        motion.frame(150_000_000L)
        val intermediate = visible().first
        assertTrue(intermediate > 3 && intermediate < 40)
        motion.frame(3_000_000_000L)
        assertTrue(40 in visible())
    }
}
