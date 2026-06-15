package yos.music.player.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import yos.music.player.code.AudioMetadataUtils

data class CachedTags(
    val uri: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val duration: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val composer: String? = null,
    val coverPath: String? = null,
    val lyrics: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val fileSize: Long? = null,
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

object RemoteTagDatabase {
    private const val MMKV_ID = "remote_tags"
    private val mmkv by lazy { MMKV.mmkvWithID(MMKV_ID) }
    private val gson = Gson()

    fun init(context: Context) { /* MMKV lazy inits */ }

    fun get(uri: String): CachedTags? {
        val json = mmkv.decodeString(uri) ?: return null
        return try {
            gson.fromJson(json, CachedTags::class.java)
                ?.let { it.copy(bitrate = AudioMetadataUtils.reliableBitrateKbps(it.bitrate, it.fileSize, it.duration)) }
        } catch (_: Exception) { null }
    }

    fun put(uri: String, tags: CachedTags) {
        val updated = tags.copy(updatedAt = System.currentTimeMillis() / 1000)
        mmkv.encode(uri, gson.toJson(updated))
    }

    fun delete(uri: String) { mmkv.removeValueForKey(uri) }

    fun count(): Int {
        return mmkv.allKeys()?.size ?: 0
    }

    fun cleanup(maxDays: Int = 30) {
        val cutoff = System.currentTimeMillis() / 1000 - maxDays * 86400L
        val allKeys = mmkv.allKeys() ?: return
        for (key in allKeys) {
            val json = mmkv.decodeString(key) ?: continue
            try {
                val tags = gson.fromJson(json, CachedTags::class.java)
                if (tags.updatedAt < cutoff) mmkv.removeValueForKey(key)
            } catch (_: Exception) { mmkv.removeValueForKey(key) }
        }
    }

    fun clearAll() {
        val allKeys = mmkv.allKeys() ?: return
        for (key in allKeys) {
            mmkv.removeValueForKey(key)
        }
    }
}
