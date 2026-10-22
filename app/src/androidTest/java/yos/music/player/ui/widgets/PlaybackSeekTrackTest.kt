package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaybackSeekTrackTest {
    @get:Rule val compose = createComposeRule()
    private val position = mutableFloatStateOf(30_000f)
    private val live = mutableFloatStateOf(30_000f)
    private val dragging = mutableStateOf(false)
    private val cancelled = mutableStateOf(false)
    private val cancelTop = mutableFloatStateOf(Float.POSITIVE_INFINITY)
    private val seeks = mutableListOf<Float>()

    private fun showTrack() {
        compose.setContent {
            Box(Modifier.fillMaxWidth().height(380.dp).testTag("surface")) {
                PlaybackSeekTrack("track", { position.floatValue }, { 180_000L }, { live.floatValue }, dragging.value,
                    { it.y >= cancelTop.floatValue },
                    onStart = { dragging.value = true },
                    onPreview = { preview, cancel ->
                        cancelled.value = cancel
                        position.floatValue = if (cancel) live.floatValue else preview
                    },
                    onFinish = {
                        if (it != null) seeks += it else position.floatValue = live.floatValue
                        dragging.value = false
                    },
                    modifier = Modifier.offset(y = 32.dp).testTag("track"))
                Box(Modifier.offset(y = 280.dp).fillMaxWidth().height(80.dp)
                    .onGloballyPositioned { cancelTop.floatValue = it.boundsInRoot().top }
                    .testTag("cancel"))
            }
        }
    }

    @Test fun movingStraightDownAfterHorizontalDragCancelsAndUsesCurrentLivePosition() {
        showTrack()
        val root = compose.onNodeWithTag("surface")
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val start = compose.onNodeWithTag("track").fetchSemanticsNode().boundsInRoot.center - origin
        val cancel = compose.onNodeWithTag("cancel").fetchSemanticsNode().boundsInRoot.center - origin
        root.performTouchInput { down(start); moveTo(start + Offset(80f, 0f)) }
        compose.runOnIdle { assertTrue(dragging.value); live.floatValue = 32_000f }
        root.performTouchInput { moveTo(Offset(start.x + 80f, cancel.y)) }
        compose.runOnIdle {
            assertTrue(cancelled.value)
            assertEquals(32_000f, position.floatValue, 0f)
            live.floatValue = 33_000f
        }
        root.performTouchInput { up() }
        compose.runOnIdle {
            assertTrue(seeks.isEmpty())
            assertFalse(dragging.value)
            assertEquals(33_000f, position.floatValue, 0f)
        }
    }

    @Test fun returningAboveCancelAreaAllowsNormalRelease() {
        showTrack()
        val root = compose.onNodeWithTag("surface")
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val start = compose.onNodeWithTag("track").fetchSemanticsNode().boundsInRoot.center - origin
        val cancel = compose.onNodeWithTag("cancel").fetchSemanticsNode().boundsInRoot.center - origin
        root.performTouchInput {
            down(start); moveTo(start + Offset(80f, 0f))
            moveTo(Offset(start.x + 80f, cancel.y))
            moveTo(start + Offset(80f, 0f)); up()
        }
        compose.runOnIdle { assertEquals(1, seeks.size); assertEquals(30_000f, seeks.single(), 0f) }
    }

    @Test fun systemCancelledGestureNeverSeeks() {
        showTrack()
        compose.onNodeWithTag("track").performTouchInput {
            down(center); moveBy(Offset(80f, 0f)); cancel()
        }
        compose.runOnIdle { assertTrue(seeks.isEmpty()); assertFalse(dragging.value) }
    }

    @Test fun sidewaysTravelInCancelAreaIsDiscardedAndNewTravelStartsAtLiveProgress() {
        showTrack()
        val root = compose.onNodeWithTag("surface")
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val track = compose.onNodeWithTag("track").fetchSemanticsNode().boundsInRoot
        val start = track.center - origin
        val cancelY = compose.onNodeWithTag("cancel").fetchSemanticsNode().boundsInRoot.center.y - origin.y
        for (side in listOf(-1f, 1f)) {
            root.performTouchInput {
                down(start)
                moveTo(start + Offset(80f, 0f))
                moveTo(Offset(start.x + 80f, cancelY))
                moveTo(Offset(start.x + side * 120f, cancelY))
            }
            compose.runOnIdle { live.floatValue = 34_000f }
            val exit = Offset(start.x + side * 160f, start.y)
            root.performTouchInput { moveTo(exit) }
            compose.runOnIdle {
                assertFalse(cancelled.value)
                assertEquals(34_000f, position.floatValue, 0.01f)
            }
            root.performTouchInput { moveTo(exit + Offset(-20f, 0f)); up() }
            compose.runOnIdle {
                assertEquals(34_000f - 20f / track.width * 180_000f, seeks.last(), 0.1f)
            }
        }
        compose.runOnIdle { assertEquals(2, seeks.size) }
    }

    @Test fun touchAboveVisibleTrackStillAllowsSeeking() {
        showTrack()
        compose.onNodeWithTag("track").performTouchInput {
            // Outside the 7 dp painted capsule, inside the enlarged 24 dp hit area.
            val start = Offset(center.x, 1f)
            down(start); moveTo(start + Offset(80f, 0f)); up()
        }
        compose.runOnIdle { assertEquals(1, seeks.size) }
    }

    @Test fun verticalIntentDoesNotStartSeeking() {
        showTrack()
        compose.onNodeWithTag("track").performTouchInput {
            down(center); moveBy(Offset(0f, 80f)); up()
        }
        compose.runOnIdle { assertTrue(seeks.isEmpty()); assertFalse(dragging.value) }
    }
}
