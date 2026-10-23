@file:Suppress("DEPRECATION")

package yos.music.player.ui.pages

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect

import androidx.compose.runtime.SideEffect
import yos.music.player.ui.widgets.playerWindowLayout
import yos.music.player.ui.widgets.playerWindowPane
import yos.music.player.ui.widgets.verticalEdgeFade
import yos.music.player.ui.widgets.PlayerContentVisibility
import androidx.compose.ui.graphics.TransformOrigin
import yos.music.player.ui.widgets.playerHeaderModifier
import yos.music.player.ui.widgets.clipPlayerLyrics

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.view.ViewTreeObserver
import androidx.core.graphics.Insets
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.AnimatedContent
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderPositions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.isActive
import yos.music.player.ui.widgets.PlaybackSeekTrack
import yos.music.player.ui.widgets.PlayerProgressState
import yos.music.player.ui.widgets.VolumeSliderState
import yos.music.player.ui.widgets.PlaybackVolumeTrack
import yos.music.player.ui.widgets.SeekCancelArc
import yos.music.player.ui.widgets.SeekCancelArcHitRegion
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.blankj.utilcode.util.TimeUtils
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsHeight
import com.google.accompanist.insets.statusBarsPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.code.MediaController.mediaControl
import yos.music.player.code.MediaController.uiRefreshTrigger
import yos.music.player.code.MediaController.musicPlaying
import yos.music.player.code.MediaController.playingMusicList
import yos.music.player.code.MediaController.shuffleEnabled
import yos.music.player.code.MediaController.toggleShuffle
import yos.music.player.code.SystemMediaControlResolver
import yos.music.player.ui.widgets.PlaybackOutputButton
import yos.music.player.ui.widgets.PlaybackOutputDialog
import yos.music.player.ui.widgets.rememberPlaybackAudioOutput
import yos.music.player.code.PlaybackAudioOutput
import yos.music.player.code.VolumeChangeReceiver
import yos.music.player.code.YosPlaybackService
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosUIConfig
import yos.music.player.code.utils.lrc.LyricCardExporter
import yos.music.player.ui.widgets.LyricTranslationButton
import yos.music.player.ui.widgets.LyricSeekPreviewState
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.code.utils.player.FadeExo.fadePlay
import yos.music.player.data.libraries.FavPlayListLibrary
import yos.music.player.data.libraries.MusicLibrary.toMediaItem
import yos.music.player.data.libraries.PlayListLibrary

import yos.music.player.ui.widgets.basic.OptionDialog
import yos.music.player.ui.widgets.basic.OptionDialogPage
import yos.music.player.ui.widgets.basic.dialogListFade
import yos.music.player.ui.widgets.basic.LocalDialogContainerColor
import yos.music.player.ui.widgets.basic.SongMenuIcon
import yos.music.player.ui.widgets.basic.songMenuTrigger
import yos.music.player.ui.theme.withNight
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.models.MainViewModel
import yos.music.player.data.models.MediaViewModel
import yos.music.player.data.objects.MediaViewModelObject
import yos.music.player.data.objects.NowPlayingBackgroundState
import yos.music.player.data.objects.NowPlayingLyricState
import yos.music.player.ui.pages.NowPlayingPage.Album
import yos.music.player.ui.pages.NowPlayingPage.Lyric
import yos.music.player.ui.pages.NowPlayingPage.PlayingList
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.rememberAdaptive
import yos.music.player.ui.widgets.YosLyricView
import yos.music.player.ui.widgets.LocalNativeLyricRenderCache
import yos.music.player.ui.widgets.LocalNativeLyricWarmup
import yos.music.player.ui.widgets.NativeLyricWarmStore
import yos.music.player.ui.widgets.NativeLyricWarmupHandle
import yos.music.player.ui.widgets.PrepareNativeLyrics
import yos.music.player.ui.widgets.PlayerArtworkBridge
import yos.music.player.ui.widgets.PlayerElementAnchors
import yos.music.player.ui.widgets.ALBUM_ARTWORK_ENDPOINT
import yos.music.player.ui.widgets.COMPACT_ARTWORK_ENDPOINT
import yos.music.player.ui.widgets.LANDSCAPE_ARTWORK_ENDPOINT
import yos.music.player.ui.widgets.LandscapePlayerLayout
import yos.music.player.ui.widgets.TabletPlayerLayout
import androidx.compose.ui.graphics.painter.Painter
import yos.music.player.ui.widgets.rememberArtworkPauseProgress
import yos.music.player.ui.widgets.artworkSharedFrame
import yos.music.player.ui.widgets.effects.YosFloatingLight
import yos.music.player.ui.widgets.audio.MusicQualityIndicator
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImageWithCache
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.YosWrapper
import yos.music.player.ui.widgets.effects.ShadowType
import yos.music.player.ui.widgets.effects.dropShadow
import yos.music.player.ui.widgets.effects.overlayEffect
import yos.music.player.logic.mss.VocalSeparationController
import yos.music.player.logic.mss.VocalSeparationStatus

private enum class NowPlayingMenuPage { More, PlaylistPicker, CreatePlaylist, Detail, SleepTimer }


@Stable
object NowPlayingPage {
    const val Album = "Album"
    const val PlayingList = "PlayingList"
    const val Lyric = "Lyric"
}

private const val AnimDurationMillis = 300

/*
private val MaterialFadeInTransitionSpec
    get() = SharedElementsTransitionSpec(
        pathMotionFactory = LinearMotionFactory,
        durationMillis = AnimDurationMillis,
        fadeMode = FadeMode.In,
        easing = EaseOutQuart
    )

private val MaterialFadeOutTransitionSpec
    get() = SharedElementsTransitionSpec(
        pathMotionFactory = LinearMotionFactory,
        durationMillis = AnimDurationMillis,
        fadeMode = FadeMode.Out,
        easing = EaseOutQuart
    )
*/

