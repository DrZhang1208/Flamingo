package yos.music.player.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaybackSliderContinuityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun volumeGestureStaysFractionalDespiteImmediateSystemBroadcastsThenSnapsOnUp() {
        compose.mainClock.autoAdvance = false
        val state = VolumeSliderState(5, 10)
        var systemStep = 5
        compose.setContent {
            PlaybackVolumeTrack(state, readSystemStep = { systemStep },
                onStepChange = {
                    systemStep = it
                    state.report(it, compose.mainClock.currentTime)
                }, onInteraction = {}, onRelease = {},
                modifier = Modifier.width(300.dp).testTag("volume"),
                nowMillis = { compose.mainClock.currentTime })
        }
        compose.mainClock.advanceTimeByFrame()
        val slider = compose.onNodeWithTag("volume")
        val width = slider.fetchSemanticsNode().size.width.toFloat()
        slider.performTouchInput {
            down(center)
            moveBy(Offset(width * 0.12f, 0f)) // Cross touch slop to begin dragging.
        }
        var before = 0f
        compose.runOnIdle { before = state.position; assertTrue(state.dragging) }
        slider.performTouchInput { moveBy(Offset(width * 0.017f, 0f)) }
        compose.runOnIdle {
            assertEquals(before + 0.017f, state.position, 0.001f)
            assertTrue(kotlin.math.abs(state.position * 10 - systemStep) > 0.01f)
        }
        var release = 0f
        compose.runOnIdle { release = state.position }
        slider.performTouchInput { up() }
        compose.runOnIdle { assertEquals(release, state.position, 0f) }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertEquals(systemStep / 10f, state.position, 0f)
            assertFalse(state.animating)
        }
    }

    @Test fun restoredPageRetainsPaintedProgressAndAnimatesToLatestPosition() {
        val restoration = StateRestorationTester(compose)
        val visible = mutableStateOf(true)
        val track = mutableStateOf("first")
        lateinit var progress: PlayerProgressState
        restoration.setContent {
            progress = rememberSaveable(track.value, key = "progress_${track.value}",
                saver = PlayerProgressState.Saver) { PlayerProgressState(10000f, 100000) }
            Box(Modifier.width(300.dp).background(Color.Black)) {
                if (visible.value) PlaybackSeekTrack(track.value, { progress.positionMs },
                    { progress.durationMs }, { progress.livePositionMs }, progress.dragging,
                    { false }, {}, { _, _ -> }, {}, Modifier.testTag("progress"))
            }
        }
        compose.runOnIdle {
            progress.frame(1000, false, 1f, 10000f)
            progress.begin()
            progress.preview(30000f, false, 1010)
            progress.finish(30000f, 1020)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(30000f, progress.positionMs, 0f)
            progress.restartFrames()
            progress.frame(10000, false, 1f, 80000f)
            assertEquals(30000f, progress.positionMs, 0f)
            assertEquals(80000f, progress.livePositionMs, 0f)
        }
        val first = compose.onNodeWithTag("progress").captureToImage().toPixelMap()
        compose.runOnIdle { progress.frame(10140, false, 1f, 80000f) }
        val midway = compose.onNodeWithTag("progress").captureToImage().toPixelMap()
        // At 45%, the first frame is unfilled and the midpoint is filled.
        val x = (first.width * 0.45f).toInt()
        assertTrue(midway[x, midway.height / 2].red > first[x, first.height / 2].red + 0.3f)
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { visible.value = true }
        compose.runOnIdle {
            assertEquals(55000f, progress.positionMs, 0.01f)
            progress.restartFrames()
            assertEquals(55000f, progress.frame(20000, false, 1f, 90000f), 0.01f)
            assertEquals(90000f, progress.frame(20280, false, 1f, 90000f), 0f)
        }
        compose.runOnIdle { track.value = "second" }
        compose.runOnIdle { assertEquals(10000f, progress.positionMs, 0f) }
    }
}
