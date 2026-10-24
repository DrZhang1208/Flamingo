package yos.music.player.ui.pages.settings.performance

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.alexzhirkevich.cupertino.CupertinoSlider
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.code.utils.lrc.LyricsController
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.pages.settings.GroupSpacer
import yos.music.player.ui.pages.settings.GroupSpacerMedium
import yos.music.player.ui.pages.settings.ListHeader
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.pages.settings.SwitchItem
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title
import java.io.File

private val weightOptions = listOf("Thin", "ExtraLight", "Light", "Regular", "Medium", "SemiBold", "Bold", "ExtraBold", "Black")

private fun weightName(value: Float): String = weightOptions.getOrElse(value.toInt()) { "ExtraBold" }
private fun weightValue(name: String): Float = weightOptions.indexOf(name).coerceAtLeast(0).toFloat()

@Composable
fun LyricSetting(navController: NavController) =
    SettingBackground {
        Title(title = stringResource(id = R.string.settings_performance_lyric_title),
            onBack = { navController.popBackStack() },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {
                        val weightSlider = remember { mutableFloatStateOf(weightValue(SettingsLibrary.LyricFontWeight)) }
                        val fontScaleSlider = remember {
                            mutableFloatStateOf(SettingsLibrary.LyricFontScale.coerceIn(0.8f, 1.2f))
                        }
                        val mainSizeSlider = remember { mutableFloatStateOf(SettingsLibrary.LyricFontSize.toFloat()) }
                        val transSizeSlider = remember { mutableFloatStateOf(SettingsLibrary.TranslationFontSize.toFloat()) }
                        val timingOffsetSlider = remember {
                            mutableFloatStateOf(SettingsLibrary.LyricTimingOffsetMs.coerceIn(-1_000, 1_000).toFloat())
                        }
                        val context = LocalContext.current
                        val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                            if (uri == null) return@rememberLauncherForActivityResult
                            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "lyrics-font"
                            val validExtension = name.endsWith(".ttf", true) || name.endsWith(".otf", true)
                            val length = runCatching {
                                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                            }.getOrNull() ?: -1L
                            if (!validExtension || length > 64L * 1024L * 1024L) {
                                Toast.makeText(context, R.string.settings_performance_lyric_custom_font_invalid, Toast.LENGTH_SHORT).show()
                                return@rememberLauncherForActivityResult
                            }
                            runCatching {
                                val target = File(context.filesDir, "lyrics/custom.${name.substringAfterLast('.', "ttf").lowercase()}")
                                target.parentFile?.mkdirs()
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    target.outputStream().use(input::copyTo)
                                } ?: error("font stream unavailable")
                                if (target.length() > 64L * 1024L * 1024L) {
                                    target.delete()
                                    error("font exceeds size limit")
                                }
                                SettingsLibrary.LyricCustomFontPath = target.absolutePath
                                SettingsLibrary.LyricCustomFontName = name
                                SettingsLibrary.LyricCustomFontEnabled = true
                            }.onFailure {
                                Toast.makeText(context, R.string.settings_performance_lyric_custom_font_invalid, Toast.LENGTH_SHORT).show()
                            }
                        }

                        ListHeader(content = "预览")
                        LyricPreview(
                            mainSize = mainSizeSlider.floatValue * fontScaleSlider.floatValue,
                            transSize = transSizeSlider.floatValue * fontScaleSlider.floatValue,
                            weightName = weightName(weightSlider.floatValue)
                        )
                        GroupSpacer()

                        ListHeader(content = "字重 — ${weightName(weightSlider.floatValue)}")
                        RoundColumn {
                            SliderRow(weightSlider.floatValue, { weightSlider.floatValue = it; SettingsLibrary.LyricFontWeight = weightName(it) }, 0f..8f, 1f)
                        }

                        GroupSpacer()

                        ListHeader(content = "歌词缩放 — ${"%.2f".format(fontScaleSlider.floatValue)}×")
                        RoundColumn {
                            SliderRow(
                                fontScaleSlider.floatValue,
                                {
                                    val scale = it.coerceIn(0.8f, 1.2f)
                                    fontScaleSlider.floatValue = scale
                                    SettingsLibrary.LyricFontScale = scale
                                },
                                0.8f..1.2f,
                                0.05f
                            )
                        }

                        GroupSpacer()

                        ListHeader(content = "主歌词字号 — ${mainSizeSlider.floatValue.toInt()}sp")
                        RoundColumn {
                            SliderRow(mainSizeSlider.floatValue, { mainSizeSlider.floatValue = it; SettingsLibrary.LyricFontSize = it.toInt() }, 24f..44f, 1f)
                        }

                        GroupSpacer()

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(R.string.settings_performance_lyric_show_translation),
                                desc = stringResource(R.string.settings_performance_lyric_show_translation_desc),
                                onClick = {
                                    SettingsLibrary.NowPlayingTranslation = !SettingsLibrary.NowPlayingTranslation
                                },
                                checkedLambda = { SettingsLibrary.NowPlayingTranslation }
                            )
                        }

                        GroupSpacer()

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(R.string.settings_performance_lyric_show_romaji),
                                desc = stringResource(R.string.settings_performance_lyric_show_romaji_desc),
                                onClick = {
                                    SettingsLibrary.NowPlayingRomaji = !SettingsLibrary.NowPlayingRomaji
                                },
                                checkedLambda = { SettingsLibrary.NowPlayingRomaji }
                            )
                        }

                        GroupSpacer()

                        ListHeader(content = "翻译字号 — ${transSizeSlider.floatValue.toInt()}sp")
                        RoundColumn {
                            SliderRow(transSizeSlider.floatValue, { transSizeSlider.floatValue = it; SettingsLibrary.TranslationFontSize = it.toInt() }, 16f..32f, 1f)
                        }

                        GroupSpacerMedium()

                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_custom_font_select))
                        RoundColumn {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                                    fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype"))
                                }.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SettingsLibrary.LyricCustomFontName.ifBlank {
                                        stringResource(R.string.settings_performance_lyric_custom_font_select)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (SettingsLibrary.LyricCustomFontEnabled) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                                        SettingsLibrary.LyricCustomFontEnabled = false
                                        SettingsLibrary.LyricCustomFontPath = ""
                                        SettingsLibrary.LyricCustomFontName = ""
                                    }.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.settings_performance_lyric_custom_font_reset))
                                }
                            }
                        }

                        GroupSpacer()

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_lyric_line_balance),
                                onClick = { SettingsLibrary.LyricLineBalance = !SettingsLibrary.LyricLineBalance },
                                checkedLambda = { SettingsLibrary.LyricLineBalance }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_line_balance_desc))

                        GroupSpacer()

                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_others))
                        GroupSpacer()
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(R.string.settings_lyric_word_by_word),
                                desc = stringResource(R.string.settings_lyric_word_by_word_desc),
                                onClick = { SettingsLibrary.EnableWordByWordLyric = !SettingsLibrary.EnableWordByWordLyric },
                                checkedLambda = { SettingsLibrary.EnableWordByWordLyric }
                            )
                        }
                        GroupSpacer()
                        ListHeader(content = "${stringResource(R.string.settings_performance_lyric_timing_offset)} — ${timingOffsetSlider.floatValue.toInt()} ms")
                        RoundColumn {
                            SliderRow(
                                timingOffsetSlider.floatValue,
                                {
                                    timingOffsetSlider.floatValue = it
                                    SettingsLibrary.LyricTimingOffsetMs = it.toInt()
                                },
                                -1000f..1000f,
                                100f
                            )
                        }
                        ListHeader(content = stringResource(R.string.settings_performance_lyric_timing_offset_desc))
                        GroupSpacer()
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(R.string.settings_performance_lyric_prefer_embedded),
                                onClick = {
                                    SettingsLibrary.LyricPreferEmbedded = !SettingsLibrary.LyricPreferEmbedded
                                    LyricsController.clearCache()
                                },
                                checkedLambda = { SettingsLibrary.LyricPreferEmbedded }
                            )
                        }
                        GroupSpacer()
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_lyric_blur_effect),
                                onClick = { SettingsLibrary.LyricBlurEffect = !SettingsLibrary.LyricBlurEffect },
                                checkedLambda = { SettingsLibrary.LyricBlurEffect }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_blur_effect_desc))
                        GroupSpacer()

                        ListHeader(content = stringResource(R.string.settings_mss_title))
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(R.string.settings_mss_gpu_acceleration),
                                desc = stringResource(R.string.settings_mss_gpu_acceleration_desc),
                                onClick = {
                                    SettingsLibrary.MssGpuAccelerationEnabled = !SettingsLibrary.MssGpuAccelerationEnabled
                                },
                                checkedLambda = { SettingsLibrary.MssGpuAccelerationEnabled }
                            )
                        }
                        GroupSpacer()


                    }
                }
            })
    }

