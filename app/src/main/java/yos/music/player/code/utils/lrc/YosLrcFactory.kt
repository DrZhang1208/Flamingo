package yos.music.player.code.utils.lrc

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
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
        // Parser always preserves source timing. Display preferences are applied by
        // the lyric renderer and never destroy cached word-level information.
        return filteredPairs
    }

    fun formatPlainLyricEntries(lyricText: String): List<YosLyricLine> {
        val text = lyricText.lines()
            .map { cleanLyricText(it) }
            .filter { it.isNotBlank() && it != "//" }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
            ?: return emptyList()
        return listOf(
                YosLyricLine(
                    startMs = 0f,
                    endMs = 0f,
                    tokens = listOf(YosLyricToken(0f, 0f, text))
                )
            )
    }

    private data class LyricSegment(val startMs: Float, val endMs: Float, val text: String)
    private data class MutableTtmlSpan(
        val startMs: Float?,
        val endMs: Float?,
        val role: String?,
        val text: StringBuilder = StringBuilder()
    )

    private val timeTextRegex = Regex("""\d{1,3}:\d{2}(?:\.\d{1,3})?""")
    private val lrcTimeTagRegex = Regex("""\[(\d{1,3}:\d{2}(?:\.\d{1,3})?)]""")
    private val inlineTimeTagRegex = Regex("""[<\[](\d{1,3}:\d{2}(?:\.\d{1,3})?)[>\]]""")
    private val agentPrefixRegex = Regex("""^(v\d+)\s*[:：]\s*""", RegexOption.IGNORE_CASE)
    private val backgroundLineRegex = Regex(
        """^\[bg:\s*(.*)]\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
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
    private val ttmlAnySpanRegex = Regex(
        """<span\b([^>]*)>(.*?)</span\s*>""",
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
            // Collapse malformed repeated opening/closing brackets independently.
            // The old character-class regex also collapsed the valid `][` boundary
            // between two timestamps and made multi-timestamp LRC lines unparsable.
            val line = rawLine.trim()
                .replace(Regex("""\[{2,}"""), "[")
                .replace(Regex("""]{2,}"""), "]")
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
            val lineTimes = leadingTags.mapNotNull { parseLrcTimeMs(it.groupValues[1]) }
            if (lineTimes.isEmpty()) return@forEach
            val rawContent = line.substring(contentStart)
            val backgroundContent = backgroundLineRegex.matchEntire(rawContent.trim())
                ?.groupValues
                ?.getOrNull(1)
            if (backgroundContent != null) {
                val lineStart = lineTimes.first()
                val background = if (inlineTimeTagRegex.containsMatchIn(backgroundContent)) {
                    parseInlineTimedLine(lineStart, backgroundContent)
                } else {
                    lineOf(
                        lineStart,
                        listOf(LyricSegment(lineStart, lineStart, cleanLyricText(backgroundContent))),
                        "",
                        "bg"
                    )
                }.copy(agent = "bg", otherSide = true)
                if (parsed.isNotEmpty() && background.text.isNotBlank()) {
                    val ownerIndex = parsed.lastIndex
                    parsed[ownerIndex] = parsed[ownerIndex].copy(
                        otherLrcEntries = parsed[ownerIndex].otherLrcEntries + background
                    )
                }
                return@forEach
            }
            val agentMatch = agentPrefixRegex.find(rawContent.trimStart())
            val agent = agentMatch?.groupValues?.get(1)?.lowercase() ?: YosLyricLine.DEFAULT_AGENT
            val content = if (agentMatch != null) {
                rawContent.trimStart().substring(agentMatch.range.last + 1)
            } else {
                rawContent
            }
            lineTimes.forEach { lineStart ->
                val pairs = if (content.isBlank()) {
                    blankLine(lineStart)
                } else if (inlineTimeTagRegex.containsMatchIn(content)) {
                    parseInlineTimedLine(lineStart, content)
                } else {
                    lineOf(lineStart, listOf(LyricSegment(lineStart, lineStart, cleanLyricText(content))), "", agent)
                }
                addOrMergeLine(parsed, pairs.copy(agent = agent, otherSide = agent != YosLyricLine.DEFAULT_AGENT))
            }
        }
        return parsed
    }

    private fun parseInlineTimedLine(lineStart: Float, content: String): YosLyricLine {
        val markers = inlineTimeTagRegex.findAll(content).toList()
        val segments = mutableListOf<LyricSegment>()
        if (markers.isEmpty()) return lineOf(lineStart, listOf(LyricSegment(lineStart, lineStart, cleanLyricText(content))), "")

        val prefix = content.substring(0, markers.first().range.first)
        if (prefix.isNotEmpty()) {
            val firstTime = parseLrcTimeMs(markers.first().groupValues[1]) ?: lineStart
            segments += LyricSegment(lineStart, firstTime, cleanLyricTokenText(prefix))
        }

        markers.forEachIndexed { index, marker ->
            val start = parseLrcTimeMs(marker.groupValues[1]) ?: return@forEachIndexed
            val textStart = marker.range.last + 1
            val textEnd = markers.getOrNull(index + 1)?.range?.first ?: content.length
            val lyric = cleanLyricTokenText(content.substring(textStart, textEnd))
            // A space between timestamps still has layout width, even when
            // both timestamps are equal. Only an empty marker has no text.
            if (lyric.isNotEmpty()) {
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
                val lyric = cleanLyricTokenText(word.groupValues[3])
                if (lyric.isBlank()) null else LyricSegment(start, start + duration, lyric)
            }.toList()
            val fallbackText = cleanLyricText(body.replace(krcWordRegex, ""))
            when {
                segments.isNotEmpty() -> lineOf(lineStart, segments, "")
                fallbackText.isNotBlank() -> lineOf(lineStart, listOf(LyricSegment(lineStart, lineStart + lineDuration, fallbackText)), "")
                lineDuration > 0f -> blankLine(lineStart, lineStart + lineDuration)
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
            if (lyric.isBlank()) blankLine(lineStart, lineEnd.coerceAtLeast(lineStart))
            else lineOf(lineStart, listOf(LyricSegment(lineStart, lineEnd.coerceAtLeast(lineStart), lyric)), "")
        }

    private fun parseLyricifySyllable(text: String): List<YosLyricLine> =
        text.lines().mapNotNull { rawLine ->
            val line = rawLine.trim()
            val head = lyricifySyllableLineHeadRegex.find(line) ?: return@mapNotNull null
            if (head.range.first != 0) return@mapNotNull null
            val body = line.substring(head.range.last + 1)
            val segments = qrcWordRegex.findAll(body).mapNotNull { word ->
                val lyric = cleanLyricTokenText(word.groupValues[1])
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
                val lyric = cleanLyricTokenText(word.groupValues[3])
                if (lyric.isBlank()) null else LyricSegment(rawStart, rawStart + duration, lyric)
            }.toList()
            val fallbackText = cleanLyricText(body.replace(yrcWordRegex, ""))
            when {
                segments.isNotEmpty() -> lineOf(lineStart, segments, "")
                fallbackText.isNotBlank() -> lineOf(lineStart, listOf(LyricSegment(lineStart, lineStart + lineDuration, fallbackText)), "")
                lineDuration > 0f -> blankLine(lineStart, lineStart + lineDuration)
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
                val lyric = cleanLyricTokenText(word.groupValues[1])
                val start = word.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
                val duration = word.groupValues[3].toFloatOrNull() ?: 0f
                if (lyric.isBlank()) null else LyricSegment(start, start + duration, lyric)
            }.toList()
            val prefixSegments = yrcWordRegex.findAll(body).mapNotNull { word ->
                val start = word.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val duration = word.groupValues[2].toFloatOrNull() ?: 0f
                val lyric = cleanLyricTokenText(word.groupValues[3])
                if (lyric.isBlank()) null else LyricSegment(start, start + duration, lyric)
            }.toList()
            val segments = if (suffixSegments.isNotEmpty()) suffixSegments else prefixSegments
            val fallbackText = cleanLyricText(body.replace(qrcWordRegex, "").replace(yrcWordRegex, ""))
            when {
                segments.isNotEmpty() -> lineOf(lineStart, segments, "")
                fallbackText.isNotBlank() -> lineOf(lineStart, listOf(LyricSegment(lineStart, lineStart + lineDuration, fallbackText)), "")
                lineDuration > 0f -> blankLine(lineStart, lineStart + lineDuration)
                else -> null
            }
        }

    private fun parseTtml(text: String): List<YosLyricLine> =
        parseTtmlXml(text).ifEmpty { parseTtmlRegex(text) }.map { line ->
            line.copy(ttmlEffects = true, otherLrcEntries = line.otherLrcEntries.map { it.copy(ttmlEffects = true) })
        }

    private fun parseTtmlXml(text: String): List<YosLyricLine> = runCatching {
        val parser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }.newPullParser()
        parser.setInput(StringReader(text))

        val lines = mutableListOf<YosLyricLine>()
        val divStartStack = mutableListOf(0f)
        val divAgentStack = mutableListOf(YosLyricLine.DEFAULT_AGENT)
        var inParagraph = false
        var lineStart = 0f
        var lineEnd = 0f
        var lineAgent = YosLyricLine.DEFAULT_AGENT
        var plainText = StringBuilder()
        var translationText = StringBuilder()
        var romajiText = StringBuilder()
        var backgroundText = StringBuilder()
        var segments = mutableListOf<LyricSegment>()
        var backgroundSegments = mutableListOf<LyricSegment>()
        val spanStack = mutableListOf<MutableTtmlSpan>()

        fun currentDivStart(): Float = divStartStack.lastOrNull() ?: 0f
        fun resolveTime(raw: String?, base: Float): Float? =
            parseTtmlTimeMs(raw)?.let { normalizeRelativeTime(base, it) }
        fun currentSpecialRole(): String? = spanStack.asSequence()
            .mapNotNull { it.role?.lowercase() }
            .firstOrNull { it == "x-translation" || it == "x-bg" || it == "x-romaji" }
        fun currentTimedSpan(): MutableTtmlSpan? = spanStack.lastOrNull { it.startMs != null && it.endMs != null }
        fun appendInlineTextToLastSegment(raw: String) {
            if (segments.isEmpty() || !isInlineTtmlText(raw)) return
            val text = cleanLyricTokenText(raw)
            if (text.isEmpty()) return
            val lastIndex = segments.lastIndex
            segments[lastIndex] = segments[lastIndex].copy(text = segments[lastIndex].text + text)
        }

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "div" -> {
                            val base = currentDivStart()
                            divStartStack += resolveTime(parser.attrValue("begin"), base) ?: base
                            divAgentStack += parser.attrValue("agent") ?: divAgentStack.last()
                        }
                        "p" -> {
                            val base = currentDivStart()
                            lineStart = resolveTime(parser.attrValue("begin"), base) ?: base
                            lineEnd = resolveTime(parser.attrValue("end"), lineStart)
                                ?: (lineStart + (parseTtmlTimeMs(parser.attrValue("dur")) ?: 0f))
                            lineAgent = parser.attrValue("agent") ?: divAgentStack.last()
                            inParagraph = true
                            plainText = StringBuilder()
                            translationText = StringBuilder()
                            romajiText = StringBuilder()
                            backgroundText = StringBuilder()
                            segments = mutableListOf()
                            backgroundSegments = mutableListOf()
                            spanStack.clear()
                        }
                        "span" -> {
                            if (inParagraph) {
                                val start = resolveTime(parser.attrValue("begin"), lineStart)
                                val end = resolveTime(parser.attrValue("end"), lineStart)
                                    ?: (start?.let { it + (parseTtmlTimeMs(parser.attrValue("dur")) ?: 0f) })
                                spanStack += MutableTtmlSpan(
                                    startMs = start,
                                    endMs = if (start != null && end != null) end.coerceAtLeast(start) else null,
                                    role = parser.attrValue("role")
                                )
                            }
                        }
                        "br" -> {
                            if (inParagraph) {
                                plainText.append('\n')
                                spanStack.lastOrNull()?.text?.append('\n')
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inParagraph) {
                        val value = parser.text.orEmpty()
                        when (currentSpecialRole()) {
                            "x-translation" -> translationText.append(value)
                            "x-romaji" -> romajiText.append(value)
                            "x-bg" -> backgroundText.append(value)
                            else -> plainText.append(value)
                        }
                        // 特殊 role 的文本不进逐字 token，防止罗马音/翻译混入主歌词
                        val timedSpan = if (currentSpecialRole() == null) currentTimedSpan() else null
                        if (timedSpan != null) {
                            timedSpan.text.append(value)
                        } else if (currentSpecialRole() == null) {
                            appendInlineTextToLastSegment(value)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "span" -> {
                            if (inParagraph && spanStack.isNotEmpty()) {
                                val span = spanStack.removeAt(spanStack.lastIndex)
                                val lyric = cleanLyricTokenText(span.text.toString())
                                if (lyric.isNotBlank() && span.startMs != null && span.endMs != null) {
                                    val segment = LyricSegment(span.startMs, span.endMs, lyric)
                                    val role = span.role?.lowercase() ?: currentSpecialRole()
                                    if (role == "x-bg") backgroundSegments += segment
                                    else if (role != "x-translation" && role != "x-romaji") segments += segment
                                }
                            }
                        }
                        "p" -> {
                            if (inParagraph) {
                                val plain = cleanLyricText(plainText.toString())
                                val translation = cleanLyricText(translationText.toString())
                                val romaji = cleanLyricText(romajiText.toString())
                                val main = when {
                                    segments.isNotEmpty() -> lineOf(lineStart, segments, translation, lineAgent, romaji)
                                    plain.isNotBlank() -> lineOf(
                                        lineStart,
                                        listOf(LyricSegment(lineStart, lineEnd.coerceAtLeast(lineStart), plain)),
                                        translation,
                                        lineAgent,
                                        romaji
                                    )
                                    lineEnd > lineStart -> blankLine(lineStart, lineEnd)
                                    else -> null
                                }
                                if (main != null) {
                                    val backgroundPlain = cleanLyricText(backgroundText.toString())
                                    val background = when {
                                        backgroundSegments.isNotEmpty() -> lineOf(
                                            backgroundSegments.minOf { it.startMs },
                                            backgroundSegments,
                                            "",
                                            "bg"
                                        )
                                        backgroundPlain.isNotBlank() -> lineOf(
                                            lineStart,
                                            listOf(LyricSegment(lineStart, lineEnd, backgroundPlain)),
                                            "",
                                            "bg"
                                        )
                                        else -> null
                                    }
                                    lines += if (background == null) main else main.copy(otherLrcEntries = listOf(background))
                                }
                                inParagraph = false
                                spanStack.clear()
                            }
                        }
                        "div" -> {
                            if (divStartStack.size > 1) divStartStack.removeAt(divStartStack.lastIndex)
                            if (divAgentStack.size > 1) divAgentStack.removeAt(divAgentStack.lastIndex)
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
            val agent = pAttrs["agent"] ?: YosLyricLine.DEFAULT_AGENT
            val spans = mutableListOf<LyricSegment>()
            val backgroundSpans = mutableListOf<LyricSegment>()
            var translation = ""
            var romaji = ""
            ttmlAnySpanRegex.findAll(body).forEach { span ->
                val attrs = parseXmlAttrs(span.groupValues[1])
                val lyric = cleanLyricTokenText(stripXml(span.groupValues[2]))
                if (lyric.isBlank()) return@forEach
                val role = attrs["role"]?.lowercase()
                if (role == "x-translation") {
                    translation = cleanLyricText(lyric)
                    return@forEach
                }
                if (role == "x-romaji") {
                    romaji = cleanLyricText(lyric)
                    return@forEach
                }
                val start = parseTtmlTimeMs(attrs["begin"])?.let { normalizeRelativeTime(lineStart, it) } ?: lineStart
                val end = parseTtmlTimeMs(attrs["end"])
                    ?: (start + (parseTtmlTimeMs(attrs["dur"]) ?: estimateSegmentDuration(lyric)))
                val segment = LyricSegment(start, end.coerceAtLeast(start), lyric)
                if (role == "x-bg") backgroundSpans += segment else spans += segment
            }
            val plain = cleanLyricText(stripXml(body))
            val main = when {
                spans.isNotEmpty() -> lineOf(lineStart, spans, translation, agent, romaji)
                plain.isNotBlank() -> lineOf(
                    lineStart,
                    listOf(LyricSegment(lineStart, lineEnd.coerceAtLeast(lineStart), plain)),
                    translation,
                    agent,
                    romaji
                )
                lineEnd > lineStart -> blankLine(lineStart, lineEnd)
                else -> null
            }
            val background = backgroundSpans.takeIf { it.isNotEmpty() }?.let {
                lineOf(it.minOf(LyricSegment::startMs), it, "", "bg")
            }
            if (main != null && background != null) main.copy(otherLrcEntries = listOf(background)) else main
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
        translation: String,
        agent: String = YosLyricLine.DEFAULT_AGENT,
        romaji: String = ""
    ): YosLyricLine {
        val tokens = segments
            .sortedBy { it.startMs }
            .filter { it.text.isNotEmpty() }
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
            translation = translation,
            romaji = romaji,
            agent = agent,
            otherSide = agent != YosLyricLine.DEFAULT_AGENT
        )
    }

    private fun blankLine(startMs: Float, endMs: Float = startMs): YosLyricLine =
        YosLyricLine(
            startMs = startMs,
            endMs = endMs.coerceAtLeast(startMs),
            tokens = emptyList()
        )

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
                },
                otherLrcEntries = applyOffset(line.otherLrcEntries, offsetMs)
            )
        }
    }

    private fun addOrMergeLine(lines: MutableList<YosLyricLine>, line: YosLyricLine) {
        val lineStart = line.startMs
        val existingIndex = lines.indexOfFirst { abs(it.startMs - lineStart) < 1f }
        if (existingIndex >= 0) {
            val existing = lines[existingIndex]
            lines[existingIndex] = if (line.agent != existing.agent) {
                existing.copy(otherLrcEntries = existing.otherLrcEntries + line)
            } else if (line.text.isNotBlank()) {
                existing.copy(translation = line.text)
            } else existing
        } else {
            lines += line
        }
    }

    private fun normalizeParsedLines(lines: List<YosLyricLine>): List<YosLyricLine> {
        val normalized = lines.asSequence()
            .map { line ->
                val tokens = line.tokens.map { token ->
                    token.copy(text = cleanLyricTokenText(token.text))
                }.filter { it.text.isNotEmpty() }
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
                text.isBlank() || (!text.startsWith("TITLE=") && !text.startsWith("ARTIST=") &&
                    !text.startsWith("ALBUM=") && !text.startsWith("GENRE=") &&
                    !text.startsWith("DATE=") && !text.startsWith("YEAR=") &&
                    !text.startsWith("TRACK=") && !text.startsWith("COMPOSER=") &&
                    !text.startsWith("WRITER=") && !text.startsWith("ENCODER=") &&
                    !text.startsWith("LENGTH="))
            }
            .sortedBy { it.startMs }
            .toList()

        val timed = normalized.mapIndexedNotNull { index, line ->
            val nextStart = normalized.getOrNull(index + 1)?.startMs
            if (line.text.isBlank()) {
                val end = maxOf(line.endMs, nextStart ?: line.endMs)
                if (end - line.startMs > WAITING_LINE_MIN_DURATION_MS) countdownLine(line.startMs, end) else null
            } else if (line.endMs <= line.startMs) {
                val estimatedEnd = nextStart ?: (line.startMs + estimateUntimedLineDuration(line.text))
                line.copy(endMs = estimatedEnd.coerceAtLeast(line.startMs))
            } else {
                line
            }
        }

        val result = ArrayList<YosLyricLine>(timed.size * 2)
        timed.forEach { line ->
            val previousEnd = result.lastOrNull()?.let(::effectiveEndMs) ?: 0f
            if (!line.isCountdown && line.startMs - previousEnd > WAITING_LINE_MIN_DURATION_MS) {
                result += countdownLine(previousEnd, line.startMs)
            }
            result += line
        }
        return result
    }

    private fun effectiveEndMs(line: YosLyricLine): Float =
        maxOf(line.endMs, line.otherLrcEntries.maxOfOrNull { it.endMs } ?: line.endMs)

    private fun countdownLine(startMs: Float, endMs: Float): YosLyricLine =
        blankLine(startMs, endMs).copy(agent = YosLyricLine.COUNTDOWN_AGENT)

    private fun parseLrcTimeMs(raw: String): Float? {
        if (!timeTextRegex.matches(raw)) return null
        val parts = raw.split(":")
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val secPart = parts.getOrNull(1) ?: return null
        val seconds = secPart.substringBefore(".").toIntOrNull() ?: return null
        val fraction = secPart.substringAfter(".", "").take(3).padEnd(3, '0').toIntOrNull() ?: 0
        return (minutes * 60_000 + seconds * 1000 + fraction).toFloat()
    }

    private companion object {
        const val WAITING_LINE_MIN_DURATION_MS = 10_000f
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

    private fun estimateUntimedLineDuration(text: String): Float =
        (text.length.coerceAtLeast(1) * 90f).coerceIn(1_200f, 12_000f)

    private fun cleanLyricText(raw: String): String {
        val text = decodeXmlEntities(raw)
            .replace("//", "")
            .replace(Regex("(?!\\n)\\s+"), " ")
        return if (formatText) text.trim() else text
    }

    private fun cleanLyricTokenText(raw: String): String =
        decodeXmlEntities(raw)
            .replace("//", "")
            .replace(Regex("(?!\\n)\\s+"), " ")

    private fun isInlineTtmlText(raw: String): Boolean =
        raw.isNotEmpty() && !raw.contains('\n') && !raw.contains('\r')

    private fun appendInlineTextToLastSegment(segments: MutableList<LyricSegment>, raw: String) {
        val text = stripXml(raw)
        if (segments.isEmpty() || !isInlineTtmlText(text)) return
        val cleaned = cleanLyricTokenText(text)
        if (cleaned.isEmpty()) return
        val lastIndex = segments.lastIndex
        segments[lastIndex] = segments[lastIndex].copy(text = segments[lastIndex].text + cleaned)
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
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&#xA0;", " ")
            .replace("&#xa0;", " ")
            .replace('\u00A0', ' ')

    /**
     * 将翻译文本合并到已解析的歌词数据中。
     * 翻译文本可以是 LRC 格式（含时间标签）或纯文本（每行一句翻译）。
     * 纯文本按"真实歌词行"的顺序对齐，自动插入的倒计时行不占翻译下标。
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
            // 尝试作为纯文本翻译处理（每行对应一句真实歌词）
            val plainLines = translationText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "//" }
            if (plainLines.isEmpty()) return lrcEntries
            var plainIndex = 0
            lrcEntries.map { line ->
                if (line.isCountdown) {
                    line
                } else if (plainIndex < plainLines.size) {
                    line.copy(translation = plainLines[plainIndex++])
                } else {
                    line
                }
            }
        }
    }

}

/*
private fun String.ifNeedMirror(): Boolean {
    val directionality = Character.getDirectionality(this.trim().first())
    return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}*/
