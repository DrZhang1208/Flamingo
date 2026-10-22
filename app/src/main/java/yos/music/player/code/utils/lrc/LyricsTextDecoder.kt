package yos.music.player.code.utils.lrc

/**
 * 歌词文件字节解码：BOM 判定 → UTF-8 → GBK 回退（NAS 上的中文歌词常见 GBK）。
 * 本地 sidecar 与远程 sidecar 共用；音频文件头防呆。
 */
internal object LyricsTextDecoder {

    fun decode(bytes: ByteArray): String? {
        if (looksLikeAudio(bytes)) return null
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
            else -> decodeUtf8OrGbk(bytes)
        }
    }

    /** 无 BOM 时先按 UTF-8 解码；出现替换字符（非法序列）说明多半是 GBK，回退再试 */
    private fun decodeUtf8OrGbk(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        return runCatching { bytes.toString(charset("GBK")) }.getOrDefault(utf8)
    }

    private fun looksLikeAudio(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val head = bytes.take(12).toByteArray().toString(Charsets.ISO_8859_1)
        return head.startsWith("ID3") || head.startsWith("fLaC") || head.startsWith("OggS") ||
            head.startsWith("RIFF") || head.contains("ftyp")
    }
}
