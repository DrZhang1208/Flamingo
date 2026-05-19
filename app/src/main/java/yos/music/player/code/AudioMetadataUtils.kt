package yos.music.player.code

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kyant.taglib.AudioPropertiesReadStyle
import com.kyant.taglib.TagLib
import java.io.File
import java.nio.charset.Charset

object AudioMetadataUtils {
    fun loadLrcFile(context: Context, filePath: String): String? {
        return try {
            val file = File(filePath)
            val uri = Uri.fromFile(file)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charset.defaultCharset())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun extractEmbeddedLyrics(filePath: String): String? {
        // Method 1: MediaMetadataRetriever (API 33+), fast, no file read
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            @Suppress("NewApi")
            val lyrics = retriever.extractMetadata(24)
            retriever.release()
            if (!lyrics.isNullOrBlank()) {
                println("内嵌歌词 MediaMetadataRetriever 获取成功")
                return lyrics
            }
        }.onFailure {
            println("内嵌歌词 MediaMetadataRetriever 失败: ${it.message}")
        }

        // Method 2: Scan file head/tail for LRC content (fast, partial read)
        return runCatching {
            val file = File(filePath)
            if (!file.exists()) return@runCatching null
            extractLrcFromFile(file)
        }.onFailure {
            println("内嵌歌词 文件扫描失败: ${it.message}")
        }.getOrNull()
    }

    private fun extractLrcFromFile(file: File): String? {
        val fileLength = file.length()
        // Only scan files under 100MB
        if (fileLength > 100 * 1024 * 1024) return null

        // Read head (lyrics often in ID3v2 header at front) and tail (Vorbis/MP4)
        val headSize = minOf(fileLength, 512L * 1024L).toInt() // 512KB head
        val tailSize = minOf(fileLength, 256L * 1024L).toInt() // 256KB tail

        val buffer = ByteArray(headSize + tailSize)
        file.inputStream().use { stream ->
            var offset = 0
            var remaining = headSize
            while (remaining > 0) {
                val read = stream.read(buffer, offset, remaining)
                if (read <= 0) break
                offset += read; remaining -= read
            }
            if (tailSize > 0 && fileLength > headSize) {
                stream.skip(fileLength - headSize - tailSize)
                remaining = tailSize
                while (remaining > 0) {
                    val read = stream.read(buffer, offset, remaining)
                    if (read <= 0) break
                    offset += read; remaining -= read
                }
            }
        }

        return extractLrcFromBytes(buffer)
    }

    private fun extractLrcFromBytes(bytes: ByteArray): String? {
        // Search for LRC timestamp patterns in the binary data
        // LRC format: [mm:ss.xx] or [mm:ss.xxx]
        val content = runCatching {
            bytes.toString(Charsets.UTF_8)
        }.getOrElse {
            bytes.toString(Charset.forName("GBK"))
        }

        // Find the first LRC timestamp
        val timestampRegex = Regex("""\[\d{2}:\d{2}\.\d{2,3}]""")
        val firstMatch = timestampRegex.find(content) ?: return null

        // Extract from the first timestamp to the end
        val startIndex = firstMatch.range.first
        val lrcSection = content.substring(startIndex)

        // Find the end of LRC content (look for consecutive non-LRC lines or binary garbage)
        val lines = lrcSection.lines()
        val lrcLines = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                lrcLines.add(line)
                continue
            }
            if (timestampRegex.containsMatchIn(line) || line.startsWith("[")) {
                lrcLines.add(line)
            } else if (lrcLines.size > 2) {
                // Stop at first non-LRC line after we've collected some LRC content
                break
            }
        }

        // Aggressively strip all non-LRC metadata
        val filtered = lrcLines.filter { line ->
            val t = line.trim()
            // Skip LRC metadata tags
            if (t.matches(Regex("""^\[(ti|ar|al|by|length|offset|re|ve|la|_)\s*:.*""", RegexOption.IGNORE_CASE))) return@filter false
            // Skip KEY=VALUE lines (TITLE=, ARTIST=, etc.)
            if (t.matches(Regex("""^[A-Za-z_]+\s*[=＝:：].*""")) && !t.startsWith("[")) return@filter false
            // Skip non-LRC lines without timestamps
            if (t.isNotEmpty() && !t.startsWith("[") && !timestampRegex.containsMatchIn(t)) return@filter false
            true
        }
        val result = filtered.joinToString("\n").trim()
        return if (result.length > 50) result else null
    }

    fun loadTranslationLrc(context: Context, mainFilePath: String): String? {
        val basePath = mainFilePath.substringBeforeLast(".")
        val patterns = listOf(
            "${basePath}.zh.lrc",
            "${basePath}-zh.lrc",
            "${basePath}.trans.lrc",
            "${basePath}-trans.lrc",
            "${basePath}.tc.lrc"
        )
        for (pattern in patterns) {
            val content = loadLrcFile(context, pattern)
            if (content != null) {
                println("翻译歌词 从文件获取成功: $pattern")
                return content
            }
        }
        return null
    }

    fun getQualityInfos(filePath: String): Pair<Int, Int> {
        val songFile = File(filePath)
        var bitrate: Int
        var sampleRate: Int

        println("质量分析 Taglib 实现获取")

        ParcelFileDescriptor.open(songFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd(), AudioPropertiesReadStyle.Fast)
            bitrate = audioProperties?.bitrate ?: -1
            sampleRate = audioProperties?.sampleRate ?: -1
        }

        if (bitrate == -1 || sampleRate == -1) {
            val extractor = MediaExtractor()
            try {
                println("质量分析 MediaExtractor 实现获取")
                extractor.setDataSource(filePath)
                val format = extractor.getTrackFormat(0)
                if (bitrate == -1) {
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                }
                if (sampleRate == -1) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                extractor.release()
            }
        }

        return Pair(bitrate, sampleRate)
    }

}