package yos.music.player.ui.widgets

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricPreviewIdleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun idleTimerRestartsAfterMoreInputAndDoesNotRunDuringScrolling() {
        val state = WavyLyricsColumnState()
        compose.mainClock.autoAdvance = false
        compose.setContent { LyricPreviewResumeEffect(state) }
        compose.runOnIdle { state.startUserScroll() }
        compose.mainClock.advanceTimeBy(6000)
        compose.runOnIdle { assertTrue(state.isPreviewing) }
        compose.runOnIdle { state.finishUserScroll() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle { assertTrue(state.isPreviewing); state.startUserScroll() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { state.finishUserScroll() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle { assertTrue(state.isPreviewing) }
        compose.mainClock.advanceTimeBy(1100)
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse(state.isPreviewing) }
    }
}
