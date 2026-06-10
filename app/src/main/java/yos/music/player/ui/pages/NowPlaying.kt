@file:Suppress("DEPRECATION")

package yos.music.player.ui.pages

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
import androidx.compose.animation.AnimatedContent
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
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
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
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
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.code.utils.player.FadeExo.fadePlay
import yos.music.player.data.libraries.FavPlayListLibrary
import yos.music.player.data.libraries.MusicLibrary.toMediaItem
import yos.music.player.data.libraries.PlayListLibrary

import yos.music.player.ui.widgets.basic.PlaylistPickerDialog
import yos.music.player.ui.widgets.basic.OptionDialog
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
import yos.music.player.ui.pages.NowPlayingPage.Album
import yos.music.player.ui.pages.NowPlayingPage.Lyric
import yos.music.player.ui.pages.NowPlayingPage.PlayingList
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.rememberAdaptive
import yos.music.player.ui.widgets.YosLyricView
import yos.music.player.ui.widgets.effects.YosFloatingLight
import yos.music.player.ui.widgets.audio.MusicQualityIndicator
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImageWithCache
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.YosWrapper
import yos.music.player.ui.widgets.effects.ShadowType
import yos.music.player.ui.widgets.effects.overlayEffect


@Stable
object NowPlayingPage {
    const val Album = "Album"
    const val PlayingList = "PlayingList"
    const val Lyric = "Lyric"
}

