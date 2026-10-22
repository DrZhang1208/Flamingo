package yos.music.player.ui.widgets

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Stable
internal class WavyLyricsColumnState(private val countdownIndexes: Set<Int> = emptySet()) {
    var scrollOffsetPx by mutableFloatStateOf(0f)
        private set
    var viewportHeightPx by mutableIntStateOf(0)
        private set
    var waveGeneration by mutableIntStateOf(0)
        private set
    var waveTravelPx by mutableFloatStateOf(0f)
        private set
    var waveCumulativeTravelPx by mutableFloatStateOf(0f)
        private set
    var waveResetGeneration by mutableIntStateOf(0)
        private set
    var waveAnchorIndex by mutableIntStateOf(-1)
        private set
    var isUserScrolling by mutableStateOf(false)
        private set
    var isSeekScrollActive by mutableStateOf(false)
        private set
    /** 手势收尾对账代数：仅用于让合成窗口在滚动结束后强制重估一次。 */
    var layoutReconcileGeneration by mutableIntStateOf(0)
        private set
    private var seekScrollMotion = LyricSeekScrollMotion()
    private var seekScrollTarget: LyricSeekScrollTarget? = null

    var isPreviewing by mutableStateOf(false)
        private set
    var previewInteractionGeneration by mutableIntStateOf(0)
        private set

    private val rowHeights = mutableStateMapOf<Int, Int>()
    private var itemCount by mutableIntStateOf(0)
    // Rebuild only when measured heights/count change, never for fractional motion.
    private val rowOffsets by derivedStateOf {
        FloatArray(itemCount + 1).also { offsets ->
            for (index in 0 until itemCount) {
                offsets[index + 1] = offsets[index] + rowHeight(index)
            }
        }
    }
    // Collapsed countdowns at the end must not become an empty scroll target.
    private val lastContentIndex by derivedStateOf {
        (itemCount - 1 downTo 0).firstOrNull { rowHeight(it) > 0 } ?: -1
    }
    private var returnFromUserScroll = false
    private var stationaryCountdownIndex = -1
    private var countdownWaveHeights by mutableStateOf<Map<Int, Int>>(emptyMap())

    // Directional composition lead. Fast flings travel farther per frame than
    // the static overscan spans; rows entering the viewport un-composed flash
    // blank and then hard-cut once the measurer catches up. Track the recent
    // scroll peak and let the layout compose rows ahead of the travel.
    private var peakScrollDeltaPx = 0f
    private var peakScrollDirection = 1f
    private var peakScrollAtNs = 0L

    fun noteScrollDelta(delta: Float) {
        val now = System.nanoTime()
        decayScrollPeak(now)
        if (abs(delta) > peakScrollDeltaPx) peakScrollDeltaPx = abs(delta)
        if (delta != 0f) peakScrollDirection = if (delta > 0f) 1f else -1f
        peakScrollAtNs = now
    }

    /** Top/bottom composition lead in px, extended only toward the travel direction. */
    fun compositionLeadPx(): Pair<Float, Float> {
        decayScrollPeak(System.nanoTime())
        val lead = (peakScrollDeltaPx * COMPOSITION_LEAD_FRAMES)
            .coerceIn(0f, MAX_COMPOSITION_LEAD_PX)
        return if (peakScrollDirection >= 0f) 0f to lead else lead to 0f
    }

    private fun decayScrollPeak(nowNs: Long) {
        if (peakScrollAtNs == 0L || peakScrollDeltaPx <= 0f) return
        val frames = ((nowNs - peakScrollAtNs) / 16_666_667f).coerceAtLeast(0f)
        if (frames > 0f) {
            peakScrollDeltaPx *= SCROLL_PEAK_FRAME_DECAY.pow(frames)
            peakScrollAtNs = nowNs
            if (peakScrollDeltaPx < 2f) peakScrollDeltaPx = 0f
        }
    }

    fun updateItemCount(count: Int) {
        itemCount = count.coerceAtLeast(0)
        rowHeights.keys.filter { it >= itemCount }.forEach(rowHeights::remove)
        if (waveAnchorIndex >= itemCount && waveAnchorIndex >= 0) {
            waveAnchorIndex = -1
            seekScrollTarget = null
            isSeekScrollActive = false
            resetRowAnimations()
        }
        scrollOffsetPx = scrollOffsetPx.coerceIn(minOffset(), maxOffset())
    }

