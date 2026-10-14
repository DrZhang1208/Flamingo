package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class SeekProgressReturnMotionTest {
    @Test fun cancellingForwardAndBackwardSeeksReturnsContinuouslyToLivePlayback() {
        for (preview in listOf(10000f, 90000f)) {
            val motion = SeekProgressReturnMotion()
            motion.start(preview, 1000)
            assertEquals(preview, motion.frame(1000, 30000f), 0f)
            var previousError = Float.POSITIVE_INFINITY
            for (elapsed in 0..280 step 4) {
                val live = 30000f + elapsed
                val displayed = motion.frame(1000L + elapsed, live)
                val error = kotlin.math.abs(displayed - live)
                assertTrue(error <= previousError)
                previousError = error
            }
            assertFalse(motion.active)
            assertEquals(30300f, motion.frame(1300, 30300f), 0f)
        }
    }

    @Test fun releasingInCancelAreaDoesNotRequireRestartingTheReturn() {
        val motion = SeekProgressReturnMotion()
        motion.start(90000f, 1000)
        val midway = motion.frame(1100, 30100f)
        assertTrue(midway > 30100f && midway < 90000f)
        assertEquals(midway, motion.frame(1100, 30100f), 0f)
        assertEquals(30280f, motion.frame(1280, 30280f), 0f)
    }

    @Test fun anotherDragCanInterruptReturnAndAChangedTrackDropsOldMotion() {
        val motion = SeekProgressReturnMotion()
        motion.start(90000f, 1000)
        val displayed = motion.frame(1080, 30080f)
        motion.start(displayed, 1080)
        assertEquals(displayed, motion.frame(1080, 40000f), 0.01f)
        motion.reset()
        assertEquals(1000f, motion.frame(1090, 1000f), 0f)
    }

    @Test fun enteringAndLeavingCancelAreaBothKeepTheFirstFrameContinuous() {
        for (preview in listOf(10000f, 90000f)) {
            val presentation = SeekProgressPresentation(30000f)
            presentation.begin()
            assertEquals(preview, presentation.preview(30000f, preview, false, 1000), 0f)
            assertEquals(preview, presentation.preview(preview, preview, true, 1010), 0f)
            assertEquals(30000f, presentation.frame(1290, 30000f), 0f)
            assertEquals(30000f, presentation.preview(30000f, preview, false, 1300), 0f)
            val midway = presentation.frame(1440, 30000f)
            assertEquals((30000f + preview) / 2f, midway, 0.01f)
            assertEquals(preview, presentation.frame(1580, 30000f), 0f)
        }
    }

    @Test fun rapidlyCrossingTheBoundaryRetargetsFromTheVisiblePosition() {
        val presentation = SeekProgressPresentation(30000f)
        presentation.begin()
        presentation.preview(30000f, 90000f, false, 1000)
        presentation.preview(90000f, 90000f, true, 1010)
        val towardsLive = presentation.frame(1080, 30000f)
        assertEquals(towardsLive, presentation.preview(towardsLive, 95000f, false, 1080), 0.01f)
        val towardsPreview = presentation.frame(1150, 30000f)
        assertTrue(towardsPreview > towardsLive)
        assertEquals(towardsPreview, presentation.preview(towardsPreview, 95000f, true, 1150), 0.01f)
        assertEquals(30000f, presentation.frame(1430, 30000f), 0f)
    }

    @Test fun movingTheFingerDuringReentryDoesNotRestartTheTransition() {
        val presentation = SeekProgressPresentation(30000f)
        presentation.begin()
        presentation.preview(30000f, 90000f, true, 1000)
        presentation.frame(1280, 30000f)
        presentation.preview(30000f, 90000f, false, 1300)
        val early = presentation.frame(1320, 30000f)
        val moved = presentation.preview(early, 100000f, false, 1320)
        // A 10s target move at the beginning must not make a 10s displayed jump.
        assertTrue(moved - early in 0f..200f)
        assertEquals(100000f, presentation.frame(1580, 30000f), 0f)
    }

    @Test fun releaseAfterReanchoringCommitsOnlyNewTravelWithoutSnappingTheDisplay() {
        val session = SeekDragSession(30000f, 100000f, 100f)
        val presentation = SeekProgressPresentation(30000f)
        presentation.begin()
        session.move(60f, true, 30000f)
        presentation.preview(30000f, session.positionMs, session.cancelled, 1000)
        session.move(80f, false, 32000f)
        presentation.preview(30000f, session.positionMs, session.cancelled, 1300)
        session.move(10f, false, 32000f)
        presentation.preview(30000f, session.positionMs, session.cancelled, 1300)
        val halfway = presentation.frame(1440, 30000f)
        val committed = session.release()!!
        assertEquals(42000f, committed, 0.01f)
        assertEquals(halfway, presentation.finish(halfway, committed, 1440), 0.01f)
        assertEquals(committed, presentation.frame(1580, committed), 0f)
    }

    @Test fun cancellationAndSystemInterruptionReturnToLiveWithoutASeek() {
        for (inCancelArea in listOf(true, false)) {
            val presentation = SeekProgressPresentation(30000f)
            presentation.begin()
            val preview = presentation.preview(30000f, 90000f, inCancelArea, 1000)
            val displayed = presentation.frame(1100, 30100f)
            assertEquals(displayed, presentation.finish(displayed, null, 1100), 0.01f)
            assertEquals(30400f, presentation.frame(1400, 30400f), 0f)
            assertTrue(preview >= 30000f)
        }
    }

    @Test fun arcRisesFromTheBottomWithAMatchingCurvedHitBoundary() {
        assertEquals(0f, seekCancelArcTop(400f, 100f, 200f), 0f)
        assertEquals(55f, seekCancelArcTop(400f, 100f, 0f), 0.001f)
        assertEquals(55f, seekCancelArcTop(400f, 100f, 400f), 0.001f)
        assertEquals(seekCancelArcTop(400f, 100f, 100f), seekCancelArcTop(400f, 100f, 300f), 0f)
    }

    @Test fun activationLiftsTheCentreWithoutMovingTheEdgesOrOpeningTheBottom() {
        for (activation in listOf(0f, 0.25f, 0.5f, 1f)) {
            assertEquals(-26f * activation, seekCancelArcTop(400f, 100f, 200f, activation), 0.001f)
            assertEquals(55f, seekCancelArcTop(400f, 100f, 0f, activation), 0.001f)
            assertEquals(55f, seekCancelArcTop(400f, 100f, 400f, activation), 0.001f)
            for (x in 0..400 step 10) {
                assertTrue(seekCancelArcTop(400f, 100f, x.toFloat(), activation) < 100f)
            }
        }
        assertEquals(-26f, seekCancelArcTop(400f, 100f, 200f, 1.1f), 0.001f)
    }
}
