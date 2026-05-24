package yos.music.player.ui.pages.settings.remote

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.remote.RemoteServerManager
import yos.music.player.data.remote.ServerConfig
import yos.music.player.data.remote.ServerType
import yos.music.player.ui.pages.settings.GroupSpacer
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.widgets.basic.OptionDialog
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteServerManagement(navController: NavController) {
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableStateOf(0) }
    var showWebDAVDialog by remember { mutableStateOf(false) }
    var showSMBDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ServerConfig?>(null) }
    var showEditDialog by remember { mutableStateOf<ServerConfig?>(null) }
    var openMenuId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val saved = MusicLibrary.loadRemoteServers()
            if (!saved.isNullOrBlank()) {
                RemoteServerManager.loadConfigs(saved)
            }
        }
    }

    val servers = remember(refreshKey) { RemoteServerManager.getAllServers() }

    SettingBackground {
        Title(title = "远程服务器", onBack = { navController.popBackStack() }, content = {
            item("space_1") { GroupSpacer() }

            if (servers.isEmpty()) {
                item("empty") {
                    Text("暂无远程服务器", modifier = Modifier.fillMaxWidth().padding(32.dp).alpha(0.5f), fontSize = 15.sp)
                }
            }

            for (server in servers) {
                val sid = server.id
                val connected = RemoteServerManager.isConnected(sid)
                val menuOpen = openMenuId == sid
                item("server_$sid") {
                    val shape = RoundedCornerShape(12.dp)
                    RoundColumn {
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(shape)
                                .clickable {
                                    if (connected) {
                                        navController.navigate("RemoteFolderPicker/$sid")
                                    } else {
                                        scope.launch(Dispatchers.IO) {
                                            RemoteServerManager.connect(sid)
                                            withContext(Dispatchers.Main) { refreshKey++ }
                                        }
                                    }
                                }
                                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val typeName = if (server.type == ServerType.SMB) "SMB" else "WebDAV"
                            val icon = if (server.type == ServerType.SMB) Icons.Filled.Computer else Icons.Filled.Cloud
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, Modifier.size(24.dp), tint = if (connected) Color(0xFF34C759) else Color(0xFF8E8E93))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(server.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.width(6.dp))
                                        Text(typeName, fontSize = 11.sp, color = Color(0xFF007AFF), modifier = Modifier.alpha(0.7f))
                                    }
                                    Text(buildSubTitle(server), fontSize = 13.sp, modifier = Modifier.alpha(0.5f))
                                }
                            }
                            Box(
                                Modifier.size(40.dp).clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { openMenuId = if (menuOpen) null else sid },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.MoreVert, "菜单", Modifier.size(18.dp).alpha(0.4f))
                            }
                        }
                        // 内联展开菜单（仿主题选择器）
                        AnimatedVisibility(
                            visible = menuOpen,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        openMenuId = null; showEditDialog = server
                                    }.padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Edit, null, Modifier.size(20.dp), tint = Color(0xFF007AFF))
                                    Spacer(Modifier.width(14.dp))
                                    Text("修改", fontSize = 15.sp, color = Color(0xFF007AFF))
                                }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        openMenuId = null; deleteTarget = server
                                    }.padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Delete, null, Modifier.size(20.dp), tint = Color.Red.copy(alpha = 0.7f))
                                    Spacer(Modifier.width(14.dp))
                                    Text("删除", fontSize = 15.sp, color = Color.Red.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
                item("space_$sid") { GroupSpacer() }
            }

            item("add_webdav") {
                RoundColumn {
                    Row(
                        Modifier.fillMaxWidth().clickable { showWebDAVDialog = true }.padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Cloud, null, Modifier.size(20.dp).alpha(0.5f))
                        Spacer(Modifier.width(8.dp))
                        Text("添加 WebDAV 服务器", fontSize = 15.sp)
                    }
                }
            }
            item("space_add1") { GroupSpacer() }
            item("add_smb") {
                RoundColumn {
                    Row(
                        Modifier.fillMaxWidth().clickable { showSMBDialog = true }.padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Computer, null, Modifier.size(20.dp).alpha(0.5f))
                        Spacer(Modifier.width(8.dp))
                        Text("添加 SMB 服务器", fontSize = 15.sp)
                    }
                }
            }
            // 缓存大小设置
            item("cache_title") {
                GroupSpacer()
                Text("缓存设置", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).alpha(0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            item("cache_settings") {
                val ctx = LocalContext.current
                val cacheDir = java.io.File(ctx.cacheDir, "audio_cache")
                val cacheSize = remember { mutableStateOf(cacheDir.walkTopDown().sumOf { it.length() }) }
                val formattedSize = when {
                    cacheSize.value >= 1024L * 1024 * 1024 -> "%.1f GB".format(cacheSize.value / (1024.0 * 1024 * 1024))
                    cacheSize.value >= 1024 * 1024 -> "%.1f MB".format(cacheSize.value / (1024.0 * 1024.0))
                    cacheSize.value >= 1024 -> "%.1f KB".format(cacheSize.value / 1024.0)
                    else -> "${cacheSize.value} B"
                }
                var showClear by remember { mutableStateOf(false) }
                var showCacheOptions by remember { mutableStateOf(false) }
                val cacheOptions = listOf(500 to "500 MB", 1024 to "1 GB", 5120 to "5 GB", 10240 to "10 GB", 0 to "无限制")
                val currentLabel = cacheOptions.firstOrNull { it.first == SettingsLibrary.RemoteCacheSizeMB }?.second ?: "${SettingsLibrary.RemoteCacheSizeMB} MB"
                RoundColumn {
                    // 已用缓存
                    Row(Modifier.fillMaxWidth().clickable { showClear = !showClear }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("已用缓存", Modifier.weight(1f), fontSize = 16.sp)
                        Text(formattedSize, fontSize = 16.sp, modifier = Modifier.alpha(0.5f))
                    }
                    AnimatedVisibility(showClear, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Row(Modifier.fillMaxWidth().clickable {
                            cacheDir.deleteRecursively(); cacheDir.mkdirs()
                            cacheSize.value = 0; showClear = false
                        }.padding(horizontal = 28.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("清除缓存", fontSize = 15.sp, color = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                    // 分隔线
                    Box(Modifier.fillMaxWidth().padding(start = 12.dp).height(0.5.dp).background(Color.Black.copy(alpha = 0.1f)))
                    // 缓存上限
                    Row(Modifier.fillMaxWidth().clickable { showCacheOptions = !showCacheOptions }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("缓存上限", Modifier.weight(1f), fontSize = 16.sp)
                        Text(currentLabel, fontSize = 16.sp, modifier = Modifier.alpha(0.5f))
                    }
                    AnimatedVisibility(showCacheOptions, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            cacheOptions.forEach { (size, label) ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    SettingsLibrary.RemoteCacheSizeMB = size
                                    showCacheOptions = false
                                }.padding(horizontal = 28.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
            item("space_end") { GroupSpacer() }
        })
    }

    // WebDAV 对话框
    if (showWebDAVDialog) {
        ServerDialog(
            type = ServerType.WEBDAV,
            title = "添加 WebDAV 服务器",
            onDismiss = { showWebDAVDialog = false },
            onSave = { config, password ->
                RemoteServerManager.addServer(config, password)
                MusicLibrary.saveRemoteServers(RemoteServerManager.saveConfigs())
                scope.launch(Dispatchers.IO) { RemoteServerManager.connect(config.id, password?.takeIf { it.isNotBlank() }) }
                refreshKey++
                showWebDAVDialog = false
            }
        )
    }

    // SMB 对话框
    if (showSMBDialog) {
        ServerDialog(
            type = ServerType.SMB,
            title = "添加 SMB 服务器",
            onDismiss = { showSMBDialog = false },
            onSave = { config, password ->
                RemoteServerManager.addServer(config, password)
                MusicLibrary.saveRemoteServers(RemoteServerManager.saveConfigs())
                scope.launch(Dispatchers.IO) { RemoteServerManager.connect(config.id, password?.takeIf { it.isNotBlank() }) }
                refreshKey++
                showSMBDialog = false
            }
        )
    }

    // 编辑对话框
    if (showEditDialog != null) {
        val s = showEditDialog!!
        ServerDialog(
            type = s.type,
            title = "编辑服务器",
            initial = s,
            onDismiss = { showEditDialog = null },
            onSave = { config, password ->
                RemoteServerManager.updateServer(config, password)
                MusicLibrary.saveRemoteServers(RemoteServerManager.saveConfigs())
                scope.launch(Dispatchers.IO) { RemoteServerManager.connect(config.id, password?.takeIf { it.isNotBlank() }) }
                refreshKey++
                showEditDialog = null
            }
        )
    }

    // 删除确认
    if (deleteTarget != null) {
        OptionDialog(
            icon = { Spacer(Modifier.size(0.dp)) },
            title = "删除服务器",
            subTitle = "确定要删除「${deleteTarget!!.label}」吗？此操作不可恢复。",
            content = null,
            positiveContent = "删除",
            onPositive = {
                deleteTarget?.let { s ->
                    RemoteServerManager.disconnect(s.id)
                    RemoteServerManager.removeServer(s.id)
                    MusicLibrary.saveRemoteServers(RemoteServerManager.saveConfigs())
                    refreshKey++
                }
                deleteTarget = null
            },
            negativeContent = "取消",
            onNegative = { deleteTarget = null },
            onDismissRequest = { deleteTarget = null }
        )
    }
}

private fun buildSubTitle(config: ServerConfig): String {
    val status = if (RemoteServerManager.isConnected(config.id)) "已连接" else "未连接"
    val addr = if (config.type == ServerType.WEBDAV) config.host else "${config.host}:${config.port}"
    return "$addr · $status"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerDialog(
    type: ServerType,
    title: String,
    initial: ServerConfig? = null,
    onDismiss: () -> Unit,
    onSave: (ServerConfig, String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isEdit = initial != null
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var webdavUrl by remember { mutableStateOf(if (type == ServerType.WEBDAV) initial?.host ?: "" else "") }
    var smbHost by remember { mutableStateOf(if (type == ServerType.SMB) initial?.host ?: "" else "") }
    var smbPort by remember { mutableStateOf(initial?.port?.toString() ?: "445") }
    var shareName by remember { mutableStateOf(initial?.shareName ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(RemoteServerManager.getPassword(initial?.id ?: "") ?: "") }
    var skipSsl by remember { mutableStateOf(initial?.skipSslVerify ?: false) }
    var connecting by remember { mutableStateOf(false) }

    OptionDialog(
        icon = { Spacer(Modifier.size(0.dp)) },
        title = title,
        subTitle = if (type == ServerType.WEBDAV) "输入完整的 WebDAV 地址" else "输入 SMB 服务器信息",
        content = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true)
                if (type == ServerType.WEBDAV) {
                    OutlinedTextField(webdavUrl, { webdavUrl = it }, Modifier.fillMaxWidth(), label = { Text("地址") }, singleLine = true,
                        placeholder = { Text("http://192.168.1.1:8080/dav") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                } else {
                    OutlinedTextField(smbHost, { smbHost = it }, Modifier.fillMaxWidth(), label = { Text("主机地址") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(smbPort, { smbPort = it }, Modifier.fillMaxWidth(), label = { Text("端口") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(shareName, { shareName = it }, Modifier.fillMaxWidth(), label = { Text("共享名") }, singleLine = true)
                }
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("用户名 (可选)") }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码 (可选)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (type == ServerType.WEBDAV) {
                    Row(Modifier.fillMaxWidth().clickable { skipSsl = !skipSsl }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(skipSsl, { skipSsl = !skipSsl })
                        Text("跳过 SSL 证书验证（自签名证书）", fontSize = 13.sp, modifier = Modifier.alpha(0.7f))
                    }
                }
            }
        },
        positiveContent = if (connecting) "连接中..." else "连接",
        dismissOnPositive = false,
        onPositive = {
            if (connecting) return@OptionDialog
            if (type == ServerType.WEBDAV && webdavUrl.isBlank()) {
                Toast.makeText(context, "请输入 WebDAV 地址", Toast.LENGTH_SHORT).show()
                return@OptionDialog
            }
            val cfg = if (type == ServerType.WEBDAV) {
                ServerConfig(id = initial?.id ?: "", type = ServerType.WEBDAV, label = label.ifBlank { webdavUrl }, host = webdavUrl,
                    username = username.ifBlank { null }, authRequired = username.isNotBlank(), skipSslVerify = skipSsl)
            } else {
                ServerConfig(id = initial?.id ?: "", type = ServerType.SMB, label = label.ifBlank { smbHost }, host = smbHost,
                    port = smbPort.toIntOrNull() ?: 445, shareName = shareName.ifBlank { null },
                    username = username.ifBlank { null }, authRequired = username.isNotBlank())
            }
            val pwd = password.ifBlank { null }
            connecting = true
            scope.launch(Dispatchers.IO) {
                val result = RemoteServerManager.testConnection(cfg, pwd)
                withContext(Dispatchers.Main) {
                    connecting = false
                    if (result.startsWith("连接成功")) {
                        onSave(cfg, pwd)
                    } else {
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    }
                }
            }
        },
        negativeContent = "取消",
        onNegative = { onDismiss() },
        onDismissRequest = onDismiss
    )
}
