package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
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

    @Test fun progressPreviewMovesAllRowsTogetherInsteadOfReplacingTheAnchoredLine() {
        val state = WavyLyricsColumnState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WavyLyricsColumn(20, state, Modifier.size(320.dp, 400.dp).testTag("lyrics"),
                animationsEnabled = false,
                rowSpringParams = { WavySpringParams(0.9f, 15f, 90f) }) { index ->
                Box(Modifier.fillMaxWidth().height(72.dp).testTag("row$index")) { Text("Lyric $index") }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { state.jumpTo(2, false) }
        compose.mainClock.advanceTimeByFrame()
        fun top(index: Int) = compose.onNodeWithTag("row$index").fetchSemanticsNode().boundsInRoot.top
        val before3 = top(3)
        val before4 = top(4)
        val rowHeight = before4 - before3
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f, 0.5f, 0f)) {
            compose.runOnIdle { state.previewAt(LyricSeekScrollTarget(2, 3, fraction)) }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(before3 - rowHeight * fraction, top(3), 2f)
            assertEquals(before3 - top(3), before4 - top(4), 2f)
            val held = top(3)
            compose.mainClock.advanceTimeBy(160)
            assertEquals("A stationary finger must not leave a tween running", held, top(3), 1f)
        }
        compose.runOnIdle { state.previewAt(LyricSeekScrollTarget(2, 3, 1f)) }
        compose.mainClock.advanceTimeByFrame()
        val boundary = top(3)
        compose.runOnIdle { state.previewAt(LyricSeekScrollTarget(3, 4, 0f)) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(boundary, top(3), 1f)
    }

    @Test fun directPreviewPlacesEachTargetOnTheFirstFrameWithoutSpringDisplacement() {
        val state = WavyLyricsColumnState()
        val animated = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WavyLyricsColumn(40, state, Modifier.size(320.dp, 400.dp).testTag("lyrics"),
                animationsEnabled = animated.value,
                rowSpringParams = { WavySpringParams(0.9f, 15f, 90f) }) { index ->
                Box(Modifier.fillMaxWidth().height(72.dp).testTag("row$index")) { Text("Lyric $index") }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { state.jumpTo(0, false) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { state.jumpTo(12, true) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { animated.value = false }
        for (target in listOf(12, 25, 3, 30, 0)) {
            compose.runOnIdle { state.jumpTo(target, false) }
            compose.mainClock.advanceTimeByFrame()
            val top = compose.onNodeWithTag("lyrics").fetchSemanticsNode().boundsInRoot.top
            for (elapsed in listOf(0L, 80L, 300L)) {
                compose.mainClock.advanceTimeBy(elapsed)
                assertEquals("Target must not chase a spring during a progress preview",
                    top + state.viewportHeightPx * 0.0618f,
                    compose.onNodeWithTag("row$target").fetchSemanticsNode().boundsInRoot.top, 2f)
            }
        }
    }

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
