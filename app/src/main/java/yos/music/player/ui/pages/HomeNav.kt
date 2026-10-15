package yos.music.player.ui.pages

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import yos.music.player.R
import yos.music.player.ui.pages.library.Library
import yos.music.player.ui.widgets.basic.YosWrapper

/*@Stable
object HomePage {
    const val Home = "主页"
    const val Library = "资料库"
}*/

@Composable
fun HomeNav(
    navController: NavController,
    pagerState: PagerState,
    nowPageOnChanged: (String) -> Unit
) =
    YosWrapper {
        val context = LocalContext.current
        val home = context.getString(R.string.page_home_title)
        val library = context.getString(R.string.page_library_title)

        //val pagerState = rememberPagerState(pageCount = { 2 })
        /*val nowPageIndex = when (nowPage.value) {
            home -> 0
            library -> 1
            else -> 0
        }

        YosWrapper {
            LaunchedEffect(nowPageIndex) {
                pagerState.animateScrollToPage(nowPageIndex)
            }
        }*/

        YosWrapper {
            LaunchedEffect(pagerState) {
                nowPageOnChanged(
                    when (pagerState.currentPage) {
                    0 -> home
                    1 -> library
                    else -> home
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            key = { page -> page },
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> Home(navController)
                1 -> Library(navController)
            }
        }
    }
