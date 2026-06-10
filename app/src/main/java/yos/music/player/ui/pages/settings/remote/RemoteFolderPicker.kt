package yos.music.player.ui.pages.settings.remote

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.Folder
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.objects.LibraryObject
import yos.music.player.data.objects.MediaViewModelObject
import yos.music.player.data.remote.RemoteFile
import yos.music.player.data.remote.RemoteMetadataScanner
import yos.music.player.data.remote.RemoteServerManager
import yos.music.player.data.remote.ServerConfig
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
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var mounting by remember { mutableStateOf(false) }

    fun loadFolder(path: String) {
        loadJob?.cancel()
        loading = true
        loadJob = scope.launch(Dispatchers.IO) {
            val files = runCatching {
                val connected = RemoteServerManager.isConnected(serverId)
                if (!connected) { RemoteServerManager.connect(serverId) }
                RemoteServerManager.listFolder(serverId, path)
            }.getOrElse {
                emptyList()
            }

            val error = RemoteServerManager.lastParseError
            withContext(Dispatchers.Main) {
                entries = files; currentPath = path; loading = false
                if (files.isEmpty() && error.isNotBlank()) {
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) { loadFolder("") }

    fun goToParent() {
        if (currentPath.isEmpty()) {
            navController.popBackStack()
        } else if (!currentPath.contains('/')) {
            // 单层目录 → 返回根
            pathStack = listOf("")
            loadFolder("")
        } else {
            val parent = currentPath.substringBeforeLast('/')
            pathStack = pathStack.dropLastWhile { it != parent } + parent
            loadFolder(parent)
        }
    }

    // 系统返回键与左上角返回按钮行为一致
    BackHandler(enabled = true) { goToParent() }

    val folderName = currentPath.substringAfterLast('/').ifEmpty { config.label }
    val sourceLabel = "WebDAV"

    SettingBackground {
        Title(title = folderName.ifEmpty { config.label }, onBack = { goToParent() }) {
            item("mount_btn") {
                RoundColumn {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (mounting) return@clickable
                            mounting = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                // 防重复：用 allFolders（包含 serverId）确保检测到已挂载的远程文件夹
                                val already = MusicLibrary.allFolders.any {
                                    it.serverId == serverId && (it.path == currentPath || it.path == currentPath.trimStart('/'))
                                }
                                if (already) {
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "已挂载", Toast.LENGTH_SHORT).show() }
                                    return@launch
                                }
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
                                // 播放中的歌曲优先扫描
                                val playingUri = MediaController.musicPlaying.value?.uri
                                val scanQueue = if (playingUri != null) {
                                    val p = songs.indexOfFirst { it.uri == playingUri }
                                    if (p > 0) listOf(songs[p]) + songs.filterIndexed { i, _ -> i != p } else songs
                                } else songs
                                RemoteMetadataScanner.startBackgroundScan(scanQueue, serverId) { updated ->
                                    MusicLibrary.updateSongInFullList(updated)
                                    MusicLibrary.updateFolderSongs(serverId, currentPath, updated)
                                    val latest = MusicLibrary.allFolders.find { it.serverId == serverId && it.path.trim('/') == currentPath.trim('/') }
                                    val isPlaying = MediaController.musicPlaying.value?.uri == updated.uri
                                    // 所有 UI 更新必须在主线程
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        if (latest != null) LibraryObject.setTargetListWithTitle(folderName, latest.songs)
                                        if (isPlaying) {
                                            MediaController.musicPlaying.value = updated
                                            MediaController.uiRefreshTrigger++
                                            // 更新歌词
                                            val cached = yos.music.player.data.remote.RemoteTagDatabase.get(updated.uri?.toString() ?: "")
                                            if (!cached?.lyrics.isNullOrBlank()) {
                                                val lrcF = yos.music.player.code.utils.lrc.YosLrcFactory()
                                                val lrc = lrcF.formatLrcEntries(cached!!.lyrics)
                                                if (lrc.isNotEmpty()) {
                                                    MediaViewModelObject.lrcEntries.value = lrc
                                                } else {
                                                    val plain = lrcF.formatPlainLyricEntries(cached.lyrics)
                                                    if (plain.isNotEmpty()) MediaViewModelObject.lrcEntries.value = plain
                                                }
                                            }
                                        }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "已挂载「${folderName}」到资料库 (${songs.size} 首)", Toast.LENGTH_SHORT).show()
                                }
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        mounting = false
                                    }
                                }
                            }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlaylistAdd, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("挂载此文件夹到资料库", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("$sourceLabel · ${audioCount(entries)} 首音频", fontSize = 13.sp, modifier = Modifier.alpha(0.5f))
                        }
                    }
                }
                GroupSpacer()
            }

            // 面包屑始终显示
            item("breadcrumb") {
                val parts = currentPath.split('/').filter { it.isNotEmpty() }
                val accent = MaterialTheme.colorScheme.primary
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(config.label, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.clickable {
                        pathStack = listOf(""); loadFolder("")
                    }.alpha(0.6f).padding(end = 4.dp), color = accent)
                    if (currentPath.isEmpty()) {
                        Text(" › ", fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.alpha(0.4f))
                        Text("/", fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.alpha(0.9f), color = accent)
                    } else {
                        for ((i, part) in parts.withIndex()) {
                            Text(" › ", fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.alpha(0.4f))
                            Text(part, fontSize = 13.sp, lineHeight = 20.sp,
                                modifier = if (i < parts.size - 1) Modifier.clickable {
                                    val targetPath = parts.take(i + 1).joinToString("/")
                                    val idx = pathStack.indexOfLast { it == targetPath }
                                    pathStack = if (idx >= 0) pathStack.take(idx + 1) else pathStack + targetPath
                                    loadFolder(targetPath)
                                }.alpha(0.6f).padding(end = 4.dp) else Modifier.alpha(0.9f),
                                color = if (i < parts.size - 1) accent else Color.Unspecified
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
                                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else Color(0xFF888888)
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
