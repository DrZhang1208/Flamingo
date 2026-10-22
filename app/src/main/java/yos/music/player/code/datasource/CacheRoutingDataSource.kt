package yos.music.player.code.datasource

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Routes remote (WebDAV/http) requests through the playback cache while local
 * files read directly. Caching local files only duplicates disk content and
 * adds a write turnaround to every first play.
 */
internal fun shouldRouteToPlaybackCache(rawUri: String, scheme: String?): Boolean = when (scheme) {
    "webdav", "http", "https" -> true
    else -> scheme == null && rawUri.startsWith("webdav://")
}

internal class CacheRoutingDataSourceFactory(
    private val directFactory: DataSource.Factory,
    private val cachedFactory: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource = CacheRoutingDataSource(
        directFactory.createDataSource(),
        cachedFactory.createDataSource()
    )
}

private class CacheRoutingDataSource(
    private val direct: DataSource,
    private val cached: DataSource
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        direct.addTransferListener(transferListener)
        cached.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        active = if (shouldRouteToPlaybackCache(uri.toString(), uri.scheme)) cached else direct
        return active!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: -1

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        val current = active
        active = null
        current?.close()
    }
}
