package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class SeekDragSessionTest {
    @Test fun normalReleaseCommitsOnlyThePreviewedPosition() {
        val session = SeekDragSession(30_000f, 180_000f, 300f)
        session.move(50f, false, 30_000f)
        assertEquals(60_000f, session.release()!!, 0.01f)
    }

    @Test fun verticalOnlyMovementIntoCancelAreaDiscardsBothForwardAndBackwardSeeks() {
        for (delta in listOf(-40f, 40f)) {
            val session = SeekDragSession(90_000f, 180_000f, 300f)
            session.move(delta, false, 90_000f)
            session.move(0f, true, 91_000f)
            assertEquals(91_000f, session.positionMs, 0f)
            assertNull(session.release())
        }
    }

    @Test fun leavingCancelAreaStartsAtTheLatestLivePosition() {
        val session = SeekDragSession(30_000f, 180_000f, 300f)
        session.move(50f, false, 30_000f)
        session.move(0f, true, 31_000f)
        session.move(0f, false, 33_000f)
        assertEquals(33_000f, session.release()!!, 0f)
    }

    @Test fun travelInsideAndAcrossTheExitNeverChangesTheNewAnchor() {
        for (insideDelta in listOf(-1000f, 1000f)) {
            for (exitDelta in listOf(-200f, 0f, 200f)) {
                val session = SeekDragSession(90_000f, 180_000f, 300f)
                session.move(50f, false, 90_000f)
                session.move(0f, true, 91_000f)
                session.move(insideDelta, true, 92_000f)
                session.move(exitDelta, false, 93_000f)
                assertEquals(93_000f, session.positionMs, 0f)
                // Only movement after exiting changes the seek, in either direction.
                session.move(-10f, false, 94_000f)
                assertEquals(87_000f, session.positionMs, 0.01f)
                session.move(20f, false, 95_000f)
                assertEquals(99_000f, session.release()!!, 0.01f)
            }
        }
    }

    @Test fun everyReentryDiscardsThePreviousPreviewAndReanchorsAgain() {
        val session = SeekDragSession(30_000f, 180_000f, 300f)
        for (live in listOf(32_000f, 34_000f, 36_000f)) {
            session.move(50f, true, live - 1000f)
            session.move(-80f, false, live)
            assertEquals(live, session.positionMs, 0f)
            session.move(10f, false, live)
            assertEquals(live + 6000f, session.release()!!, 0.01f)
        }
    }

    @Test fun draggingPastEitherEndNeverProducesAnInvalidSeek() {
        val session = SeekDragSession(30_000f, 180_000f, 300f)
        session.move(-1000f, false, 30_000f)
        assertEquals(0f, session.release()!!, 0f)
        session.move(1000f, false, 30_000f)
        assertEquals(180_000f, session.release()!!, 0f)
        session.move(0f, true, 190_000f)
        session.move(0f, false, 190_000f)
        assertEquals(180_000f, session.release()!!, 0f)
        session.move(0f, true, -100f)
        session.move(0f, false, -100f)
        assertEquals(0f, session.release()!!, 0f)
    }
}
