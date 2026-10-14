package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import yos.music.player.code.MediaController
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.data.objects.NowPlayingLyricState

class NativeLyricResumePageTest {
    @get:Rule val compose = createComposeRule()
    private var liveController: androidx.media3.session.MediaController? = null

    @Before fun useTheTestPlaybackClock() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            liveController = MediaController.mediaControl
            MediaController.mediaControl = null
        }
    }

    @After fun restoreLiveController() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MediaController.mediaControl = liveController
        }
    }

    @Test fun returningFromLongPressSelectionAnimatesDirectlyToTheLatestLine() {
        val entries = (0..5).map { index ->
            val start = index * 4000f
            YosLyricLine(start, start + 4000f,
                listOf(YosLyricToken(start, start + 4000f, "Lyric $index")),
                translation = "translation $index")
        }
        val state = NowPlayingLyricState(trackKey = "selection-resume", entries = entries,
            effectivePositionMs = 1000, generation = 1)
        val active = mutableStateOf(true)
        var playbackPosition = 1000
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YosLyricView({ state }, { playbackPosition }, object : YosMediaEvent {
                override fun onSeek(position: Int) = Unit
            }, translationLambda = { true }, weightLambda = { false },
                modifier = Modifier.size(320.dp, 600.dp), renderActive = active.value,
                onLineLongPress = { active.value = false }, onBackClick = {})
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("translation 0").performTouchInput { longClick(center) }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertFalse(active.value); playbackPosition = 9000 }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { active.value = true }
        compose.mainClock.advanceTimeByFrame()
        val target = compose.onNodeWithText("translation 2")
        val initial = target.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(500)
        val settled = target.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Return snapped to the latest line: $initial -> $settled", initial > settled + 10f)
    }
}
