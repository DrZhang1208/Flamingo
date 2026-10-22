package yos.music.player.ui.widgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

@Composable
internal fun TabletPlayerLayout(
    interactionEnabled: Boolean,
    onBack: () -> Unit,
    artwork: @Composable () -> Unit,
    controls: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = interactionEnabled, onBack = onBack)
    Row(modifier) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                artwork()
            }
            Box(Modifier.fillMaxWidth().clipToBounds()) {
                controls()
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            content()
        }
    }
}
