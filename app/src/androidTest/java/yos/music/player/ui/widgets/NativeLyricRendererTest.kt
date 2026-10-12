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

    @Test fun standaloneZeroDurationSpaceKeepsWidthAndFollowsAdjacentWordMotion() {
        val line = yos.music.player.code.utils.lrc.YosLrcFactory().formatLrcEntries(
            "[00:01.000]Hello[00:02.000] [00:02.000]world[00:03.000]"
        ).single()
        val layout = measurer.measure(line.text, TextStyle(fontSize = 32.sp))
        val paragraph = nativeLyricParagraph(layout, line.tokens)
        val spaces = paragraph.glyphs.filter { it.whitespace }
        val before = paragraph.glyphs.last { it.textOffset == 4 }
        val after = paragraph.glyphs.first { it.textOffset == 6 }
        assertEquals("Hello world", layout.layoutInput.text.text)
        assertEquals(2, spaces.size)
        assertTrue(layout.getBoundingBox(5).width > 0f)
        assertTrue(after.left > before.right)
        assertEquals(before.upliftMotion, spaces.first().upliftMotion)
        assertEquals(after.upliftMotion, spaces.last().upliftMotion)
        for (time in listOf(1900f, 2000f, 2100f, 3200f)) {
            render(paragraph, time).recycle()
        }
    }

    private fun render(paragraph: NativeLyricParagraph, time: Float, reference: Boolean = false, rise: Float = 4f,
        lineHighlight: NativeLineHighlight? = null, ttmlEffects: Boolean = false, uniformColor: Boolean = false,
        effectStrength: Float = 1f): Bitmap {
        val bitmap = Bitmap.createBitmap(paragraph.layout.size.width + 20,
            paragraph.layout.size.height + 20, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
            Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            if (reference) drawText(paragraph.layout, color = if (time < 0f) dim else Color.White,
                topLeft = Offset(0f, if (time < 0f) 0f else -rise),
                drawStyle = androidx.compose.ui.graphics.drawscope.Fill, shadow = Shadow.None)
            else drawNativeLyricParagraph(paragraph, time, Color.White, if (uniformColor) Color.White else dim, rise,
                lineHighlight = lineHighlight, ttmlEffects = ttmlEffects, effectStrength = effectStrength)
        }
        return bitmap
    }

    @Test fun heldEmphasizedLyricUsesStableFilledGlyphsWithoutAccentOrRise() {
        val text = "Held note 长音"
        val paragraph = nativeLyricParagraph(measurer.measure(text, TextStyle(fontSize = 32.sp),
            constraints = Constraints(maxWidth = 220)), listOf(YosLyricToken(1000f, 4000f, text)))
        assertTrue(paragraph.layout.lineCount > 1)
        for (time in listOf(1800f, 3000f, 4100f)) {
            val held = render(paragraph, time, rise = 0f, ttmlEffects = true,
                uniformColor = true, effectStrength = 0f)
            val baseline = render(paragraph, time, rise = 0f, uniformColor = true)
            assertTrue("Held words must share one stable filled baseline at $time", held.sameAs(baseline))
            held.recycle()
            baseline.recycle()
        }
    }

    @Test fun quietParagraphFastPathPreservesFutureAndFinishedPixels() {
        val text = "Walking through a crowd 穿过人山人海"
        val paragraph = nativeLyricParagraph(measurer.measure(text, TextStyle(fontSize = 32.sp),
            constraints = Constraints(maxWidth = 220)), listOf(YosLyricToken(1000f, 4000f, text)))
        assertTrue(paragraph.layout.lineCount > 1)
        assertEquals(0f, paragraph.quietBeforeMs)
        assertEquals(5000f, paragraph.quietAfterMs)
        for (time in listOf(-1f, 5000f)) {
            val actual = render(paragraph, time, ttmlEffects = true)
            val expected = render(paragraph, time, reference = true)
            assertTrue("Quiet fast path must retain shaped fill and settled rise at $time", actual.sameAs(expected))
            actual.recycle()
            expected.recycle()
        }
    }

    @Test fun emphasisUsesDistinctGraphemePhasesWithinOneSourceToken() {
        val text = "爱琴海"
        val paragraph = nativeLyricParagraph(measurer.measure(text, TextStyle(fontSize = 38.sp)),
            listOf(YosLyricToken(1000f, 3000f, text)))
        val letters = paragraph.glyphs.filter { !it.whitespace }
        assertEquals(3, letters.size)
        assertTrue(letters.zipWithNext().all { it.second.emphasisDelayMs > it.first.emphasisDelayMs })
        val lift = letters.map { nativeTtmlLiftStrength(it.motion.startMs, it.motion.endMs, 3160f - it.emphasisDelayMs) }
        assertTrue(lift.zipWithNext().all { it.second > it.first })
    }

    @Test fun heldNoteNeverOutlinesUnplayedDimText() {
        val text = "MMMM MMMM MMMM"
        val paragraph = nativeLyricParagraph(measurer.measure(text, TextStyle(fontSize = 38.sp),
            constraints = Constraints(maxWidth = 1000)), listOf(YosLyricToken(1000f, 4000f, text)))
        val frame = render(paragraph, 1800f, rise = 0f, ttmlEffects = true)
        val tailStart = paragraph.glyphs.last().left.toInt()
        var ink = 0
        for (y in 0 until frame.height) for (x in tailStart until frame.width) {
            val alpha = android.graphics.Color.alpha(frame.getPixel(x, y))
            if (alpha > 0) ink++
            assertTrue("Unplayed glyphs must retain their dim fill without bright outlines: $alpha", alpha <= 73)
        }
        assertTrue(ink > 20)
        frame.recycle()
    }

    @Test fun heldNoteOutlineKeepsSolidGlyphInteriorsAndRestoresPaintOnReusedLayout() {
        val text = "MMMM MMMM MMMM"
        val paragraph = nativeLyricParagraph(measurer.measure(text, TextStyle(fontSize = 38.sp),
            constraints = Constraints(maxWidth = 220)), listOf(YosLyricToken(1000f, 4000f, text)))
        assertTrue(paragraph.layout.lineCount > 1)
        fun opaquePixels(bitmap: Bitmap): Int {
            var count = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width)
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) >= 245) count++
            return count
        }
        val plain = render(paragraph, 2500f, rise = 0f, uniformColor = true)
        val emphasized = render(paragraph, 2500f, rise = 0f, uniformColor = true, ttmlEffects = true)
        assertTrue("Outline is an accent, glyph interiors must remain filled",
            opaquePixels(emphasized) >= opaquePixels(plain) * 0.9f)
        fun horizontalInkBounds(bitmap: Bitmap): Pair<Int, Int> {
            var left = bitmap.width; var right = -1
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width)
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) >= 245) {
                    left = minOf(left, x); right = maxOf(right, x)
                }
            return left to right
        }
        val baselineBounds = horizontalInkBounds(plain)
        for (time in listOf(1150f, 2500f, 3800f)) {
            val frame = render(paragraph, time, rise = 0f, uniformColor = true, ttmlEffects = true)
            assertTrue("Emphasis must preserve opaque fill throughout its envelope at $time",
                opaquePixels(frame) >= opaquePixels(plain) * 0.9f)
            val bounds = horizontalInkBounds(frame)
            assertEquals("Long-note emphasis must not move the left edge", baselineBounds.first.toFloat(), bounds.first.toFloat(), 1f)
            assertEquals("Long-note emphasis must not move the right edge", baselineBounds.second.toFloat(), bounds.second.toFloat(), 1f)
            frame.recycle()
        }
        val after = render(paragraph, 5000f, rise = 0f, ttmlEffects = true)
        val reference = render(paragraph, 5000f, reference = true, rise = 0f)
        assertTrue("Stroke and shadow must not leak through the cached paragraph paint", after.sameAs(reference))
        listOf(plain, emphasized, after, reference).forEach { it.recycle() }
    }

    @Test fun heldNoteOutlineAndScaleDisappearAfterTheNote() {
        val tokens = listOf(YosLyricToken(1000f, 3000f, "  Hold  "))
        val paragraph = nativeLyricParagraph(measurer.measure("  Hold  ", TextStyle(fontSize = 38.sp),
            constraints = Constraints(maxWidth = 300)), tokens)
        for (time in listOf(0f, 4000f)) {
            val plain = render(paragraph, time)
            val effect = render(paragraph, time, ttmlEffects = true)
            assertTrue("Emphasis must leave no duplicate/stroke after its envelope at $time", plain.sameAs(effect))
            plain.recycle(); effect.recycle()
        }
        val plain = render(paragraph, 2200f)
        val effect = render(paragraph, 2200f, ttmlEffects = true)
        assertFalse("A held note must have a visible emphasis", plain.sameAs(effect))
        plain.recycle(); effect.recycle()
    }

    @Test fun disabledWordModeHighlightsTheWholeWrappedLineTogether() {
        val line = yos.music.player.code.utils.lrc.YosLyricLine(1000f, 5000f, listOf(
            YosLyricToken(1000f, 2500f, "MMMM 歌词 "), YosLyricToken(2500f, 5000f, "MMMM 下一行")))
        val tokens = nativeDisplayTokens(line, wordByWord = false)
        val layout = measurer.measure(line.text, TextStyle(fontSize = 38.sp),
            constraints = Constraints(maxWidth = 230))
        val paragraph = nativeLyricParagraph(layout, tokens)
        assertTrue(layout.lineCount > 1)
        val timing = nativePlaybackLineHighlight(line.startMs, 6000f)
        val before = render(paragraph, 759f, rise = 0f, lineHighlight = timing)
        val expectedBefore = render(paragraph, -1f, reference = true, rise = 0f)
        val after = render(paragraph, 760f, rise = 0f, lineHighlight = timing)
        val expectedAfter = render(paragraph, 5000f, reference = true, rise = 0f)
        assertTrue("Future line must remain dim", before.sameAs(expectedBefore))
        assertTrue("All wrapped fragments must highlight together", after.sameAs(expectedAfter))
        val ended = render(paragraph, 5760f, rise = 0f, lineHighlight = timing)
        assertTrue("Previous line dims exactly at the next timestamp", ended.sameAs(expectedBefore))
        listOf(before, expectedBefore, after, expectedAfter, ended).forEach { it.recycle() }
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
                assertTrue("Coverage regressed at $x,$y at step $step: $before -> $after", after + 1 >= before)
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

    @Test fun latinWordEdgesStayIntactDuringRiseAcrossSourceTokens() {
        val text = " just "
        val tokens = text.mapIndexed { index, char ->
            YosLyricToken(index * 180f, (index + 1) * 180f, char.toString())
        }
        val layout = measurer.measure(text, TextStyle(fontSize = 48.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
        val paragraph = nativeLyricParagraph(layout, tokens)
        val letters = paragraph.glyphs.filter { !it.whitespace }
        assertEquals(1, letters.map { it.upliftMotion }.distinct().size)
        for (time in listOf(150f, 300f, 500f, 750f)) {
            val actual = Bitmap.createBitmap(layout.size.width + 20, layout.size.height + 20, Bitmap.Config.ARGB_8888)
            val expected = actual.copy(Bitmap.Config.ARGB_8888, true)
            val rise = letters.first().upliftMotion.uplift(time) * 6f
            for ((bitmap, reference) in listOf(actual to false, expected to true)) {
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
                    Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                    if (reference) drawText(layout, color = Color.White, topLeft = Offset(0f, -rise))
                    else drawNativeLyricParagraph(paragraph, time, Color.White, Color.White, 6f)
                }
            }
            for (y in 0 until actual.height) for (x in 0 until actual.width) {
                assertEquals("Word edge clipped at $x,$y during $time", expected.getPixel(x,y), actual.getPixel(x,y))
            }
            actual.recycle(); expected.recycle()
        }
    }

    @Test fun longUntimedIntroductionUsesNoGlyphGeometry() {
        val text = "这是一首纯音乐的介绍。".repeat(200)
        val layout = measurer.measure(text, TextStyle(fontSize = 20.sp), constraints = Constraints(maxWidth = 300))
        val paragraph = NativeLyricParagraph(layout, emptyList())
        assertTrue(paragraph.glyphs.isEmpty())
        assertEquals(text, paragraph.layout.layoutInput.text.text)
    }
}
