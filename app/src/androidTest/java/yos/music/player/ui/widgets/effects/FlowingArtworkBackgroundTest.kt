package yos.music.player.ui.widgets.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

@SdkSuppress(minSdkVersion = 33)
class FlowingArtworkBackgroundTest {
    @get:Rule val compose = createComposeRule()

    private fun solid(color: Int) = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun smallSurfaceMatchesTheFullSizePostWarpBlur() {
        val source = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        for (y in 0 until 256) for (x in 0 until 256) {
            source.setPixel(x, y, when {
                x in 96..103 || x in 144..151 -> AndroidColor.BLACK
                x < 128 -> AndroidColor.rgb(230, 40, 30)
                else -> AndroidColor.rgb(30, 80, 220)
            })
        }
        val texture = prepareArtworkTexture(source)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Row {
                FlowingArtworkBackground(texture, { false }, { false },
                    Modifier.size(140.dp, 280.dp).testTag("small"))
                FlowingArtworkBackground(texture, { false }, { false },
                    Modifier.size(140.dp, 280.dp).testTag("reference"), maxRenderDimension = Int.MAX_VALUE)
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val smallImage = compose.onNodeWithTag("small").captureToImage()
        val referenceImage = compose.onNodeWithTag("reference").captureToImage()
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(cache, "artwork-small.png").outputStream().use { smallImage.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(cache, "artwork-reference.png").outputStream().use { referenceImage.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val small = smallImage.toPixelMap()
        val reference = referenceImage.toPixelMap()
        assertEquals(reference.width, small.width)
        assertEquals(reference.height, small.height)
        var error = 0.0
        var maxError = 0f
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val a = small[x, y]
            val b = reference[x, y]
            assertEquals(1f, a.alpha, 0.001f)
            for (delta in listOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))) {
                maxError = maxOf(maxError, delta)
                error += delta
            }
        }
        val meanError = error / (small.width * small.height * 3)
        assertTrue("Post-warp colour fusion: maximum=${maxError * 255}, mean=${meanError * 255}", maxError <= 12f / 255f)
        assertTrue("Downsampling must preserve the reference's colours: mean=${meanError * 255}", meanError < 2f / 255f)
    }

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

