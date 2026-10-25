package yos.music.player.ui.pages.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Text
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.UI
import yos.music.player.ui.pages.settings.audio.exoPlayer.ExoPlayerSettings
import yos.music.player.ui.pages.settings.extend.statusBarLyric.LyricGetter
import yos.music.player.ui.pages.settings.library.LibraryOverview
import yos.music.player.ui.pages.settings.others.About
import yos.music.player.ui.pages.settings.performance.LyricSetting
import yos.music.player.ui.pages.settings.performance.NotificationSetting
import yos.music.player.ui.pages.settings.performance.userinterface.UserInterfaceSetting
import yos.music.player.ui.pages.settings.remote.RemoteServerManagement
import yos.music.player.ui.theme.isLandscape
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.PaneDivider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import yos.music.player.ui.widgets.basic.GlassHeaderBar
import yos.music.player.ui.widgets.basic.pinnedHeaderHeight
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.TitlePinned

@Composable
fun Settings(navController: NavController) =
    SettingBackground {
        val landscape = isLandscape()
        // 横屏右栏当前子页（路由名）；竖屏不使用
        var selectedPage by rememberSaveable { mutableStateOf<String?>(null) }

        if (landscape) {
            // 分栏：各栏从屏幕顶贯穿、独立滚动；左栏顶部为玻璃标题，
            // 右栏子页自带标题从顶部开始；竖线全高（含标题区）
            Row(Modifier.fillMaxSize()) {
                val headerHazeState = remember { HazeState() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // 左列 = 竖屏分组列表原样，点菜单行在右栏打开子页
                    Box(
                        Modifier
                            .fillMaxSize()
                            .haze(headerHazeState)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(modifier = Modifier.height(pinnedHeaderHeight()))
                            SettingsMenu(open = { selectedPage = it })
                            Spacer(
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .height(134.dp)
                            )
                        }
                    }
                    GlassHeaderBar(
                        title = stringResource(id = R.string.page_settings_title),
                        onBack = { navController.popBackStack() },
                        hazeState = headerHazeState
                    )
                }
                PaneDivider()
                Box(
                    Modifier
                        .weight(1.6f)
                        .fillMaxHeight()
                ) {
                    // 右栏顶部同款玻璃遮罩（无标题），子页滚过时与左栏一致地渐进模糊
                    val rightHazeState = remember { HazeState() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .haze(rightHazeState)
                            .padding(start = 16.dp)
                    ) {
                        val page = selectedPage
                        if (page == null) {
                            Text(
                                text = stringResource(id = R.string.settings_pane_empty_hint),
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .alpha(0.4f)
                            )
                        } else {
                            key(page) {
                                val closePane: () -> Unit = { selectedPage = null }
                                when (page) {
                                    UI.Settings.LibraryOverview -> LibraryOverview(navController, onBack = closePane)
                                    UI.Settings.RemoteServerManagement -> RemoteServerManagement(navController, onBack = closePane)
                                    UI.Settings.LyricSetting -> LyricSetting(navController, onBack = closePane)
                                    UI.Settings.UserInterfaceSetting -> UserInterfaceSetting(navController, onBack = closePane)
                                    UI.Settings.NotificationSetting -> NotificationSetting(navController, onBack = closePane)
                                    UI.Settings.ExoplayerSetting -> ExoPlayerSettings(navController, onBack = closePane)
                                    UI.Settings.LyricGetter -> LyricGetter(navController, onBack = closePane)
                                    UI.Settings.About -> About(navController, onBack = closePane)
                                }
                            }
                        }
                    }
                    GlassHeaderBar(
                        title = "",
                        hazeState = rightHazeState
                    )
                }
            }
        } else {
            Title(title = stringResource(id = R.string.page_settings_title),
                onBack = {
                    navController.popBackStack()
                },
                content = {
                    item("settings") {
                        Column(Modifier.fillMaxSize()) {
                            SettingsMenu(open = { navController.toUI(it) })
                        }
                    }
                })
        }
    }

/**
 * 设置首页的分组列表，竖屏单列直排；横屏作为分栏左列。
 * open：打开子页（竖屏 push 路由 / 横屏置右栏）。
 */
