package yos.music.player.data.remote

import kotlinx.coroutines.*
import yos.music.player.code.MediaController

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val activeJobs = mutableMapOf<String, Job>()

    private fun key(uri: String): String = uri

    /** 播放中提取标签，失败时调度重试 */
    fun onTagExtractFailed(uri: String, serverId: String, remotePath: String) {
        synchronized(lock) {
            val k = key(uri)
            val state = retryMap.getOrPut(k) {
                RetryState(uri, serverId, remotePath, isPlayback = true)
            }
            state.failCount++
            val delay = if (state.failCount <= playbackIntervals.size) {
                playbackIntervals[state.failCount - 1]
            } else {
                PLAYBACK_INTERVAL_DEFAULT
            }
            state.nextRetryTimeMs = System.currentTimeMillis() + delay
            scheduleRetry(k, delay)
        }
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
        synchronized(lock) {
            val k = key(uri)
            val state = retryMap.getOrPut(k) {
                RetryState(uri, serverId, remotePath, isPlayback = false)
            }
            state.failCount++
            if (state.failCount < MAX_BACKGROUND_RETRIES) {
                val delay = backgroundDelays[state.failCount.coerceAtMost(backgroundDelays.size) - 1]
                state.nextRetryTimeMs = System.currentTimeMillis() + delay
            }
            return state.failCount
        }
    }

    /** 后台扫描成功 */
    fun onBackgroundScanSuccess(uri: String) {
        synchronized(lock) {
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
        // 必须在锁内调用，确保 activeJobs 的一致性
        activeJobs[k]?.cancel()
        activeJobs[k] = scope.launch {
            delay(delayMs)
            val state = synchronized(lock) { retryMap[k] } ?: return@launch
            if (!state.isPlayback) return@launch
            if (MediaController.musicPlaying.value?.uri?.toString() != state.uri) return@launch
            try {
                if (!RemoteServerManager.isConnected(state.serverId)) {
                    RemoteServerManager.connect(state.serverId)
                }
                val result = RemoteTagExtractor.extract(state.serverId, state.remotePath, state.uri)
                withContext(Dispatchers.Main) {
                    if (MediaController.musicPlaying.value?.uri?.toString() != state.uri) return@withContext
                    MediaController.applyExtractedTags(
                        state.uri, state.serverId, state.remotePath, result
                    )
                }
                onTagExtractSuccess(state.uri)
            } catch (_: Exception) {
                onTagExtractFailed(state.uri, state.serverId, state.remotePath)
            }
        }
    }

    private fun cancelRetry(uri: String) {
        // 必须在锁内调用
        activeJobs[key(uri)]?.cancel()
        activeJobs.remove(key(uri))
    }
}