    @Test fun layeredArtworkUsesHighPrecisionAndCropsWithoutStretching() {
        val source = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.RED)
            Canvas(this).drawRect(40f, 0f, 120f, 80f, Paint().apply { color = AndroidColor.GREEN })
        }
        val prepared = prepareArtworkTexture(source)
        assertEquals(ARTWORK_TEXTURE_SIZE, prepared.width)
        assertEquals(ARTWORK_TEXTURE_SIZE, prepared.height)
        assertEquals(Bitmap.Config.RGBA_F16, prepared.config)
        // The red wings lie outside the center square. Stretching would keep them.
        for (x in listOf(2, ARTWORK_TEXTURE_SIZE / 2, ARTWORK_TEXTURE_SIZE - 3)) {
            assertEquals(AndroidColor.GREEN, prepared.getPixel(x, ARTWORK_TEXTURE_SIZE / 2))
        }
        val opaque = prepareArtworkTexture(solid(AndroidColor.TRANSPARENT))
        assertEquals(255, AndroidColor.alpha(opaque.getPixel(0, 0)))
        val mixed = freezeArtworkBlend(prepared, opaque, 0.4f)
        assertEquals(Bitmap.Config.RGBA_F16, mixed.config)
        assertEquals(ARTWORK_TEXTURE_SIZE, mixed.width)
        // These are runtime-compiled, so APK compilation alone cannot validate them.
        RuntimeShader(FLOWING_ARTWORK_SHADER)
        RuntimeShader(ARTWORK_FINISH_SHADER)
    }

    @Test fun finishingDitherIsSubtleAndFrozenForSolidArtwork() {
        compose.mainClock.autoAdvance = false
        val source = prepareArtworkTexture(solid(AndroidColor.rgb(100, 100, 100)))
        compose.setContent {
            FlowingArtworkBackground(source, { false }, { false },
                Modifier.size(200.dp).testTag("field"))
        }
        compose.mainClock.advanceTimeBy(100)
        val pixels = compose.onNodeWithTag("field").captureToImage().toPixelMap()
        assertTrue("A paused F16 background must retain its source brightness", pixels[pixels.width / 2, pixels.height / 2].red > 0.15f)
        val row = (10 until pixels.width - 10).map { pixels[it, pixels.height / 2].red }
        assertTrue("Noise must not look like visible grain", row.max() - row.min() <= 2.1f / 255f)
        assertTrue("Final dither should break up uniform quantization", row.max() > row.min())
        compose.mainClock.advanceTimeBy(3000)
        val frozen = compose.onNodeWithTag("field").captureToImage().toPixelMap()
        for (x in 10 until pixels.width - 10) {
            assertEquals(pixels[x, pixels.height / 2], frozen[x, pixels.height / 2])
        }
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
        // The softened background now moves at 80% of its original speed.
        // Give this visual regression a full slow-flow interval to observe.
        compose.mainClock.advanceTimeBy(12000)
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
            FlowingArtworkBackground(texture.value, { true }, { false },
                Modifier.size(200.dp).testTag("field"), motionEnabled = false)
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
        // Interrupted mixtures are now prepared off the UI thread. Let that
        // one-shot worker finish before checking the eventual latest cover.
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(100)
            val final = sample()
            final.green > 0.5f && final.red < 0.01f && final.blue < 0.01f
        }
    }

    @Test fun pausedAndCollapsedPlayersAcceptNewArtworkWithoutAnAnimation() {
        val texture = mutableStateOf(solid(AndroidColor.RED))
        val mini = mutableStateOf(false)
        val expansion = mutableStateOf(1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FlowingArtworkBackground(texture.value, { false }, { mini.value },
                Modifier.size(200.dp).testTag("field"), motionEnabled = false,
                expansionProgress = { expansion.value })
        }
        fun sample() = compose.onNodeWithTag("field").captureToImage().toPixelMap().let {
            it[it.width / 2, it.height / 2]
        }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { texture.value = solid(AndroidColor.BLUE) }
        compose.mainClock.advanceTimeBy(100)
        assertTrue(sample().blue > 0.5f && sample().red < 0.01f)
        compose.runOnUiThread {
            mini.value = true
            expansion.value = 0f
            texture.value = solid(AndroidColor.GREEN)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { expansion.value = 0.5f }
        compose.mainClock.advanceTimeBy(100)
        val opening = sample()
        assertTrue(opening.green > 0.5f && opening.blue < 0.01f)
        compose.mainClock.advanceTimeBy(2000)
        assertEquals(opening, sample())
    }

    @Test fun coverBlendFreezesDuringExpansionAndPauseThenResumes() {
        val texture = mutableStateOf(solid(AndroidColor.RED))
        val playing = mutableStateOf(true)
        val mini = mutableStateOf(false)
        val expansion = mutableStateOf(1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FlowingArtworkBackground(texture.value, { playing.value }, { mini.value },
                Modifier.size(200.dp).testTag("field"), motionEnabled = false,
                expansionProgress = { expansion.value })
        }
        fun sample() = compose.onNodeWithTag("field").captureToImage().toPixelMap().let {
            it[it.width / 2, it.height / 2]
        }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { texture.value = solid(AndroidColor.BLUE) }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnUiThread { mini.value = true; expansion.value = 0.4f }
        compose.mainClock.advanceTimeByFrame()
        val frozen = sample()
        assertTrue(frozen.red > 0.1f && frozen.blue > 0.1f)
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(frozen, sample())
        // showMiniPlayer may become false before an interactive expansion ends.
        compose.runOnUiThread { mini.value = false; expansion.value = 0.9f }
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(frozen, sample())
        compose.runOnUiThread { playing.value = false; expansion.value = 1f }
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(frozen, sample())
        compose.runOnUiThread { playing.value = true }
        compose.mainClock.advanceTimeByFrame()
        val resumed = sample()
        assertEquals(frozen.red, resumed.red, 0.05f)
        assertEquals(frozen.blue, resumed.blue, 0.05f)
        compose.mainClock.advanceTimeBy(1_100)
        val final = sample()
        assertTrue(final.blue > 0.5f && final.red < 0.01f)
    }

}
