package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken

class NativeLyricInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingShareDuringAHoldClearsFeedbackBeforeTheLyricsBecomeInteractiveAgain() {
        val enabled = mutableStateOf(true)
        val holding = mutableStateOf(false)
        var shares = 0
        val line = YosLyricLine(1000f, 3000f, listOf(YosLyricToken(1000f, 3000f, "Held lyric")))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.width(330.dp).testTag("lyrics")) {
                NativeLyricRow(line, line.tokens, staticText = false, active = true,
                    focused = true, past = false, blur = 0f, nextStart = 5000f,
                    time = { 1500f }, showTranslation = false, showRomaji = false, mainColor = Color.White,
                    subColor = Color.Gray, measurer = rememberTextMeasurer(), fontFamily = null,
                    interactionEnabled = enabled.value, onPressChanged = { holding.value = it },
                    onClick = {}, onLongClick = { shares++; enabled.value = false })
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val target = compose.onNodeWithTag("lyrics")
        target.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertTrue(holding.value) }
        // Open the share sheet while the original pointer is still held down.
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { assertEquals(1, shares); assertFalse(holding.value) }
        target.performTouchInput { up() }
        compose.runOnIdle { enabled.value = true }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertFalse(holding.value) }
        target.performTouchInput { longClick(center) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(2, shares); assertFalse(holding.value) }
    }

    @Test fun holdingBlurredFutureAndPastRowsRetainsTheGestureWhenPageBlurClears() {
        val past = mutableStateOf(false)
        val holding = mutableStateOf(false)
        var seeks = 0
        var shares = 0
        val line = YosLyricLine(1000f, 3000f, listOf(YosLyricToken(1000f, 3000f, "Quiet lyric")))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.width(330.dp).background(Color.Black).testTag("lyrics")) {
                NativeLyricRow(line, line.tokens, staticText = false, active = false,
                    focused = false, past = past.value, blur = if (holding.value) 0f else 8f,
                    nextStart = 5000f, time = { if (past.value) 6000f else 0f },
                    showTranslation = false, showRomaji = false, mainColor = Color.White, subColor = Color.Gray,
                    measurer = rememberTextMeasurer(), fontFamily = null,
                    onPressChanged = { holding.value = it },
                    onClick = { seeks++ }, onLongClick = { shares++ })
            }
        }
        val target = compose.onNodeWithTag("lyrics")
        for (isPast in listOf(false, true)) {
            compose.runOnIdle { past.value = isPast }
            compose.mainClock.advanceTimeBy(500)
            target.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(400)
            compose.runOnIdle { assertTrue("Blur changes must not cancel DOWN", holding.value) }
            val pixels = target.captureToImage().toPixelMap()
            val y = with(compose.density) { 5.dp.roundToPx() }
            assertTrue("Quiet rows must show the same backdrop", pixels[pixels.width / 2, y].red > 0.04f)
            target.performTouchInput { cancel() }
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle {
                assertFalse(holding.value)
                assertEquals(0, seeks)
                assertEquals(if (isPast) 1 else 0, shares)
            }
            target.performTouchInput { longClick(center) }
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { assertFalse(holding.value); assertEquals(0, seeks) }
        }
        compose.runOnIdle { assertEquals(2, shares) }
    }

    @Test fun outgoingAndHiddenLyricsCannotSeekOrOpenShare() {
        val enabled = mutableStateOf(true)
        val opacity = mutableStateOf(1f)
        var seeks = 0
        var shares = 0
        val line = YosLyricLine(1000f, 2000f, listOf(YosLyricToken(1000f, 2000f, "Touch target")))
        compose.setContent {
            Box(Modifier.width(330.dp).graphicsLayer { alpha = opacity.value }.testTag("lyrics")) {
                NativeLyricRow(line, line.tokens, staticText = false, active = true,
                    focused = true, past = false, blur = 0f, nextStart = 4000f,
                    time = { 1500f }, showTranslation = false, showRomaji = false, mainColor = Color.White,
                    subColor = Color.Gray, measurer = rememberTextMeasurer(), fontFamily = null,
                    interactionEnabled = enabled.value,
                    onClick = { seeks++ }, onLongClick = { shares++ })
            }
        }
        val target = compose.onNodeWithTag("lyrics")
        target.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, seeks); enabled.value = false; opacity.value = 0.5f }
        target.performTouchInput { click(center); longClick(center) }
        compose.runOnIdle { assertEquals(1, seeks); assertEquals(0, shares); opacity.value = 0f }
        target.performTouchInput { click(center); longClick(center) }
        compose.runOnIdle { assertEquals(1, seeks); assertEquals(0, shares); enabled.value = true; opacity.value = 1f }
        target.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(2, seeks) }
    }

    @Test fun holdingShowsFeedbackAndCancellationClearsItWithoutSeeking() {
        var seeks = 0
        var shares = 0
        val line = YosLyricLine(1000f, 2000f, listOf(YosLyricToken(1000f, 2000f, "Hover lyric")))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.width(330.dp).background(Color.Black).testTag("lyrics")) {
                NativeLyricRow(line, line.tokens, staticText = true, active = false,
                    focused = false, past = false, blur = 0f, nextStart = 4000f,
                    time = { 1500f }, showTranslation = false, showRomaji = false, mainColor = Color.White,
                    subColor = Color.Gray, measurer = rememberTextMeasurer(), fontFamily = null,
                    onClick = { seeks++ }, onLongClick = { shares++ })
            }
        }
        val target = compose.onNodeWithTag("lyrics")
        fun surfaceBrightness(): Float {
            val pixels = target.captureToImage().toPixelMap()
            val y = with(compose.density) { 5.dp.roundToPx() }
            // Sample the top padding, away from text and outside the glyph renderer.
            return pixels[pixels.width / 2, y].red
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f, surfaceBrightness(), 0.01f)
        target.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(220)
        assertTrue("Holding must visibly highlight the row", surfaceBrightness() > 0.04f)
        target.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(0f, surfaceBrightness(), 0.01f)
        compose.runOnIdle { assertEquals(0, seeks); assertEquals(0, shares) }

        target.performTouchInput { longClick(center) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(0, seeks); assertEquals(1, shares) }
        assertEquals(0f, surfaceBrightness(), 0.01f)
    }
}
