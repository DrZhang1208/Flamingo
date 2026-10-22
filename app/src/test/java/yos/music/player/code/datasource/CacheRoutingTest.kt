package yos.music.player.code.datasource

import org.junit.Assert.*
import org.junit.Test

class CacheRoutingTest {
    @Test fun remoteSchemesRouteThroughThePlaybackCache() {
        assertTrue(shouldRouteToPlaybackCache("webdav://nas/music/a.flac", "webdav"))
        assertTrue(shouldRouteToPlaybackCache("https://example.com/a.mp3", "https"))
        assertTrue(shouldRouteToPlaybackCache("http://example.com/a.mp3", "http"))
        assertTrue(shouldRouteToPlaybackCache("webdav://nas/music/a.flac", null))
    }

    @Test fun localSourcesReadDirectlyAndNeverTouchTheCache() {
        assertFalse(shouldRouteToPlaybackCache("content://media/external/audio/1", "content"))
        assertFalse(shouldRouteToPlaybackCache("file:///storage/a.mp3", "file"))
        assertFalse(shouldRouteToPlaybackCache("/storage/a.mp3", null))
    }
}
