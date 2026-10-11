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
