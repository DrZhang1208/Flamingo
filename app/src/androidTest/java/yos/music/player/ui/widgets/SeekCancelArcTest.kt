package yos.music.player.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeekCancelArcTest {
    @get:Rule val compose = createComposeRule()

    @Test fun paintedArcAndHitRegionAgreeAndWithdrawBelowTheScreen() {
        val visible = mutableStateOf(true)
        val cancelled = mutableStateOf(false)
        val background = mutableStateOf(Color.Green)
        val region = SeekCancelArcHitRegion()
        compose.setContent {
            Box(Modifier.size(360.dp, 300.dp).background(background.value).testTag("surface")) {
                SeekCancelArc(visible.value, cancelled.value, false, region,
                    Modifier.align(Alignment.BottomCenter).testTag("arc"))
            }
        }
        val bounds = compose.onNodeWithTag("arc").fetchSemanticsNode().boundsInRoot
        val centerTop = Offset(bounds.center.x, bounds.top + 5f)
        val edgeTop = Offset(bounds.left + 5f, bounds.top + 5f)
        val liftedCentre = Offset(bounds.center.x, bounds.top - 3f)
        compose.runOnIdle {
            assertTrue(region.contains(centerTop))
            assertFalse(region.contains(edgeTop))
            assertTrue(region.contains(Offset(bounds.left + 5f, bounds.bottom - 5f)))
            assertFalse(region.contains(liftedCentre))
        }
        compose.onNodeWithText("移入此处，放弃本次调整").assertDoesNotExist()
        val before = compose.onNodeWithTag("arc").captureToImage().toPixelMap()
        assertTrue("The arc's upper corners must reveal the background", before[2, 2].green > 0.9f)
        compose.runOnIdle { cancelled.value = true }
        val after = compose.onNodeWithTag("arc").captureToImage().toPixelMap()
        compose.onNodeWithText("松开手指，继续原进度播放").assertDoesNotExist()
        compose.runOnIdle { assertTrue(region.contains(liftedCentre)) }
        // Activation changes the small undo affordance, not the entire surface colour.
        assertEquals(before[before.width / 2, 4], after[after.width / 2, 4])
        compose.runOnIdle { background.value = Color.Magenta }
        val otherBackground = compose.onNodeWithTag("arc").captureToImage().toPixelMap()
        assertEquals("The surface must be opaque over different album backgrounds",
            after[after.width / 2, 4], otherBackground[otherBackground.width / 2, 4])
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(region.contains(centerTop)) }
    }

    @Test fun entranceSlidesAnOpaqueSurfaceInsteadOfFadingItsBody() {
        val visible = mutableStateOf(false)
        val background = mutableStateOf(Color.Green)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(360.dp, 300.dp).background(background.value).testTag("surface")) {
                SeekCancelArc(visible.value, false, false, SeekCancelArcHitRegion(),
                    Modifier.align(Alignment.BottomCenter))
            }
        }
        compose.runOnIdle { visible.value = true }
        compose.mainClock.advanceTimeBy(112)
        val first = compose.onNodeWithTag("surface").captureToImage().toPixelMap()
        compose.runOnIdle { background.value = Color.Magenta }
        compose.mainClock.advanceTimeByFrame()
        val second = compose.onNodeWithTag("surface").captureToImage().toPixelMap()
        val a = first[first.width / 2, first.height - 3]
        val b = second[second.width / 2, second.height - 3]
        // Allow for the opaque tonal gradient shifting with the moving arc.
        assertEquals(a.red, b.red, 0.02f)
        assertEquals(a.green, b.green, 0.02f)
        assertEquals(a.blue, b.blue, 0.02f)
    }

    @Test fun reversingAnUnfinishedEntranceKeepsTheSurfaceNearItsCurrentPosition() {
        val visible = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(360.dp, 300.dp)) {
                SeekCancelArc(visible.value, false, false, SeekCancelArcHitRegion(),
                    Modifier.align(Alignment.BottomCenter).testTag("arc"))
            }
        }
        compose.runOnIdle { visible.value = true }
        compose.mainClock.advanceTimeBy(96)
        val before = compose.onNodeWithTag("arc").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeByFrame()
        val after = compose.onNodeWithTag("arc").fetchSemanticsNode().boundsInRoot.top
        val maxStep = with(compose.density) { 24.dp.toPx() }
        assertTrue("Reversal must not jump to either endpoint", kotlin.math.abs(after - before) < maxStep)
        compose.runOnIdle { visible.value = true }
        compose.mainClock.advanceTimeBy(1200)
        val final = compose.onNodeWithTag("arc").fetchSemanticsNode().boundsInRoot.top
        assertEquals(with(compose.density) { 213.dp.toPx() }, final, 1f)
    }
}
