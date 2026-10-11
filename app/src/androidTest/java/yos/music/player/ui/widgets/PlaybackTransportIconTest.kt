package yos.music.player.ui.widgets

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

class PlaybackTransportIconTest {
    @get:Rule val compose = createComposeRule()
    @Test fun loadingIsVisibleWhenEngineHasStoppedPlayingToWaitForData() {
        compose.setContent { PlaybackTransportIcon(playing = false, buffering = true) }
        compose.onNodeWithContentDescription("Buffering").assertExists()
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
    }
}
