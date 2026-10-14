package yos.music.player.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.code.utils.lrc.YosUIConfig
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.objects.NowPlayingLyricState

class LyricTranslationToggleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activeTranslationIconRevealsTheBackdropLikeShuffle() {
        val backdrop = Color(0xFF254060)
        compose.setContent {
            Box(Modifier.background(backdrop)) {
                LyricTranslationButton(true, {}, Modifier.testTag("translation"))
            }
        }
        val pixels = compose.onNodeWithTag("translation").captureToImage().toPixelMap()
        var holes = 0
        var brightFill = 0
        for (y in pixels.height / 5 until pixels.height * 4 / 5) {
            for (x in pixels.width / 5 until pixels.width * 4 / 5) {
                val pixel = pixels[x, y]
                assertTrue("Active icon must not paint black over the backdrop", pixel.red >= backdrop.red - 0.02f)
                if (kotlin.math.abs(pixel.red - backdrop.red) < 0.02f &&
                    kotlin.math.abs(pixel.green - backdrop.green) < 0.02f &&
                    kotlin.math.abs(pixel.blue - backdrop.blue) < 0.02f) holes++
                if (pixel.red > 0.85f) brightFill++
            }
        }
        assertTrue("The glyph must reveal the coloured backdrop", holes > 4)
        assertTrue("The selected button must retain its bright fill", brightFill > 4)
    }

    @Test fun translationKeepsTheSameColorWhenPlaybackFocusChanges() {
        val focused = mutableStateOf(true)
        val translation = "翻译歌词"
        val line = YosLyricLine(0f, 5000f, listOf(YosLyricToken(0f, 5000f, "Lyrics")), translation)
        compose.setContent {
            val config = YosUIConfig()
            val measurer = rememberTextMeasurer()
            Box(Modifier.size(320.dp, 180.dp).background(Color.Black)) {
                NativeLyricRow(line, line.tokens, staticText = false, active = focused.value,
                    focused = focused.value, past = false, blur = 0f, nextStart = null,
                    time = { 1000f }, showTranslation = true,
                    mainColor = Color(config.mainTextBasicColor), subColor = Color(config.subTextBasicColor),
                    measurer = measurer, fontFamily = null, onClick = {}, onLongClick = {},
                    animationsEnabled = false)
            }
        }
        fun brightestInk(): Float {
            val pixels = compose.onNodeWithText(translation).captureToImage().toPixelMap()
            var brightest = 0f
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                brightest = maxOf(brightest, color.red * color.alpha)
            }
            return brightest
        }
        val initialBrightness = brightestInk()
        assertTrue("Translation must always retain unfocused brightness", initialBrightness in 0.2f..0.45f)
        compose.runOnIdle { focused.value = false }
        assertEquals("Translation brightness must not follow playback focus", initialBrightness, brightestInk(), 0.01f)
    }

    @Test fun repeatedTapsRefreshButtonAndLyricsWithoutLeavingThePageAndSettingsStayInSync() {
        val original = SettingsLibrary.NowPlayingTranslation
        val translation = "穿过人山人海"
        val state = NowPlayingLyricState(trackKey = "translation-toggle", entries = listOf(
            YosLyricLine(0f, 5000f, listOf(YosLyricToken(0f, 5000f, "Walking through a crowd")), translation)))
        try {
            SettingsLibrary.NowPlayingTranslation = false
            compose.setContent {
                val visible by SettingsLibrary.nowPlayingTranslationState
                Column {
                    Box(Modifier.background(Color.Black)) {
                        LyricTranslationButton(visible, { SettingsLibrary.NowPlayingTranslation = it },
                            Modifier.testTag("translation"))
                    }
                    YosLyricView({ state }, { 1000 }, object : YosMediaEvent {
                        override fun onSeek(position: Int) = Unit
                    }, translationLambda = { visible }, weightLambda = { false },
                        modifier = Modifier.size(320.dp, 500.dp), renderActive = false, onBackClick = {})
                }
            }
            val button = compose.onNodeWithTag("translation")
            button.assertIsOff()
            val background = button.captureToImage().toPixelMap()
            assertEquals("Disabled control keeps the same 10% white base as the vocal control",
                0.1f, background[background.width / 2, background.height / 8].red, 0.02f)
            repeat(6) { index ->
                val expected = index % 2 == 0
                button.performClick()
                compose.runOnIdle { assertEquals(expected, SettingsLibrary.NowPlayingTranslation) }
                if (expected) {
                    button.assertIsOn()
                    compose.onNodeWithText(translation).assertExists()
                } else {
                    button.assertIsOff()
                    compose.onNodeWithText(translation).assertDoesNotExist()
                }
            }
            // Changes from Settings also update the same page and button immediately.
            compose.runOnIdle { SettingsLibrary.NowPlayingTranslation = true }
            button.assertIsOn()
            compose.onNodeWithText(translation).assertExists()
        } finally { compose.runOnIdle { SettingsLibrary.NowPlayingTranslation = original } }
    }
}
