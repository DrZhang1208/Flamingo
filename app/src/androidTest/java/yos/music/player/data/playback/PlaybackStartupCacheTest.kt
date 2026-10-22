package yos.music.player.data.playback

import android.net.Uri
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import com.tencent.mmkv.MMKV
import org.junit.Assert.*
import org.junit.Test
import yos.music.player.data.libraries.PlayStatus
import yos.music.player.data.libraries.MusicLibrary.toYosMediaItem
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.util.UUID

class PlaybackStartupCacheTest {
    @Test fun coldReadPreservesCoverAndProgressAndClearingRemovesPreview() {
        MMKV.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val store = MMKV.mmkvWithID("startup-preview-test-${UUID.randomUUID()}")
        try {
            val song = MediaItem.Builder().setUri("file:///music/song.flac")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Saved song")
                    .setArtworkUri(Uri.parse("file:///covers/saved.jpg")).build()).build().toYosMediaItem()
            val expected = PlayStatus(song, 42000L, true, Player.REPEAT_MODE_ONE)
            PlaybackStartupCache.write(expected, store)
            val restored = PlaybackStartupCache.read(store)
            assertEquals(expected, restored)
            assertEquals(song.thumb, restored?.music?.thumb)
            PlaybackStartupCache.write(expected.copy(music = null), store)
            assertNull(PlaybackStartupCache.read(store))
        } finally { store.clearAll(); store.close() }
    }

    @Test fun corruptPreviewDoesNotCrashStartup() {
        MMKV.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val store = MMKV.mmkvWithID("startup-preview-test-${UUID.randomUUID()}")
        try {
            store.encode("playback_startup_preview_v1", "invalid-json")
            assertNull(PlaybackStartupCache.read(store))
        } finally { store.clearAll(); store.close() }
    }
}