private const val ShareAlbumKey = "album"
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        contentColor = Color.White,
        color = Color.Black
    ) {
        val context = LocalContext.current

        val lrcEntries: MutableState<List<YosLyricLine>> =
            MediaViewModelObject.lrcEntries
        val bitmap: MutableState<Uri?> = MediaViewModelObject.bitmap

        val lastClickTime = rememberSaveable(key = "NowPlaying_lastClickTime") {
            mutableLongStateOf(0L)
        }

        val showArtistPickerFromAlbum = remember { mutableStateOf(false) }
        val showControl = rememberSaveable(key = "NowPlaying_showControl") {
            mutableStateOf(true)
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

        val controlsHeightPx = remember("NowPlaying_controlsHeightPx") { mutableIntStateOf(0) }

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


        // 背景流光
        YosWrapper {
            /*BlendBackgroundView(
        bitmapLambda = { bitmap.value },
        isPlayingLambda = { isPlaying.value },
        nowPage = { nowPage.value }
    )*/

            YosFloatingLight(
                album = { bitmap.value },
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

            val scope = rememberCoroutineScope()

            val alphaAnim = remember { Animatable(0f) }

            YosWrapper {
                LaunchedEffect(nowPageLambda()) {
                    val targetAlpha = if (nowPageLambda() == Lyric) 1f else 0f
                    scope.launch {
                        alphaAnim.animateTo(targetAlpha)
                    }
                }
            }

            val translationButtonEnabled = remember("NowPlaying_translationButtonEnabled") {
                derivedStateOf { showControl.value && alphaAnim.value != 0f }
            }


            // 歌词
            YosWrapper {

                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy =
                                CompositingStrategy.ModulateAlpha
                            this.alpha = alphaAnim.value
                        }
                ) {

                    Lyric(
                        lrcEntries = { lrcEntries.value },
                        weightLambda = { showControl.value },
                        controlsHeightPxLambda = { controlsHeightPx.intValue },
                        translationLambda = { true },
                        showMiniPlayer = showMiniPlayer,
                        userScrollEnabled = nowPageLambda() == NowPlayingPage.Lyric,
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
                    Crossfade(
                        targetState = nowPageLambda(),
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 22.dp)
                    ) {
                        //println("nowPage: ${nowPageLambda()}")
                        //println("nowPageIt: $it")
                        when (it) {
                            Album ->
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .clickable(enabled = false, onClick = {})
                                ) {
                                    YosWrapper {
                                        Column(Modifier.fillMaxHeight(0.595f)) {
                                            // 仅在 NowPlaying 对用户可见时才播放封面交叉渐变
                                            val isVisible = nowPageLambda() == Album && !showMiniPlayer()

                                            Album(
                                                modifier = Modifier.sharedElementWithCallerManagedVisibility(
                                                    sharedContentState = rememberSharedContentState(
                                                        key = ShareAlbumKey
                                                    ),
                                                    visible = isVisible
                                                ),
                                                albumUrl = { thisMusicPlaying.value?.thumb },
                                                isPlaying = isPlayingStatusLambda,
                                                isVisible = { isVisible }
                                            )
                                            AnimatedContent(
                                                targetState = thisMusicPlaying.value,
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
                                                        ActionButtonsRow(navController = navController, closeSheet = closeSheet) {
                                                            it
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                            Lyric ->
                                Column(Modifier.fillMaxSize()) {
                                    YosWrapper {
                                        val isVisible = nowPageLambda() == Lyric
                                        PlayingBar(
                                            modifier = Modifier.sharedElementWithCallerManagedVisibility(
                                                sharedContentState = rememberSharedContentState(
                                                    key = ShareAlbumKey
                                                ),
                                                visible = isVisible
                                            ),
                                            albumUrlLambda = {
                                                thisMusicPlaying.value?.thumb
                                            },
                                            musicPlayingLambda = { thisMusicPlaying.value }) {
                                            nowPageOnChanged(Album)
                                        }
                                    }
                                }

                            PlayingList ->
                                YosWrapper {
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .clickable(enabled = false, onClick = {})
                                    ) {
                                        val isVisible = nowPageLambda() == PlayingList
                                        PlayingBar(
                                            modifier = Modifier.sharedElementWithCallerManagedVisibility(
                                                sharedContentState = rememberSharedContentState(
                                                    key = ShareAlbumKey
                                                ),
                                                visible = isVisible
                                            ),
                                            albumUrlLambda = {
                                                thisMusicPlaying.value?.thumb
                                            },
                                            musicPlayingLambda = { thisMusicPlaying.value }) {
                                            nowPageOnChanged(Album)
                                        }
                                        YosWrapper {
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

            // 音乐控制
            YosWrapper {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding(), verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        Modifier
                            /*.fillMaxHeight(0.385f)*/
                            .fillMaxHeight(0.437f)
                            .fillMaxWidth()
                    ) {

                        YosWrapper {
                            if (showControl.value) {
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
                                    enter = fadeIn(tween(durationMillis = 260, easing = EaseOutQuart)) + expandVertically(
                                        animationSpec = tween(durationMillis = 260, easing = EaseOutQuart),
                                        expandFrom = Alignment.Top,
                                        initialHeight = { (it / 1.4).toInt() }),
                                    exit = fadeOut(tween(durationMillis = 260, easing = EaseOutQuart)) + shrinkVertically(
                                        animationSpec = tween(durationMillis = 260, easing = EaseOutQuart),
                                        shrinkTowards = Alignment.Top,
                                        targetHeight = { (it / 1.4).toInt() })
                                ) {
                                    PlayerControl(
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
                                            if (nowPageLambda() == Lyric) {
                                                nowPageOnChanged(Album)
                                            } else {
                                                nowPageOnChanged(Lyric)
                                            }
                                        },
                                        onPlaylist = {
                                            if (nowPageLambda() == PlayingList) {
                                                nowPageOnChanged(Album)
                                            } else {
                                                nowPageOnChanged(PlayingList)
                                            }
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
                                            .padding(top = rememberAdaptive(52))
                                            .onSizeChanged { controlsHeightPx.intValue = it.height },
                                        onWhile = {
                                            shuffleModeEnabled.value =
                                                shuffleEnabled.value
                                            repeatMode.intValue =
                                                mediaControl?.repeatMode ?: REPEAT_MODE_OFF
                                        })
                                }
                            }
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
                            modifier = Modifier.size(52.dp), cornerRadius = 6.dp,
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
                                    yos.music.player.data.objects.LibraryObject.setTargetArtistName(artist)
                                    closeSheet()
                                    navController.toUI(UI.ArtistInfo)
                                    dismiss()
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

    }
}

@Composable
private fun ColumnScope.Album(
    modifier: Modifier,
    albumUrl: () -> Uri?,
    isPlaying: () -> Boolean,
    isSwitching: () -> Boolean = { false },
    isVisible: () -> Boolean = { true }
) = Box(
    Modifier
        .weight(1f)
        .padding(top = 20.dp)
        .padding(horizontal = 15.dp)
        .padding(bottom = 33.dp),
    contentAlignment = Alignment.BottomCenter
) {
    val albumShape = YosRoundedCornerShape(8)
    val currentUrl = remember { mutableStateOf(albumUrl()) }

    LaunchedEffect(albumUrl()) {
        val newUrl = albumUrl() ?: return@LaunchedEffect
        if (newUrl != currentUrl.value) {
            currentUrl.value = newUrl
        }
    }

    YosWrapper {
        val dp = 7.dp
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = dp, end = dp, bottom = dp)
                .then(modifier)
                .drawBehind {
                    val blur = size.width * 0.1f
                    val cr = size.minDimension * 0.04f
                    val rect = android.graphics.RectF(0f, 0f, size.width, size.height)
                    val p = android.graphics.Path().apply { addRoundRect(rect, cr, cr, android.graphics.Path.Direction.CW) }
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((0.36f * 255).toInt(), 0, 0, 0)
                        maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    drawContext.canvas.nativeCanvas.drawPath(p, paint)
                }
        ) {
            // 使用 AnimatedContent 实现封面交叉渐变
            AnimatedContent(
                targetState = currentUrl.value,
                transitionSpec = {
                    // 新图片淡入,旧图片淡出,同时进行
                    fadeIn(animationSpec = tween(600)) togetherWith 
                    fadeOut(animationSpec = tween(600))
                },
                label = "AlbumCoverCrossfade"
            ) { url ->
                url?.let {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(it).crossfade(false).build(),
                        contentDescription = null, 
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(albumShape)
                    )
                } ?: AsyncImage(
                    model = R.drawable.placeholder_music_default_artwork,
                    contentDescription = null, 
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(albumShape)
                )
            }
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
    thisMusicPlayingLambda: () -> YosMediaItem?
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
                .fillMaxHeight(0.545f),
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
    lrcEntries: () -> List<YosLyricLine>,
    weightLambda: () -> Boolean,
    controlsHeightPxLambda: () -> Int,
    translationLambda: () -> Boolean,
    mainViewModel: MainViewModel,
    mediaViewModel: MediaViewModel,
    showMiniPlayer: () -> Boolean,
    userScrollEnabled: Boolean = true,
    onBackClick: () -> Unit
) = YosWrapper {

    val context = LocalContext.current
    val controlsProgress = animateFloatAsState(
        targetValue = if (weightLambda()) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = EaseOutQuart)
    )


    Column(
        Modifier
            .fillMaxSize()
    ) {
        YosWrapper {

            Spacer(modifier = Modifier.statusBarsHeight(110.dp))

            // 冷启动时不渲染歌词控件，等用户展开播放页面后再渲染，避免主线程卡死
            val hasEverExpanded = remember { mutableStateOf(!showMiniPlayer()) }
            if (!showMiniPlayer()) hasEverExpanded.value = true

            if (hasEverExpanded.value) {
            YosLyricView(
                //mediaViewModel = mediaViewModel,
                lrcEntriesLambda = lrcEntries,
                liveTimeLambda = {
                    (mediaControl?.currentPosition ?: 0).toInt()
                },
                userScrollEnabled = userScrollEnabled,
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
                modifier = Modifier.drawWithCache {
                    onDrawWithContent {
                        val overlayPaint = Paint().apply {
                            blendMode = BlendMode.Plus
                        }
                        val rect = Rect(0f, 0f, size.width, size.height)
                        val canvas = this.drawContext.canvas

                        canvas.saveLayer(rect, overlayPaint)

                        val p = controlsProgress.value.coerceIn(0f, 1f)
                        val fallbackMaskHeightPx = size.height * 0.38f
                        val measuredMaskHeightPx = controlsHeightPxLambda().toFloat()
                        val maskHeightPx = ((if (measuredMaskHeightPx > 0f) measuredMaskHeightPx else fallbackMaskHeightPx) * p)
                            .coerceIn(0f, size.height)
                        val extraPx = 80.dp.toPx()
                        val fadePx = 70.dp.toPx()

                        val cutStart = (1f - ((maskHeightPx + extraPx) / size.height)).coerceIn(0f, 1f)
                        val fadeLen = (fadePx / size.height).coerceIn(0.001f, 0.15f)
                        val cutFadeEnd = (cutStart + fadeLen).coerceIn(cutStart, 1f)

                        val colors = if (p == 0f) {
                            arrayOf(
                                0f to Color.Transparent,
                                0.06f to Color(0x59000000),
                                0.14f to Color.Black,
                                1f to Color.Black
                            )
                        } else {
                            arrayOf(
                                0f to Color.Transparent,
                                0.06f to Color(0x59000000),
                                0.14f to Color.Black,
                                cutStart to Color.Black,
                                cutFadeEnd to Color.Transparent,
                                1f to Color.Transparent
                            )
                        }

                        drawContent()

                        drawRect(
                            brush = Brush.verticalGradient(colorStops = colors),
                            blendMode = BlendMode.DstIn
                        )

                        canvas.restore()
                    }
                },
                onBackClick = onBackClick
            )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionButtonsRow(navController: NavController? = null, closeSheet: () -> Unit = {}, musicPlayingLambda: () -> YosMediaItem?) {
    Row(
        modifier = Modifier
            .overlayEffect(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dp = 28.dp

        val context = LocalContext.current

        Box(
            modifier = Modifier
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
            modifier = Modifier
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
                        Triple("添加到歌单", Icons.Filled.Add, { dismiss(); showPlaylistPicker.value = true }),
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
                                dismiss()
                                showSleepTimerDialog.value = true
                            }
                        ),
                        Triple("歌曲信息", Icons.Outlined.Info, { dismiss(); showDetail.value = true })
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
                                    yos.music.player.data.objects.LibraryObject.setTargetArtistName(artist)
                                    navController.toUI(UI.ArtistInfo)
                                    dismiss()
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
    albumUrlLambda: () -> Uri?,
    musicPlayingLambda: () -> YosMediaItem?,
    onAlbumClick: () -> Unit
) = YosWrapper {
    val stickyUrl = remember { mutableStateOf(albumUrlLambda()) }
    val current = albumUrlLambda()
    if (current != null) stickyUrl.value = current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rememberAdaptive(28))
            .padding(top = 22.dp)
            .height(70.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        stickyUrl.value?.let { url ->
            val smallShape = YosRoundedCornerShape(8)
            Box(
                modifier = modifier.size(69.dp)
                    .drawBehind {
                        val blur = size.width * 0.1f
                        val cr = size.minDimension * 0.04f
                        val rect = android.graphics.RectF(0f, 0f, size.width, size.height)
                        val p = android.graphics.Path().apply { addRoundRect(rect, cr, cr, android.graphics.Path.Direction.CW) }
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((0.36f * 255).toInt(), 0, 0, 0)
                            maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
                        }
                        drawContext.canvas.nativeCanvas.drawPath(p, paint)
                    }
                    .clip(smallShape)
                    .clickable(MutableInteractionSource(), null) { onAlbumClick() }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 12.dp, end = 15.dp)
        ) {
            Text(
                text = musicPlayingLambda()?.title ?: defaultTitle,
                fontSize = 16.5.sp,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                fontWeight = FontWeight.Bold,
                lineHeight = 16.5.sp
            )
            Text(
                text = musicPlayingLambda()?.artistsName
                    ?: defaultArtistsName,
                fontSize = 15.sp,
                modifier = Modifier
                    .overlayEffect()
                    .basicMarquee(iterations = Int.MAX_VALUE),
                maxLines = 1,
                color = Color.White.copy(alpha = 0.35f),
                fontWeight = FontWeight.SemiBold
            )
        }

        YosWrapper {
            ActionButtonsRow(musicPlayingLambda = musicPlayingLambda)
        }
    }

}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RowScope.AirPlay() {
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
                .heightIn(min = 53.dp)
                .navigationBarsHeight(48.dp)
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

            AnimatedVisibility(showName.value, enter = scaleIn(initialScale = 0.3f) + fadeIn(), exit = scaleOut(
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
    val playingDuration = rememberSaveable(key = "PlayerControl_playingDuration") {
        mutableLongStateOf(0L)
    }
    val playingPosition = rememberSaveable(key = "PlayerControl_playingPosition") {
        mutableLongStateOf(0L)
    }
    val context = LocalContext.current
    val playedTime = rememberSaveable(key = "PlayerControl_playedTime") { mutableStateOf("0:00") }
    val remainingTime =
        rememberSaveable(key = "PlayerControl_remainingTime") { mutableStateOf("-0:00") }
    val sliderPosition = remember("PlayerControl_sliderPosition") { mutableFloatStateOf(0f) }
    val isSliding = remember("PlayerControl_isSliding") {
        mutableStateOf(false)
    }

    YosWrapper {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = rememberAdaptive(25))
                .padding(bottom = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            YosWrapper {
                // 启动作用
                YosWrapper {
                    val lifecycleState =
                        LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()

                    LaunchedEffect(Unit) {
                        var lastPosition = 0L
                        while (true) {
                            //isPlaying.value = /*mediaControl?.isPlaying ?: false*/ FadeExo.targetStatus != 0
                            if (lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED)) {
                                playingDuration.longValue = mediaControl?.duration ?: 0
                                playingPosition.longValue = mediaControl?.currentPosition ?: 0

                                if (!isSliding.value && playingDuration.longValue > 0L) {
                                    val totalSeconds =
                                        playingPosition.longValue.coerceAtLeast(0) / 1000
                                    if (totalSeconds != lastPosition) {
                                        playedTime.value = formatTime(totalSeconds)

                                        sliderPosition.floatValue =
                                            playingPosition.longValue.coerceAtLeast(0).toFloat()

                                        val remainingSeconds =
                                            playingDuration.longValue.coerceAtLeast(0) / 1000 - totalSeconds
                                        remainingTime.value = "-${formatTime(remainingSeconds)}"
                                        lastPosition = totalSeconds
                                    }
                                }

                                onWhile()
                            }

                            delay(700)
                        }
                    }
                }

                // 进度条
                YosWrapper {
                    val trackHeight = animateDpAsState(
                        targetValue = if (isSliding.value) 10.dp else 7.dp,
                        animationSpec = tween(150)
                    )
                    val trackWidthPx = remember { mutableFloatStateOf(300f) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .overlayEffect()
                            .alpha(0.45f)
                            .height(14.dp)
                            .onSizeChanged { trackWidthPx.floatValue = it.width.toFloat() }
                            .pointerInput(playingDuration.longValue) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        isSliding.value = true
                                    },
                                    onDragEnd = {
                                        Vibrator.longClick(context)
                                        onSeek(sliderPosition.floatValue)
                                        isSliding.value = false
                                    },
                                    onDragCancel = {
                                        isSliding.value = false
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        if (playingDuration.longValue <= 0L) return@detectHorizontalDragGestures
                                        val deltaMs = dragAmount / trackWidthPx.floatValue.coerceAtLeast(100f) * playingDuration.longValue.toFloat()
                                        val newPosition = (sliderPosition.floatValue + deltaMs)
                                            .coerceIn(0f, playingDuration.longValue.toFloat())
                                        sliderPosition.floatValue = newPosition
                                        val totalSeconds = newPosition.toLong() / 1000
                                        playedTime.value = formatTime(totalSeconds)
                                        val remainingSeconds = playingDuration.longValue / 1000 - totalSeconds
                                        remainingTime.value = "-${formatTime(remainingSeconds)}"
                                        onSlider()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val progress = if (playingDuration.longValue > 0L) {
                            (sliderPosition.floatValue / playingDuration.longValue.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(trackHeight.value)
                                .clip(YosRoundedCornerShape(100))
                                .background(Color.White.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .background(Color.White)
                            )
                        }
                    }
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
                            .padding(top = 25.dp, bottom = 35.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(rememberAdaptive(61))
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

                            Spacer(modifier = Modifier.width(rememberAdaptive(43)))

                            Box(
                                modifier = Modifier
                                    .size(rememberAdaptive(58))
                                    .clickable(
                                        indication = ripple(bounded = false),
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        val nowPlaying = isPlayingLambda()
                                        if (nowPlaying) mediaControl?.fadePause()
                                        else mediaControl?.fadePlay()
                                        isPlayingOnChanged(!nowPlaying)
                                        Vibrator.click(context)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(targetState = isPlayingLambda(), transitionSpec = {
                                    (scaleIn(initialScale = 0.3f) + fadeIn()).togetherWith(
                                        scaleOut(
                                            targetScale = 0.3f
                                        ) + fadeOut()
                                    )
                                }) {
                                    if (it) {
                                        Icon(
                                            painterResource(id = R.drawable.ic_nowplaying_pause),
                                            contentDescription = "Pause",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp)
                                        )
                                    } else {
                                        Icon(
                                            painterResource(id = R.drawable.ic_nowplaying_play),
                                            contentDescription = "Play",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(9.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(rememberAdaptive(43)))
                            Box(
                                modifier = Modifier
                                    .size(rememberAdaptive(61))
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
            }

            // 音量调节
            YosWrapper {
                if (SettingsLibrary.NowPlayingShowVolumeBar) {
                    VolumeSlider(context = context, onSlider)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

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
                                onClick = { onLyrics() },
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

                    AirPlay()

                    Spacer(modifier = Modifier.weight(0.1f))

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .weight(1f)
                            .clickable(
                                onClick = { onPlaylist() },
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight.value)
                        .clip(YosRoundedCornerShape(100))
                        .background(Color.White.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .background(Color.White)
                    )
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
