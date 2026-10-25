package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import yos.music.player.ui.theme.withNight

/**
 * 横屏分栏之间的竖向分割线，颜色与竖屏列表分割线同源。
 */
@Composable
fun PaneDivider(modifier: Modifier = Modifier) =
    Box(
        modifier
            .width(1.dp)
            .fillMaxHeight()
            .background((Color.Black withNight Color.White).copy(alpha = 0.12f))
    )
