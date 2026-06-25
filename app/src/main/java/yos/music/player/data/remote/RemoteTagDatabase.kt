package yos.music.player.data.remote

import android.content.Context
import android.net.Uri
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
        migrateCoversToFilesDir()
    }

    /**
     * 将封面从 cacheDir 迁移到 filesDir。
     *
     * 历史上封面存放在 cacheDir/remote_covers，而 cacheDir 会被系统在存储压力下随时清除，
     * 但 MMKV 中的标签索引（含 coverPath 与 count）存放在 filesDir 不会丢失，导致
     * "封面全部消失但缓存数量不变"的现象。改为持久化目录后做一次性迁移：
     * 把旧 cacheDir/remote_covers 下还存在的封面搬到 filesDir/remote_covers，
     * 并更新 MMKV 中的 coverPath 指向新位置。
     */
    private fun migrateCoversToFilesDir() {
        val context = appContext ?: return
        val oldDir = File(context.cacheDir, REMOTE_COVER_DIR)
        val newDir = coverDir() ?: return
        if (!oldDir.isDirectory) return
        newDir.mkdirs()
        var moved = false
        oldDir.listFiles()?.forEach { src ->
            if (!src.isFile) return@forEach
            val dst = File(newDir, src.name)
            // 同名文件已存在于新目录则跳过，避免覆盖
            if (dst.exists()) {
                runCatching { src.delete() }
                return@forEach
            }
            if (src.renameTo(dst)) {
                // 刷新时间戳，避免迁移来的旧文件因 lastModified 过老在随后的 30 天清理中被立即删除，
                // 造成"刚迁移完封面又没了"的二次丢失；交给正常淘汰机制自然管理。
                runCatching { dst.setLastModified(System.currentTimeMillis()) }
                moved = true
            }
        }
        if (moved) {
            // 重写 MMKV 中所有 coverPath，把指向旧目录的路径替换为新目录
            val allKeys = mmkv.allKeys() ?: emptyArray()
            for (key in allKeys) {
                val json = mmkv.decodeString(key) ?: continue
                val tags = runCatching { gson.fromJson(json, CachedTags::class.java) }.getOrNull() ?: continue
                val cp = tags.coverPath ?: continue
                val oldPrefix = Uri.fromFile(oldDir).toString()
                if (!cp.startsWith(oldPrefix)) continue
                val fileName = cp.removePrefix(oldPrefix).removePrefix("/")
                val newCoverPath = Uri.fromFile(File(newDir, fileName)).toString()
                mmkv.encode(key, gson.toJson(tags.copy(coverPath = newCoverPath)))
            }
        }
        // 清理空旧目录
        if (oldDir.listFiles()?.isEmpty() == true) runCatching { oldDir.delete() }
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

    /** 封面持久化目录：使用 filesDir 而非 cacheDir，避免被系统缓存清理回收。 */
    fun coverDir(): File? {
        val context = appContext ?: RemoteServerManager.appContext ?: return null
        return File(context.filesDir, REMOTE_COVER_DIR)
    }
}
