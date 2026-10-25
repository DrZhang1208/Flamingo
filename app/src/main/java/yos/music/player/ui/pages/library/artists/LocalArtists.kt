package yos.music.player.ui.pages.library.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.data.libraries.LibrarySortIndex
import yos.music.player.data.libraries.ArtistLibraryEntry
import yos.music.player.data.libraries.ArtistSort
import yos.music.player.data.libraries.artistLibraryEntries
import yos.music.player.data.libraries.sortArtists
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.widgets.basic.LibrarySortDialog
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.SearchTextField
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.TitleBarIcon
import yos.music.player.ui.widgets.basic.YosWrapper

@Composable
fun LocalArtists(
    navController: NavController,
    onBack: (() -> Unit)? = null,
    // 横屏资料库右栏内打开艺人：非空时点击不走路由
    onOpenArtist: ((String) -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxSize()
        /*.statusBarsPadding()*/
    ) {
        val librarySongs = MusicLibrary.songs
        val orders = produceState<LibrarySortIndex<ArtistLibraryEntry, ArtistSort>?>(null, librarySongs) {
            value = withContext(Dispatchers.Default) {
                LibrarySortIndex(artistLibraryEntries(librarySongs), ArtistSort.entries, ::sortArtists)
            }
        }
        val listState = rememberLazyListState()
        val searchText = remember { mutableStateOf("") }
        val showSort = remember { mutableStateOf(false) }
        val sortMode = SettingsLibrary.ArtistSortMode
        val descending = SettingsLibrary.ArtistSortDescending
        val hideMusic = remember(librarySongs) { mutableStateOf(librarySongs.isEmpty()) }
        if (showSort.value) {
            LibrarySortDialog(
                options = listOf(stringResource(R.string.library_sort_artist), stringResource(R.string.library_sort_songs), stringResource(R.string.library_sort_albums)),
                selected = sortMode, descending = descending,
                onSelect = { mode, reversed ->
                    SettingsLibrary.ArtistSortMode = mode
                    SettingsLibrary.ArtistSortDescending = reversed
                    listState.requestScrollToItem(0)
                },
                onDismiss = { showSort.value = false }
            )
        }
        if (hideMusic.value) {
            val message =
                stringResource(
                    id = R.string.tip_no_song
                )
            Title(
                title = stringResource(id = R.string.page_library_artists), onBack = onBack
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
                val ordered = index?.ordered(ArtistSort.entries.getOrElse(sortMode) { ArtistSort.NAME }, descending).orEmpty()
                if (query.isBlank()) ordered else ordered.filter { it.name.contains(query, ignoreCase = true) }
            }

            Title(
                title = stringResource(id = R.string.page_library_artists), onBack = onBack,
                state = listState,
                rightBarIcon = {
                    TitleBarIcon(
                        icon = Icons.Rounded.Sort,
                        onBack = { showSort.value = true }
                    )
                }
            ) {
                item("SearchField") {
                    val keyboardController = LocalSoftwareKeyboardController.current

                    SearchTextField(
                        text = searchText.value,
                        placeholder = stringResource(id = R.string.page_library_search_artists),
                        onValueChange = {
                            searchText.value = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .padding(top = 5.dp, bottom = 12.dp),
                        onSearch = {
                            if (searchText.value.isNotEmpty()) {
                                keyboardController?.hide()
                            }
                        })
                }

                itemsIndexed(
                    list,
                    key = { _, artist -> artist.name }/*,
                    contentType = { _, _ -> "LocalArtists_item" }*/
                ) { index, artist ->
                    ArtistItem(entry = artist) {
                        if (onOpenArtist != null) {
                            onOpenArtist(artist.name)
                        } else {
                            LibraryObject.setTargetArtistName(artist.name)
                            navController.toUI(UI.ArtistInfo)
                        }
                    }

                    key(index) {
                        val needDivider = index < list.size - 1
                        if (needDivider) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 81.dp)
                                    .alpha(0.15f)
                                    .height(0.5.dp)
                                    .background(Color.Black withNight Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.ArtistItem(
    modifier: Modifier = Modifier,
    entry: ArtistLibraryEntry,
    onClick: () -> Unit
) =
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val artistName = entry.name
        val songs = entry.songs
        val albumCount = entry.albumCount
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            YosWrapper {
                val shape = CircleShape

                    val density = LocalDensity.current
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(data = songs.getOrNull(0)?.thumb).crossfade(true)
                            .error(R.drawable.songcredits_monogram_person)
                            .placeholder(R.drawable.songcredits_monogram_person)
                            .fallback(R.drawable.songcredits_monogram_person)
                            .allowHardware(true)
                            .precision(Precision.INEXACT)
                            .size(128)
                            .build(),
                        contentDescription = "Artist_Image",
                        contentScale = ContentScale.Crop,
                        modifier = modifier
                            .size(48.dp)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                                clip = true
                                this.shape = shape
                            }
                            .drawWithCache {
                                onDrawWithContent {
                                    drawContent()
                                    val outline = shape.createOutline(
                                        Size(size.width, size.height),
                                        LayoutDirection.Ltr,
                                        density
                                    )
                                    drawOutline(
                                        outline = outline,
                                        color = Color.DarkGray.copy(alpha = 0.08f),
                                        style = Stroke(width = 6f)
                                    )
                                    drawOutline(
                                        outline = outline,
                                        color = Color.DarkGray.copy(alpha = 0.4f),
                                        style = Stroke(width = 6f),
                                        blendMode = BlendMode.Overlay
                                    )
                                }
                            }
                    )
            }
        }
        Spacer(modifier = Modifier.width(15.dp))
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = artistName,
                fontSize = 16.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            Text(
                text = stringResource(R.string.page_library_artist_counts, songs.size, albumCount),
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_action_next), contentDescription = null,
            modifier = Modifier
                .height(12.dp).padding(end = 8.dp)
                .alpha(0.3f), tint = MaterialTheme.colorScheme.onBackground
        )
    }
