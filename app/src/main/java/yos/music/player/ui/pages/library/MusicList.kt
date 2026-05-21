package yos.music.player.ui.pages.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImageWithCache
import yos.music.player.ui.widgets.basic.SongMenuIcon
import yos.music.player.ui.widgets.basic.songMenuTrigger

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicList(music: YosMediaItem, itemClick: () -> Unit) {
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
                modifier = Modifier.padding(bottom = 1.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp, lineHeight = 16.sp
            )
            Text(
                text = music.artistsName ?: defaultArtistsName,
                modifier = Modifier.alpha(0.5f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp, lineHeight = 13.sp
            )
        }
        SongMenuIcon(music)
    }
}