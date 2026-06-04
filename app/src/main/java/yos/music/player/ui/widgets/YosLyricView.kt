package yos.music.player.ui.widgets

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.code.utils.lrc.YosUIConfig
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.objects.MainViewModelObject
import yos.music.player.data.objects.MediaViewModelObject
import yos.music.player.ui.theme.rememberAdaptive
import yos.music.player.ui.widgets.basic.YosWrapper
import kotlin.math.abs
import kotlin.math.roundToInt

val yosEasing = CubicBezierEasing(0.75f, 0.0f, 0.25f, 1.0f)

/**
 * YosLyricView 主控件
 * @param lrcEntriesLambda 处理完毕的 Lrc 文本
 * @param liveTimeLambda 当前歌曲进度
 * @param mediaEvent YosLyricView 媒体事件
 * @param translationLambda 是否开启翻译
 * @param blurLambda 是否启用模糊效果
 * @param uiConfig YosLyricView UI 控制，仅管理在日常使用中不经常调节的选项
 */
@Composable
fun YosLyricView(
    //mediaViewModel: MediaViewModel,
    lrcEntriesLambda: () -> List<List<Pair<Float, String>>>,
    liveTimeLambda: () -> Int,
    mediaEvent: YosMediaEvent,
    translationLambda: () -> Boolean = { true },
    blurLambda: () -> Boolean = { false },
    //animationConfig: YosAnimationConfig = YosAnimationConfig(),
    uiConfig: YosUIConfig = YosUIConfig(),
    weightLambda: () -> Boolean,
    modifier: Modifier,
    userScrollEnabled: Boolean = true,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val mainTextBasicColor = Color(uiConfig.mainTextBasicColor)
    val subTextBasicColor = Color(uiConfig.subTextBasicColor)
    //Color(0xFF919191)
    val otherSideForLines = MediaViewModelObject.otherSideForLines

    val lrcEntries = lrcEntriesLambda()

    // 每帧更新播放位置和歌词行索引，驱动逐字歌词动画
    val framePosition = remember { mutableIntStateOf(0) }
    // 手动 seek 后的冷却时间戳，防止 LaunchedEffect 循环立即覆盖手动设置的索引
    val manualSeekCooldown = remember { mutableStateOf(0L) }
    LaunchedEffect(lrcEntries) {
        // 歌词切换时重置状态，确保入场动画和首句 scale 动画完整播放
        MainViewModelObject.syncLyricIndex.intValue = -1
        framePosition.intValue = 0
        manualSeekCooldown.value = 0L
        delay(80)
        while (isActive) {
            val pos =
                yos.music.player.code.MediaController.mediaControl?.currentPosition?.toInt() ?: 0
            framePosition.intValue = pos

            // 手动 seek 冷却期内不覆盖 syncLyricIndex，避免色闪
            val inCooldown = manualSeekCooldown.value > 0L &&
                    (System.currentTimeMillis() - manualSeekCooldown.value) < 300L
            if (!inCooldown) {
                val entries = lrcEntriesLambda()
                if (entries.isNotEmpty()) {
                    val nextIdx = entries.indexOfFirst { (it.firstOrNull()?.first ?: Float.MAX_VALUE) > pos }
                    MainViewModelObject.syncLyricIndex.intValue = when {
                        nextIdx == 0 -> -1  // 尚未到达第一句歌词的时间
                        nextIdx != -1 -> (nextIdx - 1).coerceAtLeast(0)
                        else -> (entries.size - 1).coerceAtLeast(0)
                    }
                }
            }

            delay(8) // ~120fps
        }
    }

    //val thisLyricLines = MediaViewModelObject.mainLyricLines
    if (lrcEntries.isEmpty() /*|| thisLyricLines.isEmpty()*/) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight(if (weightLambda()) 0.56f else 1f)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }) {
                    onBackClick()
                }
        ) {
            Text(
                text = uiConfig.noLrcText,
                fontSize = 18.sp,
                color = Color(uiConfig.mainTextBasicColor)
            )
        }
    } else {
            val scrollState = rememberLazyListState()
        // 歌词切换时定位到起始位置，替代 key(lrcEntries) 的全量重建（避免 deactivated node 崩溃）
        LaunchedEffect(lrcEntries) {
            scrollState.scrollToItem(0)
        }
        val currentLyricIndex =
            remember("YosLyricView_currentLyricIndex") { MainViewModelObject.syncLyricIndex }
        /*val noAnimateItems by remember {
            derivedStateOf { scrollState.layoutInfo.totalItemsCount - scrollState.layoutInfo.visibleItemsInfo.size - 1 }
        }
        val showAnimate by remember {
            derivedStateOf {
                currentLyricIndex in scrollState.layoutInfo.visibleItemsInfo.map { it.index - 1 } && currentLyricIndex > 0 && currentLyricIndex < noAnimateItems
            }
        }*/
        val blankSpacer: (LazyListScope.() -> Unit) = {
            item {
                Box(
                    modifier = Modifier
                        .height(uiConfig.blankHeight.dp)
                ) {
                }
            }
        }
        //val coroutineScope = rememberCoroutineScope()
        val enableLyricScroll = remember("YosLyricView_enableLyricScroll") {
            mutableStateOf(true)
        }
        /*val lastClickTime = rememberSaveable(key = "YosLyricView_lastClickTime") {
            mutableLongStateOf(0L)
        }*/

        /*YosWrapper {
            LaunchedEffect(enableLyricScroll.value, lastClickTime.longValue) {
                if (!enableLyricScroll.value) {
                    val time = 1500L
                    delay(time)
                    withContext(Dispatchers.Main) {
                        if (TimeUtils.getNowMills() - lastClickTime.longValue >= time) {
                            enableLyricScroll.value = true
                        }
                    }
                }
            }
        }*/

        val height = rememberSaveable(key = "YosLyricView_height") { mutableIntStateOf(0) }

        val targetWeight = 0.0618f
        val targetOffset = rememberSaveable(height.intValue, key = "YosLyricView_targetOffset") {
            height.intValue * targetWeight
        }
        // 顶部边距

        val space = 0.dp
        // 行距

        val measurer = rememberTextMeasurer(
            cacheSize = 32
        )

        val visibleItems = remember("YosLyricView_visibleItems") {
            derivedStateOf {
                scrollState.layoutInfo.visibleItemsInfo
            }
        }
        val targetItem = remember("YosLyricView_targetItem") {
            derivedStateOf {
                visibleItems.value.find {
                    it.index == currentLyricIndex.intValue + 1
                }
            }
        }
        val currentOffset = remember("YosLyricView_currentOffset", targetOffset) {
            derivedStateOf {
                targetItem.value?.offset ?: targetOffset.toInt()
            }
        }
        val scrollDistance = remember("YosLyricView_scrollDistance", targetOffset) {
            derivedStateOf {
                currentOffset.value - targetOffset
            }
        }
        val nowFirst = remember("YosLyricView_nowFirst") {
            derivedStateOf {
                scrollState.firstVisibleItemIndex
            }
        }
        val supportBlur = rememberSaveable(key = "supportBlur") {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }

        val isUserScrolling = remember { mutableStateOf(false) }
        val nestedScrollConnection = remember {
            @Stable
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    isUserScrolling.value = true
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {
                    isUserScrolling.value = false
                    return super.onPostFling(consumed, available)
                }
            }
        }

        YosWrapper {
            LaunchedEffect(isUserScrolling.value) {
                if (isUserScrolling.value) {
                    enableLyricScroll.value = false
                } else {
                    delay(1600)
                    enableLyricScroll.value = true
                }
            }
        }

        // 遮罩在 AnimatedVisibility 外层，确保歌词上浮时遮罩固定在原位
        val lyricVisible = remember { mutableStateOf(false) }
        LaunchedEffect(lrcEntries) { lyricVisible.value = true }
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = lyricVisible.value,
                enter = slideInVertically(
                    tween(durationMillis = 350, easing = yosEasing),
                    initialOffsetY = { it / 3 }
                ) + fadeIn(tween(200))
            ) {
                YosWrapper {
                    LazyColumn(
                        state = scrollState,
                        userScrollEnabled = userScrollEnabled,
                        contentPadding = PaddingValues(vertical = rememberAdaptive(16)),
                        modifier = Modifier
                            .fillMaxSize()
                    /*.drawWithCache {
                        onDrawWithContent {
                            val colors = if (weightLambda()) {
                                listOf(
                                    Color.Transparent,
                                    Color(0x59000000),
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color(0x59000000),
                                    Color(0x21000000),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent
                                )
                            } else {
                                listOf(
                                    Color.Transparent,
                                    Color(0x59000000),
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color(0x59000000),
                                    Color(0x3F000000),
                                    Color(0x21000000),
                                )
                            }

                            drawContent()

                            drawRect(
                                brush = Brush.verticalGradient(colors),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }*/
                    /*.scrollable(state = rememberScrollableState {
                        enableLyricScroll.value = false
                        lastClickTime.longValue =
                            TimeUtils.getNowMills()
                        it
                    }, orientation = Orientation.Vertical)*/
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }) {
                        onBackClick()
                    }
                    .then(if (userScrollEnabled) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
                    .onSizeChanged {
                        if (height.intValue == 0 && it.height != 0) {
                            height.intValue = it.height
                            //println("计算歌词视图高度：${height.intValue}")
                        }
                    }
            ) {
                //println("重组：歌词列表")
                blankSpacer()
                itemsIndexed(
                    items = lrcEntries,
                    key = { index, lines ->
                        // 使用索引作为稳定 key,避免 LazyColumn 复用导致的状态污染
                        // 歌词列表顺序在播放过程中保持稳定,索引是安全的标识符
                        index
                    }/*,
                contentType = { _, _ -> "YosLyricView_item" }*/
                ) { index, lines ->
                    val isCurrent = remember(lines) {
                        derivedStateOf {
                            index == currentLyricIndex.intValue
                        }
                    }

                    val isTop = remember(lines) {
                        derivedStateOf {
                            index == (currentLyricIndex.intValue - 1)
                        }
                    }

                    val showStateAnimation = remember(index) {
                        derivedStateOf {
                            (currentLyricIndex.intValue in scrollState.layoutInfo.visibleItemsInfo.map { it.index - 1 } && currentLyricIndex.intValue >= 0) && enableLyricScroll.value
                        }
                    }

                    val isLyricEmpty = rememberSaveable(lines) {
                        mutableStateOf(
                            lines.all { it.second.isBlank() }
                        )
                    }

                    key(SettingsLibrary.LyricFontSize, SettingsLibrary.TranslationFontSize, SettingsLibrary.LyricFontWeight, SettingsLibrary.LyricLineBalance) {
                        val translation = lines.last().second.ifBlank { null }
                        val blurVal = if (!showStateAnimation.value || index == currentLyricIndex.intValue || !blurLambda() || !supportBlur) {
                            0f
                        } else {
                            (abs(index - currentLyricIndex.intValue) * 2.5f).coerceAtMost(8f)
                        }
                        val otherSideVal = otherSideForLines.getOrElse(index) { false }

                        YosWrapper {
                            LyricItem(
                                isCurrent = index == currentLyricIndex.intValue,
                                isTop = index == (currentLyricIndex.intValue - 1),
                                mainLyric = lines.dropLast(1),
                                translation = translation,
                                showTranslation = translationLambda(),
                                mainTextSize = SettingsLibrary.LyricFontSize,
                                subTextSize = SettingsLibrary.TranslationFontSize,
                                blurValue = blurVal,
                                mainTextBasicColor = mainTextBasicColor,
                                subTextBasicColor = subTextBasicColor,
                                otherSide = otherSideVal,
                                liveTime = framePosition.intValue,
                                measurer = measurer,
                                isLyricEmpty = lines.all { it.second.isBlank() },
                                nextTime = if (index + 1 > lrcEntries.size - 1) 0f else (lrcEntries[(index + 1)].firstOrNull()?.first ?: 0f),
                            ) {
                                Vibrator.doubleClick(context)
                                isUserScrolling.value = false
                                enableLyricScroll.value = true
                                currentLyricIndex.intValue = index
                                manualSeekCooldown.value = System.currentTimeMillis()
                                mediaEvent.onSeek((lines.firstOrNull()?.first ?: 0f).toInt())
                            }
                        }
                    }

                }
                blankSpacer()
                item("extra_blank") {
                    Spacer(modifier = Modifier.height(rememberAdaptive(500)))
                }
            }
        }
        } // AnimatedVisibility
        } // Box (mask)

        YosWrapper {
            //val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
            LaunchedEffect(currentLyricIndex.intValue, translationLambda()) {
                try {
                    if (currentLyricIndex.intValue < 0) return@LaunchedEffect
                    if (enableLyricScroll.value) {
                        /*visibleItems = scrollState.layoutInfo.visibleItemsInfo
                        targetItem =
                            visibleItems.find { it.index == currentLyricIndex.intValue */
                        /** 2*/
                        /** 2*//* + 1 }*/
                        if (
                            try {
                                if (currentLyricIndex.intValue - 1 < 0) false
                                else (
                                        (lrcEntries[(currentLyricIndex.intValue - 1)][1].second.isBlank())
                                        /*&&
                                        (lrcEntries[(currentLyricIndex.intValue).coerceAtLeast(
                                            0
                                        )].first().first - lrcEntries[(currentLyricIndex.intValue - 1)].first().first > 900f)*/)
                                // 这里有一个特殊的更改，因为AppleMusic歌词转过来会有两个连续一样的时间轴，在LrcFactory有更改，下面的那个900不用管
                                // 已经作了规范处理

                            } catch (_: Exception) {
                                false
                            }
                        ) {
                            return@LaunchedEffect
                        }

                        if (targetItem.value != null /*|| lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED)*/) {
                            /*currentOffset.value = targetItem.value?.offset?:targetOffset.toInt()
                            scrollDistance.value = currentOffset - targetOffset*/
                            scrollState.animateScrollBy(
                                scrollDistance.value,
                                animationSpec = spring(
                                    stiffness = 120f,
                                    dampingRatio = 1f,
                                    visibilityThreshold = 0.01f
                                )
                            )
                        } else {
                            scrollState.animateScrollToItem(
                                index = (currentLyricIndex.intValue
                                        /** 2*/
                                        /** 2*/
                                        + 1).coerceAtLeast(0),
                                scrollOffset = -targetOffset.toInt()
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        /*YosWrapper {
            LaunchedEffect(Unit) {
                while (true) {
                    val liveTime = liveTimeLambda()
                    val nextIndex = lrcEntries.indexOfFirst { line ->
                        (line.firstOrNull()?.first ?: Float.MAX_VALUE) > liveTime
                    }

                    if (nextIndex != -1 && nextIndex - 1 != currentLyricIndex.intValue) {
                        currentLyricIndex.intValue = nextIndex - 1
                    } else if (nextIndex == -1 && currentLyricIndex.intValue != lrcEntries.size - 1) {
                        currentLyricIndex.intValue = lrcEntries.size - 1
                    }

                    delay(100)
                }
            }
        }*/

        YosWrapper {
            //val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
            LaunchedEffect(Unit) {
                /*if (!lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED)) {
                    return@LaunchedEffect
                }*/
                try {
                    // 无论 syncLyricIndex 是否已被初始化，都用 scrollToItem 做瞬移定位
                    // scrollToItem 是瞬间的（无动画），避免 animateScrollBy 产生夸张的上浮
                    delay(50) // 延迟一帧，避免在初始组合帧内触发布局
                    val liveTime = liveTimeLambda()
                    val nextIndex = lrcEntries.indexOfFirst { line ->
                        (line.firstOrNull()?.first ?: Float.MAX_VALUE) > liveTime
                    }
                    val scrollTarget = if (nextIndex != -1) nextIndex.coerceAtLeast(0)
                                       else lrcEntries.size.coerceAtLeast(0)
                    scrollState.scrollToItem(
                        index = scrollTarget,
                        scrollOffset = -targetOffset.toInt()
                    )
                } catch (_: Exception) {
                }

            }
        }
    }
}

/*@Composable
fun Dp.toPx(): Float {
    val density = LocalDensity.current
    return this.value * density.density
}*/

@Composable
fun Float.toDp(): Dp {
    val density = LocalDensity.current
    return (this / density.density).dp
}

@Composable
private fun LazyItemScope.Line(
    lines: List<Pair<Float, String>>,
    style: TextStyle,
    measurer: TextMeasurer,
    modifier: Modifier,
    viewAlign: Alignment.Horizontal,
    draw: CacheDrawScope.(Constraints, TextLayoutResult) -> DrawResult
) =
    YosWrapper {
        val styledString = remember(style, lines) {
            buildString {
                lines.forEach { char ->
                    if (char.second.isNotEmpty()) {
                        append(char.second)
                    }
                }
            }
        }

        val density = LocalDensity.current

        Column(
            horizontalAlignment = viewAlign,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) {
            // 两遍渲染：第一遍用 onSizeChanged 获取可用宽度，
            // 第二遍用实际宽度做文本测量和绘制。
            // 避免 BoxWithConstraints / SubcomposeLayout 导致的 "place on deactivated node" 崩溃。
            val availableWidthPx = remember { mutableIntStateOf(0) }

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .onSizeChanged { size -> if (size.width > 0) availableWidthPx.intValue = size.width }
            ) {
                if (availableWidthPx.intValue > 0) {
                    val maxWidthPx = availableWidthPx.intValue
                    val forceFullWidth = when (style.textAlign) {
                        TextAlign.End, TextAlign.Center, TextAlign.Justify -> true
                        else -> false
                    }

                    val measureResult = remember(styledString, style, maxWidthPx) {
                        measurer.measure(
                            text = styledString,
                            style = style,
                            constraints = Constraints(
                                minWidth = if (forceFullWidth) maxWidthPx else 0,
                                maxWidth = maxWidthPx,
                            ),
                            layoutDirection = LayoutDirection.Ltr
                        )
                    }

                    val heightPx = with(density) { (style.lineHeight * measureResult.lineCount).toPx() }
                    val widthPx = runCatching {
                        (0 until measureResult.lineCount).maxOf {
                            measureResult.getBoundingBox(
                                measureResult.getLineEnd(it, visibleEnd = true) - 1
                            ).right
                        }
                    }.getOrDefault(maxWidthPx.toFloat())

                    val finalWidthPx = if (forceFullWidth) maxWidthPx.toFloat() else widthPx

                    Spacer(
                        modifier = Modifier
                            .size(
                                width = with(density) { finalWidthPx.toDp() },
                                height = with(density) { heightPx.toDp() }
                            )
                            .drawWithCache {
                                draw(
                                    Constraints.fixed(
                                        finalWidthPx.roundToInt(),
                                        heightPx.roundToInt()
                                    ),
                                    measureResult
                                )
                            }
                    )
                }
            }
        }
    }

val easing: Easing = EaseInOutQuad

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LazyItemScope.LyricItem(
    isCurrent: Boolean,  // 改为直接传值,而非 lambda
    isTop: Boolean,      // 改为直接传值
    mainLyric: List<Pair<Float, String>>,
    translation: String?,
    showTranslation: Boolean,
    mainTextSize: Int,
    subTextSize: Int,
    blurValue: Float,
    mainTextBasicColor: Color,
    subTextBasicColor: Color,
    measurer: TextMeasurer,
    isLyricEmpty: Boolean,  // 改为直接传值
    nextTime: Float,
    otherSide: Boolean,
    liveTime: Int,  // 改为直接传值
    onClick: () -> Unit
) {
    val viewAlign = Alignment.Start
    val focusedColor = Color(0xFFFFFFFF)
    val unfocusedColor = Color(0x2EFFFFFF)
    val unfocusedSolidBrush = SolidColor(unfocusedColor)

    // 使用 remember 保存状态,确保 LazyColumn 复用时状态正确
    val isNotOneByOne = remember(mainLyric) {
        mainLyric.all { it.first == mainLyric.firstOrNull()?.first }
    }
    // 保持 liveTime 最新引用，避免 drawWithCache 缓存导致逐字高亮使用过期的 liveTime
    val liveTimeRef = rememberUpdatedState(liveTime)

    YosWrapper {
        Column(
            Modifier
                .padding(horizontal = rememberAdaptive(9)),
            horizontalAlignment = viewAlign
        ) {
            val otherSideAnimate = if (otherSide) {
                TransformOrigin(1f, 0.25f)
            } else {
                TransformOrigin(0f, 0.25f)
            }

            val otherSideTransformOrigin =
                if (otherSide) TransformOrigin(1f, 0.5f)
                else TransformOrigin(0f, 0.5f)

            // Scale 动画使用 isCurrent 状态,避免 lambda 引用问题
            val scale = animateFloatAsState(
                targetValue = if (isCurrent) 1.04f else 1f,
                animationSpec = if (isCurrent)
                    TweenSpec(durationMillis = 270, easing = yosEasing, delay = 0)
                else
                    TweenSpec(durationMillis = 300, easing = yosEasing, delay = 45)
            )

            val cardPadding = if (otherSide) {
                Modifier.padding(start = rememberAdaptive(28))
            } else {
                Modifier.padding(end = rememberAdaptive(28))
            }

            if (isLyricEmpty) {
                Column {
                    val percent = ((liveTime - (mainLyric.firstOrNull()?.first ?: 0f)).coerceAtLeast(0f) / (nextTime - (mainLyric.firstOrNull()?.first ?: 0f))).coerceAtMost(1f)
                    val show = isLyricEmpty && isCurrent && percent != 0f
                    
                    AnimatedVisibility(
                        show,
                        enter = fadeIn(animationSpec = TweenSpec(durationMillis = 550, easing = yosEasing, delay = 300)) +
                                scaleIn(initialScale = 0.85f, transformOrigin = otherSideAnimate, animationSpec = TweenSpec(durationMillis = 550, easing = yosEasing, delay = 300)),
                        exit = fadeOut() + scaleOut(targetScale = 0.85f, transformOrigin = otherSideAnimate, animationSpec = TweenSpec(durationMillis = 340, easing = yosEasing))
                    ) {
                        YosWrapper {
                            LyricCard(
                                { scale.value },
                                cardPadding,
                                otherSideTransformOrigin,
                                viewAlign,
                            ) {
                                Column(
                                    Modifier.padding(start = 20.dp, end = 20.dp).padding(top = 8.dp, bottom = 10.dp),
                                    horizontalAlignment = viewAlign
                                ) {
                                    CountdownAnimation({ percent }, colorLambda = { mainTextBasicColor })
                                }
                            }
                        }
                    }
                }
            } else {
                YosWrapper {
                    LyricCard(
                        { scale.value },
                        cardPadding,
                        otherSideTransformOrigin,
                        viewAlign,
                    ) {
                        val blurModifier = if (blurValue == 0f) {
                            Modifier
                        } else {
                            Modifier.blur(blurValue.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        }

                        YosWrapper {
                            Column(
                                Modifier.then(blurModifier).fillMaxWidth(),
                                horizontalAlignment = viewAlign
                            ) {
                                val textAlign = if (otherSide) TextAlign.End else TextAlign.Start

                                // 高亮状态 — 必须在 thisAlpha 之前计算
                                val showHighLight = isNotOneByOne || run {
                                    val idx = (mainLyric.size - (if (translation != null) 3 else 1)).coerceIn(0, mainLyric.size - 1)
                                    liveTime >= mainLyric[idx].first
                                }

                                // Alpha 动画：当前行和未播放行完整显示(靠文字颜色区分)，已播完行低透明度
                                val thisAlpha = animateFloatAsState(
                                    targetValue = if (isCurrent || !showHighLight) 1f else 0.14f,
                                    animationSpec = if (isCurrent)
                                        TweenSpec(durationMillis = 350, easing = yosEasing, delay = 145)
                                    else
                                        TweenSpec(durationMillis = 350, easing = yosEasing, delay = 80)
                                )

                                // 修复: otherSidePadding 依赖 otherSide
                                val otherSidePadding = if (otherSide) {
                                    Modifier.padding(
                                        start = 20.dp,
                                        end = if (mainLyric.last().second.endsWith("：")) 3.dp else 20.dp
                                    )
                                } else {
                                    Modifier.padding(start = 20.dp, end = 20.dp)
                                }

                                val lyricTextStyle = mainTextStyle()
                                // 修复: charLayoutCache 使用 mainLyric 作为 key
                                val charLayoutCache = remember(mainLyric) { mutableMapOf<String, TextLayoutResult>() }
                                
                                Line(
                                    lines = mainLyric,
                                    style = if (otherSide) lyricTextStyle.copy(textAlign = TextAlign.End) else lyricTextStyle,
                                    measurer = measurer,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            this.alpha = thisAlpha.value
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                        }
                                        .padding(vertical = 4.dp)
                                        .then(otherSidePadding)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            onClick()
                                        },
                                    viewAlign = viewAlign
                                ) { parentConstraints, measureResult ->
                                    // 非逐字歌词:全高亮
                                    if (isNotOneByOne) {
                                        return@Line onDrawWithContent {
                                            drawText(textLayoutResult = measureResult, color = focusedColor)
                                        }
                                    }

                                    // 逐字歌词但不是当前行
                                    if (!isCurrent) {
                                        if (showHighLight) {
                                            // 已播放完,高亮
                                            return@Line onDrawWithContent {
                                                drawText(textLayoutResult = measureResult, color = focusedColor, topLeft = Offset(0F, -4F))
                                            }
                                        } else {
                                            // 未播放,不高亮
                                            return@Line onDrawWithContent {
                                                drawText(textLayoutResult = measureResult, color = unfocusedColor)
                                            }
                                        }
                                    }

                                        // 以下为逐字处理

                                        var sum = 0
                                        var lastTime = 0f

                                        val wordsToDraw = arrayListOf<DrawWord>()

                                        var averageTime = 0f

                                        lastTime = mainLyric.firstOrNull()?.first ?: 0f

                                        val measureStyle = lyricTextStyle.let { if (otherSide) it.copy(textAlign = TextAlign.End) else it }
                                        mainLyric.fastForEachIndexed { wordIndex, word ->

                                            // 旧的逐字处理逻辑
                                            /*val process = processWords(word.second)

                                    process.fastForEach { word ->

                                        // 非逐字转移到上面处理
                                        *//*if (isNotOneByOne.value) {
                                                word.split("").fastForEach { charWord ->
                                                    wordsToDraw += DrawWord(
                                                        time = word.first,
                                                        word = charWord,
                                                        layout = measurer.measure(
                                                            text = charWord,
                                                            style = lyricTextStyle,
                                                            constraints = measureResult.layoutInput.constraints,
                                                            layoutDirection = if (viewAlign.value == Alignment.End) LayoutDirection.Rtl else LayoutDirection.Ltr
                                                        ),
                                                        topLeft = measureResult.getBoundingBox(sum.coerceAtMost(
                                                            mainLyric.sumOf { it.second.length } - 1).coerceAtLeast(0)).topLeft,
                                                        brush = { _, _ ->
                                                               focusedSolidBrush
                                                        }
                                                    ).also {
                                                        sum += charWord.length
                                                    }
                                                }

                                                return@fastForEach
                                            }*//*
                                        }*/

                                            //println(word.second + "：" + sum.coerceAtMost(mainLyric.sumOf { it.second.length } - 1).coerceAtLeast(0) + "，共 "+ mainLyric.sumOf { it.second.length })

                                            // 新逻辑

                                            val thisWord = word.second

                                            if (thisWord.isEmpty()) {
                                                return@fastForEachIndexed
                                            }

                                            averageTime = (word.first - lastTime) / thisWord.length

                                            val thisWordGroupLastTime = if (wordIndex - 1 < 0) {
                                                mainLyric.firstOrNull()?.first ?: 0f
                                            } else {
                                                mainLyric[(wordIndex - 1)].first
                                            }
                                            val groupPercent =
                                                if ((word.first - thisWordGroupLastTime) == 0f) {
                                                    0f
                                                } else {
                                                    ((liveTime - thisWordGroupLastTime).coerceAtLeast(
                                                        0f
                                                    ) / (word.first - thisWordGroupLastTime)).coerceIn(
                                                        0f,
                                                        1f
                                                    )
                                                }
                                            val easedPercent = easing.transform(groupPercent.coerceIn(
                                                0f,
                                                1f
                                            ))
                                            val topLeftWeight = 4 * easedPercent

                                            thisWord.forEach { char ->

                                                //println("$char：$lastTime to ${lastTime + averageTime}")

                                                val charWord = char.toString()

                                                val layout = charLayoutCache.getOrPut(charWord) {
                                                    measurer.measure(
                                                        text = charWord,
                                                        style = measureStyle,
                                                        constraints = measureResult.layoutInput.constraints
                                                    )
                                                }

                                                val thisWordLastTime = lastTime
                                                val thisWordAverageTime = averageTime

                                                wordsToDraw += DrawWord(
                                                    time = lastTime + averageTime,
                                                    word = charWord,
                                                    layout = layout,
                                                    topLeft = measureResult.getBoundingBox(sum.coerceAtMost(
                                                        mainLyric.sumOf { it.second.length } - 1)
                                                        .coerceAtLeast(0)).topLeft.minus(
                                                            Offset(
                                                                0F,
                                                                topLeftWeight
                                                            )
                                                            ),
                                                    startTime = thisWordLastTime,
                                                    duration = thisWordAverageTime,
                                                    brush = { px, percent ->
                                                        if (thisWord == " ") {
                                                            return@DrawWord unfocusedSolidBrush
                                                        }

                                                        val beforeColor = if (percent <= -0.5f) {
                                                            unfocusedColor
                                                        } else {
                                                            focusedColor
                                                        }

                                                        val afterColor = if (percent >= 1f) {
                                                            focusedColor
                                                        } else {
                                                            unfocusedColor
                                                        }
                                                        Brush.horizontalGradient(
                                                            0f to beforeColor,
                                                            (percent - px).coerceIn(
                                                                0f,
                                                                1f
                                                            ) to beforeColor,
                                                            (percent + px).coerceIn(
                                                                0f,
                                                                1f
                                                            ) to afterColor
                                                        )
                                                    }
                                                ).also {
                                                    sum += charWord.length
                                                    lastTime += averageTime
                                                }
                                            }
                                        }

                                        onDrawBehind {
                                            val t = liveTimeRef.value.toFloat()
                                            wordsToDraw.fastForEach { l ->
                                                val percent = if (l.duration == 0f) Float.POSITIVE_INFINITY
                                                              else (t - l.startTime) / l.duration
                                                drawText(
                                                    textLayoutResult = l.layout,
                                                    topLeft = l.topLeft,
                                                    brush = l.brush(0.3f, percent)
                                                )
                                            }
                                        }
                                    }
                                }
                                // 翻译文本 - 需要在 textAlign 定义的作用域内
                                val textAlign = if (otherSide) TextAlign.End else TextAlign.Start
                                
                                YosWrapper {
                                    AnimatedVisibility(showTranslation && translation != null) {
                                        translation?.let {
                                            val translationAlpha = animateFloatAsState(
                                                targetValue = if (isCurrent) 0.5f else 0.14f,
                                                animationSpec = if (isCurrent)
                                                    TweenSpec(durationMillis = 350, easing = yosEasing, delay = 145)
                                                else
                                                    TweenSpec(durationMillis = 350, easing = yosEasing, delay = 80)
                                            )

                                            val translationOtherSidePadding = Modifier.padding(start = 20.dp, end = 20.dp)

                                            Text(
                                                text = it,
                                                fontSize = SettingsLibrary.TranslationFontSize.sp,
                                                color = subTextBasicColor,
                                                fontWeight = when (SettingsLibrary.LyricFontWeight) {
                                                    "Thin" -> FontWeight.Thin
                                                    "ExtraLight" -> FontWeight.ExtraLight
                                                    "Light" -> FontWeight.Light
                                                    "Regular" -> FontWeight.Normal
                                                    "Medium" -> FontWeight.Medium
                                                    "SemiBold" -> FontWeight.SemiBold
                                                    "Bold" -> FontWeight.Bold
                                                    "ExtraBold" -> FontWeight.ExtraBold
                                                    "Black" -> FontWeight.Black
                                                    else -> FontWeight.Bold
                                                },
                                                modifier = Modifier
                                                    .graphicsLayer {
                                                        this.alpha = translationAlpha.value
                                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                                    }
                                                    .then(translationOtherSidePadding)
                                                    .padding(top = 5.dp),
                                                lineHeight = (SettingsLibrary.TranslationFontSize + 5).sp,
                                                letterSpacing = 0.3.sp,
                                                textAlign = textAlign
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } // 关闭 if (isLyricEmpty) else
        } // 关闭 Column (786行)
    } // 关闭 YosWrapper (785行)

@Composable
private fun LyricCard(
    scale: () -> Float,
    cardPadding: Modifier,
    otherSideTransformOrigin: TransformOrigin,
    viewAlign: Alignment.Horizontal,
    //otherSideThisLine: () -> Boolean,
    //onClick: () -> Unit,
    content: @Composable () -> Unit,
) =
    YosWrapper {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    //compositingStrategy = CompositingStrategy.ModulateAlpha
                    val scaleValue = scale()
                    scaleX = scaleValue
                    scaleY = scaleValue
                    transformOrigin = otherSideTransformOrigin
                }
                .fillMaxWidth()
                .then(cardPadding)
                .padding(top = 9.dp, bottom = 9.dp),
            horizontalAlignment = viewAlign
        ) {
            content()
        }
    }

@Composable
fun CountdownAnimation(progress: () -> Float, colorLambda: () -> Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale = infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = yosEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier.graphicsLayer {
            //compositingStrategy = CompositingStrategy.Offscreen
            scaleX = scale.value
            scaleY = scale.value
            alpha = 0.8f
        },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 5.dp)
        ) {
            for (i in 1..3) {
                /*val alpha = animateFloatAsState(
                    targetValue = if (progress() >= i / 4f) min(
                        1f,
                        (progress() - (i - 1) / 4f) * 4
                    ) else 0f,
                    animationSpec = tween(
                        if (progress() > 0) (progress() * 1200).toInt() else 1200,
                        easing = LinearEasing
                    )
                )*/

                val average = 1f / 3f
                val beforePadding = (i-1) * average
                val thisPercent = (progress() - beforePadding)  / ((i * average) - beforePadding)
                val alpha = 0.2f + (0.8f * thisPercent).coerceIn(0f, 0.8f)

                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(
                            colorLambda().copy(alpha = alpha),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}


@Composable
fun mainTextStyle(): TextStyle = TextStyle(
    fontSize = SettingsLibrary.LyricFontSize.sp,
    lineHeight = 40.5.sp,
    fontWeight =
    when (SettingsLibrary.LyricFontWeight) {
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
    },
    letterSpacing = 0.05.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    ),
    lineBreak = LineBreak(
        strategy = if (SettingsLibrary.LyricLineBalance) LineBreak.Strategy.Balanced else LineBreak.Strategy.Simple,
        LineBreak.Strictness.Default,
        LineBreak.WordBreak.Default
    )
)

/*val BackgroundTextStyle = TextStyle(
    fontSize = 34.sp,
    lineHeight = 42.sp,
    fontWeight = FontWeight.Bold
).copy(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)*/

@Stable
private data class DrawWord(
    val time: Float,
    val word: String,
    val layout: TextLayoutResult,
    val topLeft: Offset,
    val startTime: Float,
    val duration: Float,
    val brush: (px: Float, percent: Float) -> Brush
)

/*
fun processWords(input: String): List<String> {
    val result = mutableListOf<String>()
    var word = ""
    for (char in input) {
        if (char == ' ') {
            if (word.isNotEmpty()) {
                result.add(word)
                word = ""
            }
            result.add(" ")
        } else {
            word += char
        }
    }
    if (word.isNotEmpty()) {
        result.add(word)
    }
    return result
}*/
