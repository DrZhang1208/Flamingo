package yos.music.player.code.datasource

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import yos.music.player.data.remote.RemoteServerManager

class RemoteDataSourceFactory(
    private val context: Context
) : DataSource.Factory {

    private val defaultFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource {
        return RemoteDataSource(context, defaultFactory)
    }
}

class RemoteDataSource(
    private val context: Context,
    private val defaultFactory: DataSource.Factory
) : DataSource {

    private var delegate: DataSource? = null
    private var listener: TransferListener? = null

    override fun addTransferListener(listener: TransferListener) {
        this.listener = listener
        delegate?.addTransferListener(listener)
    }

    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        val uri = dataSpec.uri
        android.util.Log.e("FlamingoDS", "open scheme=${uri.scheme} host=${uri.host} path=${uri.path}")
        delegate = when (uri.scheme) {
            "smb" -> createSmbSource(uri)
            "webdav" -> createWebDavSource(uri)
            "http", "https" -> {
                val matchingServer = findMatchingWebDavServer(uri.toString())
                if (matchingServer != null) {
                    val baseUrl = matchingServer.host.trimEnd('/')
                    val relativePath = uri.toString().removePrefix(baseUrl).trimStart('/')
                    WebDavDataSource(matchingServer, relativePath)
                } else {
                    defaultFactory.createDataSource()
                }
            }
            else -> defaultFactory.createDataSource()
        }
        listener?.let { delegate?.addTransferListener(it) }
        return delegate!!.open(dataSpec)
    }

    private fun createSmbSource(uri: android.net.Uri): SmbDataSource {
        val serverId = uri.host ?: throw IllegalStateException("SMB URI missing server ID: $uri")
        val config = findOrLoadServer(serverId) ?: throw IllegalStateException("SMB server not found: $serverId")
        val path = uri.path?.trimStart('/') ?: ""
        return SmbDataSource(config, path)
    }

    private fun createWebDavSource(uri: android.net.Uri): WebDavDataSource {
        val serverId = uri.host ?: throw IllegalStateException("WebDAV URI missing server ID: $uri")
        val config = findOrLoadServer(serverId) ?: throw IllegalStateException("WebDAV server not found: $serverId")
        val path = uri.path?.trimStart('/') ?: ""
        return WebDavDataSource(config, path)
    }

    private fun findOrLoadServer(serverId: String) = RemoteServerManager.getServer(serverId) ?: run {
        val saved = yos.music.player.data.libraries.MusicLibrary.loadRemoteServers()
        if (!saved.isNullOrBlank()) RemoteServerManager.loadConfigs(saved)
        RemoteServerManager.getServer(serverId)
    }

    /** 查找 HTTP URL 是否匹配已注册的 WebDAV 服务器 */
    private fun findMatchingWebDavServer(httpUrl: String): yos.music.player.data.remote.ServerConfig? {
        val servers = RemoteServerManager.getAllServers()
        if (servers.isEmpty()) {
            val saved = yos.music.player.data.libraries.MusicLibrary.loadRemoteServers()
            if (!saved.isNullOrBlank()) RemoteServerManager.loadConfigs(saved)
        }
        return RemoteServerManager.getAllServers().firstOrNull { cfg ->
            cfg.type == yos.music.player.data.remote.ServerType.WEBDAV && httpUrl.startsWith(cfg.host.trimEnd('/'))
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    override fun getUri(): android.net.Uri? = delegate?.uri

    override fun close() { delegate?.close() }

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

    private object C { const val RESULT_END_OF_INPUT = -1 }
}
