package yos.music.player.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import yos.music.player.code.MediaController
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.objects.NowPlayingLyricState

class NativeLyricSeekPreviewPageTest {
    @get:Rule val compose = createComposeRule()
    private var liveController: androidx.media3.session.MediaController? = null
    private var oldHighlight = true
    private var oldUplift = true
    private val preview = LyricSeekPreviewState()
    private val seeks = mutableListOf<Int>()

    @Before fun isolatePlayback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            liveController = MediaController.mediaControl
            MediaController.mediaControl = null
            oldHighlight = SettingsLibrary.DebugLyricEnableHighlight
            oldUplift = SettingsLibrary.DebugLyricEnableUplift
            SettingsLibrary.DebugLyricEnableHighlight = true
            SettingsLibrary.DebugLyricEnableUplift = false
        }
        compose.mainClock.autoAdvance = false
    }

    @After fun restorePlayback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MediaController.mediaControl = liveController
            SettingsLibrary.DebugLyricEnableHighlight = oldHighlight
            SettingsLibrary.DebugLyricEnableUplift = oldUplift
        }
    }

    private fun showPage() {
        val tokens = listOf(YosLyricToken(0f, 6000f, "MMMMMMMM "), YosLyricToken(6000f, 12000f, "MMMMMMMM"))
        val state = NowPlayingLyricState(trackKey = "seek-preview", entries = listOf(
            YosLyricLine(0f, 12000f, tokens)), effectivePositionMs = 3000)
        compose.setContent {
            YosLyricView({ state }, { 3000 }, object : YosMediaEvent {
                override fun onSeek(position: Int) { seeks += position }
            }, translationLambda = { false }, weightLambda = { false },
                modifier = Modifier.size(350.dp, 600.dp).background(Color.Black).testTag("lyrics"),
                seekPreviewState = preview, onBackClick = {})
        }
        compose.mainClock.advanceTimeBy(500)
    }

    private fun brightInk(): Int {
        val pixels = compose.onNodeWithTag("lyrics").captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width)
            if (pixels[x, y].red > 0.8f) count++
        return count
    }

    @Test fun previewMovesWordHighlightBothWaysAndCancellationReturnsWithoutSeeking() {
        showPage()
        val baseline = brightInk()
        assertTrue(baseline > 0)
        compose.runOnIdle { preview.update(9000f) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("Highlight must follow the preview before the player seeks", brightInk() > baseline * 2)
        compose.runOnIdle { preview.update(3000f) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(baseline.toFloat(), brightInk().toFloat(), baseline * 0.15f)
        compose.runOnIdle { preview.update(9000f) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { preview.update(null) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(baseline.toFloat(), brightInk().toFloat(), baseline * 0.15f)
        assertTrue("A preview must never submit a seek", seeks.isEmpty())
    }

    @Test fun releaseRetainsTheCommittedHighlightWhileThePlayerStillReportsTheOldPosition() {
        showPage()
        compose.runOnIdle { preview.update(9000f) }
        compose.mainClock.advanceTimeByFrame()
        val expected = brightInk()
        compose.runOnIdle {
            preview.commit(9000f)
            preview.update(null)
        }
        for (delta in listOf(16L, 64L, 300L)) {
            compose.mainClock.advanceTimeBy(delta)
            assertEquals("Old player reports must not snap highlight back after UP",
                expected.toFloat(), brightInk().toFloat(), expected * 0.15f)
        }
        assertTrue(seeks.isEmpty())
    }

    @Test fun manualPreviewRemovesInactiveLineBlurOnTheFirstFrame() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val state = NowPlayingLyricState(trackKey = "preview-blur", entries = (0..5).map { index ->
            val start = index * 6000f
            YosLyricLine(start, start + 6000f, listOf(
                YosLyricToken(start, start + 3000f, "MMMM "),
                YosLyricToken(start + 3000f, start + 6000f, "MMMM")))
        }, effectivePositionMs = 3000)
        val blurEnabled = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            YosLyricView({ state }, { 3000 }, object : YosMediaEvent {
                override fun onSeek(position: Int) = Unit
            }, translationLambda = { false }, blurLambda = { blurEnabled.value }, weightLambda = { false },
                modifier = Modifier.size(320.dp, 240.dp).background(Color.Black).testTag("preview"),
                seekPreviewState = preview, onBackClick = {})
        }
        compose.mainClock.advanceTimeBy(500)
        fun pixels() = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        val normallyBlurred = pixels()
        compose.runOnIdle { blurEnabled.value = false }
        compose.mainClock.advanceTimeBy(250)
        assertFalse("Inactive lyrics should normally have blur", normallyBlurred.sameAs(pixels()))
        compose.runOnIdle { blurEnabled.value = true }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { preview.update(3000f) }
        compose.mainClock.advanceTimeByFrame()
        val firstPreviewFrame = pixels()
        // Compare the same viewport at the same fixed pointer time. Separate
        // vertically stacked viewports can be clipped by the activity window.
        compose.runOnIdle { blurEnabled.value = false }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("Seek preview must immediately match the sharp reference without fading blur out",
            firstPreviewFrame.sameAs(pixels()))
    }

    @Test fun previewInsideOneLineMovesImmediatelyAndHoldsStillWhenTheFingerStops() {
        val state = NowPlayingLyricState(trackKey = "smooth-seek-preview", entries = (0..11).map { index ->
            val start = index * 6000f
            YosLyricLine(start, start + 6000f, listOf(YosLyricToken(start, start + 6000f, "MMMMMM $index")))
        }, effectivePositionMs = 3000)
        compose.setContent {
            YosLyricView({ state }, { 3000 }, object : YosMediaEvent {
                override fun onSeek(position: Int) { seeks += position }
            }, translationLambda = { false }, blurLambda = { false }, weightLambda = { false },
                modifier = Modifier.size(350.dp, 600.dp).background(Color.Black).testTag("lyrics"),
                seekPreviewState = preview, onBackClick = {})
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { preview.update(24000f) }
        compose.mainClock.advanceTimeByFrame()
        val start = compose.onNodeWithTag("lyrics").captureToImage().asAndroidBitmap()
        compose.runOnIdle { preview.update(25500f) }
        compose.mainClock.advanceTimeByFrame()
        val quarter = compose.onNodeWithTag("lyrics").captureToImage().asAndroidBitmap()
        compose.runOnIdle { preview.update(27000f) }
        compose.mainClock.advanceTimeByFrame()
        val middle = compose.onNodeWithTag("lyrics").captureToImage().asAndroidBitmap()
        assertFalse(start.sameAs(quarter))
        assertFalse("Movement must happen inside the same lyric line", quarter.sameAs(middle))
        compose.mainClock.advanceTimeBy(300)
        assertTrue("Holding preview time must not let a tween continue scrolling", middle.sameAs(
            compose.onNodeWithTag("lyrics").captureToImage().asAndroidBitmap()))
        compose.runOnIdle { preview.update(25500f) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("Returning to the same pointer time must restore the same frame", quarter.sameAs(
            compose.onNodeWithTag("lyrics").captureToImage().asAndroidBitmap()))
        assertTrue(seeks.isEmpty())
    }

    @Test fun scrubbingThroughOpeningCountdownNeverReversesItsScrollAtTheEnd() {
        val state = NowPlayingLyricState(trackKey = "fractional-countdown", entries = listOf(
            YosLyricLine(0f, 6000f, emptyList(), agent = YosLyricLine.COUNTDOWN_AGENT),
            YosLyricLine(6000f, 12000f, listOf(YosLyricToken(6000f, 12000f, "First lyric")),
                translation = "countdown target"),
            YosLyricLine(12000f, 18000f, listOf(YosLyricToken(12000f, 18000f, "Next lyric")))
        ), effectivePositionMs = 3000)
        compose.setContent {
            YosLyricView({ state }, { 3000 }, object : YosMediaEvent {
                override fun onSeek(position: Int) { seeks += position }
            }, translationLambda = { true }, blurLambda = { false }, weightLambda = { false },
                modifier = Modifier.size(350.dp, 600.dp).background(Color.Black),
                seekPreviewState = preview, onBackClick = {})
        }
        compose.mainClock.advanceTimeBy(500)
        var previous = Float.POSITIVE_INFINITY
        for (time in listOf(0f, 1500f, 3000f, 4500f, 5500f, 5750f, 5900f, 6000f)) {
            compose.runOnIdle { preview.update(time) }
            compose.mainClock.advanceTimeByFrame()
            val top = compose.onNodeWithText("countdown target").fetchSemanticsNode().boundsInRoot.top
            assertTrue("A closing dot animation must not reverse finger-driven scrolling", top <= previous + 2f)
            previous = top
        }
        assertTrue(seeks.isEmpty())
    }
}
