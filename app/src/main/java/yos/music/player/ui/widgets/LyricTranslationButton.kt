package yos.music.player.ui.widgets

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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import yos.music.player.R
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.isFlamingoInDarkMode
import yos.music.player.ui.theme.withNight

/** 歌词工具按钮共用的磨砂样式：实时模糊按钮背后的播放页背景，昼夜玻璃雾面 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun toolButtonHazeStyle(hazeState: HazeState) =
    HazeMaterials
        .thin(Color.White withNight Color.Black)
        .copy(
            blurRadius = 18.dp,
            tint = (Color.White withNight Color.Black).copy(
                alpha = if (isFlamingoInDarkMode()) 0.45f else 0.30f
            )
        )
        .let { Modifier.hazeChild(hazeState, it) }

@Composable
internal fun LyricTranslationButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // 播放页背景的 haze 源：按钮实时模糊其背后的背景
    hazeState: HazeState
) {
    val backgroundAlpha = animateFloatAsState(if (checked) 0.92f else 0f, label = "TranslationBackground")
    val tint = animateColorAsState(if (checked) Color.Black else Color.White, label = "TranslationTint")
    val shape = remember { YosRoundedCornerShape(10.dp) }
    Box(modifier
        .size(34.dp)
        .clip(shape)
        .then(toolButtonHazeStyle(hazeState))
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
