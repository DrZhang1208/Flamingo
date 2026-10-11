package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

/** A gesture that scrolled content cannot turn into a sheet drag at its edge. */
internal class DialogScrollBoundary(private val elastic: DialogElasticMotion? = null) : NestedScrollConnection {
    private var scrolledContent = false

    fun startGesture() {
        scrolledContent = false
        elastic?.startGesture()
    }

    fun endGesture() { elastic?.release() }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f && elastic != null && elastic.offset < 0f) {
            return Offset(0f, elastic.drag(available.y))
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed.y != 0f) scrolledContent = true
        if (scrolledContent) return Offset(0f, available.y)
        if (source == NestedScrollSource.UserInput && available.y < 0f && elastic != null) {
            elastic.drag(available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (scrolledContent) Velocity(0f, available.y) else Velocity.Zero
}

internal fun Modifier.containDialogScroll(elastic: DialogElasticMotion? = null): Modifier = composed {
    val boundary = remember(elastic) { DialogScrollBoundary(elastic) }
    pointerInput(boundary) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            boundary.startGesture()
            // Observe without consuming: ordinary clicks and list scrolling win.
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
            boundary.endGesture()
            // Keep the decision for this gesture's fling; reset only on next down.
        }
    }.nestedScroll(boundary)
}
