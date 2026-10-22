package yos.music.player.data.remote

/**
 * 远程曲库 URI 约定收口：`webdav://<serverId>/<path>`、`smb://<serverId>/<path>`。
 * serverId 是 ServerConfig.id（UUID），host 部分不参与寻址，由 DataSource 再查表取真实地址。
 */
object RemoteUri {

    const val WEBDAV_SCHEME = "webdav"
    const val SMB_SCHEME = "smb"

    val SCHEMES = setOf(WEBDAV_SCHEME, SMB_SCHEME)

    fun schemeFor(type: ServerType): String = when (type) {
        ServerType.WEBDAV -> WEBDAV_SCHEME
        ServerType.SMB -> SMB_SCHEME
    }

    fun buildUri(serverId: String, path: String, type: ServerType): String {
        val clean = path.trimStart('/')
        val prefix = "${schemeFor(type)}://$serverId"
        return if (clean.isEmpty()) prefix else "$prefix/$clean"
    }

    /** 从 webdav://、smb:// 形式的 URI 提取 serverId，非远程 URI 返回 null */
    fun parseServerId(rawUri: String): String? = when {
        rawUri.startsWith("$WEBDAV_SCHEME://") -> rawUri.substringAfter("$WEBDAV_SCHEME://").substringBefore("/").ifEmpty { null }
        rawUri.startsWith("$SMB_SCHEME://") -> rawUri.substringAfter("$SMB_SCHEME://").substringBefore("/").ifEmpty { null }
        else -> null
    }

    fun isRemoteScheme(scheme: String?): Boolean = scheme in SCHEMES
}