    fun updateViewport(heightPx: Int) {
        viewportHeightPx = heightPx
        scrollOffsetPx = seekScrollTarget?.let(::seekTargetOffset)
            ?: scrollOffsetPx.coerceIn(minOffset(), maxOffset())
    }

    fun updateRowHeight(index: Int, heightPx: Int) {
        updateRowHeights(mapOf(index to heightPx))
    }

    fun updateRowHeights(measured: Map<Int, Int>) {
        if (measured.none { (index, height) ->
                index in 0 until itemCount && height >= 0 && rowHeights[index] != height
            }) return
        // Commit the batch before placement, preserving one visual anchor for all
        // measured rows. A post-placement callback would display stale prefix
        // offsets for one frame whenever a countdown changes height.
        // Opening a countdown consumes space above the following lyric. Preserve
        // that lyric even if measurement runs before the follow effect commits.
        // Closing still uses the countdown anchor for the existing upward handoff.
        val growingCountdown = countdownIndexes.firstOrNull { index ->
            index + 1 < itemCount && (index == stationaryCountdownIndex ||
                waveAnchorIndex >= 0 && index == waveAnchorIndex + 1) &&
                (measured[index] ?: rowHeight(index)) > rowHeight(index)
        }
        val layoutAnchor = when {
            isSeekScrollActive -> firstVisibleIndex()
            !isPreviewing && waveAnchorIndex >= 0 && growingCountdown != null -> growingCountdown + 1
            !isPreviewing && waveAnchorIndex in 0 until itemCount -> waveAnchorIndex
            else -> firstVisibleIndex()
        }
        val anchorTopBefore = rowTop(layoutAnchor)
        measured.forEach { (index, height) ->
            if (index in 0 until itemCount && height >= 0) rowHeights[index] = height
        }
        val anchorCorrection = rowTop(layoutAnchor) - anchorTopBefore
        val previousOffset = scrollOffsetPx
        val seekTarget = seekScrollTarget
        scrollOffsetPx = if (seekTarget != null) seekTargetOffset(seekTarget)
            else (scrollOffsetPx + anchorCorrection).coerceIn(minOffset(), maxOffset())
        if (isSeekScrollActive && seekTarget == null) seekScrollMotion.shiftBy(scrollOffsetPx - previousOffset)
    }

    fun startUserScroll() {
        if (isUserScrolling) return
        isSeekScrollActive = false
        seekScrollTarget = null
        countdownWaveHeights = emptyMap()
        isUserScrolling = true
        isPreviewing = true
        previewInteractionGeneration++
        returnFromUserScroll = true
        // Keep the current row displacement while the finger takes over. Erasing
        // it here would visibly snap an in-flight wave to its destination.
    }

    fun offsetRowBy(deltaPx: Float): Boolean {
        startUserScroll()
        val previous = scrollOffsetPx
        scrollOffsetPx = (previous + deltaPx).coerceIn(minOffset(), maxOffset())
        return scrollOffsetPx != previous
    }

    fun finishUserScroll() {
        if (!isUserScrolling) return
        isUserScrolling = false
        previewInteractionGeneration++
        layoutReconcileGeneration++
    }

    fun resumeFollowing() {
        isPreviewing = false
    }

    /** Natural countdown entry changes focus without scrolling the lyrics below it. */
    fun followTo(index: Int, animateRows: Boolean): Float {
        if (!isPreviewing && !returnFromUserScroll && viewportHeightPx > 0 &&
            index in countdownIndexes && index in 0 until itemCount && waveAnchorIndex >= 0 &&
            (waveAnchorIndex == index || waveAnchorIndex == index - 1)) {
            if (waveAnchorIndex == index - 1) stationaryCountdownIndex = index
            waveAnchorIndex = index
            return 0f
        }
        return jumpTo(index, animateRows)
    }

