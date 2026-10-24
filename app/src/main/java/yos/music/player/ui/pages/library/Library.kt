package yos.music.player.ui.pages.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.PersonCropCircle
import yos.music.player.R
import yos.music.player.data.libraries.AlbumLibraryEntry
import yos.music.player.data.libraries.AlbumSort
import yos.music.player.data.libraries.ArtistLibraryEntry
import yos.music.player.data.libraries.ArtistSort
import yos.music.player.data.libraries.LibrarySortIndex
import yos.music.player.data.libraries.MusicLibrary.songs
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.albumLibraryEntries
import yos.music.player.data.libraries.artistLibraryEntries
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.defaultArtists
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.libraries.sortAlbums
import yos.music.player.data.libraries.sortArtists
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.LibrarySortDialog
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.SearchTextField
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.TitleBarIcon
import yos.music.player.ui.widgets.basic.YosWrapper
import yos.music.player.ui.widgets.basic.paneDivider
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
                    LibraryLandscapeSplit(navController, Modifier.fillParentMaxHeight())
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

/** 横屏分栏：左侧资料库菜单，右侧所选分类的内容列表 */
private enum class LibrarySection { PLAYLISTS, ARTISTS, ALBUMS, SONGS }

@Composable
private fun LibraryLandscapeSplit(navController: NavController, modifier: Modifier = Modifier) {
    var section by remember { mutableStateOf(LibrarySection.SONGS) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 左菜单列：超出可独立滚动
        Column(Modifier.weight(0.95f).fillMaxHeight()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LibraryMenuRow(
                    iconRes = R.drawable.ic_library_link_icon_playlists,
                    label = stringResource(id = R.string.page_library_playlists),
                    selected = section == LibrarySection.PLAYLISTS
                ) { section = LibrarySection.PLAYLISTS }
                LibraryDivider()
                LibraryMenuRow(
                    iconRes = R.drawable.ic_library_link_icon_artists,
                    label = stringResource(id = R.string.page_library_artists),
                    selected = section == LibrarySection.ARTISTS
                ) { section = LibrarySection.ARTISTS }
                LibraryDivider()
                LibraryMenuRow(
                    iconRes = R.drawable.ic_library_link_icon_album,
                    label = stringResource(id = R.string.page_library_albums),
                    selected = section == LibrarySection.ALBUMS
                ) { section = LibrarySection.ALBUMS }
                LibraryDivider()
                LibraryMenuRow(
                    iconRes = R.drawable.ic_library_link_icon_songs,
                    label = stringResource(id = R.string.page_library_songs),
                    selected = section == LibrarySection.SONGS
                ) { section = LibrarySection.SONGS }
            }
        }
        // 右栏：头部（分类名+搜索+排序）+ 内容列表，独立滚动；左缘外侧画分栏竖线
        Column(
            Modifier
                .weight(2.05f)
                .fillMaxHeight()
                .paneDivider()
        ) {
            when (section) {
                LibrarySection.PLAYLISTS -> LibraryPlaylistsContent(navController)
                LibrarySection.ARTISTS -> LibraryArtistsContent(navController)
                LibrarySection.ALBUMS -> LibraryAlbumsContent(navController)
                LibrarySection.SONGS -> LibrarySongsContent()
            }
        }
    }
}

/** 右栏通用骨架：头部行（分类名 + 排序入口）+ 可选搜索框 + 内容 */
@Composable
private fun LibraryPane(
    title: String,
    showSort: Boolean,
    onSort: () -> Unit,
    searchPlaceholder: String?,
    searchText: MutableState<String>?,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxHeight()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (showSort) {
                TitleBarIcon(
                    icon = androidx.compose.material.icons.Icons.Rounded.Sort,
                    onBack = onSort
                )
            }
        }
        if (searchText != null && searchPlaceholder != null) {
            SearchTextField(
                text = searchText.value,
                placeholder = searchPlaceholder,
                onValueChange = { searchText.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                onSearch = {}
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** 菜单行：与设置分栏菜单同款（左红竖条+选中浅红底），横屏紧凑行高避让迷你条 */
@Composable
private fun LibraryMenuRow(iconRes: Int, label: String, selected: Boolean, onClick: () -> Unit) =
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .androidxSelectedBg(selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(50)
                )
        )
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(22.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            fontSize = 14.5.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 8.dp)
        )
    }

