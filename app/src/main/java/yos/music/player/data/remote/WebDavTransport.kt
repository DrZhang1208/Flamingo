package yos.music.player.data.remote

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * WebDAV 传输实现：OkHttp 手写 PROPFIND / Range GET，解析失败降级 HTML 目录页。
 * 逻辑自 RemoteServerManager 平移，语义不变。
 */
internal class WebDavTransport : RemoteTransport {

    private val clients = ConcurrentHashMap<String, OkHttpClient>()

    var lastListBody: String = ""
        private set

    fun clientFor(serverId: String): OkHttpClient? = clients[serverId]

    override fun connect(config: ServerConfig, password: String?) {
        val client = buildClient(config, password)
        verifyClient(config, client)
        clients[config.id] = client
    }

    override fun disconnect(serverId: String) {
        clients.remove(serverId)
    }

    override fun hasClient(serverId: String): Boolean = clients.containsKey(serverId)

    override fun testConnection(config: ServerConfig, password: String?): String {
        val client = buildClient(config, password)
        client.newCall(
            Request.Builder().url(config.host).method("PROPFIND", propfindBody).header("Depth", "0").build()
        ).execute().use { resp ->
            return if (resp.isSuccessful) "连接成功，根目录可达" else "连接失败: HTTP ${resp.code}"
        }
    }

    override fun listFolder(config: ServerConfig, remotePath: String): List<RemoteFile> {
        val client = clients[config.id] ?: run {
            clients[config.id] = buildClient(config, null)
            clients[config.id] ?: return emptyList()
        }
        val url = buildUrl(config, remotePath).trimEnd('/') + "/"

        val body = client.newCall(
            Request.Builder().url(url)
                .method("PROPFIND", propfindBody)
                .header("Depth", "1")
                .header("User-Agent", "Flamingo/1.0")
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw java.io.IOException("WebDAV PROPFIND failed: HTTP ${resp.code}")
            }
            resp.body?.string() ?: ""
        }

        lastListBody = body

        if (body.isNotBlank()) {
            val selfPath = android.net.Uri.parse(url).path?.trimEnd('/') ?: ""
            val result = parsePropfind(body, remotePath, selfPath)
            if (result.isNotEmpty()) return result
        }

