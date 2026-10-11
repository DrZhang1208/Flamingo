package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken

class NativeLyricInteractionTest {
    @get:Rule val compose = createComposeRule()

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
                    time = { 1500f }, showTranslation = false, mainColor = Color.White,
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
}
