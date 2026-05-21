package yos.music.player.data.remote

/**
 * 从远程文件的头部字节中提取音频元数据（特别是 duration）。
 * 不依赖 TagLib（需要文件路径），只做轻量级字节解析。
 *
 * 完整标签（title/artist/album 等）通过 ExoPlayer 的 onTracksChanged 回调
 * 在播放时自动提取并更新到 YosMediaItem 中。
 */
object RemoteMetadataExtractor {

    private const val HEADER_SIZE = 256 * 1024 // 256KB

    /**
     * 从文件头部字节中提取时长（毫秒）。
     * 支持的格式：MP3(CBR/VBR), FLAC, OGG Vorbis
     * 其他格式返回 0（由 ExoPlayer 播放时补充）。
     */
    fun extractDuration(header: ByteArray, fileName: String): Long {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "flac" -> extractFlacDuration(header)
            "mp3" -> extractMp3Duration(header)
            "ogg", "opus" -> extractOggDuration(header)
            else -> 0L
        }
    }

    /**
     * 从 FLAC 文件的 STREAMINFO 元数据块中提取时长。
     * STREAMINFO 块：采样率(20bit) + 声道数(3bit) + 位深(5bit) + 总采样数(36bit)。
     * 前4字节是 "fLaC" 标记，然后是 STREAMINFO 块（块头 + 34字节数据）。
     */
    private fun extractFlacDuration(header: ByteArray): Long {
        if (header.size < 42) return 0L
        // 查找 "fLaC" 标记
        val flacOffset = (0..header.size - 4).firstOrNull {
            header[it] == 0x66.toByte() && header[it + 1] == 0x4C.toByte() &&
            header[it + 2] == 0x61.toByte() && header[it + 3] == 0x43.toByte()
        } ?: return 0L

        // STREAMINFO 在 fLaC 后紧接着（跳过块头4字节，再跳过2字节的最小块大小）
        val streamInfoOffset = flacOffset + 4 + 4 + 2 + 2 + 2
        if (streamInfoOffset + 8 > header.size) return 0L

        // 读取采样率和总采样数
        val sampleRate = ((header[streamInfoOffset + 2].toInt() and 0xFF) shl 12) or
                         ((header[streamInfoOffset + 3].toInt() and 0xFF) shl 4) or
                         ((header[streamInfoOffset + 4].toInt() and 0xF0) shr 4)

        val totalSamples = ((header[streamInfoOffset + 4].toLong() and 0x0F) shl 32) or
                           ((header[streamInfoOffset + 5].toLong() and 0xFF) shl 24) or
                           ((header[streamInfoOffset + 6].toLong() and 0xFF) shl 16) or
                           ((header[streamInfoOffset + 7].toLong() and 0xFF) shl 8) or
                           (header[streamInfoOffset + 8].toLong() and 0xFF)

        if (sampleRate == 0) return 0L
        return (totalSamples * 1000) / sampleRate
    }

    /**
     * 从 MP3 文件中提取时长。
     * 优先检查 Xing/VBRI 头（VBR），否则用 CBR 估算。
     */
    private fun extractMp3Duration(header: ByteArray): Long {
        if (header.size < 10) return 0L
        val id3Size = getID3v2Size(header)

        // 查找第一个同步帧
        val frameStart = id3Size.coerceAtLeast(0)
        if (frameStart + 4 > header.size) return 0L

        // 检查 Xing 头部 (VBR)
        val xingOffset = findXingOffset(header, frameStart)
        if (xingOffset in 1..header.size - 120) {
            val flags = ((header[xingOffset + 4].toInt() and 0xFF) shl 24) or
                        ((header[xingOffset + 5].toInt() and 0xFF) shl 16) or
                        ((header[xingOffset + 6].toInt() and 0xFF) shl 8) or
                        (header[xingOffset + 7].toInt() and 0xFF)
            if (flags and 0x01 != 0 && xingOffset + 12 <= header.size) {
                val numFrames = ((header[xingOffset + 8].toLong() and 0xFF) shl 24) or
                                ((header[xingOffset + 9].toLong() and 0xFF) shl 16) or
                                ((header[xingOffset + 10].toLong() and 0xFF) shl 8) or
                                (header[xingOffset + 11].toLong() and 0xFF)
                val samplesPerFrame = getMp3SamplesPerFrame(header[frameStart])
                if (samplesPerFrame > 0 && numFrames > 0) {
                    val sampleRate = getMp3SampleRate(header[frameStart])
                    if (sampleRate > 0) return (numFrames * samplesPerFrame * 1000) / sampleRate
                }
            }
        }

        // CBR 估算：文件大小 / 比特率
        return 0L // 无法从头部确定 CBR 文件大小
    }

    private fun getID3v2Size(header: ByteArray): Int {
        if (header.size < 10) return 0
        if (header[0] != 0x49.toByte() || header[1] != 0x44.toByte() || header[2] != 0x33.toByte()) return 0
        return ((header[6].toInt() and 0x7F) shl 21) or
               ((header[7].toInt() and 0x7F) shl 14) or
               ((header[8].toInt() and 0x7F) shl 7) or
               (header[9].toInt() and 0x7F) + 10
    }

    private fun findXingOffset(header: ByteArray, firstFrameStart: Int): Int {
        // Xing header is located right after the first MP3 frame header
        val frameHeaderLen = if (header[firstFrameStart + 1].toInt() and 0x06 != 0) 2 else 0 // CRC check
        val sideInfoLen = if ((header[firstFrameStart + 1].toInt() and 0x08) != 0) 0 else if ((header[firstFrameStart + 3].toInt() and 0xC0) == 0xC0.toInt()) 32 else 17
        return firstFrameStart + 4 + frameHeaderLen + sideInfoLen
    }

    private fun getMp3SamplesPerFrame(firstFrameHeader: Byte): Int {
        val version = (firstFrameHeader.toInt() shr 3) and 0x03
        val layer = (firstFrameHeader.toInt() shr 1) and 0x03
        return when {
            layer == 3 -> when (version) { 3 -> 1152; 2 -> 576; 0 -> 576; else -> 0 } // Layer I
            layer == 2 -> when (version) { 3 -> 1152; 2 -> 1152; 0 -> 576; else -> 0 } // Layer II
            layer == 1 -> when (version) { 3 -> 1152; 2 -> 1152; 0 -> 576; else -> 0 } // Layer III
            else -> 0
        }
    }

    private fun getMp3SampleRate(firstFrameHeader: Byte): Int {
        val versionIndex = (firstFrameHeader.toInt() shr 3) and 0x03
        val sampleRateIndex = (firstFrameHeader.toInt() shr 2) and 0x03
        val rates = when (versionIndex) {
            3 -> intArrayOf(44100, 48000, 32000, 0) // MPEG-1
            2 -> intArrayOf(22050, 24000, 16000, 0) // MPEG-2
            0 -> intArrayOf(11025, 12000, 8000, 0)  // MPEG-2.5
            else -> return 0
        }
        return rates.getOrElse(sampleRateIndex) { 0 }
    }

    private fun extractOggDuration(header: ByteArray): Long {
        // OGG files: last page has the granule position (total samples)
        // With just the header we can't get this. Return 0 and let ExoPlayer fill it in.
        return 0L
    }
}
