package yos.music.player.data.remote

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kyant.taglib.AudioPropertiesReadStyle
import com.kyant.taglib.TagLib
import yos.music.player.code.AudioMetadataUtils

/**
 * 远程文件标签统一提取器——播放和扫描共用此入口。
 */
object RemoteTagExtractor {

    data class ExtractedResult(
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
        val lyrics: String? = null,
        val coverUri: Uri? = null,
        val bitrate: Int? = null,     // kbps
        val sampleRate: Int? = null,  // Hz
        val channels: Int? = null     // 声道数
    )

    /** 阶梯式读取大小：从 256KB 开始递增，最大 8MB */
    private val stepSizes = listOf(
        256 * 1024,
        512 * 1024,
        1024 * 1024,
        2 * 1024 * 1024,
        4 * 1024 * 1024,
        8 * 1024 * 1024
    )

    private fun hasUsefulData(r: ExtractedResult): Boolean =
        !r.title.isNullOrBlank() || !r.artist.isNullOrBlank() ||
        !r.album.isNullOrBlank() || (r.duration != null && r.duration > 0L)

    /**
     * 从远程文件头部提取完整标签。阶梯式读取，先小后大，成功即停。
     * @param serverId 服务器 ID
     * @param remotePath 远程文件路径
     * @param cacheKey 用于封面缓存命名的唯一标识
     */
    suspend fun extract(serverId: String, remotePath: String, cacheKey: String): ExtractedResult {
        for (size in stepSizes) {
            val header = RemoteServerManager.readFileBytes(serverId, remotePath, 0, size)
            if (header.isEmpty()) throw java.io.IOException("无法读取远程文件: $remotePath")
            val result = extractFromBytes(header, cacheKey)
            if (hasUsefulData(result)) return result
        }
        throw java.io.IOException("无法从文件提取标签: $remotePath")
    }

    private fun extractFromBytes(header: ByteArray, cacheKey: String): ExtractedResult {
        val tmpFile = java.io.File.createTempFile("tag_", ".tmp")
        tmpFile.writeBytes(header)

        try {
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var albumArtist: String? = null
            var genre: String? = null
            var year: Int? = null
            var duration: Long? = null
            var trackNumber: Int? = null
            var discNumber: Int? = null
            var composer: String? = null
            var lyrics: String? = null
            var coverUri: Uri? = null
            var bitrate: Int? = null
            var sampleRate: Int? = null
            var channels: Int? = null
            var tagMap: Map<String, Array<String>> = emptyMap()

            try {
                val meta = runCatching {
                    ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        TagLib.getMetadata(pfd.dup().detachFd(), false)
                    }
                }.getOrNull()
                val props = runCatching {
                    ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        TagLib.getAudioProperties(pfd.dup().detachFd(), AudioPropertiesReadStyle.Fast)
                    }
                }.getOrNull()
                val map = meta?.propertyMap ?: emptyMap()
                tagMap = map
                title = map["TITLE"]?.lastOrNull()
                artist = map["ARTIST"]?.lastOrNull()
                album = map["ALBUM"]?.lastOrNull()
                albumArtist = map["ALBUMARTIST"]?.lastOrNull()
                genre = map["GENRE"]?.lastOrNull()
                year = AudioMetadataUtils.extractYearFromTags(map)
                // TagLib JNI 不同音频格式可能返回秒或毫秒，安全换算
                val rawLength = props?.length?.toLong() ?: 0L
                duration = if (rawLength in 1..10000) rawLength * 1000L else rawLength
                trackNumber = map["TRACKNUMBER"]?.lastOrNull()?.toIntOrNull()
                discNumber = map["DISCNUMBER"]?.lastOrNull()?.toIntOrNull()
                composer = map["COMPOSER"]?.lastOrNull()
                bitrate = AudioMetadataUtils.normalizeBitrateKbps(props?.bitrate)
                sampleRate = props?.sampleRate
                channels = props?.channels
                val uslt = map.entries.firstOrNull { (k, _) ->
                    k.uppercase().let { it.contains("USLT") || it.contains("LYRICS") }
                }
                lyrics = uslt?.value?.lastOrNull()
            } catch (_: Exception) {}

            try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(tmpFile.absolutePath)
                    val coverBytes = retriever.embeddedPicture
                    if (coverBytes != null && coverBytes.isNotEmpty()) {
                        val coverDir = yos.music.player.data.remote.RemoteTagDatabase.coverDir()
                        if (coverDir != null) {
                            coverDir.mkdirs()
                            val coverFile = java.io.File(coverDir, "${cacheKey.hashCode()}.jpg")
                            coverFile.writeBytes(coverBytes)
                            coverUri = Uri.fromFile(coverFile)
                        }
                    }
                    if (title == null) title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    if (artist == null) artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    if (album == null) album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    if (albumArtist == null) albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    if (genre == null) genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    year = AudioMetadataUtils.extractYearFromTags(
                        tagMap,
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ) ?: year
                    if (duration == null) duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    if (trackNumber == null) trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull()
                    if (discNumber == null) discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.toIntOrNull()
                    if (composer == null) composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                } finally {
                    retriever.release()
                }
            } catch (_: Exception) {}

            return ExtractedResult(
                title, artist, album, albumArtist, genre, year, duration,
                trackNumber, discNumber, composer, lyrics, coverUri,
                bitrate, sampleRate, channels
            )
        } finally {
            tmpFile.delete()
        }
    }
}
