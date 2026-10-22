package yos.music.player.ui.widgets

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test

class ArtworkBlendPainterTest {
    private fun pixel(painter: Painter): Int {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(40f,40f)) {
            with(painter) { draw(size) }
        }
        val pixel = bitmap.getPixel(20,20)
        bitmap.recycle()
        return pixel
    }

    @Test fun fadeStaysOpaqueAndInterpolatesBetweenCovers() {
        val red = ColorPainter(Color.Red); val blue = ColorPainter(Color.Blue)
        var previousBlue = -1
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val color = pixel(ArtworkBlendPainter(red, blue) { fraction })
            assertEquals(255, android.graphics.Color.alpha(color))
            assertEquals(255f * fraction, android.graphics.Color.blue(color).toFloat(), 1f)
            assertEquals(255f * (1f-fraction), android.graphics.Color.red(color).toFloat(), 1f)
            assertTrue(android.graphics.Color.blue(color) >= previousBlue)
            previousBlue = android.graphics.Color.blue(color)
        }
    }

    @Test fun interruptionStartsFromTheVisibleBlendAndFinishesAtNewCover() {
        val snapshot = ArtworkBlendPainter(ColorPainter(Color.Red), ColorPainter(Color.Blue)) { 0.4f }
        val green = ColorPainter(Color.Green)
        assertEquals(pixel(snapshot), pixel(ArtworkBlendPainter(snapshot, green) { 0f }))
        assertEquals(pixel(green), pixel(ArtworkBlendPainter(snapshot, green) { 1f }))
    }
}
