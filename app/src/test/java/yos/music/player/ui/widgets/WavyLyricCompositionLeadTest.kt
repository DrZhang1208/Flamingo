package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class WavyLyricCompositionLeadTest {
    private fun column(vararg heights: Int) = WavyLyricsColumnState().apply {
        updateItemCount(heights.size)
        updateViewport(800)
        heights.forEachIndexed { index, height -> updateRowHeight(index, height) }
    }

    @Test fun idleColumnKeepsThePlainOverscanWindow() {
        val state = column(*IntArray(60) { 100 })
        assertEquals(0f to 0f, state.compositionLeadPx())
    }

    @Test fun fastForwardFlingExtendsOnlyTheBottomWindow() {
        val state = column(*IntArray(200) { 100 })
        state.jumpTo(100, false)
        state.noteScrollDelta(200f)
        val (leadTop, leadBottom) = state.compositionLeadPx()
        assertEquals(0f, leadTop, 0.001f)
        assertEquals(200f * 8f, leadBottom, 1f)

        val plain = state.visibleIndexes(480f)
        val led = state.visibleIndexes(480f, leadBottomPx = leadBottom)
        assertEquals(plain.first, led.first)
        assertTrue("Lead must compose rows ahead of travel", led.last >= plain.last + 10)
    }

    @Test fun backwardFlingExtendsOnlyTheTopWindow() {
        val state = column(*IntArray(200) { 100 })
        state.offsetRowBy(10_000f)
        state.noteScrollDelta(-200f)
        val (leadTop, leadBottom) = state.compositionLeadPx()
        assertEquals(0f, leadBottom, 0.001f)
        assertEquals(200f * 8f, leadTop, 1f)
    }

    @Test fun extremeFlingLeadIsCapped() {
        val state = column(*IntArray(400) { 100 })
        state.offsetRowBy(10_000f)
        state.noteScrollDelta(5_000f)
        val (_, leadBottom) = state.compositionLeadPx()
        assertEquals(2400f, leadBottom, 1f)
    }

    @Test fun leadDecaysAfterScrollingStops() {
        val state = column(*IntArray(200) { 100 })
        state.offsetRowBy(10_000f)
        state.noteScrollDelta(200f)
        // The decay is time based: simulate idle frames with sleeps that advance real time.
        Thread.sleep(120)
        val (_, leadBottom) = state.compositionLeadPx()
        assertTrue("Lead must decay when scrolling stops", leadBottom < 200f * 8f * 0.5f)
    }
}