@Composable
private fun LibraryPlaylistsContent(navController: NavController) {
    val playlists = PlayListLibrary.playList
    LibraryPane(
        title = stringResource(id = R.string.page_library_playlists),
        showSort = false,
        onSort = {},
        searchPlaceholder = null,
        searchText = null
    ) {
        if (playlists.isEmpty()) { LibraryEmptyHint(); return@LibraryPane }
        LazyColumn(Modifier.fillMaxHeight()) {
            itemsIndexed(playlists) { index, pl ->
                if (index > 0) LibraryPaneDivider()
                LibraryPaneRow(
                    cover = pl.songDataList.firstNotNullOfOrNull { uri -> songs.find { it.uri == uri }?.thumb },
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
    }
}

@Composable
private fun LibraryArtistsContent(navController: NavController) {
    val librarySongs = songs
    val orders = produceState<LibrarySortIndex<ArtistLibraryEntry, ArtistSort>?>(null, librarySongs) {
        value = withContext(Dispatchers.Default) {
            LibrarySortIndex(artistLibraryEntries(librarySongs), ArtistSort.entries, ::sortArtists)
        }
    }
    val searchText = remember { mutableStateOf("") }
    val showSort = remember { mutableStateOf(false) }
    if (showSort.value) {
        LibrarySortDialog(
            options = listOf(
                stringResource(R.string.library_sort_artist),
                stringResource(R.string.library_sort_songs),
                stringResource(R.string.library_sort_albums)
            ),
            selected = SettingsLibrary.ArtistSortMode,
            descending = SettingsLibrary.ArtistSortDescending,
            onSelect = { mode, reversed ->
                SettingsLibrary.ArtistSortMode = mode
                SettingsLibrary.ArtistSortDescending = reversed
            },
            onDismiss = { showSort.value = false }
        )
    }
    val query = searchText.value
    val sortMode = SettingsLibrary.ArtistSortMode
    val descending = SettingsLibrary.ArtistSortDescending
    val list = remember(orders.value, query, sortMode, descending) {
        val ordered = orders.value?.ordered(
            ArtistSort.entries.getOrElse(sortMode) { ArtistSort.NAME }, descending
        ).orEmpty()
        if (query.isBlank()) ordered else ordered.filter { it.name.contains(query, ignoreCase = true) }
    }
    LibraryPane(
        title = stringResource(id = R.string.page_library_artists),
        showSort = true,
        onSort = { showSort.value = true },
        searchPlaceholder = stringResource(id = R.string.page_library_search_artists),
        searchText = searchText
    ) {
        if (list.isEmpty()) { LibraryEmptyHint(); return@LibraryPane }
        LazyColumn(Modifier.fillMaxHeight()) {
            itemsIndexed(list, key = { _, artist -> artist.name }) { index, artist ->
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
    }
}

@Composable
private fun LibraryAlbumsContent(navController: NavController) {
    val librarySongs = songs
    val orders = produceState<LibrarySortIndex<AlbumLibraryEntry, AlbumSort>?>(null, librarySongs) {
        value = withContext(Dispatchers.Default) {
            LibrarySortIndex(albumLibraryEntries(librarySongs), AlbumSort.entries, ::sortAlbums)
        }
    }
    val searchText = remember { mutableStateOf("") }
    val showSort = remember { mutableStateOf(false) }
    if (showSort.value) {
        LibrarySortDialog(
            options = listOf(
                stringResource(R.string.library_sort_album),
                stringResource(R.string.library_sort_year),
                stringResource(R.string.library_sort_updated),
                stringResource(R.string.library_sort_artist),
                stringResource(R.string.library_sort_tracks)
            ),
            selected = SettingsLibrary.AlbumSortMode,
            descending = SettingsLibrary.AlbumSortDescending,
            onSelect = { mode, reversed ->
                SettingsLibrary.AlbumSortMode = mode
                SettingsLibrary.AlbumSortDescending = reversed
            },
            onDismiss = { showSort.value = false }
        )
    }
    val query = searchText.value
    val sortMode = SettingsLibrary.AlbumSortMode
    val descending = SettingsLibrary.AlbumSortDescending
    val list = remember(orders.value, query, sortMode, descending) {
        val ordered = orders.value?.ordered(
            AlbumSort.entries.getOrElse(sortMode) { AlbumSort.NAME }, descending
        ).orEmpty()
        if (query.isBlank()) ordered else ordered.filter { it.name.contains(query, ignoreCase = true) }
    }
    LibraryPane(
        title = stringResource(id = R.string.page_library_albums),
        showSort = true,
        onSort = { showSort.value = true },
        searchPlaceholder = stringResource(id = R.string.page_library_search_album),
        searchText = searchText
    ) {
        if (list.isEmpty()) { LibraryEmptyHint(); return@LibraryPane }
        LazyColumn(Modifier.fillMaxHeight()) {
            itemsIndexed(list, key = { _, album -> album.name }) { index, album ->
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
private fun LibrarySongsContent() {
    // 直接读音乐库实况，不依赖竖屏入口设置的 LibraryObject target（默认进入即有数据）
    val all = songs
    val searchText = remember { mutableStateOf("") }
    val showSort = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (showSort.value) {
        val sortOptions = listOf(
            SettingsLibrary.SongSortEnum.MUSIC_TITLE.ordinal to "标题",
            SettingsLibrary.SongSortEnum.MUSIC_ALBUM.ordinal to "专辑",
            SettingsLibrary.SongSortEnum.ARTIST_NAME.ordinal to "艺术家",
            SettingsLibrary.SongSortEnum.MODIFIED_DATE.ordinal to "修改日期",
            SettingsLibrary.SongSortEnum.MUSIC_ADD_DATE.ordinal to "添加时间",
            SettingsLibrary.SongSortEnum.PLAY_COUNT.ordinal to "播放次数"
        )
        LibrarySortDialog(
            options = sortOptions.map { it.second },
            selected = sortOptions.indexOfFirst { it.first == SettingsLibrary.SongSort },
            descending = SettingsLibrary.EnableDescending,
            onSelect = { index, descending ->
                SettingsLibrary.SongSort = sortOptions[index].first
                SettingsLibrary.EnableDescending = descending
            },
            onDismiss = { showSort.value = false }
        )
    }
    val query = searchText.value
    val sort = SettingsLibrary.SongSort
    val descending = SettingsLibrary.EnableDescending
    val list = remember(all, query, sort, descending) {
        val sorted = all.sortX()
        if (query.isBlank()) sorted
        else sorted.filter {
            (it.title ?: defaultTitle).contains(query, ignoreCase = true) ||
                (it.artistsList ?: defaultArtists).any { artist -> artist.contains(query, ignoreCase = true) }
        }
    }
    LibraryPane(
        title = stringResource(id = R.string.page_library_songs),
        showSort = true,
        onSort = { showSort.value = true },
        searchPlaceholder = stringResource(id = R.string.page_library_search_songs),
        searchText = searchText
    ) {
        Column(Modifier.fillMaxHeight()) {
            if (list.isNotEmpty()) {
                // 参考横屏工具行：随机播放（含总数）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                yos.music.player.code.MediaController.prepare(
                                    list.random(), list, shuffleModeEnabled = true
                                )
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.button_icon_shuffle),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.normal_button_shuffle) + " · ${list.size}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                LibraryPaneDivider()
                Spacer(Modifier.height(2.dp))
            }
            if (list.isEmpty()) {
                LibraryEmptyHint()
            } else {
                LazyColumn(Modifier.fillMaxHeight()) {
                    itemsIndexed(list, key = { _, m -> m.uri?.toString() ?: (m.mediaId ?: "") }) { index, music ->
                        if (index > 0) LibraryPaneDivider()
                        MusicList(music, null) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                yos.music.player.code.MediaController.prepare(music, list)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyHint() =
    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Text("暂无内容", fontSize = 14.sp, color = (Color.Black withNight Color.White).copy(alpha = 0.35f))
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


/** 菜单选中底色：珊瑚红浅底（与竖屏 iOS 选中行一致） */
private fun Modifier.androidxSelectedBg(selected: Boolean): Modifier = composed {
    background(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else Color.Transparent
    )
}
