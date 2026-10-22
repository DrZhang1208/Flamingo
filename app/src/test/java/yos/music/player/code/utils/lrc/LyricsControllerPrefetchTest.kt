package yos.music.player.code.utils.lrc

import org.junit.Assert.*
import org.junit.Test

/**
 * JVM-only coverage for the cache-warming APIs. Paths that touch
 * SettingsLibrary/MMKV (activateTrack, submitRawLyrics) run in androidTest.
 */
class LyricsControllerPrefetchTest {
    @Test fun prefetchFillsTheCacheForTimedAndPlainLyrics() {
        val key = "content://media/track-prefetch"
        assertFalse(LyricsController.isCached(key))
        assertTrue(
            LyricsController.cacheRawLyrics(
                key, "[00:01.00]first line\n[00:05.00]second line",
                LyricSource.SIDECAR_FILE, 200_000L
            )
        )
        assertTrue(LyricsController.isCached(key))
        // Unknown keys stay uncached so later prefetches can still fill them.
        assertFalse(LyricsController.isCached("content://media/track-absent"))
        assertFalse(LyricsController.isCached(null))
    }

    @Test fun prefetchRejectsBlankContentAndFallsBackToPlainParsing() {
        val blank = "content://media/track-blank"
        assertFalse(LyricsController.cacheRawLyrics(blank, "   ", LyricSource.EMBEDDED_TAG, 0L))
        assertFalse(LyricsController.isCached(blank))

        // Plain-text fallback accepts content without timestamps.
        val noise = "content://media/track-noise"
        assertTrue(LyricsController.cacheRawLyrics(noise, "no timestamps here", LyricSource.EMBEDDED_TAG, 0L))
        assertTrue(LyricsController.isCached(noise))
    }

    @Test fun repeatedPrefetchOfTheSameKeyIsHarmless() {
        val key = "content://media/track-repeat"
        assertTrue(LyricsController.cacheRawLyrics(key, "[00:01.00]first", LyricSource.SIDECAR_FILE, 0L))
        // A second producer parsing the same key must not throw or clear the slot.
        assertTrue(LyricsController.cacheRawLyrics(key, "[00:09.00]second", LyricSource.EMBEDDED_TAG, 0L))
        assertTrue(LyricsController.isCached(key))
    }
}
