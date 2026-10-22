package yos.music.player.ui.widgets.basic

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class DialogMotionTest {
    @Test fun dismissalHalvesAnimationTimeAndReachesTheSameEndpoint() = runBlocking {
        suspend fun frames(dismiss: Boolean): Pair<Int, Float> {
            var count = 0
            var last = -1f
            val clock = object : MonotonicFrameClock {
                override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
                    onFrame(count++.toLong() * 16_666_667L)
            }
            withContext(clock) {
                val animation: suspend () -> Unit = {
                    animate(0f, 100f, animationSpec = tween(400)) { value, _ -> last = value }
                }
                if (dismiss) withDialogDismissMotion(animation) else animation()
            }
            return count to last
        }
        val normal = frames(false)
        val dismiss = frames(true)
        assertEquals(100f, normal.second, 0f)
        assertEquals(100f, dismiss.second, 0f)
        assertTrue(dismiss.first < normal.first)
        assertEquals(normal.first / 2f, dismiss.first.toFloat(), 1f)
    }

    @Test fun systemMotionScaleAndLiveChangesArePreserved() = runBlocking {
        var systemValue = 2f
        val system = object : MotionDurationScale {
            override val scaleFactor get() = systemValue
        }
        withContext(system) {
            withDialogDismissMotion {
                val dismiss = currentCoroutineContext()[MotionDurationScale]!!
                assertEquals(1f, dismiss.scaleFactor, 0f)
                systemValue = 0f
                assertEquals(0f, dismiss.scaleFactor, 0f)
                systemValue = 10f
                assertEquals(5f, dismiss.scaleFactor, 0f)
            }
            assertSame(system, currentCoroutineContext()[MotionDurationScale])
        }
    }

    @Test fun cancellingOwnerDoesNotRunDismissCompletion() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var completed = false
        val owner = launch {
            withDialogDismissMotion {
                started.complete(Unit)
                awaitCancellation()
            }
            completed = true
        }
        started.await()
        owner.cancel()
        owner.join()
        yield()
        assertFalse(completed)
    }
}
