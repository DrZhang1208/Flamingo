package yos.music.player.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import yos.music.player.ui.widgets.basic.PaneDivider
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.TitlePinned

@Composable
fun Home(
    navController: NavController
) {
    val listenStats = rememberListenStatsUiData()
    val songs = MusicLibrary.songs

    if (isLandscape()) {
        // 横屏双栏：入口卡+听歌统计在左，最近播放/专辑在右；区块原样复用、各栏独立滚动
        TitlePinned(
            title = stringResource(id = R.string.page_home_title),
            rightIcon = CupertinoIcons.Default.PersonCropCircle,
            onRightIcon = {
                navController.toUI(UI.Settings.Main)
            },
            content = {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1.35f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(end = 16.dp)
                    ) {
                        if (songs.isNotEmpty()) {
                            HomeSectionEntrance(0) {
                                HomeQuickEntries(navController, songs, listenStats)
                            }
                        }
                        if (SettingsLibrary.ListenHistory && listenStats != null) {
                            HomeSectionEntrance(1) {
                                HomeListenStatsCard(navController, listenStats)
                            }
                        }
                        Spacer(
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .height(134.dp)
                        )
                    }
                    PaneDivider()
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp)
                    ) {
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
