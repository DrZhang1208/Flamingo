package yos.music.player.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jcifs.smb.SmbException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.util.UUID

enum class ServerType { WEBDAV, SMB }
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
    val mountFolders: List<String> = emptyList(),
    val domain: String? = null
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

    private val webDavTransport = WebDavTransport()
    private val smbTransport = SmbTransport()
    private val transports get() = listOf<RemoteTransport>(webDavTransport, smbTransport)

    private val _connectionStates = MutableStateFlow<Map<String, RemoteConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, RemoteConnectionState>> = _connectionStates
    private val serverErrors = java.util.concurrent.ConcurrentHashMap<String, String>()

    var lastParseError: String = ""
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

    private fun transportFor(config: ServerConfig): RemoteTransport = when (config.type) {
        ServerType.WEBDAV -> webDavTransport
        ServerType.SMB -> smbTransport
    }

    fun testConnection(config: ServerConfig, password: String?): String {
        if (config.host.isBlank()) {
            return if (config.type == ServerType.SMB) "请输入服务器地址" else "请输入 WebDAV 地址"
        }
        return runCatching {
            transportFor(config).testConnection(config, password)
        }.getOrElse { e ->
            connectionErrorMessage(config, e)
        }
    }

    fun connect(serverId: String, password: String? = null): Boolean {
        val cfg = getServer(serverId) ?: return false
        updateConnectionState(serverId, RemoteConnectionState.CHECKING)
        serverErrors.remove(serverId)
        lastParseError = ""
        return runCatching {
            transportFor(cfg).connect(cfg, password ?: getPassword(serverId))
            updateConnectionState(serverId, RemoteConnectionState.CONNECTED)
            true
        }.getOrElse { e ->
            markServerFailure(serverId, cfg, e)
            false
        }
    }

    fun disconnect(serverId: String) {
        transports.forEach { it.disconnect(serverId) }
        updateConnectionState(serverId, RemoteConnectionState.DISCONNECTED)
    }

    fun disconnectAll() = configCache.map { it.id }.forEach { disconnect(it) }

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

    fun getWebDavClient(serverId: String): okhttp3.OkHttpClient? = webDavTransport.clientFor(serverId)

    fun getSmbContext(serverId: String): jcifs.CIFSContext? = smbTransport.contextFor(serverId)

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
            val result = transportFor(cfg).listFolder(cfg, remotePath)
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
        val cfg = getServer(serverId) ?: return ByteArray(0)
        return try {
            val bytes = transportFor(cfg).readFileBytes(cfg, remotePath, offset, length)
            updateConnectionState(serverId, RemoteConnectionState.CONNECTED)
            bytes
        } catch (e: Exception) {
            markServerFailure(serverId, cfg, e)
            throw e
        }
    }

    fun openFileStream(serverId: String, remotePath: String): InputStream {
        val cfg = getServer(serverId) ?: throw IllegalStateException("Server not found")
        return try {
            val stream = transportFor(cfg).openFileStream(cfg, remotePath)
            updateConnectionState(serverId, RemoteConnectionState.CONNECTED)
            stream
        } catch (e: Exception) {
            markServerFailure(serverId, cfg, e)
            throw e
        }
    }

    fun getFileSize(serverId: String, remotePath: String): Long {
        val cfg = getServer(serverId) ?: return 0L
        return runCatching {
            val size = transportFor(cfg).getFileSize(cfg, remotePath)
            updateConnectionState(serverId, RemoteConnectionState.CONNECTED)
            size
        }.getOrElse {
            markServerFailure(serverId, cfg, it)
            0L
        }
    }

    // --- Error normalization ---

    private fun connectionErrorMessage(cfg: ServerConfig, e: Throwable): String {
        val msg = (e.message ?: e.javaClass.simpleName).ifBlank { e.javaClass.simpleName }
        if (cfg.type == ServerType.SMB && e is SmbException) {
            return when (e.ntStatus) {
                SmbException.NT_STATUS_LOGON_FAILURE -> "认证失败：用户名或密码错误"
                SmbException.NT_STATUS_ACCESS_DENIED, SmbException.NT_STATUS_NETWORK_ACCESS_DENIED ->
                    "访问被拒绝：该账号无权访问服务器的共享列表"
                SmbException.NT_STATUS_BAD_NETWORK_NAME -> "找不到共享或路径不存在"
                SmbException.NT_STATUS_CONNECTION_REFUSED -> "连接被拒绝：请检查端口（默认 445）与服务器在线状态"
                else -> "SMB 连接失败：$msg"
            }
        }
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

    private fun updateConnectionState(serverId: String, state: RemoteConnectionState) {
        _connectionStates.update { states -> states + (serverId to state) }
    }

    private fun syncConnectionStates() {
        val activeIds = configCache.map { it.id }.toSet()
        // 清掉已删除服务器在各传输层残留的连接（disconnect 幂等，两个传输都调）
        (_connectionStates.value.keys - activeIds).forEach { staleId ->
            transports.forEach { it.disconnect(staleId) }
        }
        serverErrors.keys.removeIf { it !in activeIds }
        _connectionStates.update { old ->
            activeIds.associateWith { id -> old[id] ?: RemoteConnectionState.UNKNOWN }
        }
    }

    private fun markServerFailure(serverId: String, cfg: ServerConfig, e: Throwable) {
        val message = connectionErrorMessage(cfg, e)
        lastParseError = message
        serverErrors[serverId] = message
        transports.forEach { it.disconnect(serverId) }
        updateConnectionState(serverId, RemoteConnectionState.DISCONNECTED)
    }
}
