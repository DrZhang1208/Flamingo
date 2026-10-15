package yos.music.player.data.libraries

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 听歌统计库：聆听时长、最近播放、每日聆听分布。
 *
 * 数据通过独立的 MMKV 实例持久化，遵循 MusicLibrary 的 Gson+MMKV 模式：
 * - history：有效播放记录（播放超过一半时长记一次，与播放次数口径一致），倒序裁剪。
 * - duration：每首歌的累计聆听毫秒数（按实际播放的墙钟时间 × 播放速度累计）。
 * - daily：按天的聆听毫秒数，用于最近 7 天分布图与"本周"统计。
 *
 * 所有统计记录均受 SettingsLibrary.ListenHistory 控制（与播放次数一致）。
 */
@Stable
object ListenStatsLibrary {

    /** 一次有效播放记录 */
    @Stable
    data class ListenRecord(val uri: String, val timestamp: Long)

    private const val mmkvID = "yos_listen_stats"
    private const val historyKey = "listen_history"
    private const val durationKey = "listen_duration"
    private const val dailyKey = "listen_daily"

    private const val MAX_HISTORY = 300
    private const val MAX_DAILY_DAYS = 366
    private const val FLUSH_INTERVAL_MS = 15_000L

    /** 数据版本号，UI 读取后即可在数据变更时重组 */
    var version by mutableIntStateOf(0)
        private set

    private val lock = Any()
    private val gson by lazy { Gson() }
    private val mmkv by lazy { MMKV.mmkvWithID(mmkvID) }
    private val dayKeyFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    private val historyRecords: MutableList<ListenRecord> by lazy {
        loadData<List<ListenRecord>>(historyKey)?.toMutableList() ?: mutableListOf()
    }

    private val durationMap: MutableMap<String, Long> by lazy {
        loadData<Map<String, Long>>(durationKey)?.toMutableMap() ?: mutableMapOf()
    }

    private val dailyMap: MutableMap<String, Long> by lazy {
        val loaded = loadData<Map<String, Long>>(dailyKey) ?: mutableMapOf()
        // 只保留最近一年，防止无限增长
        val cutoff = dayKey(System.currentTimeMillis() - MAX_DAILY_DAYS * 24L * 60L * 60L * 1000L)
        loaded.filterKeys { it >= cutoff }.toMutableMap()
    }

    private var lastFlushAt = 0L

