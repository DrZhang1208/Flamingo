package yos.music.player.ui.pages.library.playlists

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastMapNotNull
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.data.libraries.FavPlayListLibrary
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.MusicLibrary.songs
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.playList
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.OptionDialog
import yos.music.player.ui.widgets.basic.Title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayLists(navController: NavController) {
    val playLists = playList.sortedBy { it.name }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        OptionDialog(
            icon = { Spacer(Modifier.size(0.dp)) },
            title = "新建歌单",
            content = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            positiveContent = "创建",
            negativeContent = "取消",
            onPositive = {
                if (newPlaylistName.isNotBlank()) {
                    PlayListLibrary.create(newPlaylistName)
                    newPlaylistName = ""
                    showCreateDialog = false
                }
            },
            onNegative = { showCreateDialog = false },
            onDismissRequest = { showCreateDialog = false }
        )
    }

    Title(title = stringResource(id = R.string.page_library_playlists),
        onBack = { navController.popBackStack() },
        content = {
            item("AddList") {
                PlayListItem(playListType = PlayListType.Add, title = context.getString(R.string.page_library_playlists_add_title)) {
                    showCreateDialog = true
                }
                Spacer(Modifier.fillMaxWidth().padding(start = 86.dp).alpha(0.15f).height(0.5.dp).background(Color.Black withNight Color.White))
            }

            item("FavList") {
                PlayListItem(playListType = PlayListType.Favorite, title = context.getString(R.string.page_library_playlists_fav_title)) {
                    scope.launch(Dispatchers.IO) {
                        val targetList = FavPlayListLibrary.favPlayList
                        LibraryObject.setTargetListWithTitle(context.getString(R.string.page_library_playlists_fav_title), targetList)
                        withContext(Dispatchers.Main) { navController.toUI(UI.NormalMusic) }
                    }
                }
                Spacer(Modifier.fillMaxWidth().padding(start = 86.dp).alpha(0.15f).height(0.5.dp).background(Color.Black withNight Color.White))
            }

            itemsIndexed(playLists, key = { _, pl -> pl.listID }) { index, pl ->
                PlayListItem(playList = pl) {
                    scope.launch(Dispatchers.IO) {
                        val targetList = convertToSongList(pl.songDataList, songs)
                        LibraryObject.setTargetListWithTitle(pl.name, targetList)
                        withContext(Dispatchers.Main) { navController.toUI(UI.NormalMusic) }
                    }
                }
                key(index) {
                    if (index < playLists.size - 1) {
                        Spacer(Modifier.fillMaxWidth().padding(start = 86.dp).alpha(0.15f).height(0.5.dp).background(Color.Black withNight Color.White))
                    }
                }
            }
        }
    )
}

private fun convertToSongList(songDataList: List<Uri>, songs: List<YosMediaItem>): List<YosMediaItem> {
    return songDataList.fastMapNotNull { uri -> songs.find { it.uri == uri } }
}

