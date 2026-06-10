package yos.music.player.ui.pages.settings.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import yos.music.player.ui.widgets.basic.OptionDialog
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.Folder
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.MusicLibrary.allFolders
import yos.music.player.data.libraries.MusicLibrary.hideFolders
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.YosWrapper
import yos.music.player.ui.widgets.basic.yosRoundColumn

@Composable
fun LibraryOverview(navController: NavController) =
    SettingBackground {
    // 观察 folderListVersion 确保远程文件夹变更时重组
    @Suppress("UNUSED_VARIABLE") val fv = MusicLibrary.folderListVersion
    val folders = allFolders.sortedBy { it.name }
    Title(title = stringResource(id = R.string.settings_library_overview),
        onBack = {
            navController.popBackStack()
        },
        content = {
            yosRoundColumn {
                itemsIndexed(
                    folders,
                    key = { _, folder -> "${folder.serverId ?: "local"}_${folder.path}" }
                ) { index, folder ->
                    FolderItem(folder = folder) {
                        val targetTitle = folder.name
                        val targetList = folder.songs
                        LibraryObject.setTargetListWithTitle(targetTitle, targetList)
                        navController.toUI(UI.NormalMusic)
                    }
                    key(index) {
                        val needDivider = index < folders.size - 1
                        if (needDivider) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 102.dp)
                                    .alpha(0.15f)
                                    .height(0.5.dp)
                                    .background(Color.Black withNight Color.White)
                            )
                        }
                    }
                }
            }
        }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LazyItemScope.FolderItem(folder: Folder, itemClick: () -> Unit) {
    var showUnmount by remember { mutableStateOf(false) }
    val isRemote = folder.serverId != null
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .height(80.dp)
            .fillMaxWidth()
            .padding(start = 22.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        val shape = YosRoundedCornerShape(4.dp)
        val density = LocalDensity.current

        Image(painter = painterResource(id = R.drawable.placeholder_folder), contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .clickable { itemClick() }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        val outline = shape.createOutline(
                            Size(size.width, size.height),
                            LayoutDirection.Ltr,
                            density
                        )
                        drawOutline(
                            outline = outline,
                            color = Color.Gray.copy(alpha = 0.1f),
                            style = Stroke(width = 8f)
                        )
                        drawOutline(
                            outline = outline,
                            color = Color.Gray.copy(alpha = 0.5f),
                            style = Stroke(width = 8f),
                            blendMode = BlendMode.Overlay
                        )
                    }
                })

        Column(
            Modifier
                .padding(start = 16.dp)
                .weight(1f)
                .clickable { itemClick() }) {
            Text(
                text = folder.name,
                modifier = Modifier.padding(bottom = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
            if (isRemote) {
                val serverLabel = folder.serverId?.let { yos.music.player.data.remote.RemoteServerManager.getServer(it)?.label }
                if (serverLabel != null) {
                    Text(
                        text = serverLabel,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(0.35f)
                    )
                }
            }
        }

        // 远程文件夹：卸载按钮（放在开关左侧，保持开关对齐）
        if (isRemote) {
            Text(
                "卸载", fontSize = 12.sp, color = Color.Red.copy(alpha = 0.6f),
                modifier = Modifier.clickable { showUnmount = true }.padding(horizontal = 4.dp)
            )

            if (showUnmount) {
                OptionDialog(
                    icon = { Spacer(Modifier.size(0.dp)) },
                    title = "卸载远程文件夹",
                    subTitle = "确定要从资料库中移除「${folder.name}」吗？",
                    content = null,
                    positiveContent = "卸载",
                    onPositive = {
                        scope.launch {
                            MusicLibrary.unmountRemoteFolder(folder.serverId ?: "", folder.path)
                            showUnmount = false
                        }
                    },
                    negativeContent = "取消",
                    onNegative = { showUnmount = false },
                    onDismissRequest = { showUnmount = false }
                )
            }
        }

        CupertinoSwitch(checked = !hideFolders.any { it == folder.path }, onCheckedChange = {
            scope.launch(Dispatchers.IO) {
                Vibrator.click(context)
                withContext(Dispatchers.Main) {
                    if (it) MusicLibrary.unHideFolder(folder)
                    else MusicLibrary.hideFolder(folder)
                }
            }
        })

        Icon(
            painter = painterResource(id = R.drawable.ic_action_next), contentDescription = null,
            modifier = Modifier
                .height(12.dp)
                .alpha(0.3f)
                .padding(horizontal = 4.dp), tint = MaterialTheme.colorScheme.onBackground
        )
    }
}
