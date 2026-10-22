package yos.music.player.ui.widgets

import android.os.Looper
import android.os.MessageQueue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.objects.NowPlayingLyricState
import kotlin.coroutines.resume
import kotlin.math.roundToInt

internal data class NativeLyricLayoutKey(
    val tokens: List<YosLyricToken>,
    val style: TextStyle,
    val maxWidth: Int,
    val density: Float,
    val fontScale: Float,
    val direction: LayoutDirection,
    val staticText: Boolean
)

internal class PreparedLyricDocument(entries: List<YosLyricLine>) {
    val rows = nativeLyricDocument(entries)
    val lines = rows.map { it.line }
    val followTargets = rows.withIndex().groupBy { it.value.sourceIndex }.mapValues { it.value.first().index }
    val seekTimeline = LyricSeekTimeline(entries, followTargets)
}

internal class NativeLyricRenderCache(
    private val maxEntries: Int = 64,
    private val maxBytes: Long = 24L * 1024 * 1024
) {
    var document: PreparedLyricDocument? = null
    private val paragraphs = LinkedHashMap<NativeLyricLayoutKey, NativeLyricParagraph>(8, 0.75f, true)

    fun contains(key: NativeLyricLayoutKey): Boolean = paragraphs.containsKey(key)

    /** 借用而不移除：行释放后段落仍留在仓内，重开页面或行回到视口时秒命中。 */
    fun borrow(key: NativeLyricLayoutKey): NativeLyricParagraph? = paragraphs[key]

    fun put(key: NativeLyricLayoutKey, paragraph: NativeLyricParagraph) {
        if (paragraphs.containsKey(key)) return
        paragraphs[key] = paragraph
        var retained = paragraphs.values.sumOf { it.surface.retainedByteCount }
        val iterator = paragraphs.entries.iterator()
        while ((paragraphs.size > maxEntries || retained > maxBytes) && iterator.hasNext()) {
            retained -= iterator.next().value.surface.retainedByteCount
            iterator.remove()
        }
    }
}

internal val LocalNativeLyricRenderCache = staticCompositionLocalOf<NativeLyricRenderCache?> { null }

/**
 * 进程级按曲目缓存仓：播放页关闭后保留已光栅化的段落与解析文档，重开同一
 * 曲目时视口行直接借用成品段落，免去主线程重建排版与笔画光栅化。歌词重新
 * 解析会自增 generation，产生新的仓键，旧仓由最近使用逐出。
 */
internal object NativeLyricWarmStore {
    private const val MAX_TRACKS = 2
    private val tracks = LinkedHashMap<String, NativeLyricRenderCache>(4, 0.75f, true)

    @Synchronized
    fun cacheFor(trackKey: String): NativeLyricRenderCache {
        val cache = tracks.getOrPut(trackKey) { NativeLyricRenderCache() }
        val iterator = tracks.entries.iterator()
        while (tracks.size > MAX_TRACKS && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key != trackKey) iterator.remove()
        }
        return cache
    }
}

/**
 * Live handle to the paragraph warmer. Fast lyric scrolls compose rows whose
 * first draw would otherwise lazily rasterize word-by-word ink on the main
 * thread; the handle lets the lyric view warm rows around the scroll position
 * during message-queue idle time, replacing any previous request.
 */
internal val LocalNativeLyricWarmup = staticCompositionLocalOf<androidx.compose.runtime.MutableState<NativeLyricWarmupHandle?>?> { null }

/** Warm by increasing distance from the anchor, alternating sides. */
internal fun nativeLyricWarmupOrder(anchorRow: Int, radius: Int, count: Int): List<Int> {
    if (count == 0) return emptyList()
    val anchor = anchorRow.coerceIn(0, count - 1)
    return buildList {
        for (distance in 0..radius) {
            if (distance > 0) (anchor + distance).takeIf { it in 0 until count }?.let(::add)
            (anchor - distance).takeIf { it in 0 until count }?.let(::add)
        }
    }
}

