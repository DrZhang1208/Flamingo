package yos.music.player.data.remote

import jcifs.CIFSContext
import jcifs.DialectVersion
import jcifs.ResolverType
import jcifs.config.BaseConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * SMB 传输实现（jcifs-ng，SMB 2.0.2 ~ 3.1.1）。
 * 路径约定与 WebDAV 一致：remotePath 是相对服务器根（共享）的无前导斜杠路径；
 * remotePath 为空表示服务器根，listFolder 会枚举共享列表。
 */
internal class SmbTransport : RemoteTransport {

    private val contexts = ConcurrentHashMap<String, CIFSContext>()

    /** 供 SmbDataSource 复用已建立的会话与连接池 */
    fun contextFor(serverId: String): CIFSContext? = contexts[serverId]

    /**
     * 只用 DNS 解析（NetBIOS 广播解析在无 WINS 的家庭网络会阻塞数十秒）；
     * 下限 SMB202：现代 NAS 默认禁用 SMB1，主动协商 SMB1 反而容易被直接拒绝。
     * 必须走 BaseConfiguration(true)：无参构造不会 initDefaults，machineId 等
     * 字段留 null，SMB2 协商时 NPE。
     */
    private class FlamingoConfiguration : BaseConfiguration(true) {
        init {
            initProtocolVersions(DialectVersion.SMB202, DialectVersion.SMB311)
        }

        override fun getResolveOrder(): List<ResolverType> = listOf(ResolverType.RESOLVER_DNS)
        override fun getConnTimeout(): Int = CONN_TIMEOUT_MS
        override fun getSoTimeout(): Int = IO_TIMEOUT_MS
        override fun getResponseTimeout(): Int = IO_TIMEOUT_MS
    }

    private fun buildContext(config: ServerConfig, password: String?): CIFSContext {
        val base = BaseContext(FlamingoConfiguration())
        val pwd = password ?: RemoteServerManager.getPassword(config.id)
        val authenticator = if ((config.username.isNullOrBlank()) && pwd.isNullOrBlank()) {
            NtlmPasswordAuthenticator()
        } else {
            NtlmPasswordAuthenticator(config.domain ?: "", config.username ?: "", pwd ?: "")
        }
        return base.withCredentials(authenticator)
    }

    /** 真实 jcifs URL：smb://host[:port]/path；445 端口省略 */
    fun buildUrl(config: ServerConfig, path: String): String = buildUrlFor(config, path)

    override fun connect(config: ServerConfig, password: String?) {
        val ctx = buildContext(config, password)
        // 验证方式：枚举服务器根的共享列表（后续浏览也依赖这一步）
        SmbFile(buildUrl(config, ""), ctx).use { root ->
            root.listFiles()
        }
        contexts[config.id] = ctx
    }

    override fun disconnect(serverId: String) {
        contexts.remove(serverId)?.let { runCatching { it.close() } }
    }

    override fun hasClient(serverId: String): Boolean = contexts.containsKey(serverId)

    override fun testConnection(config: ServerConfig, password: String?): String {
        val ctx = buildContext(config, password)
        SmbFile(buildUrl(config, ""), ctx).use { root ->
            root.listFiles()
        }
        return "连接成功，可访问共享列表"
    }

    override fun listFolder(config: ServerConfig, remotePath: String): List<RemoteFile> {
        val ctx = contexts[config.id] ?: buildContext(config, null)
        // jcifs 目录 URL 必须带尾部斜杠，否则子项 URL 会与父路径直接拼接（music + a.wav = musica.wav）
        val rawUrl = buildUrl(config, remotePath.trimEnd('/'))
        val url = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
        SmbFile(url, ctx).use { dir ->
            if (!dir.isDirectory()) {
                throw java.io.IOException("SMB 路径不是目录: $remotePath")
            }
            val parent = remotePath.trim('/').trimEnd('/')
            return dir.listFiles().mapNotNull { entry ->
                val isDir = entry.isDirectory()
                val name = entry.name.trimEnd('/')
                // 过滤 IPC$ 等管理共享
                if (name.isEmpty() || name == "." || name.endsWith("$")) return@mapNotNull null
                val entryPath = if (parent.isEmpty()) name else "$parent/$name"
                RemoteFile(
                    name = name,
                    path = entryPath,
                    isDirectory = isDir,
                    size = if (isDir) 0L else entry.length(),
                    modifiedDate = entry.lastModified()
                )
            }
        }
    }

    override fun readFileBytes(config: ServerConfig, remotePath: String, offset: Long, length: Int): ByteArray {
        val ctx = contexts[config.id] ?: buildContext(config, null)
        SmbFile(buildUrl(config, remotePath), ctx).use { file ->
            SmbRandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                val buf = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = raf.read(buf, read, length - read)
                    if (n <= 0) break
                    read += n
                }
                return if (read == length) buf else buf.copyOf(read)
            }
        }
    }

    override fun openFileStream(config: ServerConfig, remotePath: String): InputStream {
        val ctx = contexts[config.id] ?: buildContext(config, null)
        val file = SmbFile(buildUrl(config, remotePath), ctx)
        return file.openInputStream()
    }

    override fun getFileSize(config: ServerConfig, remotePath: String): Long {
        val ctx = contexts[config.id] ?: buildContext(config, null)
        return runCatching {
            SmbFile(buildUrl(config, remotePath), ctx).use { it.length() }
        }.getOrDefault(0L)
    }

    companion object {
        const val DEFAULT_PORT = 445
        private const val CONN_TIMEOUT_MS = 8_000
        private const val IO_TIMEOUT_MS = 30_000

        /** 供 SmbDataSource 构建真实 jcifs URL：smb://host[:port]/path；445 端口省略。
         *  路径必须保持原始字符：jcifs 不做百分号解码，中文/空格按原样走 SMB 的 UTF-16 编码。 */
        fun buildUrlFor(config: ServerConfig, path: String): String {
            // 兼容用户把 smb:// 前缀或 host:port 一起填进主机字段的情况
            val raw = config.host.trim()
                .removePrefix("smb://").removePrefix("SMB://").trimEnd('/')
            val host = raw.substringBefore(':')
            val inlinePort = raw.substringAfter(':', "").toIntOrNull()
            val port = inlinePort ?: config.port.takeIf { it in 1..65535 } ?: DEFAULT_PORT
            val clean = path.trimStart('/')
            val base = if (port == DEFAULT_PORT) "smb://$host" else "smb://$host:$port"
            return if (clean.isEmpty()) "$base/" else "$base/$clean"
        }
    }
}
