package yos.music.player.ui.widgets

import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
internal fun PlaybackVolumeTrack(
    state: VolumeSliderState,
    readSystemStep: () -> Int,
    onStepChange: (Int) -> Unit,
    onInteraction: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: () -> Long = { SystemClock.uptimeMillis() }
) {
    val readRef = rememberUpdatedState(readSystemStep)
    val changeRef = rememberUpdatedState(onStepChange)
    val interactionRef = rememberUpdatedState(onInteraction)
    val releaseRef = rememberUpdatedState(onRelease)
    val nowRef = rememberUpdatedState(nowMillis)
    val height = animateDpAsState(if (state.dragging) 10.dp else 7.dp, tween(150), label = "volume height")
    LaunchedEffect(state, state.animating) {
        while (state.animating) withFrameMillis { state.frame(it) }
    }
    Box(modifier.height(14.dp).pointerInput(state) {
        detectHorizontalDragGestures(
            onDragStart = { state.begin(); interactionRef.value() },
            onDragEnd = {
                state.finish(readRef.value(), nowRef.value())
                releaseRef.value()
            },
            onDragCancel = { state.finish(readRef.value(), nowRef.value()) },
            onHorizontalDrag = { change, amount ->
                change.consume()
                state.dragBy(amount / size.width.coerceAtLeast(1).toFloat())?.let { changeRef.value(it) }
                interactionRef.value()
            }
        )
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(height.value)) { drawPlaybackProgress(state.position) }
    }
}
