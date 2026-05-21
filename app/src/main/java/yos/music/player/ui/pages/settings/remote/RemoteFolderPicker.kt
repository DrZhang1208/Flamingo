package yos.music.player.ui.pages.settings.remote

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.data.libraries.Folder
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.remote.RemoteFile
import yos.music.player.data.remote.RemoteMetadataScanner
import yos.music.player.data.remote.RemoteServerManager
import yos.music.player.data.remote.ServerConfig
import yos.music.player.data.remote.ServerType
import yos.music.player.ui.pages.settings.GroupSpacer
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title

@Composable
fun RemoteFolderPicker(navController: NavController, serverId: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (serverId == null) { navController.popBackStack(); return }

    val config = RemoteServerManager.getServer(serverId) ?: run { navController.popBackStack(); return }
    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pathStack by remember { mutableStateOf(listOf("")) }

    fun loadFolder(path: String) {
        loading = true
        scope.launch(Dispatchers.IO) {
            val connected = RemoteServerManager.isConnected(serverId)
            if (!connected) { RemoteServerManager.connect(serverId) }
            val files = RemoteServerManager.listFolder(serverId, path)
            withContext(Dispatchers.Main) {
                entries = files; currentPath = path; loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFolder("") }

    val folderName = currentPath.substringAfterLast('/').ifEmpty { config.label }
    val sourceLabel = if (config.type == ServerType.SMB) "SMB" else "WebDAV"

    SettingBackground {
        Title(title = folderName.ifEmpty { config.label }, onBack = {
            if (currentPath.isNotEmpty()) {
                // 返回上级目录
                val parent = currentPath.substringBeforeLast('/')
                val newStack = if (parent.isEmpty()) listOf("") else pathStack.dropLastWhile { it != parent } + parent
                pathStack = newStack
                loadFolder(parent)
            } else {
                navController.popBackStack()
            }
        }) {
            item("mount_btn") {
                RoundColumn {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch(Dispatchers.IO) {
                                val already = MusicLibrary.folders.any { it.serverId == serverId && it.path == currentPath }
                                if (already) { withContext(Dispatchers.Main) { Toast.makeText(context, "已挂载", Toast.LENGTH_SHORT).show() }; return@launch }
                                val audioFiles = RemoteServerManager.listAudioFiles(serverId, currentPath)
                                if (audioFiles.isEmpty()) {
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "该文件夹没有音频文件", Toast.LENGTH_SHORT).show() }
                                    return@launch
                                }
                                val songs = RemoteMetadataScanner.quickListAudioFiles(serverId, currentPath, config)
                                val folder = Folder(
                                    name = folderName, path = currentPath, songs = songs,
                                    source = sourceLabel, serverId = serverId
                                )
                                MusicLibrary.mountRemoteFolder(folder)
                                RemoteMetadataScanner.startBackgroundScan(songs, serverId) { updated ->
                                    // Update song in library when background scan completes
                                    val allSongs = MusicLibrary.songs.toMutableList()
                                    val idx = allSongs.indexOfFirst { it.uri == updated.uri }
                                    if (idx >= 0) allSongs[idx] = updated
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "已挂载「${folderName}」到资料库 (${songs.size} 首)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlaylistAdd, null, Modifier.size(22.dp), tint = Color(0xFF007AFF))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("挂载此文件夹到资料库", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("$sourceLabel · ${audioCount(entries)} 首音频", fontSize = 13.sp, modifier = Modifier.alpha(0.5f))
                        }
                    }
                }
                GroupSpacer()
            }

            // Breadcrumb
            if (currentPath.isNotEmpty()) {
                item("breadcrumb") {
                    val parts = currentPath.split('/').filter { it.isNotEmpty() }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(config.label, fontSize = 13.sp, modifier = Modifier.clickable {
                            pathStack = listOf(""); loadFolder("")
                        }.alpha(0.6f).padding(end = 4.dp), color = Color(0xFF007AFF))
                        for ((i, part) in parts.withIndex()) {
                            Text(" › ", fontSize = 13.sp, modifier = Modifier.alpha(0.4f))
                            Text(part, fontSize = 13.sp,
                                modifier = if (i < parts.size - 1) Modifier.clickable {
                                    val targetPath = parts.take(i + 1).joinToString("/")
                                    pathStack = pathStack + targetPath; loadFolder(targetPath)
                                }.alpha(0.6f).padding(end = 4.dp) else Modifier.alpha(0.9f),
                                color = if (i < parts.size - 1) Color(0xFF007AFF) else Color.Unspecified
                            )
                        }
                    }
                }
            }

            if (loading) {
                item("loading") {
                    Text("加载中...", Modifier.fillMaxWidth().padding(32.dp).alpha(0.5f), fontSize = 15.sp)
                }
            } else if (entries.isEmpty()) {
                item("empty") {
                    Text("空文件夹", Modifier.fillMaxWidth().padding(32.dp).alpha(0.5f), fontSize = 15.sp)
                }
            } else {
                for (entry in entries.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name })) {
                    item("entry_${entry.path}") {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (entry.isDirectory) {
                                    val newPath = if (currentPath.isEmpty()) entry.name else "$currentPath/${entry.name}"
                                    pathStack = pathStack + newPath
                                    loadFolder(newPath)
                                }
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.MusicNote,
                                null, Modifier.size(if (entry.isDirectory) 28.dp else 24.dp),
                                tint = if (entry.isDirectory) Color(0xFF007AFF) else Color(0xFF888888)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!entry.isDirectory) {
                                    Text(formatSize(entry.size), fontSize = 12.sp, modifier = Modifier.alpha(0.5f))
                                }
                            }
                            if (entry.isDirectory) {
                                Text("›", fontSize = 18.sp, modifier = Modifier.alpha(0.3f))
                            }
                        }
                    }
                }
            }
            item("bottom") { Spacer(Modifier.height(32.dp)) }
        }
    }
}

private fun audioCount(entries: List<RemoteFile>): Int = entries.count { !it.isDirectory && isAudioExt(it.name) }

private fun isAudioExt(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus", "ape", "aiff")
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "$bytes B"
}
