@file:Suppress("DEPRECATION")

package yos.music.player.ui.pages

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect

import androidx.compose.runtime.SideEffect
import yos.music.player.ui.widgets.playerWindowLayout
import yos.music.player.ui.widgets.playerWindowPane
import yos.music.player.ui.widgets.PlayerContentVisibility
import androidx.compose.ui.graphics.TransformOrigin
import yos.music.player.ui.widgets.playerHeaderModifier
import yos.music.player.ui.widgets.clipPlayerLyrics

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import android.media.MediaRouter2
import android.os.Build
import androidx.annotation.RequiresApi
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
import coil.ImageLoader
import coil.request.ImageRequest
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.isActive
import yos.music.player.ui.widgets.PlaybackSeekTrack
import yos.music.player.ui.widgets.drawPlaybackProgress
import yos.music.player.ui.widgets.NativeLyricClock
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastMap
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.blankj.utilcode.util.TimeUtils
import com.google.accompanist.insets.navigationBarsHeight
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
import yos.music.player.code.VolumeChangeReceiver
import yos.music.player.code.YosPlaybackService
import yos.music.player.code.utils.lrc.YosMediaEvent
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosUIConfig
import yos.music.player.code.utils.lrc.LyricCardExporter
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.code.utils.player.FadeExo.fadePlay
import yos.music.player.data.libraries.FavPlayListLibrary
import yos.music.player.data.libraries.MusicLibrary.toMediaItem
import yos.music.player.data.libraries.PlayListLibrary

