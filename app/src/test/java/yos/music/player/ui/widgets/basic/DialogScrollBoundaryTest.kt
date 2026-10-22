package yos.music.player.ui.widgets.basic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogScrollBoundaryTest {
    private val boundary = DialogScrollBoundary()
    @Test fun childrenGetFirstChanceToScrollAndFling() = runBlocking {
        assertEquals(Offset.Zero, boundary.onPreScroll(Offset(5f, 200f), NestedScrollSource.UserInput))
        assertEquals(Velocity.Zero, boundary.onPreFling(Velocity(5f, 2000f)))
    }

    @Test fun bothListEdgesAbsorbRemainingVerticalDrag() {
        for (source in listOf(NestedScrollSource.UserInput, NestedScrollSource.SideEffect)) {
            for (delta in listOf(-200f, 200f)) {
                assertEquals(Offset(0f, delta), boundary.onPostScroll(Offset(0f, 30f), Offset(7f, delta), source))
            }
        }
    }

    @Test fun boundaryFlingDoesNotReachTheSheet() = runBlocking {
        boundary.onPostScroll(Offset(0f, 10f), Offset.Zero, NestedScrollSource.UserInput)
        for (speed in listOf(-2000f, 2000f)) {
            assertEquals(Velocity(0f, speed), boundary.onPostFling(Velocity.Zero, Velocity(7f, speed)))
        }
    }
    @Test fun nextGestureAtTheEdgeCanDismissWhileSameGestureCannot() = runBlocking {
        boundary.startGesture()
        boundary.onPostScroll(Offset(0f, 40f), Offset.Zero, NestedScrollSource.UserInput)
        assertEquals(Offset(0f, 100f), boundary.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.UserInput))
        assertEquals(Velocity(0f, 1000f), boundary.onPostFling(Velocity.Zero, Velocity(0f, 1000f)))
        boundary.startGesture()
        assertEquals(Offset.Zero, boundary.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.UserInput))
        assertEquals(Velocity.Zero, boundary.onPostFling(Velocity.Zero, Velocity(0f, 1000f)))
    }

}