    fun jumpTo(index: Int, animateRows: Boolean): Float {
        if (isPreviewing || index !in 0 until itemCount || viewportHeightPx <= 0) return 0f
        isSeekScrollActive = false
        seekScrollTarget = null
        stationaryCountdownIndex = -1
        val target = (rowTop(index) - viewportHeightPx * TARGET_Y_RATIO)
            .coerceIn(minOffset(), maxOffset())
        val travel = target - scrollOffsetPx
        if (index == waveAnchorIndex && abs(travel) < 0.5f) {
            returnFromUserScroll = false
            // Taking over at the current line must also remove an unfinished wave.
            if (!animateRows && (waveTravelPx != 0f || waveCumulativeTravelPx != 0f || countdownWaveHeights.isNotEmpty())) {
                resetRowAnimations()
            }
            return 0f
        }

        val shouldAnimateRows = animateRows && waveAnchorIndex >= 0 &&
            (index != waveAnchorIndex || returnFromUserScroll)
        countdownWaveHeights = if (shouldAnimateRows && waveAnchorIndex in countdownIndexes) {
            countdownIndexes.filter { it < index }.associateWith(::rowHeight)
        } else emptyMap()
        scrollOffsetPx = target
        waveAnchorIndex = index
        if (shouldAnimateRows) {
            waveTravelPx = travel
            waveCumulativeTravelPx += travel
            waveGeneration++
        } else {
            resetRowAnimations()
        }
        returnFromUserScroll = false
        return travel
    }

    /** The same pointer time always gives the same position, with no tween chasing it. */
    fun previewAt(target: LyricSeekScrollTarget) {
        if (isPreviewing || target.fromIndex !in 0 until itemCount ||
            target.toIndex !in 0 until itemCount || viewportHeightPx <= 0) return
        if (!isSeekScrollActive) {
            seekScrollMotion = LyricSeekScrollMotion()
            resetRowAnimations()
        }
        stationaryCountdownIndex = -1
        seekScrollTarget = target
        scrollOffsetPx = seekTargetOffset(target)
        waveAnchorIndex = target.fromIndex
        returnFromUserScroll = false
        isSeekScrollActive = true
    }

    private fun seekTargetOffset(target: LyricSeekScrollTarget): Float {
        val from = rowTop(target.fromIndex)
        val to = rowTop(target.toIndex)
        return (from + (to - from) * target.fraction.coerceIn(0f, 1f) - viewportHeightPx * TARGET_Y_RATIO)
            .coerceIn(minOffset(), maxOffset())
    }

    /** Only release/cancellation uses a short handoff back to ordinary playback. */
    fun settleSeekPreviewTo(index: Int, nowMs: Long) {
        if (isPreviewing || index !in 0 until itemCount || viewportHeightPx <= 0) return
        seekScrollTarget = null
        stationaryCountdownIndex = -1
        val target = (rowTop(index) - viewportHeightPx * TARGET_Y_RATIO).coerceIn(minOffset(), maxOffset())
        scrollOffsetPx = seekScrollMotion.frame(scrollOffsetPx, target, nowMs)
            .coerceIn(minOffset(), maxOffset())
        waveAnchorIndex = index
        returnFromUserScroll = false
        isSeekScrollActive = !seekScrollMotion.finished
    }

    fun rowTop(index: Int): Float = rowOffsets[index.coerceIn(0, itemCount)] +
        (index - itemCount).coerceAtLeast(0) * ESTIMATED_ROW_HEIGHT_PX

    fun rowHeight(index: Int): Int = rowHeights[index] ?: ESTIMATED_ROW_HEIGHT_PX.toInt()

    // The spring is a second scroll coordinate, applied after layout. Clamping
    // only scrollOffsetPx allows an unfinished follow/seek to drag the rendered
    // first row below the viewport even though the logical list is at its start.
    fun boundedDisplacement(displacementPx: Float): Float {
        // A seek can remove the opening countdown while its scroll is still in
        // flight. Retain exactly that removed slot's space until the wave settles;
        // otherwise the new first-row bound clips all of the intended animation.
        val removedCountdownHeight = countdownWaveHeights.entries.sumOf { (index, height) ->
            (height - rowHeight(index)).coerceAtLeast(0)
        }.toFloat()
        return scrollOffsetPx - (scrollOffsetPx - displacementPx)
            .coerceIn(minOffset() - removedCountdownHeight, maxOffset())
    }

    fun visibleIndexes(
        overscanPx: Float,
        offsetPx: Float = scrollOffsetPx,
        leadTopPx: Float = 0f,
        leadBottomPx: Float = 0f
    ): IntRange {
        if (itemCount == 0) return IntRange.EMPTY
        val offsets = rowOffsets
        val startY = offsetPx - overscanPx - leadTopPx
        val endY = offsetPx + viewportHeightPx + overscanPx + leadBottomPx
        // Keep the original inclusive edge/extra trailing row semantics.
        val first = lowerBoundRow { offsets[it + 1] >= startY }
        val last = lowerBoundRow { offsets[it] >= endY }.coerceAtLeast(first)
        return first..last
    }

