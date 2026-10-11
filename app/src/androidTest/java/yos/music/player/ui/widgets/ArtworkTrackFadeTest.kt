package yos.music.player.ui.widgets

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class ArtworkTrackFadeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingRetainsCoverAndRapidTrackChangesContinueFromCurrentBlend() {
        val started = AtomicInteger(); val completed = AtomicInteger()
        val blueReady = CompletableDeferred<Unit>(); val greenReady = CompletableDeferred<Unit>()
        val loader = ImageLoader.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
            .components { add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    started.incrementAndGet()
                    val color = when (chain.request.data) {
                        "blue" -> { blueReady.await(); android.graphics.Color.BLUE }
                        "green" -> { greenReady.await(); android.graphics.Color.GREEN }
                        else -> android.graphics.Color.RED
                    }
                    completed.incrementAndGet()
                    return SuccessResult(ColorDrawable(color), chain.request, DataSource.NETWORK)
                }
            }) }.build()
        val track = mutableStateOf("red")
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent {
                val painter = rememberNowPlayingArtwork(track.value, loader)
                Canvas(Modifier.size(100.dp).testTag("cover")) { with(painter) { draw(size) } }
            }
            fun sample(): Color {
                val pixels = compose.onNodeWithTag("cover").captureToImage().toPixelMap()
                return pixels[pixels.width / 2, pixels.height / 2]
            }
            compose.waitUntil(5000) { completed.get() == 1 }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            assertTrue("The initial restored cover must not fade through a placeholder", sample().red > 0.98f)
            compose.runOnUiThread { track.value = "blue" }
            compose.mainClock.advanceTimeByFrame()
            compose.waitUntil(5000) { started.get() == 2 }
            assertTrue("Loading must retain the current cover", sample().red > 0.98f)
            blueReady.complete(Unit)
            compose.waitUntil(5000) { completed.get() == 2 }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(150)
            val mixed = sample()
            assertTrue("Track change must actually fade", mixed.red > 0.1f && mixed.blue > 0.1f)
            compose.runOnUiThread { track.value = "green" }
            compose.mainClock.advanceTimeByFrame()
            compose.waitUntil(5000) { started.get() == 3 }
            val held = sample()
            greenReady.complete(Unit)
            compose.waitUntil(5000) { completed.get() == 3 }
            compose.mainClock.advanceTimeByFrame()
            val resumed = sample()
            assertTrue("Interrupted fade must keep its visible blend: mixed=$mixed held=$held resumed=$resumed",
                abs(held.red - resumed.red) < 0.08f && abs(held.blue - resumed.blue) < 0.08f)
            compose.mainClock.advanceTimeBy(400)
            assertTrue(sample().green > 0.98f)
            assertEquals(3, started.get())
        } finally { loader.shutdown() }
    }
}
