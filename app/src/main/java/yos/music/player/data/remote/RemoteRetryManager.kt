package yos.music.player.data.remote

import kotlinx.coroutines.*
import yos.music.player.code.utils.player.optionalPlaybackTask
import yos.music.player.code.MediaController
import yos.music.player.code.AudioMetadataUtils
import yos.music.player.data.libraries.MusicLibrary

object RemoteRetryManager {

    // 播放中重试间隔（ms）：2s, 4s, 8s, 之后每10s
    private val playbackIntervals = longArrayOf(2000L, 4000L, 8000L)
    private const val PLAYBACK_INTERVAL_DEFAULT = 10_000L

    // 后台扫描重试退避
    private val backgroundDelays = longArrayOf(10_000L, 60_000L, 300_000L)
    const val MAX_BACKGROUND_RETRIES = 3

    data class RetryState(
        val uri: String,
        val serverId: String,
        val remotePath: String,
        var failCount: Int = 0,
        var nextRetryTimeMs: Long = 0L,
        val isPlayback: Boolean = false
    )

    private val lock = Any()
    private val retryMap = mutableMapOf<String, RetryState>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    private fun key(uri: String): String = uri

    /** 播放中提取标签，失败时调度重试 */
    fun onTagExtractFailed(uri: String, serverId: String, remotePath: String) {
        val (k, delay) = synchronized(lock) {
            val k = key(uri)
            val state = retryMap.getOrPut(k) {
                RetryState(uri, serverId, remotePath, isPlayback = true)
            }
            state.failCount++
            val d = if (state.failCount <= playbackIntervals.size) {
                playbackIntervals[state.failCount - 1]
            } else {
                PLAYBACK_INTERVAL_DEFAULT
            }
            state.nextRetryTimeMs = System.currentTimeMillis() + d
            k to d
        }
        // 必须在 synchronized 外调用 scheduleRetry，防止内部协程回调
        // (onTagExtractSuccess / onTagExtractFailed) 重入 lock 时的死锁。
        scheduleRetry(k, delay)
    }

    /** 播放中提取成功，清除重试状态 */
    fun onTagExtractSuccess(uri: String) {
        synchronized(lock) {
            cancelRetry(uri)
            retryMap.remove(key(uri))
        }
    }

    /** 切歌时取消旧歌的重试 */
    fun onTrackChanged(newUri: String?) {
        synchronized(lock) {
            val toCancel = activeJobs.entries.filter { it.key != newUri }.toList()
            toCancel.forEach { (k, job) ->
                job.cancel()
                activeJobs.remove(k)
            }
        }
    }

    /** 清除所有播放中的重试（停止播放时） */
    fun clearPlaybackRetries() {
        synchronized(lock) {
            val toCancel = activeJobs.entries.filter {
                retryMap[it.key]?.isPlayback == true
            }.toList()
            toCancel.forEach { (k, job) ->
                job.cancel()
                retryMap.remove(k)
                activeJobs.remove(k)
            }
        }
    }

    fun clearRetriesForUris(uris: Collection<String>) {
        val keys = uris.map(::key).toSet()
        if (keys.isEmpty()) return
        synchronized(lock) {
            keys.forEach { k ->
                activeJobs[k]?.cancel()
                activeJobs.remove(k)
                retryMap.remove(k)
            }
        }
    }

    fun clearRetriesForServer(serverId: String) {
        synchronized(lock) {
            val keys = retryMap.values
                .filter { it.serverId == serverId }
                .map { key(it.uri) }
                .toSet()
            keys.forEach { k ->
                activeJobs[k]?.cancel()
                activeJobs.remove(k)
                retryMap.remove(k)
            }
        }
    }

    /** 后台扫描失败，记录重试状态。返回当前失败次数 */
    fun onBackgroundScanFailed(uri: String, serverId: String, remotePath: String): Int {
        val (failCount, delay) = synchronized(lock) {
            val k = key(uri)
            val state = retryMap.getOrPut(k) {
                RetryState(uri, serverId, remotePath, isPlayback = false)
            }
            state.failCount++
            val delay = if (state.failCount < MAX_BACKGROUND_RETRIES) {
                backgroundDelays[state.failCount.coerceAtMost(backgroundDelays.size) - 1]
            } else {
                -1L
            }
            if (state.failCount < MAX_BACKGROUND_RETRIES) {
                state.nextRetryTimeMs = System.currentTimeMillis() + delay
            }
            state.failCount to delay
        }
        if (delay > 0L) {
            scheduleBackgroundRetry(key(uri), delay)
        }
        return failCount
    }

    /** 后台扫描成功 */
    fun onBackgroundScanSuccess(uri: String) {
        synchronized(lock) {
            cancelRetry(uri)
            retryMap.remove(key(uri))
        }
    }

