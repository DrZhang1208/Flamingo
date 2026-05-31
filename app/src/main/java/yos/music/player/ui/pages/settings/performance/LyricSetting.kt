package yos.music.player.ui.pages.settings.performance

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
import androidx.compose.foundation.rememberScrollState
import yos.music.player.ui.theme.YosRoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.alexzhirkevich.cupertino.CupertinoSlider
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
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
                            SliderRow(weightSlider.floatValue, { weightSlider.floatValue = it; SettingsLibrary.LyricFontWeight = weightName(it) }, 0f..8f, 1f)
                        }

                        GroupSpacer()

                        ListHeader(content = "主歌词字号 — ${mainSizeSlider.floatValue.toInt()}sp")
                        RoundColumn {
                            SliderRow(mainSizeSlider.floatValue, { mainSizeSlider.floatValue = it; SettingsLibrary.LyricFontSize = it.toInt() }, 24f..44f, 1f)
                        }

                        GroupSpacer()

                        ListHeader(content = "翻译字号 — ${transSizeSlider.floatValue.toInt()}sp")
                        RoundColumn {
                            SliderRow(transSizeSlider.floatValue, { transSizeSlider.floatValue = it; SettingsLibrary.TranslationFontSize = it.toInt() }, 16f..32f, 1f)
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

    val bgColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.06f)

    val scrollState = rememberScrollState()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = scrollState.value == 0
                val atBottom = scrollState.value >= scrollState.maxValue
                if ((atTop && available.y > 0) || (atBottom && available.y < 0)) return available
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = scrollState.value == 0
                val atBottom = scrollState.value >= scrollState.maxValue
                if ((atTop && available.y > 0) || (atBottom && available.y < 0)) return available
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val atTop = scrollState.value == 0
                val atBottom = scrollState.value >= scrollState.maxValue
                if ((atTop && available.y > 0) || (atBottom && available.y < 0)) return available
                return Velocity.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val atTop = scrollState.value == 0
                val atBottom = scrollState.value >= scrollState.maxValue
                if ((atTop && available.y > 0) || (atBottom && available.y < 0)) return available
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(160.dp)
            .clip(YosRoundedCornerShape(12.dp))
            .background(bgColor)
            .nestedScroll(nestedScrollConnection)
    ) {
        Column(
            Modifier
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 34.dp)
        ) {
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
                    fontWeight = fontWeight,
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
                    fontWeight = fontWeight,
                    color = subColor.copy(alpha = nextAlpha * 0.6f),
                    maxLines = 2
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth().height(28.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(bgColor, Color.Transparent)))
        )
        Box(
            Modifier
                .fillMaxWidth().height(28.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, bgColor)))
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