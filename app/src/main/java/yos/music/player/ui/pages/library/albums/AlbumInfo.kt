package yos.music.player.ui.pages.library.albums

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import com.cormor.overscroll.core.overScrollVertical
import com.cormor.overscroll.core.rememberOverscrollFlingBehavior
import com.google.accompanist.insets.navigationBarsHeight
import com.google.accompanist.insets.statusBarsHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultArtists
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImage
import yos.music.player.ui.widgets.basic.SongMenuIcon
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.YosWrapper
import yos.music.player.ui.widgets.effects.ShadowType

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumInfo(
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) =
    Box(
        Modifier
            .fillMaxSize()
        /*.statusBarsPadding()*/
    ) {
        val albumName = rememberSaveable(key = "AlbumInfo_albumName") {
            mutableStateOf(LibraryObject.getTargetAlbumName())
        }

        val hideMusic = remember("AlbumInfo_showMusic") {
            derivedStateOf {
                albumName.value.isEmpty()
            }
        }
        if (hideMusic.value) {
            val message = stringResource(id = R.string.tip_no_album_info)
            Title(
                title = stringResource(id = R.string.page_library_album_info_title), onBack = {
                    navController.popBackStack()
                }
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
            val songs = MusicLibrary.Album[albumName.value].sortedWith(
                compareBy<YosMediaItem>({ it.discNumber ?: 1 }, { it.trackNumber ?: 0 }, { it.title })
            )
            val discGroups = remember(songs) {
                songs.groupBy { it.discNumber ?: 1 }.toSortedMap()
            }
            val isMultiDisc = discGroups.size > 1

            val mainArtists = rememberSaveable(key = "AlbumInfo_mainArtists") {
                mutableStateOf(songs.first().artistsList ?: defaultArtists)
            }
            val mainArtistsName = rememberSaveable(key = "AlbumInfo_mainArtistsName") {
                mutableStateOf(songs.first().artistsName ?: defaultArtistsName)
            }

            val albumArtists = remember(songs) { songs.flatMap { it.artistsList ?: emptyList() }.distinct().sorted() }

            val (songCount, totalMinutes) = rememberSaveable(songs) {
                val totalDuration = songs.sumOf { it.duration }
                val totalMinutes = totalDuration / 60000
                val songCount = songs.size
                songCount to totalMinutes
            }

            val scope = rememberCoroutineScope()

            Title(title = albumName.value, onBack = { navController.popBackStack() }, showLargeTitle = false) {
                item("AlbumInfo") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 9.5.dp)
                            .padding(horizontal = 18.dp)
                            .statusBarsPadding(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        /*with(sharedTransitionScope) {*/
                        ShadowImage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 54.5.dp)
                            /*.sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = "image-$albumName"),
                                animatedVisibilityScope = animatedContentScope
                            )*/,
                            dataLambda = { songs.getOrNull(0)?.thumb },
                            contentDescription = null,
                            cornerRadius = 7.dp,
                            imageQuality = ImageQuality.RAW,
                            shadowType = ShadowType.Medium
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = albumName.value,
                            fontSize = 17.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 23.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val albumYear = songs.firstOrNull()?.releaseYear ?: songs.firstOrNull()?.recordingYear
                        Text(
                            text = if (albumYear != null) "ALBUM · $albumYear" else "ALBUM",
                            fontSize = 11.5.sp,
                            modifier = Modifier
                                .alpha(0.4f)
                                .padding(top = 2.dp)
                        )

                        YosWrapper {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 15.dp)
                            ) {

                                NormalButton(
                                    icon = painterResource(id = R.drawable.button_icon_play),
                                    label = stringResource(id = R.string.normal_button_play),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch(Dispatchers.IO) {
                                        MediaController.prepare(
                                            songs.first(),
                                            songs
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(15.dp))
                                NormalButton(
                                    icon = painterResource(id = R.drawable.button_icon_shuffle),
                                    label = stringResource(id = R.string.normal_button_shuffle),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch(Dispatchers.IO) {
                                        MediaController.prepare(
                                            songs.random(),
                                            songs,
                                            shuffleModeEnabled = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    AlbumDivider()
                }

                // 按碟号分组展示，多碟专辑显示碟片标题
                discGroups.forEach { (disc, discSongs) ->
                    if (isMultiDisc) {
                        item("disc_header_$disc") {
                            Text(
                                text = "碟片 $disc",
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .padding(horizontal = 18.dp)
                                    .padding(top = 12.dp, bottom = 6.dp)
                                    .alpha(0.5f)
                            )
                        }
                    }
                    itemsIndexed(
                        discSongs.sortedWith(compareBy({ it.trackNumber ?: 0 }, { it.title })),
                        key = { idx, music -> "disc${disc}_${idx}_${music.uri}" }
                    ) { index, music ->
                        key(music) {
                            AlbumSongsItem(
                                music = music,
                                mainArtists = mainArtists.value
                            ) {
                                scope.launch(Dispatchers.IO) {
                                    MediaController.prepare(
                                        music,
                                        songs
                                    )
                                }
                            }
                        }

                        key(index) {
                            val isLastInDisc = index == discSongs.size - 1
                            val isLastOverall = disc == discGroups.keys.last() && isLastInDisc
                            if (!isLastOverall) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 50.dp, end = 18.dp)
                                        .alpha(0.25f)
                                        .height(0.5.dp)
                                        .background(Color.Black withNight Color.White)
                                )
                            }
                        }
                    }
                }

                item {
                    AlbumDivider()
                }

                item("AlbumInfo_others") {
                    Text(
                        text = "$songCount 首歌曲，约 $totalMinutes 分钟",
                        fontSize = 15.sp, modifier = Modifier
                            .alpha(0.4f)
                            .padding(horizontal = 18.dp)
                            .padding(top = 18.dp)
                    )
                }

                // 参与歌手
                if (albumArtists.isNotEmpty()) {
                    item("AlbumArtists_header") {
                        Spacer(Modifier.height(24.dp))
                        AlbumDivider()
                        Text("歌手", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 18.dp).padding(top = 16.dp, bottom = 8.dp))
                    }
                    itemsIndexed(albumArtists, key = { _, artist -> artist }) { _, artist ->
                        val artistSongs = MusicLibrary.Artist[artist]
                        val artistCover = artistSongs.firstOrNull()?.thumb
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                yos.music.player.data.objects.LibraryObject.setTargetArtistName(artist)
                                navController.toUI(UI.ArtistInfo)
                            }.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(artistCover).size(96).build(),
                                contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(52.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(artist, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${artistSongs.size} 首", fontSize = 12.sp, modifier = Modifier.alpha(0.4f).padding(top = 2.dp))
                            }
                        }
                    }
                }

                item("album_bottom_navbar") {
                    Spacer(modifier = Modifier.navigationBarsHeight(24.dp))
                }
            }
        }
    }

