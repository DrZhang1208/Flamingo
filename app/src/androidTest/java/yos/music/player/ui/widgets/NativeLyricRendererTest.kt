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
import org.junit.Assert.*
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricToken

class NativeLyricRendererTest {
    private val density = Density(1f)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
    private val dim = Color(0x47FFFFFF)

    private fun render(paragraph: NativeLyricParagraph, time: Float, reference: Boolean = false, rise: Float = 4f): Bitmap {
        val bitmap = Bitmap.createBitmap(paragraph.layout.size.width + 20,
            paragraph.layout.size.height + 20, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
            Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            if (reference) drawText(paragraph.layout, color = if (time < 0f) dim else Color.White,
                topLeft = Offset(0f, if (time < 0f) 0f else -rise))
            else drawNativeLyricParagraph(paragraph, time, Color.White, dim, rise)
        }
        return bitmap
    }

    @Test fun completeWrappedTextHasOnlyTheRaisedParagraphSurface() {
        val tokens = listOf(YosLyricToken(0f, 1000f, "歌词上浮 "), YosLyricToken(1000f, 4000f, "MMMM 下一行"))
        val layout = measurer.measure(tokens.joinToString("") { it.text }, TextStyle(fontSize = 38.sp),
            constraints = Constraints(maxWidth = 230))
        val paragraph = nativeLyricParagraph(layout, tokens)
        assertTrue(layout.lineCount > 1)
        for (time in listOf(-1000f, 5000f)) {
            val actual = render(paragraph, time)
            val expected = render(paragraph, time, reference = true)
            var different = 0
            val examples = mutableListOf<String>()
            for (y in 0 until actual.height) for (x in 0 until actual.width) {
                if (actual.getPixel(x, y) != expected.getPixel(x, y)) {
                    different++
                    if (examples.size < 12) examples += "$x,$y:${actual.getPixel(x,y).toUInt().toString(16)}/${expected.getPixel(x,y).toUInt().toString(16)}"
                }
            }
            assertEquals("Single surface must match the shaped paragraph at $time: $examples", 0, different)
            actual.recycle(); expected.recycle()
        }
    }

    @Test fun highlightDoesNotRegressAtSpacesOrWrappedWordBoundaries() {
        val tokens = listOf(YosLyricToken(0f, 4000f, "MMMM MM MMMM MM MMMM"))
        val layout = measurer.measure(tokens[0].text, TextStyle(fontSize = 38.sp),
            constraints = Constraints(maxWidth = 250))
        val paragraph = nativeLyricParagraph(layout, tokens)
        assertTrue(layout.lineCount > 1)
        assertEquals(0f, paragraph.glyphs.minOf { it.highlightStart }, 0f)
        assertEquals(1f, paragraph.glyphs.maxOf { it.highlightEnd }, 0.001f)
        var previous = render(paragraph, -1000f, rise = 0f)
        for (step in 0..40) {
            val current = render(paragraph, step * 100f, rise = 0f)
            for (y in 0 until current.height) for (x in 0 until current.width) {
                val before = android.graphics.Color.alpha(previous.getPixel(x, y))
                val after = android.graphics.Color.alpha(current.getPixel(x, y))
                assertTrue("Coverage regressed at $x,$y at step $step", after + 1 >= before)
            }
            previous.recycle(); previous = current
        }
        previous.recycle()
    }

    @Test fun multiCharacterSourceWordHasOverlappingGlyphMotion() {
        val tokens = listOf(YosLyricToken(1000f, 2200f, "交错上浮"))
        val layout = measurer.measure(tokens[0].text, TextStyle(fontSize = 38.sp))
        val glyphs = nativeLyricParagraph(layout, tokens).glyphs
        assertEquals(4, glyphs.size)
        val first = glyphs[0].upliftMotion
        val second = glyphs[1].upliftMotion
        assertTrue(second.startMs > first.startMs)
        val time = second.startMs - 50f
        assertTrue(second.uplift(time) > 0f)
        assertTrue(first.uplift(time + 20f) > first.uplift(time))
        assertTrue(second.uplift(time + 20f) > second.uplift(time))
    }

    @Test fun longUntimedIntroductionUsesNoGlyphGeometry() {
        val text = "这是一首纯音乐的介绍。".repeat(200)
        val layout = measurer.measure(text, TextStyle(fontSize = 20.sp), constraints = Constraints(maxWidth = 300))
        val paragraph = NativeLyricParagraph(layout, emptyList())
        assertTrue(paragraph.glyphs.isEmpty())
        assertEquals(text, paragraph.layout.layoutInput.text.text)
    }
}