        val getBody = client.newCall(
            Request.Builder().url(url).header("User-Agent", "Flamingo/1.0").build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw java.io.IOException("WebDAV list fallback failed: HTTP ${resp.code}")
            }
            resp.body?.string() ?: ""
        }
        return if (getBody.isNotBlank()) parseHtmlDirectory(getBody, remotePath, url) else emptyList()
    }

    override fun readFileBytes(config: ServerConfig, remotePath: String, offset: Long, length: Int): ByteArray {
        val client = clients[config.id] ?: return ByteArray(0)
        val url = buildUrl(config, remotePath)
        val req = Request.Builder().url(url)
        if (length > 0) req.header("Range", "bytes=$offset-${offset + length - 1}")
        val resp = client.newCall(req.build()).execute()
        return readHttpRange(resp, offset, length)
    }

    override fun openFileStream(config: ServerConfig, remotePath: String): java.io.InputStream {
        val client = clients[config.id] ?: throw IllegalStateException("Not connected")
        val url = buildUrl(config, remotePath)
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        // 未成功或无 body 必须显式关闭响应，否则连接会被卡住直到 GC；
        // 之前直接返回 byteStream 可能把 4xx/5xx 的错误正文当音频流播放。
        if (!resp.isSuccessful) {
            val code = resp.code
            resp.close()
            throw java.io.IOException("WebDAV open failed: HTTP $code for $remotePath")
        }
        val body = resp.body
        if (body == null) {
            resp.close()
            throw java.io.IOException("WebDAV empty body for $remotePath")
        }
        return body.byteStream()
    }

    override fun getFileSize(config: ServerConfig, remotePath: String): Long {
        val url = buildUrl(config, remotePath)
        val resp = clients[config.id]?.newCall(Request.Builder().url(url).head().build())?.execute() ?: return 0L
        // HEAD 响应也必须关闭，否则连接占住直到 GC
        resp.use { return it.body?.contentLength()?.coerceAtLeast(0) ?: 0L }
    }

    // --- helpers ---

    private fun buildClient(config: ServerConfig, password: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                if (config.authRequired) {
                    req.header("Authorization", Credentials.basic(config.username ?: "", password ?: RemoteServerManager.getPassword(config.id) ?: ""))
                }
                chain.proceed(req.build())
            }
        if (config.skipSslVerify) {
            val trustAll = object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(c: Array<java.security.cert.X509Certificate>, a: String) {}
                override fun checkServerTrusted(c: Array<java.security.cert.X509Certificate>, a: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustAll), java.security.SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAll)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    private fun buildUrl(config: ServerConfig, path: String): String {
        val base = config.host.trimEnd('/')
        val clean = path.trimStart('/')
        return if (clean.isEmpty()) base else "$base/$clean"
    }

    private fun verifyClient(config: ServerConfig, client: OkHttpClient) {
        client.newCall(
            Request.Builder()
                .url(config.host)
                .method("PROPFIND", propfindBody)
                .header("Depth", "0")
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw java.io.IOException("HTTP ${resp.code}")
            }
        }
    }

    private val propfindBody = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:propfind xmlns:D="DAV:"><D:prop><D:displayname/><D:getcontentlength/><D:getlastmodified/><D:resourcetype/></D:prop></D:propfind>
    """.trimIndent().toRequestBody("application/xml".toMediaType())

    private fun parseHtmlDirectory(html: String, parentPath: String, baseUrl: String): List<RemoteFile> {
        val results = mutableListOf<RemoteFile>()
        val linkRegex = Regex("""<a\s+href\s*=\s*["']([^"']+)["'][^>]*>\s*(.*?)\s*</a>""", RegexOption.IGNORE_CASE)
        for (m in linkRegex.findAll(html)) {
            val href = m.groupValues[1]
            val name = m.groupValues[2].ifBlank { href.trimEnd('/').substringAfterLast('/') }
            if (name == ".." || name == "../" || name == "Parent Directory" || href == "../") continue
            val isDir = href.endsWith("/")
            val cleanHref = href.trimEnd('/')
            val resolvedPath = if (parentPath.isEmpty()) cleanHref else "$parentPath/$cleanHref"
            results.add(RemoteFile(name, resolvedPath, isDir, 0L, System.currentTimeMillis()))
        }
        return results
    }

    private fun parsePropfind(xml: String, parentPath: String, selfPath: String = ""): List<RemoteFile> {
        val results = mutableListOf<RemoteFile>()
        val responseTag = Regex("<(\\w+:)?response>", RegexOption.IGNORE_CASE)
        val closeTag = Regex("</(\\w+:)?response>", RegexOption.IGNORE_CASE)
        var searchFrom = 0
        while (true) {
            val start = responseTag.find(xml, searchFrom)?.range?.last?.plus(1) ?: break
            val end = closeTag.find(xml, start)?.range?.first ?: break
            val block = xml.substring(start, end)
            searchFrom = end + 1

            val href = extractTag(block, "href")
            val displayName = extractTag(block, "displayname")
            val lastModified = extractTag(block, "getlastmodified")
            val sizeStr = extractTag(block, "getcontentlength")
            val isCollection = block.contains(Regex("<(\\w+:)?collection[/>]", RegexOption.IGNORE_CASE))

            if (href.isNullOrBlank()) continue

            val rawName = displayName?.takeIf { it.isNotBlank() }
                ?: href.trimEnd('/').substringAfterLast('/').ifEmpty { href.trimEnd('/') }
            val name = runCatching { java.net.URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)
            if (name.isBlank()) continue

            val resolvedPath = resolveRelativePath(parentPath.trimEnd('/'), href.trimEnd('/'))
            if (resolvedPath == parentPath.trimEnd('/')) continue
            val hrefLast = href.trimEnd('/').substringAfterLast('/')
            val selfLast = selfPath.substringAfterLast('/').ifEmpty { selfPath.substringAfterLast('/') }
            val hrefDecoded = runCatching { java.net.URLDecoder.decode(hrefLast, "UTF-8") }.getOrDefault(hrefLast)
            val selfDecoded = runCatching { java.net.URLDecoder.decode(selfLast, "UTF-8") }.getOrDefault(selfLast)
            if (hrefDecoded == selfDecoded || hrefLast == selfLast) continue

            val size = sizeStr?.toLongOrNull() ?: 0L
            val modMs = runCatching { java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).parse(lastModified ?: "")?.time }.getOrNull() ?: System.currentTimeMillis()
            results.add(RemoteFile(name, resolvedPath, isCollection, size, modMs))
        }
        return results
    }

    private fun extractTag(xml: String, tagName: String): String? {
        val open = Regex("<(\\w+:)?$tagName[^>]*>", RegexOption.IGNORE_CASE)
        val close = Regex("</(\\w+:)?$tagName>", RegexOption.IGNORE_CASE)
        val start = open.find(xml)?.range?.last?.plus(1) ?: return null
        val end = close.find(xml, start)?.range?.first ?: return null
        return xml.substring(start, end).trim()
    }

    private fun resolveRelativePath(basePath: String, href: String): String {
        val name = href.substringAfterLast('/')
        return if (basePath.isEmpty()) name else "$basePath/$name"
    }
}
