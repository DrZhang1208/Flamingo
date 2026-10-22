package yos.music.player.ui.widgets

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test

class PlaybackProgressTrackTest {
    @Test fun beginningOfSongHasRoundFillInsteadOfAVerticalSliver() {
        for (height in listOf(7, 10)) {
            var previousWidth = 0
            for (progress in listOf(0f, 0.0001f, 0.004f, 0.008f, 0.02f, 0.1f, 1f)) {
                val bitmap = Bitmap.createBitmap(300, height, Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
                    Size(300f, height.toFloat())) { drawPlaybackProgress(progress) }
                var left = 300; var right = -1; var top = height; var bottom = -1
                for (y in 0 until height) for (x in 0 until 300) {
                    if (android.graphics.Color.alpha(bitmap.getPixel(x,y)) > 200) {
                        left = minOf(left, x); right = maxOf(right, x)
                        top = minOf(top, y); bottom = maxOf(bottom, y)
                    }
                }
                val width = (right - left + 1).coerceAtLeast(0)
                run {
                    assertTrue("Early fill must not be squeezed: progress=$progress size=$width x ${bottom-top+1}",
                        width >= height - 1 && bottom - top + 1 >= height - 1)
                    assertEquals(0, left)
                    assertTrue(width >= previousWidth)
                    if (progress == 0.008f) assertTrue("Early progress must keep growing", width > previousWidth)
                    if (progress == 0f) assertEquals(height, width)
                    if (progress == 1f) assertEquals(300, width)
                }
                previousWidth = width
                bitmap.recycle()
            }
        }
    }
}
