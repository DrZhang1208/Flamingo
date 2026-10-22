package yos.music.player.ui.widgets.basic

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DialogElasticMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun returnCanBeInterruptedWithoutJumpingAndSettlesToRest() {
        lateinit var motion: DialogElasticMotion
        compose.setContent {
            val scope = rememberCoroutineScope()
            motion = androidx.compose.runtime.remember { DialogElasticMotion(scope, 24f) }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            motion.startGesture()
            motion.drag(-100f)
            motion.release()
        }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertTrue(motion.offset < 0f)
            val before = motion.offset
            motion.startGesture()
            motion.drag(0f)
            assertEquals(before, motion.offset, 0.0001f)
            motion.drag(-20f)
            assertTrue(motion.offset < before)
            motion.release()
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(0f, motion.offset, 0.0001f) }
    }
}
