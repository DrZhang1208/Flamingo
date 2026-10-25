package yos.music.player.ui.pages.settings.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.Folder
import yos.music.player.data.remote.RemoteServerManager
import yos.music.player.ui.pages.settings.remote.displayLabel
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.MusicLibrary.allFolders
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.pages.settings.backOr
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.isFlamingoInDarkMode
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.YosWrapper

@Composable
fun LibraryOverview(
    navController: NavController,
    onBack: (() -> Unit)? = null,
    // 横屏设置右栏内打开文件夹歌曲列表：非空时点击不走路由
    onOpenFolderSongs: (() -> Unit)? = null
) =
    SettingBackground {
    // 观察 folderListVersion 确保远程文件夹变更时重组
    @Suppress("UNUSED_VARIABLE") val fv = MusicLibrary.folderListVersion
    val folders = allFolders.sortedBy { it.name }
    Title(title = stringResource(id = R.string.settings_library_overview),
        onBack = {
            navController.backOr(onBack)
        },
        content = {
            if (folders.isNotEmpty()) {
                item(key = "library_overview_card") {
                    // iOS 分组卡片：所有文件夹一张卡，行间分隔线在 FolderItem 内部
                    RoundColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.5.dp)
                    ) {
                        folders.forEachIndexed { index, folder ->
                            FolderItem(
                                folder = folder,
                                isFirst = index == 0,
                                isLast = index == folders.size - 1
                            ) {
                                LibraryObject.setTargetListWithTitle(folder.name, folder.songs)
                                    if (onOpenFolderSongs != null) onOpenFolderSongs() else navController.toUI(UI.NormalMusic)
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FolderItem(folder: Folder, isFirst: Boolean, isLast: Boolean, itemClick: () -> Unit) {
    var showUnmount by remember { mutableStateOf(false) }
    val isRemote = folder.serverId != null
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 整行按压高亮（iOS 风）：淡入淡出动画；首/末行裁剪到卡片圆角，避免高亮溢出卡片
    val rowInteraction = remember { MutableInteractionSource() }
    val pressed by rowInteraction.collectIsPressedAsState()
    val highlight by animateColorAsState(
        targetValue = if (pressed) Color.Black.copy(alpha = 0.05f) withNight Color.White.copy(alpha = 0.09f)
        else Color.Transparent,
        animationSpec = tween(200),
        label = "rowPressHighlight"
    )
    val rowShape = YosRoundedCornerShape(
        topStart = if (isFirst) 9.dp else 0.dp,
        topEnd = if (isFirst) 9.dp else 0.dp,
        bottomStart = if (isLast) 9.dp else 0.dp,
        bottomEnd = if (isLast) 9.dp else 0.dp
    )

    // 左滑露出「卸载」操作（iOS 滑动操作，仅远程文件夹）
    val density = LocalDensity.current
    val actionWidth = 78.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    // 行内容横向偏移：0f（闭合）.. -actionWidthPx（完全展开）
    val revealOffset = remember { Animatable(0f) }

    val closeReveal: () -> Unit = {
        if (revealOffset.value != 0f) {
            scope.launch { revealOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .then(
                    if (isRemote) Modifier.pointerInput(actionWidthPx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    revealOffset.snapTo(
                                        (revealOffset.value + dragAmount).coerceIn(-actionWidthPx, 0f)
                                    )
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    val open = revealOffset.value < -actionWidthPx * 0.5f
                                    if (open) Vibrator.click(context)
                                    revealOffset.animateTo(
                                        if (open) -actionWidthPx else 0f,
                                        spring(stiffness = Spring.StiffnessMedium)
                                    )
                                }
                            }
                        )
                    } else Modifier
                )
        ) {
            // 底层：右缘滑动操作按钮
            if (isRemote) {
                Box(Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(actionWidth)
                            .fillMaxHeight()
                            .background(Color(0xFFFF453A))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                Vibrator.click(context)
                                showUnmount = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("卸载", color = Color.White, fontSize = 14.5.sp)
                    }
                }
            }
            // 上层：行内容，不透明底色遮住操作层，随拖动平移。
            // 由本行定高（80dp）撑起外层 Box——操作层是 matchParentSize，等 Box 定尺寸，
            // 若这里也用 matchParentSize 会互相等待，Box 高度塌缩为 0，整卡不可见
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(revealOffset.value.roundToInt(), 0) }
                    .background(MaterialTheme.colorScheme.onSecondary)
                    .background(highlight, rowShape)
                    .clickable(
                        interactionSource = rowInteraction,
                        indication = null
                    ) {
                        if (revealOffset.value < -actionWidthPx * 0.5f) {
                            // 已展开时点击行 = 先收起，不进入文件夹
                            closeReveal()
                        } else {
                            itemClick()
                        }
                    }
                    .height(80.dp)
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

            val shape = YosRoundedCornerShape(4.dp)

            // 深色模式跟随应用内主题而非系统设置，需代码切换深色图标
            Image(painter = painterResource(
                id = if (isFlamingoInDarkMode()) R.drawable.placeholder_folder_dark
                else R.drawable.placeholder_folder
            ), contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(shape)
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
            ) {
                Text(
                    text = folder.name,
                    modifier = Modifier.padding(bottom = 1.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.5.sp,
                    lineHeight = 20.5.sp,
                )
                if (isRemote) {
                    val server = folder.serverId?.let { RemoteServerManager.getServer(it) }
                    if (server != null) {
                        Text(
                            text = "${server.label} · ${server.type.displayLabel()}",
                            fontSize = 13.2.sp,
                            lineHeight = 16.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(0.45f)
                        )
                    }
                }
            }

            CupertinoSwitch(checked = !MusicLibrary.isFolderHidden(folder), onCheckedChange = {
                scope.launch(Dispatchers.IO) {
                    Vibrator.click(context)
                    withContext(Dispatchers.Main) {
                        if (it) MusicLibrary.unHideFolder(folder)
                        else MusicLibrary.hideFolder(folder)
                    }
                }
            }, modifier = Modifier.height(25.dp))

            Icon(
                painter = painterResource(id = R.drawable.ic_action_next), contentDescription = null,
                modifier = Modifier
                    .padding(start = 14.dp)
                    .height(12.dp)
                    .alpha(0.3f), tint = MaterialTheme.colorScheme.onBackground
            )
        }
        }

        if (!isLast) {
            // 分隔线对齐到文字起点：行内边距 16 + 图标 64 + 文字间距 16
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 96.dp)
                    .alpha(0.15f)
                    .height(0.5.dp)
                    .background(Color.Black withNight Color.White)
            )
        }
    }

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
            onNegative = {
                showUnmount = false
                closeReveal()
            },
            onDismissRequest = {
                showUnmount = false
                closeReveal()
            }
        )
    }
}
