package yos.music.player.ui.widgets.basic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class DialogElasticMotionTest {
    @Test fun resistanceIsContinuousBoundedAndProgressivelySofter() {
        val samples = (0..1000).map { dialogElasticOffset(it.toFloat(), 24f) }
        assertEquals(0f, samples.first(), 0f)
        assertTrue(samples.all { it in -24f..0f })
        assertTrue(samples.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(samples[0] - samples[1] > samples[200] - samples[201])
    }

    @Test fun reversingDragConsumesOnlyTheRemainingStretch() {
        val motion = DialogElasticMotion(CoroutineScope(Dispatchers.Unconfined), 24f)
        motion.startGesture()
        assertEquals(-100f, motion.drag(-100f), 0.001f)
        assertTrue(motion.offset < 0f)
        assertEquals(100f, motion.drag(150f), 0.001f)
        assertEquals(0f, motion.offset, 0f)
    }

    @Test fun restartingGesturePreservesVisualOffset() {
        val motion = DialogElasticMotion(CoroutineScope(Dispatchers.Unconfined), 24f)
        motion.drag(-80f)
        val before = motion.offset
        motion.startGesture()
        motion.drag(0f)
        assertEquals(before, motion.offset, 0.0001f)
    }

    @Test fun longerPullRemainsResponsiveInsteadOfHittingAnEarlyHardStop() {
        val start = dialogElasticOffset(1f, 48f)
        assertTrue(start < -0.45f && start > -0.5f)
        assertTrue(dialogElasticOffset(300f, 48f) - dialogElasticOffset(400f, 48f) > 2f)
    }

    @Test fun upwardPullFlingDoesNotAlsoSettleTheMaterialSheet() = kotlinx.coroutines.runBlocking {
        val boundary = DialogScrollBoundary(DialogElasticMotion(this, 48f))
        boundary.startGesture()
        boundary.onPostScroll(androidx.compose.ui.geometry.Offset.Zero,
            androidx.compose.ui.geometry.Offset(0f, -150f),
            androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput)
        val velocity = androidx.compose.ui.unit.Velocity(0f, -2000f)
        assertEquals(velocity, boundary.onPostFling(androidx.compose.ui.unit.Velocity.Zero, velocity))
        boundary.startGesture()
        assertEquals(androidx.compose.ui.unit.Velocity.Zero,
            boundary.onPostFling(androidx.compose.ui.unit.Velocity.Zero, velocity))
    }

    @Test fun upwardEdgeDragYieldsButScrollingContentNeverStretchesSheet() {
        val motion = DialogElasticMotion(CoroutineScope(Dispatchers.Unconfined), 24f)
        val boundary = DialogScrollBoundary(motion)
        val up = androidx.compose.ui.geometry.Offset(0f, -50f)
        val source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput
        boundary.startGesture()
        assertEquals(up, boundary.onPostScroll(androidx.compose.ui.geometry.Offset.Zero, up, source))
        assertTrue(motion.offset < 0f)
        boundary.onPreScroll(androidx.compose.ui.geometry.Offset(0f, 50f), source)
        boundary.onPostScroll(up, up, source)
        assertEquals(0f, motion.offset, 0f)
    }
}
