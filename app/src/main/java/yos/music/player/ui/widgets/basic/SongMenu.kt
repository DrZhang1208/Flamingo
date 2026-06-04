package yos.music.player.ui.widgets.basic

import android.net.Uri
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.MusicLibrary.toMediaItem
import yos.music.player.data.libraries.FavPlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary
import coil.compose.AsyncImage
import coil.request.ImageRequest
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.ui.UI
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuIcon(music: YosMediaItem, navController: NavController? = null, showArtistMenuItem: Boolean = true, showAlbumMenuItem: Boolean = true) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }
    val menuOpenCount = remember { mutableIntStateOf(0) }
    val hasMultipleArtists = (music.artistsList?.size ?: 0) > 1

    val context = LocalContext.current

    Icon(
        painterResource(R.drawable.ic_nowplaying_more), "菜单",
        Modifier.size(20.dp).alpha(0.3f)
            .clickable(remember { MutableInteractionSource() }, null) { showMenu = true; menuOpenCount.intValue++ }
    )

    if (showMenu) {
        key(menuOpenCount.intValue) {
        OptionDialog(
            icon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShadowImageWithCache(
                        dataLambda = { music.thumb }, contentDescription = null,
                        modifier = Modifier.size(52.dp), cornerRadius = 6.dp,
                        shadowAlpha = 0f, imageQuality = ImageQuality.LOW
                    )
                }
            },
            title = music.title ?: defaultTitle,
            subTitle = "${music.artistsName ?: defaultArtistsName}${music.album?.let { " · $it" } ?: ""}",
            content = { dismiss ->
                Column {
                    val items = buildList {
                        add(Triple("添加到歌单", Icons.AutoMirrored.Filled.PlaylistPlay, { dismiss(); showPlaylistPicker = true }))
                        add(Triple("下一首播放", Icons.Filled.Add, {
                            val currentMusic = MediaController.musicPlaying.value ?: return@Triple
                            val list = MediaController.playingMusicList.value?.toMutableList() ?: return@Triple
                            val currentIdx = list.indexOfFirst { it.uri == currentMusic.uri }
                            if (currentIdx < 0) return@Triple
                            list.add(currentIdx + 1, music)
                            MediaController.playingMusicList.value = list
                            MediaController.mediaControl?.addMediaItem(currentIdx + 1, music.toMediaItem())
                            Toast.makeText(context, "已添加到下一首播放", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }))
                        if (showArtistMenuItem && hasMultipleArtists) {
                            add(Triple("歌手", Icons.Filled.Person, {
                                dismiss(); showArtistPicker = true
                            }))
                        } else if (showArtistMenuItem) {
                            add(Triple("歌手", Icons.Filled.Person, {
                                music.artistsList?.firstOrNull()?.let { artist -> yos.music.player.data.objects.LibraryObject.setTargetArtistName(artist); navController?.toUI(UI.ArtistInfo) }
                                dismiss()
                            }))
                        }
                        if (showAlbumMenuItem && music.album != null) {
                            add(Triple("专辑", Icons.Filled.Album, {
                                music.album?.let { album -> yos.music.player.data.objects.LibraryObject.setTargetAlbumName(album); navController?.toUI(UI.AlbumInfo) }
                                dismiss()
                            }))
                        }
                        add(Triple(if (FavPlayListLibrary.isFavorite(music)) "从喜爱移除" else "添加到喜爱",
                            if (FavPlayListLibrary.isFavorite(music)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        {
                            if (FavPlayListLibrary.isFavorite(music)) FavPlayListLibrary.removeMusic(music)
                            else FavPlayListLibrary.addMusic(music)
                            dismiss()
                        }))
                        add(Triple("详细信息", Icons.Outlined.Info, { dismiss(); showDetail = true }))
                    }
                    items.forEachIndexed { index, (label, icon, onClick) ->
                        if (index > 0) Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            },
            horizontalTitle = true,
            onDismissRequest = { showMenu = false }
        )
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(music, onDismiss = { showPlaylistPicker = false })
    }

    if (showDetail) {
        SongDetailDialog(music, onDismiss = { showDetail = false })
    }

    if (showArtistPicker && navController != null) {
        val artists = music.artistsList ?: emptyList()
        OptionDialog(
            icon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShadowImageWithCache(
                        dataLambda = { music.thumb }, contentDescription = null,
                        modifier = Modifier.size(52.dp), cornerRadius = 6.dp,
                        shadowAlpha = 0f, imageQuality = ImageQuality.LOW
                    )
                }
            },
            title = music.title ?: defaultTitle,
            subTitle = music.artistsName ?: defaultArtistsName,
            horizontalTitle = true,
            content = { dismiss ->
                Column {
                    artists.forEachIndexed { index, artist ->
                        if (index > 0) Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clickable {
                                yos.music.player.data.objects.LibraryObject.setTargetArtistName(artist)
                                navController.toUI(UI.ArtistInfo)
                                dismiss()
                            }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(artist, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            onDismissRequest = { showArtistPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerDialog(music: YosMediaItem, onDismiss: () -> Unit) {
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
                // 预计算封面 URI，避免在 remember 中访问 MusicLibrary.songs 阻塞 UI
                var coverUris by remember { mutableStateOf(emptyMap<String, Uri?>()) }
                LaunchedEffect(playlists) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val allSongs = yos.music.player.data.libraries.MusicLibrary.songs
                        playlists.associate { pl ->
                            pl.name to pl.songDataList
                                .mapNotNull { uri -> allSongs.find { it.uri == uri } }
                                .maxByOrNull { MusicLibrary.getPlayCount(it.uri) }
                                ?.thumb
                        }
                    }.let { coverUris = it }
                }
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    playlists.forEach { pl ->
                        val coverUri = coverUris[pl.name]
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
fun SongDetailDialog(music: YosMediaItem, onDismiss: () -> Unit) {
    val filePath = music.uri?.path
    var info by remember(filePath) { mutableStateOf<yos.music.player.code.AudioMetadataUtils.AudioFileInfo?>(null) }
    var yearFromFile by remember(filePath) { mutableStateOf<Int?>(null) }
    val playCount = remember(music.uri) {
        yos.music.player.data.libraries.MusicLibrary.getPlayCount(music.uri)
    }

    LaunchedEffect(filePath) {
        if (filePath != null) {
            val (fileInfo, year) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (music.serverId != null) {
                    // 远程歌曲：从 YosMediaItem 字段构建 AudioFileInfo，不读本地文件
                    val format = music.mimeType?.substringAfterLast("/")?.uppercase()
                        ?: music.uri?.path?.substringAfterLast(".")?.uppercase() ?: ""
                    yos.music.player.code.AudioMetadataUtils.AudioFileInfo(
                        bitrate = music.bitrate,
                        sampleRate = music.sampleRate,
                        channels = music.channels,
                        bitsPerSample = null,
                        fileSize = 0L,
                        format = format,
                        source = "WebDAV"
                    ) to (music.releaseYear ?: music.recordingYear)
                } else {
                    yos.music.player.code.AudioMetadataUtils.getAudioFileInfo(filePath) to
                            yos.music.player.code.AudioMetadataUtils.getYear(filePath)
                }
            }
            info = fileInfo
            yearFromFile = year
        }
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
                val itemYear = music.releaseYear ?: music.recordingYear ?: yearFromFile
                if (itemYear != null) DetailRow("年份", "$itemYear")
                music.trackNumber?.let { DetailRow("音轨号", "$it") }
                music.discNumber?.let { DetailRow("碟号", "$it") }
                val fileInfo = info
                if (fileInfo != null) {
                    if (fileInfo.bitrate != null && fileInfo.bitrate > 0) DetailRow("比特率", "${fileInfo.bitrate} kbps")
                    if (fileInfo.sampleRate != null && fileInfo.sampleRate > 0) DetailRow("采样率", "${"%.1f".format(fileInfo.sampleRate / 1000.0)} kHz")
                    if (fileInfo.bitsPerSample != null && fileInfo.bitsPerSample > 0) DetailRow("位深", "${fileInfo.bitsPerSample} bit")
                    if (fileInfo.channels != null && fileInfo.channels > 0) DetailRow("声道", "${fileInfo.channels}")
                    DetailRow("文件格式", fileInfo.format)
                    DetailRow("文件来源", fileInfo.source)
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
