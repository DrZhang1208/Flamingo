package yos.music.player.code.datasource

import android.net.Uri
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import yos.music.player.data.remote.RemoteServerManager
import yos.music.player.data.remote.ServerConfig
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class WebDavDataSource(
    private val serverConfig: ServerConfig,
    private val remotePath: String
) : BaseDataSource(false) {

    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = 0
    private var openedUri: Uri? = null

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val serverId = serverConfig.id

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                if (serverConfig.authRequired) {
                    val user = serverConfig.username ?: ""
                    val pass = RemoteServerManager.getPassword(serverId) ?: ""
                    req.header("Authorization", Credentials.basic(user, pass))
                }
                chain.proceed(req.build())
            }
            .build()

        val base = serverConfig.basePath.trimEnd('/')
        val cleanPath = remotePath.trimStart('/')
        val scheme = if (serverConfig.port == 443) "https" else "http"
        val authority = if (serverConfig.port == 80 || serverConfig.port == 443) serverConfig.host else "${serverConfig.host}:${serverConfig.port}"
        val url = "$scheme://$authority$base/$cleanPath"

        val request = Request.Builder().url(url)
        if (dataSpec.position > 0 || dataSpec.length != -1L) {
            val rangeStart = dataSpec.position
            val rangeEnd = if (dataSpec.length != -1L) {
                rangeStart + dataSpec.length - 1
            } else {
                ""
            }
            request.header("Range", "bytes=$rangeStart-$rangeEnd")
        }

        val response = client.newCall(request.build()).execute()
        if (!response.isSuccessful) throw IOException("WebDAV request failed: HTTP ${response.code}")

        inputStream = response.body?.byteStream() ?: throw IOException("Empty response body")

        val contentLength = response.body?.contentLength() ?: -1L
        bytesRemaining = if (contentLength > 0) contentLength else Long.MAX_VALUE

        openedUri = Uri.parse("webdav://${serverId}/$remotePath")
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
        val bytesToRead = minOf(length, bytesRemaining.toInt().coerceAtMost(buffer.size - offset))
        val bytesRead = inputStream?.read(buffer, offset, bytesToRead) ?: return C.RESULT_END_OF_INPUT
        if (bytesRead > 0) bytesRemaining -= bytesRead
        return if (bytesRead <= 0) C.RESULT_END_OF_INPUT else bytesRead
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        runCatching { inputStream?.close() }
        inputStream = null
        bytesRemaining = 0
    }

    private object C {
        const val RESULT_END_OF_INPUT = -1
    }
}
