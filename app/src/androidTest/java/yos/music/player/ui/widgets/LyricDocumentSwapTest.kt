package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class LyricDocumentSwapTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingAnAnimatingHighIndexListToSingletonDoesNotReuseOldIndexes() {
        val entries = mutableStateOf((0..19).toList())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val document = entries.value
            val state = remember(document) { WavyLyricsColumnState().apply {
                updateItemCount(document.size)
                updateViewport(600)
                jumpTo(if (document.size > 12) 12 else 0, false)
            } }
            LaunchedEffect(state) {
                if (document.size > 13) state.jumpTo(13, true)
            }
            key(state) {
                WavyLyricsColumn(document.size, state, Modifier.size(300.dp, 600.dp),
                    rowSpringParams = { WavySpringParams(0.9f, 15f, 90f) }) { index ->
                    Text("${document[index]}", Modifier.height(80.dp).testTag("row-$index"))
                }
            }
        }
        repeat(10) {
            compose.runOnIdle { entries.value = listOf(0) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("row-0").assertExists()
            compose.runOnIdle { entries.value = (0..19).toList() }
            compose.mainClock.advanceTimeByFrame()
        }
        compose.runOnIdle { entries.value = listOf(0) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("row-0").assertExists()
    }
}
