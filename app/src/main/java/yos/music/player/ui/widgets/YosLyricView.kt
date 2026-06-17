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
import androidx.compose.runtime.withFrameMillis
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
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
private const val LYRIC_LAST_LINE_DURATION_MS = 4000f
private const val LYRIC_POSITION_BACKWARD_JITTER_MS = 500
private const val LYRIC_MAX_BOUNDARY_EXTRA_MS = 500f
private const val UPLIFT_HEIGHT_PX = 10f
// 字符裁剪区域的水平缓冲，解决字形溢出（如 J 的左侧投影）导致的跨字符高亮泄漏
private const val CHAR_CLIP_HORIZONTAL_PAD_PX = 2f
// ── Pre-computed per-character layout metrics ──
// Computed once from TextLayoutResult; reused every frame for lerp-based mask animation.
@Stable
private data class CharLayoutInfo(
    val textOffset: Int,
    val startMs: Float,
    val endMs: Float,
    val xStartPx: Float,
    val xEndPx: Float,
    val visualLine: Int,
    val upliftStartMs: Float,
    val upliftEndMs: Float,
    val upliftXStartPx: Float,
    val upliftXEndPx: Float,
) {
    fun upliftKey(): String {
        return "$visualLine:$upliftStartMs:$upliftEndMs:$upliftXStartPx:$upliftXEndPx"
    }
}

// ── Per-frame highlight mask state ──
// innerFeatherPx & glowWidthPx are dynamically sized based on character duration:
//   - slow/long syllables → wide feather + broad glow (soft, atmospheric)
//   - fast/short syllables → tight feather + narrow glow (sharp, precise)
//
// activeVisualLine:     the visual line (wrapped line index) currently being sung.
//                       -1 means "nothing active yet", Int.MAX_VALUE means "all done".
// fullyDoneVisualLines: the wrapped lines whose characters are all sung out; the
//                       overlay draws them solid bright in one pass (no mask edge).
// The mask edge only lives on the activeVisualLine.
private data class HighlightMask(
    val completed: Boolean,
    val activeVisualLine: Int,
    val fullyDoneVisualLines: IntRange,
    val maskRightPx: Float,
    val innerFeatherPx: Float,
    val glowWidthPx: Float,
) {
    /** Back-compat view: the visual line carrying the mask edge. */
    val visualLine: Int get() = activeVisualLine
}

// ── Mutual-exclusive line state machine ──
// Modeled after AMLL's timeline.ts: at any instant a lyric line is exactly one of
// Past / Active / Future, decided purely by the (already unique) currentIndex.
// This replaces the old absolute-time-based showHighLight/isActiveLine judgement,
// which caused cascading highlight flips on seek (the "rows flash bright then dim
// when seeking" bug) and double-highlight on line transitions.
enum class LineState { Past, Active, Future }

