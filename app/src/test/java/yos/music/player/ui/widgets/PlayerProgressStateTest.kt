package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class PlayerProgressStateTest {
    @Test fun returningFromBackgroundKeepsFirstFrameAndCatchesMovingPlayback() {
        val state = PlayerProgressState(10000f, 100000)
        state.frame(1000, false, 1f, 10000f)
        state.restartFrames()
        assertEquals(10000f, state.frame(10000, true, 1f, 50000f), 0f)
        assertEquals(50000f, state.livePositionMs, 0f)
        val midway = state.frame(10140, true, 1f, 50140f)
        assertTrue(midway > 10000f && midway < state.livePositionMs)
        assertFalse(state.isSeekTransitioning)
        state.frame(10280, true, 1f, 50280f)
        assertEquals(state.livePositionMs, state.positionMs, 0f)
    }

    @Test fun recreatedControlsResumeFromRetainedPositionInEitherDirection() {
        for (latest in listOf(2000f, 70000f)) {
            val state = PlayerProgressState(30000f, 100000)
            state.frame(1000, false, 1f, 30000f)
            state.restartFrames()
            assertEquals(30000f, state.frame(10000, false, 1f, latest), 0f)
            assertEquals((30000f + latest) / 2f, state.frame(10140, false, 1f, latest), 0.01f)
            assertEquals(latest, state.frame(10280, false, 1f, latest), 0f)
        }
    }

    @Test fun resumeInterruptedAgainStartsFromCurrentDisplayedPosition() {
        val state = PlayerProgressState(10000f, 100000)
        state.restartFrames()
        state.frame(1000, false, 1f, 50000f)
        val midway = state.frame(1140, false, 1f, 50000f)
        state.restartFrames()
        assertEquals(midway, state.frame(2000, false, 1f, 60000f), 0f)
        assertEquals(60000f, state.frame(2280, false, 1f, 60000f), 0f)
    }

    @Test fun automaticExternalSeekRetargetsFromCurrentDisplay() {
        val state = PlayerProgressState(10000f, 100000)
        state.frame(1000, false, 1f, 10000f)
        state.frame(1280, false, 1f, 10000f)
        assertEquals(10000f, state.frame(1300, false, 1f, 90000f), 0f)
        val midway = state.frame(1440, false, 1f, 90000f)
        assertEquals(50000f, midway, 0.01f)
        assertEquals(midway, state.frame(1441, false, 1f, 20000f), 0f)
        assertEquals(20000f, state.frame(1721, false, 1f, 20000f), 0f)
    }

    @Test fun draggingInterruptsAutomaticCatchUpAndStaleReportsCannotUndoCommit() {
        val state = PlayerProgressState(10000f, 100000)
        state.restartFrames()
        state.frame(1000, false, 1f, 50000f)
        state.frame(1140, false, 1f, 50000f)
        state.begin()
        assertEquals(80000f, state.preview(80000f, false, 1150), 0f)
        assertEquals(80000f, state.frame(1160, false, 1f, 50000f), 0f)
        assertEquals(70000f, state.preview(70000f, false, 1170), 0f)
        assertEquals(70000f, state.finish(70000f, 1180), 0f)
        assertEquals(70000f, state.frame(1200, false, 1f, 50000f), 0f)
        assertEquals(70000f, state.frame(1280, false, 1f, 70000f), 0f)
    }

    @Test fun cancellingSeekStillReturnsSmoothlyToRealPlayback() {
        val state = PlayerProgressState(30000f, 100000)
        state.frame(1000, false, 1f, 30000f)
        state.begin()
        state.preview(90000f, false, 1010)
        assertEquals(90000f, state.finish(null, 1020), 0f)
        assertTrue(state.isSeekTransitioning)
        val midway = state.frame(1160, false, 1f, 30000f)
        assertEquals(60000f, midway, 0.01f)
        assertEquals(30000f, state.frame(1300, false, 1f, 30000f), 0f)
        assertEquals(30000f, state.livePositionMs, 0f)
    }

    @Test fun normalPlaybackUsesContinuousFrameClockAfterSettling() {
        val state = PlayerProgressState(10000f, 100000)
        state.frame(1000, true, 1f, 10000f)
        for (time in 1010L..1400L step 10) state.frame(time, true, 1f, null)
        assertEquals(10400f, state.positionMs, 0.01f)
        for (time in 1410L..1500L step 10) {
            val before = state.positionMs
            state.frame(time, true, 1f, null)
            assertEquals(10f, state.positionMs - before, 0.01f)
        }
    }
}
