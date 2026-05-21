package yos.music.player.data.remote

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import yos.music.player.data.libraries.TagScanStatus
import yos.music.player.data.libraries.YosMediaItem

/**
 * 远程文件夹扫描状态
 */
data class ScanProgress(
    val serverId: String = "",
    val total: Int = 0,
    val completed: Int = 0,
    val currentFile: String = "",
    val isScanning: Boolean = false
)

object RemoteMetadataScanner {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var scanJob: Job? = null

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress

    /**
     * 阶段一：快速列举远程目录中的音频文件。
     * 返回带基本文件信息的 YosMediaItem 列表（tagScanStatus = PENDING）。
     */
    fun quickListAudioFiles(serverId: String, remotePath: String, serverConfig: ServerConfig): List<YosMediaItem> {
        val files = RemoteServerManager.listAudioFiles(serverId, remotePath)
        return files.map { rf ->
            val scheme = if (serverConfig.type == ServerType.SMB) "smb" else "webdav"
            val uri = Uri.parse("$scheme://${serverConfig.id}/${rf.path.trimStart('/')}")
            val title = rf.name.substringBeforeLast('.')
            val mimeType = inferMimeType(rf.name)

            YosMediaItem(
                uri = uri,
                mediaId = "$scheme:${serverConfig.id}:${rf.path}",
                mimeType = mimeType,
                title = title,
                album = null,
                artists = null,
                thumb = null,
                duration = 0L,
                modifiedDate = rf.modifiedDate,
                trackNumber = null,
                discNumber = null,
                genre = null,
                recordingDay = null,
                recordingMonth = null,
                recordingYear = null,
                releaseYear = null,
                artistId = null,
                albumId = null,
                genreId = null,
                author = null,
                addDate = System.currentTimeMillis(),
                writer = null,
                compilation = null,
                composer = null,
                albumArtists = null,
                cdTrackNumber = null,
                serverId = serverConfig.id,
                tagScanStatus = TagScanStatus.PENDING
            )
        }
    }

