package yos.music.player.ui.widgets.basic

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary.toMediaItem
import yos.music.player.data.libraries.PlayListLibrary
import coil.compose.AsyncImage
import coil.request.ImageRequest
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle

@Composable
fun SongMenuIcon(music: YosMediaItem) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var btnPos by remember { mutableStateOf(Offset.Zero) }

    val context = LocalContext.current

    Icon(
        painterResource(R.drawable.ic_nowplaying_more), "菜单",
        Modifier.size(20.dp).alpha(0.3f)
            .onGloballyPositioned { btnPos = it.localToRoot(Offset.Zero) }
            .clickable(remember { MutableInteractionSource() }, null) { showMenu = true }
    )

    PopupMenu(
        items = listOf(
            PopupMenuItem("添加到歌单", Icons.AutoMirrored.Filled.PlaylistPlay) { showPlaylistPicker = true; showMenu = false },
            PopupMenuItem("下一首播放", Icons.Filled.Add) {
                val currentMusic = MediaController.musicPlaying.value ?: return@PopupMenuItem
                val list = MediaController.playingMusicList.value?.toMutableList() ?: return@PopupMenuItem
                val currentIdx = list.indexOfFirst { it.uri == currentMusic.uri }
                if (currentIdx < 0) return@PopupMenuItem

                // playingMusicList 始终匹配 ExoPlayer 的实际播放顺序，直接插入即可
                list.add(currentIdx + 1, music)
                MediaController.playingMusicList.value = list
                MediaController.mediaControl?.addMediaItem(currentIdx + 1, music.toMediaItem())

                Toast.makeText(context, "已添加到下一首播放", Toast.LENGTH_SHORT).show()
                showMenu = false
            },
            PopupMenuItem("详细信息", Icons.Outlined.Info) { showDetail = true; showMenu = false }
        ),
        buttonPosition = btnPos,
        expanded = showMenu,
        onDismiss = { showMenu = false }
    )

    if (showPlaylistPicker) {
        PlaylistPickerDialog(music, onDismiss = { showPlaylistPicker = false })
    }

    if (showDetail) {
        SongDetailDialog(music, onDismiss = { showDetail = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerDialog(music: YosMediaItem, onDismiss: () -> Unit) {
    val playlists = PlayListLibrary.playList
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { sheetState.expand() }

    val animatedDismiss: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    OptionDialog(
        icon = { Spacer(Modifier.size(0.dp)) },
        title = "添加到歌单",
        bottomSheetState = sheetState,
        content = if (playlists.isEmpty()) {
            { Text("暂无歌单", fontSize = 14.sp, modifier = Modifier.alpha(0.5f).padding(16.dp)) }
        } else {
            {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    playlists.forEach { pl ->
                        val coverUri = remember(pl.songDataList) {
                            val allSongs = yos.music.player.data.libraries.MusicLibrary.songs
                            pl.songDataList.firstOrNull()?.let { uri -> allSongs.find { it.uri == uri }?.thumb }
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                PlayListLibrary.run { pl.addMusic(music) }
                                Toast.makeText(context, "已添加到${pl.name}", Toast.LENGTH_SHORT).show()
                                animatedDismiss()
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (coverUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(coverUri).size(64).build(),
                                    contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.placeholder_playlist_default),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(pl.name, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        positiveContent = "关闭",
        onPositive = { animatedDismiss() },
        onDismissRequest = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongDetailDialog(music: YosMediaItem, onDismiss: () -> Unit) {
    val filePath = music.uri?.path
    val info = remember(filePath) {
        if (filePath != null) yos.music.player.code.AudioMetadataUtils.getAudioFileInfo(filePath) else null
    }
    val yearFromFile = remember(filePath) {
        if (filePath != null) yos.music.player.code.AudioMetadataUtils.getYear(filePath) else null
    }
    val playCount = remember(music.uri) {
        yos.music.player.data.libraries.MusicLibrary.getPlayCount(music.uri)
    }

    OptionDialog(
        icon = { Spacer(Modifier.size(0.dp)) },
        title = "歌曲信息",
        content = {
            Column(Modifier.padding(horizontal = 16.dp)) {
                DetailRow("标题", music.title ?: defaultTitle)
                DetailRow("艺术家", music.artistsName ?: "未知艺术家")
                music.album?.let { DetailRow("专辑", it) }
                DetailRow("时长", formatDuration(music.duration))
                DetailRow("播放次数", "${playCount}")
                if (yearFromFile != null) DetailRow("年份", "$yearFromFile")
                if (info != null) {
                    if (info.bitrate != null && info.bitrate > 0) DetailRow("比特率", "${info.bitrate} kbps")
                    if (info.sampleRate != null && info.sampleRate > 0) DetailRow("采样率", "${"%.1f".format(info.sampleRate / 1000.0)} kHz")
                    if (info.bitsPerSample != null && info.bitsPerSample > 0) DetailRow("位深", "${info.bitsPerSample} bit")
                    if (info.channels != null && info.channels > 0) DetailRow("声道", "${info.channels}")
                    DetailRow("文件大小", formatFileSize(info.fileSize))
                    DetailRow("文件格式", info.format)
                    DetailRow("文件来源", info.source)
                }
            }
        },
        positiveContent = "关闭",
        onPositive = { onDismiss() },
        onDismissRequest = onDismiss
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, modifier = Modifier.alpha(0.5f).width(60.dp))
        Text(value, fontSize = 13.sp)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.songMenuTrigger(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = this.combinedClickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = ripple(),
    onClick = onClick,
    onLongClick = onLongClick
)
