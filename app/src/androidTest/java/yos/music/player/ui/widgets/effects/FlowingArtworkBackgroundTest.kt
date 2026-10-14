package yos.music.player.ui.widgets.effects

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

@SdkSuppress(minSdkVersion = 33)
class FlowingArtworkBackgroundTest {
    @get:Rule val compose = createComposeRule()

    private fun solid(color: Int) = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun texturesAreOpaqueAndInterruptedMixRetainsColours() {
        val transparent = solid(AndroidColor.TRANSPARENT)
        val prepared = prepareFlowTexture(transparent)
        assertEquals(96, prepared.width)
        assertEquals(255, AndroidColor.alpha(prepared.getPixel(48, 48)))
        val mixed = freezeArtworkBlend(solid(AndroidColor.RED), solid(AndroidColor.BLUE), 0.4f)
        val pixel = mixed.getPixel(48, 48)
        assertEquals(255, AndroidColor.alpha(pixel))
        assertTrue(abs(AndroidColor.red(pixel) - 153) <= 1)
        assertTrue(abs(AndroidColor.blue(pixel) - 102) <= 1)
    }

    @Test fun gpuFieldMovesThenFreezesWhenDisabledPausedOrMinimised() {
        val source = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        for (y in 0 until 96) for (x in 0 until 96) {
            source.setPixel(x, y, AndroidColor.rgb(x * 255 / 95, y * 255 / 95, 100))
        }
        val playing = mutableStateOf(true)
        val mini = mutableStateOf(false)
        val motionEnabled = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FlowingArtworkBackground(source, { playing.value }, { mini.value },
                Modifier.size(200.dp).testTag("field"), motionEnabled = motionEnabled.value)
        }
        fun sample(): FloatArray {
            val p = compose.onNodeWithTag("field").captureToImage().toPixelMap()
            return floatArrayOf(p[p.width / 3, p.height / 3].red, p[p.width / 3, p.height / 3].green)
        }
        compose.mainClock.advanceTimeBy(100)
        val start = sample()
        compose.mainClock.advanceTimeBy(4000)
        val moving = sample()
        assertTrue("GPU field should change over time", abs(start[0] - moving[0]) + abs(start[1] - moving[1]) > 0.02f)
        compose.runOnUiThread { motionEnabled.value = false }
        compose.mainClock.advanceTimeBy(100)
        val disabled = sample()
        // The switch freezes the current shader field, not a different blurred image.
        assertArrayEquals(moving, disabled, 0.001f)
        compose.mainClock.advanceTimeBy(3000)
        assertArrayEquals(disabled, sample(), 0.001f)
        compose.runOnUiThread { motionEnabled.value = true }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { playing.value = false }
        compose.mainClock.advanceTimeBy(100)
        val paused = sample()
        compose.mainClock.advanceTimeBy(3000)
        assertArrayEquals(paused, sample(), 0.001f)
        compose.runOnUiThread { playing.value = true; mini.value = true }
        compose.mainClock.advanceTimeBy(100)
        val minimised = sample()
        compose.mainClock.advanceTimeBy(3000)
        assertArrayEquals(minimised, sample(), 0.001f)
    }

    @Test fun rapidCoverChangesPreserveVisibleMixAndReachLatestCover() {
        val texture = mutableStateOf(solid(AndroidColor.RED))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FlowingArtworkBackground(texture.value, { false }, { false }, Modifier.size(200.dp).testTag("field"))
        }
        fun sample() = compose.onNodeWithTag("field").captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { texture.value = solid(AndroidColor.BLUE) }
        compose.mainClock.advanceTimeBy(400)
        val mixed = sample()
        assertTrue(mixed.red > 0.1f && mixed.blue > 0.1f)
        compose.runOnUiThread { texture.value = solid(AndroidColor.GREEN) }
        compose.mainClock.advanceTimeByFrame()
        val continued = sample()
        assertEquals(mixed.red, continued.red, 0.05f)
        assertEquals(mixed.blue, continued.blue, 0.05f)
        compose.mainClock.advanceTimeBy(1100)
        val final = sample()
        assertTrue(final.green > 0.5f && final.red < 0.01f && final.blue < 0.01f)
    }
}