    /**
     * 阶段二：后台逐首读取远程文件头部，提取 duration 并更新 YosMediaItem。
     * 同时在服务器上更新 MusicLibrary 的 songs/folders。
     */
    fun startBackgroundScan(
        items: List<YosMediaItem>,
        serverId: String,
        onItemUpdated: (YosMediaItem) -> Unit
    ) {
        scanJob?.cancel()
        scanJob = scope.launch {
            val pending = items.filter { it.tagScanStatus == TagScanStatus.PENDING }
            if (pending.isEmpty()) return@launch

            _scanProgress.value = ScanProgress(serverId, pending.size, 0, "", true)

            for ((i, item) in pending.withIndex()) {
                val path = item.uri?.path ?: continue
                _scanProgress.value = ScanProgress(serverId, pending.size, i, item.title ?: path, true)

                try {
                    // 读取文件头部 256KB
                    val header = RemoteServerManager.readFileBytes(serverId, path, 0, 256 * 1024)
                    // 提取时长
                    val duration = RemoteMetadataExtractor.extractDuration(header, item.title ?: path)
                    // 从头部解析 ID3 基本标签（仅 MP3）
                    val tags = parseBasicTags(header, path)

                    val updated = item.copy(
                        duration = if (duration > 0) duration else item.duration,
                        title = tags.title ?: item.title,
                        artists = tags.artist ?: item.artists,
                        album = tags.album ?: item.album,
                        releaseYear = tags.year ?: item.releaseYear,
                        recordingYear = tags.year ?: item.recordingYear,
                        tagScanStatus = TagScanStatus.COMPLETE
                    )
                    onItemUpdated(updated)
                } catch (_: Exception) {
                    val failed = item.copy(tagScanStatus = TagScanStatus.FAILED)
                    onItemUpdated(failed)
                }
            }

            _scanProgress.value = ScanProgress(serverId, pending.size, pending.size, "", false)
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scanProgress.value = ScanProgress()
    }

    // --- Helpers ---

    private fun inferMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "wma" -> "audio/x-ms-wma"
            "opus" -> "audio/opus"
            "ape" -> "audio/ape"
            "aiff" -> "audio/aiff"
            else -> "audio/*"
        }
    }

    /**
     * 从字节缓冲区解析基本 ID3v2 标签（TIT2, TPE1, TALB, TYER/TDRC）。
     * 对于 FLAC/OGG，仅依赖文件名（Vorbis Comment 解析太复杂，播放时由 ExoPlayer 补充）。
     */
    private fun parseBasicTags(header: ByteArray, fileName: String): BasicTags {
        if (header.size < 10) return BasicTags()

        // 检查 ID3v2 头
        if (header[0] != 0x49.toByte() || header[1] != 0x44.toByte() || header[2] != 0x33.toByte()) {
            return BasicTags()
        }

        val id3Size = ((header[6].toInt() and 0x7F) shl 21) or
                      ((header[7].toInt() and 0x7F) shl 14) or
                      ((header[8].toInt() and 0x7F) shl 7) or
                      (header[9].toInt() and 0x7F) + 10

        val end = minOf(header.size, id3Size)
        var pos = 10

        // 跳过扩展头
        val flags = header[5].toInt() and 0xFF
        if (flags and 0x40 != 0) {
            val extSize = ((header[pos].toInt() and 0x7F) shl 21) or
                          ((header[pos + 1].toInt() and 0x7F) shl 14) or
                          ((header[pos + 2].toInt() and 0x7F) shl 7) or
                          (header[pos + 3].toInt() and 0x7F)
            pos += extSize + 4
        }

        val tags = BasicTags()

        while (pos + 10 <= end) {
            val frameId = String(header, pos, 4, Charsets.UTF_8)
            val frameSize = if (header[5].toInt() and 0x10 != 0) { // ID3v2.4 uses synchsafe
                ((header[pos + 4].toInt() and 0x7F) shl 21) or
                ((header[pos + 5].toInt() and 0x7F) shl 14) or
                ((header[pos + 6].toInt() and 0x7F) shl 7) or
                (header[pos + 7].toInt() and 0x7F)
            } else {
                ((header[pos + 4].toInt() and 0xFF) shl 24) or
                ((header[pos + 5].toInt() and 0xFF) shl 16) or
                ((header[pos + 6].toInt() and 0xFF) shl 8) or
                (header[pos + 7].toInt() and 0xFF)
            }

            if (frameSize <= 0 || pos + 10 + frameSize > end) break

            val encoding = header[pos + 10].toInt() and 0xFF
            val contentStart = pos + 11

            if (frameId[0].code == 0) break // padding reached

            when (frameId) {
                "TIT2" -> tags.title = readId3String(header, contentStart, frameSize - 1, encoding)
                "TPE1" -> tags.artist = readId3String(header, contentStart, frameSize - 1, encoding)
                "TALB" -> tags.album = readId3String(header, contentStart, frameSize - 1, encoding)
                "TYER" -> tags.year = readId3String(header, contentStart, frameSize - 1, encoding)?.toIntOrNull()
                "TDRC" -> tags.year = readId3String(header, contentStart, frameSize - 1, encoding)?.take(4)?.toIntOrNull()
            }

            pos += 10 + frameSize
        }

        return tags
    }

    private fun readId3String(data: ByteArray, start: Int, len: Int, encoding: Int): String? {
        if (start >= data.size || len <= 0) return null
        val end = minOf(start + len, data.size)
        return when (encoding) {
            0 -> String(data, start + 1, end - start - 1, Charsets.ISO_8859_1).trimEnd(' ')
            1 -> String(data, start + 3, end - start - 3, Charsets.UTF_16).trimEnd(' ')
            2 -> String(data, start + 3, end - start - 3, Charsets.UTF_16BE).trimEnd(' ')
            3 -> String(data, start + 1, end - start - 1, Charsets.UTF_8).trimEnd(' ')
            else -> null
        }
    }

    private class BasicTags(
        var title: String? = null,
        var artist: String? = null,
        var album: String? = null,
        var year: Int? = null
    )
}
