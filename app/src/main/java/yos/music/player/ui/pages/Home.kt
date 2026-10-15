package yos.music.player.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
