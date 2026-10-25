package yos.music.player.ui.pages.settings.audio.exoPlayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import yos.music.player.R
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.UI
import yos.music.player.ui.pages.settings.Divider
import yos.music.player.ui.pages.settings.GroupSpacer
import yos.music.player.ui.pages.settings.GroupSpacerMedium
import yos.music.player.ui.pages.settings.LabelItem
import yos.music.player.ui.pages.settings.ListHeader
import yos.music.player.ui.pages.settings.SelectItem
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.pages.settings.backOr
import yos.music.player.ui.pages.settings.SwitchItem
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title

@Composable
fun ExoPlayerSettings(
    navController: NavController,
    onBack: (() -> Unit)? = null,
    // 横屏设置右栏内打开编解码器选择：非空时不走路由
    onOpenMediaCodec: (() -> Unit)? = null
) =
    SettingBackground {
        Title(title = stringResource(id = R.string.settings_audio_exoplayer),
            subTitle = stringResource(id = R.string.settings_audio_exoplayer_sub),
            onBack = {
                navController.backOr(onBack)
            },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {

                        ListHeader(stringResource(id = R.string.settings_audio_exoplayer_behaviors))
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_audio_exoplayer_behaviors_audio_attributes),
                                // desc = stringResource(id = R.string.settings_audio_exoplayer_behaviors_audio_attributes_desc),
                                onClick = {
                                    SettingsLibrary.AudioAttributes =
                                        !SettingsLibrary.AudioAttributes
                                    yos.music.player.code.YosPlaybackService.instance
                                        ?.updateAudioFocusHandling(SettingsLibrary.AudioAttributes)
                                },
                                checkedLambda = { SettingsLibrary.AudioAttributes }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_audio_exoplayer_behaviors_audio_attributes_desc))

                        GroupSpacer()

                        ListHeader(stringResource(id = R.string.settings_audio_exoplayer_decode))
                        RoundColumn {
                            SelectItem(
                                title = stringResource(id = R.string.settings_audio_exoplayer_decode_codec),
                                items = listOf(
                                    "Auto",
                                    "FFmpeg",
                                    "System"
                                ),
                                value = SettingsLibrary.Codec,
                                onValueChange = {
                                    SettingsLibrary.Codec = it
                                }
                            )

                            Divider()

                            LabelItem(title = stringResource(id = R.string.settings_audio_exoplayer_support_mediacodec)) {
                                if (onOpenMediaCodec != null) onOpenMediaCodec() else navController.navigate(UI.Settings.MediaCodec)
                            }

                            Divider()

                            SwitchItem(
                                title = stringResource(id = R.string.settings_audio_exoplayer_decode_hardware_audio_track_playback_params),
                                onClick = {
                                    SettingsLibrary.HardwareAudioTrackPlayBackParams =
                                        !SettingsLibrary.HardwareAudioTrackPlayBackParams
                                },
                                checkedLambda = { SettingsLibrary.HardwareAudioTrackPlayBackParams }
                            )
                        }

                        GroupSpacerMedium()

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_audio_exoplayer_decode_audio_float_output),
                                // desc = stringResource(id = R.string.settings_audio_exoplayer_decode_audio_float_output_desc),
                                onClick = {
                                    SettingsLibrary.AudioFloatOutput =
                                        !SettingsLibrary.AudioFloatOutput
                                },
                                checkedLambda = { SettingsLibrary.AudioFloatOutput }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_audio_exoplayer_decode_audio_float_output_desc))

                        GroupSpacer()
                    }

                }
            }
        )
    }
