package yos.music.player.ui.widgets

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
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

    @Test fun heldWordRiseRemainsContinuousBelowOnePixel() {
        val text = "MMMM"
        val paragraph = nativeLyricParagraph(measurer.measure(text, TextStyle(fontSize = 48.sp)),
            listOf(YosLyricToken(1000f, 6000f, text)))
        fun centerOfInk(bitmap: Bitmap): Double {
            var mass = 0.0
            var moment = 0.0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val alpha = android.graphics.Color.alpha(bitmap.getPixel(x, y)).toDouble()
                mass += alpha
                moment += alpha * y
            }
            assertTrue(mass > 0.0)
            return moment / mass
        }
        var previousY = Double.NaN
        var previousRise = Float.NaN
        for (time in 3300..3800 step 100) {
            val frame = render(paragraph, time.toFloat(), rise = 4f, uniformColor = true)
            val y = centerOfInk(frame)
            val rise = paragraph.glyphs.first().upliftMotion.uplift(time.toFloat()) * 4f
            if (!previousY.isNaN()) {
                val step = (rise - previousRise).toDouble()
                assertTrue("This regression must exercise subpixel steps", step > 0.0 && step < 0.1)
                assertTrue("Held word must not stick to the previous baseline", y < previousY)
                assertEquals("Rendered movement must track continuous note timing", step, previousY - y, 0.015)
            }
            frame.recycle()
            previousY = y
            previousRise = rise
        }
    }

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
        val slot = nativeTtmlGlyphStepMs(2000f, letters.size)
        for (index in letters.indices) {
            val peak = 1000f + letters[index].emphasisDelayMs + slot * 0.5f
            val strengths = letters.map { nativeTtmlGlowStrength(it.motion.startMs, it.motion.endMs,
                peak - it.emphasisDelayMs, it.emphasisCount) }
            assertEquals(1f, strengths[index], 0.00001f)
            assertTrue(strengths.filterIndexed { other, _ -> other != index }.all { it <= 0.57f })
            assertTrue(strengths.count { it > 0.5f } <= 3)
        }
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

    @Test fun heldNoteBloomKeepsSolidGlyphInteriorsAndRestoresPaintOnReusedLayout() {
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
        assertTrue("Bloom is an accent, glyph interiors must remain filled",
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
            val permittedGrowth = paragraph.fontSizePx * 0.25f
            assertEquals("Swell must stay within its reserved horizontal margin", baselineBounds.first.toFloat(), bounds.first.toFloat(), permittedGrowth)
            assertEquals("Swell must not duplicate neighbouring shaped text", baselineBounds.second.toFloat(), bounds.second.toFloat(), permittedGrowth)
            frame.recycle()
        }
        val after = render(paragraph, 5000f, rise = 0f, ttmlEffects = true)
        val reference = render(paragraph, 5000f, reference = true, rise = 0f)
        assertTrue("Stroke and shadow must not leak through the cached paragraph paint", after.sameAs(reference))
        listOf(plain, emphasized, after, reference).forEach { it.recycle() }
    }

    @Test fun emphasizedWordMergesSyllableTimingAndReusesItsSoftHalosAcrossSeeks() {
        val text = "  Holding  future"
        val tokens = listOf(YosLyricToken(1000f, 1600f, "  Hold"),
            YosLyricToken(1600f, 3000f, "ing"), YosLyricToken(5000f, 5500f, "  future"))
        val paragraph = nativeLyricParagraph(measurer.measure(text, TextStyle(fontSize = 38.sp)), tokens)
        val word = paragraph.glyphs.filter { it.textOffset in 2..8 && !it.whitespace }
        assertTrue(word.isNotEmpty())
        assertTrue(word.all { it.emphasisMotion == word.first().emphasisMotion })
        assertTrue(word.last().emphasisDelayMs > word.first().emphasisDelayMs)
        val baseline = render(paragraph, 1900f, rise = 0f, uniformColor = true)
        val peak = render(paragraph, 1900f, rise = 0f, uniformColor = true, ttmlEffects = true)
        assertFalse("Long-note swell must be visible", baseline.sameAs(peak))
        render(paragraph, 1800f, rise = 0f, ttmlEffects = true).recycle()
        val cached = paragraph.surface.cachedEmphasisFragmentCount
        assertTrue(cached > 0)
        assertTrue(paragraph.surface.cachedEmphasisPixelCount <= 2_097_152L)
        repeat(4) {
            render(paragraph, 1800f, rise = 0f, ttmlEffects = true).recycle()
            render(paragraph, 1900f, rise = 0f, uniformColor = true, ttmlEffects = true).also {
                assertTrue("Seeking back must reproduce identical ink", it.sameAs(peak))
                it.recycle()
            }
            assertEquals("No per-frame halo rasterization", cached, paragraph.surface.cachedEmphasisFragmentCount)
        }
        // A soft halo adds low-alpha pixels around the ink, not another solid fill.
        fun haloPixels(bitmap: Bitmap): Int {
            var count = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width)
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) in 1..40) count++
            return count
        }
        assertTrue("Use soft bloom rather than only a hard outline", haloPixels(peak) > haloPixels(baseline))
        val directory = java.io.File(context.cacheDir, "emphasis-preview").apply { mkdirs() }
        for (time in listOf(800f, 1300f, 1900f, 2400f, 3200f, 3600f)) {
            val frame = render(paragraph, time, ttmlEffects = true)
            java.io.File(directory, "frame-${time.toInt()}.png").outputStream().use {
                frame.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            frame.recycle()
        }
        baseline.recycle(); peak.recycle()
    }

    @Test fun heldNoteBloomAndScaleDisappearAfterTheNote() {
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

    @Test fun finalHeldGlyphStopsSwellingAndGlowingWhileItsLongNoteIsStillSung() {
        val paragraph = nativeLyricParagraph(measurer.measure("啊", TextStyle(fontSize = 38.sp)),
            listOf(YosLyricToken(1000f, 9000f, "啊")))
        val peak = render(paragraph, 1160f, ttmlEffects = true)
        val plainPeak = render(paragraph, 1160f)
        assertFalse("The final held glyph should still receive one visible wave", peak.sameAs(plainPeak))
        peak.recycle(); plainPeak.recycle()
        for (time in listOf(1800f, 5000f, 8900f)) {
            val plain = render(paragraph, time)
            val effect = render(paragraph, time, ttmlEffects = true)
            assertTrue("The wave must return to ordinary singing before the held note ends at $time",
                plain.sameAs(effect))
            plain.recycle(); effect.recycle()
        }
    }

    @Test fun denseWaveFramesHaveNoInkJumpAtGlyphHandoffs() {
        val text = "WAVES"
        val paragraph = nativeLyricParagraph(measurer.measure(text, TextStyle(fontSize = 48.sp)),
            listOf(YosLyricToken(1000f, 3000f, text)))
        val directory = java.io.File(context.cacheDir, "emphasis-preview/dense").apply { mkdirs() }
        var previous = render(paragraph, 800f, rise = 0f, uniformColor = true, ttmlEffects = true)
        var largestChange = 0.0
        for (time in 808..3200 step 8) {
            val current = render(paragraph, time.toFloat(), rise = 0f, uniformColor = true, ttmlEffects = true)
            var difference = 0L
            var ink = 0L
            for (y in 0 until current.height) for (x in 0 until current.width) {
                val before = android.graphics.Color.alpha(previous.getPixel(x, y))
                val after = android.graphics.Color.alpha(current.getPixel(x, y))
                difference += kotlin.math.abs(after - before)
                ink += after
            }
            val change = difference.toDouble() / ink.coerceAtLeast(1L)
            largestChange = maxOf(largestChange, change)
            assertTrue("Wave ink must remain continuous at $time ms: $change", change < 0.04)
            if ((time - 808) % 40 == 0) {
                java.io.File(directory, "frame-$time.png").outputStream().use {
                    current.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            previous.recycle()
            previous = current
        }
        previous.recycle()
        assertTrue("The checked frames must include visible motion", largestChange > 0.0005)
    }

    @Test fun independentlyRaisedItalicLetterNeverContainsItsNeighboursInk() {
        val style = nativeIndependentGlyphStyle(TextStyle(fontSize = 60.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            letterSpacing = (-3).sp))
        for (text in listOf("fW", "jT", "AV", "fi", "fl")) {
            val paragraph = nativeLyricParagraph(measurer.measure(text, style),
                listOf(YosLyricToken(1000f, 3000f, text)))
            val first = paragraph.glyphs.first { !it.whitespace }
            assertTrue(first.independentInk)
            val ownedText = text.substring(first.textOffset, first.textEnd)
            val ownedLayout = measurer.measure(ownedText, style.copy(
                textAlign = androidx.compose.ui.text.style.TextAlign.Start))
            val reference = NativeLyricSurface(ownedLayout)
            for (rise in listOf(0f, 0.25f, 3.5f, 8f)) {
                val actual = Bitmap.createBitmap(220, 120, Bitmap.Config.ARGB_8888)
                val expected = actual.copy(Bitmap.Config.ARGB_8888, true)
                for ((bitmap, alone) in listOf(actual to false, expected to true)) {
                    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
                        Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                        translate(left = 40f, top = 16f) {
                            if (alone) translate(left = first.inkLeft,
                                top = first.baseline - ownedLayout.firstBaseline) {
                                reference.drawSolid(this, Color.White, rise)
                            } else paragraph.surface.drawEmphasized(this, first, null, Color.White,
                                Color.White, rise, 0f)
                        }
                    }
                }
                for (y in 0 until actual.height) for (x in 0 until actual.width) {
                    val own = android.graphics.Color.alpha(expected.getPixel(x, y))
                    val rendered = android.graphics.Color.alpha(actual.getPixel(x, y))
                    assertEquals("$text must contain only $ownedText at rise=$rise, pixel=$x,$y",
                        own.toFloat(), rendered.toFloat(), 2f)
                }
                actual.recycle(); expected.recycle()
            }
        }
    }

    @Test fun independentlyAnimatedLatinLettersKeepLigatureLettersAndCombiningMarksIntact() {
        for (text in listOf("office", "affine", "fluffy", "cafe\u0301")) {
            val style = nativeIndependentGlyphStyle(TextStyle(fontSize = 48.sp))
            val paragraph = nativeLyricParagraph(measurer.measure(text, style),
                listOf(YosLyricToken(1000f, 3000f, text)))
            val letters = paragraph.glyphs.filter { !it.whitespace }
            assertEquals("Every grapheme must get its own nonzero geometry", lyricGraphemeRanges(text).size,
                letters.size)
            assertTrue(letters.all { it.independentInk && it.right > it.left })
            if (text.endsWith("e\u0301")) {
                assertEquals("The accent must move with its base letter", "e\u0301",
                    text.substring(letters.last().textOffset, letters.last().textEnd))
            }
        }
    }

    @Test fun wrappedIndependentLettersKeepOneMonotonicHighlightSweepPerRow() {
        val text = "MMMM ".repeat(12).trimEnd()
        val layout = measurer.measure(text, nativeIndependentGlyphStyle(TextStyle(fontSize = 38.sp)),
            constraints = Constraints(maxWidth = 240))
        val paragraph = nativeLyricParagraph(layout, listOf(YosLyricToken(1000f, 13000f, text)))
        assertTrue(layout.lineCount >= 3)
        var partialRows = 0
        for (time in listOf(4600f, 7700f, 10100f)) {
            val frame = Bitmap.createBitmap(layout.size.width + 20, layout.size.height + 20, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(frame.asImageBitmap()),
                Size(frame.width.toFloat(), frame.height.toFloat())) {
                drawNativeLyricParagraph(paragraph, time, Color.White, dim, 0f,
                    ttmlEffects = true, effectStrength = 0f)
            }
            for (row in paragraph.rows) {
                val brightness = row.glyphs.filter { !it.whitespace }.map { glyph ->
                    var peak = 0
                    for (y in glyph.top.toInt().coerceAtLeast(0) until glyph.bottom.toInt().coerceAtMost(frame.height))
                        for (x in kotlin.math.ceil(glyph.left).toInt().coerceAtLeast(0) until
                            kotlin.math.floor(glyph.right).toInt().coerceAtMost(frame.width))
                            peak = maxOf(peak, android.graphics.Color.alpha(frame.getPixel(x, y)))
                    peak
                }
                for ((before, after) in brightness.zipWithNext())
                    assertTrue("Highlight must not brighten again farther along a row at $time: $brightness",
                        after <= before + 3)
                if (brightness.isNotEmpty() && brightness.max() - brightness.min() > 80) partialRows++
            }
            frame.recycle()
        }
        assertTrue("The regression must include a real bright-to-dim transition", partialRows > 0)
    }

    @Test fun transparentOverhangPaddingDoesNotExhaustTheHaloCacheOnLongEnglishLines() {
        val text = "lilili ".repeat(12).trimEnd()
        val layout = measurer.measure(text, nativeIndependentGlyphStyle(TextStyle(fontSize = 120.sp)),
            constraints = Constraints(maxWidth = 1100))
        val paragraph = nativeLyricParagraph(layout, listOf(YosLyricToken(1000f, 61000f, text)))
        val frame = Bitmap.createBitmap(layout.size.width + 40, layout.size.height + 40, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(frame.asImageBitmap()),
            Size(frame.width.toFloat(), frame.height.toFloat())) {
            for (glyph in paragraph.glyphs) paragraph.surface.drawEmphasized(this, glyph,
                null, Color.White, Color.White, 0f, 1f)
        }
        assertEquals("Later letters must retain cached halos too", paragraph.glyphs.size,
            paragraph.surface.cachedEmphasisFragmentCount)
        assertTrue(paragraph.surface.cachedEmphasisPixelCount <= 2_097_152L)
        frame.recycle()
    }

    @Test fun exhaustedHaloCacheStillDrawsTheEmphasisGlow() {
        val layout = measurer.measure("M", nativeIndependentGlyphStyle(TextStyle(fontSize = 48.sp)))
        val glyph = nativeLyricParagraph(layout, listOf(YosLyricToken(1000f, 3000f, "M"))).glyphs.single()
        val surface = NativeLyricSurface(layout, emphasisPixelLimit = 0L)
        val plain = Bitmap.createBitmap(140, 140, Bitmap.Config.ARGB_8888)
        val glowing = plain.copy(Bitmap.Config.ARGB_8888, true)
        for ((bitmap, glow) in listOf(plain to 0f, glowing to 1f)) {
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
                Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                translate(left = 32f, top = 24f) {
                    surface.drawEmphasized(this, glyph, null, Color.White, Color.White, 0f, glow)
                }
            }
        }
        var halo = 0
        for (y in 0 until glowing.height) for (x in 0 until glowing.width) {
            if (android.graphics.Color.alpha(plain.getPixel(x, y)) == 0 &&
                android.graphics.Color.alpha(glowing.getPixel(x, y)) >= 8) halo++
        }
        assertTrue("Fallback must keep visible glow outside its own ink: $halo", halo > 20)
        assertEquals(0L, surface.cachedEmphasisPixelCount)
        plain.recycle(); glowing.recycle()
    }

    @Test fun travellingEmphasisScalesEachLetterWithoutHorizontalDrift() {
        val text = "MMMMM"
        val layout = measurer.measure(text, nativeIndependentGlyphStyle(TextStyle(
            fontSize = 48.sp, letterSpacing = 8.sp)))
        val paragraph = nativeLyricParagraph(layout, listOf(YosLyricToken(1000f, 9000f, text)))
        fun centres(frame: Bitmap): List<Double> = paragraph.glyphs.map { glyph ->
            var mass = 0L
            var moment = 0.0
            for (y in 0 until frame.height) for (x in kotlin.math.ceil(glyph.left).toInt().coerceAtLeast(0)
                until kotlin.math.floor(glyph.right).toInt().coerceAtMost(frame.width)) {
                val alpha = android.graphics.Color.alpha(frame.getPixel(x, y))
                if (alpha >= 245) { mass += alpha; moment += alpha * x.toDouble() }
            }
            assertTrue("Each glyph must contain solid ink", mass > 0)
            moment / mass
        }
        val settled = render(paragraph, 2300f, rise = 0f, uniformColor = true, ttmlEffects = true)
        val anchors = centres(settled)
        for (time in listOf(800f, 1080f, 1240f, 1400f, 1560f, 1720f)) {
            val frame = render(paragraph, time, rise = 0f, uniformColor = true, ttmlEffects = true)
            centres(frame).forEachIndexed { index, centre ->
                assertEquals("Glyph $index must swell around its own anchor at $time", anchors[index], centre, 0.75)
            }
            frame.recycle()
        }
        settled.recycle()
    }

    @Test fun lineHighlightKeepsWrappedGlyphsOnTheirOriginalBaseline() {
        val text = "逐行歌词保持基线 Long line"
        val paragraph = nativeLyricParagraph(measurer.measure(text, TextStyle(fontSize = 38.sp),
            constraints = Constraints(maxWidth = 230)), listOf(YosLyricToken(1000f, 5000f, text)))
        assertTrue(paragraph.layout.lineCount > 1)
        val timing = nativePlaybackLineHighlight(1000f, 6000f)
        val reference = render(paragraph, 5000f, reference = true, rise = 0f)
        for (time in listOf(700f, 900f, 1500f, 5000f, 8000f)) {
            val frame = render(paragraph, time, rise = 6f, lineHighlight = timing,
                uniformColor = true, ttmlEffects = true)
            assertTrue("Line highlighting must not move the paragraph at $time", frame.sameAs(reference))
            frame.recycle()
        }
        reference.recycle()
    }

    @Test fun disabledWordModeCrossfadesTheWholeWrappedLineTogether() {
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
        val beginning = render(paragraph, 760f, rise = 0f, lineHighlight = timing)
        val halfway = render(paragraph, 900f, rise = 0f, lineHighlight = timing)
        val after = render(paragraph, 1040f, rise = 0f, lineHighlight = timing)
        val expectedAfter = render(paragraph, 5000f, reference = true, rise = 0f)
        assertTrue("Future line must remain dim", before.sameAs(expectedBefore))
        assertTrue("The first focused frame must not jump to bright", beginning.sameAs(expectedBefore))
        assertTrue("All wrapped fragments must finish highlighting together", after.sameAs(expectedAfter))
        for (row in 0 until layout.lineCount) {
            fun coverage(bitmap: Bitmap): Long {
                var total = 0L
                for (y in layout.getLineTop(row).toInt() until layout.getLineBottom(row).toInt())
                    for (x in 0 until bitmap.width) total += android.graphics.Color.alpha(bitmap.getPixel(x, y))
                return total
            }
            val low = coverage(before)
            val high = coverage(after)
            assertTrue(high > low)
            assertEquals("Every visual row must share the same intermediate colour", 0.5,
                (coverage(halfway) - low).toDouble() / (high - low), 0.015)
        }
        val leaving = render(paragraph, 5760f, rise = 0f, lineHighlight = timing)
        val fading = render(paragraph, 5900f, rise = 0f, lineHighlight = timing)
        val ended = render(paragraph, 6040f, rise = 0f, lineHighlight = timing)
        assertTrue("The outgoing line must preserve its first frame", leaving.sameAs(expectedAfter))
        assertTrue("Outgoing fade must remain alive beyond the paragraph quiet window", fading.sameAs(halfway))
        assertTrue("Previous line finishes dimming after the transition", ended.sameAs(expectedBefore))
        listOf(before, expectedBefore, beginning, halfway, after, expectedAfter, leaving, fading, ended).forEach { it.recycle() }
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
        // Compare clipping against one complete shaped surface translated with
        // filtering. Re-rasterizing vector text at each fractional baseline is
        // the snapping behaviour this renderer intentionally avoids now.
        val wholeWord = NativeLyricSurface(layout)
        for (time in listOf(150f, 300f, 500f, 750f)) {
            val actual = Bitmap.createBitmap(layout.size.width + 20, layout.size.height + 20, Bitmap.Config.ARGB_8888)
            val expected = actual.copy(Bitmap.Config.ARGB_8888, true)
            val rise = letters.first().upliftMotion.uplift(time) * 6f
            for ((bitmap, reference) in listOf(actual to false, expected to true)) {
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
                    Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                    if (reference) wholeWord.drawSolid(this, Color.White, rise)
                    else drawNativeLyricParagraph(paragraph, time, Color.White, Color.White, 6f)
                }
            }
            for (y in 0 until actual.height) for (x in 0 until actual.width) {
                assertEquals("Word edge clipped at $x,$y during $time",
                    android.graphics.Color.alpha(expected.getPixel(x,y)).toFloat(),
                    android.graphics.Color.alpha(actual.getPixel(x,y)).toFloat(), 1f)
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
