package yos.music.player.ui.pages.library.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.accompanist.insets.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.github.promeg.pinyinhelper.Pinyin
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.SongMenuIcon
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.Title

@Composable
fun ArtistInfo(navController: NavController) {
    val artistName = rememberSaveable(key = "ArtistInfo_artistName") {
        mutableStateOf(LibraryObject.getTargetArtistName())
    }
    val songs = remember(artistName.value) {
        mutableStateOf(MusicLibrary.Artist[artistName.value].sortedWith(
            compareBy { song -> (song.title ?: defaultTitle).map { Pinyin.toPinyin(it) }.joinToString("") }
        ))
    }
    val scope = rememberCoroutineScope()

    val albums = remember(songs.value) {
        songs.value.groupBy { it.album }.mapNotNull { (album, albumSongs) ->
            if (album == null) return@mapNotNull null
            val year = MusicLibrary.albumYear(album)
            album to year
        }.sortedByDescending { it.second ?: 0 }.map { it.first }
    }

    val (songCount, totalMinutes) = rememberSaveable(songs.value) {
        val totalDuration = songs.value.sumOf { it.duration }
        val totalMinutes = totalDuration / 60000
        val songCount = songs.value.size
        songCount to totalMinutes
    }

    Title(title = artistName.value, onBack = { navController.popBackStack() }, showLargeTitle = false) {
        item("ArtistHeader") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 9.5.dp)
                    .padding(horizontal = 18.dp)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .drawBehind {
                            val blur = 24.dp.toPx()
                            val paint = Paint().apply {
                                color = Color.Black.copy(alpha = 0.25f)
                                asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
                            }
                            val inset = blur / 2f
                            drawContext.canvas.drawCircle(center, size.minDimension / 2f - inset, paint)
                        }
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(songs.value.maxByOrNull { MusicLibrary.getPlayCount(it.uri) }?.thumb)
                            .crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = artistName.value,
                    fontSize = 17.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 23.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 15.dp)) {
                    NormalButton(painterResource(id = R.drawable.button_icon_play), stringResource(id = R.string.normal_button_play), Modifier.weight(1f)) {
                        scope.launch(Dispatchers.IO) { MediaController.prepare(songs.value.first(), songs.value) }
                    }
                    Spacer(Modifier.width(15.dp))
                    NormalButton(painterResource(id = R.drawable.button_icon_shuffle), stringResource(id = R.string.normal_button_shuffle), Modifier.weight(1f)) {
                        scope.launch(Dispatchers.IO) { MediaController.prepare(songs.value.random(), songs.value, shuffleModeEnabled = true) }
                    }
                }
            }
        }

        item { ArtistDivider() }

        itemsIndexed(songs.value, key = { index, music -> music.uri?.toString() ?: music.mediaId ?: "artist_song_$index" }) { index, music ->
            key(music) {
                ArtistSongsItem(music, artistName.value) {
                    scope.launch(Dispatchers.IO) { MediaController.prepare(music, songs.value) }
                }
            }
            key(index) {
                if (index < songs.value.size - 1) {
                    Spacer(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp).alpha(0.25f).height(0.5.dp).background(Color.Black withNight Color.White))
                }
            }
        }

        item { ArtistDivider() }

        item("ArtistInfo_others") {
            Text("$songCount 首歌曲，约 $totalMinutes 分钟", fontSize = 15.sp, modifier = Modifier.alpha(0.4f).padding(horizontal = 18.dp).padding(top = 18.dp))
        }

        // 参与专辑
        if (albums.isNotEmpty()) {
            item("ArtistAlbums_header") {
                Spacer(Modifier.height(24.dp))
                ArtistDivider()
                Text("专辑", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 18.dp).padding(top = 16.dp, bottom = 8.dp))
            }
            itemsIndexed(albums, key = { _, album -> album }) { _, album ->
                val albumSongs = songs.value.filter { it.album == album }
                val albumCover = albumSongs.firstOrNull()?.thumb
                val year = MusicLibrary.albumYear(album)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        yos.music.player.data.objects.LibraryObject.setTargetAlbumName(album)
                        navController.toUI(UI.AlbumInfo)
                    }.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(albumCover).size(96).build(),
                        contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(5.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(album, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val info = buildString {
                            append("${albumSongs.size} 首")
                            if (year != null) append(" · $year")
                        }
                        Text(info, fontSize = 12.sp, modifier = Modifier.alpha(0.4f).padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NormalButton(icon: Painter, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.background((Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f), shape).clip(shape).clickable(onClick = onClick).height(44.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    }
}

@Composable
private fun ArtistDivider() = Spacer(Modifier.fillMaxWidth().padding(horizontal = 18.dp).alpha(0.2f).height(0.5.dp).background(Color.Black withNight Color.White))

@Composable
private fun ArtistSongsItem(music: YosMediaItem, artistName: String, onClick: () -> Unit) {
    val isPlaying = MediaController.musicPlaying.value?.uri == music.uri
    val highlightColor = MaterialTheme.colorScheme.primary
    val normalColor = Color.Black withNight Color.White

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(music.thumb).size(80).build(),
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                music.title ?: defaultTitle, fontSize = 16.sp, maxLines = 1,
                overflow = if (isPlaying) TextOverflow.Visible else TextOverflow.Ellipsis,
                modifier = if (isPlaying) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                lineHeight = 20.sp, color = if (isPlaying) highlightColor else normalColor
            )
            val subtitle = buildString {
                val hasArtist = music.artistsName?.let { it != artistName } == true
                if (hasArtist) append(music.artistsName!!)
                music.album?.let { if (hasArtist) append(" · $it") else append(it) }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle, fontSize = 11.sp, maxLines = 1,
                    overflow = if (isPlaying) TextOverflow.Visible else TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(0.4f).then(if (isPlaying) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                    lineHeight = 16.sp, color = if (isPlaying) highlightColor.copy(alpha = 0.7f) else normalColor.copy(alpha = 0.7f)
                )
            }
        }
        SongMenuIcon(music, showArtistMenuItem = false, showAlbumMenuItem = false)
    }
}
