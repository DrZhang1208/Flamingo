package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricCountdownLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hiddenMarkerLeavesNoGapAndItsHeightChangesContinuously() {
        val time = mutableFloatStateOf(0f)
        var followingTop = 0f
        compose.setContent {
            Column {
                Box(Modifier.lyricCountdownLayout({ time.floatValue }, 1000f, 8000f).size(100.dp))
                Box(Modifier.size(10.dp).onGloballyPositioned { followingTop = it.positionInRoot().y })
            }
        }
        var baseline = 0f
        compose.runOnIdle { baseline = followingTop; time.floatValue = 2000f }
        var full = 0f
        compose.runOnIdle { full = followingTop - baseline; assertTrue(full > 0f); time.floatValue = 1180f }
        compose.runOnIdle { assertEquals(full / 2f, followingTop - baseline, 1f); time.floatValue = 7510f }
        compose.runOnIdle { assertEquals(full / 2f, followingTop - baseline, 1f); time.floatValue = 7760f }
        compose.runOnIdle { assertEquals("No remaining slot when the next lyric takes focus", baseline, followingTop, 0f); time.floatValue = 8000f }
        compose.runOnIdle { assertEquals(baseline, followingTop, 0f); time.floatValue = 999f }
        compose.runOnIdle { assertEquals(baseline, followingTop, 0f) }
    }

    @Test fun nextLyricNeverMovesBackDownDuringCountdownHandoff() {
        val time = mutableFloatStateOf(10900f)
        val state = WavyLyricsColumnState()
        val positions = mutableListOf<Float>()
        compose.setContent {
            WavyLyricsColumn(4, state, Modifier.size(320.dp, 500.dp),
                rowSpringParams = { WavySpringParams(0.9f, 24f, 170f) }) { index ->
                if (index == 1) {
                    Box(Modifier.lyricCountdownLayout({ time.floatValue }, 1000f, 12000f)
                        .fillMaxWidth().height(64.dp))
                } else {
                    Box(Modifier.fillMaxWidth().height(100.dp).onGloballyPositioned {
                        if (index == 2) positions += it.positionInRoot().y
                    })
                }
            }
        }
        compose.runOnIdle { state.jumpTo(1, false) }
        compose.runOnIdle { positions.clear() }
        for (ms in 10900..12300 step 16) {
            compose.runOnIdle {
                time.floatValue = ms.toFloat()
                if (ms >= nativeLyricVisualStartMs(12000f)) state.jumpTo(2, true)
            }
        }
        compose.runOnIdle {
            assertTrue("Capture actual layout positions across the exit", positions.size > 5)
            positions.zipWithNext().forEach { (before, after) ->
                assertTrue("Next lyric reversed during handoff: $before -> $after", after <= before + 1f)
            }
            assertEquals(0, state.rowHeight(1))
        }
    }
}
