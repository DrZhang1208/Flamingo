package yos.music.player.code.utils.lrc

import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.objects.MediaViewModelObject
import yos.music.player.data.objects.NowPlayingLyricState
import java.util.LinkedHashSet
import kotlin.math.abs

internal fun effectiveLyricTimingOffsetMs(): Int =
    SettingsLibrary.LyricTimingOffsetMs.coerceIn(MIN_LYRIC_TIMING_OFFSET_MS, MAX_LYRIC_TIMING_OFFSET_MS)

/**
 * 内嵌歌词与 sidecar 文件的统一胜者选择：LyricPreferEmbedded 决定优先级，
 * 本地 LocalLyricsLoader 与远程 applyTags 共用同一语义，只提交胜者避免二次替换。
 */
internal fun chooseLyricCandidate(
    embedded: String?,
    sidecar: String?
): Pair<String, LyricSource>? = chooseLyricCandidate(embedded, sidecar, SettingsLibrary.LyricPreferEmbedded)

internal fun chooseLyricCandidate(
    embedded: String?,
    sidecar: String?,
    preferEmbedded: Boolean
): Pair<String, LyricSource>? {
    val e = embedded?.takeIf(String::isNotBlank)
    val s = sidecar?.takeIf(String::isNotBlank)
    return if (preferEmbedded) {
        e?.let { it to LyricSource.EMBEDDED_TAG } ?: s?.let { it to LyricSource.SIDECAR_FILE }
    } else {
        s?.let { it to LyricSource.SIDECAR_FILE } ?: e?.let { it to LyricSource.EMBEDDED_TAG }
    }
}

/** Exact identity used by the APK to suppress duplicate parse/publish cycles. */
data class LyricDocumentIdentity(
    val mediaUri: String?,
    val mediaDurationMs: Long,
    val content: String?
)

data class LyricLineResolution(
    val anchorIndex: Int,
    val activeIndexes: Set<Int>,
    val nextBoundaryMs: Long
) {
    val displayIndex: Int
        get() = activeIndexes.firstOrNull() ?: anchorIndex
}

/**
 * APK timeline resolver: cached-index walk for nearby positions, binary search for
 * distant seeks, and a bounded +/-8 scan for simultaneous/background vocal rows.
 */
class LyricLineResolver(entries: List<YosLyricLine>) {
    private val begins = LongArray(entries.size) { entries[it].startMs.toLong() }
    private val ends = LongArray(entries.size) { entries[it].endMs.toLong() }
    private val boundaries = (begins.asSequence() + ends.asSequence())
        .distinct()
        .sorted()
        .toList()
        .toLongArray()

    fun resolve(positionMs: Long, cachedIndex: Int): LyricLineResolution {
        if (begins.isEmpty()) return LyricLineResolution(-1, emptySet(), Long.MAX_VALUE)

        var anchor = cachedIndex
        if (anchor !in begins.indices || abs(begins[anchor] - positionMs) > CACHE_WALK_LIMIT_MS) {
            anchor = begins.binarySearch(positionMs).let { hit ->
                if (hit >= 0) {
                    var lastEqual = hit
                    while (lastEqual < begins.lastIndex && begins[lastEqual + 1] == positionMs) lastEqual++
                    lastEqual
                } else {
                    -hit - 2
                }
            }
        } else {
            while (anchor < begins.lastIndex && begins[anchor + 1] <= positionMs) anchor++
            while (anchor >= 0 && begins[anchor] > positionMs) anchor--
        }

        val active = LinkedHashSet<Int>()
        val scanStart = (anchor - ACTIVE_SCAN_RADIUS).coerceAtLeast(0)
        val scanEnd = (anchor + ACTIVE_SCAN_RADIUS).coerceAtMost(begins.lastIndex)
        if (scanStart <= scanEnd) {
            for (index in scanStart..scanEnd) {
                if (positionMs >= begins[index] && positionMs < ends[index]) active += index
            }
        }

        val resolvedAnchor = active.firstOrNull() ?: when {
            anchor < 0 -> -1
            positionMs >= ends[anchor] && anchor < begins.lastIndex -> anchor + 1
            else -> anchor
        }
        val boundaryIndex = boundaries.binarySearch(positionMs).let { if (it >= 0) it + 1 else -it - 1 }
        return LyricLineResolution(
            anchorIndex = resolvedAnchor,
            activeIndexes = active,
            nextBoundaryMs = boundaries.getOrNull(boundaryIndex) ?: Long.MAX_VALUE
        )
    }

    private companion object {
        const val CACHE_WALK_LIMIT_MS = 15_000L
        const val ACTIVE_SCAN_RADIUS = 8
    }
}

