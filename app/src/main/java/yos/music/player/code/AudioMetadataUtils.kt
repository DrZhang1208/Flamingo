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
import kotlin.math.abs
import kotlin.math.roundToInt

object AudioMetadataUtils {
    private data class YearCandidate(
        val year: Int,
        val precision: Int,
        val keyPriority: Int,
        val order: Int
    )

    fun parseYear(raw: String?): Int? {
        return parseYearCandidate(raw, keyPriority = 0, order = 0)?.year
    }

    fun extractYearFromTags(propertyMap: Map<String, Array<String>>, fallbackYear: String? = null): Int? {
        fun normalized(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }

        val releaseKeys = setOf("RELEASEDATE", "RELEASETIME", "RELEASEYEAR", "TDRL")
        val dateKeys = setOf("DATE", "YEAR", "TDRC")
        val originalKeys = setOf("ORIGINALDATE", "ORIGINALYEAR", "ORIGINALRELEASEDATE", "ORIGINALRELEASEYEAR", "TDOR")

        val candidates = buildList {
            var order = 0
            propertyMap.entries.forEach { (key, values) ->
                val priority = when (normalized(key)) {
                    in releaseKeys -> 0
                    in dateKeys -> 1
                    in originalKeys -> 2
                    else -> return@forEach
                }
                values.forEach { value ->
                    parseYearCandidate(value, priority, order++)?.let { add(it) }
                }
            }
            parseYearCandidate(fallbackYear, keyPriority = 1, order = order)?.let { add(it) }
        }

        return candidates
            .sortedWith(
                compareByDescending<YearCandidate> { it.precision }
                    .thenBy { it.keyPriority }
                    .thenBy { it.order }
            )
            .firstOrNull()
            ?.year
    }