    /**
     * 持久化走单线程后台队列：MMKV 写入串行有序，避免旧快照覆盖新数据；
     * 调用方（播放服务主线程）只做内存更新和快照拷贝，Gson 序列化不占主线程
     */
    private val persistExecutor: java.util.concurrent.ExecutorService by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ListenStatsPersist").apply { isDaemon = true }
        }
    }

    private fun bumpVersion() {
        version++
    }

    /** 后台序列化并写入；value 必须是不可变快照，不能传内部可变集合 */
    private fun persistAsync(key: String, snapshot: Any) {
        persistExecutor.execute {
            runCatching { mmkv.encode(key, gson.toJson(snapshot)) }
        }
    }

    /** 记录一次有效播放（与 incrementPlayCount 同时调用） */
    fun onValidPlay(uri: String) {
        val snapshots = synchronized(lock) {
            historyRecords.add(ListenRecord(uri, System.currentTimeMillis()))
            if (historyRecords.size > MAX_HISTORY) {
                historyRecords.subList(0, historyRecords.size - MAX_HISTORY).clear()
            }
            lastFlushAt = System.currentTimeMillis()
            Triple(historyRecords.toList(), durationMap.toMap(), dailyMap.toMap())
        }
        persistAsync(historyKey, snapshots.first)
        persistAsync(durationKey, snapshots.second)
        persistAsync(dailyKey, snapshots.third)
        bumpVersion()
    }

    /**
     * 累计聆听时长（毫秒）。播放服务每秒调用一次，内部按 15 秒节流落盘。
     */
    fun addListenDuration(uri: String, ms: Long) {
        if (ms <= 0) return
        val flushSnapshots: Pair<Map<String, Long>, Map<String, Long>>? = synchronized(lock) {
            durationMap[uri] = (durationMap[uri] ?: 0L) + ms
            val today = dayKey(System.currentTimeMillis())
            dailyMap[today] = (dailyMap[today] ?: 0L) + ms
            val now = System.currentTimeMillis()
            if (now - lastFlushAt >= FLUSH_INTERVAL_MS) {
                lastFlushAt = now
                Pair(durationMap.toMap(), dailyMap.toMap())
            } else null
        }
        if (flushSnapshots != null) {
            persistAsync(durationKey, flushSnapshots.first)
            persistAsync(dailyKey, flushSnapshots.second)
            bumpVersion()
        }
    }

    /** 立即落盘（服务销毁等场景调用）；进入同一串行队列，等待完成保证不丢数据 */
    fun flush() {
        val snapshots = synchronized(lock) {
            lastFlushAt = System.currentTimeMillis()
            Pair(durationMap.toMap(), dailyMap.toMap())
        }
        val future = persistExecutor.submit {
            runCatching {
                mmkv.encode(durationKey, gson.toJson(snapshots.first))
                mmkv.encode(dailyKey, gson.toJson(snapshots.second))
            }
        }
        runCatching { future.get(1, java.util.concurrent.TimeUnit.SECONDS) }
    }

    // --- 查询 ---

    /** 是否有任何统计数据（用于主页空态判断） */
    fun hasAnyData(): Boolean = synchronized(lock) {
        historyRecords.isNotEmpty() || durationMap.values.sum() > 0
    }

    /** 累计聆听总时长（毫秒） */
    fun getTotalDuration(): Long = synchronized(lock) { durationMap.values.sum() }

    /** 单首歌累计聆听时长（毫秒） */
    fun getListenDuration(uri: String): Long = synchronized(lock) { durationMap[uri] ?: 0L }

    /**
     * 最近 N 天每天的聆听时长。返回按日期升序排列的 (当天起始时间戳, 毫秒)，
     * 共 [days] 项，今天在最后一项。
     */
    fun getDailyDurations(days: Int): List<Pair<Long, Long>> {
        val todayStart = startOfDay(System.currentTimeMillis())
        val snapshot = synchronized(lock) { dailyMap.toMap() }
        return (days - 1 downTo 0).map { offset ->
            val dayStart = todayStart - offset * 24L * 60L * 60L * 1000L
            val ms = snapshot[dayKey(dayStart)] ?: 0L
            Pair(dayStart, ms)
        }
    }

    /** 最近 [days] 天内的聆听总时长（含今天） */
    fun getDurationInRecentDays(days: Int): Long =
        getDailyDurations(days).sumOf { it.second }

    /**
     * 最近播放的歌曲 URI（按播放时间倒序，按 URI 去重，仅限最近 [days] 天内、0 表示不限）。
     */
    fun getRecentUris(limit: Int, days: Int = 0): List<String> {
        val cutoff = if (days > 0) System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L else 0L
        val records = synchronized(lock) { historyRecords.toList() }
        val seen = HashSet<String>()
        val result = ArrayList<String>(limit)
        for (record in records.asReversed()) {
            if (record.timestamp < cutoff) continue
            if (seen.add(record.uri)) {
                result.add(record.uri)
                if (result.size >= limit) break
            }
        }
        return result
    }

    /** 最近 [days] 天（0 = 全部历史记录范围内）各 URI 的有效播放次数 */
    fun getPlayCountsInRecentDays(days: Int): Map<String, Int> {
        val cutoff = if (days > 0) System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L else 0L
        val records = synchronized(lock) { historyRecords.toList() }
        val counts = HashMap<String, Int>()
        for (record in records) {
            if (record.timestamp < cutoff) continue
            counts[record.uri] = (counts[record.uri] ?: 0) + 1
        }
        return counts
    }

    // --- 工具 ---

    private fun dayKey(timeMs: Long): String = dayKeyFormat.get()!!.format(Date(timeMs))

    private fun startOfDay(timeMs: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private inline fun <reified T> loadData(key: String): T? {
        val json = runCatching { mmkv.decodeString(key) }.getOrNull() ?: return null
        return runCatching {
            gson.fromJson<T>(json, object : TypeToken<T>() {}.type)
        }.getOrNull()
    }
}
