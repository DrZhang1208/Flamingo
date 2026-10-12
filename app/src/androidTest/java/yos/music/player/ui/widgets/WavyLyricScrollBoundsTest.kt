package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WavyLyricScrollBoundsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedDownwardDragsDuringAnUnfinishedFollowKeepTheFirstLyricAtTheTop() {
        val state = WavyLyricsColumnState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WavyLyricsColumn(60, state, Modifier.size(320.dp, 400.dp).testTag("lyrics"),
                rowSpringParams = { WavySpringParams(0.9f, 15f, 90f) }) { index ->
                Box(Modifier.fillMaxWidth().height(72.dp).testTag("row$index")) { Text("Lyric $index") }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { state.jumpTo(0, false) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { state.jumpTo(40, true) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { state.offsetRowBy(-100_000f) }
        compose.mainClock.advanceTimeByFrame()
        val list = compose.onNodeWithTag("lyrics")
        val top = list.fetchSemanticsNode().boundsInRoot.top
        repeat(4) {
            list.performTouchInput { swipeDown(durationMillis = 80) }
            compose.mainClock.advanceTimeBy(80)
            val first = compose.onNodeWithTag("row0").fetchSemanticsNode().boundsInRoot
            assertEquals(top + state.viewportHeightPx * 0.0618f, first.top, 2f)
            assertTrue(first.bottom <= list.fetchSemanticsNode().boundsInRoot.bottom)
        }
    }

    @Test fun draggingAndFlingingAtTheEndsKeepTheBoundaryLyricVisible() {
        val state = WavyLyricsColumnState()
        compose.setContent {
            WavyLyricsColumn(20, state, Modifier.size(320.dp, 400.dp).testTag("lyrics"),
                rowSpringParams = { WavySpringParams(0.9f, 24f, 170f) }) { index ->
                Box(Modifier.fillMaxWidth().height(72.dp).testTag("row$index")) {
                    Text("Lyric $index")
                }
            }
        }
        compose.runOnIdle { state.offsetRowBy(100_000f); state.offsetRowBy(-60f) }
        val list = compose.onNodeWithTag("lyrics")
        list.performTouchInput { swipeUp(durationMillis = 80) }
        val last = compose.onNodeWithTag("row19").fetchSemanticsNode().boundsInRoot
        val viewport = list.fetchSemanticsNode().boundsInRoot
        assertTrue(last.top >= viewport.top)
        assertTrue(last.bottom <= viewport.bottom)
        repeat(3) { list.performTouchInput { swipeUp(durationMillis = 80) } }
        assertEquals(last.top, compose.onNodeWithTag("row19").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { state.offsetRowBy(-100_000f) }
        val first = compose.onNodeWithTag("row0").fetchSemanticsNode().boundsInRoot
        repeat(3) { list.performTouchInput { swipeDown(durationMillis = 80) } }
        assertEquals(first.top, compose.onNodeWithTag("row0").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertTrue(first.top >= viewport.top)
        assertTrue(first.bottom <= viewport.bottom)
    }
}
