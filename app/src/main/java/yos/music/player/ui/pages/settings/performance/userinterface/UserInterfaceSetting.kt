package yos.music.player.ui.pages.settings.performance.userinterface

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import yos.music.player.R
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.pages.settings.Divider
import yos.music.player.ui.pages.settings.GroupSpacer
import yos.music.player.ui.pages.settings.GroupSpacerMedium
import yos.music.player.ui.pages.settings.LabelItem
import yos.music.player.ui.pages.settings.ListHeader
import yos.music.player.ui.pages.settings.SelectItem
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.pages.settings.SwitchItem
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title

@Composable
fun UserInterfaceSetting(navController: NavController) =
    SettingBackground {
        Title(title = stringResource(id = R.string.settings_performance_ui_title),
            onBack = {
                navController.popBackStack()
            },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {
                        // ListHeader(content = stringResource(id = R.string.settings_performance_ui_basic))

                        RoundColumn {
                            SelectItem(
                                title = stringResource(id = R.string.settings_performance_ui_theme),
                                items = listOf(
                                    "Auto",
                                    "Dark",
                                    "Light"
                                ),
                                value = SettingsLibrary.CustomTheme,
                                onValueChange = {
                                    SettingsLibrary.CustomTheme = it
                                }
                            )

                            Divider()

                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_ui_blur_effect_title),
                                // desc = stringResource(id = R.string.settings_performance_ui_blur_effect_desc),
                                onClick = {
                                    SettingsLibrary.BarBlurEffect = !SettingsLibrary.BarBlurEffect
                                },
                                checkedLambda = { SettingsLibrary.BarBlurEffect }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_blur_effect_desc))

                        GroupSpacerMedium()

                        val showCornerSetDialog =
                        remember("UserInterfaceSetting_showCornerSetDialog") {
                            mutableStateOf(false)
                        }

                        RoundColumn {
                            LabelItem(
                                title = stringResource(id = R.string.settings_performance_ui_screen_corner_title),
                                // desc = stringResource(id = R.string.settings_performance_ui_screen_corner_desc),
                                superLink = true
                            ) {
                                showCornerSetDialog.value = true
                            }
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_screen_corner_desc))

                        if (showCornerSetDialog.value) {
                            ScreenCornerSetDialog {
                                showCornerSetDialog.value = false
                            }
                        }

                        GroupSpacerMedium()

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_ui_nowplaying_show_volume_bar),
                                // desc = stringResource(id = R.string.settings_performance_ui_nowplaying_show_volume_bar_desc),
                                onClick = {
                                    SettingsLibrary.NowPlayingShowVolumeBar =
                                        !SettingsLibrary.NowPlayingShowVolumeBar
                                },
                                checkedLambda = { SettingsLibrary.NowPlayingShowVolumeBar }
                            )
                        }

                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_nowplaying_show_volume_bar_desc))
                        GroupSpacerMedium()

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_ui_nowplaying_background_effect),
                                // desc = stringResource(id = R.string.settings_performance_ui_nowplaying_background_effect_desc),
                                onClick = {
                                    SettingsLibrary.NowplayingBackgroundEffect =
                                        !SettingsLibrary.NowplayingBackgroundEffect
                                },
                                checkedLambda = { SettingsLibrary.NowplayingBackgroundEffect }
                            )
                        }

                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_nowplaying_background_effect_desc))
                        GroupSpacerMedium()

                        RoundColumn {
                            SwitchItem(
                                title = "歌词页自动隐藏控制按钮",
                                onClick = {
                                    SettingsLibrary.LyricsHideControls = !SettingsLibrary.LyricsHideControls
                                },
                                checkedLambda = { SettingsLibrary.LyricsHideControls }
                            )
                        }
                        ListHeader(content = "开启后，在歌词页面几秒无操作后自动隐藏播放控制按钮")

                        GroupSpacer()
                    }
                }
            }
        )
    }