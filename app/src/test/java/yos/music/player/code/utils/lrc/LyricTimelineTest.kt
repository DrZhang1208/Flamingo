package yos.music.player.code.utils.lrc

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricLineResolverTest {
    private val entries = listOf(
        line(1_000f, "first"),
        line(2_500f, "second"),
        line(5_000f, "third")
    )

    @Test
    fun beforeFirstLineReturnsMinusOne() {
        assertEquals(-1, LyricLineResolver(entries).resolve(999L, -1).displayIndex)
    }

    @Test
    fun exactBoundarySelectsNewLine() {
        assertEquals(1, LyricLineResolver(entries).resolve(2_500L, -1).displayIndex)
    }

    @Test
    fun gapPreselectsTheNextLineLikeTheApkResolver() {
        assertEquals(2, LyricLineResolver(entries).resolve(4_999L, -1).displayIndex)
    }

    @Test
    fun emptyTimelineReturnsMinusOne() {
        assertEquals(-1, LyricLineResolver(emptyList()).resolve(10_000L, -1).displayIndex)
    }

    @Test
    fun overlappingLinesAreReturnedAsOneActiveSet() {
        val resolver = LyricLineResolver(
            listOf(
                line(1_000f, "lead").copy(endMs = 3_000f),
                line(1_500f, "background").copy(endMs = 2_500f),
                line(4_000f, "next")
            )
        )

        val resolution = resolver.resolve(2_000L, cachedIndex = 0)

        assertEquals(setOf(0, 1), resolution.activeIndexes)
        assertEquals(0, resolution.displayIndex)
        assertEquals(2_500L, resolution.nextBoundaryMs)
    }

    @Test
    fun distantSeekUsesBinarySearchAndCachedWalkContinuesNearby() {
        val resolver = LyricLineResolver(entries)
        val distant = resolver.resolve(5_100L, cachedIndex = -1)
        val nearby = resolver.resolve(2_600L, cachedIndex = distant.displayIndex)

        assertEquals(2, distant.displayIndex)
        assertEquals(1, nearby.displayIndex)
    }

    private fun line(start: Float, text: String) = YosLyricLine(
        startMs = start,
        endMs = start + 500f,
        tokens = listOf(YosLyricToken(start, start + 500f, text))
    )
}
