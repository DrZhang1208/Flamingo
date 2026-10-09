package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
        compose.runOnIdle { assertEquals(full / 2f, followingTop - baseline, 1f); time.floatValue = 8000f }
        compose.runOnIdle { assertEquals(baseline, followingTop, 0f); time.floatValue = 999f }
        compose.runOnIdle { assertEquals(baseline, followingTop, 0f) }
    }
}
