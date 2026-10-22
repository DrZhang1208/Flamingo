package yos.music.player.ui.widgets

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricSingleSurfaceTest {
    @Test fun raisedCompleteTextHasExactlyOneGlyphSilhouette() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val text = "MMMM"
        val layout = measurer.measure(text, TextStyle(fontSize = 48.sp))
        val chars = text.indices.map { index ->
            val bounds = layout.getBoundingBox(index)
            CharLayoutInfo(index, text[index], false, 0f, 1000f, bounds.left, bounds.right,
                0, 0f, 1000f, bounds.left, bounds.right)
        }
        fun render(actual: Boolean): Bitmap {
            val result = Bitmap.createBitmap(layout.size.width + 20, layout.size.height + 20, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(result.asImageBitmap()),
                Size(result.width.toFloat(), result.height.toFloat())) {
                if (actual) drawTimedHighlight(layout, mapOf(0 to calculateHighlightMask(chars, 2000f)),
                    chars, 2000f, Color.White, Color(0x2EFFFFFF), true)
                else drawText(layout, color = Color.White, topLeft = Offset(0f, -10f))
            }
            return result
        }
        val actual = render(true)
        val expected = render(false)
        var extraPixels = 0
        for (y in 0 until actual.height) for (x in 0 until actual.width) {
            if (android.graphics.Color.alpha(actual.getPixel(x, y)) > 0 &&
                android.graphics.Color.alpha(expected.getPixel(x, y)) == 0) extraPixels++
        }
        assertEquals("No extra dark silhouette may remain below the raised glyphs", 0, extraPixels)
        actual.recycle()
        expected.recycle()
    }
}
