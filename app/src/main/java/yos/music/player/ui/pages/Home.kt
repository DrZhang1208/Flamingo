package yos.music.player.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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

@Composable
fun Home(
    navController: NavController
) {
    val listenStats = rememberListenStatsUiData()
    val songs = MusicLibrary.songs

    if (isLandscape()) {
        // 横屏垂直流（原版 Title 顶栏样式）：四宫格与听歌统计并排一行，
        // 下方依次是最近播放/最近专辑
        Title(
            title = stringResource(id = R.string.page_home_title),
            // 横屏设置入口在侧栏，右上角头像仅竖屏保留
            rightIcon = if (isLandscape()) null else CupertinoIcons.Default.PersonCropCircle,
            onRightIcon = {
                navController.toUI(UI.Settings.Main)
            },
            content = {
                if (songs.isNotEmpty() || (SettingsLibrary.ListenHistory && listenStats != null)) {
                    item("home_top_row") {
                        Row(Modifier.fillMaxWidth()) {
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
                }
                if (SettingsLibrary.ListenHistory && listenStats != null) {
                    if (listenStats.recentSongs.isNotEmpty()) {
                        item("home_recent_songs") {
                            HomeSectionEntrance(2) {
                                HomeRecentSongsSection(navController, listenStats)
                            }
                        }
                    }
                    if (listenStats.recentAlbums.isNotEmpty()) {
                        item("home_recent_albums") {
                            HomeSectionEntrance(3) {
                                HomeRecentAlbumsSection(navController, listenStats)
                            }
                        }
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
