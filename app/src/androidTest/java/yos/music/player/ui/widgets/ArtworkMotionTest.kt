package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArtworkMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rapidPlaybackReversalKeepsPositionAndDoesNotRemeasureArtworkEachFrame() {
        val playing = mutableStateOf(true)
        var progress: () -> Float = { 0f }
        var measures = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val motion = rememberArtworkPauseProgress(playing.value)
            SideEffect { progress = { motion.value } }
            Box(Modifier.size(300.dp).artworkPlaybackTransform(progress = { motion.value })
                .layout { measurable, constraints ->
                    measures++
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                })
        }
        compose.runOnIdle { playing.value = false }
        compose.mainClock.advanceTimeBy(120)
        var before = 0f
        var measureCount = 0
        compose.runOnIdle {
            before = progress()
            assertTrue(before > 0f && before < 1f)
            measureCount = measures
            playing.value = true
            assertEquals(before, progress(), 0f)
        }
        // Let the target-state recomposition settle before measuring animation frames.
        // The test's layout lambda is recreated once by that recomposition.
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { measureCount = measures }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle {
            assertEquals(0f, progress(), 0.002f)
            assertEquals(measureCount, measures)
        }
        compose.runOnIdle { playing.value = false }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle { assertEquals(1f, progress(), 0.002f) }
    }
}
