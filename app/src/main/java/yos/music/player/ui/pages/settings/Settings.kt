package yos.music.player.ui.pages.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.paneDivider

/** 设置分区：标题 + 该区内容（open 打开二级页：竖屏整页导航 / 横屏右栏渲染） */
private class SettingsSection(
    val key: String,
    val title: String,
    val content: @Composable (NavController, (String) -> Unit) -> Unit
)

@Composable
fun Settings(navController: NavController) =
    SettingBackground {
        val context = LocalContext.current

        val sections = listOf(
            SettingsSection("library", stringResource(id = R.string.page_library_title)) { nc, open ->
                SettingsLibrarySection(nc, context, open)
            },
            SettingsSection("remote", stringResource(id = R.string.settings_remote_title)) { nc, open ->
                RoundColumn {
                    LabelItem(title = stringResource(id = R.string.settings_remote_server_management)) {
                        open(UI.Settings.RemoteServerManagement)
                    }
                }
            },
            SettingsSection("performance", stringResource(id = R.string.settings_performance)) { nc, open ->
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
            },
            SettingsSection("audio", stringResource(id = R.string.settings_audio)) { nc, open ->
                Column {
                    RoundColumn {
                        LabelItem(title = stringResource(id = R.string.settings_audio_exoplayer)) {
                            open(UI.Settings.ExoplayerSetting)
                        }
                        Divider()
                        SwitchItem(
                            title = stringResource(id = R.string.settings_audio_fade_in_out),
                            onClick = { SettingsLibrary.FadePlay = !SettingsLibrary.FadePlay },
                            checkedLambda = { SettingsLibrary.FadePlay }
                        )
                    }
                    ListHeader(content = stringResource(id = R.string.settings_audio_fade_in_out_desc))
                }
            },
            SettingsSection("play", stringResource(id = R.string.settings_play)) { nc, open ->
                Column {
                    RoundColumn {
                        SwitchItem(
                            title = stringResource(id = R.string.settings_play_history),
                            onClick = { SettingsLibrary.ListenHistory = !SettingsLibrary.ListenHistory },
                            checkedLambda = { SettingsLibrary.ListenHistory }
                        )
                    }
                    ListHeader(content = stringResource(id = R.string.settings_play_history_desc))
                }
            },
            SettingsSection("extend", stringResource(id = R.string.settings_extend)) { nc, open ->
                RoundColumn {
                    LabelItem(
                        title = stringResource(id = R.string.settings_extend_statusbarlyric),
                    ) {
                        open(UI.Settings.LyricGetter)
                    }
                }
            },
            SettingsSection("others", stringResource(id = R.string.settings_others)) { nc, open ->
                RoundColumn {
                    LabelItem(
                        title = stringResource(id = R.string.settings_others_about),
                    ) {
                        open(UI.Settings.About)
                    }
                }
            }
        )

        val isLandscape = LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        Title(title = stringResource(id = R.string.page_settings_title),
            onBack = {
                navController.popBackStack()
            },
            content = {
                item("settings") {
                    if (isLandscape) {
                        // 横屏三段式：中列=与竖屏首页一致的分区列表，右列=二级页面
                        var selectedPage by remember { mutableStateOf<String?>(null) }
                        val open: (String) -> Unit = { selectedPage = it }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 中列：竖屏同款设置首页，可独立滚动
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                sections.forEachIndexed { index, section ->
                                    if (index > 0) GroupSpacer()
                                    ListHeader(section.title)
                                    section.content(navController, open)
                                }
                                Spacer(Modifier.height(20.dp))
                            }
                            // 右列：二级页面（返回仅清除右栏，不退出设置）；左缘外侧画分栏竖线
                            val onBackPage = { selectedPage = null }
                            Box(
                                Modifier
                                    .weight(1.3f)
                                    .fillMaxHeight()
                                    .paneDivider()
                            ) {
                                when (selectedPage) {
                                    UI.Settings.LibraryOverview ->
                                        yos.music.player.ui.pages.settings.library.LibraryOverview(navController, onBackPage)
                                    UI.Settings.RemoteServerManagement ->
                                        yos.music.player.ui.pages.settings.remote.RemoteServerManagement(navController, onBackPage)
                                    UI.Settings.LyricSetting ->
                                        yos.music.player.ui.pages.settings.performance.LyricSetting(navController, onBackPage)
                                    UI.Settings.UserInterfaceSetting ->
                                        yos.music.player.ui.pages.settings.performance.userinterface.UserInterfaceSetting(navController, onBackPage)
                                    UI.Settings.NotificationSetting ->
                                        yos.music.player.ui.pages.settings.performance.NotificationSetting(navController, onBackPage)
                                    UI.Settings.ExoplayerSetting ->
                                        yos.music.player.ui.pages.settings.audio.exoPlayer.ExoPlayerSettings(navController, onBackPage)
                                    UI.Settings.LyricGetter ->
                                        yos.music.player.ui.pages.settings.extend.statusBarLyric.LyricGetter(navController, onBackPage)
                                    UI.Settings.About ->
                                        yos.music.player.ui.pages.settings.others.About(navController, onBackPage)
                                    null -> Box(
                                        Modifier.fillMaxSize().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            stringResource(id = R.string.settings_pane_empty_hint),
                                            fontSize = 14.sp,
                                            color = (Color.Black withNight Color.White).copy(alpha = 0.35f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            val open: (String) -> Unit = { navController.toUI(it) }
                            sections.forEachIndexed { index, section ->
                                if (index > 0) GroupSpacer()
                                ListHeader(section.title)
                                section.content(navController, open)
                            }
                        }
                    }
                }
            })
    }

@Composable
private fun SettingsLibrarySection(navController: NavController, context: Context, open: (String) -> Unit) {
    Column {
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
