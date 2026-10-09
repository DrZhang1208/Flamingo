package yos.music.player.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ServerType { WEBDAV }
enum class RemoteConnectionState { UNKNOWN, CHECKING, CONNECTED, DISCONNECTED }

data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: ServerType,
    val label: String,
    val host: String,
    val port: Int = 8080,
    val shareName: String? = null,
    val basePath: String = "/",
    val username: String? = null,
    val authRequired: Boolean = true,
    val skipSslVerify: Boolean = false,
    val mountFolders: List<String> = emptyList()
)

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedDate: Long
)

object RemoteServerManager {
    private const val CRED_PREFS_NAME = "remote_servers_creds"

    private val gson = Gson()
    private val credLock = Any()

    private val configCache = java.util.concurrent.CopyOnWriteArrayList<ServerConfig>()
    @Volatile private var credPrefs: android.content.SharedPreferences? = null

    private val webDavClients = java.util.concurrent.ConcurrentHashMap<String, OkHttpClient>()
    private val _connectionStates = MutableStateFlow<Map<String, RemoteConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, RemoteConnectionState>> = _connectionStates
    private val serverErrors = java.util.concurrent.ConcurrentHashMap<String, String>()

    var lastListBody: String = ""
        private set

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus", "ape", "aiff", "alac", "dsf", "dff"
    )

    @Volatile var appContext: Context? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        if (credPrefs == null) {
            val appCtx = context.applicationContext
            Thread {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                ensureCredPrefs(appCtx)
            }.start()
        }
    }

    private fun ensureCredPrefs(context: Context? = appContext): android.content.SharedPreferences? {
        credPrefs?.let { return it }
        val appCtx = context ?: return null
        synchronized(credLock) {
            credPrefs?.let { return it }
            credPrefs = try {
                val masterKey = MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appCtx, CRED_PREFS_NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                appCtx.getSharedPreferences(CRED_PREFS_NAME, Context.MODE_PRIVATE)
            }
            return credPrefs
        }
    }

    // --- Config persistence ---

    fun addServer(config: ServerConfig, password: String?): ServerConfig {
        val finalConfig = config.copy(id = config.id.ifEmpty { UUID.randomUUID().toString() })
        configCache.add(finalConfig)
        if (password != null) ensureCredPrefs()?.edit()?.putString("pwd_${finalConfig.id}", password)?.commit()
        updateConnectionState(finalConfig.id, RemoteConnectionState.UNKNOWN)
        serverErrors.remove(finalConfig.id)
        return finalConfig
    }

    fun updateServer(config: ServerConfig, password: String?) {
        configCache.removeAll { it.id == config.id }; configCache.add(config)
        if (password != null) ensureCredPrefs()?.edit()?.putString("pwd_${config.id}", password)?.apply()
        else if (!config.authRequired) ensureCredPrefs()?.edit()?.remove("pwd_${config.id}")?.apply()
        disconnect(config.id)
        updateConnectionState(config.id, RemoteConnectionState.UNKNOWN)
        serverErrors.remove(config.id)
    }

    fun removeServer(serverId: String) {
        configCache.removeAll { it.id == serverId }
        ensureCredPrefs()?.edit()?.remove("pwd_$serverId")?.apply()
        disconnect(serverId)
        _connectionStates.update { it - serverId }
        serverErrors.remove(serverId)
    }

    fun getServer(serverId: String) = configCache.find { it.id == serverId }
    fun getAllServers() = configCache.toList()
    fun getPassword(serverId: String) = ensureCredPrefs()?.getString("pwd_$serverId", null)

    fun loadConfigs(json: String?) {
        configCache.clear()
        if (!json.isNullOrBlank()) {
            configCache.addAll(gson.fromJson(json, object : TypeToken<List<ServerConfig>>() {}.type))
        }
        syncConnectionStates()
    }

    fun saveConfigs(): String = gson.toJson(configCache)

    // --- Connection ---

    fun testConnection(config: ServerConfig, password: String?): String {
        if (config.host.isBlank()) return "请输入 WebDAV 地址"
        return runCatching {
            testWebDav(config, password)
        }.getOrElse { e ->
            val msg = (e.message ?: e.javaClass.simpleName).ifBlank { e.javaClass.simpleName }
            when (e) {
                is java.net.UnknownHostException -> "连接失败：无法解析主机（请检查地址是否正确）"
                is java.net.SocketTimeoutException -> "连接失败：超时（请检查网络、端口、服务器在线状态）"
                else -> "连接失败：$msg"
            }
        }
    }

    private fun testWebDav(config: ServerConfig, password: String?): String {
        val client = buildWebDavClient(config, password)
        val resp = client.newCall(Request.Builder().url(config.host).method("PROPFIND", propfindBody).header("Depth", "0").build()).execute()
        return if (resp.isSuccessful) "连接成功，根目录可达" else "连接失败: HTTP ${resp.code}"
    }

    fun connect(serverId: String, password: String? = null): Boolean {
        val cfg = getServer(serverId) ?: return false
        updateConnectionState(serverId, RemoteConnectionState.CHECKING)
        serverErrors.remove(serverId)
        lastParseError = ""
        return runCatching {
            val client = buildWebDavClient(cfg, password ?: getPassword(serverId))
            verifyWebDavClient(cfg, client)
            webDavClients[serverId] = client
            updateConnectionState(serverId, RemoteConnectionState.CONNECTED)
            true
        }.getOrElse { e ->
            webDavClients.remove(serverId)
            markServerFailure(serverId, cfg, e)
            false
        }
    }

    private fun connectWebDav(config: ServerConfig, password: String? = null): Boolean {
        val pwd = password ?: getPassword(config.id)
        webDavClients[config.id] = buildWebDavClient(config, pwd)
        return true
    }

    fun disconnect(serverId: String) {
        webDavClients.remove(serverId)
        updateConnectionState(serverId, RemoteConnectionState.DISCONNECTED)
    }

    fun disconnectAll() = webDavClients.keys.toList().forEach { disconnect(it) }

    fun isConnected(serverId: String) = getConnectionState(serverId) == RemoteConnectionState.CONNECTED

    fun getConnectionState(serverId: String): RemoteConnectionState {
        return _connectionStates.value[serverId] ?: RemoteConnectionState.UNKNOWN
    }

    fun getConnectionError(serverId: String): String? = serverErrors[serverId]

    fun refreshConnectionState(serverId: String): Boolean = connect(serverId)

    fun refreshAllConnectionStates() {
        configCache.forEach { config ->
            connect(config.id)
        }
    }

    fun getWebDavClient(serverId: String): OkHttpClient? = webDavClients[serverId]

    // --- File listing ---

    fun listFolder(serverId: String, remotePath: String): List<RemoteFile> {
        val cfg = getServer(serverId) ?: return emptyList()
        return runCatching {
            listFolderOrThrow(serverId, remotePath)
        }.getOrElse { e ->
            markServerFailure(serverId, cfg, e)
            emptyList()
        }
    }

    fun listAudioFiles(serverId: String, remotePath: String): List<RemoteFile> {
        return listFolder(serverId, remotePath).filter { !it.isDirectory && isAudioFile(it.name) }
    }

    fun listFolderOrThrow(serverId: String, remotePath: String): List<RemoteFile> {
        val cfg = getServer(serverId) ?: return emptyList()
        return try {
            val result = listWebDavOrThrow(serverId, remotePath)
            lastParseError = ""
            serverErrors.remove(serverId)
            updateConnectionState(serverId, RemoteConnectionState.CONNECTED)
            result
        } catch (e: Exception) {
            markServerFailure(serverId, cfg, e)
            throw e
        }
    }

    fun listAudioFilesOrThrow(serverId: String, remotePath: String): List<RemoteFile> {
        return listFolderOrThrow(serverId, remotePath).filter { !it.isDirectory && isAudioFile(it.name) }
    }

    private fun isAudioFile(name: String) = name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    // --- File reading ---

    fun readFileBytes(serverId: String, remotePath: String, offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length > 0)
        return readWebDavBytes(serverId, remotePath, offset, length)
    }

    fun openFileStream(serverId: String, remotePath: String): InputStream {
        val cfg = getServer(serverId) ?: throw IllegalStateException("Server not found")
        return openWebDavStream(serverId, remotePath)
    }

    fun getFileSize(serverId: String, remotePath: String): Long {
        val cfg = getServer(serverId) ?: return 0L
        return runCatching {
            val url = buildWebDavUrl(cfg, remotePath)
            val resp = webDavClients[serverId]?.newCall(Request.Builder().url(url).head().build())?.execute()
            updateConnectionState(serverId, RemoteConnectionState.CONNECTED)
            resp?.body?.contentLength()?.coerceAtLeast(0) ?: 0L
        }.getOrElse {
            markServerFailure(serverId, cfg, it)
            0L
        }
    }

    private fun readWebDavBytes(serverId: String, path: String, offset: Long, length: Int): ByteArray {
        val cfg = getServer(serverId) ?: return ByteArray(0)
        val client = webDavClients[serverId] ?: return ByteArray(0)
        val url = buildWebDavUrl(cfg, path)
        val req = Request.Builder().url(url)
        if (length > 0) req.header("Range", "bytes=$offset-${offset + length - 1}")
        val resp = client.newCall(req.build()).execute()
        return try {
            val bytes = readHttpRange(resp, offset, length)
            updateConnectionState(serverId, RemoteConnectionState.CONNECTED)
            bytes
        } catch (e: Exception) {
            markServerFailure(serverId, cfg, e)
            throw e
        } finally {
            resp.close()
        }
    }

    private fun openWebDavStream(serverId: String, path: String): InputStream {
        val cfg = getServer(serverId) ?: throw IllegalStateException("Server not found")
        val client = webDavClients[serverId] ?: throw IllegalStateException("Not connected")
        val url = buildWebDavUrl(cfg, path)
        try {
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            // 未成功或无 body 必须显式关闭响应，否则连接会被卡住直到 GC；
            // 之前直接返回 byteStream 可能把 4xx/5xx 的错误正文当音频流播放。
            if (!resp.isSuccessful) {
                val code = resp.code
                resp.close()
                throw java.io.IOException("WebDAV open failed: HTTP $code for $path")
            }
            val body = resp.body
            if (body == null) {
                resp.close()
                throw java.io.IOException("WebDAV empty body for $path")
            }
            updateConnectionState(serverId, RemoteConnectionState.CONNECTED)
            return body.byteStream()
        } catch (e: Exception) {
            markServerFailure(serverId, cfg, e)
            throw e
        }
    }

    // --- WebDAV helpers ---

    private fun buildWebDavClient(config: ServerConfig, password: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                if (config.authRequired) {
                    req.header("Authorization", Credentials.basic(config.username ?: "", password ?: getPassword(config.id) ?: ""))
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

    private fun buildWebDavUrl(config: ServerConfig, path: String): String {
        val base = config.host.trimEnd('/')
        val clean = path.trimStart('/')
        return if (clean.isEmpty()) base else "$base/$clean"
    }

    private fun listWebDavOrThrow(serverId: String, remotePath: String): List<RemoteFile> {
        val cfg = getServer(serverId) ?: return emptyList()
        val client = webDavClients[serverId] ?: run {
            connectWebDav(cfg)
            webDavClients[serverId] ?: return emptyList()
        }
        val url = buildWebDavUrl(cfg, remotePath).trimEnd('/') + "/"

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

    private val propfindBody = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:propfind xmlns:D="DAV:"><D:prop><D:displayname/><D:getcontentlength/><D:getlastmodified/><D:resourcetype/></D:prop></D:propfind>
    """.trimIndent().toRequestBody("application/xml".toMediaType())

    var lastParseError: String = ""
        private set

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

    private fun connectionErrorMessage(cfg: ServerConfig, e: Throwable): String {
        val msg = (e.message ?: e.javaClass.simpleName).ifBlank { e.javaClass.simpleName }
        return when (e) {
            is javax.net.ssl.SSLHandshakeException, is javax.net.ssl.SSLException -> {
                val schemeHint = if (cfg.host.trim().startsWith("https://", ignoreCase = true)) {
                    "（如果服务器是 http，请把地址改成 http://；若是自签名证书可勾选「跳过 SSL 证书验证」）"
                } else {
                    "（若使用 https 且为自签名证书可勾选「跳过 SSL 证书验证」）"
                }
                "WebDAV SSL 握手失败：$msg $schemeHint"
            }
            is java.net.UnknownHostException -> "连接失败：无法解析主机（请检查地址是否正确）"
            is java.net.SocketTimeoutException -> "连接失败：超时（请检查网络、端口、服务器在线状态）"
            else -> "连接失败：$msg"
        }
    }

    private fun verifyWebDavClient(config: ServerConfig, client: OkHttpClient) {
        client.newCall(
            Request.Builder()
                .url(config.host)
                .method("PROPFIND", propfindBody)
                .header("Depth", "0")
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}")
            }
        }
    }

    private fun updateConnectionState(serverId: String, state: RemoteConnectionState) {
        _connectionStates.update { states -> states + (serverId to state) }
    }

    private fun syncConnectionStates() {
        val activeIds = configCache.map { it.id }.toSet()
        webDavClients.keys.removeIf { it !in activeIds }
        serverErrors.keys.removeIf { it !in activeIds }
        _connectionStates.update { old ->
            activeIds.associateWith { id -> old[id] ?: RemoteConnectionState.UNKNOWN }
        }
    }

    private fun markServerFailure(serverId: String, cfg: ServerConfig, e: Throwable) {
        val message = connectionErrorMessage(cfg, e)
        lastParseError = message
        serverErrors[serverId] = message
        webDavClients.remove(serverId)
        updateConnectionState(serverId, RemoteConnectionState.DISCONNECTED)
    }
}
