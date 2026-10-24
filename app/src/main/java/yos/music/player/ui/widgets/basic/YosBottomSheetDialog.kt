package yos.music.player.ui.widgets.basic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight

/** Actual painted Surface colour, including inherited Material tonal elevation. */
internal val LocalDialogContainerColor = staticCompositionLocalOf { Color.Unspecified }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YosBottomSheetDialog(
    bottomSheetState: SheetState = rememberModalBottomSheetState(),
    properties: ModalBottomSheetProperties,
    cornerRadius: () -> Dp,
    bottomContentPadding: Dp = 26.dp,
    scrollContent: Boolean = true,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val edgeToEdgeEnabled by remember("YosBottomSheetDialog_edgeToEdgeEnabled") {
        mutableStateOf(
            true
        )
    }
    val windowInsets = if (edgeToEdgeEnabled)
        WindowInsets(0) else BottomSheetDefaults.windowInsets

    val context = LocalContext.current

    YosWrapper {
        LaunchedEffect(Unit) {
            Vibrator.click(context)
        }
    }

    val radius = cornerRadius()
    val shape = remember(radius) { YosRoundedCornerShape(radius) }
    val scope = rememberCoroutineScope()
    val elasticLimit = with(LocalDensity.current) { 48.dp.toPx() }
    val elastic = remember(scope, elasticLimit) { DialogElasticMotion(scope, elasticLimit) }
    val containerColor = Color.White withNight Color.Black

    ModalBottomSheet(
        // Translate above Material's Surface clipping, so the entire sheet outline
        // moves too. Translating only the child clips its top at the old bounds.
        modifier = Modifier.graphicsLayer {
            translationY = elastic.offset
            val sheetOffset = runCatching { bottomSheetState.requireOffset() }.getOrDefault(0f)
            this.shape = DialogShadowShape(shape, 5.dp, sheetOffset)
            shadowElevation = if (bottomSheetState.isVisible || bottomSheetState.targetValue != SheetValue.Hidden)
                4.dp.toPx() else 0f
            spotShadowColor = Color.Black
            clip = false
        },
        onDismissRequest = onDismissRequest,
        sheetState = bottomSheetState,
        windowInsets = windowInsets,
        properties = properties,
        shape = RectangleShape,
        content = {
            DialogRefreshRate()
            // M3 1.2 的 sheet 窗口是裸 WindowManager 面板：宽取 displayMetrics 且默认避让
            // 挖孔，横屏会在摄像头一侧留下未遮罩亮条。改成 MATCH_PARENT + SHORT_EDGES
            // 让窗口（含遮罩）全屏铺满
            val sheetView = LocalView.current
            DisposableEffect(sheetView) {
                val root = sheetView.rootView
                val wm = root.context.getSystemService(android.content.Context.WINDOW_SERVICE)
                    as? android.view.WindowManager
                val lp = root.layoutParams as? android.view.WindowManager.LayoutParams
                if (wm != null && lp != null) {
                    lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        lp.layoutInDisplayCutoutMode =
                            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    runCatching { wm.updateViewLayout(root, lp) }
                }
                onDispose { }
            }
            // 横屏给卡片左右留边距：全宽卡片贴边观感差（竖屏维持原 5dp）
            val isLandscape = LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            Surface(modifier = Modifier
                .containDialogScroll(elastic)
                .padding(vertical = 5.dp, horizontal = if (isLandscape) 48.dp else 5.dp)
                .graphicsLayer {
                    this.shape = if (radius != 0.dp) {
                        shape
                    } else {
                        RectangleShape
                    }
                    clip = true
                }
                /*.navigationBarsPadding()*/,
                color = containerColor,
                contentColor = Color.Black withNight Color.White,
                content = {
                    val scheme = MaterialTheme.colorScheme
                    val paintedColor = if (containerColor == scheme.surface && LocalTonalElevationEnabled.current)
                        scheme.surfaceColorAtElevation(LocalAbsoluteTonalElevation.current)
                    else containerColor
                    CompositionLocalProvider(LocalDialogContainerColor provides paintedColor) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 26.dp, top = 26.dp, end = 26.dp, bottom = bottomContentPadding)
                                .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        ) {
                            content()
                        }
                    }
                }
            )
        },
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = MaterialTheme.colorScheme.onBackground.copy(0.13f)
    )
}

