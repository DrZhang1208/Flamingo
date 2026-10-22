package yos.music.player.code.utils.lrc

import org.junit.Assert.*
import org.junit.Test

/** Instrumented coverage: cache warming interacting with the active document. */
class LyricsControllerPrefetchTest {
    @Test fun activateTrackRendersAPrefetchedDocumentFromMemory() {
        val key = "content://media/android-track-prefetch"
        LyricsController.cacheRawLyrics(
            key, "[00:01.00]first line\n[00:05.00]second line",
            LyricSource.SIDECAR_FILE, 200_000L
        )
        LyricsController.activateTrack(key, currentPositionMs = 60_000)
        val state = LyricsController.currentState()
        assertEquals(key, state.trackKey)
        assertFalse(state.isLoading)
        assertEquals(2, state.entries.size)
        assertEquals("first line", state.entries[0].text)
    }

    @Test fun prefetchNeverDisturbsTheActiveTrack() {
        val active = "content://media/android-active"
        LyricsController.activateTrack(active, 0, 100_000L)
        LyricsController.submitRawLyrics(
            active, "[00:02.00]active line", 0, LyricSource.SIDECAR_FILE, 100_000L
        )
        val before = LyricsController.currentState()

        val upcoming = "content://media/android-upcoming"
        LyricsController.cacheRawLyrics(upcoming, "[00:03.00]upcoming", LyricSource.SIDECAR_FILE, 90_000L)

        val after = LyricsController.currentState()
        assertEquals(active, after.trackKey)
        assertEquals(before.entries, after.entries)
        assertTrue(LyricsController.isCached(upcoming))
    }
}