fun computeLineState(index: Int, currentIndex: Int): LineState = when {
    index < currentIndex -> LineState.Past
    index == currentIndex -> LineState.Active
    else -> LineState.Future
}

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
    lrcEntriesLambda: () -> List<YosLyricLine>,
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
    // 精确浮点时间，每帧必定变化（不取整），确保上浮动画、mask 插值连续无卡顿
    val framePositionFloat = remember { mutableStateOf(0f) }
    // 手动 seek 的目标位置 (ms)，-1 表示无待处理的 seek。
    // 替换原来的时间戳冷却机制：当播放器位置到达目标附近时自动清除，避免固定冷却时间在快/慢设备上的不同步问题。
    val pendingSeekTargetMs = remember { mutableIntStateOf(-1) }
    val pendingSeekTimestamp = remember { mutableStateOf(0L) }
    LaunchedEffect(lrcEntries) {
        // 用播放器当前位置初始化，而非 0/-1，避免切歌/页面重建时的高亮跳变
        val initPos = liveTimeLambda()
        framePosition.intValue = initPos
        framePositionFloat.value = initPos.toFloat()
        pendingSeekTargetMs.intValue = -1
        pendingSeekTimestamp.value = 0L
        val initEntries = lrcEntriesLambda()
        MainViewModelObject.syncLyricIndex.intValue = if (initEntries.isNotEmpty()) {
            val nextIdx = initEntries.indexOfFirst { it.startMs > initPos }
            when {
                nextIdx == 0 -> -1
                nextIdx != -1 -> (nextIdx - 1).coerceAtLeast(0)
                else -> (initEntries.size - 1).coerceAtLeast(0)
            }
        } else -1
        delay(80)
        // 时间插值：本地时钟前推 + EMA 拉回校准，消除播放器回调离散导致的阶梯跳变
        var smoothPos = initPos.toFloat()
        var lastFrameTimeMs = 0L
        while (isActive) {
            withFrameMillis { frameTimeMs ->
                if (lastFrameTimeMs == 0L) {
                    lastFrameTimeMs = frameTimeMs
                    return@withFrameMillis
                }

                val mc = yos.music.player.code.MediaController.mediaControl
                val rawPos = mc?.currentPosition?.toInt() ?: 0
                val isPlaying = mc?.isPlaying ?: false
                val target = pendingSeekTargetMs.intValue

                if (target >= 0) {
                    // 有待处理的 seek：保持 framePosition 在目标位置
                    smoothPos = target.toFloat()
                    framePosition.intValue = target
                    framePositionFloat.value = target.toFloat()
                } else if (isPlaying) {
                    // 本地时钟前推：用帧间隔 × 播放速度推算位移
                    val speed = mc?.playbackParameters?.speed ?: 1f
                    val elapsed = maxOf((frameTimeMs - lastFrameTimeMs) * speed, 0f)
                    smoothPos += elapsed

                    // 仅做正向校准：smoothPos 严格单调，从不被 rawPos 拖回
                    // 每个字的关键帧进度由歌词时间戳决定，不应受播放器回调抖动影响
                    val error = rawPos - smoothPos
                    when {
                        abs(error) > 500f -> {
                            // 大幅跳变（系统 seek）：直接吸附
                            smoothPos = rawPos.toFloat()
                        }
                        error > 2f -> {
                            // smoothPos 落后于播放器：正向加速追赶
                            smoothPos += error * 0.15f
                        }
                        // error <= 2f 或负数（smoothPos 超前）：保持当前速率，
                        // 不减速不回退，播放器自然会赶上
                    }

                    framePosition.intValue = smoothPos.roundToInt()
                    framePositionFloat.value = smoothPos
                } else {
                    // 暂停：精确跟随播放器位置
                    smoothPos = rawPos.toFloat()
                    framePosition.intValue = rawPos
                    framePositionFloat.value = rawPos.toFloat()
                }

                lastFrameTimeMs = frameTimeMs

                // 检查待处理的 seek 是否已完成（使用 rawPos 判断播放器实际位置）
                if (target >= 0) {
                    val caughtUp = rawPos >= target
                    val withinJitterRange = (target - rawPos) in 1..<LYRIC_POSITION_BACKWARD_JITTER_MS
                    val timedOut = System.currentTimeMillis() - pendingSeekTimestamp.value > 2000L
                    if (caughtUp || withinJitterRange || timedOut) {
                        pendingSeekTargetMs.intValue = -1
                        pendingSeekTimestamp.value = 0L
                    }
                }

                // 无待处理 seek 时正常同步歌词行索引
                if (pendingSeekTargetMs.intValue < 0) {
                    val entries = lrcEntriesLambda()
                    if (entries.isNotEmpty()) {
                        val nextIdx = entries.indexOfFirst { it.startMs > framePosition.intValue }
                        MainViewModelObject.syncLyricIndex.intValue = when {
                            nextIdx == 0 -> -1
                            nextIdx != -1 -> (nextIdx - 1).coerceAtLeast(0)
                            else -> (entries.size - 1).coerceAtLeast(0)
                        }
                    }
                }
            }
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

        // 歌词切换时直接定位到当前播放位置，避免先滚到开头再 animate 回来的弹跳
        LaunchedEffect(lrcEntries) {
            val currentPos = liveTimeLambda()
            val entries = lrcEntriesLambda()
            val nextIdx = if (entries.isNotEmpty()) {
                entries.indexOfFirst { it.startMs > currentPos }
            } else -1
            // LazyColumn 索引 = 歌词行索引 + 1（顶部 blankSpacer 占位），
            // 与 auto-scroll 的 scrollTarget = currentLyricIndex + 1 对齐
            val targetIndex = if (nextIdx != -1) {
                (nextIdx + 1).coerceAtLeast(0)
            } else if (entries.isNotEmpty()) {
                entries.size // 已播完所有行，定位到最后一行
            } else {
                0
            }
            scrollState.scrollToItem(targetIndex, scrollOffset = -targetOffset.toInt())
        }

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

                    key(SettingsLibrary.LyricFontSize, SettingsLibrary.TranslationFontSize, SettingsLibrary.LyricFontWeight, SettingsLibrary.LyricLineBalance) {
                        val translation = lines.translation.ifBlank { null }
                        val nextLineStart = lrcEntries.getOrNull(index + 1)?.startMs
                        val boundaryExtraMs = lyricBoundaryExtraMs(lines.tokens)
                        val lineDisplayEnd = maxOf(
                            lines.endMs + boundaryExtraMs,
                            nextLineStart ?: (lines.startMs + LYRIC_LAST_LINE_DURATION_MS),
                            lines.startMs + 1f
                        )
                        // 互斥的行状态：基于唯一的 currentIndex 直接推导，任意时刻只有一行 Active。
                        // 替代旧的基于绝对时间的 isActiveLine 判定，消除 seek 时的连锁亮暗闪烁。
                        val lineState = computeLineState(index, currentLyricIndex.intValue)
                        val isActiveLine = lineState == LineState.Active
                        val blurVal = if (!showStateAnimation.value || isActiveLine || !blurLambda() || !supportBlur) {
                            0f
                        } else {
                            (abs(index - currentLyricIndex.intValue) * 2.5f).coerceAtMost(8f)
                        }
                        val otherSideVal = lines.otherSide || otherSideForLines.getOrElse(index) { false }

                        YosWrapper {
                            LyricItem(
                                isCurrent = isActiveLine,
                                isFocusedLine = index == currentLyricIndex.intValue,
                                isTop = index == (currentLyricIndex.intValue - 1),
                                lineState = lineState,
                                mainLyric = lines.tokens,
                                lineStartMs = lines.startMs,
                                lineEndMs = lineDisplayEnd,
                                lyricEndMs = lines.endMs,
                                translation = translation,
                                showTranslation = translationLambda(),
                                mainTextSize = SettingsLibrary.LyricFontSize,
                                subTextSize = SettingsLibrary.TranslationFontSize,
                                blurValue = blurVal,
                                mainTextBasicColor = mainTextBasicColor,
                                subTextBasicColor = subTextBasicColor,
                                otherSide = otherSideVal,
                                liveTime = framePositionFloat.value,
                                measurer = measurer,
                                isLyricEmpty = lines.text.isBlank(),
                                nextTime = nextLineStart ?: lineDisplayEnd,
                            ) {
                                Vibrator.doubleClick(context)
                                isUserScrolling.value = false
                                enableLyricScroll.value = true
                                // 立即更新歌词行索引和高亮位置，消除 seek 等待期间的闪烁
                                currentLyricIndex.intValue = index
                                framePosition.intValue = lines.startMs.toInt()
                                framePositionFloat.value = lines.startMs
                                pendingSeekTargetMs.intValue = lines.startMs.toInt()
                                pendingSeekTimestamp.value = System.currentTimeMillis()
                                mediaEvent.onSeek(lines.startMs.toInt())
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
                                else lrcEntries[currentLyricIndex.intValue - 1].text.isBlank()
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
                        line.startMs > liveTime
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
                        line.startMs > liveTime
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
    lines: List<YosLyricToken>,
    style: TextStyle,
    measurer: TextMeasurer,
    modifier: Modifier,
    viewAlign: Alignment.Horizontal,
    draw: CacheDrawScope.(Constraints, TextLayoutResult, List<CharLayoutInfo>) -> DrawResult
) =
    YosWrapper {
        val styledString = remember(style, lines) {
            buildString {
                lines.forEach { char ->
                    if (char.text.isNotEmpty()) {
                        append(char.text)
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

                    // Pre-compute per-character pixel positions once; reused every frame for lerp animation.
                    val charLayout = remember(measureResult, lines) {
                        precomputeCharLayout(lines, measureResult)
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
                                    measureResult,
                                    charLayout
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
    isFocusedLine: Boolean,
    isTop: Boolean,      // 改为直接传值
    lineState: LineState,
    mainLyric: List<YosLyricToken>,
    lineStartMs: Float,
    lineEndMs: Float,
    lyricEndMs: Float,
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
    liveTime: Float,  // 精确浮点时间，确保上浮/mask 动画平滑无卡顿
    onClick: () -> Unit
) {
    val viewAlign = Alignment.Start
    val focusedColor = Color(0xFFFFFFFF)
    val unfocusedColor = Color(0x2EFFFFFF)

    // 使用 remember 保存状态,确保 LazyColumn 复用时状态正确
    val enableWordByWordLyric = SettingsLibrary.EnableWordByWordLyric
    val isNotOneByOne = remember(mainLyric, enableWordByWordLyric) {
        !enableWordByWordLyric || mainLyric.isEmpty() || mainLyric.all { it.startMs == it.endMs }
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
                targetValue = if (isFocusedLine) 1.04f else 1f,
                animationSpec = if (isFocusedLine)
                    TweenSpec(durationMillis = 270, easing = yosEasing, delay = 0)
                else
                    TweenSpec(durationMillis = 300, easing = yosEasing, delay = 0)
            )

            val cardPadding = if (otherSide) {
                Modifier.padding(start = rememberAdaptive(28))
            } else {
                Modifier.padding(end = rememberAdaptive(28))
            }

            if (isLyricEmpty) {
                Column {
                    val lineDuration = (nextTime.takeIf { it > lineStartMs } ?: lineEndMs).coerceAtLeast(lineStartMs + 1f) - lineStartMs
                    val percent = ((liveTime - lineStartMs).coerceAtLeast(0f) / lineDuration).coerceAtMost(1f)
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

                                // 高亮状态：Past / Active 高亮（已唱 + 正在唱），Future 不高亮（未唱）。
                                // 由互斥的 lineState 决定，而非绝对播放时间 —— seek 时状态一次性收敛，
                                // 不会触发中间行的连锁亮暗动画。
                                val showHighLight = lineState != LineState.Future

                                // Alpha 动画（透明度策略随模式不同）：
                                // - 逐字模式：Past(已唱)→0.14 暗，Active(当前)/Future(未唱)→1f。
                                //   未唱行虽 alpha=1f，但文字用 unfocusedColor(暗色)绘制，靠颜色区分亮暗。
                                // - 逐行模式：只有当前行亮(焦点)，其余全暗。逐行文字一律用 focusedColor，
                                //   明暗完全由 alpha 决定。
                                val alphaTarget = when {
                                    isCurrent -> 1f
                                    isNotOneByOne -> 0.14f
                                    lineState == LineState.Past -> 0.14f
                                    else -> 1f
                                }
                                val thisAlpha = animateFloatAsState(
                                    targetValue = alphaTarget,
                                    animationSpec = if (isCurrent)
                                        TweenSpec(durationMillis = 350, easing = yosEasing, delay = 145)
                                    else
                                        TweenSpec(durationMillis = 350, easing = yosEasing, delay = 80)
                                )

                                // 修复: otherSidePadding 依赖 otherSide
                                val otherSidePadding = if (otherSide) {
                                    Modifier.padding(
                                        start = 20.dp,
                                        end = if (mainLyric.lastOrNull()?.text?.endsWith("：") == true) 3.dp else 20.dp
                                    )
                                } else {
                                    Modifier.padding(start = 20.dp, end = 20.dp)
                                }

                                val lyricTextStyle = mainTextStyle()
                                // 上浮只属于当前行，切换后直接归位，不拖尾动画。
                                val lineLiftPx = if (isCurrent) -4f else 0f
                                // EMA-smoothed glow widths to prevent jumps at character boundaries.
                                // key = mainLyric：LazyColumn 复用槽位时按歌词行内容重置 EMA，
                                // 避免上一行的羽化/光晕宽度值被带到新行导致开头几个字错乱。
                                val smoothGlow = remember(mainLyric) { mutableStateOf(0f to 0f) }
                                Line(
                                    lines = mainLyric,
                                    style = if (otherSide) lyricTextStyle.copy(textAlign = TextAlign.End) else lyricTextStyle,
                                    measurer = measurer,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            this.alpha = thisAlpha.value
                                            this.translationY = lineLiftPx
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
                                ) { _, measureResult, charLayout ->
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

                                    // 当前行逐字歌词：双层渲染 + 时间驱动遮罩
                                    onDrawBehind {
                                        val currentTimeMs = liveTimeRef.value

                                        // 计算遮罩（只算一次，底层和顶层共用）
                                        val rawMask = calculateHighlightMask(charLayout, currentTimeMs)

                                        // EMA 平滑羽化/光晕宽度，消除跨字符边界时的跳变
                                        val (prevInner, prevGlow) = smoothGlow.value
                                        val blend = 0.25f
                                        val newInner = prevInner + blend * (rawMask.innerFeatherPx - prevInner)
                                        val newGlow = prevGlow + blend * (rawMask.glowWidthPx - prevGlow)
                                        smoothGlow.value = newInner to newGlow

                                        val mask = rawMask.copy(innerFeatherPx = newInner, glowWidthPx = newGlow)

                                        // 底图层：整行未播放文本（暗色），逐字保留上浮
                                        drawUnplayedChars(measureResult, charLayout, mask, currentTimeMs, unfocusedColor)

                                        // 顶图层：整行共用一个时间驱动遮罩和光晕，逐字保留上浮
                                        drawHighlightOverlay(measureResult, mask, charLayout, currentTimeMs, focusedColor)
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

private fun lyricBoundaryExtraMs(tokens: List<YosLyricToken>): Float {
    val longestCharDuration = tokens.maxOfOrNull { token ->
        val length = token.text.length.coerceAtLeast(1)
        ((token.endMs - token.startMs).coerceAtLeast(0f) / length)
    } ?: 0f
    return (longestCharDuration * 0.5f).coerceIn(0f, LYRIC_MAX_BOUNDARY_EXTRA_MS)
}

// ── Pre-computation: extract per-character pixel positions from TextLayoutResult ──

private fun precomputeCharLayout(
    tokens: List<YosLyricToken>,
    layout: TextLayoutResult,
): List<CharLayoutInfo> {
    val text = layout.layoutInput.text.text
    if (tokens.isEmpty() || text.isEmpty()) return emptyList()
    val result = mutableListOf<CharLayoutInfo>()
    var textOffset = 0
    for (token in tokens) {
        val tokenLen = token.text.length
        if (tokenLen == 0) continue
        val tokenDuration = (token.endMs - token.startMs).coerceAtLeast(0f)
        val upliftUnits = buildUpliftUnits(token.text, textOffset, layout, token.startMs, token.endMs)
        for (i in 0 until tokenLen) {
            val charOffset = textOffset + i
            if (charOffset >= text.length) break
            val box = layout.getBoundingBox(charOffset)
            val line = layout.getLineForOffset(charOffset)
            val charDuration = if (tokenDuration > 0f) tokenDuration / tokenLen else 0f
            val charStart = token.startMs + charDuration * i
            val charEnd = if (i == tokenLen - 1) token.endMs else token.startMs + charDuration * (i + 1)
            val upliftUnit = upliftUnits.firstOrNull { unit ->
                charOffset in unit.offsetStart..unit.offsetEndExclusive - 1
            }
            result += CharLayoutInfo(
                textOffset = charOffset,
                startMs = charStart,
                endMs = charEnd,
                xStartPx = box.left,
                xEndPx = box.right,
                visualLine = line,
                upliftStartMs = upliftUnit?.startMs ?: charStart,
                upliftEndMs = upliftUnit?.endMs ?: charEnd,
                upliftXStartPx = upliftUnit?.xStartPx ?: box.left,
                upliftXEndPx = upliftUnit?.xEndPx ?: box.right,
            )
        }
        textOffset += tokenLen
    }
    return result
}

private data class UpliftUnit(
    val offsetStart: Int,
    val offsetEndExclusive: Int,
    val startMs: Float,
    val endMs: Float,
    val xStartPx: Float,
    val xEndPx: Float,
)

private fun buildUpliftUnits(
    tokenText: String,
    textOffset: Int,
    layout: TextLayoutResult,
    tokenStartMs: Float,
    tokenEndMs: Float,
): List<UpliftUnit> {
    if (tokenText.isEmpty()) return emptyList()
    val tokenDuration = (tokenEndMs - tokenStartMs).coerceAtLeast(0f)
    val charDuration = if (tokenDuration > 0f) tokenDuration / tokenText.length else 0f
    val units = mutableListOf<UpliftUnit>()
    var i = 0
    while (i < tokenText.length) {
        val start = i
        val first = tokenText[i]
        if (first.isAsciiWordChar()) {
            i++
            while (i < tokenText.length && tokenText[i].isAsciiWordChar()) i++
        } else {
            i++
        }
        val end = i
        val offsetStart = textOffset + start
        val offsetEndExclusive = textOffset + end
        val unitStartMs = tokenStartMs + charDuration * start
        val unitEndMs = if (end == tokenText.length) tokenEndMs else tokenStartMs + charDuration * end
        val boxes = (offsetStart until offsetEndExclusive).mapNotNull { offset ->
            runCatching { layout.getBoundingBox(offset) }.getOrNull()
        }
        if (boxes.isNotEmpty()) {
            units += UpliftUnit(
                offsetStart = offsetStart,
                offsetEndExclusive = offsetEndExclusive,
                startMs = unitStartMs,
                endMs = unitEndMs,
                xStartPx = boxes.minOf { it.left },
                xEndPx = boxes.maxOf { it.right },
            )
        }
    }
    return units
}

private fun Char.isAsciiWordChar(): Boolean {
    return this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '\''
}

// ── Per-frame: calculate highlight mask using linear interpolation ──
// X_current = X_start + α × (X_end − X_start), where α = (t − T_begin) / (T_end − T_begin)
//
// 重写（参考 AMLL generateWebAnimationBasedMaskImage）：
// 按 visual line 分段。找出哪些折行已全部唱完（fullyDoneVisualLines）和
// 哪一行正在唱（activeVisualLine）。正在唱的行内 mask 边缘起点固定为该行
// 最左字符的 xStartPx，不再沿用上一行的 lastCompletedX，消除跨行脆弱性。
// 零宽 token（charStart == charEnd）直接当作已唱完，不做 0.001 强行插值，
// 消除换行首字瞬跳。

private fun featherForDuration(durationMs: Float): Float = when {
    durationMs < 80f -> 8f
    durationMs < 200f -> 14f
    durationMs < 500f -> 22f
    durationMs < 1000f -> 32f
    else -> 42f
}

private fun calculateHighlightMask(
    chars: List<CharLayoutInfo>,
    currentTimeMs: Float,
): HighlightMask {
    val empty = HighlightMask(
        completed = false, activeVisualLine = -1, fullyDoneVisualLines = -1..-2,
        maskRightPx = 0f, innerFeatherPx = 0f, glowWidthPx = 0f
    )
    if (chars.isEmpty()) return empty

    val lastVisualLine = chars.last().visualLine

    // 全部唱完
    val lastCharEnd = chars.last().let { it.endMs.coerceAtLeast(it.startMs) }
    if (currentTimeMs >= lastCharEnd) {
        return HighlightMask(
            completed = true,
            activeVisualLine = Int.MAX_VALUE,
            fullyDoneVisualLines = 0..lastVisualLine,
            maskRightPx = chars.last().xEndPx, innerFeatherPx = 0f, glowWidthPx = 0f,
        )
    }

    // 还没开始唱任何字
    val firstCharStart = chars.first().startMs
    if (currentTimeMs <= firstCharStart) {
        return HighlightMask(
            completed = false, activeVisualLine = -1, fullyDoneVisualLines = -1..-2,
            maskRightPx = chars.first().xStartPx, innerFeatherPx = 0f, glowWidthPx = 0f,
        )
    }

    // 逐字遍历，定位第一个未唱完的字 → 它所在行即 activeVisualLine，
    // 它之前的所有字所在行（含同行早于它的字）都已唱完。
    // mask 边缘相对「上一个已唱完字的右边界」插值（参考 AMLL generateWebAnimationBasedMaskImage
    // 的 curPos 累计推进），而非从行首 —— 否则正在唱的字会覆盖掉前面已唱完字的高亮。
    var doneLineMax = -1        // 已唱完的最大 visual line（闭区间右端）
    var doneXRight = chars.first().xStartPx   // 上一个已唱完字的右边界（mask 起跳点）
    var activeLine = -1
    var maskRightPx = 0f
    var innerFeather = 0f
    var glowWidth = 0f
    var prevVel = 0f            // 前一个字的线速度 (px/ms)，用于平滑变速

    for (idx in chars.indices) {
        val char = chars[idx]
        val charStart = char.startMs
        val charEnd = char.endMs.coerceAtLeast(charStart)
        val zeroWidth = (charEnd <= charStart)
        val isDone = if (zeroWidth) currentTimeMs > charStart else currentTimeMs >= charEnd

        // 当前字的线速度 = 字宽 / 时长（px/ms），供前后字做速度平滑
        val charVel = if (charEnd > charStart) {
            (char.xEndPx - (if (char.visualLine != doneLineMax.coerceAtLeast(-1))
                chars.first { it.visualLine == char.visualLine }.xStartPx
            else doneXRight)) / (charEnd - charStart)
        } else 0f

        if (isDone) {
            doneXRight = char.xEndPx
            if (char.visualLine > doneLineMax) doneLineMax = char.visualLine
            prevVel = if (charVel > 0f) charVel else prevVel
            continue
        }

        val isActive = if (zeroWidth) false else currentTimeMs > charStart
        // 跨行时 doneXRight 来自上一折行，不能直接作为本行起跳点；
        // 改用本字所属行的行首。
        val startX = if (char.visualLine != doneLineMax.coerceAtLeast(-1)) {
            chars.first { it.visualLine == char.visualLine }.xStartPx
        } else {
            doneXRight
        }
        val charWidth = char.xEndPx - startX

        if (isActive) {
            activeLine = char.visualLine
            val duration = (charEnd - charStart).coerceAtLeast(0.001f)
            val rawAlpha = ((currentTimeMs - charStart) / duration).coerceIn(0f, 1f)

            // 三次 Hermite 平滑变速：字内速度从 prevVel 平滑过渡到 nextVel，
            // 起止时间精确落在字的开始和结束，无速度跳变。
            val vCurr = if (duration > 0f && charWidth > 0f) charWidth / duration else 0f
            val vPrev = prevVel.coerceAtLeast(0f)
            val vNext = run {
                val next = chars.getOrNull(idx + 1)
                if (next != null) {
                    val nd = (next.endMs - next.startMs).coerceAtLeast(0.001f)
                    val nw = next.xEndPx - if (next.visualLine != char.visualLine)
                        chars.first { it.visualLine == next.visualLine }.xStartPx
                    else char.xEndPx
                    if (nd > 0f && nw > 0f) nw / nd else 0f
                } else 0f
            }

            val s0 = if (vPrev > 0f && vCurr > 0f) (2f * vPrev / (vPrev + vCurr)).coerceIn(0f, 3f) else 1f
            val s1 = if (vNext > 0f && vCurr > 0f) (2f * vNext / (vCurr + vNext)).coerceIn(0f, 3f) else 1f

            // 三次 Hermite: f(t) = at³ + bt² + ct, f(0)=0, f(1)=1, f'(0)=s0, f'(1)=s1
            val a = s0 + s1 - 2f
            val b = 3f - 2f * s0 - s1
            val c = s0
            val t = rawAlpha
            val smoothAlpha = ((a * t + b) * t + c) * t

            maskRightPx = startX + smoothAlpha * charWidth
            innerFeather = featherForDuration(charEnd - charStart)
            glowWidth = (innerFeather * 2.25f).coerceIn(16f, 72f)
        } else {
            // 尚未开始的字（字间间隙）：mask 边缘停在起跳点
            if (activeLine == -1) {
                activeLine = char.visualLine
                maskRightPx = startX
                innerFeather = featherForDuration(charEnd - charStart)
                glowWidth = (innerFeather * 2.25f).coerceIn(16f, 72f)
            }
        }
        break
    }

    // activeLine 可能仍未被设置（例如所有字都判定为 done 但 lastCharEnd 判定未触发，浮点边界）
    if (activeLine == -1) {
        return HighlightMask(
            completed = true,
            activeVisualLine = Int.MAX_VALUE,
            fullyDoneVisualLines = 0..lastVisualLine,
            maskRightPx = chars.last().xEndPx, innerFeatherPx = 0f, glowWidthPx = 0f,
        )
    }

    return HighlightMask(
        completed = false,
        activeVisualLine = activeLine,
        // activeLine 本身不算 fullyDone；它之前的行算
        fullyDoneVisualLines = 0..(doneLineMax.coerceAtMost(activeLine - 1)),
        maskRightPx = maskRightPx,
        innerFeatherPx = innerFeather,
        glowWidthPx = glowWidth,
    )
}

/**
 * 计算字符的无缝水平裁剪边界，相邻字符的裁剪区域在中点处相接，
 * 消除因字形溢出（如 J 的左侧投影）导致的跨字符高亮泄漏。
 */
private fun seamlessClipBounds(
    sortedChars: List<CharLayoutInfo>,
    index: Int,
    clipLeft: Float,
    clipRight: Float,
): Pair<Float, Float> {
    val char = sortedChars[index]
    val charLeft = char.upliftXStartPx
    val charRight = char.upliftXEndPx
    val left = if (index == 0) {
        charLeft - CHAR_CLIP_HORIZONTAL_PAD_PX
    } else {
        (sortedChars[index - 1].upliftXEndPx + charLeft) / 2f
    }
    val right = if (index == sortedChars.lastIndex) {
        charRight + CHAR_CLIP_HORIZONTAL_PAD_PX
    } else {
        (charRight + sortedChars[index + 1].upliftXStartPx) / 2f
    }
    return maxOf(left, clipLeft) to minOf(right, clipRight)
}

// ── Base layer: dim text with per-character uplift, sharing the same timing as the overlay ──

private fun DrawScope.drawUnplayedChars(
    layout: TextLayoutResult,
    chars: List<CharLayoutInfo>,
    mask: HighlightMask,
    currentTimeMs: Float,
    dimColor: Color,
) {
    if (chars.isEmpty() || mask.completed) return
    for (line in 0 until layout.lineCount) {
        val sorted = chars.filter { it.visualLine == line }.distinctBy { it.upliftKey() }
        for (i in sorted.indices) {
            val char = sorted[i]
            val upliftY = calculateCharUplift(char, currentTimeMs)
            val (clipTop, clipBottom) = visualLineClip(layout, char.visualLine, upliftY)
            val (left, right) = seamlessClipBounds(sorted, i,
                Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
            clipRect(
                left = left,
                top = clipTop,
                right = right,
                bottom = clipBottom,
            ) {
                drawText(textLayoutResult = layout, color = dimColor, topLeft = Offset(0f, -upliftY))
            }
        }
    }
}

// ── Overlay drawing: a single continuous mask shared by the whole lyric line ──
// 参考 AMLL 的「单个 mask 渐变 + 整行应用」：当前 visual line 用一条水平渐变
// 一笔画完 —— 渐变左端到 mask 边缘是平坦的 focusedColor（实心亮区），mask 边缘
// 右侧羽化到透明。整行 clipRect 裁出「实心 + 羽化尾」，无 solid/glow 分段接缝。

private val GlowColor = Color(0xFFFFF8F0) // warm white, simulates over-bright halo

private fun DrawScope.drawHighlightOverlay(
    layout: TextLayoutResult,
    mask: HighlightMask,
    chars: List<CharLayoutInfo>,
    currentTimeMs: Float,
    focusedColor: Color,
) {
    if (layout.lineCount == 0) return

    if (mask.completed) {
        // 全部唱完：逐行绘制，避免 seamlessClipBounds 在不同 visual line 之间
        // 计算无意义的中点导致换行首字被裁剪消失
        for (line in 0 until layout.lineCount) {
            drawLineChars(layout, chars, currentTimeMs, focusedColor, visualLine = line)
        }
        return
    }

    val validDoneLines = if (mask.fullyDoneVisualLines.first > mask.fullyDoneVisualLines.last) {
        emptyList()
    } else {
        mask.fullyDoneVisualLines.toList()
    }

    // 已全部唱完的折行：整行纯亮色，一笔画完
    for (line in validDoneLines) {
        if (line in 0 until layout.lineCount) {
            drawLineChars(layout, chars, currentTimeMs, focusedColor, visualLine = line)
        }
    }

    // 正在唱的折行：硬边高光 + 右侧羽化。
    // 1. 硬边 [lineLeft, maskEdge] → 纯白实心，已唱区域完整覆盖
    // 2. 羽化 [maskEdge, maskEdge + glow] → 渐变白→暖白→透明，自然过渡
    // 两段在 maskEdge 处精准对接（均为聚焦色），无分界。
    val activeLine = mask.activeVisualLine
    if (activeLine < 0 || activeLine >= layout.lineCount) return

    val lineLeft = layout.getLineLeft(activeLine)
    val lineRight = layout.getLineRight(activeLine)
    val maskEdge = mask.maskRightPx.coerceIn(lineLeft, lineRight)
    val glow = mask.glowWidthPx.coerceAtLeast(0f)

    // 硬边高光：[lineLeft, maskEdge]
    if (maskEdge > lineLeft) {
        drawLineChars(layout, chars, currentTimeMs, focusedColor, activeLine, lineLeft, maskEdge)
    }

    // 右侧羽化：[maskEdge, maskEdge + glow]
    // 渐变立刻过渡（0.0→0.05→1.0），聚焦色只占前 5%
    val featherEnd = maskEdge + glow
    if (featherEnd > maskEdge) {
        val brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.0f to focusedColor,
                0.05f to GlowColor,
                1.0f to Color.Transparent,
            ),
            startX = maskEdge,
            endX = featherEnd,
        )
        drawLineChars(
            layout = layout,
            chars = chars,
            currentTimeMs = currentTimeMs,
            brush = brush,
            visualLine = activeLine,
            clipLeft = maskEdge,
            clipRight = featherEnd,
        )
    }
}

private fun calculateCharUplift(char: CharLayoutInfo, currentTimeMs: Float): Float {
    val duration = (char.upliftEndMs - char.upliftStartMs).coerceAtLeast(0.001f)
    val alpha = ((currentTimeMs - char.upliftStartMs) / duration).coerceIn(0f, 1f)
    return alpha * UPLIFT_HEIGHT_PX
}

private fun DrawScope.drawLineChars(
    layout: TextLayoutResult,
    chars: List<CharLayoutInfo>,
    currentTimeMs: Float,
    color: Color,
    visualLine: Int? = null,
    clipLeft: Float = Float.NEGATIVE_INFINITY,
    clipRight: Float = Float.POSITIVE_INFINITY,
) {
    val sorted = (visualLine?.let { line -> chars.filter { it.visualLine == line } } ?: chars)
        .distinctBy { it.upliftKey() }
    for (i in sorted.indices) {
        val char = sorted[i]
        val (left, right) = seamlessClipBounds(sorted, i, clipLeft, clipRight)
        if (right <= left) continue
        val upliftY = calculateCharUplift(char, currentTimeMs)
        val (clipTop, clipBottom) = visualLineClip(layout, char.visualLine, upliftY)
        clipRect(
            left = left,
            top = clipTop,
            right = right,
            bottom = clipBottom,
        ) {
            drawText(textLayoutResult = layout, color = color, topLeft = Offset(0f, -upliftY))
        }
    }
}

private fun DrawScope.drawLineChars(
    layout: TextLayoutResult,
    chars: List<CharLayoutInfo>,
    currentTimeMs: Float,
    brush: Brush,
    visualLine: Int,
    clipLeft: Float,
    clipRight: Float,
) {
    val sorted = chars.filter { it.visualLine == visualLine }.distinctBy { it.upliftKey() }
    for (i in sorted.indices) {
        val char = sorted[i]
        val (left, right) = seamlessClipBounds(sorted, i, clipLeft, clipRight)
        if (right <= left) continue
        val upliftY = calculateCharUplift(char, currentTimeMs)
        val (clipTop, clipBottom) = visualLineClip(layout, char.visualLine, upliftY)
        clipRect(
            left = left,
            top = clipTop,
            right = right,
            bottom = clipBottom,
        ) {
            drawText(
                textLayoutResult = layout,
                brush = brush,
                topLeft = Offset(0f, -upliftY),
            )
        }
    }
}

private fun visualLineClip(layout: TextLayoutResult, visualLine: Int, upliftY: Float): Pair<Float, Float> {
    val line = visualLine.coerceIn(0, layout.lineCount - 1)
    val top = layout.getLineTop(line) - upliftY
    val bottom = layout.getLineBottom(line) - upliftY
    return top to bottom
}
