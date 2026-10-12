package yos.music.player.ui.widgets

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
    interactionEnabled: Boolean = true,
    renderActive: Boolean = true,
    onLineLongPress: (Int) -> Unit = {},
    onBackClick: () -> Unit
) {
    val canInteract = interactionEnabled && renderActive
    val interactionAllowed = androidx.compose.runtime.rememberUpdatedState(canInteract)
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
                    enabled = canInteract,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onBackClick
                )
        ) {
            Text(uiConfig.noLrcText, fontSize = 18.sp, color = mainColor)
        }
        return
    }

    val columnState = remember(lyricState.trackKey, entries) {
        WavyLyricsColumnState(document.indices.filter { document[it].line.isCountdown }.toSet())
    }
    val pressedRows = remember(columnState) { androidx.compose.runtime.mutableStateMapOf<YosLyricLine, Boolean>() }
    val holdingLyric = renderActive && pressedRows.isNotEmpty()
    LaunchedEffect(renderActive, pressedRows) {
        if (!renderActive) pressedRows.clear()
    }
    val framePosition = remember(lyricState.trackKey) {
        mutableFloatStateOf(lyricState.effectivePositionMs.toFloat())
    }
    val clock = remember(lyricState.trackKey, lyricState.generation) {
        NativeLyricClock(lyricState.effectivePositionMs.toFloat())
    }
    val seekRevision = remember(lyricState.trackKey) { mutableIntStateOf(0) }
    val seekToLyric: (Float) -> Unit = seek@{ startMs ->
        if (!interactionAllowed.value) return@seek
        val target = startMs.toInt()
        columnState.resumeFollowing()
        val now = SystemClock.uptimeMillis()
        seekRevision.intValue++
        // Capture the travel while an active countdown still occupies its slot.
        // Updating playback first can collapse that slot during measurement,
        // leaving the later follow effect with zero distance to animate.
        val presentationTime = (target + effectiveLyricTimingOffsetMs()).toFloat()
        followTargets[lyricVisualFollowIndex(entries, presentationTime)]?.let {
            columnState.jumpTo(it, animateRows = columnState.waveAnchorIndex >= 0)
        }
        clock.seek(presentationTime, now)
        framePosition.floatValue = clock.positionMs
        LyricsController.updatePosition(lyricState.trackKey, startMs)
        mediaEvent.onSeek(target)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, lyricState.trackKey, lyricState.generation, renderActive) {
        if (!renderActive) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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
                    val position = clock.frame(frameTime, controller?.isPlaying == true,
                        controller?.playbackParameters?.speed ?: 1f, report)
                    if (pressedRows.isEmpty() && abs(position - framePosition.floatValue) > SEEK_BACKWARD_JITTER_MS) {
                        followTargets[lyricVisualFollowIndex(entries, position)]?.let {
                            columnState.jumpTo(it, animateRows = columnState.waveAnchorIndex >= 0)
                        }
                    }
                    framePosition.floatValue = position
                }
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
        renderActive,
        followIndex,
        columnState.viewportHeightPx,
        columnState.isUserScrolling,
        columnState.isPreviewing,
        holdingLyric,
        showTranslation,
        fontFamily,
        SettingsLibrary.LyricFontSize,
        SettingsLibrary.TranslationFontSize,
        SettingsLibrary.LyricFontWeight,
        SettingsLibrary.LyricFontScale,
        SettingsLibrary.LyricLineBalance
    ) {
        if (!renderActive || columnState.viewportHeightPx == 0 ||
            columnState.isUserScrolling || columnState.isPreviewing || holdingLyric) return@LaunchedEffect
        // One transition, directly to the requested line. Measurement corrections
        // are handled by the column, without delayed jumps or a next-line detour.
        columnState.followTo(
            followTargets[followIndex.coerceAtLeast(0)] ?: return@LaunchedEffect,
            animateRows = columnState.waveAnchorIndex >= 0
        )
    }

    // A new document arrives gently; playback frames never restart this entrance.
    val entrance = remember(lyricState.trackKey, entries) { Animatable(0f) }
    LaunchedEffect(entrance, renderActive) {
        if (renderActive && entrance.value < 1f) entrance.animateTo(1f, tween(210))
    }
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    key(columnState) {
    WavyLyricsColumn(
        itemCount = document.size,
        state = columnState,
        userScrollEnabled = userScrollEnabled && canInteract,
        renderActive = renderActive,
        itemKey = { index ->
            val line = document[index].line
            "${lyricState.trackKey}|$index|${line.agent}|${line.startMs}|${line.endMs}|${line.text}"
        },
        rowSpringParams = { index -> wavySpringParams(documentLines, index) },
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * 8.dp.toPx()
            }
            .clickable(
                    enabled = canInteract,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onBackClick
            )
    ) { index ->
        val row = document.getOrNull(index) ?: return@WavyLyricsColumn
        val line = row.line
        val nextLine = entries.getOrNull(row.sourceIndex + 1)
        val nextStart = nextLine?.startMs
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
        val browsing = columnState.isUserScrolling || columnState.isPreviewing || holdingLyric
        val blur = if (browsing || !blurLambda() || !supportsBlur || active) 0f
        else (abs(row.sourceIndex - followIndex) * 2.5f).coerceAtMost(8f)

        key(lyricState.trackKey, index) {
            Column {
                RenderNativeLyricRow(
                    line = line,
                    nextStart = nextStart,
                    nextIsCountdown = nextLine?.isCountdown == true,
                    lineState = lineState,
                    active = active,
                    seekRevision = seekRevision.intValue,
                    focused = index == followTargets[followIndex],
                    staticText = row.staticText,
                    displayEnd = displayEnd,
                    blur = blur,
                    browsing = browsing,
                    liveTime = { framePosition.floatValue },
                    showTranslation = showTranslation,
                    mainColor = mainColor,
                    subColor = subColor,
                    measurer = measurer,
                    fontFamily = fontFamily,
                    interactionEnabled = canInteract,
                    onPressChanged = { pressed ->
                        if (pressed) pressedRows[line] = true else pressedRows.remove(line)
                    },
                    onClick = {
                        if (!interactionAllowed.value) return@RenderNativeLyricRow
                        Vibrator.doubleClick(context)
                        seekToLyric(line.startMs)
                    },
                    onLongClick = {
                        if (!interactionAllowed.value) return@RenderNativeLyricRow
                        Vibrator.longClick(context)
                        onLineLongPress(row.sourceIndex)
                    }
                )
                line.otherLrcEntries.forEach { background ->
                    RenderNativeLyricRow(
                        line = background,
                        nextStart = nextStart,
                        nextIsCountdown = nextLine?.isCountdown == true,
                        lineState = lineState,
                        active = active,
                        focused = false,
                        seekRevision = seekRevision.intValue,
                        displayEnd = maxOf(background.endMs, displayEnd),
                        blur = blur,
                        browsing = browsing,
                        liveTime = { framePosition.floatValue },
                        showTranslation = showTranslation,
                        mainColor = mainColor,
                        subColor = subColor,
                        measurer = measurer,
                        fontFamily = fontFamily,
                        interactionEnabled = canInteract,
                        onPressChanged = { pressed ->
                            if (pressed) pressedRows[background] = true else pressedRows.remove(background)
                        },
                        onClick = { seekToLyric(background.startMs) },
                        onLongClick = { if (interactionAllowed.value) onLineLongPress(row.sourceIndex) }
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
    nextIsCountdown: Boolean,
    lineState: LineState,
    active: Boolean,
    focused: Boolean,
    seekRevision: Int,
    staticText: Boolean = false,
    displayEnd: Float,
    blur: Float,
    browsing: Boolean,
    liveTime: () -> Float,
    showTranslation: Boolean,
    mainColor: Color,
    subColor: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
    fontFamily: FontFamily?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    interactionEnabled: Boolean = true,
    onPressChanged: (Boolean) -> Unit = {}
) {
    val committedBlur by animateFloatAsState(
        targetValue = blur,
        animationSpec = TweenSpec(durationMillis = 180),
        label = "LyricRowBlur"
    )
    val displayTokens = remember(line, staticText, SettingsLibrary.EnableWordByWordLyric) {
        if (staticText) listOf(yos.music.player.code.utils.lrc.YosLyricToken(line.startMs, line.startMs, line.text))
        else nativeDisplayTokens(line, SettingsLibrary.EnableWordByWordLyric)
    }
    // Stationary past/future rows must not repaint for every playback tick.
    // Keep a conservative margin for preroll and overlapping uplift at each edge.
    val renderWindow = remember(line, displayTokens) {
        (minOf(line.startMs, displayTokens.minOfOrNull { it.startMs } ?: line.startMs) - 1000f) to
            (maxOf(line.endMs, displayTokens.maxOfOrNull { it.endMs } ?: line.endMs) + 1000f)
    }
    val lineHighlight = remember(line.startMs, nextStart, nextIsCountdown, line.hasWordTiming, SettingsLibrary.EnableWordByWordLyric) {
        if (!SettingsLibrary.EnableWordByWordLyric || !line.hasWordTiming) nativePlaybackLineHighlight(line.startMs, nextStart, nextIsCountdown) else null
    }
    val liveTimeRef = androidx.compose.runtime.rememberUpdatedState(liveTime)
    val rowTime = remember(renderWindow, lineHighlight) {
        derivedStateOf {
            val time = liveTimeRef.value()
            lineHighlight?.renderTime(time, renderWindow) ?: time.coerceIn(renderWindow.first, renderWindow.second)
        }
    }
    NativeLyricRow(
        line = line, tokens = displayTokens, staticText = staticText,
        active = active, focused = focused, past = lineState == LineState.Past, seekRevision = seekRevision, blur = if (browsing) 0f else committedBlur, nextStart = nextStart, nextIsCountdown = nextIsCountdown,
        time = { rowTime.value }, showTranslation = showTranslation,
        mainColor = mainColor, subColor = subColor, measurer = measurer,
        fontFamily = fontFamily, onClick = onClick, onLongClick = onLongClick, interactionEnabled = interactionEnabled,
        onPressChanged = onPressChanged
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
        positionMs >= nativeLyricVisualStartMs(line.startMs, line.isCountdown)
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
