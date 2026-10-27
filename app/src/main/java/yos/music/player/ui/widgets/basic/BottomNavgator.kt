package yos.music.player.ui.widgets.basic

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width

@Stable
data class NavItem(val label: String, val iconResId: Int)

/** 横屏侧栏宽度 */
val SideNavigatorWidth = 132.dp

/** 底部 tab 栏向上扩展的透明触控高度：
 *  迷你播放条收起后与 tab 栏之间有一条 ~10px 的缝隙，两层都不消费触摸，
 *  点击会穿透命中列表内容。把 tab 的命中区域向上延伸同等范围，
 *  视觉不变（透明），只是让这层缝隙的点击落在 tab 切换上。 */
val TabTouchExtensionPx = 12.dp

/**
 * 横屏侧栏导航：与底栏同源的导航项竖排在左侧，
 * 视觉与底栏一致（30dp 图标 + 12sp Medium 文字，选中珊瑚红）。
 */
@Composable
fun SideNavigator(
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit,
    items: List<NavItem>,
    modifier: Modifier
) {
    Column(
        modifier
            .fillMaxHeight()
            .width(SideNavigatorWidth),
        verticalArrangement = Arrangement.Center
    ) {
        items.forEach { item ->
            SideNavigatorItem(item, nowLabel, onLabelChange)
        }
    }
}

@Composable
private fun SideNavigatorItem(
    item: NavItem,
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit
) =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
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
                .padding(start = 24.dp)
                .size(26.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            item.label,
            color = color.value,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            fontWeight = FontWeight.Medium
        )
    }

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
        // 触控扩展层：与上方 Row 等宽等分，点击等效于点击对应 tab。
        // 用负 offset 把自身移到 Box 边界之外：Box 视觉高度与内部布局完全不变，
        // 仅这条透明层向上探出，堵住迷你条与 tab 栏之间的穿透缝隙。
        // 后声明的兄弟位于轴测上层，可拦截触控；无任何绘制内容。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabTouchExtensionPx)
                .offset(y = -TabTouchExtensionPx)
        ) {
            items.forEach { item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onLabelChange(item.label)
                        }
                )
            }
        }
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
