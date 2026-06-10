package yos.music.player.code.utils.lrc

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.util.fastForEachIndexed
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.objects.MediaViewModelObject
import java.io.StringReader
import kotlin.math.abs

/**
 * Lrc 歌词文本处理
 */
class YosLrcFactory(private val formatText: Boolean = true) {
    /**
     * Lrc 歌词文本处理方法
     * @param lrcText Lrc 格式的文本
     */
    fun formatLrcEntries(lrcText: String): List<YosLyricLine> {
        val parsedPairs = when {
            lrcText.contains("<tt", ignoreCase = true) -> parseTtml(lrcText)
            krcFormattedLineRegex.containsMatchIn(lrcText) -> parseKrc(lrcText)
            yrcFormattedLineRegex.containsMatchIn(lrcText) -> parseYrc(lrcText)
            qrcFormattedLineRegex.containsMatchIn(lrcText) -> parseQrc(lrcText)
            lyricifySyllableLineRegex.containsMatchIn(lrcText) -> parseLyricifySyllable(lrcText)
            lyricifyLineRegex.containsMatchIn(lrcText) -> parseLyricifyLines(lrcText)
            else -> parseLrcLike(lrcText)
        }
        val filteredPairs = normalizeParsedLines(applyOffset(parsedPairs, extractOffsetMs(lrcText)))
        // 关闭逐字歌词时，将逐字时间戳折叠为逐行
        val result = if (!SettingsLibrary.EnableWordByWordLyric) {
            filteredPairs.map { line ->
                line.copy(
                    tokens = listOf(
                        YosLyricToken(
                            startMs = line.startMs,
                            endMs = line.endMs.coerceAtLeast(line.startMs),
                            text = line.text
                        )
                    )
                )
            }
        } else filteredPairs
        return processOtherSide(result)
    }

    fun formatPlainLyricEntries(lyricText: String): List<YosLyricLine> {
        val text = lyricText.lines()
            .map { cleanLyricText(it) }
            .filter { it.isNotBlank() && it != "//" }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
            ?: return emptyList()
        return processOtherSide(
            listOf(
                YosLyricLine(
                    startMs = 0f,
                    endMs = 0f,
                    tokens = listOf(YosLyricToken(0f, 0f, text))
                )
            )
        )
    }

    private data class LyricSegment(val startMs: Float, val endMs: Float, val text: String)
    private data class MutableTtmlSpan(val startMs: Float, val endMs: Float, val text: StringBuilder = StringBuilder())

