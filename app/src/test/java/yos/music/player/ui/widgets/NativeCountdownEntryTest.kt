package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class NativeCountdownEntryTest {
    private fun column(anchor: Int = 2) = WavyLyricsColumnState(setOf(3)).apply {
        updateItemCount(8)
        updateViewport(1000)
        updateRowHeights((0..7).associateWith { if (it == 3) 0 else 100 })
        jumpTo(anchor, false)
    }

    @Test fun entryKeepsEveryFollowingLyricStillRegardlessOfMeasurementOrder() {
        for (measureFirst in listOf(false, true)) {
            val state = column()
            val before = (4..7).map { state.rowTop(it) - state.scrollOffsetPx }
            val generation = state.waveGeneration
            for (ms in 1000..1500 step 4) {
                val height = (64f * countdownEmphasis(ms.toFloat(), 1000f, 12000f)).toInt()
                if (measureFirst) state.updateRowHeight(3, height)
                state.followTo(3, true)
                if (!measureFirst) state.updateRowHeight(3, height)
                (4..7).forEachIndexed { i, row ->
                    assertEquals("Following row $row moved at $ms", before[i],
                        state.rowTop(row) - state.scrollOffsetPx, 0.001f)
                }
                assertEquals("Entering dots must not start a scroll spring", generation, state.waveGeneration)
            }
        }
    }

    @Test fun exitStillMovesUpAndTheNextLyricFollowsNormally() {
        val state = column()
        state.followTo(3, true)
        state.updateRowHeight(3, 64)
        val before = state.rowTop(4) - state.scrollOffsetPx
        state.updateRowHeight(3, 32)
        assertEquals(before - 32f, state.rowTop(4) - state.scrollOffsetPx, 0.001f)
        state.updateRowHeight(3, 0)
        assertTrue(state.followTo(4, true) > 0f)
        assertEquals(4, state.waveAnchorIndex)
    }

    @Test fun initialCountdownAndExplicitSeekStillReachTheirRequestedAnchor() {
        val initial = column(0)
        assertTrue(initial.followTo(3, true) > 0f)
        val seeking = column()
        assertTrue(seeking.jumpTo(3, true) > 0f)
        assertEquals(3, seeking.waveAnchorIndex)
        seeking.startUserScroll()
        seeking.offsetRowBy(50f)
        seeking.finishUserScroll()
        seeking.resumeFollowing()
        assertTrue(seeking.followTo(3, true) != 0f)
    }

    @Test fun initialCountdownKeepsTheHighlightAnchorInsteadOfMovingAboveIt() {
        for (index in listOf(0, 3)) {
            val state = WavyLyricsColumnState(setOf(index)).apply {
                updateItemCount(8)
                updateViewport(1000)
                updateRowHeights((0..7).associateWith { if (it == index) 0 else 100 })
                followTo(index, false)
            }
            val before = state.rowTop(index) - state.scrollOffsetPx
            for (height in 0..64) {
                state.updateRowHeight(index, height)
                state.followTo(index, true)
                assertEquals("Initial dots drifted above their anchor at height $height", before,
                    state.rowTop(index) - state.scrollOffsetPx, 0.001f)
            }
        }
    }
}
