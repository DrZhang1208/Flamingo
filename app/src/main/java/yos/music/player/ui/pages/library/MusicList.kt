package yos.music.player.ui.pages.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImageWithCache
import yos.music.player.ui.widgets.basic.SongMenuIcon
import yos.music.player.ui.widgets.basic.songMenuTrigger
import yos.music.player.ui.theme.withNight

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicList(music: YosMediaItem, navController: NavController? = null, itemClick: () -> Unit) {
    val isPlaying = MediaController.musicPlaying.value?.uri == music.uri
    val highlightColor = MaterialTheme.colorScheme.primary
    val normalColor = Color.Black withNight Color.White

    Row(
        modifier = Modifier
            .height(64.dp).fillMaxWidth()
            .songMenuTrigger(onClick = itemClick, onLongClick = {})
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShadowImageWithCache(
            dataLambda = { music.thumb }, contentDescription = null,
            modifier = Modifier.size(52.dp), cornerRadius = 3.5.dp,
            shadowAlpha = 0f, imageQuality = ImageQuality.LOW
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = music.title ?: defaultTitle,
                modifier = Modifier
                    .padding(bottom = 1.dp)
                    .then(if (isPlaying) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                maxLines = 1,
                overflow = if (isPlaying) TextOverflow.Visible else TextOverflow.Ellipsis,
                fontSize = 16.sp, lineHeight = 16.sp,
                color = if (isPlaying) highlightColor else normalColor
            )
            Text(
                text = buildString {
                    append(music.artistsName ?: defaultArtistsName)
                    music.album?.let { append(" · $it") }
                },
                modifier = Modifier
                    .alpha(0.5f)
                    .then(if (isPlaying) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                maxLines = 1,
                overflow = if (isPlaying) TextOverflow.Visible else TextOverflow.Ellipsis,
                fontSize = 13.sp, lineHeight = 13.sp,
                color = if (isPlaying) highlightColor.copy(alpha = 0.7f) else normalColor
            )
        }
        SongMenuIcon(music, navController)
    }
}