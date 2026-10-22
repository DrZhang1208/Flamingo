package yos.music.player.data.remote

import java.net.ServerSocket
import kotlin.concurrent.thread
import java.io.InputStream
import java.io.IOException
import okhttp3.*
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.Assert.*
import org.junit.Test

class HttpRangeReaderTest {
    @Test fun serverIgnoringRangeStillReturnsRequestedOffset() {
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val content = ByteArray(64 * 1024) { (it % 251).toByte() }
        val worker = thread(isDaemon = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { }
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\nContent-Length: ${content.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(content)
                output.flush()
            }
        }
        try {
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/audio")
                .header("Range", "bytes=300-1323").build()
            OkHttpClient().newCall(request).execute().use {
                assertArrayEquals(content.copyOfRange(300, 1324), readHttpRange(it, 300, 1024))
            }
        } finally { server.close(); worker.join(2000) }
    }

    @Test fun hugeResponseIsNotReadIntoMemory() {
        var consumed = 0
        val stream = object : InputStream() {
            override fun read(): Int { consumed++; return 7 }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                bytes.fill(7, offset, offset + length)
                consumed += length
                return length
            }
        }
        val body = object : ResponseBody() {
            private val buffered = stream.source().buffer()
            override fun contentType(): MediaType? = null
            override fun contentLength() = 512L * 1024 * 1024
            override fun source(): BufferedSource = buffered
        }
        response(200, body).use {
            assertEquals(1024, readHttpRange(it, 0, 1024).size)
        }
        assertTrue("Consumed $consumed bytes for a 1KB request", consumed <= 8192)
    }

    @Test fun partialResponseMustStartAtRequestedOffset() {
        response(206, ResponseBody.create(null, byteArrayOf(1, 2)), "bytes 100-101/200").use {
            assertArrayEquals(byteArrayOf(1, 2), readHttpRange(it, 100, 8))
        }
        response(206, ResponseBody.create(null, byteArrayOf(1, 2)), "bytes 0-1/200").use {
            try { readHttpRange(it, 100, 8); fail("Wrong offset must be rejected") }
            catch (_: IOException) {}
        }
    }

    private fun response(code: Int, body: ResponseBody, range: String? = null): Response =
        Response.Builder().request(Request.Builder().url("http://localhost/audio").build())
            .protocol(Protocol.HTTP_1_1).code(code).message("test").body(body)
            .apply { if (range != null) header("Content-Range", range) }.build()
}
