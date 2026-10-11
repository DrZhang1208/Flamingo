package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class SeekDragSessionTest {
    @Test fun normalReleaseCommitsOnlyThePreviewedPosition() {
        val session = SeekDragSession(30_000f, 180_000f, 300f)
        session.move(50f, false)
        assertEquals(60_000f, session.release()!!, 0.01f)
    }

    @Test fun verticalOnlyMovementIntoCancelAreaDiscardsBothForwardAndBackwardSeeks() {
        for (delta in listOf(-40f, 40f)) {
            val session = SeekDragSession(90_000f, 180_000f, 300f)
            session.move(delta, false)
            session.move(0f, true)
            assertNull(session.release())
        }
    }

    @Test fun leavingCancelAreaRestoresTentativeSeekWithoutAddingLivePlaybackTime() {
        val session = SeekDragSession(30_000f, 180_000f, 300f)
        session.move(50f, false)
        session.move(0f, true)
        session.move(0f, false)
        assertEquals(60_000f, session.release()!!, 0.01f)
    }

    @Test fun draggingPastEitherEndNeverProducesAnInvalidSeek() {
        val session = SeekDragSession(30_000f, 180_000f, 300f)
        session.move(-1000f, false)
        assertEquals(0f, session.release()!!, 0f)
        session.move(1000f, false)
        assertEquals(180_000f, session.release()!!, 0f)
    }
}
