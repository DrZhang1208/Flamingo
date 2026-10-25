package yos.music.player.ui.widgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.launch

@Composable
internal fun LandscapePlayerLayout(
    interactionEnabled: Boolean,
    onClose: () -> Unit,
    artwork: @Composable () -> Unit,
    controls: @Composable BoxScope.(visible: Boolean) -> Unit,
    lyrics: @Composable BoxScope.(visible: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // 收起不重置页码：重新展开回到退出时的页面（控制页/歌词页），与竖屏
    // nowPage 跨收起保留的行为一致
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = interactionEnabled) {
        if (pager.currentPage != 0 || pager.targetPage != 0) {
            scope.launch { pager.animateScrollToPage(0) }
        } else onClose()
    }
    Row(modifier) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            artwork()
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds(),
            userScrollEnabled = interactionEnabled
        ) { page ->
            val visible = interactionEnabled && (pager.currentPage == page || pager.targetPage == page)
            Box(Modifier.fillMaxSize()) {
                if (page == 0) controls(visible) else lyrics(visible)
            }
        }
    }
}