    private val timeTextRegex = Regex("""\d{1,3}:\d{2}(?:\.\d{1,3})?""")
    private val lrcTimeTagRegex = Regex("""\[(\d{1,3}:\d{2}(?:\.\d{1,3})?)]""")
    private val inlineTimeTagRegex = Regex("""[<\[](\d{1,3}:\d{2}(?:\.\d{1,3})?)[>\]]""")
    private val offsetRegex = Regex("""(?m)^\s*\[offset\s*:\s*([+-]?\d+)]\s*$""", RegexOption.IGNORE_CASE)
    private val metadataRegex = Regex("""^\[(ti|ar|al|by|length|offset|re|ve|la|_|tool|offset)\s*:.*]$""", RegexOption.IGNORE_CASE)
    private val keyValueRegex = Regex("""^[A-Za-z_]+\s*[=＝:：].*""", RegexOption.IGNORE_CASE)
    private val yrcLineRegex = Regex("""\[(\d+),(\d+)]""")
    private val yrcWordRegex = Regex("""\((\d+),(\d+)(?:,\d+)?\)([^()]*)""")
    private val yrcFormattedLineRegex = Regex("""(?m)^\s*\[\d+,\d+]\s*\(\d+,\d+(?:,\d+)?\)""")
    private val qrcLineRegex = Regex("""\[(\d+),(\d+)]""")
    private val qrcWordRegex = Regex("""([^()\[\]]+?)\((\d+),(\d+)\)""")
    private val qrcFormattedLineRegex = Regex("""(?m)^\s*\[\d+,\d+]\s*[^()\[\]\r\n]+?\(\d+,\d+\)""")
    private val krcLineRegex = Regex("""\[(\d+),(\d+)]""")
    private val krcWordRegex = Regex("""<(\d+),(\d+)(?:,\d+)?>([^<]*)""")
    private val krcFormattedLineRegex = Regex("""(?m)^\s*\[\d+,\d+]\s*<\d+,\d+(?:,\d+)?>""")
    private val lyricifyLineRegex = Regex("""(?m)^\s*\[(\d+),(\d+)]([^\r\n]*)$""")
    private val lyricifySyllableLineRegex = Regex("""(?m)^\s*\[\d]\s*.+?\(\d+,\d+\)""")
    private val lyricifySyllableLineHeadRegex = Regex("""^\s*\[(\d)]""")
    private val ttmlAttrRegex = Regex("""([\w:.-]+)\s*=\s*["']([^"']*)["']""")
    private val ttmlPRegex = Regex("""<p\b([^>]*)>(.*?)</p\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ttmlSpanRegex = Regex(
        """<span\b(?=[^>]*(?:\bbegin\b|\bend\b|\bdur\b)\s*=)([^>]*)>(.*?)</span\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private fun parseLrcLike(text: String): List<YosLyricLine> {
        val rawLines = text.lines().filter { raw ->
            val line = raw.trim()
            line.isNotEmpty() && !metadataRegex.matches(line) &&
                !(lrcTimeTagRegex.find(line) == null && keyValueRegex.matches(line))
        }
        val parsed = mutableListOf<YosLyricLine>()
        rawLines.forEach { rawLine ->
            val line = rawLine.trim().replace(Regex("([\\[\\]]){2,}"), "$1")
            val leadingTags = mutableListOf<MatchResult>()
            var nextStart = 0
            while (nextStart < line.length) {
                val match = lrcTimeTagRegex.find(line, nextStart) ?: break
                if (match.range.first != nextStart) break
                leadingTags += match
                nextStart = match.range.last + 1
            }
            if (leadingTags.isEmpty()) return@forEach
            val contentStart = leadingTags.last().range.last + 1
            val content = line.substring(contentStart)
            val lineTimes = leadingTags.mapNotNull { parseLrcTimeMs(it.groupValues[1]) }
            if (lineTimes.isEmpty()) return@forEach
            lineTimes.forEach { lineStart ->
                val pairs = if (inlineTimeTagRegex.containsMatchIn(content)) {
                    parseInlineTimedLine(lineStart, content)
                } else {
                    lineOf(lineStart, listOf(LyricSegment(lineStart, lineStart, cleanLyricText(content))), "")
                }
                addOrMergeLine(parsed, pairs)
            }
        }
        return parsed
    }

    private fun parseInlineTimedLine(lineStart: Float, content: String): YosLyricLine {
        val markers = inlineTimeTagRegex.findAll(content).toList()
        val segments = mutableListOf<LyricSegment>()
        if (markers.isEmpty()) return lineOf(lineStart, listOf(LyricSegment(lineStart, lineStart, cleanLyricText(content))), "")

        val prefix = content.substring(0, markers.first().range.first)
        if (prefix.isNotBlank()) {
            val firstTime = parseLrcTimeMs(markers.first().groupValues[1]) ?: lineStart
            segments += LyricSegment(lineStart, firstTime, cleanLyricText(prefix))
        }

        markers.forEachIndexed { index, marker ->
            val start = parseLrcTimeMs(marker.groupValues[1]) ?: return@forEachIndexed
            val textStart = marker.range.last + 1
            val textEnd = markers.getOrNull(index + 1)?.range?.first ?: content.length
            val lyric = cleanLyricText(content.substring(textStart, textEnd))
            if (lyric.isNotBlank()) {
                val end = parseLrcTimeMs(markers.getOrNull(index + 1)?.groupValues?.get(1) ?: "")
                    ?: (start + estimateSegmentDuration(lyric))
                segments += LyricSegment(start, end.coerceAtLeast(start), lyric)
            }
        }
        return lineOf(lineStart, segments, "")
    }

    private fun parseKrc(text: String): List<YosLyricLine> =
        text.lines().mapNotNull { rawLine ->
            val line = rawLine.trim()
            val lineMatch = krcLineRegex.find(line) ?: return@mapNotNull null
            if (lineMatch.range.first != 0) return@mapNotNull null
            val lineStart = lineMatch.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val lineDuration = lineMatch.groupValues[2].toFloatOrNull() ?: 0f
            val body = line.substring(lineMatch.range.last + 1)
            val segments = krcWordRegex.findAll(body).mapNotNull { word ->
                val rawStart = word.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val duration = word.groupValues[2].toFloatOrNull() ?: 0f
                val start = normalizeRelativeTime(lineStart, rawStart)
                val lyric = cleanLyricText(word.groupValues[3])
                if (lyric.isBlank()) null else LyricSegment(start, start + duration, lyric)
            }.toList()
            val fallbackText = cleanLyricText(body.replace(krcWordRegex, ""))
            when {
                segments.isNotEmpty() -> lineOf(lineStart, segments, "")
                fallbackText.isNotBlank() -> lineOf(lineStart, listOf(LyricSegment(lineStart, lineStart + lineDuration, fallbackText)), "")
                else -> null
            }
        }

    private fun parseLyricifyLines(text: String): List<YosLyricLine> =
        text.lines().mapNotNull { rawLine ->
            val line = rawLine.trim()
            val match = lyricifyLineRegex.matchEntire(line) ?: return@mapNotNull null
            val lineStart = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val lineEnd = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            val lyric = cleanLyricText(match.groupValues[3])
            if (lyric.isBlank()) null else lineOf(lineStart, listOf(LyricSegment(lineStart, lineEnd.coerceAtLeast(lineStart), lyric)), "")
        }

    private fun parseLyricifySyllable(text: String): List<YosLyricLine> =
        text.lines().mapNotNull { rawLine ->
            val line = rawLine.trim()
            val head = lyricifySyllableLineHeadRegex.find(line) ?: return@mapNotNull null
            if (head.range.first != 0) return@mapNotNull null
            val body = line.substring(head.range.last + 1)
            val segments = qrcWordRegex.findAll(body).mapNotNull { word ->
                val lyric = cleanLyricText(word.groupValues[1])
                val start = word.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
                val duration = word.groupValues[3].toFloatOrNull() ?: 0f
                if (lyric.isBlank()) null else LyricSegment(start, start + duration, lyric)
            }.toList()
            val lineStart = segments.firstOrNull()?.startMs ?: return@mapNotNull null
            if (segments.isEmpty()) null else lineOf(lineStart, segments, "")
        }

    private fun parseYrc(text: String): List<YosLyricLine> =
        text.lines().mapNotNull { line ->
            val lineMatch = yrcLineRegex.find(line) ?: return@mapNotNull null
            val lineStart = lineMatch.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val lineDuration = lineMatch.groupValues[2].toFloatOrNull() ?: 0f
            val body = line.substring(lineMatch.range.last + 1)
            val segments = yrcWordRegex.findAll(body).mapNotNull { word ->
                val rawStart = word.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val duration = word.groupValues[2].toFloatOrNull() ?: 0f
                val lyric = cleanLyricText(word.groupValues[3])
                if (lyric.isBlank()) null else LyricSegment(rawStart, rawStart + duration, lyric)
            }.toList()
            val fallbackText = cleanLyricText(body.replace(yrcWordRegex, ""))
            when {
                segments.isNotEmpty() -> lineOf(lineStart, segments, "")
                fallbackText.isNotBlank() -> lineOf(lineStart, listOf(LyricSegment(lineStart, lineStart + lineDuration, fallbackText)), "")
                else -> null
            }
        }

    private fun parseQrc(text: String): List<YosLyricLine> =
        text.lines().mapNotNull { line ->
            val lineMatch = qrcLineRegex.find(line) ?: return@mapNotNull null
            val lineStart = lineMatch.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val lineDuration = lineMatch.groupValues[2].toFloatOrNull() ?: 0f
            val body = line.substring(lineMatch.range.last + 1)
            val suffixSegments = qrcWordRegex.findAll(body).mapNotNull { word ->
                val lyric = cleanLyricText(word.groupValues[1])
                val start = word.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
                val duration = word.groupValues[3].toFloatOrNull() ?: 0f
                if (lyric.isBlank()) null else LyricSegment(start, start + duration, lyric)
            }.toList()
            val prefixSegments = yrcWordRegex.findAll(body).mapNotNull { word ->
                val start = word.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val duration = word.groupValues[2].toFloatOrNull() ?: 0f
                val lyric = cleanLyricText(word.groupValues[3])
                if (lyric.isBlank()) null else LyricSegment(start, start + duration, lyric)
            }.toList()
            val segments = if (suffixSegments.isNotEmpty()) suffixSegments else prefixSegments
            val fallbackText = cleanLyricText(body.replace(qrcWordRegex, "").replace(yrcWordRegex, ""))
            when {
                segments.isNotEmpty() -> lineOf(lineStart, segments, "")
                fallbackText.isNotBlank() -> lineOf(lineStart, listOf(LyricSegment(lineStart, lineStart + lineDuration, fallbackText)), "")
                else -> null
            }
        }

    private fun parseTtml(text: String): List<YosLyricLine> =
        parseTtmlXml(text).ifEmpty { parseTtmlRegex(text) }

    private fun parseTtmlXml(text: String): List<YosLyricLine> = runCatching {
        val parser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }.newPullParser()
        parser.setInput(StringReader(text))

        val lines = mutableListOf<YosLyricLine>()
        val divStartStack = mutableListOf(0f)
        var inParagraph = false
        var lineStart = 0f
        var lineEnd = 0f
        var plainText = StringBuilder()
        var segments = mutableListOf<LyricSegment>()
        val spanStack = mutableListOf<MutableTtmlSpan?>()

        fun currentDivStart(): Float = divStartStack.lastOrNull() ?: 0f
        fun resolveTime(raw: String?, base: Float): Float? =
            parseTtmlTimeMs(raw)?.let { normalizeRelativeTime(base, it) }

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "div" -> {
                            val base = currentDivStart()
                            divStartStack += resolveTime(parser.attrValue("begin"), base) ?: base
                        }
                        "p" -> {
                            val base = currentDivStart()
                            lineStart = resolveTime(parser.attrValue("begin"), base) ?: base
                            lineEnd = resolveTime(parser.attrValue("end"), lineStart)
                                ?: (lineStart + (parseTtmlTimeMs(parser.attrValue("dur")) ?: 0f))
                            inParagraph = true
                            plainText = StringBuilder()
                            segments = mutableListOf()
                            spanStack.clear()
                        }
                        "span" -> {
                            if (inParagraph) {
                                val start = resolveTime(parser.attrValue("begin"), lineStart)
                                val end = resolveTime(parser.attrValue("end"), lineStart)
                                    ?: (start?.let { it + (parseTtmlTimeMs(parser.attrValue("dur")) ?: 0f) })
                                spanStack += if (start != null && end != null) {
                                    MutableTtmlSpan(start, end.coerceAtLeast(start))
                                } else {
                                    null
                                }
                            }
                        }
                        "br" -> {
                            if (inParagraph) {
                                plainText.append('\n')
                                spanStack.lastOrNull { it != null }?.text?.append('\n')
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inParagraph) {
                        val value = parser.text.orEmpty()
                        plainText.append(value)
                        spanStack.lastOrNull { it != null }?.text?.append(value)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "span" -> {
                            if (inParagraph && spanStack.isNotEmpty()) {
                                val span = spanStack.removeAt(spanStack.lastIndex)
                                if (span != null) {
                                    val lyric = cleanLyricText(span.text.toString())
                                    if (lyric.isNotBlank()) segments += LyricSegment(span.startMs, span.endMs, lyric)
                                }
                            }
                        }
                        "p" -> {
                            if (inParagraph) {
                                val plain = cleanLyricText(plainText.toString())
                                when {
                                    segments.isNotEmpty() -> lines += lineOf(lineStart, segments, "")
                                    plain.isNotBlank() -> lines += lineOf(
                                        lineStart,
                                        listOf(LyricSegment(lineStart, lineEnd.coerceAtLeast(lineStart), plain)),
                                        ""
                                    )
                                }
                                inParagraph = false
                                spanStack.clear()
                            }
                        }
                        "div" -> {
                            if (divStartStack.size > 1) divStartStack.removeAt(divStartStack.lastIndex)
                        }
                    }
                }
            }
        }

        lines
    }.getOrElse { emptyList() }

    private fun parseTtmlRegex(text: String): List<YosLyricLine> =
        ttmlPRegex.findAll(text).mapNotNull { p ->
            val pAttrs = parseXmlAttrs(p.groupValues[1])
            val body = p.groupValues[2]
            val lineStart = parseTtmlTimeMs(pAttrs["begin"]) ?: 0f
            val lineEnd = parseTtmlTimeMs(pAttrs["end"]) ?: (lineStart + (parseTtmlTimeMs(pAttrs["dur"]) ?: 0f))
            val spans = ttmlSpanRegex.findAll(body).mapNotNull { span ->
                val attrs = parseXmlAttrs(span.groupValues[1])
                val lyric = cleanLyricText(stripXml(span.groupValues[2]))
                if (lyric.isBlank()) return@mapNotNull null
                val start = parseTtmlTimeMs(attrs["begin"])?.let { normalizeRelativeTime(lineStart, it) } ?: lineStart
                val end = parseTtmlTimeMs(attrs["end"])
                    ?: (start + (parseTtmlTimeMs(attrs["dur"]) ?: estimateSegmentDuration(lyric)))
                LyricSegment(start, end.coerceAtLeast(start), lyric)
            }.toList()
            val plain = cleanLyricText(stripXml(body))
            when {
                spans.isNotEmpty() -> lineOf(lineStart, spans, "")
                plain.isNotBlank() -> lineOf(lineStart, listOf(LyricSegment(lineStart, lineEnd.coerceAtLeast(lineStart), plain)), "")
                else -> null
            }
        }.toList()

    private fun XmlPullParser.attrValue(name: String): String? {
        for (index in 0 until attributeCount) {
            if (getAttributeName(index).substringAfter(":") == name) return getAttributeValue(index)
        }
        return null
    }

    private fun lineOf(
        lineStart: Float,
        segments: List<LyricSegment>,
        translation: String
    ): YosLyricLine {
        val tokens = segments
            .sortedBy { it.startMs }
            .filter { it.text.isNotBlank() }
            .map { segment ->
                YosLyricToken(
                    startMs = segment.startMs.coerceAtLeast(lineStart),
                    endMs = segment.endMs.coerceAtLeast(segment.startMs).coerceAtLeast(lineStart),
                    text = segment.text
                )
            }
        val endMs = tokens.maxOfOrNull { it.endMs } ?: lineStart
        return YosLyricLine(
            startMs = lineStart,
            endMs = endMs.coerceAtLeast(lineStart),
            tokens = tokens,
            translation = translation
        )
    }

    private fun extractOffsetMs(text: String): Float =
        offsetRegex.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f

    private fun applyOffset(
        lines: List<YosLyricLine>,
        offsetMs: Float
    ): List<YosLyricLine> {
        if (offsetMs == 0f) return lines
        return lines.map { line ->
            val startMs = (line.startMs - offsetMs).coerceAtLeast(0f)
            line.copy(
                startMs = startMs,
                endMs = (line.endMs - offsetMs).coerceAtLeast(startMs),
                tokens = line.tokens.map { token ->
                    val tokenStart = (token.startMs - offsetMs).coerceAtLeast(0f)
                    token.copy(
                        startMs = tokenStart,
                        endMs = (token.endMs - offsetMs).coerceAtLeast(tokenStart)
                    )
                }
            )
        }
    }

    private fun addOrMergeLine(lines: MutableList<YosLyricLine>, line: YosLyricLine) {
        val lineStart = line.startMs
        val existingIndex = lines.indexOfFirst { abs(it.startMs - lineStart) < 1f }
        if (existingIndex >= 0) {
            val existing = lines[existingIndex]
            val translationText = line.text
            if (translationText.isNotBlank()) {
                lines[existingIndex] = existing.copy(translation = translationText)
            }
        } else {
            lines += line
        }
    }

    private fun normalizeParsedLines(lines: List<YosLyricLine>): List<YosLyricLine> =
        lines.asSequence()
            .map { line ->
                val tokens = line.tokens.map { token ->
                    token.copy(text = cleanLyricText(token.text))
                }.filter { it.text.isNotBlank() }
                val startMs = line.startMs.coerceAtLeast(0f)
                val endMs = (tokens.maxOfOrNull { it.endMs } ?: line.endMs).coerceAtLeast(startMs)
                line.copy(
                    startMs = startMs,
                    endMs = endMs,
                    tokens = tokens,
                    translation = cleanLyricText(line.translation)
                )
            }
            .filter { line ->
                val text = line.text.trim().uppercase()
                text.isNotBlank() && !text.startsWith("TITLE=") && !text.startsWith("ARTIST=") &&
                    !text.startsWith("ALBUM=") && !text.startsWith("GENRE=") &&
                    !text.startsWith("DATE=") && !text.startsWith("YEAR=") &&
                    !text.startsWith("TRACK=") && !text.startsWith("COMPOSER=") &&
                    !text.startsWith("WRITER=") && !text.startsWith("ENCODER=") &&
                    !text.startsWith("LENGTH=")
            }
            .sortedBy { it.startMs }
            .toList()

    private fun parseLrcTimeMs(raw: String): Float? {
        if (!timeTextRegex.matches(raw)) return null
        val parts = raw.split(":")
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val secPart = parts.getOrNull(1) ?: return null
        val seconds = secPart.substringBefore(".").toIntOrNull() ?: return null
        val fraction = secPart.substringAfter(".", "").take(3).padEnd(3, '0').toIntOrNull() ?: 0
        return (minutes * 60_000 + seconds * 1000 + fraction).toFloat()
    }

    private fun parseTtmlTimeMs(raw: String?): Float? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.endsWith("ms")) return value.removeSuffix("ms").toFloatOrNull()
        if (value.endsWith("s")) return value.removeSuffix("s").toFloatOrNull()?.times(1000f)
        if (value.matches(Regex("""\d+(?:\.\d+)?"""))) return value.toFloatOrNull()?.times(1000f)
        val parts = value.split(":")
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toFloatOrNull() ?: return null
                val minutes = parts[1].toFloatOrNull() ?: return null
                val seconds = parts[2].toFloatOrNull() ?: return null
                ((hours * 3600 + minutes * 60 + seconds) * 1000f)
            }
            2 -> parseLrcTimeMs(value)
            else -> null
        }
    }

    private fun normalizeRelativeTime(lineStart: Float, rawStart: Float): Float =
        if (rawStart < lineStart && rawStart < 60_000f) lineStart + rawStart else rawStart

    private fun estimateSegmentDuration(text: String): Float =
        (text.length.coerceAtLeast(1) * 220f).coerceIn(180f, 1600f)

    private fun cleanLyricText(raw: String): String {
        val text = decodeXmlEntities(raw)
            .replace("//", "")
            .replace(Regex("(?!\\n)\\s+"), " ")
        return if (formatText) text.trim() else text
    }

    private fun parseXmlAttrs(raw: String): Map<String, String> =
        ttmlAttrRegex.findAll(raw).associate { it.groupValues[1].substringAfter(":") to it.groupValues[2] }

    private fun stripXml(raw: String): String =
        raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")

    private fun decodeXmlEntities(raw: String): String =
        raw.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")

    /**
     * 将翻译文本合并到已解析的歌词数据中。
     * 翻译文本可以是 LRC 格式（含时间标签）或纯文本（每行一句翻译）。
     * 合并后，每行歌词的最后一个元素将为翻译文本。
     */
    fun mergeTranslation(
        lrcEntries: List<YosLyricLine>,
        translationText: String
    ): List<YosLyricLine> {
        // 解析翻译 LRC 文本
        val translationLines = translationText.lines()
            .mapNotNull { line ->
                val timeIndex = line.indexOf("[")
                val timeAfter = line.indexOf("]")
                if (timeIndex != -1 && timeAfter != -1) {
                    val timeText = line.substring(timeIndex + 1, timeAfter)
                    val timeParts = timeText.split(":")
                    if (timeParts.size == 2) {
                        val minutes = timeParts[0].toIntOrNull()
                        val seconds = timeParts[1].toFloatOrNull()
                        if (minutes != null && seconds != null) {
                            val time = (minutes * 60 + seconds) * 1000f
                            val text = line.substring(timeAfter + 1).trim()
                            if (text.isNotEmpty() && text != "//") {
                                return@mapNotNull time to text
                            }
                        }
                    }
                }
                null
            }

        return if (translationLines.isNotEmpty()) {
            // LRC 格式的翻译：按时间戳匹配
            lrcEntries.map { line ->
                val lineTime = line.startMs
                val matchedTranslation = translationLines
                    .filter { abs(it.first - lineTime) < 50f }
                    .minByOrNull { abs(it.first - lineTime) }
                if (matchedTranslation != null) {
                    line.copy(translation = matchedTranslation.second)
                } else {
                    line
                }
            }
        } else {
            // 尝试作为纯文本翻译处理（每行对应一句歌词）
            val plainLines = translationText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "//" }
            if (plainLines.isEmpty()) return lrcEntries
            lrcEntries.mapIndexed { index, line ->
                if (index < plainLines.size) {
                    line.copy(translation = plainLines[index])
                } else {
                    line
                }
            }
        }
    }

    private fun processOtherSide(lrcEntries: List<YosLyricLine>): List<YosLyricLine> {
        fun normalizeSinger(raw: String): String = raw.trim().trimEnd(':', '：').trim()
        fun isChorusSinger(singer: String): Boolean = singer == "合" || singer == "合唱" || singer.equals("ALL", ignoreCase = true)

        // Count duet markers - only enable duet mode if there are multiple markers
        val duetMarkerCount = lrcEntries.count { lines ->
            val lyric = lines.text + lines.translation
            lyric.trimEnd().endsWith(":") || lyric.trimEnd().endsWith("：") ||
                (lines.tokens.size > 1 && lines.tokens.firstOrNull()?.text?.matches(Regex(".+\\s*:\\s*")) == true)
        }
        val isDuetSong = duetMarkerCount >= 3

        val otherSideResult = mutableStateListOf<Boolean>()
        var otherSide = false
        var lastSinger: String? = null
        var otherSideFirstTime = false

        val filteredLrcEntries = lrcEntries.map { lines ->
            val lyric = lines.text + lines.translation
            var deleteType = -1

            if (isDuetSong && (lyric.trimEnd().endsWith(":") || lyric.trimEnd().endsWith("："))) {
                val singer = normalizeSinger(lyric)
                if (singer.isNotEmpty() && !isChorusSinger(singer)) {
                    if (lastSinger == null || lastSinger != singer) {
                        if (otherSideFirstTime) otherSide = !otherSide else otherSideFirstTime = true
                        lastSinger = singer
                    }
                }
            } else if (isDuetSong && lines.tokens.isNotEmpty()) {
                val currentSinger = lines.tokens.first().text
                if (currentSinger.matches(Regex(".+\\s*:\\s*"))) {
                    deleteType = 0
                    val singer = normalizeSinger(currentSinger)
                    if (singer.isNotEmpty() && !isChorusSinger(singer)) {
                        if (lastSinger != null && lastSinger == singer) {
                        } else {
                            if (otherSideFirstTime) otherSide = !otherSide
                            else otherSideFirstTime = true
                        }
                        lastSinger = singer
                    }
                }
            }

            if (!isDuetSong) otherSide = false
            otherSideResult.add(otherSide)

            if (isDuetSong) {
                val tokens = if (deleteType == 0) lines.tokens.drop(1) else lines.tokens
                lines.copy(tokens = tokens, otherSide = otherSide)
            } else {
                lines.copy(otherSide = false)
            }
        }

        // 复制一份后更新到主线程状态列表
        // 若已在主线程则直接更新，确保在 lrcEntries 更新前完成，避免 remember 缓存旧值
        val snapshot = otherSideResult.toList()
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            MediaViewModelObject.otherSideForLines.clear()
            MediaViewModelObject.otherSideForLines.addAll(snapshot)
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                MediaViewModelObject.otherSideForLines.clear()
                MediaViewModelObject.otherSideForLines.addAll(snapshot)
            }
        }
        return filteredLrcEntries
    }
}

/*
private fun String.ifNeedMirror(): Boolean {
    val directionality = Character.getDirectionality(this.trim().first())
    return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}*/