@ExperimentalSharedTransitionApi
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun NowPlaying(
    artwork: Painter,
    artworkBridge: PlayerArtworkBridge,
    mainViewModel: MainViewModel,
    mediaViewModel: MediaViewModel,
    navController: NavController,
    isPlayingStatusLambda: () -> Boolean,
    isPlayingOnChanged: (Boolean) -> Unit,
    nowPageLambda: () -> String,
    showMiniPlayer: () -> Boolean,
    closeSheet: () -> Unit = {},
    nowPageOnChanged: (String) -> Unit
) {
    val thisMusicPlaying = musicPlaying
    val headerAnchors = remember { PlayerElementAnchors() }
    val headerY = remember { Animatable(0f) }
    val headerInitialized = remember { mutableStateOf(false) }
    val albumHeaderY = headerAnchors.bounds["album"]?.center?.y
    val compactHeaderY = headerAnchors.bounds["compact"]?.center?.y
    val headerTarget = if (nowPageLambda() == Album) albumHeaderY ?: compactHeaderY else compactHeaderY ?: albumHeaderY
    Surface(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { headerAnchors.owner(it) },
        contentColor = Color.White,
        color = Color.Black
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowHeight = maxHeight.value
        val density = LocalDensity.current
        val windowInsets = LocalWindowInsets.current
        val view = LocalView.current
        val displayCutout = remember(view) { mutableStateOf(Insets.NONE) }
        DisposableEffect(view) {
            fun updateCutout() {
                val cutout = ViewCompat.getRootWindowInsets(view)?.displayCutout
                displayCutout.value = if (cutout == null) Insets.NONE else Insets.of(
                    cutout.safeInsetLeft, cutout.safeInsetTop, cutout.safeInsetRight, cutout.safeInsetBottom)
            }
            val listener = ViewTreeObserver.OnGlobalLayoutListener { updateCutout() }
            updateCutout()
            view.viewTreeObserver.addOnGlobalLayoutListener(listener)
            onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
        }
        val landscapePadding = with(density) {
            PaddingValues.Absolute(
                left = maxOf(windowInsets.navigationBars.left, displayCutout.value.left).toDp(),
                top = maxOf(windowInsets.statusBars.top, displayCutout.value.top).toDp(),
                right = maxOf(windowInsets.navigationBars.right, displayCutout.value.right).toDp(),
                bottom = maxOf(windowInsets.navigationBars.bottom, displayCutout.value.bottom).toDp()
            )
        }
        val fullyCollapsed by remember(artworkBridge) {
            derivedStateOf { artworkBridge.progress() == 0f }
        }
        val controlsHeightPx = remember { mutableIntStateOf(0) }
        val window = playerWindowLayout(maxWidth.value, windowHeight)
        val compactWindow = window.compact
        val splitWindow = window.split
        val tabletLandscape = window.tabletLandscape
        val tabletPage = remember(tabletLandscape) { mutableStateOf(Lyric) }
        LaunchedEffect(tabletLandscape, fullyCollapsed) {
            if (tabletLandscape && fullyCollapsed) tabletPage.value = Lyric
        }
        LaunchedEffect(splitWindow, headerTarget) {
            if (!splitWindow && headerTarget != null) {
                if (!headerInitialized.value) {
                    headerY.snapTo(headerTarget)
                    headerInitialized.value = true
                } else headerY.animateTo(headerTarget, spring(dampingRatio = 1f, stiffness = 360f))
            }
        }
        SideEffect {
            artworkBridge.expandedEndpoint = when {
                splitWindow -> LANDSCAPE_ARTWORK_ENDPOINT
                nowPageLambda() == Album -> ALBUM_ARTWORK_ENDPOINT
                else -> COMPACT_ARTWORK_ENDPOINT
            }
        }
        val mainTopPadding = if (compactWindow) 8.dp else 22.dp
        val mainContentHeight = (maxHeight - with(density) { windowInsets.statusBars.top.toDp() } - mainTopPadding)
            .coerceAtLeast(0.dp)
        val preferredAlbumHeight = mainContentHeight * if (splitWindow) 1f else if (compactWindow)
            (1f - 220f / windowHeight).coerceIn(0.25f, 0.595f) else 0.595f
        val availableMainHeight = if (!splitWindow && controlsHeightPx.intValue > 0) {
            (mainContentHeight - with(density) {
                (windowInsets.navigationBars.bottom + controlsHeightPx.intValue).toDp()
            } - 8.dp).coerceAtLeast(0.dp)
        } else mainContentHeight
        val albumHeight = minOf(preferredAlbumHeight, availableMainHeight)
        val albumPage = nowPageLambda() == Album
        val context = LocalContext.current
        val audioOutput = rememberPlaybackAudioOutput()
        val outputResolver = remember(context) { SystemMediaControlResolver(context) }
        val showOutputDialog = remember { mutableStateOf(false) }
        val seekCancelRegion = remember { SeekCancelArcHitRegion() }
        val seekDragging = remember(thisMusicPlaying.value?.uri) { mutableStateOf(false) }
        val seekInCancelArea = remember(thisMusicPlaying.value?.uri) { mutableStateOf(false) }
        val lyricSeekPreview = remember(thisMusicPlaying.value?.uri) { LyricSeekPreviewState() }
        val progressTrackKey = thisMusicPlaying.value?.uri?.toString()
        val playerProgress = rememberSaveable(progressTrackKey,
            key = "NowPlaying_progress_$progressTrackKey", saver = PlayerProgressState.Saver) {
            val initial = MediaController.playbackProgress()
            PlayerProgressState(initial.positionMs.toFloat(), initial.durationMs)
        }
        val translationVisible by SettingsLibrary.nowPlayingTranslationState
        val romajiVisible by SettingsLibrary.nowPlayingRomajiState

        val lyricState: MutableState<NowPlayingLyricState> =
            MediaViewModelObject.nowPlayingLyrics
        val lyricEntries = remember(lyricState) {
            derivedStateOf(structuralEqualityPolicy()) { lyricState.value.entries }
        }
        val backgroundState: MutableState<NowPlayingBackgroundState> =
            MediaViewModelObject.nowPlayingBackground

        val lastClickTime = rememberSaveable(key = "NowPlaying_lastClickTime") {
            mutableLongStateOf(0L)
        }

        val showArtistPickerFromAlbum = remember { mutableStateOf(false) }
        val showLyricShare = remember { mutableStateOf(false) }
        val lyricShareInitialIndex = remember { mutableIntStateOf(0) }
        val showControl = rememberSaveable(key = "NowPlaying_showControl") {
            mutableStateOf(true)
        }
        val lyricViewportInsetPx = remember { mutableIntStateOf(0) }
        val lyricContentReady = rememberSaveable(key = "NowPlaying_lyricContentReady") {
            mutableStateOf(!showMiniPlayer())
        }
        // 仓键含 generation：同曲目重开命中热缓存，歌词重新解析则换新仓
        val lyricStoreKey = "${lyricState.value.trackKey ?: progressTrackKey}:${lyricState.value.generation}"
        val lyricRenderCache = remember(lyricStoreKey) { NativeLyricWarmStore.cacheFor(lyricStoreKey) }
        val lyricWarmupHandle = remember(lyricStoreKey) { mutableStateOf<NativeLyricWarmupHandle?>(null) }
        PrepareNativeLyrics(
            cache = lyricRenderCache,
            entries = lyricEntries.value,
            lyricState = { lyricState.value },
            widthPx = if (splitWindow) {
                (with(density) { maxWidth.roundToPx() } -
                    maxOf(windowInsets.navigationBars.left, displayCutout.value.left) -
                    maxOf(windowInsets.navigationBars.right, displayCutout.value.right))
                    .coerceAtLeast(0) / 2
            } else with(density) { maxWidth.roundToPx() },
            allowed = {
                val progress = artworkBridge.progress()
                (progress == 0f || progress == 1f) && !showLyricShare.value
            },
            warmupState = lyricWarmupHandle
        )
        
        // 监听 shouldShowWarning 状态变化(定时关闭警告)
        YosWrapper {
            LaunchedEffect(yos.music.player.code.SleepTimerManager.shouldShowWarning.value) {
                if (yos.music.player.code.SleepTimerManager.shouldShowWarning.value && 
                    yos.music.player.code.SleepTimerManager.isActive.value) {
                    // 通过全局事件或状态传递到菜单区域
                    // 这里简化处理,直接通过全局状态控制
                }
            }
        }

        val shuffleModeEnabled = rememberSaveable(key = "NowPlaying_shuffleModeEnabled") {
            mutableStateOf(shuffleEnabled.value)
        }
        // 启动恢复是异步的：本地镜像若只在初始化时取值（onWhile 又来得晚），
        // 随机模式下首开队列页会先按"非随机"显示列表、恢复完成后才跳回空态。
        // 让镜像逐帧跟随全局状态，恢复落地的瞬间即同步。
        LaunchedEffect(Unit) {
            snapshotFlow { shuffleEnabled.value }.collect { shuffleModeEnabled.value = it }
        }
        val repeatMode = rememberSaveable(key = "NowPlaying_repeatMode") {
            mutableIntStateOf(mediaControl?.repeatMode ?: REPEAT_MODE_OFF)
        }
        // 循环模式没有全局 Compose 状态（随机有 shuffleEnabled）：启动恢复、
        // 通知栏与播放页都可改它。低频轮询控制器真实值，让镜像始终跟上，
        // 修复恢复完成前首开队列页按钮先显示未激活再跳变。
        LaunchedEffect(Unit) {
            while (isActive) {
                val current = mediaControl?.repeatMode ?: REPEAT_MODE_OFF
                if (repeatMode.intValue != current) repeatMode.intValue = current
                delay(500)
            }
        }


        /*val nowPage = rememberSaveable(key = "NowPlaying_nowPage") {
            MainViewModelObject.nowPage
        }*/


        // 触摸超时：仅在歌词页面、播放中且开启自动隐藏时隐藏控件
        YosWrapper {
            LaunchedEffect(splitWindow, showControl.value, nowPageLambda(), lastClickTime.longValue,
                isPlayingStatusLambda()) {
                if (splitWindow) return@LaunchedEffect
                if (nowPageLambda() != Lyric && !showControl.value) {
                    showControl.value = true
                    return@LaunchedEffect
                }
                // 暂停时控制区域保持展示：已隐藏则唤醒，且不参与自动隐藏
                if (!isPlayingStatusLambda()) {
                    if (nowPageLambda() == Lyric && !showControl.value) {
                        showControl.value = true
                    }
                    return@LaunchedEffect
                }
                if (showControl.value && nowPageLambda() == Lyric && SettingsLibrary.LyricsHideControls) {
                    val time = 2500L
                    delay(time)
                    withContext(Dispatchers.Main) {
                        if (TimeUtils.getNowMills() - lastClickTime.longValue >= time && nowPageLambda() == Lyric
                            && isPlayingStatusLambda()) {
                            showControl.value = false
                        }
                    }
                }
            }
        }

        YosWrapper {
            LaunchedEffect(splitWindow, showMiniPlayer(), nowPageLambda()) {
                if (splitWindow) return@LaunchedEffect
                if (nowPageLambda() == Lyric) {
                    lyricContentReady.value = true
                    return@LaunchedEffect
                }
                if (!showMiniPlayer() && !lyricContentReady.value) {
                    delay(AnimDurationMillis.toLong() + 120L)
                    if (!showMiniPlayer()) {
                        lyricContentReady.value = true
                    }
                }
            }
        }


        // 背景流光
        YosWrapper {
            /*BlendBackgroundView(
        bitmapLambda = { bitmap.value },
        isPlayingLambda = { isPlaying.value },
        nowPage = { nowPage.value }
    )*/

            YosFloatingLight(
                  backgroundState = { backgroundState.value },
                isPlaying = isPlayingStatusLambda,
                modifier = Modifier.fillMaxSize(),
                nowPage = { nowPageLambda() },
                showMiniPlayer = showMiniPlayer,
                expansionProgress = { artworkBridge.progress() }
            )
        }


        // 实际显示区
        YosWrapper {
            /*
        val controlAlpha = animateFloatAsState(
            targetValue = if (showControl.value) 1f else 0f,
            tween(200)
        )

        val buttonEnabled = remember("NowPlaying_buttonEnabled") {
            derivedStateOf { controlAlpha.value != 0f }
        }

        val translationButtonEnabled = remember("NowPlaying_translationButtonEnabled") {
            derivedStateOf { buttonEnabled.value && alpha.value != 0f }
        }*/

            val artworkPainter = artwork
            val artworkPauseProgress = rememberArtworkPauseProgress(isPlayingStatusLambda())
            SideEffect { artworkBridge.pauseProgress = { artworkPauseProgress.value } }

            fun requestNowPage(targetPage: String) {
                if (nowPageLambda() != targetPage) nowPageOnChanged(targetPage)
            }

            val alphaAnim = remember { Animatable(0f) }

            YosWrapper {
                LaunchedEffect(splitWindow, nowPageLambda()) {
                    if (splitWindow) return@LaunchedEffect
                    val targetAlpha = if (nowPageLambda() == Lyric) 1f else 0f
                    alphaAnim.animateTo(targetAlpha, tween(210, easing = FastOutSlowInEasing))
                }
            }

            val lyricRenderActive = remember(artworkBridge, splitWindow) {
                derivedStateOf(structuralEqualityPolicy()) {
                    !splitWindow && artworkBridge.progress() > 0f && !showLyricShare.value &&
                        (nowPageLambda() == Lyric || alphaAnim.value > 0f)
                }
            }
            val hasTranslation = remember(lyricEntries) {
                derivedStateOf {
                    lyricEntries.value.any { line ->
                        line.translation.isNotBlank() || line.otherLrcEntries.any { it.translation.isNotBlank() }
                    }
                }
            }


            if (tabletLandscape) {
                val interactive = !showMiniPlayer() && !showLyricShare.value
                TabletPlayerLayout(
                    interactionEnabled = interactive,
                    onBack = {
                        if (tabletPage.value == PlayingList) tabletPage.value = Lyric else closeSheet()
                    },
                    modifier = Modifier.fillMaxSize().padding(landscapePadding),
                    artwork = {
                        BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Spacer(Modifier.size(minOf(maxWidth, maxHeight))
                                .onGloballyPositioned { artworkBridge.record(LANDSCAPE_ARTWORK_ENDPOINT, it) })
                        }
                    },
                    controls = {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(thisMusicPlaying.value?.title ?: defaultTitle,
                                        fontSize = 19.5.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(thisMusicPlaying.value?.artistsName ?: defaultArtistsName,
                                        fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.35f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                ActionButtonsRow(navController = navController, closeSheet = closeSheet,
                                    musicPlayingLambda = { thisMusicPlaying.value })
                            }
                            PlayerControl(
                                progress = playerProgress, audioOutput = audioOutput,
                                onOutputDeviceClick = {
                                    Vibrator.click(context)
                                    if (!outputResolver.intentSystemMediaDialog()) {
                                        audioOutput.refresh()
                                        showOutputDialog.value = true
                                    }
                                },
                                interactionEnabled = interactive,
                                seekPreviewState = lyricSeekPreview, cancelAreaContains = seekCancelRegion::contains,
                                onSeekGestureChange = { dragging, cancelled ->
                                    seekDragging.value = dragging
                                    seekInCancelArea.value = cancelled
                                },
                                renderActive = interactive,
                                isPlayingLambda = isPlayingStatusLambda,
                                isPlayingOnChanged = isPlayingOnChanged,
                                onPrevious = { mediaControl?.seekToPreviousMediaItem() },
                                previousEnabled = !MediaController.wanderMode.value,
                                onStatus = { status ->
                                    if (status) mediaControl?.fadePlay() else mediaControl?.fadePause()
                                },
                                onNext = { MediaController.skipToNext() },
                                onSeek = { mediaControl?.seekTo(it.toLong()) },
                                onLyrics = { tabletPage.value = Lyric },
                                onPlaylist = {
                                    tabletPage.value = if (tabletPage.value == PlayingList) Lyric else PlayingList
                                },
                                nowPage = { tabletPage.value }, onSlider = {},
                                onWhile = {
                                    shuffleModeEnabled.value = shuffleEnabled.value
                                    repeatMode.intValue = mediaControl?.repeatMode ?: REPEAT_MODE_OFF
                                },
                                modifier = Modifier
                            )
                        }
                        SeekCancelArc(visible = interactive && seekDragging.value, cancelled = seekInCancelArea.value,
                            compact = false, region = seekCancelRegion,
                            modifier = Modifier.align(Alignment.BottomCenter))
                    },
                    content = {
                        // 裸 if 交换会让队列整块内容一帧内以全透明度出现（跳变），
                        // 与竖屏一致改用 210ms 交叉淡入。
                        AnimatedContent(
                            targetState = tabletPage.value == PlayingList,
                            transitionSpec = {
                                (fadeIn(tween(210)) togetherWith fadeOut(tween(210)))
                                    .using(SizeTransform(clip = false))
                            }
                        ) { showQueue ->
                            if (showQueue) {
                                PlayingList(
                                    navController = navController,
                                    shuffleModeEnabledLambda = { shuffleModeEnabled.value },
                                    shuffleModeOnChanged = { shuffleModeEnabled.value = it },
                                    repeatModeLambda = { repeatMode.intValue },
                                    repeatModeOnChanged = { repeatMode.intValue = it },
                                    thisMusicPlayingLambda = { thisMusicPlaying.value },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(Modifier.fillMaxSize()) {
                                    // 顶/底渐隐：歌词滚出分栏边缘与翻译/人声工具行交界处均淡出
                                    Box(
                                        Modifier.weight(1f).fillMaxWidth().clipToBounds()
                                            .verticalEdgeFade(bottomFade = 20.dp)
                                    ) {
                                        CompositionLocalProvider(LocalNativeLyricRenderCache provides lyricRenderCache,
            LocalNativeLyricWarmup provides lyricWarmupHandle) {
                                            Lyric(
                                                lyricState = { lyricState.value }, weightLambda = { false },
                                                onViewportInsetChanged = {}, splitWindow = true, insetsHandled = true,
                                                translationLambda = { translationVisible }, romajiLambda = { romajiVisible }, seekPreviewState = lyricSeekPreview,
                                                shouldRenderContent = true, mainViewModel = mainViewModel,
                                                mediaViewModel = mediaViewModel, showMiniPlayer = showMiniPlayer,
                                                userScrollEnabled = interactive, renderActive = interactive,
                                                onLineLongPress = { index ->
                                                    lyricShareInitialIndex.intValue = index
                                                    showLyricShare.value = true
                                                },
                                                onBackClick = {}
                                            )
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth().padding(20.dp),
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                                        if (hasTranslation.value) LyricTranslationButton(translationVisible, {
                                            SettingsLibrary.NowPlayingTranslation = it
                                            Vibrator.click(context)
                                        })
                                        VocalSeparationControl(context = context, onSlider = {})
                                    }
                                }
                            }
                        }
                    }
                )
            } else if (splitWindow) {
                LandscapePlayerLayout(
                    interactionEnabled = !showMiniPlayer() && !showLyricShare.value,
                    onClose = closeSheet,
                    modifier = Modifier.fillMaxSize().padding(landscapePadding),
                    fullyCollapsed = fullyCollapsed,
                    artwork = {
                        BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                            Spacer(Modifier.size(minOf(maxWidth, maxHeight))
                                .onGloballyPositioned { artworkBridge.record(LANDSCAPE_ARTWORK_ENDPOINT, it) })
                        }
                    },
                    controls = { visible ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 12.dp)) {
                                Text(thisMusicPlaying.value?.title ?: defaultTitle,
                                    fontSize = 19.5.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(thisMusicPlaying.value?.artistsName ?: defaultArtistsName,
                                    fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.35f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            PlayerControl(
                                progress = playerProgress, audioOutput = audioOutput,
                                onOutputDeviceClick = {}, compactWindow = true, showBottomActions = false,
                                interactionEnabled = visible,
                                seekPreviewState = lyricSeekPreview, cancelAreaContains = seekCancelRegion::contains,
                                onSeekGestureChange = { dragging, cancelled ->
                                    seekDragging.value = dragging
                                    seekInCancelArea.value = cancelled
                                },
                                renderActive = visible,
                                isPlayingLambda = isPlayingStatusLambda,
                                isPlayingOnChanged = isPlayingOnChanged,
                                onPrevious = { mediaControl?.seekToPreviousMediaItem() },
                                previousEnabled = !MediaController.wanderMode.value,
                                onStatus = { status ->
                                    if (status) mediaControl?.fadePlay() else mediaControl?.fadePause()
                                },
                                onNext = { MediaController.skipToNext() },
                                onSeek = { mediaControl?.seekTo(it.toLong()) },
                                onLyrics = {}, onPlaylist = {}, nowPage = { Album },
                                onSlider = {}, onWhile = {}, modifier = Modifier
                            )
                        }
                        SeekCancelArc(visible = visible && seekDragging.value, cancelled = seekInCancelArea.value,
                            compact = true, region = seekCancelRegion,
                            modifier = Modifier.align(Alignment.BottomCenter))
                    },
                    lyrics = { visible ->
                        // 歌词滚出分栏边缘时渐隐，与竖屏 clipPlayerLyrics 的顶/底过渡一致
                        Box(Modifier.fillMaxSize().clipToBounds().verticalEdgeFade()) {
                            CompositionLocalProvider(LocalNativeLyricRenderCache provides lyricRenderCache,
            LocalNativeLyricWarmup provides lyricWarmupHandle) {
                                Lyric(
                                    lyricState = { lyricState.value }, weightLambda = { false },
                                    onViewportInsetChanged = {}, compactWindow = true, splitWindow = true, insetsHandled = true,
                                    translationLambda = { translationVisible }, romajiLambda = { romajiVisible }, seekPreviewState = lyricSeekPreview,
                                    shouldRenderContent = true, mainViewModel = mainViewModel,
                                    mediaViewModel = mediaViewModel, showMiniPlayer = showMiniPlayer,
                                    userScrollEnabled = visible, renderActive = visible,
                                    onLineLongPress = { index ->
                                        lyricShareInitialIndex.intValue = index
                                        showLyricShare.value = true
                                },
                                onBackClick = {}
                                )
                            }
                        }
                    }
                )
            } else {
            // 歌词
            YosWrapper {

                Column(
                    Modifier
                        .playerWindowPane(if (splitWindow) 0.5f else 0f, if (splitWindow) 0.5f else 1f)
                        .fillMaxSize()
                        .clipPlayerLyrics(
                            enabled = { !splitWindow && showControl.value },
                            controlsTop = { headerAnchors.bounds["controls"]?.top },
                            lyricsTop = { lyricViewportInsetPx.intValue.takeIf { it > 0 }?.toFloat() }
                        )
                        .graphicsLayer {
                            compositingStrategy =
                                CompositingStrategy.ModulateAlpha
                            this.alpha = alphaAnim.value
                            val contentScale = 0.97f + 0.03f * alphaAnim.value
                            scaleX = contentScale
                            scaleY = contentScale
                            transformOrigin = TransformOrigin(0.5f, 0.15f)
                            val compact = headerAnchors.bounds["compact"]?.center?.y
                            translationY = if (!splitWindow && compact != null && headerInitialized.value) headerY.value - compact else 0f
                        }
                ) {

                    CompositionLocalProvider(LocalNativeLyricRenderCache provides lyricRenderCache,
            LocalNativeLyricWarmup provides lyricWarmupHandle) {
                    Lyric(
                          lyricState = { lyricState.value },
                        weightLambda = { !splitWindow && showControl.value },
                        onViewportInsetChanged = { lyricViewportInsetPx.intValue = it },
                        compactWindow = compactWindow,
                        splitWindow = splitWindow,
                        translationLambda = { translationVisible },
                        romajiLambda = { romajiVisible },
                        seekPreviewState = lyricSeekPreview,
                          shouldRenderContent = lyricContentReady.value || nowPageLambda() == Lyric,
                        showMiniPlayer = showMiniPlayer,
                        userScrollEnabled = nowPageLambda() == NowPlayingPage.Lyric && !showMiniPlayer(),
                        renderActive = lyricRenderActive.value,
                        onLineLongPress = { index ->
                            lyricShareInitialIndex.intValue = index
                            showLyricShare.value = true
                        },
                        onBackClick = {
                            showControl.value = true
                            lastClickTime.longValue =
                                TimeUtils.getNowMills()
                        },
                        mainViewModel = mainViewModel,
                        mediaViewModel = mediaViewModel
                    )
                    }
                }
            }

            // 这是小把手
            YosWrapper {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 20.dp), contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .overlayEffect()
                                .size(
                                    width = 32.dp,
                                    height = 4.5.dp
                                )
                                .background(Color(0x4DFFFFFF), RoundedCornerShape(2.25.dp))
                                .clip(RoundedCornerShape(2.25.dp))
                        )
                    }
                }
            }

            // 主 View
            YosWrapper {
                SharedTransitionLayout {
                    AnimatedContent(
                        transitionSpec = {
                            (fadeIn(tween(210)) togetherWith fadeOut(tween(210))).using(SizeTransform(clip = false))
                        },
                        // The artwork has exactly two endpoints: album and compact.
                        targetState = nowPageLambda() == Album,
                        modifier = Modifier
                            .playerWindowPane(0f, if (splitWindow) 0.5f else 1f)
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = mainTopPadding)
                    ) {
                        val visibilityScope = this
                        val sharedHeader: @Composable (String) -> Modifier = { key ->
                            playerHeaderModifier(key, visibilityScope)
                        }
                        when (it) {
                            true ->
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .clickable(enabled = false, onClick = {})
                                ) {
                                    YosWrapper {
                                        Column(Modifier.height(albumHeight)) {
                                            // Shared element 的可见性只跟页内状态走，避免播放页展开动画未完全 settle 时
                                            // showMiniPlayer() 仍为 true，导致从歌词页快速返回封面页缺少共享元素动画。
                                            val isVisible = nowPageLambda() == Album

                                            Album(
                                                modifier = Modifier,
                                                artwork = artworkPainter,
                                                artworkBridge = artworkBridge,
                                                compactWindow = compactWindow
                                            )
                                            AnimatedContent(
                                                targetState = thisMusicPlaying.value,
                                                contentKey = { Unit },
                                                transitionSpec = {
                                                    fadeIn() togetherWith fadeOut()
                                                }, modifier = Modifier.padding(horizontal = rememberAdaptive(32))
                                            ) {
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .weight(1f)
                                                            .padding(end = rememberAdaptive(15))
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clickable(
                                                                    indication = null,
                                                                    interactionSource = remember { MutableInteractionSource() }
                                                                ) {
                                                                    it?.album?.let { album ->
                                                                        yos.music.player.data.objects.LibraryObject.setTargetAlbumName(album)
                                                                        closeSheet()
                                                                        navController.toUI(UI.AlbumInfo)
                                                                    }
                                                                }
                                                        ) {
                                                            Text(
                                                                modifier = sharedHeader("title"),
                                                                text = it?.title
                                                                    ?: defaultTitle,
                                                                fontSize = 19.5.sp,
                                                                lineHeight = 26.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .clickable(
                                                                    indication = null,
                                                                    interactionSource = remember { MutableInteractionSource() }
                                                                ) {
                                                                    val artists = it?.artistsList ?: emptyList()
                                                                    if (artists.size > 1) {
                                                                        showArtistPickerFromAlbum.value = true
                                                                    } else {
                                                                        artists.firstOrNull()?.let { artist ->
                                                                            yos.music.player.data.objects.LibraryObject.setTargetArtistName(artist)
                                                                            closeSheet()
                                                                            navController.toUI(UI.ArtistInfo)
                                                                        }
                                                                    }
                                                                }
                                                        ) {
                                                            Text(
                                                                modifier = sharedHeader("artist"),
                                                                text = it?.artistsName
                                                                    ?: defaultArtistsName,
                                                                fontSize = 18.5.sp,
                                                                lineHeight = 24.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = Color.White.copy(alpha = 0.35f),
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                    }

                                                    YosWrapper {
                                                        ActionButtonsRow(
                                                            navController = navController, closeSheet = closeSheet,
                                                            modifier = Modifier.onGloballyPositioned { headerAnchors.record("album", it) },
                                                            favoriteModifier = sharedHeader("favorite"),
                                                            menuModifier = sharedHeader("menu")
                                                        ) {
                                                            it
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                            false ->
                                Box(Modifier.fillMaxSize()) {
                                    Column(Modifier.fillMaxWidth().height(availableMainHeight)) {
                                        PlayingBar(
                                            modifier = Modifier,
                                            artwork = artworkPainter,
                                            artworkBridge = artworkBridge,
                                            compactWindow = compactWindow,
                                            titleModifier = sharedHeader("title"),
                                            artistModifier = sharedHeader("artist"),
                                            favoriteModifier = sharedHeader("favorite"),
                                            menuModifier = sharedHeader("menu"),
                                            actionsModifier = Modifier.onGloballyPositioned { headerAnchors.record("compact", it) },
                                            musicPlayingLambda = { thisMusicPlaying.value }
                                        ) { requestNowPage(Album) }
                                        if (!splitWindow) PlayerContentVisibility(
                                            nowPageLambda() == PlayingList,
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                        ) {
                                            PlayingList(
                                                navController = navController,
                                                shuffleModeEnabledLambda = { shuffleModeEnabled.value },
                                                shuffleModeOnChanged = { shuffleModeSet ->
                                                    shuffleModeEnabled.value = shuffleModeSet
                                                },
                                                repeatModeLambda = { repeatMode.intValue },
                                                repeatModeOnChanged = { repeatModeSet ->
                                                    repeatMode.intValue = repeatModeSet
                                                },
                                                thisMusicPlayingLambda = { thisMusicPlaying.value },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                        }
                    }
                }
            }

            if (splitWindow) {
                PlayerContentVisibility(
                    visible = nowPageLambda() == PlayingList,
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                        .playerWindowPane(0.5f, 0.5f)
                ) {
                    PlayingList(
                        navController = navController,
                        shuffleModeEnabledLambda = { shuffleModeEnabled.value },
                        shuffleModeOnChanged = { shuffleModeEnabled.value = it },
                        repeatModeLambda = { repeatMode.intValue },
                        repeatModeOnChanged = { repeatMode.intValue = it },
                        thisMusicPlayingLambda = { thisMusicPlaying.value },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 音乐控制
            YosWrapper {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .playerWindowPane(if (splitWindow && albumPage) 0.5f else 0f, if (splitWindow) 0.5f else 1f),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(Modifier.fillMaxWidth()) playerOverlayBox@ {

                        YosWrapper {
                            if (showControl.value && !compactWindow) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .padding(top = 42.dp, bottom = 20.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { })
                                )
                            }
                        }

                        YosWrapper {
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                AnimatedVisibility(
                                    visible = showControl.value,
                                    enter = fadeIn(tween(durationMillis = 200, easing = EaseOutQuart)) + expandVertically(
                                        animationSpec = tween(durationMillis = 200, easing = EaseOutQuart),
                                        expandFrom = Alignment.Top,
                                        initialHeight = { (it / 1.4).toInt() }),
                                    exit = fadeOut(tween(durationMillis = 200, easing = EaseOutQuart)) + shrinkVertically(
                                        animationSpec = tween(durationMillis = 200, easing = EaseOutQuart),
                                        shrinkTowards = Alignment.Top,
                                        targetHeight = { (it / 1.4).toInt() })
                                ) {
                                    PlayerControl(
                                        progress = playerProgress,
                                        audioOutput = audioOutput,
                                        onOutputDeviceClick = {
                                            lastClickTime.longValue = TimeUtils.getNowMills()
                                            Vibrator.click(context)
                                            if (!outputResolver.intentSystemMediaDialog()) {
                                                audioOutput.refresh()
                                                showOutputDialog.value = true
                                            }
                                        },
                                        compactWindow = compactWindow,
                                        seekPreviewState = lyricSeekPreview,
                                        cancelAreaContains = seekCancelRegion::contains,
                                        onSeekGestureChange = { dragging, cancelled ->
                                            seekDragging.value = dragging
                                            seekInCancelArea.value = cancelled
                                        },
                                        renderActive = !showMiniPlayer(),
                                        isPlayingLambda = isPlayingStatusLambda,
                                        isPlayingOnChanged = isPlayingOnChanged,
                                        onPrevious = {
                                            mediaControl?.seekToPreviousMediaItem()
                                            showControl.value = true
                                            lastClickTime.longValue = TimeUtils.getNowMills()
                                        },
                                        previousEnabled = !MediaController.wanderMode.value,
                                        onStatus = { status ->
                                            if (status) {
                                                mediaControl?.fadePlay()
                                            } else {
                                                mediaControl?.fadePause()
                                            }
                                            showControl.value = true
                                            lastClickTime.longValue = TimeUtils.getNowMills()
                                        },
                                        onNext = {
                                            MediaController.skipToNext()
                                            showControl.value = true
                                            lastClickTime.longValue = TimeUtils.getNowMills()
                                        },
                                        onSeek = { position ->
                                            mediaControl?.seekTo(position.toLong())
                                        },
                                        onLyrics = {
                                              requestNowPage(if (nowPageLambda() == Lyric) Album else Lyric)
                                        },
                                        onPlaylist = {
                                              requestNowPage(if (nowPageLambda() == PlayingList) Album else PlayingList)
                                        },
                                        nowPage = {
                                            nowPageLambda()
                                        },
                                        onSlider = {
                                            showControl.value = true
                                            lastClickTime.longValue = TimeUtils.getNowMills()
                                        },
                                        modifier = Modifier
                                            /*.graphicsLayer {
                                                compositingStrategy =
                                                    CompositingStrategy.Offscreen
                                                //this.alpha = controlAlpha.value
                                            }*/
                                            .padding(top = if (compactWindow) 0.dp else rememberAdaptive(52))
                                            .onSizeChanged { controlsHeightPx.intValue = it.height }
                                            .onGloballyPositioned { headerAnchors.record("controls", it) },
                                        onWhile = {
                                            shuffleModeEnabled.value =
                                                shuffleEnabled.value
                                            repeatMode.intValue =
                                                mediaControl?.repeatMode ?: REPEAT_MODE_OFF
                                        })
                                }
                            }
                        }

                        // The APK places MSS as a sibling overlay of the whole
                        // control panel. Its expansion therefore grows upward
                        // without participating in PlayerControl measurement.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showControl.value && nowPageLambda() == Lyric,
                            enter = fadeIn(tween(200, easing = EaseOutQuart)) + slideInVertically(
                                animationSpec = tween(200, easing = EaseOutQuart),
                                initialOffsetY = { it / 4 }),
                            exit = fadeOut(tween(160, easing = EaseOutQuart)) + slideOutVertically(
                                animationSpec = tween(160, easing = EaseOutQuart),
                                targetOffsetY = { it / 4 }),
                            modifier = with(this@playerOverlayBox) {
                                Modifier.align(Alignment.BottomEnd)
                            }.offset(
                                x = -rememberAdaptive(25),
                                y = -if (compactWindow) {
                                    if (SettingsLibrary.NowPlayingShowVolumeBar) 184.dp else 157.5.dp
                                } else if (SettingsLibrary.NowPlayingShowVolumeBar) 300.dp else 273.5.dp
                            )
                        ) {
                            Row(verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (hasTranslation.value) {
                                    LyricTranslationButton(translationVisible, {
                                        SettingsLibrary.NowPlayingTranslation = it
                                        Vibrator.click(context)
                                        lastClickTime.longValue = TimeUtils.getNowMills()
                                    })
                                }
                                if (!compactWindow) VocalSeparationControl(
                                    context = context,
                                    onSlider = {
                                        showControl.value = true
                                        lastClickTime.longValue = TimeUtils.getNowMills()
                                    }
                                )
                            }
                        }
                    }
                }
            }

        }

        }
        if (!splitWindow) SeekCancelArc(visible = seekDragging.value, cancelled = seekInCancelArea.value,
            compact = compactWindow, region = seekCancelRegion,
            modifier = Modifier.align(Alignment.BottomCenter))

        if (showOutputDialog.value) {
            PlaybackOutputDialog(audioOutput.devices, onDismiss = { showOutputDialog.value = false })
        }

        // 封面页多歌手选择
        if (showArtistPickerFromAlbum.value && thisMusicPlaying.value != null) {
            val m = thisMusicPlaying.value!!
            val artists = m.artistsList ?: emptyList()
            OptionDialog(
                icon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShadowImageWithCache(
                            dataLambda = { m.thumb }, contentDescription = null,
                            modifier = Modifier.size(56.dp), cornerRadius = 8.dp,
                            shadowAlpha = 0f, imageQuality = ImageQuality.LOW
                        )
                    }
                },
                title = m.title ?: defaultTitle,
                subTitle = m.artistsName ?: defaultArtistsName,
                horizontalTitle = true,
                content = { dismiss ->
                    Column(
                        Modifier.clip(YosRoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.onSecondary)
                    ) {
                        artists.forEachIndexed { index, artist ->
                            if (index > 0) Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                            Row(
                                Modifier.fillMaxWidth().height(48.dp).clickable {
                                    dismiss.after {
                                        yos.music.player.data.objects.LibraryObject.setTargetArtistName(artist)
                                        closeSheet()
                                        navController.toUI(UI.ArtistInfo)
                                    }
                                }.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(artist, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                },
                onDismissRequest = { showArtistPickerFromAlbum.value = false }
            )
        }

        if (showLyricShare.value) {
            LyricShareSheet(
                entries = lyricEntries.value,
                initialIndex = lyricShareInitialIndex.intValue,
                music = thisMusicPlaying.value,
                backgroundArtworkUri = backgroundState.value.artworkUri,
                onDismiss = { showLyricShare.value = false }
            )
        }

        }
    }
}

@Composable
private fun ColumnScope.Album(
    modifier: Modifier,
    artwork: Painter,
    artworkBridge: PlayerArtworkBridge,
    compactWindow: Boolean = false
) = BoxWithConstraints(
    Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(top = if (compactWindow) 4.dp else 32.dp)
        .padding(horizontal = 15.dp)
        .padding(bottom = if (compactWindow) 8.dp else 21.dp),
    contentAlignment = Alignment.BottomCenter
) {
    val albumShape = YosRoundedCornerShape(8)

    YosWrapper {
        Box(
            Modifier
                .size(minOf(maxWidth, maxHeight))
                .artworkSharedFrame(modifier) { 0f }
                .shadow(
                    elevation = 0.dp, shape = albumShape, clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.36f),
                    spotColor = Color.Black.copy(alpha = 0.36f)
                )
        ) {
            Image(
                painter = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(albumShape)
                    .onGloballyPositioned { artworkBridge.record(ALBUM_ARTWORK_ENDPOINT, it) }
                    .graphicsLayer { alpha = 0f }
            )
        }
    }
}


/**
 * 队列页随机/循环按钮图标：未激活为普通白图标；激活时画白色圆角底并
 * 把字形从底里抠空——镂空处透出流动背景，替代原先的黑色字形。
 * [activeProgress] 随底色动画 0..1，交叉过渡避免突变。
 */
@Composable
private fun QueueLoopIcon(painter: Painter, activeProgress: Float) {
    // 白底沿用应用统一的平滑圆角（连续曲率），与未激活态及其他按钮一致
    val shape = remember { YosRoundedCornerShape(10.dp) }
    Canvas(Modifier.size(36.dp)) {
        val progress = activeProgress.coerceIn(0f, 1f)
        val filter = ColorFilter.tint(Color.White)
        val icon = Size(36.dp.toPx(), 36.dp.toPx())
        if (progress < 1f) {
            with(painter) { draw(icon, alpha = 1f - progress, colorFilter = filter) }
        }
        if (progress > 0f) {
            drawContext.canvas.saveLayer(
                Rect(0f, 0f, size.width, size.height), Paint()
            )
            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Generic -> drawPath(
                    outline.path, color = Color.White.copy(alpha = 0.9f * progress)
                )
                is Outline.Rounded -> drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f * progress),
                    cornerRadius = outline.roundRect.topLeftCornerRadius
                )
                is Outline.Rectangle -> drawRect(
                    color = Color.White.copy(alpha = 0.9f * progress)
                )
            }
            // 抠字形：仅作用于上一个 saveLayer 内的白底，不触碰其后的流动背景
            drawContext.canvas.saveLayer(
                Rect(0f, 0f, size.width, size.height),
                Paint().apply { blendMode = BlendMode.DstOut }
            )
            with(painter) { draw(icon, alpha = progress, colorFilter = filter) }
            drawContext.canvas.restore()
            drawContext.canvas.restore()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayingList(
    navController: NavController,
    shuffleModeEnabledLambda: () -> Boolean,
    shuffleModeOnChanged: (Boolean) -> Unit,
    repeatModeLambda: () -> Int,
    repeatModeOnChanged: (Int) -> Unit,
    thisMusicPlayingLambda: () -> YosMediaItem?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val musicList = remember("PlayingList_musicList") {
        playingMusicList
    }
    val wanderMode = MediaController.wanderMode.value
    val shuffleMode = shuffleModeEnabledLambda() && !wanderMode
    val hide = wanderMode || musicList.value.isNullOrEmpty() || shuffleMode

    YosWrapper {
        Column(modifier.clipToBounds()) {
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rememberAdaptive(30))
                    .padding(top = 10.dp)
                    .height(65.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        text = stringResource(if (wanderMode) R.string.playlist_wander_title else R.string.page_library_playlists),
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (!wanderMode) Text(
                        text = stringResource(
                            id = R.string.page_library_playlists_music_total,
                            musicList.value?.size ?: 0
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        // 入场淡入期间 Plus 混合层无法与真实背景合成，动画结束瞬间
                        // 亮度突跳（开页闪烁）；此处与下行控件改普通 alpha 分级
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .alpha(0.55f)
                    )
                }

                Row(
                    modifier = Modifier
                        .alpha(if (wanderMode) 0.35f else 0.95f)
                ) {
                    val dp = 36.dp
                    YosWrapper {
                        val shuffleBackgroundAlpha =
                            animateFloatAsState(targetValue = if (shuffleMode) 0.9f else 0f)
                        Box(
                            modifier = Modifier
                                .clickable(
                                    enabled = !wanderMode,
                                    onClick = {
                                        if (!MediaController.wanderMode.value) {
                                            Vibrator.click(context)
                                            toggleShuffle()
                                            shuffleModeOnChanged(!shuffleModeEnabledLambda())
                                        }
                                    },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() })
                                .size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            QueueLoopIcon(
                                painter = painterResource(id = R.drawable.ic_nowplaying_shuffle),
                                activeProgress = shuffleBackgroundAlpha.value / 0.9f
                            )
                        }
                    }
                    YosWrapper {
                        val repeatHighlight = !wanderMode &&
                            (repeatModeLambda() == REPEAT_MODE_ALL || repeatModeLambda() == REPEAT_MODE_ONE)
                        val repeatBackgroundAlpha =
                            animateFloatAsState(targetValue = if (repeatHighlight) 0.9f else 0f)
                        val repeatPainter = if ((if (wanderMode) REPEAT_MODE_OFF else repeatModeLambda()) == REPEAT_MODE_ONE) {
                            painterResource(id = R.drawable.ic_nowplaying_repeatone)
                        } else {
                            painterResource(id = R.drawable.ic_nowplaying_repeat)
                        }
                        Box(
                            modifier = Modifier
                                .clickable(
                                    enabled = !wanderMode,
                                    onClick = {
                                        if (!MediaController.wanderMode.value) {
                                            Vibrator.click(context)
                                            val targetMode = when (repeatModeLambda()) {
                                                REPEAT_MODE_OFF -> REPEAT_MODE_ALL
                                                REPEAT_MODE_ALL -> REPEAT_MODE_ONE
                                                else -> REPEAT_MODE_OFF
                                            }
                                            mediaControl?.repeatMode = targetMode
                                            mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
                                            repeatModeOnChanged(targetMode)
                                        }
                                    },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() })
                                .padding(start = 10.dp)
                                .size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            QueueLoopIcon(
                                painter = repeatPainter,
                                activeProgress = repeatBackgroundAlpha.value / 0.9f
                            )
                        }
                    }
                }
            }


            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            if (hide) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_uitabbar_library),
                        contentDescription = null,
                        modifier = Modifier
                            .size(70.dp)
                            .alpha(0.95f)
                    )
                    Text(
                        text = stringResource(if (wanderMode) R.string.playlist_wander_title else R.string.playlist_unavailable_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
                    )
                    YosWrapper {
                        val message = when {
                            wanderMode -> R.string.playlist_wander_desc
                            musicList.value.isNullOrEmpty() -> R.string.playlist_unavailable_desc
                            else -> R.string.playlist_shuffle_desc
                        }
                        Text(
                            text = stringResource(message),
                            fontSize = 16.sp,
                            color = Color.White,
                            modifier = Modifier
                                .alpha(0.6f)
                        )
                    }
                }
            } else {
                val musicIndex = remember(musicList.value, thisMusicPlayingLambda()) {
                    musicList.value?.indexOf(musicPlaying.value) ?: 0
                }
                val scope = rememberCoroutineScope()
                val state = rememberLazyListState(
                    initialFirstVisibleItemIndex = musicIndex + 1,
                    initialFirstVisibleItemScrollOffset = -15
                )

                YosWrapper {
                    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {

                        LazyColumn(state = state, modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithCache {
                                onDrawWithContent {
                                    val colors = listOf(
                                        Color.Transparent,
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
                                        Color.Transparent
                                    )

                                    drawContent()

                                    drawRect(
                                        brush = Brush.verticalGradient(colors),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            }/*, contentPadding = PaddingValues(vertical = 12.dp)*/
                        ) {
                            item("blank_before") {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            itemsIndexed(
                                musicList.value ?: emptyList(),
                                key = { index, music -> music.uri?.toString() ?: music.mediaId ?: "queue_$index" }
                            ) { _, music ->
                                SmallMusicListItem(
                                    music, navController
                                ) {
                                    scope.launch(Dispatchers.IO) {
                                        MediaController.prepare(
                                            music,
                                            musicList.value ?: emptyList()
                                        )
                                    }
                                }
                            }
                            item("blank_after") {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.SmallMusicListItem(music: YosMediaItem, navController: NavController? = null, itemClick: () -> Unit) {
    val isPlaying = MediaController.musicPlaying.value?.uri == music.uri
    val normalColor = Color.White

    Row(
        modifier = Modifier
            .height(64.dp)
            .fillMaxWidth()
            .songMenuTrigger(onClick = itemClick, onLongClick = {})
            .padding(horizontal = rememberAdaptive(30)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShadowImageWithCache(
            dataLambda = { music.thumb },
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            cornerRadius = 4.dp,
            shadowAlpha = 0f,
            imageQuality = ImageQuality.LOW
        )

        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                text = music.title ?: defaultTitle,
                modifier = Modifier
                    .padding(bottom = 1.dp)
                    .then(if (isPlaying) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                maxLines = 1,
                overflow = if (isPlaying) TextOverflow.Visible else TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                color = normalColor,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal
            )

            Text(
                text = music.artistsName ?: defaultArtistsName,
                modifier = Modifier
                    .alpha(0.5f)
                    .then(if (isPlaying) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                maxLines = 1,
                overflow = if (isPlaying) TextOverflow.Visible else TextOverflow.Ellipsis,
                fontSize = 11.5.sp,
                lineHeight = 11.5.sp,
                color = normalColor.copy(alpha = 0.7f),
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        SongMenuIcon(music, navController)
    }
}

@Composable
private fun Lyric(
    lyricState: () -> NowPlayingLyricState,
    weightLambda: () -> Boolean,
    onViewportInsetChanged: (Int) -> Unit,
    compactWindow: Boolean = false,
    splitWindow: Boolean = false,
    insetsHandled: Boolean = false,
    translationLambda: () -> Boolean,
    romajiLambda: () -> Boolean = { true },
    seekPreviewState: LyricSeekPreviewState,
    shouldRenderContent: Boolean,
    mainViewModel: MainViewModel,
    mediaViewModel: MediaViewModel,
    showMiniPlayer: () -> Boolean,
    userScrollEnabled: Boolean = true,
    renderActive: Boolean = true,
    onLineLongPress: (Int) -> Unit,
    onBackClick: () -> Unit
) = YosWrapper {

    val context = LocalContext.current


    Column(
        Modifier
            .fillMaxSize()
    ) {
        YosWrapper {

            // Measure the fixed spacer, not global bounds inside the animated layer.
            // Graphics-layer motion need not dispatch another layout callback.
            val topSpace = if (insetsHandled) Modifier.height(16.dp) else
                Modifier.statusBarsHeight(if (splitWindow) 16.dp else if (compactWindow) 94.dp else 110.dp)
            Spacer(modifier = topSpace.onSizeChanged { onViewportInsetChanged(it.height) })

              // 歌词控件较重，首次打开播放页时延后到展开动画结束后再初始化；
              // 若用户直接切到歌词页，则立即渲染，避免和页面展开动画争主线程。
              if (shouldRenderContent) {
                  YosLyricView(
                      //mediaViewModel = mediaViewModel,
                      lyricStateLambda = lyricState,
                      liveTimeLambda = {
                          (mediaControl?.currentPosition ?: 0).toInt()
                      },
                      userScrollEnabled = userScrollEnabled,
                      interactionEnabled = userScrollEnabled,
                      renderActive = renderActive,
                      mediaEvent = object : YosMediaEvent {
                          override fun onSeek(position: Int) {
                              mediaControl?.seekTo(position.toLong())
                          }
                      },
                      translationLambda = translationLambda,
                      romajiLambda = romajiLambda,
                      seekPreviewState = seekPreviewState,
                      blurLambda = {
                          SettingsLibrary.LyricBlurEffect
                      },
                      uiConfig = YosUIConfig(
                          noLrcText = stringResource(id = R.string.tip_no_lyrics),
                          mainTextSize = SettingsLibrary.LyricFontSize,
                          subTextSize = SettingsLibrary.TranslationFontSize
                      ),
                      weightLambda = weightLambda,
                      modifier = Modifier,
                      onLineLongPress = onLineLongPress,
                      onBackClick = onBackClick
                  )
              } else {
                  Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricShareSheet(
    entries: List<YosLyricLine>,
    initialIndex: Int,
    music: YosMediaItem?,
    backgroundArtworkUri: Uri?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareEntries = remember(entries) { entries.filter { it.text.isNotBlank() && !it.isCountdown } }
    val initialTime = entries.getOrNull(initialIndex)?.startMs
    val initialShareIndex = remember(shareEntries, initialTime) {
        shareEntries.indexOfFirst { it.startMs == initialTime }
            .takeIf { it >= 0 }
            ?: shareEntries.indices.firstOrNull()
            ?: -1
    }
    val selected = remember(shareEntries, initialShareIndex) {
        mutableStateOf(if (initialShareIndex >= 0) setOf(initialShareIndex) else emptySet())
    }
    val selectedLines = selected.value.sorted().mapNotNull(shareEntries::getOrNull)
    val saveSuccess = stringResource(R.string.tip_lyric_share_save_success)
    val saveFailed = stringResource(R.string.tip_lyric_share_save_fail)
    val shareFailed = stringResource(R.string.tip_lyric_share_to_fail_desc)
    val working = remember { mutableStateOf(false) }

    suspend fun renderCard() = withContext(Dispatchers.IO) {
        LyricCardExporter.render(
            context = context,
            lines = selectedLines,
            title = music?.title,
            artist = music?.artists,
            artworkUri = music?.thumb,
            includeTranslation = SettingsLibrary.NowPlayingTranslation,
            backgroundArtworkUri = backgroundArtworkUri
        )
    }

    fun share() {
        if (selectedLines.isEmpty() || working.value) return
        scope.launch {
            working.value = true
            runCatching {
                val bitmap = renderCard()
                val intent = try {
                    withContext(Dispatchers.IO) { LyricCardExporter.createShareIntent(context, bitmap) }
                } finally { bitmap.recycle() }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.lyric_share_to)))
            }.onFailure { Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show() }
            working.value = false
        }
    }

    fun save() {
        if (selectedLines.isEmpty() || working.value) return
        scope.launch {
            working.value = true
            runCatching {
                val bitmap = renderCard()
                try {
                    withContext(Dispatchers.IO) { LyricCardExporter.saveToPictures(context, bitmap) }
                } finally { bitmap.recycle() }
            }.onSuccess {
                Toast.makeText(context, saveSuccess, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, saveFailed, Toast.LENGTH_SHORT).show()
            }
            working.value = false
        }
    }

    OptionDialog(
        icon = {
            ShadowImageWithCache(
                dataLambda = { music?.thumb }, contentDescription = null,
                modifier = Modifier.size(52.dp), cornerRadius = 6.dp,
                shadowAlpha = 0f, imageQuality = ImageQuality.LOW
            )
        },
        title = stringResource(R.string.lyric_share_title, selected.value.size),
        subTitle = stringResource(R.string.lyric_share_subtitle, selected.value.size),
        horizontalTitle = true,
        contentBottomSpacing = 0.dp,
        bottomContentPadding = 16.dp,
        scrollContent = false,
        content = {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialShareIndex.coerceAtLeast(0))
            val rowShape = remember { YosRoundedCornerShape(18.dp) }
            val sheetBackground = LocalDialogContainerColor.current
            val listHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 280)
                .coerceIn(72, 430).dp
            Column(Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = listHeight)
                        .dialogListFade(sheetBackground),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 36.dp)
                ) {
                    itemsIndexed(shareEntries, key = { index, _ -> index }, contentType = { _, _ -> "lyric" }) { index, line ->
                        val checked = index in selected.value
                        val foreground = if (checked) Color.White else MaterialTheme.colorScheme.onSurface
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(rowShape)
                                .background(if (checked) yos.music.player.ui.theme.primary else MaterialTheme.colorScheme.onSecondary)
                                .toggleable(
                                    value = checked, enabled = !working.value, role = Role.Checkbox,
                                    onValueChange = { choose ->
                                        selected.value = if (!choose) selected.value - index
                                            else if (selected.value.size < 14) selected.value + index
                                            else selected.value
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(line.text, fontSize = 20.sp, lineHeight = 28.sp,
                                fontWeight = FontWeight.Bold, color = foreground)
                            if (SettingsLibrary.NowPlayingTranslation && line.translation.isNotBlank()) {
                                Text(line.translation, fontSize = 13.sp, color = foreground.copy(alpha = 0.65f),
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val enabled = selected.value.isNotEmpty() && !working.value
                    Surface(
                        modifier = Modifier.weight(1f).clickable(enabled = enabled, onClick = ::save),
                        shape = YosRoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    ) {
                        Text(stringResource(R.string.lyric_share_save), Modifier.padding(15.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clickable(enabled = enabled, onClick = ::share),
                        shape = YosRoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Text(stringResource(R.string.lyric_share_to), Modifier.padding(15.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        },
        onDismissRequest = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionButtonsRow(
    navController: NavController? = null, closeSheet: () -> Unit = {},
    modifier: Modifier = Modifier, favoriteModifier: Modifier = Modifier, menuModifier: Modifier = Modifier,
    musicPlayingLambda: () -> YosMediaItem?
) {
    Row(
        modifier = modifier.overlayEffect(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dp = 28.dp

        val context = LocalContext.current

        Box(
            modifier = favoriteModifier
                .clickable(
                    onClick = {
                        //println("收藏 开始")
                        val musicPlaying = musicPlayingLambda()
                        //println("收藏 $musicPlaying")
                        if (musicPlaying != null) {
                            Vibrator.click(context)
                            //println("收藏 切换状态
                            if (musicPlaying.let { FavPlayListLibrary.isFavorite(it) }) {
                                FavPlayListLibrary.removeMusic(musicPlaying)
                            } else {
                                FavPlayListLibrary.addMusic(musicPlaying)
                            }
                            //println("收藏 完毕")
                        }
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .size(dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = musicPlayingLambda()?.let { FavPlayListLibrary.isFavorite(it) }
                    ?: false,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }) {
                if (it) {
                    Icon(
                        painterResource(id = R.drawable.ic_nowplaying_favorited),
                        contentDescription = null,
                        modifier = Modifier
                            .size(dp)
                    )
                } else {
                    Icon(
                        painterResource(id = R.drawable.ic_nowplaying_favorite),
                        contentDescription = null,
                        modifier = Modifier
                            .overlayEffect()
                            .size(dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 三点菜单
        var showMoreMenu = remember { mutableStateOf(false) }
        var menuPage = remember { mutableStateOf(NowPlayingMenuPage.More) }
        // 跨页保留：新建歌单输入内容
        val newPlaylistName = remember { mutableStateOf("") }
        var showArtistPicker = remember { mutableStateOf(false) }
        var showSleepTimerWarning = remember { mutableStateOf(false) }
        val menuOpenCount = remember { mutableIntStateOf(0) }
        var btnPos = remember { mutableStateOf(Offset.Zero) }

        val menuOpen = showMoreMenu.value

        Box(
            modifier = menuModifier
                .graphicsLayer {
                    rotationZ = 90f
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .onGloballyPositioned { btnPos.value = it.localToRoot(Offset.Zero) }
                .clickable(
                    onClick = { showMoreMenu.value = true; menuOpenCount.intValue++; menuPage.value = NowPlayingMenuPage.More },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() })
                .size(dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = menuOpen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }) {
                if (it) {
                    Icon(
                        painterResource(id = R.drawable.ic_nowplaying_more_fill),
                        contentDescription = null,
                        modifier = Modifier.size(dp)
                    )
                } else {
                    Icon(
                        painterResource(id = R.drawable.ic_nowplaying_more),
                        contentDescription = null,
                        modifier = Modifier.overlayEffect().size(dp)
                    )
                }
            }
        }

        // 菜单弹出
        val music = musicPlayingLambda()
        if (music != null && showMoreMenu.value) {
            key(menuOpenCount.intValue) {
            // 定时关闭页的滑条与开关状态：本次打开菜单期间跨页保留
            val sleepMinutes = remember { mutableFloatStateOf(15f) }
            val sleepExtendToSongEnd = remember {
                mutableStateOf(
                    if (yos.music.player.code.SleepTimerManager.isActive.value) yos.music.player.code.SleepTimerManager.isExtendToSongEnd.value
                    else SettingsLibrary.SleepTimerExtendToSongEnd
                )
            }
            val morePage = OptionDialogPage(
                key = NowPlayingMenuPage.More,
                title = music.title ?: defaultTitle,
                subTitle = "${music.artistsName ?: defaultArtistsName}${music.album?.let { " · $it" } ?: ""}",
                icon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShadowImageWithCache(
                            dataLambda = { music.thumb }, contentDescription = null,
                            modifier = Modifier.size(52.dp), cornerRadius = 6.dp,
                            shadowAlpha = 0f, imageQuality = ImageQuality.LOW
                        )
                    }
                },
                horizontalTitle = true,
                content = { dismiss ->
                    val items = listOf(
                        Triple("添加到歌单", Icons.Filled.Add, { menuPage.value = NowPlayingMenuPage.PlaylistPicker }),
                        Triple("下一首播放", Icons.AutoMirrored.Filled.PlaylistPlay, {
                            val currentMusic = MediaController.musicPlaying.value ?: return@Triple
                            val list = MediaController.playingMusicList.value?.toMutableList() ?: return@Triple
                            val currentIdx = list.indexOfFirst { it.uri == currentMusic.uri }
                            if (currentIdx >= 0) {
                                list.add(currentIdx + 1, music)
                                MediaController.playingMusicList.value = list
                                MediaController.mediaControl?.addMediaItem(currentIdx + 1, music.toMediaItem())
                                Toast.makeText(context, "已添加到下一首播放", Toast.LENGTH_SHORT).show()
                            }
                            dismiss()
                        }),
                        Triple(if (FavPlayListLibrary.isFavorite(music)) "从喜爱移除" else "添加到喜爱",
                            if (FavPlayListLibrary.isFavorite(music)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        {
                            if (FavPlayListLibrary.isFavorite(music)) FavPlayListLibrary.removeMusic(music)
                            else FavPlayListLibrary.addMusic(music)
                            dismiss()
                        }),
                        // 定时关闭菜单项 - 动态显示剩余时间
                        Triple(
                            if (yos.music.player.code.SleepTimerManager.isActive.value) {
                                if (yos.music.player.code.SleepTimerManager.isExtendToSongEnd.value && yos.music.player.code.SleepTimerManager.remainingSeconds.intValue == 0) {
                                    "定时关闭 (等待播完退出)"
                                } else {
                                    "定时关闭 (${yos.music.player.code.SleepTimerManager.getFormattedRemainingTime()})"
                                }
                            } else {
                                "定时关闭"
                            },
                            if (yos.music.player.code.SleepTimerManager.isActive.value) Icons.Filled.Timer else Icons.Outlined.Timer,
                            {
                                menuPage.value = NowPlayingMenuPage.SleepTimer
                            }
                        ),
                        Triple("歌曲信息", Icons.Outlined.Info, { menuPage.value = NowPlayingMenuPage.Detail })
                    )
                    Column(
                        Modifier.clip(YosRoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.onSecondary)
                    ) {
                        items.forEachIndexed { index, (label, icon, onClick) ->
                            if (index > 0) Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                            Row(
                                Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }
            )
            OptionDialog(
                pages = listOf(
                    morePage,
                    yos.music.player.ui.widgets.basic.playlistPickerDialogPage(
                        key = NowPlayingMenuPage.PlaylistPicker,
                        music = music, context = context, newPlaylistName = newPlaylistName,
                        onCreateClick = { menuPage.value = NowPlayingMenuPage.CreatePlaylist }
                    ),
                    yos.music.player.ui.widgets.basic.createPlaylistDialogPage(
                        NowPlayingMenuPage.CreatePlaylist, music, context, newPlaylistName
                    ) { menuPage.value = NowPlayingMenuPage.PlaylistPicker },
                    yos.music.player.ui.widgets.basic.songDetailDialogPage(NowPlayingMenuPage.Detail, music),
                    yos.music.player.ui.pages.sleepTimerDialogPage(
                        key = NowPlayingMenuPage.SleepTimer,
                        context = context,
                        currentSongUri = music.uri?.toString(),
                        minutes = sleepMinutes,
                        extendToSongEnd = sleepExtendToSongEnd
                    )
                ),
                currentPage = menuPage.value,
                onDismissRequest = { showMoreMenu.value = false }
            )
            }
        }

        if (showSleepTimerWarning.value && yos.music.player.code.SleepTimerManager.isActive.value) {
            SleepTimerWarningDialog(
                onDismiss = { showSleepTimerWarning.value = false },
                onContinue = {
                    // 继续倒计时,重置警告状态
                    yos.music.player.code.SleepTimerManager.shouldShowWarning.value = false
                    showSleepTimerWarning.value = false
                },
                onCancel = {
                    // 取消定时
                    showSleepTimerWarning.value = false
                }
            )
        }

        if (showArtistPicker.value && music != null && navController != null) {
            val artists = music.artistsList ?: emptyList()
            OptionDialog(
                icon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShadowImageWithCache(
                            dataLambda = { music.thumb }, contentDescription = null,
                            modifier = Modifier.size(52.dp), cornerRadius = 6.dp,
                            shadowAlpha = 0f, imageQuality = ImageQuality.LOW
                        )
                    }
                },
                title = music.title ?: defaultTitle,
                subTitle = music.artistsName ?: defaultArtistsName,
                horizontalTitle = true,
                content = { dismiss ->
                    Column(
                        Modifier.clip(YosRoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.onSecondary)
                    ) {
                        artists.forEachIndexed { index, artist ->
                            if (index > 0) Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                            Row(
                                Modifier.fillMaxWidth().height(48.dp).clickable {
                                    dismiss.after {
                                        yos.music.player.data.objects.LibraryObject.setTargetArtistName(artist)
                                        navController.toUI(UI.ArtistInfo)
                                    }
                                }.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(artist, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                },
                onDismissRequest = { showArtistPicker.value = false }
            )
        }
    }
}

@Composable
private fun PlayingBar(
    modifier: Modifier,
    artwork: Painter,
    artworkBridge: PlayerArtworkBridge,
    compactWindow: Boolean = false,
    titleModifier: Modifier, artistModifier: Modifier,
    favoriteModifier: Modifier, menuModifier: Modifier, actionsModifier: Modifier,
    musicPlayingLambda: () -> YosMediaItem?,
    onAlbumClick: () -> Unit
) = YosWrapper {

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compactWindow) 12.dp else rememberAdaptive(28))
            .padding(top = if (compactWindow) 8.dp else 22.dp)
            .height(if (compactWindow) 52.dp else 70.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        run {
            val smallShape = YosRoundedCornerShape(8)
            Box(
                modifier = modifier.size(if (compactWindow) 48.dp else 69.dp)
                      .dropShadow(
                          shape = smallShape,
                          shadowAlpha = 0f,
                          shadowType = ShadowType.Small
                      )
                    .clip(smallShape)
                    .clickable(MutableInteractionSource(), null) { onAlbumClick() }
            ) {
                Image(
                    painter = artwork,
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .onGloballyPositioned { artworkBridge.record(COMPACT_ARTWORK_ENDPOINT, it) }
                        .graphicsLayer { alpha = 0f }
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = if (compactWindow) 8.dp else 12.dp, end = if (compactWindow) 8.dp else 15.dp)
        ) {
            Text(
                text = musicPlayingLambda()?.title ?: defaultTitle,
                fontSize = 16.5.sp,
                maxLines = 1,
                modifier = titleModifier.basicMarquee(iterations = Int.MAX_VALUE),
                fontWeight = FontWeight.Bold,
                lineHeight = 16.5.sp
            )
            Text(
                text = musicPlayingLambda()?.artistsName
                    ?: defaultArtistsName,
                fontSize = 15.sp,
                modifier = artistModifier
                    .overlayEffect()
                    .basicMarquee(iterations = Int.MAX_VALUE),
                maxLines = 1,
                color = Color.White.copy(alpha = 0.35f),
                fontWeight = FontWeight.SemiBold
            )
        }

        YosWrapper {
            ActionButtonsRow(modifier = actionsModifier, favoriteModifier = favoriteModifier,
                menuModifier = menuModifier, musicPlayingLambda = musicPlayingLambda)
        }
    }

}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControl(
    progress: PlayerProgressState,
    audioOutput: PlaybackAudioOutput,
    onOutputDeviceClick: () -> Unit,
    compactWindow: Boolean = false,
    showBottomActions: Boolean = true,
    interactionEnabled: Boolean = true,
    seekPreviewState: LyricSeekPreviewState,
    cancelAreaContains: (Offset) -> Boolean,
    onSeekGestureChange: (Boolean, Boolean) -> Unit,
    renderActive: Boolean,
    isPlayingLambda: () -> Boolean,
    isPlayingOnChanged: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    previousEnabled: Boolean = true,
    onStatus: (Boolean) -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onLyrics: () -> Unit,
    onPlaylist: () -> Unit,
    nowPage: () -> String,
    onSlider: () -> Unit,
    onWhile: suspend () -> Unit,
    modifier: Modifier
) {
    val trackKey = musicPlaying.value?.uri?.toString()
    val context = LocalContext.current
    val playedTime = remember(progress) { mutableStateOf(formatTime(progress.positionMs.toLong() / 1000L)) }
    val remainingTime = remember(progress) {
        mutableStateOf("-${formatTime(((progress.durationMs - progress.positionMs.toLong()) / 1000L).coerceAtLeast(0L))}")
    }
    val onWhileRef = rememberUpdatedState(onWhile)
    DisposableEffect(seekPreviewState) {
        onDispose { seekPreviewState.update(null) }
    }
    LaunchedEffect(renderActive) {
        if (!renderActive) seekPreviewState.update(null)
    }

    YosWrapper {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = rememberAdaptive(25))
                .padding(bottom = if (compactWindow) 4.dp else 15.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                YosWrapper {
                // 启动作用
                YosWrapper {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    LaunchedEffect(lifecycleOwner, renderActive, trackKey, progress) {
                        if (!renderActive) return@LaunchedEffect
                        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            progress.restartFrames()
                            var nextSampleAt = 0L
                                var sampledPlaying = false
                                var sampledSpeed = 1f
                            var nextControlsAt = 0L
                            var lastSeconds = -1L
                            var lastDuration = -1L
                            while (isActive) {
                                val frame = withFrameMillis { it }
                                var report: Float? = null
                                if (frame >= nextSampleAt) {
                                    nextSampleAt = frame + 80L
                                    val sample = MediaController.playbackProgress()
                                    progress.durationMs = sample.durationMs
                                    report = sample.positionMs.toFloat()
                                    // isPlaying/speed 只在采样点读取：这两个 getter 每帧调用
                                    // 是跨会话往返，120Hz 下纯浪费，80ms 内的变化无感知差异。
                                    sampledPlaying = mediaControl?.isPlaying == true
                                    sampledSpeed = mediaControl?.playbackParameters?.speed ?: 1f
                                }
                                // Preserve the actual clock while automatic reports animate from
                                // the last visible progress, including after controls are recreated.
                                progress.frame(frame, sampledPlaying, sampledSpeed, report)
                                seekPreviewState.update(progress.positionMs.takeIf {
                                    progress.dragging || progress.isSeekTransitioning
                                })
                                val seconds = progress.positionMs.toLong() / 1000L
                                if (seconds != lastSeconds || progress.durationMs != lastDuration) {
                                    playedTime.value = formatTime(seconds)
                                    remainingTime.value = "-${formatTime((progress.durationMs / 1000L - seconds).coerceAtLeast(0L))}"
                                    lastSeconds = seconds
                                    lastDuration = progress.durationMs
                                }
                                if (frame >= nextControlsAt) {
                                    nextControlsAt = frame + 700L
                                    onWhileRef.value()
                                }
                            }
                        }
                    }
                }

                // 进度条
                YosWrapper {
                    PlaybackSeekTrack(
                        gestureKey = trackKey,
                        position = { progress.positionMs },
                        duration = { progress.durationMs },
                        livePosition = { progress.livePositionMs },
                        dragging = progress.dragging,
                        cancelAreaContains = cancelAreaContains,
                        onStart = {
                            progress.begin()
                            seekPreviewState.update(progress.positionMs)
                            onSeekGestureChange(true, false)
                            onSlider()
                        },
                        onPreview = { position, cancelled ->
                            progress.preview(position, cancelled, android.os.SystemClock.uptimeMillis())
                            seekPreviewState.update(progress.positionMs)
                            onSeekGestureChange(true, cancelled)
                            onSlider()
                        },
                        onFinish = { position ->
                            val now = android.os.SystemClock.uptimeMillis()
                            progress.finish(position, now)
                            if (position != null) {
                                seekPreviewState.commit(position)
                                Vibrator.longClick(context)
                                onSeek(position)
                            }
                            seekPreviewState.update(progress.positionMs.takeIf { progress.isSeekTransitioning })
                            onSeekGestureChange(false, false)
                            onSlider()
                        },
                        modifier = Modifier.padding(horizontal = 4.dp).overlayEffect().alpha(0.45f),
                        enabled = interactionEnabled
                    )
                }

                // 控制按钮&进度文本
                YosWrapper {
                    //println("重组：控制区域内部 - 控制按钮&进度文本")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 7.dp)
                            .height(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = playedTime.value,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.3.sp,
                                color = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.overlayEffect()
                            )
                            Text(
                                text = remainingTime.value,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.3.sp,
                                color = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.overlayEffect()
                            )
                        }

                        MusicQualityIndicator()
                    }


                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (compactWindow) 4.dp else 25.dp, bottom = if (compactWindow) 4.dp else 35.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (compactWindow) 44.dp else rememberAdaptive(61))
                                    .clickable(
                                        enabled = previousEnabled && interactionEnabled,
                                        indication = ripple(bounded = false),
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        onPrevious()
                                        Vibrator.click(context)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_rewind),
                                    contentDescription = "Previous",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp)
                                        .alpha(if (previousEnabled) 1f else 0.3f)
                                )
                            }

                            Spacer(modifier = Modifier.width(if (compactWindow) 20.dp else rememberAdaptive(43)))

                            Box(
                                modifier = Modifier
                                    .size(if (compactWindow) 44.dp else rememberAdaptive(58))
                                    .clickable(
                                        enabled = interactionEnabled,
                                        indication = ripple(bounded = false),
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        val nowPlaying = isPlayingLambda() || MediaViewModelObject.isBuffering.value
                                        if (nowPlaying) mediaControl?.fadePause()
                                        else mediaControl?.fadePlay()
                                        isPlayingOnChanged(!nowPlaying)
                                        Vibrator.click(context)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                yos.music.player.ui.widgets.PlaybackTransportIcon(
                                    playing = isPlayingLambda(),
                                    buffering = MediaViewModelObject.isBuffering.value
                                )
                            }
                            Spacer(modifier = Modifier.width(if (compactWindow) 20.dp else rememberAdaptive(43)))
                            Box(
                                modifier = Modifier
                                    .size(if (compactWindow) 44.dp else rememberAdaptive(61))
                                    .clickable(
                                        enabled = interactionEnabled,
                                        indication = ripple(bounded = false),
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        onNext()
                                        Vibrator.click(context)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_fforward),
                                    contentDescription = "Next",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }

                // 音量调节
                YosWrapper {
                    if (SettingsLibrary.NowPlayingShowVolumeBar) {
                        VolumeSlider(context = context, onSlider, interactionEnabled)
                    }
                }
                if (showBottomActions) {
                Spacer(modifier = Modifier.height(if (compactWindow) 6.dp else 16.dp))

                // 底部 歌词&播放列表
                YosWrapper {
                    //println("重组：控制区域内部 - 底部栏")
                    Row(
                        modifier = Modifier
                            .overlayEffect()
                            .fillMaxWidth()
                            .alpha(0.4f),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val dp = 32.dp
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .weight(1f)
                                .clickable(
                                    onClick = { onLyrics(); Vibrator.click(context) },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = nowPage() == Lyric,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                }) {
                                if (it) {
                                    Icon(
                                        painterResource(id = R.drawable.ic_nowplaying_lyricson),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(dp)
                                    )
                                } else {
                                    Icon(
                                        painterResource(id = R.drawable.ic_nowplaying_lyrics),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(0.1f))

                        PlaybackOutputButton(audioOutput.selectedName, audioOutput.externalOutput,
                            onClick = onOutputDeviceClick, compactWindow = compactWindow,
                            modifier = Modifier.weight(1f))

                        Spacer(modifier = Modifier.weight(0.1f))

                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .weight(1f)
                                .clickable(
                                    onClick = { onPlaylist(); Vibrator.click(context) },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = nowPage() == PlayingList,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                }) {
                                if (it) {
                                    Icon(
                                        painterResource(id = R.drawable.ic_nowplaying_queueon),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(dp)
                                    )
                                } else {
                                    Icon(
                                        painterResource(id = R.drawable.ic_nowplaying_queue),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(dp)
                                    )
                                }
                            }
                        }
                    }
                }

                }
                // 当前输出设备名称由底部设备按钮显示。
                }
            }
        }
    }
}

@Composable
internal fun VocalSeparationControl(
    context: Context,
    onSlider: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by VocalSeparationController.state.collectAsState()
    val failureText = stringResource(R.string.now_playing_mss_failed)
    val unavailableText = stringResource(R.string.now_playing_mss_unavailable)

    LaunchedEffect(state.status) {
        if (state.status == VocalSeparationStatus.FAILED) {
            Toast.makeText(context, failureText, Toast.LENGTH_SHORT).show()
        }
    }

    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val expansion = remember { Animatable(if (state.enabled) 1f else 0f) }
    val liquidFraction = remember {
        Animatable(if (state.enabled) state.selectedGain else 1f)
    }
    val liquidAlpha = remember { Animatable(if (state.enabled) 1f else 0f) }
    val selectedGainState = rememberUpdatedState(state.selectedGain)

    // H1 in the APK deliberately sequences these three values. Opening first
    // grows the control while the fill fades in, then lowers the liquid;
    // closing fills it, fades it out, and only then collapses the control.
    LaunchedEffect(state.enabled) {
        if (state.enabled) {
            liquidFraction.snapTo(1f)
            launch {
                liquidAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 240)
                )
            }
            expansion.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 250f)
            )
            liquidFraction.animateTo(
                targetValue = selectedGainState.value,
                animationSpec = tween(durationMillis = 520)
            )
        } else {
            liquidFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520)
            )
            liquidAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 240)
            )
            expansion.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 250f)
            )
        }
    }
    LaunchedEffect(state.enabled, state.selectedGain) {
        if (state.enabled && expansion.value >= 0.999f) {
            liquidFraction.snapTo(state.selectedGain)
        }
    }
    val controlScale by animateFloatAsState(
        targetValue = if (pressed && !state.enabled) 1.35f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "MssControlScale"
    )
    val controlAlpha by animateFloatAsState(
        targetValue = if (state.available) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 405f),
        label = "MssControlVisibility"
    )
    val adjustmentRangePx = with(density) { 60.dp.toPx() }
    // 拖动音量时高宽一起增大、松手弹回；仅开启不改变尺寸
    val draggingGain = remember { mutableStateOf(false) }
    val dragActive = remember { Animatable(0f) }
    LaunchedEffect(draggingGain.value) {
        dragActive.animateTo(
            targetValue = if (draggingGain.value) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f)
        )
    }
    val controlHeight = (34f + 60f * expansion.value.coerceIn(0f, 1f) + 24f * dragActive.value).dp
    val controlWidth = (34f + 26f * dragActive.value).dp
    // The continuous corner reaches beyond its nominal radius. 10dp keeps
    // adjacent curves apart even in the 34dp square before expansion.
    val smoothShape = remember { YosRoundedCornerShape(roundSize = 10.dp) }

    Box(
        modifier = modifier
            .width(controlWidth)
            .height(controlHeight)
            .overlayEffect(outset = 48.dp)
            .graphicsLayer {
                scaleX = controlScale
                scaleY = controlScale
                alpha = controlAlpha
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .drawWithCache {
                val outline = smoothShape.createOutline(size, layoutDirection, this)
                // Cut the inverse path from the isolated additive layer. This
                // antialiased mask never switches to a hardware round-rect
                // outline while pressed, fading, expanding or collapsing.
                val outside = androidx.compose.ui.graphics.Path().apply {
                    fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                    // The mask's outer edge must lie beyond the filled rectangle.
                    // Coincident antialiased edges leave a rectangular hairline
                    // at fractional screen positions / animated press scales.
                    val maskOutset = 4.dp.toPx()
                    addRect(androidx.compose.ui.geometry.Rect(-maskOutset, -maskOutset,
                        size.width + maskOutset, size.height + maskOutset))
                    if (outline is androidx.compose.ui.graphics.Outline.Generic) addPath(outline.path)
                    else reset() // A zero-sized entrance frame has no visible corner to mask.
                }
                onDrawWithContent {
                    drawRect(Color.White.copy(alpha = 0.10f))
                    drawContent()
                    drawPath(outside, Color.Transparent, blendMode = BlendMode.Clear)
                }
            }
            .pointerInput(state.available, state.enabled) {
                if (!state.available || !state.enabled) return@pointerInput
                var dragGain = selectedGainState.value
                detectVerticalDragGestures(
                    onDragStart = {
                        dragGain = selectedGainState.value
                        draggingGain.value = true
                    },
                    onDragEnd = { draggingGain.value = false },
                    onDragCancel = { draggingGain.value = false },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragGain = (dragGain - dragAmount / adjustmentRangePx).coerceIn(0f, 1f)
                        VocalSeparationController.setSelectedGain(dragGain)
                        onSlider()
                    }
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (state.available) {
                    VocalSeparationController.toggle()
                    onSlider()
                    Vibrator.click(context)
                } else {
                    Toast.makeText(context, unavailableText, Toast.LENGTH_SHORT).show()
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(liquidFraction.value.coerceIn(0f, 1f))
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = liquidAlpha.value }
                .background(Color.White.copy(alpha = 0.92f))
        )
        Icon(
            painter = painterResource(R.drawable.ic_nowplaying_vocal),
            contentDescription = stringResource(R.string.now_playing_mss_toggle),
            tint = Color.White,
            modifier = Modifier
                .padding(bottom = 3.dp)
                .size(28.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // The same fill boundary clips a second tint of the icon:
                    // White above the liquid; black contributes no colour through
                    // the parent's additive layer, revealing the page within it.
                    val fillTop = controlHeight.toPx() * (1f - liquidFraction.value.coerceIn(0f, 1f))
                    val iconTop = controlHeight.toPx() - 3.dp.toPx() - size.height
                    val localFillTop = (fillTop - iconTop).coerceIn(0f, size.height)
                    clipRect(top = localFillTop) {
                        drawRect(
                            color = androidx.compose.ui.graphics.lerp(Color.White, Color.Black, liquidAlpha.value.coerceIn(0f, 1f)),
                            blendMode = BlendMode.SrcIn
                        )
                    }
                }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSlider(context: Context, onSlider: () -> Unit, enabled: Boolean = true) {
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val volumeState = remember(audioManager, maxVolume) {
        VolumeSliderState(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), maxVolume)
    }
    val volumeChangeReceiver = remember(volumeState) {
        VolumeChangeReceiver { step -> volumeState.report(step, android.os.SystemClock.uptimeMillis()) }
    }
    val intentFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")

    DisposableEffect(context, volumeChangeReceiver) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                volumeChangeReceiver,
                intentFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(volumeChangeReceiver, intentFilter)
        }

        onDispose {
            context.unregisterReceiver(volumeChangeReceiver)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 1.5.dp)
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 2.5.dp)
            .overlayEffect()
            .alpha(0.45f)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_nowplaying_volume),
            contentDescription = "Mute",
            modifier = Modifier.size(20.dp)
        )

        PlaybackVolumeTrack(
            state = volumeState,
            readSystemStep = { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) },
            onStepChange = { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) },
            onInteraction = onSlider,
            onRelease = { Vibrator.longClick(context) },
            modifier = Modifier.weight(1f).padding(start = 1.5.dp, end = 5.dp),
            enabled = enabled
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_nowplaying_volume_full),
            contentDescription = "Max Volume",
            modifier = Modifier.size(20.dp)
        )
    }
}

fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "$minutes:${if (secs < 10) "0$secs" else "$secs"}"
}
