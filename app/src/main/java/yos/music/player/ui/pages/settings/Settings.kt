package yos.music.player.ui.pages.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title

@Composable
fun Settings(
    navController: NavController,
    // 横屏从整页子页路由进入时的预选页
    initialPage: String? = null
) =
    SettingBackground {
        val landscape = isLandscape()
        // 横屏右栏页面栈（一级子页在底，二级页面压栈、左栏不隐藏）；竖屏不使用
        val paneStack = remember { mutableStateListOf(initialPage ?: UI.Settings.Main) }
        val selectedPage: String? = paneStack.lastOrNull().takeIf { it != UI.Settings.Main }
        val popPane: () -> Unit = { if (paneStack.size > 1) paneStack.removeAt(paneStack.lastIndex) }
        val openPane: (String) -> Unit = { route ->
            paneStack.clear()
            paneStack.add(route)
        }
        BackHandler(enabled = paneStack.size > 1, onBack = popPane)

        if (landscape) {
            // 分栏：左栏与右栏都用原版 Title 顶栏样式（大标题随滚动收起、
            // 上滑出现顶栏并在顶栏显示标题），各栏独立滚动；竖线全高
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Title(
                        title = stringResource(id = R.string.page_settings_title),
                        // 横屏从侧栏进入，顶栏不再放返回键；竖屏保留
                        onBack = if (isLandscape()) null else ({ navController.popBackStack() })
                    ) {
                        item("settings_menu") {
                            Column(Modifier.fillMaxWidth()) {
                                SettingsMenu(
                                    open = { openPane(it) },
                                    selectedPage = selectedPage
                                )
                            }
                        }
                    }
                }
                PaneDivider()
                Box(
                    Modifier
                        .weight(1.6f)
                        .fillMaxHeight()
                ) {
                    val page = selectedPage
                    if (page == null) {
                        Box(Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(id = R.string.settings_pane_empty_hint),
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .alpha(0.4f)
                            )
                        }
                    } else {
                        key(page) {
                            // 横屏右栏子页不放返回键；二级页面在右栏内压栈打开
                            when (page) {
                                UI.Settings.LibraryOverview -> LibraryOverview(
                                    navController,
                                    onOpenFolderSongs = {
                                        paneStack.add(UI.NormalMusic)
                                    }
                                )
                                UI.Settings.RemoteServerManagement -> RemoteServerManagement(
                                    navController,
                                    onOpenFolderPicker = { sid ->
                                        paneStack.add("${UI.Settings.RemoteFolderPicker}/$sid")
                                    }
                                )
                                UI.Settings.LyricSetting -> LyricSetting(navController)
                                UI.Settings.UserInterfaceSetting -> UserInterfaceSetting(navController)
                                UI.Settings.NotificationSetting -> NotificationSetting(navController)
                                UI.Settings.ExoplayerSetting -> ExoPlayerSettings(
                                    navController,
                                    onOpenMediaCodec = {
                                        paneStack.add(UI.Settings.MediaCodec)
                                    }
                                )
                                UI.Settings.LyricGetter -> LyricGetter(navController)
                                UI.Settings.About -> About(navController)
                                // 概览里的文件夹歌曲列表：右栏内打开
                                UI.NormalMusic -> yos.music.player.ui.pages.library.NormalMusic(
                                    navController,
                                    onBack = popPane
                                )
                                // 编解码器选择：右栏内打开
                                UI.Settings.MediaCodec -> yos.music.player.ui.pages.settings.audio.exoPlayer.MediaCodec(
                                    navController,
                                    onBack = popPane
                                )
                                // 远程文件夹选择器：右栏内打开
                                else -> if (page.startsWith("${UI.Settings.RemoteFolderPicker}/")) {
                                    yos.music.player.ui.pages.settings.remote.RemoteFolderPicker(
                                        navController,
                                        page.substringAfterLast('/'),
                                        onBack = popPane
                                    )
                                }
                            }
                        }
                    }
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
private fun SettingsMenu(open: (String) -> Unit, selectedPage: String? = null) {
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
        SettingsMenuRow(
            title = stringResource(id = R.string.settings_library_overview),
            selected = selectedPage == UI.Settings.LibraryOverview
        ) {
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
        SettingsMenuRow(
            title = stringResource(id = R.string.settings_remote_server_management),
            selected = selectedPage == UI.Settings.RemoteServerManagement
        ) {
            open(UI.Settings.RemoteServerManagement)
        }
    }

    GroupSpacer()
    ListHeader(stringResource(id = R.string.settings_performance))
    RoundColumn {
        SettingsMenuRow(
            title = stringResource(id = R.string.settings_performance_lyric_title),
            selected = selectedPage == UI.Settings.LyricSetting
        ) {
            open(UI.Settings.LyricSetting)
        }
        Divider()
        SettingsMenuRow(
            title = stringResource(id = R.string.settings_performance_ui_title),
            selected = selectedPage == UI.Settings.UserInterfaceSetting
        ) {
            open(UI.Settings.UserInterfaceSetting)
        }
        Divider()
        SettingsMenuRow(
            title = stringResource(id = R.string.settings_performance_notification_title),
            selected = selectedPage == UI.Settings.NotificationSetting
        ) {
            open(UI.Settings.NotificationSetting)
        }
    }

    GroupSpacer()
    ListHeader(stringResource(id = R.string.settings_audio))
    RoundColumn {
        SettingsMenuRow(
            title = stringResource(id = R.string.settings_audio_exoplayer),
            selected = selectedPage == UI.Settings.ExoplayerSetting
        ) {
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
        SettingsMenuRow(
            title = stringResource(id = R.string.settings_extend_statusbarlyric),
            selected = selectedPage == UI.Settings.LyricGetter
        ) {
            open(UI.Settings.LyricGetter)
        }
    }

    GroupSpacer()
    ListHeader(stringResource(id = R.string.settings_others))
    RoundColumn {
        SettingsMenuRow(
            title = stringResource(id = R.string.settings_others_about),
            selected = selectedPage == UI.Settings.About
        ) {
            open(UI.Settings.About)
        }
    }
}


/** 设置左栏菜单行：LabelItem 同款视觉，叠加选中态（红竖条 + 浅红底 + 红字，与资料库菜单同语言） */
@Composable
private fun SettingsMenuRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) =
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 11.dp)
        )
        if (!selected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_next), contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .height(11.dp)
                    .alpha(0.3f), tint = MaterialTheme.colorScheme.onBackground
            )
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