    /** 获取某个服务器所有待重试的后台扫描项（未达上限且时间到了） */
    fun getPendingBackgroundRetries(serverId: String): List<RetryState> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            return retryMap.values.filter {
                !it.isPlayback && it.serverId == serverId &&
                it.failCount < MAX_BACKGROUND_RETRIES &&
                it.nextRetryTimeMs <= now
            }
        }
    }

    /** 是否可以在启动时重试（failCount < 上限，由 YosMediaItem.tagScanStatus 编码的计数） */
    fun isRecoverable(tagScanStatus: String?): Boolean {
        if (tagScanStatus == null) return false
        if (tagScanStatus == "PENDING") return true
        val count = parseFailCount(tagScanStatus)
        return count in 1 until MAX_BACKGROUND_RETRIES
    }

    /** 从 tagScanStatus 恢复重试状态（启动时调用） */
    fun restoreFromTagStatus(uri: String, serverId: String, remotePath: String, tagScanStatus: String?) {
        if (tagScanStatus == null || tagScanStatus == "COMPLETE" || tagScanStatus == "PENDING") return
        val count = parseFailCount(tagScanStatus)
        if (count <= 0) return
        synchronized(lock) {
            val k = key(uri)
            retryMap[k] = RetryState(
                uri = uri, serverId = serverId, remotePath = remotePath,
                failCount = count, isPlayback = false,
                nextRetryTimeMs = System.currentTimeMillis()
            )
        }
    }

    /** 编码 failCount 到 tagScanStatus */
    fun encodeFailStatus(count: Int): String = "FAILED_$count"

    /** 解析 tagScanStatus 中的 failCount */
    fun parseFailCount(status: String?): Int {
        if (status == null) return 0
        return status.removePrefix("FAILED_").toIntOrNull() ?: 0
    }

    private fun scheduleRetry(k: String, delayMs: Long) {
        // 内部自行同步 activeJobs；不要求调用方持锁，以避免协程回调重入 lock 造成死锁。
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(delayMs)
            val state = synchronized(lock) { retryMap[k] } ?: return@launch
            if (!state.isPlayback) return@launch
            if (MediaController.musicPlaying.value?.uri?.toString() != state.uri) return@launch
            val context = currentCoroutineContext()
            optionalPlaybackTask(onFailure = {
                context.ensureActive()
                onTagExtractFailed(state.uri, state.serverId, state.remotePath)
            }) {
                if (!RemoteServerManager.isConnected(state.serverId)) {
                    RemoteServerManager.connect(state.serverId)
                }
                val result = RemoteTagExtractor.extract(state.serverId, state.remotePath, state.uri)
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.Main) {
                    if (MediaController.musicPlaying.value?.uri?.toString() != state.uri) return@withContext
                    MediaController.applyExtractedTags(
                        state.uri, state.serverId, state.remotePath, result
                    )
                }
                onTagExtractSuccess(state.uri)
            }
        }
        val previous = synchronized(lock) {
            val prev = activeJobs[k]
            activeJobs[k] = job
            prev
        }
        previous?.cancel()
        job.start()
    }

    private fun scheduleBackgroundRetry(k: String, delayMs: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(delayMs)
            val state = synchronized(lock) { retryMap[k] } ?: return@launch
            if (state.isPlayback) return@launch

            val current = MusicLibrary.findSongByUri(state.uri) ?: run {
                synchronized(lock) {
                    activeJobs.remove(k)
                    retryMap.remove(k)
                }
                return@launch
            }

            val context = currentCoroutineContext()
            optionalPlaybackTask(onFailure = {
                context.ensureActive()
                onBackgroundScanFailed(state.uri, state.serverId, state.remotePath)
            }) {
                if (!RemoteServerManager.isConnected(state.serverId)) {
                    RemoteServerManager.connect(state.serverId)
                }
                val result = RemoteTagExtractor.extract(state.serverId, state.remotePath, state.uri)
                currentCoroutineContext().ensureActive()
                val duration = result.duration ?: current.duration
                val updated = current.copy(
                    duration = duration,
                    title = result.title ?: current.title,
                    artists = result.artist ?: current.artists,
                    albumArtists = result.albumArtist ?: current.albumArtists,
                    album = result.album ?: current.album,
                    genre = result.genre ?: current.genre,
                    releaseYear = result.year ?: current.releaseYear,
                    recordingYear = result.year ?: current.recordingYear,
                    trackNumber = result.trackNumber ?: current.trackNumber,
                    discNumber = result.discNumber ?: current.discNumber,
                    composer = result.composer ?: current.composer,
                    thumb = result.coverUri ?: current.thumb,
                    bitrate = AudioMetadataUtils.reliableBitrateKbps(
                        result.bitrate ?: current.bitrate,
                        current.fileSize,
                        duration.takeIf { it > 0L }
                    ) ?: result.bitrate ?: current.bitrate,
                    sampleRate = result.sampleRate ?: current.sampleRate,
                    channels = result.channels ?: current.channels,
                    tagScanStatus = "COMPLETE"
                )
                RemoteTagDatabase.put(state.uri, CachedTags(
                    uri = state.uri,
                    title = updated.title,
                    artist = updated.artists,
                    album = updated.album,
                    albumArtist = updated.albumArtists,
                    genre = updated.genre,
                    year = updated.releaseYear ?: updated.recordingYear,
                    duration = if (updated.duration > 0) updated.duration else null,
                    trackNumber = updated.trackNumber,
                    discNumber = updated.discNumber,
                    composer = updated.composer,
                    coverPath = updated.thumb?.toString(),
                    lyrics = result.lyrics,
                    bitrate = updated.bitrate,
                    sampleRate = updated.sampleRate,
                    channels = updated.channels,
                    fileSize = updated.fileSize
                ))
                withContext(Dispatchers.Main) {
                    MusicLibrary.updateSongInFullList(updated)
                    MusicLibrary.updateFolderSongs(
                        state.serverId,
                        state.remotePath.substringBeforeLast('/', ""),
                        updated
                    )
                }
                onBackgroundScanSuccess(state.uri)
            }
        }
        val previous = synchronized(lock) {
            val prev = activeJobs[k]
            activeJobs[k] = job
            prev
        }
        previous?.cancel()
        job.start()
    }

    private fun cancelRetry(uri: String) {
        // 必须在锁内调用
        activeJobs[key(uri)]?.cancel()
        activeJobs.remove(key(uri))
    }
}