    private fun parseYearCandidate(raw: String?, keyPriority: Int, order: Int): YearCandidate? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val fullDate = Regex("""(?<!\d)(\d{4})[-./](\d{1,2})[-./](\d{1,2})(?!\d)""").find(value)
        if (fullDate != null) {
            val year = fullDate.groupValues[1].toIntOrNull()?.takeIf { it in 1000..2999 } ?: return null
            return YearCandidate(year, precision = 3, keyPriority = keyPriority, order = order)
        }
        val yearMonth = Regex("""(?<!\d)(\d{4})[-./](\d{1,2})(?!\d)""").find(value)
        if (yearMonth != null) {
            val year = yearMonth.groupValues[1].toIntOrNull()?.takeIf { it in 1000..2999 } ?: return null
            return YearCandidate(year, precision = 2, keyPriority = keyPriority, order = order)
        }
        val year = Regex("""(?<!\d)(\d{4})(?!\d)""")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in 1000..2999 }
            ?: return null
        return YearCandidate(year, precision = 1, keyPriority = keyPriority, order = order)
    }

    fun normalizeBitrateKbps(rawBitrate: Int?): Int? {
        val bitrate = rawBitrate ?: return null
        if (bitrate <= 0) return null
        return when {
            bitrate >= 100_000 -> bitrate / 1000
            else -> bitrate
        }
    }

    fun normalizeBitrateKbps(rawBitrate: Int): Int =
        normalizeBitrateKbps(rawBitrate as Int?) ?: -1

    fun estimateBitrateKbps(fileSizeBytes: Long?, durationMs: Long?): Int? {
        val size = fileSizeBytes ?: return null
        val duration = durationMs ?: return null
        if (size <= 0L || duration <= 0L) return null
        return ((size * 8.0) / duration).roundToInt().takeIf { it > 0 }
    }

    fun reliableBitrateKbps(rawBitrate: Int?, fileSizeBytes: Long?, durationMs: Long?): Int? {
        val normalized = normalizeBitrateKbps(rawBitrate)
        val estimated = estimateBitrateKbps(fileSizeBytes, durationMs)
        return when {
            normalized != null && normalized in 1..20 && estimated != null -> estimated
            normalized != null && estimated != null && normalized >= 1000 && normalized % 1000 == 0 &&
                    abs(normalized - estimated) > 50 -> estimated
            else -> normalized ?: estimated
        }
    }

    private inline fun <T> runWithDetachedFd(file: File, block: (Int) -> T): T? {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                block(pfd.dup().detachFd())
            }
        }.getOrNull()
    }

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
        // Method 1: MediaMetadataRetriever (API 33+)
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            @Suppress("NewApi")
            val lyrics = retriever.extractMetadata(24)
            retriever.release()
            if (!lyrics.isNullOrBlank()) {
                return lyrics
            }
        }.onFailure {
        }

        // Method 2: TagLib ID3 parser (properly extracts USLT frame)
        runCatching {
            val file = File(filePath)
            if (!file.exists()) return@runCatching null
            val metadata = runWithDetachedFd(file) { fd -> TagLib.getMetadata(fd, false) } ?: return@runCatching null
            val propertyMap = metadata.propertyMap ?: return@runCatching null
            val lyricEntry = propertyMap.entries.firstOrNull { (key, _) ->
                key.uppercase().let { it.contains("USLT") || it.contains("UNSYNCEDLYRICS") || it.contains("LYRICS") }
            }
            if (lyricEntry != null) {
                val lyricText = lyricEntry.value.lastOrNull()
                if (!lyricText.isNullOrBlank()) {
                    return lyricText
                }
            }
        }.onFailure {
        }

        // Method 3: Scan file for LRC content (fallback)
        return runCatching {
            val file = File(filePath)
            if (!file.exists()) return@runCatching null
            extractLrcFromFile(file)
        }.onFailure {
        }.getOrNull()
    }

    private fun extractLrcFromFile(file: File): String? {
        val fileLength = file.length()
        // Only scan files under 500MB
        if (fileLength > 500 * 1024 * 1024) return null

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
                return content
            }
        }
        return null
    }

    fun getQualityInfos(filePath: String): Pair<Int, Int> {
        val songFile = File(filePath)
        var bitrate: Int
        var sampleRate: Int
        var durationMs: Long? = null

        val tagLibResult = runCatching {
            if (!songFile.exists()) return@runCatching (-1 to -1)
            runWithDetachedFd(songFile) { fd ->
                val audioProperties = TagLib.getAudioProperties(fd, AudioPropertiesReadStyle.Fast)
                val rawLength = audioProperties?.length?.toLong() ?: 0L
                durationMs = if (rawLength in 1..10000) rawLength * 1000L else rawLength.takeIf { it > 0L }
                (reliableBitrateKbps(audioProperties?.bitrate, songFile.length(), durationMs) ?: -1) to (audioProperties?.sampleRate ?: -1)
            } ?: (-1 to -1)
        }.getOrElse { (-1 to -1) }

        bitrate = tagLibResult.first
        sampleRate = tagLibResult.second

        if (bitrate == -1 || sampleRate == -1) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(filePath)
                if (extractor.trackCount > 0) {
                    val format = extractor.getTrackFormat(0)
                    if (bitrate == -1 && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        bitrate = reliableBitrateKbps(format.getInteger(MediaFormat.KEY_BIT_RATE), songFile.length(), durationMs) ?: -1
                    }
                    if (sampleRate == -1 && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                extractor.release()
            }
        }

        return Pair(bitrate, sampleRate)
    }

    data class AudioFileInfo(
        val bitrate: Int?,        // kbps
        val sampleRate: Int?,     // Hz
        val channels: Int?,       // channel count
        val bitsPerSample: Int?,  // bit depth (PCM only)
        val fileSize: Long,       // bytes
        val format: String,       // file extension
        val source: String        // parent folder name
    )

    fun getAudioFileInfo(filePath: String): AudioFileInfo {
        val file = File(filePath)
        var bitrate: Int? = null
        var sampleRate: Int? = null
        var channels: Int? = null
        var bitsPerSample: Int? = null
        var durationMs: Long? = null

        // Try TagLib for audio properties
        runCatching {
            if (file.exists()) {
                val props = runWithDetachedFd(file) { fd ->
                    TagLib.getAudioProperties(fd, AudioPropertiesReadStyle.Fast)
                }
                val rawLength = props?.length?.toLong() ?: 0L
                durationMs = if (rawLength in 1..10000) rawLength * 1000L else rawLength.takeIf { it > 0L }
                bitrate = reliableBitrateKbps(props?.bitrate, file.length(), durationMs)
                sampleRate = props?.sampleRate
                channels = props?.channels
            }
        }

        // Fallback to MediaExtractor for missing fields
        runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)
            val format = extractor.getTrackFormat(0)
            if (bitrate == null && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                bitrate = reliableBitrateKbps(format.getInteger(MediaFormat.KEY_BIT_RATE), file.length(), durationMs)
            }
            if (sampleRate == null) sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            if (channels == null) channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // Try to get PCM encoding for bit depth
            runCatching {
                val pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                bitsPerSample = when (pcmEncoding) {
                    2 -> 16  // ENCODING_PCM_16BIT
                    3 -> 8   // ENCODING_PCM_8BIT
                    4 -> 32  // ENCODING_PCM_FLOAT
                    else -> null
                }
            }
            extractor.release()
        }

        val format = file.extension.uppercase().ifEmpty { "UNKNOWN" }
        val source = file.parentFile?.name ?: "未知来源"
        val fileSize = if (file.exists()) file.length() else 0L

        return AudioFileInfo(
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            fileSize = fileSize,
            format = format,
            source = source
        )
    }

    fun getYear(filePath: String): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            parseYear(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR))
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    fun getTrackNumber(filePath: String): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull()
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    // METADATA_KEY_DISC_NUMBER = 26, available since API 35
    private const val METADATA_KEY_DISC_NUMBER = 26

    fun getDiscNumber(filePath: String): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                retriever.extractMetadata(METADATA_KEY_DISC_NUMBER)?.toIntOrNull()
            } else null
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

}
