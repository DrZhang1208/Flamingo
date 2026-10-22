package yos.music.player.code.utils.lrc

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import yos.music.player.code.utils.player.playbackTail
import yos.music.player.code.utils.player.postPlaybackUpdate
import yos.music.player.code.utils.player.optionalPlaybackTask

class PlaybackUpdatesTest {
    @Test fun rapidTrackChangesDiscardQueuedOldUpdates() {
        val callbacks = mutableListOf<() -> Unit>()
        val published = mutableListOf<String>()
        val old = Job()
        postPlaybackUpdate(old, { callbacks.add(it) }) { published.add("A") }
        old.cancel()
        postPlaybackUpdate(Job(), { callbacks.add(it) }) { published.add("B") }
        callbacks.reversed().forEach { it() }
        assertEquals(listOf("B"), published)
    }

    @Test fun queueUsesNewPlayerHeadWhenUiStillShowsPreviousTrack() {
        val queue = listOf("A", "B", "C", "D")
        val tail = playbackTail(queue, queue, "B", true) { it }
        val result = listOf("B") + tail
        assertEquals("B", result.first())
        assertEquals(queue.toSet(), result.toSet())
        assertEquals(1, result.count { it == "B" })
        assertEquals(listOf("C", "D", "A"), playbackTail(result, queue, "B", false) { it })
    }

    @Test fun cancellationDuringWorkDoesNotScheduleFailureRetry() = runBlocking {
        var retried = false
        val started = CompletableDeferred<Unit>()
        val job = launch {
            optionalPlaybackTask({ retried = true }) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        job.cancelAndJoin()
        assertFalse(retried)
    }
}
