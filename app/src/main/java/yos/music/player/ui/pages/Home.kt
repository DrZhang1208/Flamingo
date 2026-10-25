package yos.music.player.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.PersonCropCircle
import yos.music.player.R
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.UI
import yos.music.player.ui.theme.isLandscape
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.TitlePinned
import yos.music.player.ui.widgets.basic.pinnedHeaderHeight

@Composable
fun Home(
    navController: NavController
) {
    val listenStats = rememberListenStatsUiData()
    val songs = MusicLibrary.songs

    if (isLandscape()) {
        // 横屏单列垂直流：四宫格与听歌统计并排一行，下方依次是最近播放/最近专辑；
        // 区块原样复用，整页独立滚动
        TitlePinned(
            title = stringResource(id = R.string.page_home_title),
            // 横屏设置入口在侧栏，右上角头像仅竖屏保留
            rightIcon = if (isLandscape()) null else CupertinoIcons.Default.PersonCropCircle,
            onRightIcon = {
                navController.toUI(UI.Settings.Main)
            },
            content = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 内容贯穿到屏幕顶，首部让出玻璃标题栏
                    Spacer(modifier = Modifier.height(pinnedHeaderHeight()))
                    if (songs.isNotEmpty() || (SettingsLibrary.ListenHistory && listenStats != null)) {
                        Row {
                            if (songs.isNotEmpty()) {
                                Box(Modifier.weight(1.15f)) {
                                    HomeSectionEntrance(0) {
                                        HomeQuickEntries(navController, songs, listenStats)
                                    }
                                }
                            }
                            if (SettingsLibrary.ListenHistory && listenStats != null) {
                                Box(Modifier.weight(1f)) {
                                    HomeSectionEntrance(1) {
                                        HomeListenStatsCard(navController, listenStats)
                                    }
                                }
                            }
                        }
                    }
                    if (SettingsLibrary.ListenHistory && listenStats != null) {
                        if (listenStats.recentSongs.isNotEmpty()) {
                            HomeSectionEntrance(2) {
                                HomeRecentSongsSection(navController, listenStats)
                            }
                        }
                        if (listenStats.recentAlbums.isNotEmpty()) {
                            HomeSectionEntrance(3) {
                                HomeRecentAlbumsSection(navController, listenStats)
                            }
                        }
                    }
                    Spacer(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .height(134.dp)
                    )
                }
            }
        )
        return
    }

    Title(
        title = stringResource(id = R.string.page_home_title),
        rightIcon = CupertinoIcons.Default.PersonCropCircle,
        onRightIcon = {
            navController.toUI(UI.Settings.Main)
        },
        content = {
            if (songs.isNotEmpty()) {
                item("HomeQuickEntries") {
                    HomeSectionEntrance(0) {
                        HomeQuickEntries(navController, songs, listenStats)
                    }
                }
            }
            if (SettingsLibrary.ListenHistory && listenStats != null) {
                item("HomeListenStats") {
                    HomeSectionEntrance(1) {
                        HomeListenStatsCard(navController, listenStats)
                    }
                }
                if (listenStats.recentSongs.isNotEmpty()) {
                    item("HomeRecentSongs") {
                        HomeSectionEntrance(2) {
                            HomeRecentSongsSection(navController, listenStats)
                        }
                    }
                }
                if (listenStats.recentAlbums.isNotEmpty()) {
                    item("HomeRecentAlbums") {
                        HomeSectionEntrance(3) {
                            HomeRecentAlbumsSection(navController, listenStats)
                        }
                    }
                }
            }
        })
}
