package yos.music.player.ui.widgets.effects

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class ArtworkRenderSizeTest {
    @Test fun portraitLandscapeAndSquareStayBoundedWithoutDistortingTheirAspect() {
        for ((w, h) in listOf(1080 to 2400, 1440 to 3200, 2400 to 1080, 2048 to 2048)) {
            val size = artworkRenderSize(w, h)
            assertTrue(maxOf(size.width, size.height) <= ARTWORK_RENDER_LONG_EDGE)
            assertTrue(size.width * size.height < w * h * 0.04f)
            assertEquals(w.toFloat() / h, size.width.toFloat() / size.height, 0.005f)
        }
    }

    @Test fun smallAndZeroViewportsDoNotCreateAnOversizedOrEmptySurface() {
        assertEquals(IntSize(120, 240), artworkRenderSize(120, 240))
        assertEquals(IntSize(1, 1), artworkRenderSize(0, 0))
        assertEquals(IntSize(1080, 2400), artworkRenderSize(1080, 2400, Int.MAX_VALUE))
    }
}