    private inline fun lowerBoundRow(predicate: (Int) -> Boolean): Int {
        var low = 0
        var high = itemCount - 1
        while (low < high) {
            val mid = (low + high) ushr 1
            if (predicate(mid)) high = mid else low = mid + 1
        }
        return low
    }

    private fun firstVisibleIndex(): Int {
        if (itemCount == 0 || scrollOffsetPx <= 0f) return 0
        val offsets = rowOffsets
        return lowerBoundRow { offsets[it + 1] > scrollOffsetPx }
    }

    private fun totalHeight(): Float = rowOffsets[itemCount]

    private fun minOffset(): Float = -viewportHeightPx * TARGET_Y_RATIO

    private fun maxOffset(): Float {
        val last = lastContentIndex
        if (last < 0) return minOffset()
        val anchor = viewportHeightPx * TARGET_Y_RATIO
        // Keep the last ordinary row at the same readable anchor as auto-follow.
        // A paragraph taller than the viewport may still scroll to its bottom.
        return maxOf(minOffset(), rowTop(last) - anchor,
            totalHeight() - viewportHeightPx + anchor)
    }

    private fun resetRowAnimations() {
        countdownWaveHeights = emptyMap()
        waveTravelPx = 0f
        waveCumulativeTravelPx = 0f
        waveGeneration++
        waveResetGeneration = waveGeneration
    }

    private companion object {
        const val TARGET_Y_RATIO = 0.0618f
        const val ESTIMATED_ROW_HEIGHT_PX = 112f
        const val COMPOSITION_LEAD_FRAMES = 8f
        const val MAX_COMPOSITION_LEAD_PX = 2400f
        const val SCROLL_PEAK_FRAME_DECAY = 0.86f
    }
}

@Composable
internal fun rememberWavyLyricsColumnState(): WavyLyricsColumnState =
    remember { WavyLyricsColumnState() }

