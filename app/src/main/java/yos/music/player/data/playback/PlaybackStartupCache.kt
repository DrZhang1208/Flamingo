package yos.music.player.data.playback

import android.net.Uri
import com.google.gson.GsonBuilder
import com.tencent.mmkv.MMKV
import yos.music.player.UriTypeAdapter
import yos.music.player.data.libraries.PlayStatus

/** One song only: render the last session without opening Room or decoding the queue. */
internal object PlaybackStartupCache {
    private const val KEY = "playback_startup_preview_v1"
    private val gson = GsonBuilder().registerTypeAdapter(Uri::class.java, UriTypeAdapter()).create()
    private val storage get() = MMKV.mmkvWithID("yos_player_core")

    fun read(store: MMKV = storage): PlayStatus? = runCatching {
        store.decodeString(KEY)?.let { gson.fromJson(it, PlayStatus::class.java) }
            ?.takeIf { it.music?.uri != null }
    }.getOrNull()

    // Room remains authoritative; callers update this disposable preview only after commit.
    fun write(status: PlayStatus, store: MMKV = storage) {
        if (status.music == null) store.removeValueForKey(KEY)
        else store.encode(KEY, gson.toJson(status.copy(position = status.position.coerceAtLeast(0L))))
    }
}