@Composable
private fun DialogTitle(icon: @Composable () -> Unit, title: String, subTitle: String? = null, horizontal: Boolean = false) {
    if (horizontal) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                if (subTitle != null) {
                    Text(
                        text = subTitle, fontSize = 14.sp,
                        modifier = Modifier
                            .alpha(0.5f).padding(top = 2.dp)
                            .basicMarquee(iterations = Int.MAX_VALUE),
                        maxLines = 1
                    )
                }
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            icon()
            Text(
                text = title,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                textAlign = TextAlign.Center
            )
            if (subTitle != null) {
                Text(
                    text = subTitle, fontSize = 16.sp,
                    modifier = Modifier
                        .alpha(0.5f).padding(horizontal = 14.dp),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.5.sp
                )
            }
        }
    }
}

@Composable
fun DialogContent(text: String, modifier: Modifier = Modifier) =
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .alpha(0.5f),
        lineHeight = 19.5.sp,
        fontSize = 14.5.sp
    )

/** OptionDialog 的单页描述：标题/副标题/图标/内容/按钮按页独立，其余通用参数在 OptionDialog 上 */
class OptionDialogPage(
    val key: Any,
    val title: String,
    val subTitle: String? = null,
    val icon: @Composable () -> Unit = {},
    val content: (@Composable (dismiss: DialogDismissAction) -> Unit)? = null,
    val positiveContent: String? = null,
    val negativeContent: String? = null,
    val dismissOnPositive: Boolean = true,
    val dismissOnNegative: Boolean = true,
    val onPositive: (() -> Unit)? = null,
    val onNegative: (() -> Unit)? = null,
    val horizontalTitle: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionDialog(
    icon: @Composable () -> Unit,
    title: String,
    subTitle: String? = null,
    content: (@Composable (dismiss: DialogDismissAction) -> Unit)?,
    positiveContent: String? = null,
    negativeContent: String? = null,
    dismissOnPositive: Boolean = true,
    dismissOnNegative: Boolean = true,
    horizontalTitle: Boolean = false,
    contentKey: Any? = null,
    contentBottomSpacing: Dp = 19.5.dp,
    bottomContentPadding: Dp = 26.dp,
    scrollContent: Boolean = true,
    bottomSheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties(),
    cornerRadius: () -> Dp = { SettingsLibrary.ScreenCorner.toInt().dp },
    onPositive: (() -> Unit)? = null,
    onNegative: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    OptionDialog(
        page = OptionDialogPage(
            key = Unit,
            title = title,
            subTitle = subTitle,
            icon = icon,
            content = if (contentKey != null) {
                // 保留旧 contentKey 通道的语义：仅内容区切换
                { dismiss -> AnimatedContent(targetState = contentKey, label = "OptionDialogContent") { content?.invoke(dismiss) } }
            } else content,
            positiveContent = positiveContent,
            negativeContent = negativeContent,
            dismissOnPositive = dismissOnPositive,
            dismissOnNegative = dismissOnNegative,
            onPositive = onPositive,
            onNegative = onNegative,
            horizontalTitle = horizontalTitle
        ),
        contentBottomSpacing = contentBottomSpacing,
        bottomContentPadding = bottomContentPadding,
        scrollContent = scrollContent,
        bottomSheetState = bottomSheetState,
        properties = properties,
        cornerRadius = cornerRadius,
        onDismissRequest = onDismissRequest
    )
}

/**
 * 多页版：菜单内跳转二级菜单时卡片不收起，整页（标题+内容+按钮）水平推入切换，
 * 卡片高度随页差异平滑缩放（SizeTransform 不裁剪）。dismiss 行为与单页版一致（收整卡）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionDialog(
    page: OptionDialogPage,
    contentBottomSpacing: Dp = 19.5.dp,
    bottomContentPadding: Dp = 26.dp,
    scrollContent: Boolean = true,
    bottomSheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties(),
    cornerRadius: () -> Dp = { SettingsLibrary.ScreenCorner.toInt().dp },
    onDismissRequest: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val dismissController = remember(scope, bottomSheetState) {
        DialogDismissController(scope,
            hide = {
                withDialogDismissMotion { bottomSheetState.hide() }
                bottomSheetState.currentValue == SheetValue.Hidden
            },
            dismissed = { currentOnDismiss() })
    }
    val animatedDismiss = remember(dismissController) { DialogDismissAction(dismissController::request) }

    YosBottomSheetDialog(
        bottomSheetState = bottomSheetState,
        properties = properties,
        onDismissRequest = animatedDismiss,
        cornerRadius = cornerRadius,
        bottomContentPadding = bottomContentPadding,
        scrollContent = scrollContent
    ) {
        OptionDialogBody(
            page = page,
            contentBottomSpacing = contentBottomSpacing,
            animatedDismiss = animatedDismiss,
            dismissController = dismissController
        )
    }
}

/**
 * 带页面切换的菜单：pages 内按 key 匹配当前页，整页推入/推出并平滑缩放卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionDialog(
    pages: List<OptionDialogPage>,
    currentPage: Any,
    contentBottomSpacing: Dp = 19.5.dp,
    bottomContentPadding: Dp = 26.dp,
    scrollContent: Boolean = true,
    bottomSheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties(),
    cornerRadius: () -> Dp = { SettingsLibrary.ScreenCorner.toInt().dp },
    onDismissRequest: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val dismissController = remember(scope, bottomSheetState) {
        DialogDismissController(scope,
            hide = {
                withDialogDismissMotion { bottomSheetState.hide() }
                bottomSheetState.currentValue == SheetValue.Hidden
            },
            dismissed = { currentOnDismiss() })
    }
    val animatedDismiss = remember(dismissController) { DialogDismissAction(dismissController::request) }

    YosBottomSheetDialog(
        bottomSheetState = bottomSheetState,
        properties = properties,
        onDismissRequest = animatedDismiss,
        cornerRadius = cornerRadius,
        bottomContentPadding = bottomContentPadding,
        scrollContent = scrollContent
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                ((slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(250))))
                    .using(SizeTransform(clip = false))
            },
            label = "OptionDialogPages"
        ) { key ->
            // 按 key 派生：退出中的旧页保持旧内容，不随外部重组变化
            val page = pages.firstOrNull { it.key == key } ?: return@AnimatedContent
            OptionDialogBody(
                page = page,
                contentBottomSpacing = contentBottomSpacing,
                animatedDismiss = animatedDismiss,
                dismissController = dismissController
            )
        }
    }
}

/** 单页/多页共用的整页渲染：标题 → 内容 → 确认/取消按钮 */
@Composable
private fun OptionDialogBody(
    page: OptionDialogPage,
    contentBottomSpacing: Dp,
    animatedDismiss: DialogDismissAction,
    dismissController: DialogDismissController
) {
    // 必须自带 Column：pages 版的父级是 AnimatedContent 的叠放容器（非垂直排列），
    // 多个根节点会被叠在一起，标题被内容色块遮挡
    Column(
        Modifier
            .fillMaxWidth()
    ) {
        DialogTitle(icon = page.icon, title = page.title, subTitle = page.subTitle, horizontal = page.horizontalTitle)
        Column(
            Modifier
                .fillMaxWidth()
        ) {
        if (page.content != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = contentBottomSpacing)
            ) {
                page.content(animatedDismiss)
            }
        }
        val height = 50.dp
        // 平滑圆角矩形（14dp圆角，与拖动条背景一致）
        val smoothShape = YosRoundedCornerShape(14.dp)

        val hasButtons = page.positiveContent != null || page.negativeContent != null
        if (hasButtons) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp, bottom = 2.dp)
            ) {
                if (page.positiveContent != null && page.onPositive != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height)
                            .background(MaterialTheme.colorScheme.primary, shape = smoothShape)
                            .clip(smoothShape)
                            .clickable {
                                if (page.dismissOnPositive) {
                                    dismissController.confirm { page.onPositive?.invoke() }
                                } else page.onPositive?.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = page.positiveContent,
                            color = Color.White,
                            fontSize = 16.5.sp
                        )
                    }
                }

                if (page.negativeContent != null && page.onNegative != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height)
                            .clip(smoothShape)
                            .background(
                                color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                            )
                            .clickable {
                                if (page.dismissOnNegative) {
                                    dismissController.confirm { page.onNegative?.invoke() }
                                } else page.onNegative?.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = page.negativeContent,
                            fontSize = 16.5.sp
                        )
                    }
                }
            }
        }
    }
    }
}
