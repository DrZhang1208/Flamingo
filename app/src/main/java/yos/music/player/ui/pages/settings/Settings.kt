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

/** 设置分区：标题 + 该区内容（竖屏顺序拼排 / 横屏右栏按选中渲染） */
private class SettingsSection(
    val key: String,
    val title: String,
    val content: @Composable (NavController) -> Unit
)

@Composable
fun Settings(navController: NavController) =
    SettingBackground {
        val context = LocalContext.current

        val sections = listOf(
            SettingsSection("library", stringResource(id = R.string.page_library_title)) { nc ->
                SettingsLibrarySection(nc, context)
            },
            SettingsSection("remote", stringResource(id = R.string.settings_remote_title)) { nc ->
                RoundColumn {
                    LabelItem(title = stringResource(id = R.string.settings_remote_server_management)) {
                        nc.toUI(UI.Settings.RemoteServerManagement)
                    }
                }
            },
            SettingsSection("performance", stringResource(id = R.string.settings_performance)) { nc ->
                RoundColumn {
                    LabelItem(title = stringResource(id = R.string.settings_performance_lyric_title)) {
                        nc.toUI(UI.Settings.LyricSetting)
                    }
                    Divider()
                    LabelItem(title = stringResource(id = R.string.settings_performance_ui_title)) {
                        nc.toUI(UI.Settings.UserInterfaceSetting)
                    }
                    Divider()
                    LabelItem(title = stringResource(id = R.string.settings_performance_notification_title)) {
                        nc.toUI(UI.Settings.NotificationSetting)
                    }
                }
            },
            SettingsSection("audio", stringResource(id = R.string.settings_audio)) { nc ->
                Column {
                    RoundColumn {
                        LabelItem(title = stringResource(id = R.string.settings_audio_exoplayer)) {
                            nc.toUI(UI.Settings.ExoplayerSetting)
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
            SettingsSection("play", stringResource(id = R.string.settings_play)) {
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
            SettingsSection("extend", stringResource(id = R.string.settings_extend)) { nc ->
                RoundColumn {
                    LabelItem(
                        title = stringResource(id = R.string.settings_extend_statusbarlyric),
                    ) {
                        nc.toUI(UI.Settings.LyricGetter)
                    }
                }
            },
            SettingsSection("others", stringResource(id = R.string.settings_others)) { nc ->
                RoundColumn {
                    LabelItem(
                        title = stringResource(id = R.string.settings_others_about),
                    ) {
                        nc.toUI(UI.Settings.About)
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
                        // 横屏分栏：左分类菜单，右所选分区内容
                        var selectedKey by remember { mutableStateOf(sections.first().key) }
                        Row(
                            Modifier.fillMaxWidth().fillParentMaxHeight(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            RoundColumn(Modifier.weight(0.9f).fillMaxHeight()) {
                                sections.forEachIndexed { index, section ->
                                    if (index > 0) Divider()
                                    SettingsMenuRow(
                                        title = section.title,
                                        selected = selectedKey == section.key
                                    ) { selectedKey = section.key }
                                }
                            }
                            Column(
                                Modifier
                                    .weight(2.1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                sections.firstOrNull { it.key == selectedKey }?.let { section ->
                                    ListHeader(section.title)
                                    section.content(navController)
                                }
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            sections.forEachIndexed { index, section ->
                                if (index > 0) GroupSpacer()
                                ListHeader(section.title)
                                section.content(navController)
                            }
                        }
                    }
                }
            })
    }

@Composable
private fun SettingsLibrarySection(navController: NavController, context: Context) {
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
                navController.toUI(UI.Settings.LibraryOverview)
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

/** 横屏设置菜单行：与 LabelItem 同风格，选中态珊瑚红浅底 */
@Composable
private fun SettingsMenuRow(title: String, selected: Boolean, onClick: () -> Unit) =
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 3.dp, height = 18.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape
                )
        )
        Spacer(Modifier.padding(start = 10.dp))
        Text(
            title,
            fontSize = 15.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            fontWeight = if (selected) FontWeight.SemiBold else null
        )
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