@Composable
fun NormalButton(icon: Painter, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .background(
                color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                shape = shape
            )
            .clip(shape)
            .clickable(onClick = onClick)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp
        )
    }
}

/**
 * 专辑页面的横向分割线
 */
@Composable
private fun AlbumDivider(modifier: Modifier = Modifier) =
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .alpha(0.2f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White)
    )

@Composable
private fun AlbumSongsItem(
    modifier: Modifier = Modifier,
    music: YosMediaItem,
    mainArtists: List<String>,
    onClick: () -> Unit
) {
    val isPlaying = MediaController.musicPlaying.value?.uri == music.uri
    val highlightColor = MaterialTheme.colorScheme.primary
    val normalColor = Color.Black withNight Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.TopCenter, modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        ) {
            Text(
                text = "${music.trackNumber?:"-"}",
                fontSize = 16.sp,
                modifier = Modifier.alpha(0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) highlightColor else normalColor
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text = music.title ?: defaultTitle,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = if (isPlaying) TextOverflow.Visible else TextOverflow.Ellipsis,
                modifier = if (isPlaying) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                lineHeight = 20.sp,
                color = if (isPlaying) highlightColor else normalColor
            )
            YosWrapper {
                val needShowArtists = remember(music) {
                    derivedStateOf {
                        !mainArtists.containsAll(music.artistsList ?: defaultArtists)
                    }
                }
                if (needShowArtists.value) {
                    Text(
                        text = music.artistsName ?: defaultArtistsName,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = if (isPlaying) TextOverflow.Visible else TextOverflow.Ellipsis,
                        modifier = Modifier
                            .alpha(0.4f)
                            .then(if (isPlaying) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                        lineHeight = 16.sp,
                        color = if (isPlaying) highlightColor.copy(alpha = 0.7f) else normalColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
        SongMenuIcon(music, showArtistMenuItem = false, showAlbumMenuItem = false)
    }
}