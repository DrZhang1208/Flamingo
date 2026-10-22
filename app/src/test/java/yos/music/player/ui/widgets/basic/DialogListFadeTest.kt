package yos.music.player.ui.widgets.basic

import org.junit.Assert.*
import org.junit.Test

class DialogListFadeTest {
    @Test fun fadeIsBoundedMonotonicAndSymmetric() {
        var previous = 1f
        for (step in 0..1000) {
            val t = step / 1000f
            val opacity = dialogFadeOpacity(t)
            assertTrue(opacity in 0f..1f)
            assertTrue(opacity <= previous + 0.000001f)
            assertEquals(1f, opacity + dialogFadeOpacity(1f - t), 0.000002f)
            previous = opacity
        }
    }

    @Test fun bothEndsJoinTheSurfaceWithoutAnOpacityKink() {
        assertEquals(1f, dialogFadeOpacity(-1f), 0f)
        assertEquals(0f, dialogFadeOpacity(2f), 0f)
        assertTrue(1f - dialogFadeOpacity(0.01f) < 0.00002f)
        assertTrue(dialogFadeOpacity(0.99f) < 0.00002f)
        assertEquals(0.5f, dialogFadeOpacity(0.5f), 0f)
    }
}