internal class NativeLyricWarmupHandle internal constructor(
    private val scope: CoroutineScope,
    private val document: PreparedLyricDocument,
    private val cache: NativeLyricRenderCache,
    private val measurer: TextMeasurer,
    private val baseStyle: TextStyle,
    private val density: Density,
    private val direction: LayoutDirection,
    private val widthPx: Int,
    private val wordByWord: Boolean
) {
    private var job: Job? = null

    /** Stop the in-flight warm request, e.g. while the user is actively scrolling. */
    fun cancelRequests() {
        job?.cancel()
        job = null
    }

    /** Warm rows by distance around [anchor], replacing an in-flight request. */
    fun requestAround(anchor: Int, radius: Int) {
        if (!scope.isActive) return
        job?.cancel()
        job = scope.launch {
            val rows = document.rows
            if (rows.isEmpty()) return@launch
            val anchorRow = rows.indexOfFirst { it.sourceIndex == anchor.coerceAtLeast(0) }
                .takeIf { it >= 0 } ?: anchor.coerceIn(0, rows.lastIndex)
            for (index in nativeLyricWarmupOrder(anchorRow, radius, rows.size)) warmRow(rows[index])
        }
    }

    private suspend fun warmRow(row: NativeLyricDocumentRow) {
        for (line in listOf(row.line) + row.line.otherLrcEntries) {
            if (line.isCountdown) continue
            awaitLyricIdle()
            currentCoroutineContext().ensureActive()
            val staticText = row.staticText && line === row.line
            val tokens = if (staticText) listOf(YosLyricToken(line.startMs, line.startMs, line.text))
                else nativeDisplayTokens(line, wordByWord)
            val side = line.otherSide || line.agent == "bg"
            val rowStyle = baseStyle.copy(textAlign = if (side) TextAlign.End else TextAlign.Start)
            val independent = if (line.hasWordTiming && !staticText && wordByWord)
                nativeIndependentGlyphStyle(rowStyle) else rowStyle
            val inset = with(density) { 29.dp.toPx() }
            val trailing = if (staticText) inset else nativeLyricFocusTrailingInset(widthPx.toFloat(), inset)
            val textWidth = (widthPx - inset.roundToInt() - trailing.roundToInt()).coerceAtLeast(1)
            val key = NativeLyricLayoutKey(tokens, independent, textWidth,
                density.density, density.fontScale, direction, staticText)
            if (cache.contains(key)) continue
            val layout = measurer.measure(AnnotatedString(tokens.joinToString("") { it.text }),
                independent, constraints = Constraints(maxWidth = textWidth),
                layoutDirection = direction, skipCache = true)
            val paragraph = withContext(Dispatchers.Default) {
                val result = if (staticText) NativeLyricParagraph(layout, emptyList())
                    else nativeLyricParagraph(layout, tokens)
                if (!staticText) result.surface.prepare(result.glyphs, line.hasWordTiming && wordByWord)
                result
            }
            cache.put(key, paragraph)
        }
    }
}

private suspend fun awaitLyricIdle() = suspendCancellableCoroutine { continuation ->
    val queue = Looper.myQueue()
    val handler = MessageQueue.IdleHandler {
        if (continuation.isActive) continuation.resume(Unit)
        false
    }
    queue.addIdleHandler(handler)
    continuation.invokeOnCancellation { queue.removeIdleHandler(handler) }
}

@Composable
internal fun PrepareNativeLyrics(
    cache: NativeLyricRenderCache,
    entries: List<YosLyricLine>,
    lyricState: () -> NowPlayingLyricState,
    widthPx: Int,
    allowed: () -> Boolean,
    warmupState: androidx.compose.runtime.MutableState<NativeLyricWarmupHandle?>? = null
) {
    val mayPrepare by remember(allowed) { derivedStateOf(structuralEqualityPolicy()) { allowed() } }
    val owner = LocalLifecycleOwner.current
    val source = rememberUpdatedState(lyricState)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val fontFamily = rememberLyricFontFamily()
    val style = mainTextStyle(fontFamily)
    val wordByWord = SettingsLibrary.EnableWordByWordLyric
    LaunchedEffect(cache, entries, widthPx, mayPrepare, style, density, direction, wordByWord, owner, warmupState) {
        if (!mayPrepare || entries.isEmpty() || widthPx <= 0) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            awaitLyricIdle()
            val document = cache.document ?: withContext(Dispatchers.Default) {
                PreparedLyricDocument(entries)
            }.let { prepared ->
                cache.document ?: prepared.also { cache.document = it }
            }
            // Publish the warmer only while resumed; exiting the block cancels
            // its scope so in-flight idle work cannot outlive the page.
            coroutineScope {
                val handle = NativeLyricWarmupHandle(
                    CoroutineScope(coroutineContext + kotlinx.coroutines.SupervisorJob()),
                    document, cache, measurer, style, density, direction, widthPx, wordByWord
                )
                warmupState?.value = handle
                try {
                    snapshotFlow {
                        source.value().currentLineIndex
                    }.collectLatest { sourceIndex ->
                        handle.requestAround(sourceIndex, radius = 2)
                    }
                } finally {
                    warmupState?.value = null
                }
            }
        }
    }
}
