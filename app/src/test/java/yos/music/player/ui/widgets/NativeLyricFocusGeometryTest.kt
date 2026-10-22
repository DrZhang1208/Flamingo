package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class NativeLyricFocusGeometryTest {
    @Test fun fullFocusLeavesEqualVisibleMarginsAcrossPhoneAndSmallWindows() {
        for (width in listOf(180f, 240f, 360f, 480f, 1080f)) {
            val trailing = nativeLyricFocusTrailingInset(width, 29f)
            val scaled = (width - 29f - trailing) * NATIVE_LYRIC_FOCUS_SCALE
            assertEquals(29f, width - 29f - scaled, 0.0001f)
            assertTrue(trailing > 29f)
        }
    }
    @Test fun everyIntermediateScaleKeepsLeadingEdgeFixedAndTrailingMarginSafe() {
        val width = 360f
        val trailing = nativeLyricFocusTrailingInset(width, 29f)
        for (step in 0..100) {
            val scale = 1f + (NATIVE_LYRIC_FOCUS_SCALE - 1f) * step / 100f
            val visibleRight = 29f + (width - 29f - trailing) * scale
            assertTrue(width - visibleRight >= 28.999f)
        }
    }
}
