package yos.music.player.ui.widgets

import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken

internal data class NativeLyricDocumentRow(val line: YosLyricLine, val sourceIndex: Int, val staticText: Boolean)

/** Long introductions become bounded lazy rows; never shape a whole essay on the UI thread. */
internal fun nativeLyricDocument(entries: List<YosLyricLine>): List<NativeLyricDocumentRow> = buildList {
    entries.forEachIndexed { sourceIndex, line ->
        val static = shouldRenderStaticLyric(line, entries.size)
        if (!static || line.text.length <= 512) {
            add(NativeLyricDocumentRow(line, sourceIndex, static))
        } else {
            val text = line.text
            var start = 0
            while (start < text.length) {
                var end = minOf(start + 512, text.length)
                if (end < text.length) {
                    // Prefer paragraph/sentence boundaries, retaining all source characters.
                    val boundary = (end - 1 downTo start + 256).firstOrNull {
                        text[it] == '\n' || text[it] in "。！？.!?"
                    }
                    if (boundary != null) end = boundary + 1
                    if (text[end - 1].isHighSurrogate()) end--
                }
                val chunk = text.substring(start, end)
                add(NativeLyricDocumentRow(line.copy(
                    tokens = listOf(YosLyricToken(line.startMs, line.startMs, chunk)),
                    lyricsTextLine = chunk,
                    translation = if (end == text.length) line.translation else "",
                    otherLrcEntries = if (end == text.length) line.otherLrcEntries else emptyList()
                ), sourceIndex, true))
                start = end
            }
        }
    }
}

/** Never infer word timings from a line's length or duration. */
internal fun nativeDisplayTokens(line: yos.music.player.code.utils.lrc.YosLyricLine,
    wordByWord: Boolean): List<yos.music.player.code.utils.lrc.YosLyricToken> =
    if (wordByWord && line.hasWordTiming) line.tokens
    else listOf(yos.music.player.code.utils.lrc.YosLyricToken(line.startMs, line.startMs, line.text))
