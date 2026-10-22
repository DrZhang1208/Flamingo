package yos.music.player.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TabletPlayerLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullControlsRemainBelowArtworkAndBesideLyricsWhenTheirHeightChanges() {
        val controlsHeight = mutableStateOf(70.dp)
        compose.setContent {
            TabletPlayerLayout(
                interactionEnabled = true, onBack = {},
                modifier = Modifier.size(320.dp, 240.dp).testTag("player"),
                artwork = { Box(Modifier.fillMaxSize().background(Color.Red).testTag("artwork")) },
                controls = { Box(Modifier.fillMaxWidth().height(controlsHeight.value).testTag("controls")) },
                content = { Box(Modifier.fillMaxSize().testTag("lyrics")) }
            )
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val initialLyrics = bounds("lyrics")
        fun check() {
            val player = bounds("player")
            val artwork = bounds("artwork")
            val controls = bounds("controls")
            val lyrics = bounds("lyrics")
            assertEquals(artwork.left, controls.left, 0.5f)
            assertEquals(artwork.right, controls.right, 0.5f)
            assertEquals(artwork.bottom, controls.top, 0.5f)
            assertEquals(controls.bottom, player.bottom, 0.5f)
            assertTrue(controls.right <= lyrics.left + 0.5f)
            assertEquals(initialLyrics, lyrics)
        }
        check()
        compose.runOnIdle { controlsHeight.value = 110.dp }
        check()
    }
}
