package yos.music.player.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
class PlayerHeaderMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun textKeepsEndpointLayoutInsteadOfEllipsizingAtAnimatedWidths() {
        val album = mutableStateOf(true)
        val widths = mutableListOf<Int>()
        var large = 0; var small = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            with(LocalDensity.current) { large = 280.dp.roundToPx(); small = 160.dp.roundToPx() }
            SharedTransitionLayout(Modifier.size(320.dp, 500.dp)) {
                AnimatedContent(album.value) { expanded ->
                    Box(Modifier.fillMaxSize().padding(top = if (expanded) 280.dp else 20.dp)) {
                        Text(
                            "A long song title that stays in one measured text layout",
                            modifier = Modifier.width(if (expanded) 280.dp else 160.dp)
                                .then(playerHeaderModifier("title", this@AnimatedContent)),
                            fontSize = if (expanded) 20.sp else 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            onTextLayout = { widths += it.size.width }
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { widths.clear(); album.value = false }
        repeat(35) { compose.mainClock.advanceTimeBy(32) }
        compose.runOnIdle {
            assertTrue(widths.isNotEmpty())
            assertTrue("Text was remeasured at in-flight widths: $widths", widths.all { it == large || it == small })
        }
    }

    @Test fun translatedLyricsStayClippedAboveControls() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            Box(Modifier.size(200.dp, 240.dp).background(Color.Black).testTag("viewport")) {
                Box(Modifier.fillMaxSize()
                    .clipPlayerLyrics({ true }, { 160.dp.value * density })
                    .graphicsLayer { translationY = 60.dp.toPx() }
                    .background(Color.Red))
            }
        }
        val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
        val x = (100 * density).roundToInt()
        assertTrue(pixels[x, (96 * density).roundToInt()].red > 0.9f)
        assertTrue("Missing gradual fade", pixels[x, (120 * density).roundToInt()].red in 0.5f..0.8f)
        assertTrue("Fade did not reach transparency", pixels[x, (144 * density).roundToInt()].red in 0.05f..0.3f)
        assertTrue("Lyrics leaked under the controls", pixels[x, (180 * density).roundToInt()].red < 0.1f)
    }
    @Test fun oneMaskFadesBothViewportEdgesDuringTranslation() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            Box(Modifier.size(200.dp, 240.dp).background(Color.Black).testTag("mask")) {
                Box(Modifier.fillMaxSize()
                    .clipPlayerLyrics({ true }, { 160f * density }, { 32f * density })
                    .graphicsLayer { translationY = 20.dp.toPx() }
                    .background(Color.Red))
            }
        }
        val pixels = compose.onNodeWithTag("mask").captureToImage().toPixelMap()
        fun red(y: Int) = pixels[(100 * density).roundToInt(), (y * density).roundToInt()].red
        assertTrue(red(28) < 0.1f)
        assertTrue(red(48) in 0.4f..0.6f)
        assertTrue(red(80) > 0.9f)
        assertTrue(red(144) in 0.05f..0.3f)
        assertTrue(red(180) < 0.1f)
    }

    @Test fun pausedFirstEntryUsesFixedInsetWithoutPlaybackInvalidation() {
        val inset = mutableIntStateOf(0)
        val translation = Animatable(80f)
        var density = 1f
        compose.mainClock.autoAdvance = false
        compose.setContent {
            density = LocalDensity.current.density
            LaunchedEffect(Unit) { translation.animateTo(0f, tween(210)) }
            Box(Modifier.size(200.dp, 240.dp).background(Color.Black).testTag("pausedEntry")) {
                Column(Modifier.fillMaxSize()
                    .clipPlayerLyrics({ true }, { 160f * density }, { inset.intValue.takeIf { it > 0 }?.toFloat() })
                    .graphicsLayer { translationY = translation.value * density }) {
                    Spacer(Modifier.height(32.dp).onSizeChanged { inset.intValue = it.height })
                    Box(Modifier.fillMaxSize().background(Color.Red))
                }
            }
        }
        // Only the page animation advances: no player clock, seek or play event.
        compose.mainClock.advanceTimeBy(350)
        val pixels = compose.onNodeWithTag("pausedEntry").captureToImage().toPixelMap()
        val x = (100 * density).roundToInt()
        assertTrue("Paused initial lyrics were masked out", pixels[x, (80 * density).roundToInt()].red > 0.9f)
        assertTrue(pixels[x, (48 * density).roundToInt()].red in 0.4f..0.6f)
        assertTrue(pixels[x, (180 * density).roundToInt()].red < 0.1f)
    }

}
