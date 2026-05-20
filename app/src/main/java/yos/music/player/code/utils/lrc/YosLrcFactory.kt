package yos.music.player.code.utils.lrc

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastJoinToString
import yos.music.player.data.objects.MediaViewModelObject
import kotlin.math.abs

/**
 * Lrc 歌词文本处理
 */
class YosLrcFactory(private val formatText: Boolean = true) {
    /**
     * Lrc 歌词文本处理方法
     * @param lrcText Lrc 格式的文本
     */
    /*fun formatLrcEntries(lrcText: String): List<Pair<Float, String>> {
        val lrcLines = lrcText.lines()
        return lrcLines.mapNotNull { line ->
            val timeIndex = line.indexOf("]")
            if (timeIndex == -1) return@mapNotNull null
            val timeText = line.substring(1, timeIndex)
            val timeParts = timeText.split(":")
            if (timeParts.size != 2) return@mapNotNull null
            val minutes = timeParts[0].toIntOrNull() ?: return@mapNotNull null
            val seconds = timeParts[1].toFloatOrNull() ?: return@mapNotNull null
            val time = (minutes * 60 + seconds) * 1000
            val lyric = line.substring(timeIndex + 1)
            if (lyric.isBlank() || lyric.trim() == "//") return@mapNotNull null
            time to if (formatText) lyric.replace(Regex("(?!\\n)\\s+"), " ").trim() else lyric
        }
    }*/
    fun formatLrcEntries(lrcText: String): List<List<Pair<Float, String>>> {
        // Strip content before first timestamp and after last timestamp
        val timestampRegex = Regex("""\[\d{2}:\d{2}\.\d{2,3}]""")
        val cleanText = run {
            val firstTs = timestampRegex.find(lrcText)?.range?.first ?: 0
            var result = lrcText.substring(firstTs)
            // Find last valid timestamp line
            val lines = result.lines()
            val lastTsIndex = lines.indexOfLast { timestampRegex.containsMatchIn(it) }
            if (lastTsIndex >= 0) {
                result = lines.take(lastTsIndex + 1).joinToString("\n")
            }
            result
        }
        // Filter out metadata lines but keep timestamp lines
        val lrcLines = cleanText.lines().filter { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@filter true
            // Keep all lines that have timestamps
            if (timestampRegex.containsMatchIn(trimmed)) return@filter true
            // Skip LRC metadata tags: [ti:...], [ar:...], etc.
            if (trimmed.matches(Regex("""^\[(ti|ar|al|by|length|offset|re|ve|la|_)\s*:.*""", RegexOption.IGNORE_CASE))) return@filter false
            // Skip KEY=VALUE or KEY:VALUE lines without timestamps
            if (trimmed.matches(Regex("""^[A-Za-z_]+\s*[=＝:：].*""", RegexOption.IGNORE_CASE))) return@filter false
            // Skip other non-timestamp lines
            false
        }
        val timeLyricPairs = mutableListOf<MutableList<Pair<Float, String>>>()
        lrcLines.fastForEachIndexed { index, line ->
            //将文本中完全相同而且重复的两个时间轴修改为一个
            //比如[12:34.56][12:34.56]改为[12:34.56]
            var remainingLine =
                line.replace(Regex("([\\[\\]]){2,}"), "$1").replace(Regex("<([^>]+)>"), "[$1]")
                    .replace(Regex("(\\[\\d{2}:\\d{2}\\.\\d{2,3}]){2,}"), "$1")
            println("歌词处理：$remainingLine")
            val currentLinePairs = mutableListOf<Pair<Float, String>>()
            while (remainingLine.isNotEmpty()) {
                /*val timeIndex = remainingLine.indexOf("]")
                if (timeIndex == -1) break
                val timeText = remainingLine.substring(1, timeIndex)
                val timeParts = timeText.split(":")
                if (timeParts.size != 2) break
                val minutes = timeParts[0].toIntOrNull() ?: break
                val seconds = timeParts[1].toFloatOrNull() ?: break
                val time = (minutes * 60 + seconds) * 1000
                remainingLine = remainingLine.substring(timeIndex + 1)
                val nextTimeIndex = remainingLine.indexOf("[")
                val lyric = if (nextTimeIndex != -1) {
                    remainingLine.substring(0, nextTimeIndex)
                } else {
                    remainingLine
                }*/

                val timeIndex = remainingLine.indexOf("[")
                if (timeIndex == -1) break
                val timeAfter = remainingLine.indexOf("]")
                if (timeAfter == -1) break
                val timeText = remainingLine.substring(timeIndex + 1, timeAfter)
                val timeParts = timeText.split(":")
                if (timeParts.size != 2) break
                val minutes = timeParts[0].toIntOrNull() ?: break
                val seconds = timeParts[1].toFloatOrNull() ?: break
                val time = (minutes * 60 + seconds) * 1000

                if (remainingLine.substring(timeAfter + 1, remainingLine.length)
                        .isBlank() && remainingLine.substring(0, timeIndex).isBlank()
                ) {
                    // 检查下一行的时间差
                    if (index + 1 < lrcLines.size) {
                        val nextLine = lrcLines[index + 1]
                        val nextTimeIndex = nextLine.indexOf("[")
                        val nextTimeAfter = nextLine.indexOf("]")
                        if (nextTimeIndex != -1 && nextTimeAfter != -1) {
                            val nextTimeText = nextLine.substring(nextTimeIndex + 1, nextTimeAfter)
                            val nextTimeParts = nextTimeText.split(":")
                            if (nextTimeParts.size == 2) {
                                val nextMinutes = nextTimeParts[0].toIntOrNull()
                                val nextSeconds = nextTimeParts[1].toFloatOrNull()
                                if (nextMinutes != null && nextSeconds != null) {
                                    val nextTime = (nextMinutes * 60 + nextSeconds) * 1000
                                    if (nextTime - time <= 4200) {
                                        // 忽略当前行的处理，进行下一行的处理
                                        break
                                    }
                                }
                            }
                        }
                    } else {
                        // 这是最后一行，且为空行
                        break
                    }
                }

                val nextTimeIndex = remainingLine.substring(timeAfter + 1).indexOf("[")

                // 逐行起始或逐字末尾
                var lyric = remainingLine.substring(0, timeIndex)

                if (lyric.isEmpty()) {
                    // 句子起始
                    lyric = ""
                    currentLinePairs.add(time to lyric.replace(Regex("(?!\\n)\\s+"), " "))
                } else {
                    // 正常句子成分
                    if (/*lyric.isNotBlank() && */lyric.trim() != "//") {
                        currentLinePairs.add(
                            time to lyric.replace(Regex("(?!\\n)\\s+"), " ")
                        )
                    }
                }

                remainingLine = remainingLine.substring(timeAfter + 1)
                if (nextTimeIndex == -1) {
                    if (lyric == "") {
                        currentLinePairs.add(
                            time to remainingLine.replace("//", "").replace(
                                Regex("(?!\\n)\\s+"),
                                " "
                            )/*.trim()*/
                        )
                    }
                    remainingLine = ""
                }
            }
            if (currentLinePairs.isNotEmpty()) {
                val existingList =
                    timeLyricPairs.find { it.first().first == currentLinePairs.first().first }
                if (existingList != null) {
                    // 相同时间戳的行视为翻译，将翻译文本填入 sentinel 槽位
                    // currentLinePairs 结构: [(time, ""), (time, "text"), ...] 或 [(time, "text"), ...]
                    // 需要取非空的文本内容
                    val translationText = currentLinePairs
                        .firstOrNull { it.second.isNotEmpty() }
                        ?.second.orEmpty()
                    if (translationText.isNotEmpty()) {
                        existingList[existingList.size - 1] =
                            existingList.last().first to translationText
                    }
                } else {
                    currentLinePairs.add(currentLinePairs[0].first to "")
                    timeLyricPairs.add(currentLinePairs)
                }
            }
        }
        val processedEntries = processOtherSide(timeLyricPairs)
        return processedEntries.filter { entry ->
            if (entry.isEmpty()) return@filter false
            val hasTimestamp = entry.any { it.first > 0f && it.second.isNotEmpty() }
            if (!hasTimestamp) return@filter false
            val text = entry.fastJoinToString(separator = "") { it.second }.trim().uppercase()
            !text.startsWith("TITLE=") && !text.startsWith("ARTIST=") &&
            !text.startsWith("ALBUM=") && !text.startsWith("GENRE=") &&
            !text.startsWith("DATE=") && !text.startsWith("YEAR=") &&
            !text.startsWith("TRACK=") && !text.startsWith("COMPOSER=") &&
            !text.startsWith("WRITER=") && !text.startsWith("ENCODER=") &&
            !text.startsWith("LENGTH=")
        }
    }