enum class LyricSource(val basePriority: Int) {
    MEMORY_CACHE(0),
    REMOTE_CACHE(10),
    REMOTE_SCAN(20),
    TRACK_METADATA(30),
    SIDECAR_FILE(40),
    EMBEDDED_TAG(40)
}

/** Single owner of lyric document selection, parsing, caching and timeline state. */
object LyricsController {
    private const val CACHE_LIMIT = 50
    private const val RESOLUTION_REUSE_WINDOW_MS = 1_000L

    private data class CachedDocument(
        val identity: LyricDocumentIdentity,
        val entries: List<YosLyricLine>,
        val source: LyricSource
    )

    private val cache = object : LinkedHashMap<String, CachedDocument>(CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDocument>?): Boolean =
            size > CACHE_LIMIT
    }
    private val lock = Any()

    @Volatile private var activeTrackKey: String? = null
    @Volatile private var activeDurationMs: Long = 0L
    @Volatile private var activeDocument: CachedDocument? = null
    @Volatile private var resolver: LyricLineResolver? = null
    @Volatile private var generation: Int = 0

    fun activateTrack(trackKey: String?, currentPositionMs: Int = 0, durationMs: Long = 0L) {
        val changed = trackKey != activeTrackKey || (durationMs > 0 && durationMs != activeDurationMs)
        activeTrackKey = trackKey
        if (durationMs > 0) activeDurationMs = durationMs

        val cached = synchronized(lock) { trackKey?.let(cache::get) }
        activeDocument = cached
        resolver = cached?.entries?.let(::LyricLineResolver)
        if (changed) generation++
        publish(cached, currentPositionMs.toLong(), loading = cached == null && trackKey != null)
    }

    fun isCached(trackKey: String?): Boolean =
        trackKey != null && synchronized(lock) { cache.containsKey(trackKey) }

    /**
     * Warm the LRU for an upcoming track without touching the active document.
     * A later activateTrack() for the same key then renders lyrics from memory
     * instead of waiting for file reads and tag extraction.
     */
    fun cacheRawLyrics(
        trackKey: String,
        rawLyrics: String,
        source: LyricSource,
        durationMs: Long,
        translationText: String? = null
    ): Boolean {
        val content = rawLyrics.takeIf { it.isNotBlank() } ?: return false
        if (isCached(trackKey)) return true
        val parser = YosLrcFactory()
        val parsed = parseLyrics(parser, content, translationText)
        if (parsed.isEmpty()) return false
        val normalized = parsed.sortedWith(compareBy<YosLyricLine> { it.startMs }.thenBy { it.endMs })
        val document = CachedDocument(
            LyricDocumentIdentity(trackKey, durationMs.coerceAtLeast(0L), identityContent(content, translationText)),
            normalized,
            source
        )
        synchronized(lock) {
            // Another producer may have filled the slot while we were parsing.
            if (cache.containsKey(trackKey)) return true
            cache[trackKey] = document
        }
        return true
    }

    fun submitRawLyrics(
        trackKey: String?,
        rawLyrics: String?,
        currentPositionMs: Int,
        source: LyricSource,
        durationMs: Long = activeDurationMs,
        translationText: String? = null
    ): Boolean {
        val content = rawLyrics?.takeIf { it.isNotBlank() } ?: return false
        if (trackKey == null || trackKey != activeTrackKey) return false

        val identity = LyricDocumentIdentity(trackKey, durationMs.coerceAtLeast(0L), identityContent(content, translationText))
        if (identity == activeDocument?.identity) return true
        if (!canReplaceActiveSource(source)) return false

        val parser = YosLrcFactory()
        val parsed = parseLyrics(parser, content, translationText)
        return submitDocument(identity, parsed, currentPositionMs, source)
    }

    /** 统一解析入口：可选的独立翻译文本在主文档解析后合并 */
    private fun parseLyrics(parser: YosLrcFactory, content: String, translationText: String?): List<YosLyricLine> {
        val parsed = parser.formatLrcEntries(content).ifEmpty { parser.formatPlainLyricEntries(content) }
        if (translationText.isNullOrBlank()) return parsed
        return parser.mergeTranslation(parsed, translationText).ifEmpty { parsed }
    }

    /** 翻译文本参与 identity，避免"同原文不同翻译"被去重跳过 */
    private fun identityContent(content: String, translationText: String?): String =
        if (translationText.isNullOrBlank()) content else "$content\n\u2425translation\u2425\n$translationText"

    fun submitParsedLyrics(
        trackKey: String?,
        entries: List<YosLyricLine>,
        currentPositionMs: Int,
        source: LyricSource
    ): Boolean {
        if (trackKey == null || entries.isEmpty() || trackKey != activeTrackKey) return false
        val syntheticContent = entries.joinToString("\u0000") { line ->
            buildString {
                append(line.agent).append('|').append(line.startMs).append('|').append(line.endMs)
                append('|').append(line.text).append('|').append(line.translation)
            }
        }
        return submitDocument(
            LyricDocumentIdentity(trackKey, activeDurationMs, syntheticContent),
            entries,
            currentPositionMs,
            source
        )
    }

    fun updatePosition(trackKey: String?, currentPositionMs: Float) {
        if (trackKey == null || trackKey != activeTrackKey) return
        val state = MediaViewModelObject.nowPlayingLyrics.value
        if (state.trackKey != trackKey || state.entries.isEmpty()) return

        val effective = currentPositionMs.toLong() + effectiveLyricTimingOffsetMs()
        if (
            effective >= state.effectivePositionMs &&
            effective < state.nextBoundaryMs &&
            abs(effective - state.effectivePositionMs) <= RESOLUTION_REUSE_WINDOW_MS &&
            state.currentLineIndex in state.entries.indices
        ) {
            if (effective != state.effectivePositionMs) {
                MediaViewModelObject.nowPlayingLyrics.value = state.copy(effectivePositionMs = effective)
            }
            return
        }

        val resolution = resolver?.resolve(effective, state.currentLineIndex)
            ?: LyricLineResolution(-1, emptySet(), Long.MAX_VALUE)
        MediaViewModelObject.nowPlayingLyrics.value = state.copy(
            currentLineIndex = resolution.displayIndex,
            activeLineIndexes = resolution.activeIndexes,
            effectivePositionMs = effective,
            nextBoundaryMs = resolution.nextBoundaryMs
        )
    }

    fun currentState(): NowPlayingLyricState = MediaViewModelObject.nowPlayingLyrics.value

    fun clearCache() {
        synchronized(lock) { cache.clear() }
        activeDocument = null
        resolver = null
        generation++
        publish(null, 0L, loading = activeTrackKey != null)
    }

    private fun submitDocument(
        identity: LyricDocumentIdentity,
        entries: List<YosLyricLine>,
        currentPositionMs: Int,
        source: LyricSource
    ): Boolean {
        if (identity.mediaUri != activeTrackKey || entries.isEmpty() || !canReplaceActiveSource(source)) return false
        val normalized = entries.sortedWith(compareBy<YosLyricLine> { it.startMs }.thenBy { it.endMs })
        val document = CachedDocument(identity, normalized, source)
        activeDurationMs = identity.mediaDurationMs
        activeDocument = document
        resolver = LyricLineResolver(normalized)
        generation++
        synchronized(lock) { cache[identity.mediaUri!!] = document }
        publish(document, currentPositionMs.toLong(), loading = false)
        return true
    }

    private fun publish(document: CachedDocument?, positionMs: Long, loading: Boolean) {
        val entries = document?.entries.orEmpty()
        val effective = positionMs + effectiveLyricTimingOffsetMs()
        val resolution = resolver?.resolve(effective, -1)
            ?: LyricLineResolution(-1, emptySet(), Long.MAX_VALUE)
        MediaViewModelObject.nowPlayingLyrics.value = NowPlayingLyricState(
            trackKey = activeTrackKey,
            entries = entries,
            currentLineIndex = resolution.displayIndex,
            activeLineIndexes = resolution.activeIndexes,
            effectivePositionMs = effective,
            nextBoundaryMs = resolution.nextBoundaryMs,
            documentIdentity = document?.identity,
            source = document?.source,
            generation = generation,
            isLoading = loading
        )
    }

    private fun canReplaceActiveSource(candidate: LyricSource): Boolean {
        val current = activeDocument?.source ?: return true
        if (candidate == LyricSource.MEMORY_CACHE) return activeDocument == null
        return sourcePriority(candidate) >= sourcePriority(current)
    }

    private fun sourcePriority(source: LyricSource): Int = when (source) {
        LyricSource.EMBEDDED_TAG -> if (SettingsLibrary.LyricPreferEmbedded) 60 else 50
        LyricSource.SIDECAR_FILE -> if (SettingsLibrary.LyricPreferEmbedded) 50 else 60
        LyricSource.TRACK_METADATA -> 55
        else -> source.basePriority
    }
}

internal const val MIN_LYRIC_TIMING_OFFSET_MS = -1_000
internal const val MAX_LYRIC_TIMING_OFFSET_MS = 1_000
