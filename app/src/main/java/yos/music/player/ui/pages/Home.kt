package yos.music.player.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.PersonCropCircle
import yos.music.player.R
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.Title

@Composable
fun Home(
    navController: NavController
) {
    val listenStats = rememberListenStatsUiData()
    val songs = MusicLibrary.songs
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Title(
        title = stringResource(id = R.string.page_home_title),
        rightIcon = CupertinoIcons.Default.PersonCropCircle,
        onRightIcon = {
            navController.toUI(UI.Settings.Main)
        },
        content = {
            // 横屏双栏：入口渐变卡与在听时段收左栏，最近播放（歌/专辑）提为右栏
            if (isLandscape) {
                item("HomeLandscape") {
                    HomeSectionEntrance(0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(Modifier.weight(1.35f)) {
                                if (songs.isNotEmpty()) {
                                    HomeQuickEntries(navController, songs, listenStats)
                                }
                                if (SettingsLibrary.ListenHistory && listenStats != null) {
                                    HomeListenStatsCard(navController, listenStats)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                if (SettingsLibrary.ListenHistory && listenStats != null) {
                                    if (listenStats.recentSongs.isNotEmpty()) {
                                        HomeRecentSongsSection(navController, listenStats)
                                    }
                                    if (listenStats.recentAlbums.isNotEmpty()) {
                                        HomeRecentAlbumsSection(navController, listenStats)
                                    }
                                }
                            }
                        }
                    }
                }
                return@Title
            }
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
