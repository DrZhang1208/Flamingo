package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yos.music.player.R
import yos.music.player.ui.theme.YosRoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySortDialog(
    options: List<String>, selected: Int, descending: Boolean,
    onSelect: (Int, Boolean) -> Unit, onDismiss: () -> Unit
) {
    OptionDialog(icon = {}, title = stringResource(R.string.library_sort_title), content = { dismiss ->
        val shape = YosRoundedCornerShape(9.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.onSecondary)) {
            options.forEachIndexed { index, label ->
                if (index > 0) Spacer(Modifier.fillMaxWidth().height(0.5.dp)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)))
                Row(Modifier.fillMaxWidth().height(48.dp).clickable { onSelect(index, if (index == selected) !descending else descending); dismiss() }
                    .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f),
                        color = if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
                    if (index == selected) Icon(
                        if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(if (descending) R.string.library_sort_descending else R.string.library_sort_ascending),
                        modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

    }, onDismissRequest = onDismiss)
}
