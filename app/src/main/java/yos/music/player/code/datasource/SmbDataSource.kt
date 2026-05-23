package yos.music.player.code.datasource

import android.net.Uri
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import yos.music.player.data.remote.RemoteServerManager
import yos.music.player.data.remote.ServerConfig
import java.io.IOException
import java.io.InputStream

class SmbDataSource(
    private val serverConfig: ServerConfig,
    private val remotePath: String
) : BaseDataSource(false) {

    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = 0
    private var openedUri: Uri? = null

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val serverId = serverConfig.id

        if (!RemoteServerManager.isConnected(serverId)) {
            RemoteServerManager.connect(serverId)
        }

        inputStream = RemoteServerManager.openFileStream(serverId, remotePath)

        if (dataSpec.position > 0) {
            var skipped = 0L
            val toSkip = dataSpec.position
            while (skipped < toSkip) {
                val thisSkip = inputStream?.skip(toSkip - skipped) ?: break
                if (thisSkip == 0L) break
                skipped += thisSkip
            }
        }

        val totalSize = RemoteServerManager.getFileSize(serverId, remotePath)
        bytesRemaining = if (dataSpec.length != -1L) {
            minOf(dataSpec.length, (totalSize - dataSpec.position).coerceAtLeast(0))
        } else {
            (totalSize - dataSpec.position).coerceAtLeast(0)
        }

        openedUri = Uri.parse("smb://${serverId}/$remotePath")
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
        val bytesToRead = minOf(length.toLong(), bytesRemaining).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val bytesRead = inputStream?.read(buffer, offset, bytesToRead) ?: return C.RESULT_END_OF_INPUT
        if (bytesRead > 0) bytesRemaining -= bytesRead
        return if (bytesRead <= 0) C.RESULT_END_OF_INPUT else bytesRead
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        try { inputStream?.close() } catch (_: IOException) {}
        inputStream = null
        bytesRemaining = 0
    }

    private object C {
        const val RESULT_END_OF_INPUT = -1
    }
}
