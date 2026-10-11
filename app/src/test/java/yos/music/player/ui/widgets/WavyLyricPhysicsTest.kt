package yos.music.player.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import kotlin.math.sqrt

class WavyLyricPhysicsTest {
    @Test
    fun firstAndBlankLinesUseApkFallbackParams() {
        val lines = listOf(
            line(1_000f, "first"),
            line(2_000f, "")
        )

        assertEquals(WavySpringParams(0.9f, 15f, 90f), wavySpringParams(lines, 0))
        assertEquals(WavySpringParams(0.9f, 15f, 90f), wavySpringParams(lines, 1))
    }

    @Test
    fun lyricGapMapsToApkStiffnessRange() {
        val closeLines = listOf(line(1_000f, "a"), line(1_100f, "b"))
        val distantLines = listOf(line(1_000f, "a"), line(1_800f, "b"))

        val close = wavySpringParams(closeLines, 1)
        val distant = wavySpringParams(distantLines, 1)

        assertEquals(220f, close.stiffness, 0.0001f)
        assertEquals(sqrt(220f) * 2.2f, close.damping, 0.0001f)
        assertEquals(170f, distant.stiffness, 0.0001f)
        assertEquals(sqrt(170f) * 2.2f, distant.damping, 0.0001f)
    }

    @Test
    fun onlyRowsBelowAnchorReceiveShrinkingDelay() {
        assertEquals(0L, wavyRowDelayMs(index = 3, anchorIndex = 4))
        assertEquals(0L, wavyRowDelayMs(index = 4, anchorIndex = 4))
        assertEquals(24L, wavyRowDelayMs(index = 5, anchorIndex = 4))
        assertEquals(46L, wavyRowDelayMs(index = 6, anchorIndex = 4))
        assertEquals(
            wavyRowDelayMs(index = 12, anchorIndex = 4),
            wavyRowDelayMs(index = 40, anchorIndex = 4)
        )
    }

    @Test
    fun apkOverDampedBranchIsRepresentedAsCriticalDamping() {
        val normal = wavySpringParams(
            listOf(line(1_000f, "a"), line(1_300f, "b")),
            1
        ).asComposeSpec()
        val fallback = wavySpringParams(listOf(line(1_000f, "a")), 0).asComposeSpec()

        assertEquals(1f, normal.dampingRatio, 0.0001f)
        assertEquals(100f, fallback.stiffness, 0.0001f)
        assertEquals(0.8333333f, fallback.dampingRatio, 0.0001f)
    }

    @Test
    fun analyticalCriticalSpringMatchesRecoveredEquation() {
        val sample = sampleSpring(
            initialPosition = 10f,
            initialVelocity = 0f,
            seconds = 0.1f,
            omega = 10f,
            dampingRatio = 1.2f
        )

        assertEquals(20f / kotlin.math.E.toFloat(), sample.position, 0.0001f)
        assertEquals(-100f / kotlin.math.E.toFloat(), sample.velocity, 0.0001f)
    }

    @Test
    fun analyticalUnderDampedSpringStartsFromSuppliedState() {
        val sample = sampleSpring(12f, -3f, 0f, omega = 9f, dampingRatio = 0.7f)

        assertEquals(12f, sample.position, 0.0001f)
        assertEquals(-3f, sample.velocity, 0.0001f)
    }

    @Test
    fun measuredRowsBeforeAnchorKeepFocusedRowAtSameViewportPosition() {
        val state = WavyLyricsColumnState()
        state.updateItemCount(20)
        state.updateViewport(1_000)
        state.jumpTo(index = 10, animateRows = false)
        val focusedY = state.rowTop(10) - state.scrollOffsetPx

        state.updateRowHeight(index = 2, heightPx = 212)
        assertEquals(focusedY, state.rowTop(10) - state.scrollOffsetPx, 0.0001f)

        // A disappearing/countdown row is a real zero-height measurement, not an
        // instruction to retain the previous estimate.
        state.updateRowHeight(index = 3, heightPx = 0)
        assertEquals(focusedY, state.rowTop(10) - state.scrollOffsetPx, 0.0001f)
        assertEquals(0, state.rowHeight(3))
    }

    @Test
    fun exactJumpDoesNotRestartAnExistingWaveGeneration() {
        val state = WavyLyricsColumnState()
        state.updateItemCount(10)
        state.updateViewport(1_000)
        state.jumpTo(index = 3, animateRows = false)
        val generation = state.waveGeneration

        state.jumpTo(index = 3, animateRows = true)

        assertEquals(generation, state.waveGeneration)
    }

    @Test
    fun releasingManualScrollCreatesReturnWaveForSameAnchor() {
        val state = WavyLyricsColumnState()
        state.updateItemCount(10)
        state.updateViewport(1_000)
        state.jumpTo(index = 3, animateRows = false)
        state.offsetRowBy(40f)
        state.finishUserScroll()
        state.resumeFollowing()

        val travel = state.jumpTo(index = 3, animateRows = true)

        assertEquals(-40f, travel, 0.0001f)
        assertEquals(-40f, state.waveTravelPx, 0.0001f)
    }

    private fun line(startMs: Float, text: String): YosLyricLine = YosLyricLine(
        startMs = startMs,
        endMs = startMs + 500f,
        tokens = listOf(YosLyricToken(startMs, startMs + 500f, text))
    )
}
