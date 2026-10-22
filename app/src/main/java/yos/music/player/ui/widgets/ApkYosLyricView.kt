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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.collectLatest
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
    onBackClick: () -> Unit,
    seekPreviewState: LyricSeekPreviewState? = null
) {
    val canInteract = interactionEnabled && renderActive
    val interactionAllowed = androidx.compose.runtime.rememberUpdatedState(canInteract)
    val lyricStateSource = rememberUpdatedState(lyricStateLambda)
    val presentationState = remember {
        lyricPresentationState { lyricStateSource.value() }
    }
    val lyricState = presentationState.value
    val entries = lyricState.entries
    val renderCache = LocalNativeLyricRenderCache.current
    val preparedDocument = remember(entries, renderCache) {
        renderCache?.document ?: PreparedLyricDocument(entries).also { renderCache?.document = it }
    }
    val document = preparedDocument.rows
    val documentLines = preparedDocument.lines
    val followTargets = preparedDocument.followTargets
    val seekTimeline = preparedDocument.seekTimeline
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
        mutableFloatStateOf(Snapshot.withoutReadObservation {
            lyricStateSource.value().effectivePositionMs.toFloat()
        })
    }
    val visualPosition = remember(lyricState.trackKey) {
        mutableFloatStateOf(framePosition.floatValue)
    }
    val clock = remember(lyricState.trackKey, lyricState.generation) {
        NativeLyricClock(Snapshot.withoutReadObservation {
            lyricStateSource.value().effectivePositionMs.toFloat()
        })
    }
    val seekRevision = remember(lyricState.trackKey) { mutableIntStateOf(0) }
    val previewStateRef = androidx.compose.runtime.rememberUpdatedState(seekPreviewState)
    val isSeekPreview by remember(seekPreviewState) {
        derivedStateOf { seekPreviewState?.positionMs != null }
    }
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
        visualPosition.floatValue = clock.visualPositionMs
        LyricsController.updatePosition(lyricState.trackKey, startMs)
        mediaEvent.onSeek(target)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, lyricState.trackKey, lyricState.generation, columnState, renderActive) {
        if (!renderActive) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            clock.restartFrames()
            var nextSampleAt = 0L
            var sampledPlaying = false
            var sampledSpeed = 1f
            var lastCommit = previewStateRef.value?.committedSeek
            var wasSeekPreview = false
            while (isActive) {
                withFrameMillis { frameTime ->
                    val controller = MediaController.mediaControl
                    val preview = previewStateRef.value
                    val commit = preview?.committedSeek
                    if (commit != null && commit != lastCommit) {
                        lastCommit = commit
                        clock.seek(commit.positionMs + effectiveLyricTimingOffsetMs(), frameTime)
                        seekRevision.intValue++
                        columnState.resumeFollowing()
                    }
                    val previewPosition = preview?.positionMs?.plus(effectiveLyricTimingOffsetMs())
                    if (previewPosition != null && !wasSeekPreview) {
                        seekRevision.intValue++
                        columnState.resumeFollowing()
                    }
                    wasSeekPreview = previewPosition != null
                    // Player position is sampled at 12.5 Hz. The frame clock interpolates
                    // presentation time; it does not query the player 120 times per second.
                    val report = if (frameTime >= nextSampleAt) {
                        nextSampleAt = frameTime + 80L
                        // isPlaying/speed 一并只在采样点读取，避免每帧的会话往返。
                        sampledPlaying = controller?.isPlaying == true
                        sampledSpeed = controller?.playbackParameters?.speed ?: 1f
                        (controller?.currentPosition?.toFloat() ?: liveTimeLambda().toFloat()) +
                            effectiveLyricTimingOffsetMs()
                    } else null
                    val livePosition = clock.frame(frameTime, sampledPlaying, sampledSpeed, report)
                    // Preview is sampled every display frame, without player polling,
                    // report jitter filtering or resume smoothing delaying the finger.
                    val position = previewPosition ?: livePosition
                    if (previewPosition != null) {
                        seekTimeline.resolve(previewPosition)?.let(columnState::previewAt)
                    } else if (columnState.isSeekScrollActive) {
                        followTargets[lyricVisualFollowIndex(entries, position).coerceAtLeast(0)]?.let {
                            columnState.settleSeekPreviewTo(it, frameTime)
                        }
                    } else if (pressedRows.isEmpty() && abs(position - framePosition.floatValue) > SEEK_BACKWARD_JITTER_MS) {
                        followTargets[lyricVisualFollowIndex(entries, position)]?.let {
                            columnState.jumpTo(it, animateRows = columnState.waveAnchorIndex >= 0)
                        }
                    }
                    framePosition.floatValue = position
                    visualPosition.floatValue = previewPosition ?: clock.visualPositionMs
                }
            }
        }
    }

    // A document owns its measurements and scroll position. Font/layout changes
    // update those measurements in place instead of recreating every animated row.
    LyricPreviewResumeEffect(columnState)
    // 快速滚动时新行的首帧会在主线程惰性光栅化逐字笔画；围绕合成窗口在
    // 消息队列空闲时段预热段落，行进入视口前即命中缓存。
    val warmupHandleState = LocalNativeLyricWarmup.current
    LaunchedEffect(columnState, warmupHandleState, renderActive) {
        val state = warmupHandleState ?: return@LaunchedEffect
        // 预热句柄在文档就绪后才发布，先观察句柄再跟随合成窗口。
        snapshotFlow { state.value }.collectLatest { warmup ->
            if (warmup == null || !renderActive) return@collectLatest
            var lastRequested = Int.MIN_VALUE
            snapshotFlow {
                val visible = columnState.visibleIndexes(0f)
                if (visible.isEmpty()) -1 else if (columnState.isUserScrolling) {
                    // 滚动期间预热不取消：锚点偏向行程方向的前缘，先暖到达区；
                    // 预热内部的空闲门控让滚动帧先行。
                    val (leadTop, leadBottom) = columnState.compositionLeadPx()
                    when {
                        leadBottom > 0f -> visible.last
                        leadTop > 0f -> visible.first
                        else -> (visible.first + visible.last) / 2
                    }
                } else (visible.first + visible.last) / 2
            }.collect { anchor ->
                // 锚点移动不足两行不重启请求：快甩逐帧换锚会反复取消预热，
                // 让任何一次请求都完成不了。
                if (anchor >= 0) {
                    if (abs(anchor - lastRequested) >= 2) {
                        lastRequested = anchor
                        warmup.requestAround(anchor, radius = 60)
                    }
                } else warmup.cancelRequests()
            }
        }
    }
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
        isSeekPreview,
        columnState.isSeekScrollActive,
        showTranslation,
        fontFamily,
        SettingsLibrary.LyricFontSize,
        SettingsLibrary.TranslationFontSize,
        SettingsLibrary.LyricFontWeight,
        SettingsLibrary.LyricFontScale,
        SettingsLibrary.LyricLineBalance
    ) {
        if (!renderActive || columnState.viewportHeightPx == 0 ||
            columnState.isUserScrolling || columnState.isPreviewing || holdingLyric ||
            isSeekPreview || columnState.isSeekScrollActive) return@LaunchedEffect
        // One transition, directly to the requested line. Measurement corrections
        // are handled by the column, without delayed jumps or a next-line detour.
        val target = followTargets[followIndex.coerceAtLeast(0)] ?: return@LaunchedEffect
        columnState.followTo(target, animateRows = columnState.waveAnchorIndex >= 0)
    }

    // A new document arrives gently; playback frames never restart this entrance.
    val entrance = remember(lyricState.trackKey, entries) { Animatable(0f) }
    LaunchedEffect(entrance, renderActive) {
        if (renderActive && entrance.value < 1f) entrance.animateTo(1f, tween(210))
    }
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    key(columnState) {
    WavyLyricsColumn(
        itemCount = document.size,
        state = columnState,
        userScrollEnabled = userScrollEnabled && canInteract,
        renderActive = renderActive,
        animationsEnabled = !isSeekPreview && !columnState.isSeekScrollActive,
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
        val active = row.sourceIndex == followIndex ||
            (!isSeekPreview && row.sourceIndex in lyricState.activeLineIndexes)
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
        val browsing = isSeekPreview || columnState.isUserScrolling || columnState.isPreviewing || holdingLyric
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
                    animationsEnabled = !isSeekPreview && !columnState.isSeekScrollActive,
                    seekPreview = isSeekPreview,
                    liveTime = { visualPosition.floatValue },
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
                        animationsEnabled = !isSeekPreview && !columnState.isSeekScrollActive,
                        seekPreview = isSeekPreview,
                        liveTime = { visualPosition.floatValue },
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
    animationsEnabled: Boolean,
    seekPreview: Boolean,
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
    // Keep a conservative margin for preroll and overlapping uplift at each edge,
    // and let long-note emphasis tails finish before the row freezes.
    val renderWindow = remember(line, displayTokens) {
        (minOf(line.startMs, displayTokens.minOfOrNull { it.startMs } ?: line.startMs) - 1000f) to
            maxOf(maxOf(line.endMs, displayTokens.maxOfOrNull { it.endMs } ?: line.endMs) + 1000f,
                nativeLyricEmphasisEndMs(displayTokens))
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
        animationsEnabled = animationsEnabled,
        seekPreview = seekPreview,
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
/**
 * A line whose timeline truly interleaves with the held one — it begins before
 * the held line finishes — must not scroll it away mid-note: the viewport
 * moves only after both lines' times have ended. Lines that start after the
 * held line ends keep the original preroll scroll, and countdown tickers or
 * other-side vocals never hold, so background vocals cannot freeze the flow.
 */
internal fun lyricVisualFollowIndex(entries: List<YosLyricLine>, positionMs: Float): Int {
    var held = -1
    for (index in entries.indices) {
        val line = entries[index]
        if (positionMs < nativeLyricVisualStartMs(line.startMs, line.isCountdown)) continue
        val keeper = entries.getOrNull(held)
        if (keeper != null && !keeper.isCountdown && !keeper.otherSide &&
            line.startMs < keeper.endMs &&
            positionMs < maxOf(line.endMs, keeper.endMs)
        ) continue
        held = index
    }
    return held
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
