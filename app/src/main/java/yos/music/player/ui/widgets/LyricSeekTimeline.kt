package yos.music.player.ui.widgets

import yos.music.player.code.utils.lrc.YosLyricLine

internal data class LyricSeekScrollTarget(val fromIndex: Int, val toIndex: Int, val fraction: Float)

/** Maps pointer time to a fractional document position, independently of highlight preroll. */
internal class LyricSeekTimeline(entries: List<YosLyricLine>, documentTargets: Map<Int, Int>) {
    private data class Point(val startMs: Float, val endMs: Float, val documentIndex: Int)
    private val points = buildList<Point> {
        entries.forEachIndexed { index, line ->
            val target = documentTargets[index] ?: return@forEachIndexed
            val point = Point(line.startMs, line.endMs, target)
            // Simultaneous lines use the same last-line convention as lyric focus.
            if (lastOrNull()?.startMs == line.startMs) removeAt(lastIndex)
            add(point)
        }
    }

    fun resolve(timeMs: Float): LyricSeekScrollTarget? {
        if (points.isEmpty()) return null
        var low = 0
        var high = points.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (points[mid].startMs <= timeMs) low = mid + 1 else high = mid
        }
        val index = (low - 1).coerceAtLeast(0)
        val point = points[index]
        val next = points.getOrNull(index + 1)
            ?: return LyricSeekScrollTarget(point.documentIndex, point.documentIndex, 0f)
        val end = minOf(next.startMs, point.endMs.takeIf { it > point.startMs } ?: next.startMs)
        val fraction = ((timeMs - point.startMs) / (end - point.startMs).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return LyricSeekScrollTarget(point.documentIndex, next.documentIndex, fraction)
    }
}
