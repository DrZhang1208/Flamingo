package yos.music.player.ui.widgets.basic

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI

/** 歌曲菜单的内部页面：切页不收卡片，整页平滑推入/缩放 */
private enum class SongMenuPage { Main, PlaylistPicker, CreatePlaylist, ArtistPicker, Detail }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuIcon(music: YosMediaItem, navController: NavController? = null, showArtistMenuItem: Boolean = true, showAlbumMenuItem: Boolean = true, showRemoveFromPlaylist: Boolean = false) {
    var showMenu by remember { mutableStateOf(false) }
    var menuPage by remember { mutableStateOf(SongMenuPage.Main) }
    var menuOpenCount by remember { mutableIntStateOf(0) }
    // 跨页保留：新建歌单的输入内容在页间切换时不丢（工厂需要 MutableState 引用）
    val newPlaylistName = remember { mutableStateOf("") }
    val hasMultipleArtists = (music.artistsList?.size ?: 0) > 1

    val context = LocalContext.current

    Icon(
        painterResource(R.drawable.ic_nowplaying_more), "菜单",
        Modifier.size(20.dp).alpha(0.3f)
            .clickable(remember { MutableInteractionSource() }, null) {
                showMenu = true; menuOpenCount++
                menuPage = SongMenuPage.Main
            }
    )

    if (showMenu) {
        key(menuOpenCount) {
            val songHeaderIcon: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShadowImageWithCache(
                        dataLambda = { music.thumb }, contentDescription = null,
                        modifier = Modifier.size(52.dp), cornerRadius = 6.dp,
                        shadowAlpha = 0f, imageQuality = ImageQuality.LOW
                    )
                }
            }

