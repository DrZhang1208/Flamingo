package yos.music.player.ui.widgets

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Stable
internal class WavyLyricsColumnState {
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

    var isPreviewing by mutableStateOf(false)
        private set
    var previewInteractionGeneration by mutableIntStateOf(0)
        private set

    private val rowHeights = mutableStateMapOf<Int, Int>()
    private var itemCount by mutableIntStateOf(0)
    private var returnFromUserScroll = false

    fun updateItemCount(count: Int) {
        itemCount = count.coerceAtLeast(0)
        rowHeights.keys.filter { it >= itemCount }.forEach(rowHeights::remove)
        if (waveAnchorIndex >= itemCount && waveAnchorIndex >= 0) {
            waveAnchorIndex = -1
            resetRowAnimations()
        }
        scrollOffsetPx = scrollOffsetPx.coerceIn(minOffset(), maxOffset())
    }

    fun updateViewport(heightPx: Int) {
        viewportHeightPx = heightPx
        scrollOffsetPx = scrollOffsetPx.coerceIn(minOffset(), maxOffset())
    }

    fun updateRowHeight(index: Int, heightPx: Int) {
        if (index !in 0 until itemCount || heightPx < 0 || rowHeights[index] == heightPx) return

        // onSizeChanged runs after the row has already been placed once. Preserve a
        // visual anchor while replacing the estimate so the following layout does
        // not move the focused lyric (or the user's first visible row) by the error.
        val layoutAnchor = when {
            !isPreviewing && waveAnchorIndex in 0 until itemCount -> waveAnchorIndex
            else -> firstVisibleIndex()
        }
        val anchorTopBefore = rowTop(layoutAnchor)
        rowHeights[index] = heightPx
        val anchorCorrection = rowTop(layoutAnchor) - anchorTopBefore
        scrollOffsetPx = (scrollOffsetPx + anchorCorrection).coerceIn(minOffset(), maxOffset())
    }

    fun startUserScroll() {
        if (isUserScrolling) return
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
    }

    fun resumeFollowing() {
        isPreviewing = false
    }

    fun jumpTo(index: Int, animateRows: Boolean): Float {
        if (isPreviewing || index !in 0 until itemCount || viewportHeightPx <= 0) return 0f
        val target = (rowTop(index) - viewportHeightPx * TARGET_Y_RATIO)
            .coerceIn(minOffset(), maxOffset())
        val travel = target - scrollOffsetPx
        if (index == waveAnchorIndex && abs(travel) < 0.5f) {
            returnFromUserScroll = false
            return 0f
        }

        val shouldAnimateRows = animateRows && waveAnchorIndex >= 0 &&
            (index != waveAnchorIndex || returnFromUserScroll)
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

    fun rowTop(index: Int): Float {
        var result = 0f
        for (row in 0 until index) result += (rowHeights[row] ?: ESTIMATED_ROW_HEIGHT_PX.toInt())
        return result
    }

    fun rowHeight(index: Int): Int = rowHeights[index] ?: ESTIMATED_ROW_HEIGHT_PX.toInt()

    fun visibleIndexes(overscanPx: Float): IntRange {
        if (itemCount == 0) return IntRange.EMPTY
        val startY = scrollOffsetPx - overscanPx
        val endY = scrollOffsetPx + viewportHeightPx + overscanPx
        var y = 0f
        var first = 0
        while (first < itemCount - 1 && y + rowHeight(first) < startY) {
            y += rowHeight(first)
            first++
        }
        var last = first
        while (last < itemCount - 1 && y < endY) {
            y += rowHeight(last)
            last++
        }
        return first..last
    }

    private fun firstVisibleIndex(): Int {
        if (itemCount == 0 || scrollOffsetPx <= 0f) return 0
        var row = 0
        var bottom = rowHeight(0).toFloat()
        while (row < itemCount - 1 && bottom <= scrollOffsetPx) {
            row++
            bottom += rowHeight(row)
        }
        return row
    }

    private fun totalHeight(): Float =
        (0 until itemCount).sumOf { rowHeight(it).toDouble() }.toFloat()

    private fun minOffset(): Float = -viewportHeightPx * TARGET_Y_RATIO

    private fun maxOffset(): Float =
        maxOf(minOffset(), totalHeight() - viewportHeightPx * TARGET_Y_RATIO)

    private fun resetRowAnimations() {
        waveTravelPx = 0f
        waveCumulativeTravelPx = 0f
        waveGeneration++
        waveResetGeneration = waveGeneration
    }

    private companion object {
        const val TARGET_Y_RATIO = 0.0618f
        const val ESTIMATED_ROW_HEIGHT_PX = 112f
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
    itemKey: (Int) -> Any = { it },
    rowSpringParams: (Int) -> WavySpringParams,
    content: @Composable (Int) -> Unit
) {
    SideEffect { state.updateItemCount(itemCount) }
    val motion = remember(state) { NativeLyricScrollMotion() }
    LaunchedEffect(motion, state.waveGeneration) {
        motion.retarget(state.waveCumulativeTravelPx, state.waveResetGeneration,
            state.waveAnchorIndex, System.nanoTime())
        var moving = true
        while (moving) withFrameNanos { moving = motion.frame(it) }
    }
    val itemKeys = (0 until itemCount).map(itemKey)
    require(itemKeys.toSet().size == itemCount) { "WavyLazyColumn item keys must be unique" }

    val scrollState = remember(state) {
        ScrollableState { delta ->
            val before = state.scrollOffsetPx
            state.offsetRowBy(-delta)
            before - state.scrollOffsetPx
        }
    }
    LaunchedEffect(state, scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) state.startUserScroll() else state.finishUserScroll()
    }
    DisposableEffect(state) {
        onDispose { state.finishUserScroll() }
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
        val requested = buildSet {
            addAll(state.visibleIndexes(OVERSCAN_PX))
            if (state.waveAnchorIndex in 0 until itemCount) {
                val start = (state.waveAnchorIndex - ACTIVE_ROW_RADIUS).coerceAtLeast(0)
                val end = (state.waveAnchorIndex + ACTIVE_ROW_RADIUS).coerceAtMost(itemCount - 1)
                addAll(start..end)
            }
        }.filter { it in 0 until itemCount }.sorted()
        val loose = constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = requested.associateWith { index ->
            subcompose(itemKeys[index]) {
                WavyLyricsRow(
                    index = index,
                    stableKey = itemKeys[index],
                    state = state,
                    motion = motion,
                    springParams = rowSpringParams(index),
                    content = content
                )
            }.single().measure(loose)
        }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            placeables.forEach { (index, placeable) ->
                val y = state.rowTop(index) - state.scrollOffsetPx
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
            .onSizeChanged { state.updateRowHeight(index, it.height) }
            .graphicsLayer {
                translationY = motion.displacement(stableKey,
                    state.waveCumulativeTravelPx, state.waveResetGeneration)
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
