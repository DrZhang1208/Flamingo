package yos.music.player.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import yos.music.player.data.libraries.SettingsLibrary

class NativeLyricSeekAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun skippedRowsDoNotFlashBrightOrDimOnForwardAndBackwardSeeks() {
        val time = mutableFloatStateOf(-2000f)
        val past = mutableStateOf(false)
        val revision = mutableIntStateOf(0)
        val line = YosLyricLine(1000f, 2000f, listOf(YosLyricToken(1000f, 2000f, "Skipped lyrics")))
        val oldHighlight = SettingsLibrary.DebugLyricEnableHighlight
        val oldUplift = SettingsLibrary.DebugLyricEnableUplift
        SettingsLibrary.DebugLyricEnableHighlight = true
        SettingsLibrary.DebugLyricEnableUplift = false
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent {
                Box(Modifier.width(330.dp).background(Color.Black).testTag("row")) {
                    NativeLyricRow(line, line.tokens, staticText = false, active = false,
                        focused = false, past = past.value, blur = 0f, nextStart = 4000f,
                        seekRevision = revision.intValue, time = { time.floatValue },
                        showTranslation = false, showRomaji = false, mainColor = Color.White, subColor = Color.White,
                        measurer = rememberTextMeasurer(), fontFamily = null, onClick = {}, onLongClick = {})
                }
            }
            compose.mainClock.advanceTimeByFrame()
            fun coverage(): Double {
                val pixels = compose.onNodeWithTag("row").captureToImage().toPixelMap()
                var total = 0.0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) total += pixels[x,y].red
                return total
            }
            val baseline = coverage()
            assertTrue(baseline > 0)
            for (forward in listOf(true, false, true, false)) {
                compose.runOnIdle {
                    revision.intValue++
                    past.value = forward
                    time.floatValue = if (forward) 5000f else -2000f
                }
                for (delta in listOf(16L, 64L, 200L, 500L)) {
                    compose.mainClock.advanceTimeBy(delta)
                    val ratio = coverage() / baseline
                    assertTrue("Skipped row flashed: forward=$forward ratio=$ratio", ratio in 0.85..1.15)
                }
            }
        } finally {
            SettingsLibrary.DebugLyricEnableHighlight = oldHighlight
            SettingsLibrary.DebugLyricEnableUplift = oldUplift
        }
    }
}
