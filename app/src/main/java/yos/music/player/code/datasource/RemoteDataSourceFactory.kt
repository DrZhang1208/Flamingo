package yos.music.player.code.datasource

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import yos.music.player.data.remote.RemoteServerManager
import yos.music.player.data.remote.ServerType

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
        val rawUri = uri.toString()
        val scheme = uri.scheme ?: when {
            rawUri.startsWith("webdav://") -> "webdav"
            else -> null
        }
        delegate = (when (scheme) {
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
        }) ?: defaultFactory.createDataSource()
        listener?.let { delegate?.addTransferListener(it) }
        return delegate!!.open(dataSpec)
    }

    private fun createWebDavSource(uri: android.net.Uri): WebDavDataSource {
        val raw = uri.toString()
        val serverId = uri.host ?: raw.substringAfter("webdav://").substringBefore("/")
        val config = findOrLoadServer(serverId) ?: throw IllegalStateException("WebDAV server not found: $serverId")
        val path = uri.path?.trimStart('/') ?: raw.substringAfter("webdav://$serverId/")
        return WebDavDataSource(config, path)
    }

    private fun findOrLoadServer(serverId: String) = RemoteServerManager.getServer(serverId) ?: run {
        val saved = yos.music.player.data.libraries.MusicLibrary.loadRemoteServers()
        if (!saved.isNullOrBlank()) RemoteServerManager.loadConfigs(saved)
        RemoteServerManager.getServer(serverId)
    }

    private fun findMatchingWebDavServer(httpUrl: String): yos.music.player.data.remote.ServerConfig? {
        val servers = RemoteServerManager.getAllServers()
        if (servers.isEmpty()) {
            val saved = yos.music.player.data.libraries.MusicLibrary.loadRemoteServers()
            if (!saved.isNullOrBlank()) RemoteServerManager.loadConfigs(saved)
        }
        return RemoteServerManager.getAllServers().firstOrNull { cfg ->
            cfg.type == ServerType.WEBDAV && httpUrl.startsWith(cfg.host.trimEnd('/'))
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
