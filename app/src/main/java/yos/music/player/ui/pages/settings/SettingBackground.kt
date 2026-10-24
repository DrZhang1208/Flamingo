package yos.music.player.ui.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun SettingBackground(content: @Composable BoxScope.() -> Unit) =
    Box(modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.secondary), content = content)

/** 页面返回回调：横屏分栏传入清空右栏的 onBack，默认返回上一页 */
fun NavController.backOr(onBack: (() -> Unit)?): () -> Unit =
    onBack ?: { popBackStack() }