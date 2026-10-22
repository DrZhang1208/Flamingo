package yos.music.player.ui.widgets

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricHighlightPaintTest {
    @Test fun gradientHighlightDoesNotInheritDimBaseAlphaBeforeWrapping() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val layout = measurer.measure("MMMM MMMM MMMM", TextStyle(fontSize = 48.sp),
            constraints = Constraints(maxWidth = 300))
        assertTrue(layout.lineCount > 1)
        fun render(corrected: Boolean): Bitmap {
            val bitmap = Bitmap.createBitmap(320, layout.size.height + 20, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
                Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                drawRect(Color.Black)
                drawText(layout, color = Color(0x2EFFFFFF))
                val brush = Brush.horizontalGradient(colors = listOf(Color.White, Color.Transparent),
                    startX = 180f, endX = 240f)
                if (corrected) drawLyricHighlightText(layout, brush)
                else drawText(layout, brush = brush)
            }
            return bitmap
        }
        fun brightPixels(bitmap: Bitmap): Int {
            var count = 0
            for (y in 0 until layout.getLineBottom(0).toInt()) {
                for (x in 0 until 170) {
                    if (android.graphics.Color.red(bitmap.getPixel(x, y)) > 230) count++
                }
            }
            return count
        }
        val before = render(false)
        val after = render(true)
        android.util.Log.i("LyricPaintTest", "legacyBright=${brightPixels(before)} correctedBright=${brightPixels(after)}")
        val outputDir = java.io.File(context.getExternalFilesDir(null), "lyric-render-tests").apply { mkdirs() }
        for ((name, bitmap) in listOf("before.png" to before, "after.png" to after)) {
            java.io.File(outputDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue("First wrapped line must already contain opaque white glyphs", brightPixels(after) > 200)
        before.recycle()
        after.recycle()
    }
}
