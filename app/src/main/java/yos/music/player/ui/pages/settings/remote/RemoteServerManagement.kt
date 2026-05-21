package yos.music.player.ui.pages.settings.remote

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.remote.RemoteServerManager
import yos.music.player.data.remote.ServerConfig
import yos.music.player.data.remote.ServerType
import yos.music.player.ui.pages.settings.Divider
import yos.music.player.ui.pages.settings.GroupSpacer
import yos.music.player.ui.pages.settings.LabelItem
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.OptionDialog
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteServerManagement(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

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
                item("server_$sid") {
                    RoundColumn {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (connected) {
                                    navController.navigate("RemoteFolderPicker/$sid")
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        RemoteServerManager.connect(sid)
                                        withContext(Dispatchers.Main) { refreshKey++ }
                                    }
                                }
                            }.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = if (server.type == ServerType.SMB) Icons.Filled.Computer else Icons.Filled.Cloud
                            Icon(icon, null, Modifier.size(24.dp), tint = if (connected) Color(0xFF34C759) else Color(0xFF8E8E93))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(server.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(buildSubTitle(server), fontSize = 13.sp, modifier = Modifier.alpha(0.5f))
                            }
                            if (connected) Text("›", fontSize = 18.sp, modifier = Modifier.alpha(0.3f))
                        }
                        Divider()
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                RemoteServerManager.removeServer(sid)
                                MusicLibrary.saveRemoteServers(RemoteServerManager.saveConfigs())
                                refreshKey++
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Delete, null, Modifier.size(18.dp).alpha(0.5f))
                            Spacer(Modifier.width(8.dp))
                            Text("删除", fontSize = 14.sp, color = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }
                item("space_$sid") { GroupSpacer() }
            }

            item("add_btn") {
                RoundColumn {
                    Row(
                        Modifier.fillMaxWidth().clickable { showAddDialog = true }.padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(20.dp).alpha(0.5f))
                        Spacer(Modifier.width(8.dp))
                        Text("添加服务器", fontSize = 15.sp)
                    }
                }
            }
            item("space_end") { GroupSpacer() }
        })
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onSave = { config, password ->
                RemoteServerManager.addServer(config, password)
                MusicLibrary.saveRemoteServers(RemoteServerManager.saveConfigs())
                scope.launch(Dispatchers.IO) { RemoteServerManager.connect(config.id) }
                refreshKey++
                showAddDialog = false
            }
        )
    }
}

private fun buildSubTitle(config: ServerConfig): String {
    val type = if (config.type == ServerType.SMB) "SMB" else "WebDAV"
    val status = if (RemoteServerManager.isConnected(config.id)) "已连接" else "未连接"
    return "$type · ${config.host}:${config.port} · $status"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddServerDialog(onDismiss: () -> Unit, onSave: (ServerConfig, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var type by remember { mutableStateOf(ServerType.SMB) }
    var label by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(if (type == ServerType.SMB) "445" else "8080") }
    var shareName by remember { mutableStateOf("") }
    var basePath by remember { mutableStateOf("/") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    OptionDialog(
        icon = { Spacer(Modifier.size(0.dp)) },
        title = "添加服务器",
        subTitle = "支持 SMB / WebDAV",
        content = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    listOf(ServerType.SMB to "SMB", ServerType.WEBDAV to "WebDAV").forEach { (t, name) ->
                        Box(
                            Modifier.weight(1f).padding(horizontal = 4.dp).height(36.dp).clickable { type = t },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, fontWeight = if (type == t) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true)
                OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("地址") }, singleLine = true)
                OutlinedTextField(port, { port = it }, Modifier.fillMaxWidth(), label = { Text("端口") }, singleLine = true)
                if (type == ServerType.SMB) {
                    OutlinedTextField(shareName, { shareName = it }, Modifier.fillMaxWidth(), label = { Text("共享名") }, singleLine = true)
                } else {
                    OutlinedTextField(basePath, { basePath = it }, Modifier.fillMaxWidth(), label = { Text("路径") }, singleLine = true)
                }
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("用户名") }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true)
            }
        },
        positiveContent = if (testing) "测试中..." else "保存",
        onPositive = {
            if (testing) return@OptionDialog
            val cfg = ServerConfig(
                type = type, label = label.ifBlank { host }, host = host,
                port = port.toIntOrNull() ?: if (type == ServerType.SMB) 445 else 8080,
                shareName = shareName.ifBlank { null }, basePath = basePath.ifBlank { "/" },
                username = username.ifBlank { null }, authRequired = username.isNotBlank()
            )
            onSave(cfg, password.ifBlank { null })
        },
        negativeContent = if (testing) "测试中..." else "测试连接",
        onNegative = {
            testing = true
            val cfg = ServerConfig(
                type = type, label = label.ifBlank { host }, host = host,
                port = port.toIntOrNull() ?: if (type == ServerType.SMB) 445 else 8080,
                shareName = shareName.ifBlank { null }, basePath = basePath.ifBlank { "/" },
                username = username.ifBlank { null }, authRequired = username.isNotBlank()
            )
            scope.launch(Dispatchers.IO) {
                val result = RemoteServerManager.testConnection(cfg, password.ifBlank { null })
                withContext(Dispatchers.Main) { testing = false; Toast.makeText(context, result, Toast.LENGTH_LONG).show() }
            }
        },
        onDismissRequest = onDismiss
    )
}
