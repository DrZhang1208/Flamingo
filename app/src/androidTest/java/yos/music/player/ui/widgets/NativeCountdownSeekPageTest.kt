package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.data.objects.NowPlayingLyricState

class NativeCountdownSeekPageTest {
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

    @Test fun clickingTheNextLyricDuringInterludeAnimatesInsteadOfSnapping() {
        val entries = listOf(
            YosLyricLine(0f, 1000f, listOf(YosLyricToken(0f, 1000f, "Before"))),
            YosLyricLine(1000f, 12000f, emptyList(), agent = YosLyricLine.COUNTDOWN_AGENT),
            YosLyricLine(12000f, 15000f, listOf(YosLyricToken(12000f, 15000f, "After interlude")),
                translation = "target translation"),
            YosLyricLine(15000f, 18000f, listOf(YosLyricToken(15000f, 18000f, "Next")))
        )
        val state = NowPlayingLyricState(trackKey = "countdown-seek", entries = entries,
            effectivePositionMs = 5000, generation = 1)
        val seeks = mutableListOf<Int>()
        var playbackPosition = 5000
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YosLyricView({ state }, { playbackPosition }, object : YosMediaEvent {
                override fun onSeek(position: Int) {
                    seeks += position
                    // A successful seek must also be acknowledged by the fake
                    // player, rather than reporting the old position forever.
                    playbackPosition = position
                }
            }, translationLambda = { true }, weightLambda = { false },
                modifier = Modifier.size(320.dp, 600.dp), renderActive = true, onBackClick = {})
        }
        compose.mainClock.advanceTimeBy(800)
        val target = compose.onNodeWithText("target translation")
        target.performClick()
        compose.mainClock.advanceTimeByFrame()
        val initial = target.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(500)
        val settled = target.fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            assertEquals(listOf(12000), seeks)
            assertTrue("Target lyric skipped the scroll: $initial -> $settled", initial > settled + 10f)
        }
    }

    @Test fun clickingTheFirstLyricDuringOpeningCountdownRetainsItsScrollAnimation() {
        val entries = listOf(
            YosLyricLine(0f, 12000f, emptyList(), agent = YosLyricLine.COUNTDOWN_AGENT),
            YosLyricLine(12000f, 15000f, listOf(YosLyricToken(12000f, 15000f, "First lyric")),
                translation = "opening seek target"),
            YosLyricLine(15000f, 18000f, listOf(YosLyricToken(15000f, 18000f, "Next")))
        )
        val state = NowPlayingLyricState(trackKey = "opening-countdown-seek", entries = entries,
            effectivePositionMs = 5000, generation = 1)
        var playbackPosition = 5000
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YosLyricView({ state }, { playbackPosition }, object : YosMediaEvent {
                override fun onSeek(position: Int) { playbackPosition = position }
            }, translationLambda = { true }, weightLambda = { false },
                modifier = Modifier.size(320.dp, 600.dp), onBackClick = {})
        }
        compose.mainClock.advanceTimeBy(800)
        val target = compose.onNodeWithText("opening seek target")
        val before = target.fetchSemanticsNode().boundsInRoot.top
        target.performClick()
        compose.mainClock.advanceTimeByFrame()
        val initial = target.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(500)
        val settled = target.fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            assertEquals(12000, playbackPosition)
            assertTrue("Opening countdown collapsed straight to the destination: $initial -> $settled",
                initial > settled + 10f)
            assertTrue("The first seek frame lost the countdown slot: $before -> $initial",
                kotlin.math.abs(initial - before) < 12f)
        }
    }
}
