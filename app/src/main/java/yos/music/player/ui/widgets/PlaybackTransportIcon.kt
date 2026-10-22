package yos.music.player.ui.widgets

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import yos.music.player.R

@Composable
internal fun PlaybackTransportIcon(playing: Boolean, buffering: Boolean) {
    if (buffering) {
        CircularProgressIndicator(
            modifier = Modifier.size(34.dp).semantics { contentDescription = "Buffering" },
            color = Color.White, strokeWidth = 3.dp
        )
    } else {
        AnimatedContent(targetState = playing, transitionSpec = {
            (scaleIn(initialScale = 0.3f) + fadeIn()).togetherWith(
                scaleOut(targetScale = 0.3f) + fadeOut()
            )
        }, label = "PlaybackTransport") { active ->
            Icon(
                painterResource(if (active) R.drawable.ic_nowplaying_pause else R.drawable.ic_nowplaying_play),
                contentDescription = if (active) "Pause" else "Play",
                modifier = Modifier.fillMaxSize().padding(if (active) 10.dp else 9.dp)
            )
        }
    }
}
