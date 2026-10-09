package yos.music.player.ui.widgets

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test

class NativeLyricCountdownTest {
    private data class Coverage(val width: Int, val alpha: Long, val firstDotHeight: Int)

    private fun render(time: Float): Coverage {
        val bitmap = Bitmap.createBitmap(90, 40, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(90f, 40f)) {
            drawNativeCountdown(time, 1000f, 101000f, Color.White)
        }
        var left = bitmap.width; var right = -1; var top = bitmap.height; var bottom = -1
        var alpha = 0L
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixelAlpha = android.graphics.Color.alpha(bitmap.getPixel(x, y))
            alpha += pixelAlpha
            if (pixelAlpha > 0) {
                left = minOf(left, x); right = maxOf(right, x)
                if (x < 24) { top = minOf(top, y); bottom = maxOf(bottom, y) }
            }
        }
        bitmap.recycle()
        return Coverage((right - left + 1).coerceAtLeast(0), alpha, (bottom - top + 1).coerceAtLeast(0))
    }

    @Test fun largerDotsBreatheAndBothBoundariesScaleAndFadeToNothing() {
        for (time in listOf(999f, 1000f, 101000f, 101001f)) assertEquals(0L, render(time).alpha)
        val contracted = render(1450f)
        val expanded = render(2350f)
        assertTrue("Dots should be visibly larger than the former 7–8px diameter", contracted.firstDotHeight >= 10)
        assertTrue("Breathing must change actual drawn size", expanded.firstDotHeight > contracted.firstDotHeight)
        val entering = render(1180f)
        val leaving = render(100840f)
        assertTrue(entering.width < contracted.width * 0.75f)
        assertTrue(leaving.width < contracted.width * 0.75f)
        assertTrue(entering.alpha < contracted.alpha)
        assertTrue(leaving.alpha < expanded.alpha)
    }
}
