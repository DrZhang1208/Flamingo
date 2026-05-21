package yos.music.player.ui.pages.library.artists

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.SongMenuIcon
import yos.music.player.ui.widgets.basic.Title

@Composable
fun ArtistInfo(navController: NavController) {
    val artistName = rememberSaveable(key = "ArtistInfo_artistName") {
        mutableStateOf(LibraryObject.getTargetArtistName())
    }
    val songs = remember(artistName.value) {
        mutableStateOf(MusicLibrary.Artist[artistName.value])
    }
    val scope = rememberCoroutineScope()

    val (songCount, totalMinutes) = rememberSaveable(songs.value) {
        val totalDuration = songs.value.sumOf { it.duration }
        val totalMinutes = totalDuration / 60000
        val songCount = songs.value.size
        songCount to totalMinutes
    }

    Title(title = artistName.value, onBack = { navController.popBackStack() }) {
        item("ArtistHeader") {
            Column(
                Modifier.fillMaxWidth().padding(top = 9.5.dp).padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(songs.value.getOrNull(0)?.thumb).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(170.dp).clip(CircleShape)
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

        itemsIndexed(songs.value, key = { index, music -> "${index}_${music.uri}" }) { index, music ->
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
            Text("$songCount Songs, about $totalMinutes minutes", fontSize = 15.sp, modifier = Modifier.alpha(0.4f).padding(horizontal = 18.dp).padding(top = 18.dp))
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
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(music.title ?: defaultTitle, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
            music.artistsName?.let { name ->
                if (name != artistName) {
                    Text(name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp, modifier = Modifier.alpha(0.4f))
                }
            }
        }
        SongMenuIcon(music)
    }
}