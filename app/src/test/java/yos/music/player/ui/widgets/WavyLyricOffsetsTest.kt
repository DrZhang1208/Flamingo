package yos.music.player.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class WavyLyricOffsetsTest {
    @Test fun cachedOffsetsAndBinaryVisibilityMatchLinearLayoutIncludingZeroHeightRows() {
        val random = Random(73)
        val heights = List(300) { random.nextInt(0, 300) }
        val state = WavyLyricsColumnState().apply {
            updateItemCount(heights.size)
            updateViewport(800)
            heights.forEachIndexed { i, h -> updateRowHeight(i, h) }
        }
        for (index in 0..heights.size) {
            assertEquals(heights.take(index).sum().toFloat(), state.rowTop(index), 0f)
        }
        for (offset in -1000..heights.sum() + 1000 step 37) {
            var y = 0f
            var first = 0
            while (first < heights.lastIndex && y + heights[first] < offset - 100f) {
                y += heights[first++]
            }
            var last = first
            while (last < heights.lastIndex && y < offset + 900f) y += heights[last++]
            assertEquals(first..last, state.visibleIndexes(100f, offset.toFloat()))
        }
        state.updateRowHeight(0, 400)
        assertEquals(400f, state.rowTop(1), 0f)
        state.updateItemCount(1)
        assertEquals(400f, state.rowTop(1), 0f)
        state.updateItemCount(0)
        assertEquals(IntRange.EMPTY, state.visibleIndexes(100f))
    }
}
