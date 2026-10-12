package yos.music.player.ui.widgets

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/** Start with horizontal intent, then track both axes, including vertical-only cancellation. */
@Composable
internal fun PlaybackSeekTrack(
    gestureKey: Any?,
    position: () -> Float,
    duration: () -> Long,
    livePosition: () -> Float,
    dragging: Boolean,
    cancelAreaContains: (androidx.compose.ui.geometry.Offset) -> Boolean,
    onStart: () -> Unit,
    onPreview: (Float, Boolean) -> Unit,
    onFinish: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    val coordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val positionRef = rememberUpdatedState(position)
    val durationRef = rememberUpdatedState(duration)
    val livePositionRef = rememberUpdatedState(livePosition)
    val cancelContainsRef = rememberUpdatedState(cancelAreaContains)
    val startRef = rememberUpdatedState(onStart)
    val previewRef = rememberUpdatedState(onPreview)
    val finishRef = rememberUpdatedState(onFinish)
    val height = animateDpAsState(if (dragging) 10.dp else 7.dp, tween(150), label = "seek height")
    Box(
        modifier.fillMaxWidth().height(24.dp)
            .onGloballyPositioned { coordinates.value = it }
            .pointerInput(gestureKey) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (durationRef.value() <= 0L) return@awaitEachGesture
                    var initialDelta = 0f
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        initialDelta = overSlop
                        change.consume()
                    } ?: return@awaitEachGesture
                    val session = SeekDragSession(positionRef.value(), durationRef.value().toFloat(), size.width.toFloat())
                    var released = false
                    startRef.value()
                    try {
                        fun preview(deltaX: Float, localPosition: androidx.compose.ui.geometry.Offset) {
                            val layout = coordinates.value
                            val inCancelArea = layout?.isAttached == true &&
                                cancelContainsRef.value(layout.localToRoot(localPosition))
                            session.move(deltaX, inCancelArea, livePositionRef.value())
                            previewRef.value(session.positionMs, session.cancelled)
                        }
                        preview(initialDelta, slop.position)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == slop.id } ?: break
                            if (change.isConsumed) break
                            // Include the last pointer position on UP; no last-frame cancellation race.
                            preview(change.positionChange().x, change.position)
                            change.consume()
                            if (!change.pressed) {
                                released = true
                                finishRef.value(session.release())
                                break
                            }
                        }
                    } finally {
                        // System cancellation, a new track or removal of this page must never seek.
                        if (!released) finishRef.value(null)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().height(height.value)) {
            val total = durationRef.value()
            drawPlaybackProgress(if (total > 0L) positionRef.value() / total else 0f)
        }
    }
}
