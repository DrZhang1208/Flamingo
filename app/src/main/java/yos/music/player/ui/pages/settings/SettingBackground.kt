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

/**
 * 横屏分栏内嵌页面有自定义返回（右栏退栈）时优先调用，
 * 否则按导航栈返回。不能在参数位写 elvis（会推断成 Any 导致编译错）。
 */
fun NavController.backOr(onBack: (() -> Unit)?) {
    if (onBack != null) onBack() else popBackStack()
}