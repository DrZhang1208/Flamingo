package yos.music.player.ui.pages.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.PersonCropCircle
import yos.music.player.R
import yos.music.player.data.libraries.MusicLibrary.songs
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.albumLibraryEntries
import yos.music.player.data.libraries.artistLibraryEntries
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.YosWrapper
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight

@Composable
fun Library(navController: NavController) {
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Column(
        Modifier
            .fillMaxSize()
        /*.statusBarsPadding()*/
    ) {
        Title(
            title = stringResource(id = R.string.page_library_title),
            rightIcon = CupertinoIcons.Default.PersonCropCircle,
            onRightIcon = {
                navController.toUI(UI.Settings.Main)
            }
        ) {
            if (isLandscape) {
                item("LibraryLandscape") {
                    LibraryLandscapePanes(navController)
                }
                return@Title
            }
            item("Library") {
                Column(
                    Modifier
                        .fillMaxSize(),
                ) {
                    SmallLabelItem(
                        icon = painterResource(id = R.drawable.ic_library_link_icon_playlists),
                        label = stringResource(
                            id = R.string.page_library_playlists
                        )
                    ) {
                        navController.toUI(UI.PlayLists)
                    }
                    LibraryDivider()
                    SmallLabelItem(
                        icon = painterResource(id = R.drawable.ic_library_link_icon_artists),
                        label = stringResource(
                            id = R.string.page_library_artists
                        )
                    ) {
                        navController.toUI(UI.LocalArtists)
                    }
                    LibraryDivider()
                    SmallLabelItem(
                        icon = painterResource(id = R.drawable.ic_library_link_icon_album),
                        label = stringResource(
                            id = R.string.page_library_albums
                        )
                    ) {
                        navController.toUI(UI.LocalAlbums)
                    }
                    LibraryDivider()

                    YosWrapper {
                        val targetTitle = stringResource(
                            id = R.string.page_library_songs
                        )
                        val targetList = songs
                        SmallLabelItem(
                            icon = painterResource(id = R.drawable.ic_library_link_icon_songs),
                            label = targetTitle
                        ) {
                            LibraryObject.setTargetListWithTitle(targetTitle, targetList)
                            navController.toUI(UI.NormalMusic)
                        }
                    }

                    LibraryDivider()
                }
            }
        }
    }
}

/** 横屏三栏：歌单 / 歌手 / 专辑 各一栏卡片，头部链接 + 前几项预览（样式与竖屏一致） */
@Composable
private fun LibraryLandscapePanes(navController: NavController) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val entries = remember(songs) { artistLibraryEntries(songs) }
        val albums = remember(songs) { albumLibraryEntries(songs) }
        val playlists = PlayListLibrary.playList

        // 歌单
        LibraryPane(
            modifier = Modifier.weight(1f),
            iconRes = R.drawable.ic_library_link_icon_playlists,
            title = stringResource(id = R.string.page_library_playlists),
            onMore = { navController.toUI(UI.PlayLists) }
        ) {
            playlists.take(4).forEachIndexed { index, pl ->
                if (index > 0) LibraryPaneDivider()
                LibraryPaneRow(
                    cover = pl.songDataList.firstNotNullOfOrNull { uri ->
                        songs.find { it.uri == uri }?.thumb
                    },
                    name = pl.name,
                    meta = "${pl.songDataList.size} 首"
                ) {
                    LibraryObject.setTargetListWithTitle(
                        pl.name, pl.songDataList.mapNotNull { uri -> songs.find { it.uri == uri } }
                    )
                    navController.toUI(UI.NormalMusic)
                }
            }
        }

        // 歌手
        LibraryPane(
            modifier = Modifier.weight(1f),
            iconRes = R.drawable.ic_library_link_icon_artists,
            title = stringResource(id = R.string.page_library_artists),
            onMore = { navController.toUI(UI.LocalArtists) }
        ) {
            entries.take(4).forEachIndexed { index, artist ->
                if (index > 0) LibraryPaneDivider()
                LibraryPaneRow(
                    cover = artist.songs.firstOrNull()?.thumb,
                    name = artist.name,
                    meta = "${artist.songs.size} 首 · ${artist.albumCount} 专辑",
                    roundCover = true
                ) {
                    LibraryObject.setTargetArtistName(artist.name)
                    navController.toUI(UI.ArtistInfo)
                }
            }
        }

        // 专辑
        LibraryPane(
            modifier = Modifier.weight(1.15f),
            iconRes = R.drawable.ic_library_link_icon_album,
            title = stringResource(id = R.string.page_library_albums),
            onMore = { navController.toUI(UI.LocalAlbums) }
        ) {
            albums.take(4).forEachIndexed { index, album ->
                if (index > 0) LibraryPaneDivider()
                LibraryPaneRow(
                    cover = album.songs.firstOrNull()?.thumb,
                    name = album.name,
                    meta = "${album.songs.size} 首"
                ) {
                    LibraryObject.setTargetAlbumName(album.name)
                    navController.toUI(UI.AlbumInfo)
                }
            }
        }
    }
}

@Composable
private fun LibraryPane(
    modifier: Modifier = Modifier,
    iconRes: Int,
    title: String,
    onMore: () -> Unit,
    rows: @Composable () -> Unit
) {
    RoundColumn(modifier = modifier.fillMaxWidth()) {
        SmallLabelItem(icon = painterResource(id = iconRes), label = title, onClick = onMore)
        rows()
    }
}

@Composable
private fun LibraryPaneDivider() =
    Spacer(
        Modifier
            .fillMaxWidth()
            .alpha(0.15f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White)
    )

@Composable
private fun LibraryPaneRow(
    cover: Any?,
    name: String,
    meta: String,
    roundCover: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = if (roundCover) RoundedCornerShape(50) else RoundedCornerShape(4.dp)
        if (cover != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(cover).size(64).build(),
                contentDescription = null,
                modifier = Modifier.size(34.dp).clip(shape)
            )
        } else {
            Image(
                painter = painterResource(R.drawable.placeholder_playlist_default),
                contentDescription = null,
                modifier = Modifier.size(34.dp).clip(shape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, fontSize = 12.sp, color = (Color.Black withNight Color.White).copy(alpha = 0.4f))
        }
        Icon(
            painterResource(id = R.drawable.ic_action_next), contentDescription = null,
            modifier = Modifier.height(11.dp).alpha(0.3f),
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}
