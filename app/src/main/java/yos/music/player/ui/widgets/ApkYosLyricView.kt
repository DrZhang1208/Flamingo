package yos.music.player.ui.widgets

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import yos.music.player.code.MediaController
import yos.music.player.code.utils.lrc.LyricsController
import yos.music.player.code.utils.lrc.effectiveLyricTimingOffsetMs
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.code.utils.lrc.YosUIConfig
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.objects.NowPlayingLyricState
import kotlin.math.abs

/** Native lyrics page. Parsing belongs to the controller; presentation owns its clock and motion. */
@Composable
fun YosLyricView(
    lyricStateLambda: () -> NowPlayingLyricState,
    liveTimeLambda: () -> Int,
    mediaEvent: YosMediaEvent,
    translationLambda: () -> Boolean = { true },
    blurLambda: () -> Boolean = { false },
    uiConfig: YosUIConfig = YosUIConfig(),
    weightLambda: () -> Boolean,
    modifier: Modifier,
    userScrollEnabled: Boolean = true,
    renderActive: Boolean = true,
    onLineLongPress: (Int) -> Unit = {},
    onBackClick: () -> Unit
) {
    val lyricState = lyricStateLambda()
    val entries = lyricState.entries
    val document = remember(entries) { nativeLyricDocument(entries) }
    val documentLines = remember(document) { document.map { it.line } }
    val followTargets = remember(document) {
        document.withIndex().groupBy { it.value.sourceIndex }.mapValues { it.value.first().index }
    }
    val context = LocalContext.current
    val mainColor = Color(uiConfig.mainTextBasicColor)
    val subColor = Color(uiConfig.subTextBasicColor)

    if (entries.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxHeight(if (weightLambda()) 0.56f else 1f)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onBackClick
                )
        ) {
            Text(uiConfig.noLrcText, fontSize = 18.sp, color = mainColor)
        }
        return
    }

    val columnState = remember(lyricState.trackKey, entries) { WavyLyricsColumnState() }
    val framePosition = remember(lyricState.trackKey) {
        mutableFloatStateOf(lyricState.effectivePositionMs.toFloat())
    }
    val clock = remember(lyricState.trackKey, lyricState.generation) {
        NativeLyricClock(lyricState.effectivePositionMs.toFloat())
    }
    val seekToLyric: (Float) -> Unit = { startMs ->
        val target = startMs.toInt()
        columnState.resumeFollowing()
        val now = SystemClock.uptimeMillis()
        clock.seek((target + effectiveLyricTimingOffsetMs()).toFloat(), now)
        framePosition.floatValue = clock.positionMs
        LyricsController.updatePosition(lyricState.trackKey, startMs)
        mediaEvent.onSeek(target)
    }
    LaunchedEffect(lyricState.trackKey, lyricState.generation, renderActive) {
        if (!renderActive) return@LaunchedEffect
        clock.restartFrames()
        var nextSampleAt = 0L
        while (isActive) {
            withFrameMillis { frameTime ->
                val controller = MediaController.mediaControl
                // Player position is sampled at 12.5 Hz. The frame clock interpolates
                // presentation time; it does not query the player 120 times per second.
                val report = if (frameTime >= nextSampleAt) {
                    nextSampleAt = frameTime + 80L
                    (controller?.currentPosition?.toFloat() ?: liveTimeLambda().toFloat()) +
                        effectiveLyricTimingOffsetMs()
                } else null
                framePosition.floatValue = clock.frame(frameTime, controller?.isPlaying == true,
                    controller?.playbackParameters?.speed ?: 1f, report)
            }
        }
    }

    // A document owns its measurements and scroll position. Font/layout changes
    // update those measurements in place instead of recreating every animated row.
    LyricPreviewResumeEffect(columnState)
    // Follow the same frame clock and preroll as the highlight, rather than
    // waiting for the service's later current-line notification.
    val followIndex by remember(entries, framePosition) {
        derivedStateOf { lyricVisualFollowIndex(entries, framePosition.floatValue) }
    }
    val fontFamily = rememberLyricFontFamily()
    val showTranslation = translationLambda()
    LaunchedEffect(
        columnState,
        followIndex,
        columnState.viewportHeightPx,
        columnState.isUserScrolling,
        columnState.isPreviewing,
        showTranslation,
        fontFamily,
        SettingsLibrary.LyricFontSize,
        SettingsLibrary.TranslationFontSize,
        SettingsLibrary.LyricFontWeight,
        SettingsLibrary.LyricFontScale,
        SettingsLibrary.LyricLineBalance
    ) {
        if (followIndex < 0 || columnState.viewportHeightPx == 0 ||
            columnState.isUserScrolling || columnState.isPreviewing) return@LaunchedEffect
        // One transition, directly to the requested line. Measurement corrections
        // are handled by the column, without delayed jumps or a next-line detour.
        columnState.jumpTo(
            followTargets[followIndex] ?: return@LaunchedEffect,
            animateRows = columnState.waveAnchorIndex >= 0
        )
    }

    val measurer = rememberTextMeasurer(cacheSize = 32)
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    key(columnState) {
    WavyLyricsColumn(
        itemCount = document.size,
        state = columnState,
        userScrollEnabled = userScrollEnabled,
        itemKey = { index ->
            val line = document[index].line
            "${lyricState.trackKey}|$index|${line.agent}|${line.startMs}|${line.endMs}|${line.text}"
        },
        rowSpringParams = { index -> wavySpringParams(documentLines, index) },
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onBackClick
            )
    ) { index ->
        val row = document.getOrNull(index) ?: return@WavyLyricsColumn
        val line = row.line
        val nextStart = entries.getOrNull(row.sourceIndex + 1)?.startMs
        val active = row.sourceIndex in lyricState.activeLineIndexes || row.sourceIndex == followIndex
        val lineState = when {
            active -> LineState.Active
            row.sourceIndex < followIndex -> LineState.Past
            else -> LineState.Future
        }
        val displayEnd = maxOf(
            line.endMs + lyricBoundaryExtraMs(line.tokens),
            nextStart ?: (line.startMs + LAST_LINE_DURATION_MS),
            line.startMs + 1f
        )
        val blur = if (!blurLambda() || !supportsBlur || active) 0f
        else (abs(row.sourceIndex - followIndex) * 2.5f).coerceAtMost(8f)

        key(lyricState.trackKey, index) {
            Column {
                RenderNativeLyricRow(
                    line = line,
                    nextStart = nextStart,
                    lineState = lineState,
                    active = active,
                    focused = index == followTargets[followIndex],
                    staticText = row.staticText,
                    displayEnd = displayEnd,
                    blur = blur,
                    liveTime = { framePosition.floatValue },
                    showTranslation = showTranslation,
                    mainColor = mainColor,
                    subColor = subColor,
                    measurer = measurer,
                    fontFamily = fontFamily,
                    onClick = {
                        Vibrator.doubleClick(context)
                        seekToLyric(line.startMs)
                    },
                    onLongClick = {
                        Vibrator.longClick(context)
                        onLineLongPress(row.sourceIndex)
                    }
                )
                line.otherLrcEntries.forEach { background ->
                    RenderNativeLyricRow(
                        line = background,
                        nextStart = nextStart,
                        lineState = lineState,
                        active = active,
                        focused = false,
                        displayEnd = maxOf(background.endMs, displayEnd),
                        blur = blur,
                        liveTime = { framePosition.floatValue },
                        showTranslation = showTranslation,
                        mainColor = mainColor,
                        subColor = subColor,
                        measurer = measurer,
                        fontFamily = fontFamily,
                        onClick = { seekToLyric(background.startMs) },
                        onLongClick = { onLineLongPress(row.sourceIndex) }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun RenderNativeLyricRow(
    line: YosLyricLine,
    nextStart: Float?,
    lineState: LineState,
    active: Boolean,
    focused: Boolean,
    staticText: Boolean = false,
    displayEnd: Float,
    blur: Float,
    liveTime: () -> Float,
    showTranslation: Boolean,
    mainColor: Color,
    subColor: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
    fontFamily: FontFamily?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val committedBlur by animateFloatAsState(
        targetValue = blur,
        animationSpec = TweenSpec(durationMillis = 180),
        label = "LyricRowBlur"
    )
    val displayTokens = remember(line, nextStart, staticText, SettingsLibrary.SmartWordByWordLyric) {
        if (staticText) listOf(yos.music.player.code.utils.lrc.YosLyricToken(line.startMs, line.startMs, line.text))
        else createDisplayTokens(line, nextStart)
    }
    // Stationary past/future rows must not repaint for every playback tick.
    // Keep a conservative margin for preroll and overlapping uplift at each edge.
    val renderWindow = remember(line, displayTokens) {
        (minOf(line.startMs, displayTokens.minOfOrNull { it.startMs } ?: line.startMs) - 1000f) to
            (maxOf(line.endMs, displayTokens.maxOfOrNull { it.endMs } ?: line.endMs) + 1000f)
    }
    val liveTimeRef = androidx.compose.runtime.rememberUpdatedState(liveTime)
    val rowTime = remember(renderWindow) {
        derivedStateOf { liveTimeRef.value().coerceIn(renderWindow.first, renderWindow.second) }
    }
    NativeLyricRow(
        line = line, tokens = displayTokens, staticText = staticText,
        active = active, focused = focused, past = lineState == LineState.Past, blur = committedBlur, nextStart = nextStart,
        time = { rowTime.value }, showTranslation = showTranslation,
        mainColor = mainColor, subColor = subColor, measurer = measurer,
        fontFamily = fontFamily, onClick = onClick, onLongClick = onLongClick
    )
}

private const val LAST_LINE_DURATION_MS = 4_000f
internal fun shouldRenderStaticLyric(line: YosLyricLine, documentSize: Int): Boolean {
    if (line.isCountdown) return false
    val timedWords = line.tokens.count { it.text.isNotBlank() && it.endMs > it.startMs }
    return timedWords <= 1 && (documentSize == 1 || line.text.length > 160)
}
internal fun lyricVisualFollowIndex(entries: List<YosLyricLine>, positionMs: Float): Int =
    entries.indexOfLast { line ->
        positionMs >= line.startMs - if (line.isCountdown) 0f else LYRIC_LINE_PREROLL_MS
    }
private const val SEEK_BACKWARD_JITTER_MS = 500f
internal fun reconcileLyricClock(previous: Float, reported: Float): Float =
    if (abs(reported - previous) > SEEK_BACKWARD_JITTER_MS) reported else maxOf(previous, reported)

internal const val LYRIC_PREVIEW_IDLE_MS = 4_000L

@Composable
internal fun LyricPreviewResumeEffect(state: WavyLyricsColumnState) {
    LaunchedEffect(state, state.previewInteractionGeneration, state.isPreviewing) {
        if (state.isPreviewing && !state.isUserScrolling) {
            delay(LYRIC_PREVIEW_IDLE_MS)
            state.resumeFollowing()
        }
    }
}
