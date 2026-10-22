package yos.music.player.data.remote

import okhttp3.Response
import java.io.IOException

/** Read only the requested window, even when a server ignores Range. */
internal fun readHttpRange(response: Response, offset: Long, length: Int): ByteArray {
    require(offset >= 0 && length > 0)
    if (!response.isSuccessful) throw IOException("WebDAV read failed: HTTP ${response.code}")
    val body = response.body ?: throw IOException("WebDAV empty body")
    val skip = when (response.code) {
        200 -> offset
        206 -> {
            val start = response.header("Content-Range")
                ?.let { Regex("bytes (\\d+)-\\d+/(?:\\d+|\\*)").matchEntire(it.trim()) }
                ?.groupValues?.get(1)?.toLongOrNull()
            if (start != offset) throw IOException("WebDAV invalid Content-Range")
            0L
        }
        else -> throw IOException("WebDAV unexpected range response: HTTP ${response.code}")
    }
    return body.byteStream().use { stream ->
        var remaining = skip
        val discard = ByteArray(8192)
        while (remaining > 0) {
            val count = stream.read(discard, 0, minOf(remaining, discard.size.toLong()).toInt())
            if (count < 0) return@use ByteArray(0)
            remaining -= count
        }
        val result = ByteArray(length)
        var count = 0
        while (count < length) {
            val read = stream.read(result, count, length - count)
            if (read < 0) break
            count += read
        }
        if (count == length) result else result.copyOf(count)
    }
}
