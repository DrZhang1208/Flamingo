package yos.music.player.ui.widgets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalSharedTransitionApi::class)
class ArtworkSharedTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pausedSharedCoverNeverExpandsBeyondItsFinalPausedSize() {
        val album = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SharedTransitionLayout(Modifier.size(300.dp).background(Color.Black).testTag("frame")) {
                Crossfade(album.value) { large ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        val shared = Modifier.sharedElementWithCallerManagedVisibility(
                            rememberSharedContentState("cover"), visible = album.value == large
                        )
                        val frame = if (large) Modifier.fillMaxWidth()
                            .artworkSharedFrame(shared, { 1f }, 1f).aspectRatio(1f)
                        else Modifier.size(64.dp).then(shared)
                        Box(frame.background(Color.Red))
                    }
                }
            }
        }
        compose.runOnIdle { album.value = true }
        val widths = mutableListOf<Int>()
        repeat(45) {
            compose.mainClock.advanceTimeBy(32)
            val pixels = compose.onNodeWithTag("frame").captureToImage().toPixelMap()
            var left = pixels.width
            var right = -1
            for (y in 0 until pixels.height step 3) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > 0.8f && color.green < 0.1f && color.blue < 0.1f) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                }
            }
            widths += (right - left + 1).coerceAtLeast(0)
        }
        val finalWidth = widths.last()
        assertTrue(finalWidth > 0)
        assertTrue("cover overshot paused bounds: $widths", widths.max() <= finalWidth + 2)
    }
}