import yos.music.player.ui.widgets.basic.PlaylistPickerDialog
import yos.music.player.ui.widgets.basic.OptionDialog
import yos.music.player.ui.widgets.basic.dialogListFade
import yos.music.player.ui.widgets.basic.SongDetailDialog
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
import yos.music.player.ui.widgets.PlayerArtworkBridge
import yos.music.player.ui.widgets.PlayerElementAnchors
import yos.music.player.ui.widgets.ALBUM_ARTWORK_ENDPOINT
import yos.music.player.ui.widgets.COMPACT_ARTWORK_ENDPOINT
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
    LaunchedEffect(headerTarget) {
        if (headerTarget != null) {
            if (!headerInitialized.value) {
                headerY.snapTo(headerTarget)
                headerInitialized.value = true
            } else headerY.animateTo(headerTarget, spring(dampingRatio = 1f, stiffness = 360f))
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { headerAnchors.owner(it) },
        contentColor = Color.White,
        color = Color.Black
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowHeight = maxHeight.value
        val window = playerWindowLayout(maxWidth.value, windowHeight)
        val compactWindow = window.compact
        val splitWindow = window.split
        val albumPage = nowPageLambda() == Album
        val context = LocalContext.current

        val lyricState: MutableState<NowPlayingLyricState> =
            MediaViewModelObject.nowPlayingLyrics
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
        val repeatMode = rememberSaveable(key = "NowPlaying_repeatMode") {
            mutableIntStateOf(mediaControl?.repeatMode ?: REPEAT_MODE_OFF)
        }


        /*val nowPage = rememberSaveable(key = "NowPlaying_nowPage") {
            MainViewModelObject.nowPage
        }*/


        // 触摸超时：仅在歌词页面且开启自动隐藏时隐藏控件
        YosWrapper {
            LaunchedEffect(showControl.value, nowPageLambda(), lastClickTime.longValue) {
                if (nowPageLambda() != Lyric && !showControl.value) {
                    showControl.value = true
                    return@LaunchedEffect
                }
                if (showControl.value && nowPageLambda() == Lyric && SettingsLibrary.LyricsHideControls) {
                    val time = 2500L
                    delay(time)
                    withContext(Dispatchers.Main) {
                        if (TimeUtils.getNowMills() - lastClickTime.longValue >= time && nowPageLambda() == Lyric) {
                            showControl.value = false
                        }
                    }
                }
            }
        }

        YosWrapper {
            LaunchedEffect(showMiniPlayer(), nowPageLambda()) {
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
                showMiniPlayer = showMiniPlayer
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
                LaunchedEffect(nowPageLambda()) {
                    val targetAlpha = if (nowPageLambda() == Lyric) 1f else 0f
                    alphaAnim.animateTo(targetAlpha, tween(210, easing = FastOutSlowInEasing))
                }
            }

            val lyricRenderActive = remember {
                derivedStateOf { !showLyricShare.value && (nowPageLambda() == Lyric || alphaAnim.value > 0f) }
            }
            val translationButtonEnabled = remember("NowPlaying_translationButtonEnabled") {
                derivedStateOf { showControl.value && alphaAnim.value != 0f }
            }


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

                    Lyric(
                          lyricState = { lyricState.value },
                        weightLambda = { !splitWindow && showControl.value },
                        onViewportInsetChanged = { lyricViewportInsetPx.intValue = it },
                        compactWindow = compactWindow,
                        splitWindow = splitWindow,
                        translationLambda = { SettingsLibrary.NowPlayingTranslation },
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
                            .padding(top = if (compactWindow) 8.dp else 22.dp)
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
                                        Column(Modifier.fillMaxHeight(if (splitWindow) 1f else if (compactWindow)
                                            (1f - 220f / windowHeight).coerceIn(0.25f, 0.595f) else 0.595f)) {
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
                                YosWrapper {
                                    Column(Modifier.fillMaxSize()) {
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
                                        if (!splitWindow) PlayerContentVisibility(nowPageLambda() == PlayingList) {
                                            Column {
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
                                                thisMusicPlayingLambda = { thisMusicPlaying.value }
                                            )
                                            }
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
                    modifier = Modifier.playerWindowPane(0.5f, 0.5f).fillMaxSize().statusBarsPadding()
                ) {
                Column(Modifier.fillMaxSize()) {
                    PlayingList(
                        navController = navController,
                        shuffleModeEnabledLambda = { shuffleModeEnabled.value },
                        shuffleModeOnChanged = { shuffleModeEnabled.value = it },
                        repeatModeLambda = { repeatMode.intValue },
                        repeatModeOnChanged = { repeatMode.intValue = it },
                        thisMusicPlayingLambda = { thisMusicPlaying.value },
                        fillAvailableHeight = true
                    )
                }
                }
            }

            // 音乐控制
            YosWrapper {
                Column(
                    Modifier
                        .playerWindowPane(if (splitWindow && albumPage) 0.5f else 0f, if (splitWindow) 0.5f else 1f)
                        .fillMaxSize()
                        .statusBarsPadding(), verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        Modifier
                            /*.fillMaxHeight(0.385f)*/
                            .fillMaxHeight(if (compactWindow) 1f else 0.437f)
                            .fillMaxWidth()
                    ) playerOverlayBox@ {

                        YosWrapper {
                            if (showControl.value && !compactWindow) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
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
                                Modifier.fillMaxSize(),
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
                                        compactWindow = compactWindow,
                                        renderActive = !showMiniPlayer(),
                                        isPlayingLambda = isPlayingStatusLambda,
                                        isPlayingOnChanged = isPlayingOnChanged,
                                        onPrevious = {
                                            mediaControl?.seekToPreviousMediaItem()
                                            showControl.value = true
                                            lastClickTime.longValue = TimeUtils.getNowMills()
                                        },
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
                                            mediaControl?.seekToNextMediaItem()
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
                        if (!compactWindow && showControl.value && nowPageLambda() == Lyric) {
                            val vocalBottomOffset =
                                if (SettingsLibrary.NowPlayingShowVolumeBar) 300.dp else 273.5.dp
                            VocalSeparationControl(
                                context = context,
                                onSlider = {
                                    showControl.value = true
                                    lastClickTime.longValue = TimeUtils.getNowMills()
                                },
                                modifier = with(this@playerOverlayBox) {
                                    Modifier.align(Alignment.BottomEnd)
                                }.offset(
                                    x = -rememberAdaptive(25),
                                    y = -vocalBottomOffset
                                )
                            )
                        }
                    }
                }
            }

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
                entries = lyricState.value.entries,
                initialIndex = lyricShareInitialIndex.intValue,
                music = thisMusicPlaying.value,
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayingList(
    navController: NavController,
    shuffleModeEnabledLambda: () -> Boolean,
    shuffleModeOnChanged: (Boolean) -> Unit,
    repeatModeLambda: () -> Int,
    repeatModeOnChanged: (Int) -> Unit,
    thisMusicPlayingLambda: () -> YosMediaItem?,
    fillAvailableHeight: Boolean = false
) {
    val context = LocalContext.current

    Spacer(modifier = Modifier.height(18.dp))

    val musicList = remember("PlayingList_musicList") {
        playingMusicList
    }

    YosWrapper {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (fillAvailableHeight) 1f else 0.545f),
        ) {
            val hide = remember("PlayingList_hide") {
                derivedStateOf {
                    musicList.value.isNullOrEmpty() || shuffleModeEnabledLambda()
                }
            }

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
                        text = stringResource(id = R.string.page_library_playlists),
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier/*.padding(top = 10.dp)*/
                    )
                    Text(
                        text = stringResource(
                            id = R.string.page_library_playlists_music_total,
                            musicList.value?.size ?: 0
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .overlayEffect()
                            .alpha(0.35f)
                    )
                }

                Row(
                    modifier = Modifier
                        .overlayEffect()
                        .alpha(0.6f)
                ) {
                    val dp = 36.dp
                    YosWrapper {
                        val shuffleBackgroundAlpha =
                            animateFloatAsState(targetValue = if (shuffleModeEnabledLambda()) 0.9f else 0f)
                        Box(
                            modifier = Modifier
                                .clickable(
                                    onClick = {
                                        Vibrator.click(context)
                                        toggleShuffle()
                                        shuffleModeOnChanged(!shuffleModeEnabledLambda())
                                    },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() })
                                .size(36.dp)
                                .background(
                                    Color.White.copy(alpha = shuffleBackgroundAlpha.value),
                                    shape = YosRoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            YosWrapper {
                                val shuffleIconTint =
                                    animateColorAsState(targetValue = if (shuffleModeEnabledLambda()) Color.Black else Color.White)
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_shuffle),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(dp),
                                    tint = shuffleIconTint.value
                                )
                            }
                        }
                    }
                    YosWrapper {
                        val repeatHighlight =
                            repeatModeLambda() == REPEAT_MODE_ALL || repeatModeLambda() == REPEAT_MODE_ONE
                        val repeatBackgroundAlpha =
                            animateFloatAsState(targetValue = if (repeatHighlight) 0.9f else 0f)
                        Box(
                            modifier = Modifier
                                .clickable(
                                    onClick = {
                                        Vibrator.click(context)
                                        val targetMode = when (repeatModeLambda()) {
                                            REPEAT_MODE_OFF -> {
                                                REPEAT_MODE_ALL
                                            }

                                            REPEAT_MODE_ALL -> {
                                                REPEAT_MODE_ONE
                                            }

                                            else -> {
                                                REPEAT_MODE_OFF
                                            }
                                        }
                                        mediaControl?.repeatMode = targetMode
                                        mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
                                        repeatModeOnChanged(targetMode)
                                    },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() })
                                .padding(start = 10.dp)
                                .size(36.dp)
                                .background(
                                    Color.White.copy(alpha = repeatBackgroundAlpha.value),
                                    shape = YosRoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            YosWrapper {
                                AnimatedContent(targetState = repeatModeLambda(), transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                }) {
                                    when (it) {
                                        REPEAT_MODE_ONE -> Icon(
                                            painterResource(id = R.drawable.ic_nowplaying_repeatone),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(dp),
                                            tint = animateColorAsState(targetValue = if (repeatHighlight) Color.Black else Color.White).value
                                        )

                                        else -> Icon(
                                            painterResource(id = R.drawable.ic_nowplaying_repeat),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(dp),
                                            tint = animateColorAsState(targetValue = if (repeatHighlight) Color.Black else Color.White).value
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


            if (hide.value) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_uitabbar_library),
                        contentDescription = null,
                        modifier = Modifier
                            .overlayEffect()
                            .size(70.dp)
                            .alpha(0.6f)
                    )
                    Text(
                        text = stringResource(id = R.string.playlist_unavailable_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
                    )
                    YosWrapper {
                        val msg = remember("PlayingList_msg") {
                            derivedStateOf {
                                if (musicList.value.isNullOrEmpty()) {
                                    R.string.playlist_unavailable_desc
                                } else {
                                    R.string.playlist_shuffle_desc
                                }
                            }
                        }
                        Text(
                            text = stringResource(id = msg.value),
                            fontSize = 16.sp,
                            color = Color.White,
                            modifier = Modifier
                                .overlayEffect()
                                .alpha(0.4f)
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
    translationLambda: () -> Boolean,
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
            Spacer(modifier = Modifier
                .statusBarsHeight(if (splitWindow) 16.dp else if (compactWindow) 94.dp else 110.dp)
                .onSizeChanged { onViewportInsetChanged(it.height) })

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
            includeTranslation = SettingsLibrary.NowPlayingTranslation
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
            val sheetBackground = Color.White withNight Color.Black
            val listHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 280)
                .coerceIn(72, 430).dp
            Column(Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = listHeight)
                        .dialogListFade(sheetBackground),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 18.dp)
                ) {
                    itemsIndexed(shareEntries, key = { index, _ -> index }, contentType = { _, _ -> "lyric" }) { index, line ->
                        val checked = index in selected.value
                        val foreground = if (checked) Color.White else MaterialTheme.colorScheme.onSurface
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(rowShape)
                                .background(if (checked) yos.music.player.ui.theme.primary else MaterialTheme.colorScheme.surface)
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
        var showPlaylistPicker = remember { mutableStateOf(false) }
        var showDetail = remember { mutableStateOf(false) }
        var showArtistPicker = remember { mutableStateOf(false) }
        var showSleepTimerDialog = remember { mutableStateOf(false) }
        var showSleepTimerWarning = remember { mutableStateOf(false) }
        val menuOpenCount = remember { mutableIntStateOf(0) }
        var btnPos = remember { mutableStateOf(Offset.Zero) }

        val menuOpen = showMoreMenu.value || showPlaylistPicker.value || showDetail.value

        Box(
            modifier = menuModifier
                .graphicsLayer {
                    rotationZ = 90f
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .onGloballyPositioned { btnPos.value = it.localToRoot(Offset.Zero) }
                .clickable(
                    onClick = { showMoreMenu.value = true; menuOpenCount.intValue++ },
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
                subTitle = "${music.artistsName ?: defaultArtistsName}${music.album?.let { " · $it" } ?: ""}",
                content = { dismiss ->
                    val items = listOf(
                        Triple("添加到歌单", Icons.Filled.Add, { dismiss.after { showPlaylistPicker.value = true } }),
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
                                dismiss.after { showSleepTimerDialog.value = true }
                            }
                        ),
                        Triple("歌曲信息", Icons.Outlined.Info, { dismiss.after { showDetail.value = true } })
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
                },
                horizontalTitle = true,
                onDismissRequest = { showMoreMenu.value = false }
            )
            }
        }

        if (showPlaylistPicker.value && music != null) {
            PlaylistPickerDialog(music, onDismiss = { showPlaylistPicker.value = false })
        }

        if (showDetail.value && music != null) {
            SongDetailDialog(music, onDismiss = { showDetail.value = false })
        }

        if (showSleepTimerDialog.value) {
            SleepTimerDialog(
                currentSongUri = music?.uri?.toString(),
                onDismiss = { showSleepTimerDialog.value = false }
            )
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RowScope.AirPlay(compactWindow: Boolean = false) {
    val contextCompose = LocalContext.current
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val connectedDevices =
        remember("AirPlay_connectedDevices") { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    val audioDeviceName = remember("AirPlay_audioDeviceName") { mutableStateOf("") }
    val showName = remember("AirPlay_showName") { mutableStateOf(false) }

    YosWrapper {
        DisposableEffect(Unit) {
            val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction("yos.music.player.BLUETOOTH_STATUS_REFRESH")
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action
                    if (action == BluetoothDevice.ACTION_ACL_CONNECTED || action == BluetoothDevice.ACTION_ACL_DISCONNECTED || action == "yos.music.player.BLUETOOTH_STATUS_REFRESH") {
                        if (ActivityCompat.checkSelfPermission(
                                contextCompose,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        connectedDevices.value =
                            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

                        val thisName =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                connectedDevices.value.firstOrNull { it.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO && it.isConnected() }?.alias
                            } else {
                                connectedDevices.value.firstOrNull { it.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO && it.isConnected() }?.name
                            }
                        showName.value = thisName != null
                        if (thisName != null) {
                            audioDeviceName.value = thisName.trim()
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                contextCompose.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                contextCompose.registerReceiver(receiver, filter)
            }

            if (ActivityCompat.checkSelfPermission(
                    contextCompose,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                connectedDevices.value = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                val thisName =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        connectedDevices.value.firstOrNull { it.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO && it.isConnected() }?.alias
                    } else {
                        connectedDevices.value.firstOrNull { it.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO && it.isConnected() }?.name
                    }
                showName.value = thisName != null
                if (thisName != null) {
                    audioDeviceName.value = thisName.trim()
                }
            }

            onDispose {
                runCatching {
                    contextCompose.unregisterReceiver(receiver)
                }
            }
        }
    }

    YosWrapper {
        Column(
            modifier = Modifier
                .then(if (compactWindow) Modifier.height(36.dp) else
                    Modifier.heightIn(min = 53.dp).navigationBarsHeight(48.dp))
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = showName.value, transitionSpec = {
                    (scaleIn(initialScale = 0.3f) + fadeIn()).togetherWith(
                        scaleOut(
                            targetScale = 0.3f
                        ) + fadeOut()
                    )
                }, contentAlignment = Alignment.Center) {
                    if (it) {
                        Icon(
                            painterResource(id = R.drawable.ic_earphone),
                            contentDescription = null,
                            modifier = Modifier
                                .size(27.dp)
                        )
                    } else {
                        Icon(
                            painterResource(id = R.drawable.ic_nowplaying_airplay),
                            contentDescription = null,
                            modifier = Modifier
                                .size(21.5.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(showName.value && !compactWindow, enter = scaleIn(initialScale = 0.3f) + fadeIn(), exit = scaleOut(
                targetScale = 0.3f
            ) + fadeOut()) {
                Text(
                    text = audioDeviceName.value,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun BluetoothDevice.isConnected(): Boolean {
    return runCatching {
        val isConnectedMethod =
            BluetoothDevice::class.java.getMethod("isConnected")
        isConnectedMethod.isAccessible = true
        isConnectedMethod.invoke(this) as Boolean
    }.getOrDefault(false)
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControl(
    compactWindow: Boolean = false,
    renderActive: Boolean,
    isPlayingLambda: () -> Boolean,
    isPlayingOnChanged: (Boolean) -> Unit,
    onPrevious: () -> Unit,
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
    val restoredStatus = MediaController.restoredPlayStatus.value
    val initialProgress = remember(trackKey, restoredStatus) { MediaController.playbackProgress() }
    val playingDuration = remember(trackKey, restoredStatus) { mutableLongStateOf(initialProgress.durationMs) }
    val context = LocalContext.current
    val playedTime = remember(trackKey, restoredStatus) { mutableStateOf(formatTime(initialProgress.positionMs / 1000L)) }
    val remainingTime = remember(trackKey, restoredStatus) {
        mutableStateOf("-${formatTime(((initialProgress.durationMs - initialProgress.positionMs) / 1000L).coerceAtLeast(0L))}")
    }
    val sliderPosition = remember(trackKey, restoredStatus) { mutableFloatStateOf(initialProgress.positionMs.toFloat()) }
    val isSliding = remember(trackKey) { mutableStateOf(false) }
    val seekCancelled = remember(trackKey) { mutableStateOf(false) }
    val cancelAreaTop = remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    val progressClock = remember(trackKey, restoredStatus) { NativeLyricClock(initialProgress.positionMs.toFloat()) }
    val onWhileRef = rememberUpdatedState(onWhile)

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
                    LaunchedEffect(lifecycleOwner, renderActive, trackKey, restoredStatus) {
                        if (!renderActive) return@LaunchedEffect
                        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            progressClock.restartFrames()
                            var nextSampleAt = 0L
                            var nextControlsAt = 0L
                            var trackId = mediaControl?.currentMediaItem?.mediaId
                            var lastSeconds = -1L
                            var lastDuration = -1L
                            while (isActive) {
                                val frame = withFrameMillis { it }
                                var report: Float? = null
                                if (frame >= nextSampleAt) {
                                    nextSampleAt = frame + 80L
                                    val sample = MediaController.playbackProgress()
                                    playingDuration.longValue = sample.durationMs
                                    report = sample.positionMs.toFloat()
                                    val currentTrack = mediaControl?.currentMediaItem?.mediaId
                                    if (trackId != currentTrack) {
                                        trackId = currentTrack
                                        progressClock.seek(report, frame)
                                    }
                                }
                                if (isSliding.value && !seekCancelled.value) {
                                    progressClock.seek(sliderPosition.floatValue, frame)
                                } else {
                                    sliderPosition.floatValue = progressClock.frame(frame,
                                        mediaControl?.isPlaying == true,
                                        mediaControl?.playbackParameters?.speed ?: 1f, report)
                                        .let { position ->
                                            if (playingDuration.longValue > 0L) position.coerceIn(0f, playingDuration.longValue.toFloat())
                                            else position.coerceAtLeast(0f)
                                        }
                                    val seconds = sliderPosition.floatValue.toLong() / 1000L
                                    if (seconds != lastSeconds || playingDuration.longValue != lastDuration) {
                                        playedTime.value = formatTime(seconds)
                                        remainingTime.value = "-${formatTime((playingDuration.longValue / 1000L - seconds).coerceAtLeast(0L))}"
                                        lastSeconds = seconds
                                        lastDuration = playingDuration.longValue
                                    }
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
                    fun restoreLiveProgress() {
                        val live = MediaController.playbackProgress().positionMs.toFloat()
                        sliderPosition.floatValue = live
                        progressClock.seek(live, android.os.SystemClock.uptimeMillis())
                        playedTime.value = formatTime(live.toLong() / 1000L)
                        remainingTime.value = "-${formatTime(((playingDuration.longValue - live.toLong()) / 1000L).coerceAtLeast(0L))}"
                    }
                    PlaybackSeekTrack(
                        gestureKey = trackKey,
                        position = { sliderPosition.floatValue },
                        duration = { playingDuration.longValue },
                        dragging = isSliding.value,
                        cancelAreaTop = { cancelAreaTop.floatValue },
                        onStart = {
                            seekCancelled.value = false
                            isSliding.value = true
                            onSlider()
                        },
                        onPreview = { position, cancelled ->
                            if (cancelled && !seekCancelled.value) restoreLiveProgress()
                            seekCancelled.value = cancelled
                            if (!cancelled) {
                                sliderPosition.floatValue = position
                                playedTime.value = formatTime(position.toLong() / 1000L)
                                remainingTime.value = "-${formatTime(((playingDuration.longValue - position.toLong()) / 1000L).coerceAtLeast(0L))}"
                            }
                            onSlider()
                        },
                        onFinish = { position ->
                            if (position == null) {
                                restoreLiveProgress()
                            } else {
                                Vibrator.longClick(context)
                                sliderPosition.floatValue = position
                                progressClock.seek(position, android.os.SystemClock.uptimeMillis())
                                onSeek(position)
                            }
                            isSliding.value = false
                            seekCancelled.value = false
                            onSlider()
                        },
                        modifier = Modifier.padding(horizontal = 4.dp).overlayEffect().alpha(0.45f)
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
                                )
                            }

                            Spacer(modifier = Modifier.width(if (compactWindow) 20.dp else rememberAdaptive(43)))

                            Box(
                                modifier = Modifier
                                    .size(if (compactWindow) 44.dp else rememberAdaptive(58))
                                    .clickable(
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
                        VolumeSlider(context = context, onSlider)
                    }
                }
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

                        AirPlay(compactWindow = compactWindow)

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

                // 边距填充
                /*YosWrapper {
                    Spacer(modifier = Modifier.navigationBarsHeight(5.dp))
                }*/
                // 为显示设备名称，迁移到 AirPlay 底部处理
                }
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .height(if (compactWindow) 44.dp else 64.dp)
                    .onGloballyPositioned { cancelAreaTop.floatValue = it.boundsInRoot().top },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(isSliding.value, enter = fadeIn(tween(100)), exit = fadeOut(tween(100))) {
                    Box(
                        Modifier.fillMaxSize().background(
                            if (seekCancelled.value) Color(0xFF9C3238) else Color(0xEB242424),
                            RoundedCornerShape(18.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (seekCancelled.value) "松开取消进度调整" else "手指移到这里撤销进度调整",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VocalSeparationControl(
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
    // grows the control and only then lowers the liquid; closing fills it,
    // fades it out, and only then collapses the control.
    LaunchedEffect(state.enabled) {
        if (state.enabled) {
            liquidFraction.snapTo(1f)
            liquidAlpha.snapTo(1f)
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
    val controlElevation by animateDpAsState(
        targetValue = if (pressed && !state.enabled) 24.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 220f),
        label = "MssControlElevation"
    )
    val controlAlpha by animateFloatAsState(
        targetValue = if (state.available) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 405f),
        label = "MssControlVisibility"
    )
    val adjustmentRangePx = with(density) { 60.dp.toPx() }
    val controlHeight = (34f + 60f * expansion.value).dp
    val smoothShape = remember { YosRoundedCornerShape(roundSize = 9.dp) }

    Box(
        modifier = modifier
            .width(34.dp)
            .height(controlHeight)
            .graphicsLayer {
                scaleX = controlScale
                scaleY = controlScale
                alpha = controlAlpha
            }
            .shadow(controlElevation, smoothShape, clip = false)
            .clip(smoothShape)
            .background(Color.White.copy(alpha = 0.10f))
            .pointerInput(state.available, state.enabled) {
                if (!state.available || !state.enabled) return@pointerInput
                var dragGain = selectedGainState.value
                detectVerticalDragGestures(
                    onDragStart = { dragGain = selectedGainState.value },
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
                    // white above the liquid, dark violet within the bright fill.
                    val fillTop = controlHeight.toPx() * (1f - liquidFraction.value.coerceIn(0f, 1f))
                    val iconTop = controlHeight.toPx() - 3.dp.toPx() - size.height
                    val localFillTop = (fillTop - iconTop).coerceIn(0f, size.height)
                    clipRect(top = localFillTop) {
                        drawRect(
                            color = androidx.compose.ui.graphics.lerp(Color.White, Color(0xFF3C2455), liquidAlpha.value.coerceIn(0f, 1f)),
                            blendMode = BlendMode.SrcIn
                        )
                    }
                }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSlider(context: Context, onSlider: () -> Unit) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val sliderPosition =
        remember("VolumeSlider_sliderPosition") { mutableFloatStateOf(currentVolume / maxVolume.toFloat()) }
    val sliding = remember("VolumeSlider_sliding") {
        mutableStateOf(false)
    }

    val volumeChangeReceiver = remember("VolumeSlider_volumeChangeReceiver") {
        VolumeChangeReceiver { newVolume ->
            sliderPosition.floatValue = newVolume / maxVolume.toFloat()
        }
    }
    val intentFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")

    DisposableEffect(Unit) {
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

        YosWrapper {
            val trackHeight = animateDpAsState(
                targetValue = if (sliding.value) 10.dp else 7.dp,
                animationSpec = tween(150)
            )
            val trackWidthPx = remember { mutableFloatStateOf(300f) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 1.5.dp, end = 5.dp)
                    .height(14.dp)
                    .onSizeChanged { trackWidthPx.floatValue = it.width.toFloat() }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                sliding.value = true
                            },
                            onDragEnd = {
                                val targetStep = ((sliderPosition.floatValue * maxVolume) + 0.5f).toInt().coerceIn(0, maxVolume)
                                val snapped = targetStep.toFloat() / maxVolume
                                sliderPosition.floatValue = snapped
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
                                Vibrator.longClick(context)
                                sliding.value = false
                            },
                            onDragCancel = {
                                sliding.value = false
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                if (maxVolume <= 0) return@detectHorizontalDragGestures
                                val rawDelta = dragAmount / trackWidthPx.floatValue.coerceAtLeast(100f)
                                val rawVolume = (sliderPosition.floatValue + rawDelta).coerceIn(0f, 1f)
                                sliderPosition.floatValue = rawVolume
                                val step = ((rawVolume * maxVolume) + 0.5f).toInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
                                onSlider()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val animatedProgress = if (sliding.value) {
                    sliderPosition.floatValue
                } else {
                    animateFloatAsState(
                        targetValue = sliderPosition.floatValue,
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                        visibilityThreshold = 0.0001f
                    ).value
                }

                Canvas(Modifier.fillMaxWidth().height(trackHeight.value)) {
                    drawPlaybackProgress(animatedProgress)
                }
            }
        }
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
