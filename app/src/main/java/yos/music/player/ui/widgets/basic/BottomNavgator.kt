package yos.music.player.ui.widgets.basic

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues

@Stable
data class NavItem(val label: String, val iconResId: Int)

@Composable
fun BottomNavigator(
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit,
    items: List<NavItem>,
    modifier: Modifier
) {
    // 显式求和：windowInsetsBottomHeight 总高=inset 本身（手势导航下仅几十像素，内容被压没）
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier
            .fillMaxWidth()
            .height(navBottom + 64.dp)
        //.background(Color.White withNight Color.Black)
    ) {
        /*Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.08f)
                .height(1.6.dp)
                .background(Color.Black withNight Color.White)
        )*/
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEach { item ->
                NavigatorItem(item, nowLabel, onLabelChange)
            }
        }
    }
}

/** 横屏左侧边栏：导航项横排（图标左、文字右），等价于底部 tab 栏的侧栏形态 */
@Composable
fun SideNavigator(
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit,
    items: List<NavItem>,
    modifier: Modifier = Modifier
) = Column(
    modifier
        .fillMaxHeight()
        .width(132.dp),
    verticalArrangement = Arrangement.Center
) {
    items.forEach { item ->
        SideNavigatorItem(item, nowLabel, onLabelChange)
    }
}

@Composable
private fun SideNavigatorItem(
    item: NavItem,
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit
) {
    val isSelected = remember(item) {
        derivedStateOf {
            nowLabel() == item.label
        }
    }
    val color = animateColorAsState(if (isSelected.value) MaterialTheme.colorScheme.primary else Color.Gray)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onLabelChange(item.label)
            }
            .padding(start = 24.dp, end = 12.dp, top = 13.dp, bottom = 13.dp)
    ) {
        Icon(
            painterResource(item.iconResId),
            contentDescription = null,
            tint = color.value,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            item.label,
            color = color.value,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RowScope.NavigatorItem(
    item: NavItem,
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit
) =
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxHeight()
            .weight(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = /*ripple(bounded = true, radius = 68.dp)*/ null
            ) {
                onLabelChange(item.label)
            }
    ) {
        val isSelected = remember(item) {
            derivedStateOf {
                nowLabel() == item.label
            }
        }
        val color = animateColorAsState(if (isSelected.value) MaterialTheme.colorScheme.primary else Color.Gray)

        Icon(
            painterResource(item.iconResId),
            contentDescription = null,
            tint = color.value,
            modifier = Modifier
                .size(30.dp)
                .padding(bottom = 0.dp)
        )
        Text(
            item.label,
            color = color.value,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
