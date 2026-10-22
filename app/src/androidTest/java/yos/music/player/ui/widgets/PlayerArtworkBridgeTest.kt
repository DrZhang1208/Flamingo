package yos.music.player.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerArtworkBridgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coverShadowRemainsVisibleOutsideCoverBoundsWhilePlayingAndPaused() {
        val pause = mutableFloatStateOf(0f)
        val bridge = PlayerArtworkBridge { 1f }.apply { pauseProgress = { pause.floatValue } }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(320.dp, 400.dp).background(Color(0xFF805020)).testTag("shadowHost")) {
                Spacer(Modifier.offset(32.dp, 90.dp).size(260.dp)
                    .onGloballyPositioned { bridge.record(ALBUM_ARTWORK_ENDPOINT, it) })
                PlayerArtworkBridgeOverlay(bridge, ColorPainter(Color.Red), { ALBUM_ARTWORK_ENDPOINT }, Modifier.fillMaxSize())
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        fun assertShadowBelow(bottomDp: Float) {
            val pixels = compose.onNodeWithTag("shadowHost").captureToImage().toPixelMap()
            val density = pixels.width / 320f
            val y = ((bottomDp + 7f) * density).toInt()
            val shadow = pixels[(160f * density).toInt(), y]
            val background = pixels[(6f * density).toInt(), y]
            val ratio = shadow.red / background.red
            assertTrue("Shadow must be visible without forming a black border: $ratio",
                ratio in 0.58f..0.95f)
            assertTrue("Outside-cover shadow must not contain the red artwork", shadow.green > 0f)
        }
        assertShadowBelow(350f)
        compose.runOnUiThread { pause.floatValue = 1f }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertShadowBelow(317.5f) // min(37dp, 260dp * .125) inset
    }

    @Test fun oneCoverTracksSheetDragAndInterruptsAlbumCompactTransitions() {
        val progress = mutableFloatStateOf(0f)
        val destination = mutableStateOf(ALBUM_ARTWORK_ENDPOINT)
        val bridge = PlayerArtworkBridge { progress.floatValue }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(320.dp, 400.dp).background(Color.Black).testTag("host")) {
                Spacer(Modifier.offset(8.dp, 7.dp).size(47.dp)
                    .onGloballyPositioned { bridge.record(MINI_ARTWORK_ENDPOINT, it) })
                Spacer(Modifier.offset(32.dp, 90.dp).size(260.dp)
                    .onGloballyPositioned { bridge.record(ALBUM_ARTWORK_ENDPOINT, it) })
                Spacer(Modifier.offset(28.dp, 40.dp).size(69.dp)
                    .onGloballyPositioned { bridge.record(COMPACT_ARTWORK_ENDPOINT, it) })
                PlayerArtworkBridgeOverlay(bridge, ColorPainter(Color.Red), { destination.value }, Modifier.fillMaxSize())
            }
        }
        fun width(): Int {
            val p = compose.onNodeWithTag("host").captureToImage().toPixelMap()
            var left = p.width; var right = -1
            for (y in 0 until p.height step 3) for (x in 0 until p.width) {
                val c = p[x, y]
                if (c.red > 0.9f && c.green < 0.1f) { left = minOf(left, x); right = maxOf(right, x) }
            }
            return (right - left + 1).coerceAtLeast(0)
        }
        compose.mainClock.advanceTimeBy(1000)
        val mini = width()
        compose.runOnUiThread { progress.floatValue = 0.5f }
        compose.mainClock.advanceTimeByFrame()
        val middle = width()
        compose.runOnUiThread { progress.floatValue = 1f }
        compose.mainClock.advanceTimeByFrame()
        val full = width()
        assertTrue(mini > 0 && middle > mini && full > middle)
        compose.runOnUiThread { destination.value = COMPACT_ARTWORK_ENDPOINT }
        compose.mainClock.advanceTimeBy(120)
        val shrinking = width()
        assertTrue(shrinking < full && shrinking > mini)
        compose.runOnUiThread { destination.value = ALBUM_ARTWORK_ENDPOINT }
        compose.mainClock.advanceTimeByFrame()
        val interrupted = width()
        assertTrue("Reversal must continue from its current size", interrupted < full && interrupted > mini)
        compose.mainClock.advanceTimeBy(1500)
        assertEquals(full.toFloat(), width().toFloat(), 2f)
        compose.runOnUiThread { progress.floatValue = 0f }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(mini.toFloat(), width().toFloat(), 2f)
    }
    @Test fun playbackSizeUpdatesFollowMeasuredBoundsWithoutASecondSpring() {
        val side = mutableStateOf(220.dp)
        val bridge = PlayerArtworkBridge { 1f }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(320.dp, 400.dp).background(Color.Black).testTag("host")) {
                Spacer(Modifier.size(47.dp).onGloballyPositioned { bridge.record(MINI_ARTWORK_ENDPOINT, it) })
                Spacer(Modifier.offset(20.dp, 40.dp).size(side.value)
                    .onGloballyPositioned { bridge.record(ALBUM_ARTWORK_ENDPOINT, it) })
                PlayerArtworkBridgeOverlay(bridge, ColorPainter(Color.Red), { ALBUM_ARTWORK_ENDPOINT }, Modifier.fillMaxSize())
            }
        }
        fun redWidth(): Int {
            val p = compose.onNodeWithTag("host").captureToImage().toPixelMap()
            val y = p.height / 4
            return (0 until p.width).count { p[it, y].red > 0.9f && p[it, y].green < 0.1f }
        }
        compose.mainClock.advanceTimeBy(1000)
        val initial = redWidth()
        compose.runOnUiThread { side.value = 160.dp }
        // One frame measures the playback bounds; the next draws their new size.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(initial * 160f / 220f, redWidth().toFloat(), 2f)
    }

    @Test fun pauseShrinksAroundTheSameCenterWithoutRemeasuringTheSurface() {
        val pause = mutableFloatStateOf(0f)
        val bridge = PlayerArtworkBridge { 1f }.also { it.pauseProgress = { pause.floatValue } }
        var measures = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(320.dp, 400.dp).background(Color.Black).testTag("host")) {
                Spacer(Modifier.size(47.dp).onGloballyPositioned { bridge.record(MINI_ARTWORK_ENDPOINT, it) })
                Spacer(Modifier.offset(30.dp, 70.dp).size(240.dp)
                    .onGloballyPositioned { bridge.record(ALBUM_ARTWORK_ENDPOINT, it) })
                PlayerArtworkBridgeOverlay(bridge, ColorPainter(Color.Red), { ALBUM_ARTWORK_ENDPOINT },
                    Modifier.fillMaxSize().layout { measurable, constraints ->
                        measures++
                        val child = measurable.measure(constraints)
                        layout(child.width, child.height) { child.place(0, 0) }
                    })
            }
        }
        fun bounds(): Rect {
            val p = compose.onNodeWithTag("host").captureToImage().toPixelMap()
            var left = p.width; var right = -1; var top = p.height; var bottom = -1
            for (y in 0 until p.height) for (x in 0 until p.width) {
                if (p[x, y].red > 0.9f && p[x, y].green < 0.1f) {
                    left = minOf(left, x); right = maxOf(right, x)
                    top = minOf(top, y); bottom = maxOf(bottom, y)
                }
            }
            return Rect(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
        }
        compose.mainClock.advanceTimeBy(1000)
        val full = bounds()
        val settledMeasures = measures
        for (fraction in listOf(0.25f, 0.5f, 1f, 0.4f, 0f)) {
            compose.runOnUiThread { pause.floatValue = fraction }
            compose.mainClock.advanceTimeByFrame()
            val visible = bounds()
            assertEquals(full.center.x, visible.center.x, 1.5f)
            assertEquals(full.center.y, visible.center.y, 1.5f)
            assertTrue(visible.width <= full.width + 1f)
        }
        assertEquals(settledMeasures, measures)
    }

}
