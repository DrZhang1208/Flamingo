package yos.music.player.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import yos.music.player.R
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.widgets.effects.overlayEffect

@Composable
internal fun LyricTranslationButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundAlpha = animateFloatAsState(if (checked) 0.92f else 0f, label = "TranslationBackground")
    val tint = animateColorAsState(if (checked) Color.Black else Color.White, label = "TranslationTint")
    val shape = remember { YosRoundedCornerShape(10.dp) }
    Box(modifier.size(34.dp).overlayEffect().clip(shape)
        .background(Color.White.copy(alpha = 0.10f))
        .drawBehind { drawRect(Color.White.copy(alpha = backgroundAlpha.value)) }
        .toggleable(checked, interactionSource = remember { MutableInteractionSource() },
            indication = null, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Translate,
                contentDescription = stringResource(if (checked) R.string.lyric_translation_hide else R.string.lyric_translation_show),
                tint = tint.value,
                modifier = Modifier.size(22.dp))
    }
}
