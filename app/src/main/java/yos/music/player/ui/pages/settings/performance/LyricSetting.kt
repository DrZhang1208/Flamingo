package yos.music.player.ui.pages.settings.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.alexzhirkevich.cupertino.CupertinoSlider
import yos.music.player.R
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.pages.settings.GroupSpacer
import yos.music.player.ui.pages.settings.GroupSpacerMedium
import yos.music.player.ui.pages.settings.ListHeader
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.pages.settings.SwitchItem
import yos.music.player.ui.theme.isFlamingoInDarkMode
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title

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
                        val mainSizeSlider = remember { mutableFloatStateOf(SettingsLibrary.LyricFontSize.toFloat()) }
                        val transSizeSlider = remember { mutableFloatStateOf(SettingsLibrary.TranslationFontSize.toFloat()) }

                        ListHeader(content = "预览")
                        LyricPreview(
                            mainSize = mainSizeSlider.floatValue,
                            transSize = transSizeSlider.floatValue,
                            weightName = weightName(weightSlider.floatValue)
                        )
                        GroupSpacer()

                        ListHeader(content = "字重 — ${weightName(weightSlider.floatValue)}")
                        RoundColumn {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                CupertinoSlider(
                                    value = weightSlider.floatValue,
                                    onValueChange = {
                                        weightSlider.floatValue = it
                                        SettingsLibrary.LyricFontWeight = weightName(it)
                                    },
                                    valueRange = 0f..8f,
                                    steps = 7
                                )
                            }
                        }

                        GroupSpacer()

                        ListHeader(content = "主歌词字号 — ${mainSizeSlider.floatValue.toInt()}sp")
                        RoundColumn {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                CupertinoSlider(
                                    value = mainSizeSlider.floatValue,
                                    onValueChange = {
                                        mainSizeSlider.floatValue = it
                                        SettingsLibrary.LyricFontSize = it.toInt()
                                    },
                                    valueRange = 24f..44f
                                )
                            }
                        }

                        GroupSpacer()

                        ListHeader(content = "翻译字号 — ${transSizeSlider.floatValue.toInt()}sp")
                        RoundColumn {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                CupertinoSlider(
                                    value = transSizeSlider.floatValue,
                                    onValueChange = {
                                        transSizeSlider.floatValue = it
                                        SettingsLibrary.TranslationFontSize = it.toInt()
                                    },
                                    valueRange = 16f..32f
                                )
                            }
                        }

                        GroupSpacerMedium()

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
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_lyric_blur_effect),
                                onClick = { SettingsLibrary.LyricBlurEffect = !SettingsLibrary.LyricBlurEffect },
                                checkedLambda = { SettingsLibrary.LyricBlurEffect }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_blur_effect_desc))
                        GroupSpacer()
                    }
                }
            })
    }

@Composable
private fun LyricPreview(mainSize: Float, transSize: Float, weightName: String) {
    val mainSp = mainSize.toInt()
    val transSp = transSize.toInt()
    val isDark = isFlamingoInDarkMode()

    val mainColor = if (isDark) Color.White else Color.Black
    val subColor = Color(0xFF919191)
    val currentAlpha = 0.9f
    val nextAlpha = 0.14f

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

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background((if (isDark) Color.White else Color.Black).copy(alpha = 0.06f))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column {
            Column {
                Text(
                    text = "Walking through a crowd",
                    fontSize = mainSp.sp,
                    lineHeight = (mainSp + 8).sp,
                    fontWeight = fontWeight,
                    color = mainColor.copy(alpha = currentAlpha),
                    maxLines = 2
                )
                Text(
                    text = "穿过人山人海",
                    fontSize = transSp.sp,
                    lineHeight = (transSp + 6).sp,
                    fontWeight = FontWeight.Bold,
                    color = subColor.copy(alpha = currentAlpha * 0.6f),
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(12.dp))
            Column {
                Text(
                    text = "The village is aglow",
                    fontSize = mainSp.sp,
                    lineHeight = (mainSp + 8).sp,
                    fontWeight = fontWeight,
                    color = mainColor.copy(alpha = nextAlpha),
                    maxLines = 2
                )
                Text(
                    text = "整个城市流光溢彩",
                    fontSize = transSp.sp,
                    lineHeight = (transSp + 6).sp,
                    fontWeight = FontWeight.Bold,
                    color = subColor.copy(alpha = nextAlpha * 0.6f),
                    maxLines = 2
                )
            }
        }
    }
}