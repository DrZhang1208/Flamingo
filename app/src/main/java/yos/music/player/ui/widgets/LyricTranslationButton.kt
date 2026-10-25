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
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import yos.music.player.R
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight

/** 歌词工具按钮共用的磨砂样式：透玻璃——实时模糊按钮背后的播放页背景，
 *  tint 只留一丝保图标可辨，不加噪点，玻璃本身不改变底色 */
@Composable
internal fun toolButtonHazeStyle(hazeState: HazeState) =
    Modifier.hazeChild(
        hazeState,
        HazeStyle(
            // haze 的 RenderEffect 路径要求垫底色非 Unspecified，否则首绘崩溃；
            // 必须透明：不透明垫底会把 blur 采样超界的小按钮填成直角实色块，圆角消失
            backgroundColor = Color.Transparent,
            blurRadius = 20.dp,
            tint = (Color.White withNight Color.Black).copy(alpha = 0.10f),
            noiseFactor = 0f
        )
    )

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
