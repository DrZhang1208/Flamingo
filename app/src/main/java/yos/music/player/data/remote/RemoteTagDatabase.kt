package yos.music.player.data.remote

import android.content.Context
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import java.io.File
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
    private const val REMOTE_COVER_DIR = "remote_covers"
    private val mmkv by lazy { MMKV.mmkvWithID(MMKV_ID) }
    private val gson = Gson()
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

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
        touchCover(updated.coverPath)
    }

    fun delete(uri: String) {
        getRaw(uri)?.coverPath?.let { deleteCover(it) }
        mmkv.removeValueForKey(uri)
    }

    fun count(): Int {
        return mmkv.allKeys()?.size ?: 0
    }

    fun cleanup(maxDays: Int = 30) {
        val cutoff = System.currentTimeMillis() / 1000 - maxDays * 86400L
        val allKeys = mmkv.allKeys() ?: emptyArray()
        for (key in allKeys) {
            val json = mmkv.decodeString(key) ?: continue
            try {
                val tags = gson.fromJson(json, CachedTags::class.java)
                if (tags.updatedAt < cutoff) {
                    deleteCover(tags.coverPath)
                    mmkv.removeValueForKey(key)
                }
            } catch (_: Exception) {
                mmkv.removeValueForKey(key)
            }
        }
        cleanupCoverFiles(maxDays)
    }

    fun clearAll() {
        val allKeys = mmkv.allKeys() ?: emptyArray()
        for (key in allKeys) {
            getRaw(key)?.coverPath?.let { deleteCover(it) }
            mmkv.removeValueForKey(key)
        }
        clearCoverFiles()
    }

    private fun getRaw(uri: String): CachedTags? {
        val json = mmkv.decodeString(uri) ?: return null
        return runCatching { gson.fromJson(json, CachedTags::class.java) }.getOrNull()
    }

    private fun touchCover(coverPath: String?) {
        val file = coverFile(coverPath) ?: return
        if (file.exists()) runCatching { file.setLastModified(System.currentTimeMillis()) }
    }

    private fun deleteCover(coverPath: String?) {
        val file = coverFile(coverPath) ?: return
        if (file.exists()) runCatching { file.delete() }
    }

    private fun coverFile(coverPath: String?): File? {
        if (coverPath.isNullOrBlank()) return null
        val rawPath = coverPath.removePrefix("file://").removePrefix("file:")
        return File(rawPath)
    }

    private fun cleanupCoverFiles(maxDays: Int) {
        val cutoff = System.currentTimeMillis() - maxDays * 86400_000L
        coverDir()?.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private fun clearCoverFiles() {
        coverDir()?.listFiles()?.forEach { file ->
            if (file.isFile) runCatching { file.delete() }
        }
    }

    private fun coverDir(): File? {
        val context = appContext ?: RemoteServerManager.appContext ?: return null
        return File(context.cacheDir, REMOTE_COVER_DIR)
    }
}
