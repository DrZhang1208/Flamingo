package yos.music.player.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NativeLyricLayoutWorkTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movingRowsDoNotRemeasureWhenVirtualizedRowsStayTheSame() {
        val state = WavyLyricsColumnState()
        val measures = AtomicInteger()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WavyLyricsColumn(3, state, Modifier.size(320.dp, 500.dp),
                rowSpringParams = { WavySpringParams(0.9f, 24f, 170f) }) { index ->
                Box(Modifier.testTag("row-$index").fillMaxWidth().height(140.dp).layout { measurable, constraints ->
                    measures.incrementAndGet()
                    val child = measurable.measure(constraints)
                    layout(child.width, child.height) { child.place(0, 0) }
                })
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnUiThread { state.jumpTo(0, false) }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { state.jumpTo(1, true) }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        val afterStart = measures.get()
        val startY = compose.onNodeWithTag("row-1").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        val endY = compose.onNodeWithTag("row-1").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The rows must actually be animating", endY < startY)
        assertEquals("Moving an unchanged set of rows should only update layers", afterStart, measures.get())
    }
}