@Stable
private enum class PlayListType { Add, Favorite }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LazyItemScope.PlayListItem(playList: PlayList, itemClick: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(playList.name) }

    val coverUri = remember(playList.songDataList) {
        playList.songDataList
            .mapNotNull { uri -> songs.find { it.uri == uri } }
            .maxByOrNull { MusicLibrary.getPlayCount(it.uri) }
            ?.thumb
    }

    Row(
        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).height(80.dp).fillMaxWidth()
            .clickable { itemClick() }
            .padding(start = 22.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = YosRoundedCornerShape(4.dp)
        val density = LocalDensity.current
        if (coverUri != null) {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(coverUri).size(128).build(),
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(shape))
        } else {
            Image(painter = painterResource(R.drawable.placeholder_playlist_default), contentDescription = null,
                modifier = Modifier.size(64.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; clip = true; this.shape = shape }
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            val o = shape.createOutline(Size(size.width, size.height), LayoutDirection.Ltr, density)
                            drawOutline(o, Color.Gray.copy(alpha = 0.1f), style = Stroke(width = 8f))
                            drawOutline(o, Color.Gray.copy(alpha = 0.5f), style = Stroke(width = 8f), blendMode = BlendMode.Overlay)
                        }
                    })
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(playList.name, modifier = Modifier.padding(bottom = 1.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, lineHeight = 16.sp)
        }
        Icon(painterResource(R.drawable.ic_nowplaying_more), "菜单",
            Modifier.size(20.dp).alpha(0.3f).clickable(remember { MutableInteractionSource() }, null) { showMenu = true })

        // 菜单对话框 — 参考 SongMenuIcon 的两对话框模式
        if (showMenu) {
            OptionDialog(
                icon = {
                    if (coverUri != null) {
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(coverUri).size(128).build(),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(YosRoundedCornerShape(6.dp)))
                    } else {
                        Image(painter = painterResource(R.drawable.placeholder_playlist_default), contentDescription = null,
                            modifier = Modifier.size(52.dp).clip(YosRoundedCornerShape(6.dp)))
                    }
                },
                title = playList.name,
                horizontalTitle = true,
                content = { dismiss ->
                    Column {
                        Row(Modifier.fillMaxWidth().height(48.dp).clickable {
                            dismiss(); renameText = playList.name; showRename = true
                        }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("重命名", fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.Edit, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                        Row(Modifier.fillMaxWidth().height(48.dp).clickable {
                            PlayListLibrary.remove(playList); dismiss()
                        }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("删除", fontSize = 16.sp, modifier = Modifier.weight(1f), color = Color.Red.copy(alpha = 0.7f))
                            Icon(Icons.Outlined.Delete, null, Modifier.size(22.dp), tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                },
                onDismissRequest = { showMenu = false }
            )
        }

        // 重命名对话框 — 独立的 OptionDialog，dismiss 后再出现 = 下滑 + 上滑
        if (showRename) {
            OptionDialog(
                icon = {
                    if (coverUri != null) {
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(coverUri).size(128).build(),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(YosRoundedCornerShape(6.dp)))
                    } else {
                        Image(painter = painterResource(R.drawable.placeholder_playlist_default), contentDescription = null,
                            modifier = Modifier.size(52.dp).clip(YosRoundedCornerShape(6.dp)))
                    }
                },
                title = "重命名歌单",
                horizontalTitle = true,
                content = {
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it },
                        label = { Text("歌单名称") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                },
                positiveContent = "确定", negativeContent = "取消",
                onPositive = { if (renameText.isNotBlank()) { PlayListLibrary.run { playList.rename(renameText) }; showRename = false } },
                onNegative = { showRename = false },
                onDismissRequest = { showRename = false }
            )
        }
    }
}

@Composable
private fun LazyItemScope.PlayListItem(playListType: PlayListType, title: String, itemClick: () -> Unit) {
    Row(
        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).height(80.dp).fillMaxWidth()
            .clickable { itemClick() }.padding(start = 22.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = YosRoundedCornerShape(4.dp)
        val density = LocalDensity.current
        Box(
            modifier = Modifier.size(64.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; clip = true; this.shape = shape }
                .background(Color(0xFFF0F0F0) withNight Color(0xFF2C2C2E), shape)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        val o = shape.createOutline(Size(size.width, size.height), LayoutDirection.Ltr, density)
                        drawOutline(o, Color.Gray.copy(alpha = 0.1f), style = Stroke(width = 8f))
                        drawOutline(o, Color.Gray.copy(alpha = 0.5f), style = Stroke(width = 8f), blendMode = BlendMode.Overlay)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when (playListType) {
                PlayListType.Add -> Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color(0xFFFA233B)  // red accent, consistent across themes
                )
                PlayListType.Favorite -> Icon(
                    painter = painterResource(R.drawable.ic_nowplaying_favorite),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color(0xFFFF9500)  // orange/gold for star
                )
            }
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, modifier = Modifier.padding(bottom = 1.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, lineHeight = 16.sp)
        }
    }
}