@Composable
private fun SettingsMenu(open: (String) -> Unit) {
    val context = LocalContext.current

    ListHeader(stringResource(id = R.string.page_library_title))
    RoundColumn {
        SwitchItem(
            title = stringResource(id = R.string.settings_library_refresh_everytime),
            onClick = {
                SettingsLibrary.RefreshEveryTime =
                    !SettingsLibrary.RefreshEveryTime
            },
            checkedLambda = { SettingsLibrary.RefreshEveryTime }
        )

        Divider()
        LabelItem(title = stringResource(id = R.string.settings_library_overview)) {
            open(UI.Settings.LibraryOverview)
        }
    }

    GroupSpacerMedium()
    RoundColumn {
        val scope = rememberCoroutineScope()
        LabelItem(
            title = stringResource(id = R.string.settings_library_refresh_now),
            //desc = stringResource(id = R.string.settings_library_refresh_now_desc)
            superLink = true
        ) {
            scope.launch(Dispatchers.Main) {
                var toast = Toast.makeText(
                    context,
                    R.string.tip_scanning,
                    Toast.LENGTH_SHORT
                )
                toast.show()
                withContext(Dispatchers.IO) {
                    MusicLibrary.scanMedia(context)
                }
                toast.cancel()
                val size = MediaController.mainMusicList.size
                if (size == 0) {
                    toast = Toast.makeText(
                        context,
                        R.string.tip_no_song,
                        Toast.LENGTH_SHORT
                    )
                } else {
                    val msg =
                        context.getString(R.string.tip_scan_finished, size)
                    toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                }
                toast.show()
            }
        }
    }
    ListHeader(content = stringResource(id = R.string.settings_library_refresh_now_desc))

    GroupSpacer()
    ListHeader(stringResource(id = R.string.settings_remote_title))
    RoundColumn {
        LabelItem(title = stringResource(id = R.string.settings_remote_server_management)) {
            open(UI.Settings.RemoteServerManagement)
        }
    }

    GroupSpacer()
    ListHeader(stringResource(id = R.string.settings_performance))
    RoundColumn {
        LabelItem(title = stringResource(id = R.string.settings_performance_lyric_title)) {
            open(UI.Settings.LyricSetting)
        }
        Divider()
        LabelItem(title = stringResource(id = R.string.settings_performance_ui_title)) {
            open(UI.Settings.UserInterfaceSetting)
        }
        Divider()
        LabelItem(title = stringResource(id = R.string.settings_performance_notification_title)) {
            open(UI.Settings.NotificationSetting)
        }
    }

    GroupSpacer()
    ListHeader(stringResource(id = R.string.settings_audio))
    RoundColumn {
        LabelItem(title = stringResource(id = R.string.settings_audio_exoplayer)) {
            open(UI.Settings.ExoplayerSetting)
        }
        Divider()
        SwitchItem(
            title = stringResource(id = R.string.settings_audio_fade_in_out),
            // desc = stringResource(id = R.string.settings_audio_fade_in_out_desc),
            onClick = { SettingsLibrary.FadePlay = !SettingsLibrary.FadePlay },
            checkedLambda = { SettingsLibrary.FadePlay }
        )
    }
    ListHeader(content = stringResource(id = R.string.settings_audio_fade_in_out_desc))

    GroupSpacer()
    ListHeader(stringResource(id = R.string.settings_play))
    RoundColumn {
        SwitchItem(
            title = stringResource(id = R.string.settings_play_history),
            // desc = stringResource(id = R.string.settings_play_history_desc),
            onClick = { SettingsLibrary.ListenHistory = !SettingsLibrary.ListenHistory },
            checkedLambda = { SettingsLibrary.ListenHistory }
        )
    }
    ListHeader(content = stringResource(id = R.string.settings_play_history_desc))

    GroupSpacer()
    ListHeader(stringResource(id = R.string.settings_extend))
    RoundColumn {
        LabelItem(
            title = stringResource(id = R.string.settings_extend_statusbarlyric),
            // desc = stringResource(id = R.string.settings_extend_statusbarlyric_desc),
        ) {
            open(UI.Settings.LyricGetter)
        }
    }

    GroupSpacer()
    ListHeader(stringResource(id = R.string.settings_others))
    RoundColumn {
        LabelItem(
            title = stringResource(id = R.string.settings_others_about),
            // desc = stringResource(id = R.string.settings_others_about_desc)
        ) {
            open(UI.Settings.About)
        }
    }
}


fun safeStartActivity(context: Context, intent: Intent, options: Bundle?) {
    if (intent.resolveActivity(context.packageManager) != null) {
        ContextCompat.startActivity(context, intent, options)
    } else {
        Toast.makeText(
            context,
            context.getString(R.string.tip_intent_resolve_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun startWeb(url: String, context: Context) {
    try {
        val uri: Uri =
            Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        safeStartActivity(context, intent, null)
    } catch (_: Exception) {
    }
}
