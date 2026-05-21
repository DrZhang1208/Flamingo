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
    private val smbClients = mutableMapOf<String, SMBClient>()
    private val smbSessions = mutableMapOf<String, Session>()
    private val smbShares = mutableMapOf<String, DiskShare>()
    private val webDavClients = mutableMapOf<String, OkHttpClient>()

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus", "ape", "aiff", "alac", "dsf", "dff"
    )

    fun init(context: Context) {
        if (credPrefs == null) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            credPrefs = EncryptedSharedPreferences.create(
                context, CRED_PREFS_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    // --- Config persistence ---

    fun addServer(config: ServerConfig, password: String?): ServerConfig {
        val finalConfig = config.copy(id = config.id.ifEmpty { UUID.randomUUID().toString() })
        configCache.add(finalConfig)
        if (password != null) credPrefs?.edit()?.putString("pwd_${finalConfig.id}", password)?.apply()
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

    fun connect(serverId: String): Boolean {
        return runCatching {
            val cfg = getServer(serverId) ?: return false
            when (cfg.type) {
                ServerType.SMB -> connectSmb(cfg)
                ServerType.WEBDAV -> connectWebDav(cfg)
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

    private fun connectWebDav(config: ServerConfig): Boolean {
        webDavClients[config.id] = buildWebDavClient(config, getPassword(config.id))
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
        return resp.body?.bytes() ?: ByteArray(0)
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
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                if (config.authRequired) {
                    req.header("Authorization", Credentials.basic(config.username ?: "", password ?: getPassword(config.id) ?: ""))
                }
                chain.proceed(req.build())
            }.build()
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
            println("WebDAV client not found for $serverId, attempting connect")
            connectWebDav(cfg)
            webDavClients[serverId] ?: return emptyList()
        }
        val url = buildWebDavUrl(cfg, remotePath).trimEnd('/') + "/"
        println("WebDAV PROPFIND url=$url")
        val resp = client.newCall(Request.Builder().url(url).method("PROPFIND", propfindBody).header("Depth", "1").build()).execute()
        println("WebDAV PROPFIND response: code=${resp.code} success=${resp.isSuccessful}")
        if (!resp.isSuccessful) {
            println("WebDAV PROPFIND failed: ${resp.message}")
            return emptyList()
        }
        val body = resp.body?.string() ?: ""
        println("WebDAV PROPFIND body length=${body.length} body=${body.take(500)}")
        val result = parsePropfind(body, remotePath)
        println("WebDAV PROPFIND parsed ${result.size} entries")
        return result
    }

    private val propfindBody = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:propfind xmlns:D="DAV:"><D:prop><D:displayname/><D:getcontentlength/><D:getlastmodified/><D:resourcetype/></D:prop></D:propfind>
    """.trimIndent().toRequestBody("application/xml".toMediaType())

    private fun parsePropfind(xml: String, parentPath: String): List<RemoteFile> {
        val results = mutableListOf<RemoteFile>()
        val ns = "(?:\\w+:)?"
        val respRegex = Regex("<${ns}response>(.*?)</${ns}response>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val hrefR = Regex("<${ns}href>(.*?)</${ns}href>", RegexOption.IGNORE_CASE)
        val nameR = Regex("<${ns}displayname>(.*?)</${ns}displayname>", RegexOption.IGNORE_CASE)
        val sizeR = Regex("<${ns}getcontentlength>(\\d+)</${ns}getcontentlength>", RegexOption.IGNORE_CASE)
        val modR = Regex("<${ns}getlastmodified>(.*?)</${ns}getlastmodified>", RegexOption.IGNORE_CASE)
        val collR = Regex("<${ns}collection/>", RegexOption.IGNORE_CASE)

        for (m in respRegex.findAll(xml)) {
            val r = m.groupValues[1]
            val rawHref = (hrefR.find(r)?.groupValues?.get(1) ?: continue).trim()
            val name = (nameR.find(r)?.groupValues?.get(1) ?: rawHref.substringAfterLast('/').ifEmpty { rawHref }).trim()
            if (name.isBlank()) continue

            // href 是服务器根路径（如 /dav/Music），需要转为相对于当前浏览路径的路径
            val href = rawHref.trimEnd('/')
            val resolvedPath = resolveRelativePath(parentPath.trimEnd('/'), href)

            // 跳过当前目录自身
            if (resolvedPath == parentPath.trimEnd('/')) continue

            val size = sizeR.find(r)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val isDir = collR.containsMatchIn(r)
            val mod = modR.find(r)?.groupValues?.get(1)
            val modMs = runCatching { java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).parse(mod ?: "")?.time }.getOrNull() ?: System.currentTimeMillis()
            results.add(RemoteFile(name, resolvedPath, isDir, size, modMs))
        }
        return results
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
