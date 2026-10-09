package yos.music.player.ui.widgets

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricBatchedDrawingTest {
    @Test fun batchedUpliftMatchesPerGlyphDrawingAcrossWrappedWords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val text = "MMMM MM MMMM MM MMMM"
        val layout = measurer.measure(text, TextStyle(fontSize = 38.sp), constraints = Constraints(maxWidth = 250))
        val chars = text.indices.map { index ->
            val bounds = layout.getBoundingBox(index)
            val start = index * 500f
            CharLayoutInfo(index, text[index], text[index].isWhitespace(), start, start + 500f,
                bounds.left, bounds.right, layout.getLineForOffset(index),
                start - 120f, start + 620f, bounds.left, bounds.right)
        }
        for (time in listOf(300f, 1600f, 4200f, 6300f, 9000f)) {
            val masks = chars.groupBy { it.visualLine }.mapValues { calculateHighlightMask(it.value, time) }
            fun render(batch: Boolean): Bitmap {
                val bitmap = Bitmap.createBitmap(layout.size.width + 20, layout.size.height + 20, Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
                    Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                    drawTimedHighlight(layout, masks, chars, time, Color.White, Color(0x2EFFFFFF), true,
                        batchEqualUplift = batch)
                }
                return bitmap
            }
            val batched = render(true)
            val reference = render(false)
            var differences = 0
            for (y in 0 until batched.height) for (x in 0 until batched.width) {
                if (batched.getPixel(x, y) != reference.getPixel(x, y)) differences++
            }
            assertEquals("Changed glyph pixels at $time ms", 0, differences)
            batched.recycle()
            reference.recycle()
        }
    }
}