    /**
     * 将翻译文本合并到已解析的歌词数据中。
     * 翻译文本可以是 LRC 格式（含时间标签）或纯文本（每行一句翻译）。
     * 合并后，每行歌词的最后一个元素将为翻译文本。
     */
    fun mergeTranslation(
        lrcEntries: List<List<Pair<Float, String>>>,
        translationText: String
    ): List<List<Pair<Float, String>>> {
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
                if (line.isEmpty()) return@map line
                val lineTime = line.first().first
                val matchedTranslation = translationLines
                    .filter { abs(it.first - lineTime) < 50f }
                    .minByOrNull { abs(it.first - lineTime) }
                if (matchedTranslation != null) {
                    line.dropLast(1) + (line.last().first to matchedTranslation.second)
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
                if (index < plainLines.size && line.isNotEmpty()) {
                    line.dropLast(1) + (line.last().first to plainLines[index])
                } else {
                    line
                }
            }
        }
    }

    private fun processOtherSide(lrcEntries: List<List<Pair<Float, String>>>): List<List<Pair<Float, String>>> {
        // Count duet markers - only enable duet mode if there are multiple markers
        val duetMarkerCount = lrcEntries.count { lines ->
            val lyric = lines.fastJoinToString(separator = "") { it.second }
            lyric.endsWith(":") || lyric.endsWith("：") ||
                (lines.size > 1 && lines[1].second.matches(Regex(".+\\s*:\\s*")))
        }
        val isDuetSong = duetMarkerCount >= 3

        val otherSideResult = mutableStateListOf<Boolean>()
        var otherSide = false
        var lastSinger: String? = null
        var otherSideFirstTime = false

        val filteredLrcEntries = lrcEntries.map { lines ->
            val lyric = lines.fastJoinToString(separator = "") { it.second }
            var deleteType = -1

            if (isDuetSong && (lyric.endsWith(":") || lyric.endsWith("："))) {
                otherSide = !otherSide
            } else if (isDuetSong && lines.size > 1) {
                val currentSinger = lines[1].second
                if (currentSinger.matches(Regex(".+\\s*:\\s*"))) {
                    deleteType = 0
                    if (lastSinger != null && lastSinger == currentSinger) {
                    } else {
                        if (otherSideFirstTime) otherSide = !otherSide
                        else otherSideFirstTime = true
                    }
                    lastSinger = currentSinger
                }
            }

            if (!isDuetSong) otherSide = false
            otherSideResult.add(otherSide)

            if (isDuetSong) {
                lines.filterIndexed { index, char ->
                    !((index == 1 && char.second.matches(Regex(".+\\s*:\\s*"))) && deleteType == 0)
                }
            } else {
                lines
            }
        }

        MediaViewModelObject.otherSideForLines.clear()
        MediaViewModelObject.otherSideForLines.addAll(otherSideResult)
        return filteredLrcEntries
    }
}

/*
private fun String.ifNeedMirror(): Boolean {
    val directionality = Character.getDirectionality(this.trim().first())
    return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}*/