@Composable
internal fun WavyLyricsColumn(
    itemCount: Int,
    state: WavyLyricsColumnState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    renderActive: Boolean = true,
    animationsEnabled: Boolean = true,
    itemKey: (Int) -> Any = { it },
    rowSpringParams: (Int) -> WavySpringParams,
    content: @Composable (Int) -> Unit
) {
    SideEffect { state.updateItemCount(itemCount) }
    val motion = remember(state) { NativeLyricScrollMotion() }
    LaunchedEffect(motion, state.waveGeneration, renderActive, animationsEnabled) {
        if (!renderActive || !animationsEnabled) {
            motion.settle(state.waveCumulativeTravelPx, state.waveResetGeneration, state.waveAnchorIndex)
            return@LaunchedEffect
        }
        // Start and sample from the same display clock. A wall-clock start can
        // fall after the current frame timestamp and skip the first sample.
        withFrameNanos { now ->
            motion.retarget(state.waveCumulativeTravelPx, state.waveResetGeneration,
                state.waveAnchorIndex, now)
        }
        var moving = true
        while (moving) withFrameNanos { moving = motion.frame(it) }
    }
    val itemKeys = remember(itemCount, itemKey) {
        (0 until itemCount).map(itemKey).also { keys ->
            require(keys.toSet().size == itemCount) { "WavyLazyColumn item keys must be unique" }
        }
    }

    val scrollState = remember(state) {
        ScrollableState { delta ->
            val before = state.scrollOffsetPx
            state.offsetRowBy(-delta)
            state.noteScrollDelta(delta)
            before - state.scrollOffsetPx
        }
    }
    LaunchedEffect(state, scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) state.startUserScroll() else state.finishUserScroll()
    }
    DisposableEffect(state) {
        onDispose { state.finishUserScroll() }
    }

    // 渐进全覆盖：常规歌曲最终合成全部行，每行持有实测高度——快甩到任何
    // 位置偏移都精确，不会落在估高脱节的窗口里。爬坡以动作位置为锚（近处
    // 先行），滚动中暂停；落定或跳转后重新锚定，优先补齐当前所在区域。
    // 进程级段落仓让重开后的爬坡全部命中借用，行合成近乎零成本。
    val fullCoverage = itemCount <= FULL_RENDER_ROW_LIMIT
    val documentToken = itemKeys.firstOrNull() to itemKeys.lastOrNull()
    val coverage = remember(documentToken) { WavyLyricCoverage(itemCount) }
    LaunchedEffect(state, itemCount, renderActive, fullCoverage) {
        if (!renderActive || !fullCoverage) return@LaunchedEffect
        val runRamp: suspend () -> Unit = {
            coverage.reanchor(
                state.waveAnchorIndex.takeIf { it in 0 until itemCount }
                    ?: state.visibleIndexes(0f).takeIf { !it.isEmpty() }?.let { (it.first + it.last) / 2 }
                    ?: 0
            )
            while (coverage.hasPending()) {
                withFrameNanos { }
                if (state.isUserScrolling) continue
                coverage.step(FULL_RENDER_STEP_ROWS)
            }
        }
        launch { snapshotFlow { state.isUserScrolling }.collectLatest { if (!it) runRamp() } }
        launch { snapshotFlow { state.waveAnchorIndex }.drop(1).collectLatest { if (!state.isUserScrolling) runRamp() } }
    }

    // Fractional motion invalidates drawing each frame, but should only
    // invalidate measurement when virtualization actually needs different rows.
    val fullRenderList = remember(itemCount) { (0 until itemCount).toList() }
    val requestedIndexes = remember(state, motion, itemCount, animationsEnabled, coverage) {
        derivedStateOf(structuralEqualityPolicy()) {
            // 手势收尾对账：估高→实测的收敛迭代在极端落位下可能停在旧窗口，
            // finishUserScroll 递增该代数强制重估一次，保证视口行全部合成。
            state.layoutReconcileGeneration
            coverage.revision
            val composed = coverage.snapshot()
            // 覆盖完成后集合恒定：短路返回，滚动/弹簧帧不再重建全量集合
            if (itemCount <= FULL_RENDER_ROW_LIMIT && composed.size >= itemCount) {
                return@derivedStateOf fullRenderList
            }
            buildSet {
                val (leadTop, leadBottom) = state.compositionLeadPx()
                addAll(state.visibleIndexes(OVERSCAN_PX, leadTopPx = leadTop, leadBottomPx = leadBottom))
                if (animationsEnabled) motion.visibleDisplacements(state.waveCumulativeTravelPx, state.waveResetGeneration).forEach { displacement ->
                    addAll(state.visibleIndexes(OVERSCAN_PX,
                        state.scrollOffsetPx - state.boundedDisplacement(displacement)))
                }
                if (state.waveAnchorIndex in 0 until itemCount) {
                    val start = (state.waveAnchorIndex - ACTIVE_ROW_RADIUS).coerceAtLeast(0)
                    val end = (state.waveAnchorIndex + ACTIVE_ROW_RADIUS).coerceAtMost(itemCount - 1)
                    addAll(start..end)
                }
                addAll(composed)
            }.filter { it in 0 until itemCount }.sorted()
        }
    }

    SubcomposeLayout(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { state.updateViewport(it.height) }
            .scrollable(
                state = scrollState,
                orientation = Orientation.Vertical,
                enabled = userScrollEnabled,
                flingBehavior = LyricFlingBehavior
            ),
    ) { constraints ->
        val requested = requestedIndexes.value
        val loose = constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = requested.associateWith { index ->
            subcompose(itemKeys[index]) {
                WavyLyricsRow(
                    index = index,
                    stableKey = itemKeys[index],
                    state = state,
                    motion = motion,
                    animationsEnabled = animationsEnabled,
                    springParams = rowSpringParams(index),
                    content = content
                )
            }.single().measure(loose)
        }
        // Internal measurement bookkeeping must not subscribe this measure pass
        // to the state it just wrote. Placement below reads the updated offsets.
        Snapshot.withoutReadObservation {
            state.updateRowHeights(placeables.mapValues { it.value.height })
        }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            placeables.forEach { (index, placeable) ->
                val y = state.rowTop(index) - state.scrollOffsetPx
                // 整篇渲染下行数多：视口外的行保留上次位置，滚动帧不再全量 placement
                if (y > state.viewportHeightPx + OVERSCAN_PX ||
                    y + placeable.height < -OVERSCAN_PX) return@forEach
                placeable.placeRelative(0, y.toInt())
            }
        }
    }
}