            val mainPage = OptionDialogPage(
                key = SongMenuPage.Main,
                title = music.title ?: defaultTitle,
                subTitle = "${music.artistsName ?: defaultArtistsName}${music.album?.let { " · $it" } ?: ""}",
                icon = songHeaderIcon,
                horizontalTitle = true,
                content = { dismiss ->
                    Column(
                        Modifier.clip(YosRoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.onSecondary)
                    ) {
                        val items = buildList {
                            add(Triple("添加到歌单", Icons.Filled.Add, { menuPage = SongMenuPage.PlaylistPicker }))
                            if (showRemoveFromPlaylist) {
                                val (playlistTitle, _) = yos.music.player.data.objects.LibraryObject.getTargetListWithTitle()
                                val targetPlaylist = PlayListLibrary.playList.find { it.name == playlistTitle }
                                if (targetPlaylist != null) {
                                    add(Triple("从歌单删除", Icons.Outlined.Delete, {
                                        PlayListLibrary.run { targetPlaylist.removeMusic(music) }
                                        // 同步更新当前列表，使 UI 立即刷新
                                        val (title, list) = yos.music.player.data.objects.LibraryObject.getTargetListWithTitle()
                                        yos.music.player.data.objects.LibraryObject.setTargetListWithTitle(
                                            title, list.filter { it.uri != music.uri }
                                        )
                                        Toast.makeText(context, "已从「${targetPlaylist.name}」删除", Toast.LENGTH_SHORT).show()
                                        dismiss()
                                    }))
                                }
                            }
                            add(Triple("下一首播放", Icons.AutoMirrored.Filled.PlaylistPlay, {
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
                            val artistMenuItemLabel = "歌手 · ${music.artistsName ?: defaultArtistsName}"
                            if (showArtistMenuItem && hasMultipleArtists) {
                                add(Triple(artistMenuItemLabel, Icons.Filled.Person, {
                                    menuPage = SongMenuPage.ArtistPicker
                                }))
                            } else if (showArtistMenuItem) {
                                add(Triple(artistMenuItemLabel, Icons.Filled.Person, {
                                    dismiss.after {
                                        music.artistsList?.firstOrNull()?.let { artist ->
                                            yos.music.player.data.objects.LibraryObject.setTargetArtistName(artist)
                                            navController?.toUI(UI.ArtistInfo)
                                        }
                                    }
                                }))
                            }
                            if (showAlbumMenuItem && music.album != null) {
                                add(Triple("专辑 · ${music.album}", Icons.Filled.Album, {
                                    dismiss.after {
                                        music.album?.let { album ->
                                            yos.music.player.data.objects.LibraryObject.setTargetAlbumName(album)
                                            navController?.toUI(UI.AlbumInfo)
                                        }
                                    }
                                }))
                            }
                            add(Triple(if (FavPlayListLibrary.isFavorite(music)) "从喜爱移除" else "添加到喜爱",
                                if (FavPlayListLibrary.isFavorite(music)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            {
                                if (FavPlayListLibrary.isFavorite(music)) FavPlayListLibrary.removeMusic(music)
                                else FavPlayListLibrary.addMusic(music)
                                dismiss()
                            }))
                            add(Triple("详细信息", Icons.Outlined.Info, { menuPage = SongMenuPage.Detail }))
                        }
                        items.forEachIndexed { index, (label, icon, onClick) ->
                            if (index > 0) Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                            Row(
                                Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }
            )

            val playlistPickerPage = playlistPickerDialogPage(
                music = music, context = context, newPlaylistName = newPlaylistName,
                onCreateClick = { menuPage = SongMenuPage.CreatePlaylist }
            )

            val artistPickerPage = OptionDialogPage(
                key = SongMenuPage.ArtistPicker,
                title = music.title ?: defaultTitle,
                subTitle = music.artistsName ?: defaultArtistsName,
                icon = songHeaderIcon,
                horizontalTitle = true,
                content = { dismiss ->
                    Column(
                        Modifier.clip(YosRoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.onSecondary)
                    ) {
                        (music.artistsList ?: emptyList()).forEachIndexed { index, artist ->
                            if (index > 0) Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                            Row(
                                Modifier.fillMaxWidth().height(48.dp).clickable {
                                    dismiss.after {
                                        yos.music.player.data.objects.LibraryObject.setTargetArtistName(artist)
                                        navController?.toUI(UI.ArtistInfo)
                                    }
                                }.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(artist, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            )

            val detailPage = songDetailDialogPage(music)

            val pages = buildList {
                add(mainPage)
                add(playlistPickerPage)
                add(createPlaylistDialogPage(music, context, newPlaylistName) {
                    menuPage = SongMenuPage.PlaylistPicker
                })
                if (navController != null) add(artistPickerPage)
                add(detailPage)
            }
            OptionDialog(
                pages = pages,
                currentPage = menuPage,
                onDismissRequest = { showMenu = false }
            )
        }
    }
}


/** 歌单选择页：菜单内页复用（SongMenu / NowPlaying 更多菜单） */
internal fun playlistPickerDialogPage(
    music: YosMediaItem,
    context: android.content.Context,
    newPlaylistName: androidx.compose.runtime.MutableState<String>,
    onCreateClick: () -> Unit
): OptionDialogPage = OptionDialogPage(
    key = "playlistPicker",
    title = "添加到歌单",
    icon = { Spacer(Modifier.size(0.dp)) },
    content = { dismiss ->
        val playlists = PlayListLibrary.playList
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
        Column(
            Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                .clip(YosRoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.onSecondary)
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { onCreateClick() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("新建歌单", fontSize = 15.sp)
            }
            if (playlists.isEmpty()) {
                Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                Text(
                    "暂无歌单，先创建一个吧", fontSize = 14.sp,
                    modifier = Modifier.alpha(0.5f).padding(16.dp)
                )
            } else {
                playlists.forEachIndexed { index, pl ->
                    if (index > 0) Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                    val coverUri = coverUris[pl.name]
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            PlayListLibrary.run { pl.addMusic(music) }
                            Toast.makeText(context, "已添加到${pl.name}", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }.padding(horizontal = 14.dp, vertical = 12.dp),
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
    onPositive = {}
)

/** 新建歌单页：创建成功后由 dismissOnPositive 直接收卡；onCancel 由宿主决定（一般回歌单选择页） */
internal fun createPlaylistDialogPage(
    music: YosMediaItem,
    context: android.content.Context,
    newPlaylistName: androidx.compose.runtime.MutableState<String>,
    onCancel: () -> Unit
): OptionDialogPage = OptionDialogPage(
    key = "createPlaylist",
    title = "新建歌单",
    icon = { Spacer(Modifier.size(0.dp)) },
    content = {
        YosDialogTextField(
            value = newPlaylistName.value,
            onValueChange = { newPlaylistName.value = it },
            placeholder = "歌单名称"
        )
    },
    positiveContent = "创建并添加",
    negativeContent = "取消",
    onPositive = {
        val name = newPlaylistName.value.trim()
        if (name.isNotBlank()) {
            PlayListLibrary.create(name)
            PlayListLibrary.playList.firstOrNull { it.name == name }
                ?.let { created -> PlayListLibrary.run { created.addMusic(music) } }
            Toast.makeText(context, "已添加到$name", Toast.LENGTH_SHORT).show()
            newPlaylistName.value = ""
        }
    },
    onNegative = onCancel
)

/** 歌曲信息页 */
internal fun songDetailDialogPage(music: YosMediaItem): OptionDialogPage = OptionDialogPage(
    key = "songDetail",
    title = "歌曲信息",
    icon = { Spacer(Modifier.size(0.dp)) },
    content = { SongDetailContent(music) },
    positiveContent = "关闭",
    onPositive = {}
)

/** 详细信息页主体：菜单页内与独立弹窗共用 */
@Composable
private fun SongDetailContent(music: YosMediaItem) {
    val filePath = music.uri?.path
    var info by remember(filePath) { mutableStateOf<yos.music.player.code.AudioMetadataUtils.AudioFileInfo?>(null) }
    var yearFromFile by remember(filePath) { mutableStateOf<Int?>(null) }
    var cachedTags by remember(music.uri) { mutableStateOf<yos.music.player.data.remote.CachedTags?>(null) }
    val playCount = remember(music.uri) {
        yos.music.player.data.libraries.MusicLibrary.getPlayCount(music.uri)
    }

    LaunchedEffect(filePath) {
        if (filePath != null) {
            val (remoteCached, fileInfo, year) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (music.serverId != null) {
                    val cached = yos.music.player.data.remote.RemoteTagDatabase.get(music.uri?.toString() ?: "")
                    // 远程歌曲：从 YosMediaItem 字段构建 AudioFileInfo，不读本地文件
                    val format = music.mimeType?.substringAfterLast("/")?.uppercase()
                        ?: music.uri?.path?.substringAfterLast(".")?.uppercase() ?: ""
                    val sourceLabel = yos.music.player.data.remote.RemoteUri.parseServerId(music.uri?.toString() ?: "")
                        ?.let { sid -> yos.music.player.data.remote.RemoteServerManager.getServer(sid) }
                        ?.let { cfg -> if (cfg.type == yos.music.player.data.remote.ServerType.SMB) "SMB" else "WebDAV" }
                        ?: "远程"
                    Triple(cached, yos.music.player.code.AudioMetadataUtils.AudioFileInfo(
                        bitrate = yos.music.player.code.AudioMetadataUtils.reliableBitrateKbps(
                            cached?.bitrate ?: music.bitrate,
                            cached?.fileSize ?: music.fileSize,
                            cached?.duration ?: music.duration
                        ),
                        sampleRate = cached?.sampleRate ?: music.sampleRate,
                        channels = cached?.channels ?: music.channels,
                        bitsPerSample = null,
                        fileSize = 0L,
                        format = format,
                        source = sourceLabel
                    ), cached?.year ?: music.releaseYear ?: music.recordingYear)
                } else {
                    Triple(
                        null,
                        yos.music.player.code.AudioMetadataUtils.getAudioFileInfo(filePath),
                        yos.music.player.code.AudioMetadataUtils.getYear(filePath)
                    )
                }
            }
            cachedTags = remoteCached
            info = fileInfo
            yearFromFile = year
        }
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
                val cached = cachedTags
                DetailRow("标题", cached?.title ?: music.title ?: defaultTitle)
                DetailRow("艺术家", cached?.artist ?: music.artistsName ?: "未知艺术家")
                (cached?.album ?: music.album)?.let { DetailRow("专辑", it) }
                (cached?.albumArtist ?: music.albumArtists)?.let { DetailRow("专辑艺人", it) }
                (cached?.genre ?: music.genre)?.let { DetailRow("流派", it) }
                (cached?.composer ?: music.composer)?.let { DetailRow("作曲", it) }
                DetailRow("时长", formatDuration(music.duration))
                DetailRow("播放次数", "${playCount}")
                val itemYear = cached?.year ?: music.releaseYear ?: music.recordingYear ?: yearFromFile
                if (itemYear != null) DetailRow("年份", "$itemYear")
                (cached?.trackNumber ?: music.trackNumber)?.let { DetailRow("音轨号", "$it") }
                (cached?.discNumber ?: music.discNumber)?.let { DetailRow("碟号", "$it") }
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
