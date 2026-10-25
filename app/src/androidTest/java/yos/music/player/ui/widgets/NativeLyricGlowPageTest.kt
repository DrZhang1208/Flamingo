package yos.music.player.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricToken

class NativeLyricGlowPageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cachedHaloIsVisibleOnTheHardwareCanvasOverAnOpaqueBackground() {
        val glowing = mutableStateOf(false)
        compose.setContent {
            val measurer = rememberTextMeasurer()
            val paragraph = androidx.compose.runtime.remember(measurer) {
                nativeLyricParagraph(measurer.measure("光", TextStyle(fontSize = 38.sp)),
                    listOf(YosLyricToken(1000f, 3000f, "光")))
            }
            Canvas(Modifier.size(160.dp, 120.dp).background(Color(0xFF555555)).testTag("glow")) {
                translate(left = 35.dp.toPx(), top = 25.dp.toPx()) {
                    paragraph.surface.drawEmphasized(this, paragraph.glyphs.single(), null,
                        Color.White, Color.White, 0f, if (glowing.value) 1f else 0f)
                }
            }
        }
        val plain = compose.onNodeWithTag("glow").captureToImage().asAndroidBitmap()
        compose.runOnIdle { glowing.value = true }
        val glow = compose.onNodeWithTag("glow").captureToImage().asAndroidBitmap()
        var visibleHalo = 0
        for (y in 0 until plain.height) for (x in 0 until plain.width) {
            val before = android.graphics.Color.red(plain.getPixel(x, y))
            val after = android.graphics.Color.red(glow.getPixel(x, y))
            if (before == 85 && after >= 105) visibleHalo++
        }
        assertTrue("Hardware rendering must show a halo outside the solid glyph: $visibleHalo pixels",
            visibleHalo > 100)
        val directory = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "emphasis-preview").apply { mkdirs() }
        java.io.File(directory, "hardware-glow.png").outputStream().use {
            glow.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun emphasisKeepsTheOriginalRowHeightWithTranslation() {
        val oldWordMode = yos.music.player.data.libraries.SettingsLibrary.EnableWordByWordLyric
        yos.music.player.data.libraries.SettingsLibrary.EnableWordByWordLyric = true
        try {
            val timed = yos.music.player.code.utils.lrc.YosLyricLine(1000f, 3000f,
                listOf(YosLyricToken(1000f, 3000f, "爱琴海")), translation = "Aegean Sea")
            val ordinary = timed.copy(tokens = listOf(YosLyricToken(1000f, 1000f, "爱琴海")))
            compose.setContent {
                val measurer = rememberTextMeasurer()
                androidx.compose.foundation.layout.Column(Modifier.size(300.dp, 400.dp).background(Color(0xFF555555))) {
                    for (line in listOf(timed, ordinary)) {
                        androidx.compose.foundation.layout.Box(Modifier.testTag(if (line.hasWordTiming) "timed" else "ordinary")) {
                            NativeLyricRow(line = line, tokens = line.tokens, staticText = false,
                                active = true, focused = false, past = false, blur = 0f, nextStart = 4000f,
                                time = { 1700f }, showTranslation = true, showRomaji = false, mainColor = Color.White,
                                subColor = Color.White, measurer = measurer, fontFamily = null,
                                onClick = {}, onLongClick = {}, animationsEnabled = false)
                        }
                    }
                }
            }
            val timedHeight = compose.onNodeWithTag("timed").fetchSemanticsNode().boundsInRoot.height
            val ordinaryHeight = compose.onNodeWithTag("ordinary").fetchSemanticsNode().boundsInRoot.height
            org.junit.Assert.assertEquals("Glow must not add spacing between lyric rows",
                ordinaryHeight, timedHeight, 0.01f)
            val bitmap = compose.onNodeWithTag("timed").captureToImage().asAndroidBitmap()
            val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "emphasis-preview/row-overlap.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            yos.music.player.data.libraries.SettingsLibrary.EnableWordByWordLyric = oldWordMode
        }
    }

    @Test fun isolatedLetterGradientsStayInParagraphCoordinatesOnTheHardwareCanvas() {
        var origin = 0f
        var fieldWidth = 0f
        var lines = 0
        compose.setContent {
            val measurer = rememberTextMeasurer()
            val density = androidx.compose.ui.platform.LocalDensity.current
            val paragraph = androidx.compose.runtime.remember(measurer, density) {
                val text = "But they never did ever lived ebbing and flowing"
                val layout = measurer.measure(text, nativeIndependentGlyphStyle(TextStyle(fontSize = 30.sp)),
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = with(density) { 280.dp.roundToPx() }))
                lines = layout.lineCount
                nativeLyricParagraph(layout, listOf(YosLyricToken(1000f, 15000f, text)))
            }
            Canvas(Modifier.size(320.dp, 260.dp).background(Color.Black).testTag("gradient")) {
                origin = 12.dp.toPx()
                fieldWidth = 280.dp.toPx()
                val field = android.graphics.LinearGradient(0f, 0f, fieldWidth, 0f,
                    android.graphics.Color.RED, android.graphics.Color.BLUE, android.graphics.Shader.TileMode.CLAMP)
                translate(left = origin, top = origin) {
                    for (glyph in paragraph.glyphs) paragraph.surface.drawEmphasized(this, glyph,
                        field, Color.White, Color.White, 0.75.dp.toPx(), 0f)
                }
            }
        }
        val frame = compose.onNodeWithTag("gradient").captureToImage().asAndroidBitmap()
        assertTrue("The colour-coordinate regression must include wrapped English", lines >= 2)
        var checked = 0
        for (y in 0 until frame.height) for (x in 0 until frame.width) {
            val pixel = frame.getPixel(x, y)
            val red = android.graphics.Color.red(pixel)
            val blue = android.graphics.Color.blue(pixel)
            if (red + blue < 220) continue
            val expected = ((x + 0.5f - origin) / fieldWidth).coerceIn(0f, 1f)
            val observed = blue.toFloat() / (red + blue)
            org.junit.Assert.assertEquals("Every letter must sample the same field at $x,$y",
                expected, observed, 0.02f)
            checked++
        }
        assertTrue("Check substantial solid glyph ink, not an empty frame", checked > 400)
    }

}