@Composable
private fun WavyLyricsRow(
    index: Int,
    stableKey: Any,
    state: WavyLyricsColumnState,
    motion: NativeLyricScrollMotion,
    animationsEnabled: Boolean,
    springParams: WavySpringParams,
    content: @Composable (Int) -> Unit
) {
    DisposableEffect(motion, stableKey, springParams) {
        motion.register(stableKey, index, springParams,
            state.waveCumulativeTravelPx, state.waveResetGeneration)
        onDispose { motion.unregister(stableKey) }
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = if (animationsEnabled) state.boundedDisplacement(motion.displacement(stableKey,
                    state.waveCumulativeTravelPx, state.waveResetGeneration, index, springParams)) else 0f
            }
    ) {
        content(index)
    }
}

/** Offset visible before and after a row consumes a batch of scroll events. */
internal fun resolveWaveOffset(
    rowOffset: Float,
    consumedTravel: Float,
    consumedReset: Int,
    totalTravel: Float,
    resetGeneration: Int
): Float = if (consumedReset != resetGeneration) {
    totalTravel
} else {
    rowOffset + totalTravel - consumedTravel
}

// Foundation owns touch slop, gesture cancellation and interruptible flings;
// retain the existing lyric-specific decay rather than changing scroll feel.
private object LyricFlingBehavior : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        var velocity = initialVelocity / 1000f
        var lastFrame = withFrameMillis { it }
        var hitBoundary = false
        while (abs(velocity) > FLING_STOP_VELOCITY_PX_PER_MS && !hitBoundary) {
            withFrameMillis { frame ->
                val deltaMs = (frame - lastFrame).coerceIn(0L, 100L).toFloat()
                val delta = velocity * deltaMs
                val consumed = scrollBy(delta)
                hitBoundary = abs(delta - consumed) > 0.5f
                velocity *= FLING_FRICTION.pow(deltaMs / 16f)
                lastFrame = frame
            }
        }
        return if (hitBoundary) velocity * 1000f else 0f
    }
}

private const val FLING_FRICTION = 0.95f
private const val FLING_STOP_VELOCITY_PX_PER_MS = 0.05f
private const val OVERSCAN_PX = 480f
private const val ACTIVE_ROW_RADIUS = 8
private const val FULL_RENDER_ROW_LIMIT = 300
private const val FULL_RENDER_STEP_ROWS = 10

/** 渐进全覆盖的记账：已合成行集合与按锚点距离排序的待合成队列。 */
private class WavyLyricCoverage(private val count: Int) {
    private val composed = HashSet<Int>()
    private var pending: List<Int> = emptyList()
    var revision by mutableIntStateOf(0)
        private set

    fun reanchor(center: Int) {
        if (composed.size >= count) return
        val anchor = center.coerceIn(0, (count - 1).coerceAtLeast(0))
        pending = nativeLyricWarmupOrder(anchor, count, count).filter { it !in composed }
        revision++
    }

    fun step(rows: Int) {
        if (pending.isEmpty()) return
        composed.addAll(pending.take(rows))
        pending = pending.drop(rows)
        revision++
    }

    fun hasPending(): Boolean = pending.isNotEmpty()
    fun snapshot(): Set<Int> = composed
}

internal data class SpringSample(val position: Float, val velocity: Float)

internal fun sampleSpring(
    initialPosition: Float,
    initialVelocity: Float,
    seconds: Float,
    omega: Float,
    dampingRatio: Float
): SpringSample {
    if (dampingRatio >= 1f) {
        val coefficient = initialVelocity + omega * initialPosition
        val decay = exp(-omega * seconds)
        val position = (initialPosition + coefficient * seconds) * decay
        val velocity = (coefficient - omega * (initialPosition + coefficient * seconds)) * decay
        return SpringSample(position, velocity)
    }
    val dampedOmega = omega * sqrt(1f - dampingRatio * dampingRatio)
    val coefficient = (initialVelocity + dampingRatio * omega * initialPosition) / dampedOmega
    val decay = exp(-dampingRatio * omega * seconds)
    val cosine = cos(dampedOmega * seconds)
    val sine = sin(dampedOmega * seconds)
    val position = decay * (initialPosition * cosine + coefficient * sine)
    val velocity = decay * (
        -dampingRatio * omega * (initialPosition * cosine + coefficient * sine) +
            (-initialPosition * dampedOmega * sine + coefficient * dampedOmega * cosine)
        )
    return SpringSample(position, velocity)
}
