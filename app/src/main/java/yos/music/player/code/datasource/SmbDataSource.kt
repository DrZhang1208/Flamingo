package yos.music.player.code.datasource

import android.net.Uri
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import yos.music.player.data.remote.RemoteServerManager
import yos.music.player.data.remote.RemoteUri
import yos.music.player.data.remote.ServerConfig
import yos.music.player.data.remote.SmbTransport
import java.io.IOException

/**
 * SMB 播放 DataSource。统一走 SmbRandomAccessFile：open 时按 dataSpec.position
 * seek，后续顺序读由 jcifs-ng 内部分块请求完成。会话与连接池复用 SmbTransport
 * 缓存的 CIFSContext，因此每次 open 的开销只剩一次文件打开。
 */
class SmbDataSource(
    private val serverConfig: ServerConfig,
    private val remotePath: String
) : BaseDataSource(false) {

    private var randomAccessFile: jcifs.smb.SmbRandomAccessFile? = null
    private var bytesRemaining: Long = 0
    private var openedUri: Uri? = null

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val serverId = serverConfig.id
        val context = RemoteServerManager.getSmbContext(serverId)
            ?: throw IOException("SMB session not initialized for server $serverId")

        val cleanPath = remotePath.trimStart('/')
        val url = SmbTransport.buildUrlFor(serverConfig, cleanPath)
        val file = try {
            jcifs.smb.SmbFile(url, context)
        } catch (e: java.net.MalformedURLException) {
            throw IOException("Invalid SMB URL for $remotePath", e)
        }

        val fileLength = try {
            file.length()
        } catch (e: jcifs.smb.SmbException) {
            throw IOException("SMB open failed for $remotePath", e)
        }
        if (fileLength <= 0) {
            throw IOException("SMB file is empty or missing: $remotePath")
        }

        val raf = try {
            jcifs.smb.SmbRandomAccessFile(file, "r")
        } catch (e: jcifs.smb.SmbException) {
            throw IOException("SMB open failed for $remotePath", e)
        }
        if (dataSpec.position > 0) {
            raf.seek(dataSpec.position)
        }
        randomAccessFile = raf

        bytesRemaining = if (dataSpec.length != -1L) {
            dataSpec.length
        } else {
            fileLength - dataSpec.position
        }

        openedUri = Uri.parse(RemoteUri.buildUri(serverId, remotePath, yos.music.player.data.remote.ServerType.SMB))
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
        val bytesToRead = minOf(
            length.toLong(),
            bytesRemaining,
            (buffer.size - offset).toLong().coerceAtLeast(0)
        ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT
        val bytesRead = try {
            raf.read(buffer, offset, bytesToRead)
        } catch (e: jcifs.smb.SmbException) {
            throw IOException("SMB read failed for $remotePath", e)
        }
        if (bytesRead > 0) bytesRemaining -= bytesRead
        return if (bytesRead <= 0) C.RESULT_END_OF_INPUT else bytesRead
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        try { randomAccessFile?.close() } catch (_: Exception) {}
        randomAccessFile = null
        bytesRemaining = 0
    }

    private object C {
        const val RESULT_END_OF_INPUT = -1
    }
}