@Composable
private fun LyricPreview(mainSize: Float, transSize: Float, weightName: String) {
    val mainSp = mainSize.toInt()
    val transSp = transSize.toInt()
    val mainColor = Color.Black withNight Color.White
    val subColor = Color(0xFF919191) withNight Color(0xFF8E8E93)

    val fontWeight = when (weightName) {
        "Thin" -> FontWeight.Thin
        "ExtraLight" -> FontWeight.ExtraLight
        "Light" -> FontWeight.Light
        "Regular" -> FontWeight.Normal
        "Medium" -> FontWeight.Medium
        "SemiBold" -> FontWeight.SemiBold
        "Bold" -> FontWeight.Bold
        "ExtraBold" -> FontWeight.ExtraBold
        "Black" -> FontWeight.Black
        else -> FontWeight.ExtraBold
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(YosRoundedCornerShape(12.dp)).background(Color.White withNight Color(0xFF1c1c1e))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Walking through a crowd",
            fontSize = mainSp.sp, lineHeight = (mainSp + 8).sp,
            fontWeight = fontWeight, color = mainColor
        )
        Text(
            text = "穿过人山人海",
            fontSize = transSp.sp, lineHeight = (transSp + 6).sp,
            fontWeight = fontWeight, color = subColor,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

}

@Composable
fun SliderRow(value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, step: Float) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_tips_minus), null, Modifier.size(12.dp).alpha(0.45f).clickable(
            remember { MutableInteractionSource() }, null) {
            if (value > range.start) { Vibrator.click(ctx); onValueChange((value - step).coerceAtLeast(range.start)) }
        })
        CupertinoSlider(value, onValueChange, Modifier.weight(1f).padding(horizontal = 10.dp), valueRange = range)
        Icon(painterResource(R.drawable.ic_tips_plus), null, Modifier.size(14.dp).alpha(0.45f).clickable(
            remember { MutableInteractionSource() }, null) {
            if (value < range.endInclusive) { Vibrator.click(ctx); onValueChange((value + step).coerceAtMost(range.endInclusive)) }
        })
    }
}
