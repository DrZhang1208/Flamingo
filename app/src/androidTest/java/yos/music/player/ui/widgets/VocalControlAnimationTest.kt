package yos.music.player.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yos.music.player.logic.mss.VocalSeparationController
import yos.music.player.ui.pages.VocalSeparationControl

class VocalControlAnimationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bottomAndContinuousCornersSurvivePressExpansionAndCollapse() {
        val original = VocalSeparationController.state.value
        val backdrop = Color(0xFF254060)
        compose.mainClock.autoAdvance = false
        VocalSeparationController.reportAvailable(true)
        VocalSeparationController.setEnabled(false)
        try {
            compose.setContent {
                Box(Modifier.size(220.dp, 200.dp).background(backdrop).clipToBounds().testTag("surface")) {
                    VocalSeparationControl(LocalContext.current, {},
                        Modifier.align(Alignment.BottomCenter)
                            .graphicsLayer { translationX = 0.37f; translationY = 0.41f }
                            .testTag("vocal"))
                }
            }
            compose.mainClock.advanceTimeBy(1600)
            val baseline = compose.onNodeWithTag("vocal").fetchSemanticsNode().boundsInRoot.bottom
            fun checkFrame() {
                val bounds = compose.onNodeWithTag("vocal").fetchSemanticsNode().boundsInRoot
                assertEquals("Press scale must grow upward from a fixed bottom", baseline, bounds.bottom, 1f)
                val root = compose.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot
                val pixels = compose.onNodeWithTag("surface").captureToImage().toPixelMap()
                val bottom = (baseline - root.top - 1f).toInt().coerceIn(0, pixels.height - 1)
                val center = (bounds.center.x - root.left).toInt()
                val edge = (bounds.left - root.left + 1f).toInt()
                assertTrue("Bottom centre must remain inside the button", pixels[center, bottom].red > backdrop.red + 0.05f)
                assertEquals("Bottom corner must stay rounded, never cut off flat",
                    backdrop.red, pixels[edge, bottom].red, 0.025f)
                // Probe the actual outer pixel, not only the inset corner. A
                // coincident inverse mask leaves a rectangular AA fringe here.
                for (cornerX in listOf(bounds.left, bounds.right - 1f)) {
                    for (cornerY in listOf(bounds.top, bounds.bottom - 1f)) {
                        val x = (cornerX - root.left).toInt().coerceIn(0, pixels.width - 1)
                        val y = (cornerY - root.top).toInt().coerceIn(0, pixels.height - 1)
                        val p = pixels[x, y]
                        assertEquals("No rectangular fringe outside the smooth corner", backdrop.red, p.red, 0.01f)
                        assertEquals(backdrop.green, p.green, 0.01f)
                        assertEquals(backdrop.blue, p.blue, 0.01f)
                    }
                }
            }
            compose.onNodeWithTag("vocal").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(112)
            checkFrame()
            compose.onNodeWithTag("vocal").performTouchInput { up() }
            repeat(15) {
                compose.mainClock.advanceTimeBy(48)
                checkFrame()
            }
            compose.runOnUiThread { VocalSeparationController.setEnabled(false) }
            repeat(25) {
                compose.mainClock.advanceTimeBy(48)
                checkFrame()
            }
        } finally {
            VocalSeparationController.reportAvailable(true)
            VocalSeparationController.selectMode(original.mode)
            VocalSeparationController.setEnabled(original.enabled)
            VocalSeparationController.setSelectedGain(original.selectedGain)
            VocalSeparationController.reportAvailable(original.available, original.usingGpu)
        }
    }
}
