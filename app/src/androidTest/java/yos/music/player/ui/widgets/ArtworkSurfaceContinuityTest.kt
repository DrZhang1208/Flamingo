package yos.music.player.ui.widgets

import android.graphics.drawable.ColorDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import androidx.compose.ui.graphics.painter.Painter
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalSharedTransitionApi::class)
class ArtworkSurfaceContinuityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rapidAlbumLyricsAndQueueTransitionsKeepLoadedCoverWithoutNewRequests() {
        val requests = AtomicInteger()
        val loader = ImageLoader.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
            .components { add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    requests.incrementAndGet()
                    delay(100) // A new request at either endpoint would expose a visible empty frame.
                    return SuccessResult(ColorDrawable(android.graphics.Color.RED), chain.request, DataSource.NETWORK)
                }
            }) }.build()
        val page = mutableIntStateOf(0)
        var painter: Painter? = null
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent {
                val artwork = rememberNowPlayingArtwork("slow-test-artwork", loader)
                SideEffect { painter = artwork }
                SharedTransitionLayout(Modifier.size(300.dp).background(Color.Black).testTag("cover-frame")) {
                    Crossfade(page.intValue == 0) { large ->
                        Box(Modifier.fillMaxSize(), contentAlignment = if (large) Alignment.BottomCenter else Alignment.TopStart) {
                            val shared = Modifier.sharedElementWithCallerManagedVisibility(
                                rememberSharedContentState("cover"), visible = (page.intValue == 0) == large)
                            val bounds = if (large) Modifier.fillMaxWidth()
                                .artworkSharedFrame(shared, { 0f }).aspectRatio(1f)
                            else Modifier.size(69.dp).then(shared)
                            Image(artwork, null, bounds, contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
            android.util.Log.i("CoverTest", "content set")
            compose.mainClock.advanceTimeBy(200)
            android.util.Log.i("CoverTest", "initial advanced")
            compose.waitUntil(5000) { requests.get() == 1 }
            // The interceptor has a real coroutine delay; the Compose test clock
            // only controls animation time. Allow that request to finish first.
            Thread.sleep(1000) // Also finish the Activity window entrance before window screenshots.
            compose.mainClock.advanceTimeBy(300)
            compose.mainClock.advanceTimeByFrame()
            android.util.Log.i("CoverTest", "loaded ${painter?.javaClass}")
            for (target in listOf(1, 0, 2, 0, 1, 2, 0)) {
                android.util.Log.i("CoverTest", "target $target")
                compose.runOnUiThread { page.intValue = target }
                repeat(8) { step ->
                    android.util.Log.i("CoverTest", "frame $step")
                    compose.mainClock.advanceTimeBy(16)
                    // Screenshot the window without Compose's idle wait: with a
                    // manually paused clock, interrupted shared transitions may
                    // require another frame before their layout becomes idle.
                    val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                    var solidRed = 0
                    for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
                        val pixel = bitmap.getPixel(x, y)
                        if (android.graphics.Color.red(pixel) > 250 &&
                            android.graphics.Color.green(pixel) < 12 && android.graphics.Color.blue(pixel) < 12) solidRed++
                    }
                    if (solidRed <= 100) {
                        val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "cover-failure.png")
                        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    bitmap.recycle()
                    assertTrue("Cover faded or disappeared during page $target, red samples=$solidRed", solidRed > 100)
                }
            }
            compose.mainClock.advanceTimeBy(1000)
            assertEquals("Changing pages must reuse the loaded painter", 1, requests.get())
        } finally { loader.shutdown() }
    }
}
