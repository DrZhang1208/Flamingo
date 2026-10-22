package yos.music.player.data.remote

import java.io.InputStream

/**
 * 远程服务器的协议传输层。实现负责各自的连接/会话缓存，错误一律以异常上抛，
 * 由 RemoteServerManager 统一归一化为连接状态与用户可见的错误文案。
 */
internal interface RemoteTransport {

    /** 建立并缓存连接（含一次连通性验证），失败抛异常 */
    fun connect(config: ServerConfig, password: String?)

    /** 丢弃缓存的连接（尽力关闭，不抛异常） */
    fun disconnect(serverId: String)

    /** 是否已持有该服务器的活跃客户端 */
    fun hasClient(serverId: String): Boolean

    /** 无副作用的连接测试，返回给 UI 直显的结果文案 */
    fun testConnection(config: ServerConfig, password: String?): String

    fun listFolder(config: ServerConfig, remotePath: String): List<RemoteFile>

    fun readFileBytes(config: ServerConfig, remotePath: String, offset: Long, length: Int): ByteArray

    fun openFileStream(config: ServerConfig, remotePath: String): InputStream

    fun getFileSize(config: ServerConfig, remotePath: String): Long
}
