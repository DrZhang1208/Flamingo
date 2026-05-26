package yos.music.player.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ServerType { SMB, WEBDAV }

data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: ServerType,
    val label: String,
    val host: String,
    val port: Int = if (type == ServerType.SMB) 445 else 8080,
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

    private val configCache = mutableListOf<ServerConfig>()
    private var credPrefs: android.content.SharedPreferences? = null

    // Connections
    private val smbClients = java.util.concurrent.ConcurrentHashMap<String, SMBClient>()
    private val smbSessions = java.util.concurrent.ConcurrentHashMap<String, Session>()
    private val smbShares = java.util.concurrent.ConcurrentHashMap<String, DiskShare>()
    private val webDavClients = java.util.concurrent.ConcurrentHashMap<String, OkHttpClient>()

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
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                credPrefs = EncryptedSharedPreferences.create(
                    context, CRED_PREFS_NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // 部分设备（MIUI 等）Keystore 不可用，回退到普通 SharedPreferences
                credPrefs = context.getSharedPreferences(CRED_PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    // --- Config persistence ---

    fun addServer(config: ServerConfig, password: String?): ServerConfig {
        val finalConfig = config.copy(id = config.id.ifEmpty { UUID.randomUUID().toString() })
        configCache.add(finalConfig)
        if (password != null) credPrefs?.edit()?.putString("pwd_${finalConfig.id}", password)?.commit()
        return finalConfig
    }

    fun updateServer(config: ServerConfig, password: String?) {
        configCache.removeAll { it.id == config.id }; configCache.add(config)
        if (password != null) credPrefs?.edit()?.putString("pwd_${config.id}", password)?.apply()
        else if (!config.authRequired) credPrefs?.edit()?.remove("pwd_${config.id}")?.apply()
        disconnect(config.id)
    }

    fun removeServer(serverId: String) {
        configCache.removeAll { it.id == serverId }
        credPrefs?.edit()?.remove("pwd_$serverId")?.apply()
        disconnect(serverId)
    }

    fun getServer(serverId: String) = configCache.find { it.id == serverId }
    fun getAllServers() = configCache.toList()
    fun getPassword(serverId: String) = credPrefs?.getString("pwd_$serverId", null)

    fun loadConfigs(json: String?) {
        configCache.clear()
        if (!json.isNullOrBlank()) {
            configCache.addAll(gson.fromJson(json, object : TypeToken<List<ServerConfig>>() {}.type))
        }
    }

    fun saveConfigs(): String = gson.toJson(configCache)

    // --- Connection ---

    fun testConnection(config: ServerConfig, password: String?): String {
        if (config.type == ServerType.WEBDAV && config.host.isBlank()) return "请输入 WebDAV 地址"
        return when (config.type) {
            ServerType.SMB -> testSmb(config, password)
            ServerType.WEBDAV -> testWebDav(config, password)
        }
    }

    private fun testSmb(config: ServerConfig, password: String?): String {
        val client = SMBClient(SmbConfig.builder().withTimeout(15, TimeUnit.SECONDS).withSoTimeout(15, TimeUnit.SECONDS).build())
        return try {
            val conn = client.connect(config.host, config.port)
            val session = authenticateSmb(conn, config, password)
            val share = session.connectShare(config.shareName ?: return "SMB 需要共享名") as DiskShare
            val files = share.list("", "*")
            share.close(); session.close(); conn.close()
            "连接成功，根目录 ${files.size} 个项目"
        } finally {
            client.close()
        }
    }

    private fun testWebDav(config: ServerConfig, password: String?): String {
        val client = buildWebDavClient(config, password)
        val resp = client.newCall(Request.Builder().url(config.host).method("PROPFIND", propfindBody).header("Depth", "0").build()).execute()
        return if (resp.isSuccessful) "连接成功，根目录可达" else "连接失败: HTTP ${resp.code}"
    }

    fun connect(serverId: String, password: String? = null): Boolean {
        return runCatching {
            val cfg = getServer(serverId) ?: return false
            when (cfg.type) {
                ServerType.SMB -> connectSmb(cfg)
                ServerType.WEBDAV -> connectWebDav(cfg, password)
            }
        }.isSuccess
    }

    private fun connectSmb(config: ServerConfig): Boolean {
        val client = SMBClient(SmbConfig.builder().withTimeout(30, TimeUnit.SECONDS).withSoTimeout(30, TimeUnit.SECONDS).build())
        val conn = client.connect(config.host, config.port)
        val session = authenticateSmb(conn, config, getPassword(config.id))
        val share = session.connectShare(config.shareName!!) as DiskShare
        smbClients[config.id] = client; smbSessions[config.id] = session; smbShares[config.id] = share
        return true
    }

    private fun authenticateSmb(conn: Connection, config: ServerConfig, password: String?): Session {
        return if (config.authRequired) {
            conn.authenticate(AuthenticationContext(config.username ?: "guest", (password ?: "").toCharArray(), ""))
        } else {
            conn.authenticate(AuthenticationContext.guest())
        }
    }

    private fun connectWebDav(config: ServerConfig, password: String? = null): Boolean {
        val pwd = password ?: getPassword(config.id)
        webDavClients[config.id] = buildWebDavClient(config, pwd)
        return true
    }

    fun disconnect(serverId: String) {
        runCatching { smbShares[serverId]?.close() }
        runCatching { smbSessions[serverId]?.close() }
        runCatching { smbClients[serverId]?.close() }
        smbShares.remove(serverId); smbSessions.remove(serverId); smbClients.remove(serverId)
        webDavClients.remove(serverId)
    }

    fun disconnectAll() = smbShares.keys.toList().forEach { disconnect(it) }

    fun isConnected(serverId: String) = getServer(serverId)?.let {
        when (it.type) { ServerType.SMB -> smbShares.containsKey(serverId); ServerType.WEBDAV -> webDavClients.containsKey(serverId) }
    } ?: false

    // --- File listing ---

    fun listFolder(serverId: String, remotePath: String): List<RemoteFile> {
        val cfg = getServer(serverId) ?: return emptyList()
        return when (cfg.type) {
            ServerType.SMB -> listSmb(serverId, remotePath)
            ServerType.WEBDAV -> listWebDav(serverId, remotePath)
        }
    }

    fun listAudioFiles(serverId: String, remotePath: String): List<RemoteFile> {
        return listFolder(serverId, remotePath).filter { !it.isDirectory && isAudioFile(it.name) }
    }

    private fun isAudioFile(name: String) = name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    private fun listSmb(serverId: String, path: String): List<RemoteFile> {
        val share = smbShares[serverId] ?: return emptyList()
        val clean = path.trim('/').replace('/', '\\')
        val search = if (clean.isEmpty()) "*" else "$clean\\*"
        return share.list(clean, search).map { f -> f.toRemoteFile(isDir = false) }.mapNotNull { rf ->
            if (isSmbDir(share, rf, clean)) {
                rf.copy(isDirectory = true, size = 0L)
            } else {
                rf
            }
        }
    }

    private fun FileIdBothDirectoryInformation.toRemoteFile(isDir: Boolean): RemoteFile {
        return RemoteFile(
            name = fileName,
            path = fileName,
            isDirectory = isDir,
            size = if (isDir) 0L else endOfFile,
            modifiedDate = lastWriteTime.toEpochMillis()
        )
    }

    private fun isSmbDir(share: DiskShare, rf: RemoteFile, parentPath: String): Boolean {
        return runCatching {
            val fullPath = if (parentPath.isEmpty()) rf.name.replace('/', '\\') else "$parentPath\\${rf.name}".replace('/', '\\')
            val info = share.getFileInformation(fullPath)
            (info.basicInformation.fileAttributes and 0x10L) != 0L // FILE_ATTRIBUTE_DIRECTORY
        }.getOrDefault(false)
    }

    // --- File reading ---

    fun readFileBytes(serverId: String, remotePath: String, offset: Long, length: Int): ByteArray {
        val cfg = getServer(serverId) ?: return ByteArray(0)
        return when (cfg.type) {
            ServerType.SMB -> readSmbBytes(serverId, remotePath, offset, length)
            ServerType.WEBDAV -> readWebDavBytes(serverId, remotePath, offset, length)
        }
    }

    fun openFileStream(serverId: String, remotePath: String): InputStream {
        val cfg = getServer(serverId) ?: throw IllegalStateException("Server not found")
        return when (cfg.type) {
            ServerType.SMB -> openSmbStream(serverId, remotePath)
            ServerType.WEBDAV -> openWebDavStream(serverId, remotePath)
        }
    }

    fun getFileSize(serverId: String, remotePath: String): Long {
        return runCatching {
            val cfg = getServer(serverId) ?: return 0L
            when (cfg.type) {
                ServerType.SMB -> {
                    val path = remotePath.replace('/', '\\')
                    smbShares[serverId]?.getFileInformation(path)?.standardInformation?.endOfFile ?: 0L
                }
                ServerType.WEBDAV -> {
                    val url = buildWebDavUrl(cfg, remotePath)
                    val resp = webDavClients[serverId]?.newCall(Request.Builder().url(url).head().build())?.execute()
                    resp?.body?.contentLength()?.coerceAtLeast(0) ?: 0L
                }
            }
        }.getOrDefault(0L)
    }

    private fun readSmbBytes(serverId: String, path: String, offset: Long, length: Int): ByteArray {
        val share = smbShares[serverId] ?: return ByteArray(0)
        val smbPath = path.replace('/', '\\')
        val file = share.openFile(
            smbPath, EnumSet.of(AccessMask.GENERIC_READ), null,
            EnumSet.noneOf(SMB2ShareAccess::class.java), SMB2CreateDisposition.FILE_OPEN, null
        )
        val size = share.getFileInformation(smbPath).standardInformation.endOfFile
        val bufLen = minOf(length, (size - offset).coerceAtLeast(0).toInt())
        val buf = ByteArray(bufLen)
        if (bufLen > 0) file.read(buf, offset)
        file.close()
        return buf
    }

    private fun openSmbStream(serverId: String, path: String): InputStream {
        val share = smbShares[serverId] ?: throw IllegalStateException("Not connected: $serverId")
        val smbPath = path.replace('/', '\\')
        val file = share.openFile(smbPath, EnumSet.of(AccessMask.GENERIC_READ), null,
            EnumSet.noneOf(SMB2ShareAccess::class.java), SMB2CreateDisposition.FILE_OPEN, null)
        val size = share.getFileInformation(smbPath).standardInformation.endOfFile
        return object : InputStream() {
            private var pos = 0L
            override fun read(): Int { val b = ByteArray(1); return if (read(b) == 1) b[0].toInt() and 0xFF else -1 }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val remaining = size - pos; if (remaining <= 0) return -1
                val toRead = minOf(len, remaining.toInt())
                val read = file.read(b, pos, off, toRead)
                if (read > 0) pos += read; return if (read == 0) -1 else read
            }
            override fun close() { runCatching { file.close() } }
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
            if (!resp.isSuccessful) throw java.io.IOException("WebDAV read failed: HTTP ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("WebDAV empty body")
            val full = body.bytes()
            // 如果服务器不支持 Range 返回了完整文件，只取需要的部分
            if (length > 0 && full.size > length) full.copyOf(length) else full
        } finally {
            resp.close()
        }
    }

    private fun openWebDavStream(serverId: String, path: String): InputStream {
        val cfg = getServer(serverId) ?: throw IllegalStateException("Server not found")
        val client = webDavClients[serverId] ?: throw IllegalStateException("Not connected")
        val url = buildWebDavUrl(cfg, path)
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        return resp.body?.byteStream() ?: throw IllegalStateException("No body for $path")
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
        // WebDAV: host 字段存储完整 URL（如 http://192.168.1.1:8080/dav）
        val base = config.host.trimEnd('/')
        val clean = path.trimStart('/')
        return if (clean.isEmpty()) base else "$base/$clean"
    }

    private fun listWebDav(serverId: String, remotePath: String): List<RemoteFile> {
        val cfg = getServer(serverId) ?: return emptyList()
        val client = webDavClients[serverId] ?: run {
            connectWebDav(cfg)
            webDavClients[serverId] ?: return emptyList()
        }
        val url = buildWebDavUrl(cfg, remotePath).trimEnd('/') + "/"

        // Try PROPFIND
        val resp = client.newCall(
            Request.Builder().url(url)
                .method("PROPFIND", propfindBody)
                .header("Depth", "1")
                .header("User-Agent", "Flamingo/1.0")
                .build()
        ).execute()

        val body = resp.body?.string() ?: ""
        lastListBody = body

        if (resp.isSuccessful && body.isNotBlank()) {
            // 用请求 URL 的路径作为自引用标识，过滤当前目录自身
            val selfPath = android.net.Uri.parse(url).path?.trimEnd('/') ?: ""
            val result = parsePropfind(body, remotePath, selfPath)
            if (result.isNotEmpty()) return result
        }

        // Fallback: GET + parse HTML or simple file list
        val getResp = client.newCall(
            Request.Builder().url(url).header("User-Agent", "Flamingo/1.0").build()
        ).execute()
        val getBody = getResp.body?.string() ?: ""
        if (getResp.isSuccessful && getBody.isNotBlank()) {
            return parseHtmlDirectory(getBody, remotePath, url)
        }

        return emptyList()
    }

    /**
     * 解析 WebDAV 服务器返回的 HTML 目录列表（Apache/Nginx/内置索引页）
     */
    private fun parseHtmlDirectory(html: String, parentPath: String, baseUrl: String): List<RemoteFile> {
        val results = mutableListOf<RemoteFile>()
        // 匹配 <a href="...">name</a> 链接
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
        // 简单字符串解析：按 <D:response> 分割，逐块提取字段
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
            // 跳过当前目录自身：比较路径最后一段（处理中英文/编码差异）
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

    /**
     * 将 PROPFIND 返回的绝对路径 href 转为相对于 basePath 的相对路径。
     * 例：basePath="" href="/dav/Music" → "Music"
     *     basePath="Music" href="/dav/Music/Rock" → "Rock"
     */
    private fun resolveRelativePath(basePath: String, href: String): String {
        // href 的最后一个路径段作为简单相对路径（适用于 Depth: 1）
        val name = href.substringAfterLast('/')
        return if (basePath.isEmpty()) name else "$basePath/$name"
    }
}
