package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.data.objects.NowPlayingLyricState

class NativeCountdownEntryPageTest {
    @get:Rule val compose = createComposeRule()
    private var liveController: androidx.media3.session.MediaController? = null

    @Before fun useTheTestPlaybackClock() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            liveController = yos.music.player.code.MediaController.mediaControl
            yos.music.player.code.MediaController.mediaControl = null
        }
    }

    @After fun restoreLiveController() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            yos.music.player.code.MediaController.mediaControl = liveController
        }
    }

    @Test fun openingCountdownStaysAtTheHighlightPositionWhileItsSlotExpands() {
        val time = mutableFloatStateOf(0f)
        val state = WavyLyricsColumnState(setOf(0))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WavyLyricsColumn(3, state, Modifier.size(320.dp, 600.dp).testTag("lyrics"),
                rowSpringParams = { WavySpringParams(0.9f, 24f, 170f) }) { index ->
                if (index == 0) {
                    Canvas(Modifier.testTag("dots").lyricCountdownLayout({ time.floatValue }, 0f, 12000f)
                        .fillMaxWidth().height(64.dp)) {
                        drawNativeCountdown(time.floatValue, 0f, 12000f, Color.White)
                    }
                } else {
                    Canvas(Modifier.fillMaxWidth().height(100.dp)) {}
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { state.followTo(0, false) }
        compose.mainClock.advanceTimeByFrame()
        val expectedTop = compose.onNodeWithTag("lyrics").fetchSemanticsNode().boundsInRoot.top +
            state.viewportHeightPx * 0.0618f
        for (ms in 32..512 step 16) {
            compose.runOnIdle { time.floatValue = ms.toFloat(); state.followTo(0, true) }
            compose.mainClock.advanceTimeByFrame()
            assertEquals("Opening countdown moved above its highlight anchor at $ms", expectedTop,
                compose.onNodeWithTag("dots").fetchSemanticsNode().boundsInRoot.top, 1f)
        }
    }

    @Test fun realPageKeepsFollowingLyricsStillWhileDotsAppear() {
        val entries = listOf(
            YosLyricLine(1000f, 3000f, listOf(YosLyricToken(1000f, 3000f, "Before"))),
            YosLyricLine(3000f, 14000f, emptyList(), agent = YosLyricLine.COUNTDOWN_AGENT),
            YosLyricLine(14000f, 17000f, listOf(YosLyricToken(14000f, 17000f, "After")),
                translation = "First following lyric"),
            YosLyricLine(17000f, 20000f, listOf(YosLyricToken(17000f, 20000f, "Later")),
                translation = "Second following lyric")
        )
        val state = NowPlayingLyricState(trackKey = "countdown-entry", entries = entries,
            effectivePositionMs = 2900, generation = 1)
        var playbackMs = 2900
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YosLyricView({ state }, { playbackMs }, object : YosMediaEvent {
                override fun onSeek(position: Int) = Unit
            }, translationLambda = { true }, weightLambda = { false },
                modifier = Modifier.size(320.dp, 600.dp), renderActive = true, onBackClick = {})
        }
        compose.mainClock.advanceTimeBy(1000)
        val following = listOf("First following lyric", "Second following lyric")
        val before = following.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        for (ms in 2980..3500 step 16) {
            compose.runOnIdle { playbackMs = ms }
            compose.mainClock.advanceTimeBy(96)
            following.forEachIndexed { index, text ->
                assertEquals("$text moved while entering dots at $ms", before[index],
                    compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top, 1f)
            }
        }
    }
}
