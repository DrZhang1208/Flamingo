package yos.music.player.data.remote

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var scanJob: Job? = null

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress

    /**
     * 阶段一：快速列举远程目录中的音频文件。
     * 返回带基本文件信息的 YosMediaItem 列表（tagScanStatus = PENDING）。
     */
    fun quickListAudioFiles(serverId: String, remotePath: String, serverConfig: ServerConfig): List<YosMediaItem> {
        val files = RemoteServerManager.listAudioFiles(serverId, remotePath)
        return files.map { rf ->
            val scheme = "webdav"
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
                tagScanStatus = "PENDING"
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
        forceRescan: Boolean = false,
        onItemUpdated: (YosMediaItem) -> Unit
    ) {
        scanJob?.cancel()
        scanJob = scope.launch {
            val pending = items.filter { it.tagScanStatus == "PENDING" }
            // 包含未达重试上限的 FAILED 项
            val recoverableUris = RemoteRetryManager.getPendingBackgroundRetries(serverId)
                .map { it.uri }.toSet()
            val failedRetry = items.filter {
                it.tagScanStatus == "FAILED" && it.uri?.toString() in recoverableUris
            }
            val completeRetry = if (forceRescan) {
                items.filter { it.tagScanStatus == "COMPLETE" || it.tagScanStatus == null }
            } else emptyList()
            val toProcess = (pending + failedRetry + completeRetry).distinctBy { it.uri?.toString() }
            if (toProcess.isEmpty()) return@launch

            _scanProgress.value = ScanProgress(serverId, toProcess.size, 0, "", true)

            for ((i, item) in toProcess.withIndex()) {
                val path = item.uri?.path ?: continue
                val uri = item.uri?.toString() ?: path
                _scanProgress.value = ScanProgress(serverId, toProcess.size, i, item.title ?: path, true)

                try {
                    val result = RemoteTagExtractor.extract(serverId, path, uri)
                    val updated = item.copy(
                        duration = result.duration ?: item.duration,
                        title = result.title ?: item.title,
                        artists = result.artist ?: item.artists,
                        albumArtists = result.albumArtist ?: item.albumArtists,
                        album = result.album ?: item.album,
                        genre = result.genre ?: item.genre,
                        releaseYear = result.year ?: item.releaseYear,
                        recordingYear = result.year ?: item.recordingYear,
                        trackNumber = result.trackNumber ?: item.trackNumber,
                        discNumber = result.discNumber ?: item.discNumber,
                        composer = result.composer ?: item.composer,
                        thumb = result.coverUri ?: item.thumb,
                        bitrate = result.bitrate ?: item.bitrate,
                        sampleRate = result.sampleRate ?: item.sampleRate,
                        channels = result.channels ?: item.channels,
                        tagScanStatus = "COMPLETE"
                    )
                    RemoteTagDatabase.put(uri, CachedTags(
                        uri = uri,
                        title = updated.title,
                        artist = updated.artists,
                        album = updated.album,
                        year = updated.releaseYear ?: updated.recordingYear,
                        duration = if (updated.duration > 0) updated.duration else null,
                        coverPath = result.coverUri?.toString(),
                        lyrics = result.lyrics,
                        bitrate = result.bitrate,
                        sampleRate = result.sampleRate,
                        channels = result.channels
                    ))
                    RemoteRetryManager.onBackgroundScanSuccess(uri)
                    onItemUpdated(updated)
                } catch (_: Exception) {
                    val failCount = RemoteRetryManager.onBackgroundScanFailed(uri, serverId, path)
                    val newStatus = if (failCount >= RemoteRetryManager.MAX_BACKGROUND_RETRIES) "FAILED"
                        else RemoteRetryManager.encodeFailStatus(failCount)
                    val failed = item.copy(tagScanStatus = newStatus)
                    onItemUpdated(failed)
                }
            }

            _scanProgress.value = ScanProgress(serverId, toProcess.size, toProcess.size, "", false)
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scanProgress.value = ScanProgress()
    }

    // --- Helpers ---

    fun inferMimeType(fileName: String): String {
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

}
