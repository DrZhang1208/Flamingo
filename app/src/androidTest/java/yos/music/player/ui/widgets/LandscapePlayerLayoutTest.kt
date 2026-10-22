package yos.music.player.ui.widgets

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LandscapePlayerLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rightPaneSlidesBetweenControlsAndLyricsWithoutMovingArtwork() {
        compose.setContent {
            LandscapePlayerLayout(
                interactionEnabled = true,
                onClose = {},
                modifier = Modifier.size(320.dp, 240.dp),
                artwork = { Box(Modifier.size(120.dp).background(Color.Red).testTag("artwork")) },
                controls = { Box(Modifier.fillMaxSize().testTag("controls")) },
                lyrics = { Box(Modifier.fillMaxSize().testTag("lyrics")) }
            )
        }
        val before = compose.onNodeWithTag("artwork").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("controls").assertIsDisplayed().performTouchInput { swipeLeft() }
        compose.onNodeWithTag("lyrics").assertIsDisplayed()
        compose.onNodeWithTag("controls").assertIsNotDisplayed()
        assertEquals(before, compose.onNodeWithTag("artwork").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("lyrics").performTouchInput { swipeRight() }
        compose.onNodeWithTag("controls").assertIsDisplayed()
        compose.onNodeWithTag("lyrics").assertIsNotDisplayed()
        assertEquals(before, compose.onNodeWithTag("artwork").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun backReturnsFromLyricsToControlsBeforeClosingThePlayer() {
        lateinit var dispatcher: OnBackPressedDispatcher
        var closed = 0
        compose.setContent {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            LandscapePlayerLayout(
                interactionEnabled = true,
                onClose = { closed++ },
                modifier = Modifier.size(320.dp, 240.dp),
                artwork = {},
                controls = { Box(Modifier.fillMaxSize().testTag("controls")) },
                lyrics = { Box(Modifier.fillMaxSize().testTag("lyrics")) }
            )
        }
        compose.onNodeWithTag("controls").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("lyrics").assertIsDisplayed()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithTag("controls").assertIsDisplayed()
        assertEquals(0, closed)
        compose.runOnIdle { dispatcher.onBackPressed() }
        assertEquals(1, closed)
    }

    @Test fun horizontalSeekDragDoesNotChangeTheRightPage() {
        val position = mutableStateOf(5000f)
        var previews = 0
        compose.setContent {
            LandscapePlayerLayout(
                interactionEnabled = true,
                onClose = {},
                modifier = Modifier.size(320.dp, 240.dp),
                artwork = {},
                controls = {
                    Box(Modifier.fillMaxSize().testTag("controls")) {
                        PlaybackSeekTrack(
                            gestureKey = Unit, position = { position.value }, duration = { 10000L },
                            livePosition = { 5000f }, dragging = false, cancelAreaContains = { false },
                            onStart = {},
                            onPreview = { time, _ -> position.value = time; previews++ },
                            onFinish = {}, modifier = Modifier.testTag("seek")
                        )
                    }
                },
                lyrics = { Box(Modifier.fillMaxSize().testTag("lyrics")) }
            )
        }
        compose.onNodeWithTag("seek").performTouchInput {
            swipe(start = Offset(width * 0.8f, center.y), end = Offset(width * 0.2f, center.y), durationMillis = 400)
        }
        compose.onNodeWithTag("controls").assertIsDisplayed()
        compose.onNodeWithTag("lyrics").assertIsNotDisplayed()
        compose.runOnIdle {
            assertTrue(previews > 0)
            assertTrue(position.value < 5000f)
        }
    }
}
