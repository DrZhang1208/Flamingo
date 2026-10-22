package yos.music.player.ui.widgets

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yos.music.player.R
import yos.music.player.code.PlaybackAudioOutput
import yos.music.player.code.PlaybackOutputDevice
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.widgets.basic.OptionDialog

@Composable
internal fun rememberPlaybackAudioOutput(): PlaybackAudioOutput {
    val context = LocalContext.current
    val output = remember(context) { PlaybackAudioOutput(context) }
    DisposableEffect(output) {
        output.start()
        onDispose { output.stop() }
    }
    return output
}

@Composable
internal fun PlaybackOutputButton(
    deviceName: String, externalOutput: Boolean, onClick: () -> Unit,
    compactWindow: Boolean = false, modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.playback_output_title)
    Column(modifier.then(if (compactWindow) Modifier.height(36.dp) else
        Modifier.heightIn(min = 53.dp))
        .clickable(remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(externalOutput, transitionSpec = {
                (scaleIn(initialScale = 0.3f) + fadeIn()).togetherWith(
                    scaleOut(targetScale = 0.3f) + fadeOut())
            }, label = "output device", contentAlignment = Alignment.Center) { external ->
                Icon(painterResource(if (external) R.drawable.ic_earphone else R.drawable.ic_nowplaying_airplay),
                    contentDescription = label,
                    modifier = Modifier.size(if (external) 27.dp else 21.5.dp))
            }
        }
        AnimatedVisibility(externalOutput && !compactWindow,
            enter = scaleIn(initialScale = 0.3f) + fadeIn(),
            exit = scaleOut(targetScale = 0.3f) + fadeOut()) {
            Text(deviceName, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackOutputDialog(devices: List<PlaybackOutputDevice>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    OptionDialog(icon = {}, title = stringResource(R.string.playback_output_title), content = { dismiss ->
        Column(Modifier.fillMaxWidth().clip(YosRoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.onSecondary)) {
            if (devices.isEmpty()) Text(stringResource(R.string.playback_output_empty), Modifier.padding(16.dp))
            devices.forEach { device ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .clickable(enabled = device.enabled, role = Role.RadioButton) {
                        if (device.selected || device.select()) dismiss()
                        else Toast.makeText(context, R.string.playback_output_unavailable, Toast.LENGTH_SHORT).show()
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name, Modifier.weight(1f), fontSize = 16.sp,
                        color = if (device.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
                    if (device.selected) Icon(Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.playback_output_selected),
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }, onDismissRequest = onDismiss)
}
