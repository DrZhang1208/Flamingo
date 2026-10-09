package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class PlaybackTransportIconTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bufferingShowsProgressAndPauseClearsIt() {
        val playing = mutableStateOf(true)
        val buffering = mutableStateOf(true)
        compose.setContent {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                PlaybackTransportIcon(playing.value, buffering.value)
            }
        }
        compose.onNodeWithContentDescription("Buffering").assertExists()
        compose.runOnIdle { playing.value = false }
        compose.onNodeWithContentDescription("Buffering").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play").assertExists()
        compose.runOnIdle { buffering.value = false; playing.value = true }
        compose.onNodeWithContentDescription("Pause").assertExists()
    }
}
