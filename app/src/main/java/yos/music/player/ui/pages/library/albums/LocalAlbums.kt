package yos.music.player.ui.pages.library.albums

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.data.libraries.LibrarySortIndex
import yos.music.player.data.libraries.AlbumLibraryEntry
import yos.music.player.data.libraries.AlbumSort
import yos.music.player.data.libraries.albumLibraryEntries
import yos.music.player.data.libraries.sortAlbums
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.widgets.basic.LibrarySortDialog
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.theme.isLandscape
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.SearchTextField
import yos.music.player.ui.widgets.basic.ShadowImage
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.TitleBarIcon
import yos.music.player.ui.widgets.basic.TitleWithLazyVerticalGrid

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LocalAlbums(
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    onBack: (() -> Unit)? = null,
    // 横屏资料库右栏内打开专辑：非空时点击不走路由
    onOpenAlbum: ((String) -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxSize()
        /*.statusBarsPadding()*/
    ) {
        val librarySongs = MusicLibrary.songs
        val orders = produceState<LibrarySortIndex<AlbumLibraryEntry, AlbumSort>?>(null, librarySongs) {
            value = withContext(Dispatchers.Default) {
                LibrarySortIndex(albumLibraryEntries(librarySongs), AlbumSort.entries, ::sortAlbums)
            }
        }
        val listState = rememberLazyGridState()
        val searchText = remember { mutableStateOf("") }
        val showSort = remember { mutableStateOf(false) }
        val sortMode = SettingsLibrary.AlbumSortMode
        val descending = SettingsLibrary.AlbumSortDescending
        val hideMusic = remember(librarySongs) { mutableStateOf(librarySongs.isEmpty()) }
        if (showSort.value) {
            LibrarySortDialog(
                options = listOf(stringResource(R.string.library_sort_album), stringResource(R.string.library_sort_year), stringResource(R.string.library_sort_updated), stringResource(R.string.library_sort_artist), stringResource(R.string.library_sort_tracks)),
                selected = sortMode, descending = descending,
                onSelect = { mode, reversed ->
                    SettingsLibrary.AlbumSortMode = mode
                    SettingsLibrary.AlbumSortDescending = reversed
                    listState.requestScrollToItem(0)
                },
                onDismiss = { showSort.value = false }
            )
        }
        val albumColumns = if (isLandscape()) 3 else 2
        if (hideMusic.value) {
            val message =
                stringResource(
                    id = R.string.tip_no_song
                )
            Title(
                title = stringResource(id = R.string.page_library_albums), onBack = onBack
            ) {
                item("tip_no_song") {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    ) {
                        Text(text = message, fontSize = 18.sp, modifier = Modifier.alpha(0.6f))
                    }
                }
            }
        } else {
            val query = searchText.value
            val index = orders.value
            val list = remember(index, query, sortMode, descending) {
                val ordered = index?.ordered(AlbumSort.entries.getOrElse(sortMode) { AlbumSort.NAME }, descending).orEmpty()
                if (query.isBlank()) ordered else ordered.filter { it.name.contains(query, ignoreCase = true) }
            }

            TitleWithLazyVerticalGrid(
                title = stringResource(id = R.string.page_library_albums), onBack = onBack,
                columns = { albumColumns },
                state = listState,
                rightBarIcon = {
                    TitleBarIcon(
                        icon = Icons.Rounded.Sort,
                        onBack = { showSort.value = true }
                    )
                }
            ) {
                item("SearchField", span = { GridItemSpan(maxLineSpan) }) {
                    val keyboardController = LocalSoftwareKeyboardController.current

                    SearchTextField(
                        text = searchText.value,
                        placeholder = stringResource(id = R.string.page_library_search_album),
                        onValueChange = {
                            searchText.value = it
                        },
                        modifier = Modifier
                            .fillMaxWidth(),
                        onSearch = {
                            if (searchText.value.isNotEmpty()) {
                                keyboardController?.hide()
                            }
                        })
                }
                itemsIndexed(
                    list,
                    key = { _, album -> album.name }/*,
                    contentType = { _, _ -> "LocalAlbums_item" }*/
                ) { _, album ->
                    AlbumItems(
                        entry = album,
                        animatedContentScope = animatedContentScope,
                        sharedTransitionScope = sharedTransitionScope
                    ) {
                        if (onOpenAlbum != null) {
                            onOpenAlbum(album.name)
                        } else {
                            LibraryObject.setTargetAlbumName(album.name)
                            navController.toUI(UI.AlbumInfo)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LazyGridItemScope.AlbumItems(
    entry: AlbumLibraryEntry,
    sharedTransitionScope: SharedTransitionScope?,
    animatedContentScope: AnimatedContentScope?,
    onClick: () -> Unit
) {
    val albumName = entry.name
    val songs = entry.songs
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        /*with(sharedTransitionScope) {*/
        ShadowImage(
            dataLambda = { songs.getOrNull(0)?.thumb },
            contentDescription = "Album",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp)
            /*.sharedElement(
                sharedTransitionScope.rememberSharedContentState(key = "image-$albumName"),
                animatedVisibilityScope = animatedContentScope
            )*/,
            shadowAlpha = 0f,
            cornerRadius = 7.dp,
            imageQuality = ImageQuality.HIGH
        )
        Text(
            text = albumName,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .alpha(0.9f)
            /*.sharedElement(
                sharedTransitionScope.rememberSharedContentState(key = "album_name-$albumName"),
                animatedVisibilityScope = animatedContentScope
            )*/
        )
        /*}*/

        Text(
            text = stringResource(id = R.string.page_library_album_desc, songs.size),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(0.6f),
            lineHeight = 17.sp
        )
    }
}
