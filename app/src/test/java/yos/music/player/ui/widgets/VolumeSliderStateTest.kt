package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class VolumeSliderStateTest {
    @Test fun systemBroadcastsDoNotQuantizeFingerTravel() {
        val state = VolumeSliderState(5, 10)
        state.begin()
        assertNull(state.dragBy(0.02f))
        state.report(5, 1000)
        assertEquals(0.52f, state.position, 0.0001f)
        assertEquals(6, state.dragBy(0.04f))
        state.report(6, 1010)
        state.frame(1020)
        assertEquals(0.56f, state.position, 0.0001f)
        assertEquals(5, state.dragBy(-0.03f))
        state.report(5, 1030)
        assertEquals(0.53f, state.position, 0.0001f)
    }

    @Test fun repeatedSmallMovesAccumulateAcrossUnchangedBroadcasts() {
        val state = VolumeSliderState(5, 10)
        state.begin()
        repeat(4) {
            assertNull(state.dragBy(0.01f))
            state.report(5, 1000L + it)
        }
        assertEquals(6, state.dragBy(0.02f))
        assertEquals(0.56f, state.position, 0.0001f)
    }

    @Test fun releaseAnimatesFromFingerToAuthoritativeSystemVolume() {
        val state = VolumeSliderState(5, 10)
        state.begin()
        state.dragBy(0.16f)
        // The system may reject/cap the requested step; read it again on UP.
        state.finish(6, 1000)
        assertEquals(0.66f, state.position, 0.0001f)
        state.frame(1000)
        assertEquals(0.66f, state.position, 0.0001f)
        state.frame(1140)
        assertEquals(0.63f, state.position, 0.0001f)
        state.frame(1280)
        assertEquals(0.6f, state.position, 0.0001f)
        assertFalse(state.animating)
    }

    @Test fun newDragInterruptsReleaseAtTheVisiblePosition() {
        val state = VolumeSliderState(5, 10)
        state.begin()
        state.dragBy(0.16f)
        state.finish(6, 1000)
        state.frame(1140)
        state.begin()
        state.dragBy(-0.01f)
        state.frame(2000)
        assertEquals(0.62f, state.position, 0.0001f)
        assertFalse(state.animating)
        assertTrue(state.dragging)
    }

    @Test fun hardwareVolumeChangesRetargetWithoutJumping() {
        val state = VolumeSliderState(5, 10)
        state.report(8, 1000)
        state.frame(1140)
        val visible = state.position
        assertEquals(0.65f, visible, 0.0001f)
        state.report(2, 1140)
        state.frame(1140)
        assertEquals(visible, state.position, 0f)
        state.report(2, 1200) // Duplicate reports must not restart the animation.
        state.frame(1420)
        assertEquals(0.2f, state.position, 0f)
        assertFalse(state.animating)
    }

    @Test fun endpointsAndFixedVolumeDevicesStayFinite() {
        val state = VolumeSliderState(5, 10)
        state.begin()
        assertEquals(10, state.dragBy(100f))
        assertEquals(1f, state.position, 0f)
        assertEquals(0, state.dragBy(-100f))
        assertEquals(0f, state.position, 0f)
        val fixed = VolumeSliderState(0, 0)
        fixed.begin()
        assertNull(fixed.dragBy(1f))
        fixed.finish(0, 1000)
        fixed.frame(1280)
        assertEquals(0f, fixed.position, 0f)
    }
}
