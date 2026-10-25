@file:OptIn(ExperimentalSharedTransitionApi::class)

package yos.music.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.PlayerArtworkBridge
import yos.music.player.ui.widgets.PlayerArtworkBridgeOverlay
import yos.music.player.ui.widgets.MINI_ARTWORK_ENDPOINT
import yos.music.player.data.objects.LibraryObject
import yos.music.player.data.libraries.artistsName
import yos.music.player.ui.widgets.rememberNowPlayingArtwork
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.akane.libphonograph.hasScopedStorageWithMediaTypes
import yos.music.player.code.MediaController
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.code.utils.player.FadeExo.fadePlay
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.models.MainViewModel
import yos.music.player.data.models.MediaViewModel
import yos.music.player.data.objects.MediaViewModelObject
import yos.music.player.ui.UI
import yos.music.player.ui.UI.Settings.Companion.ExoplayerSetting
import yos.music.player.ui.pages.HomeNav
import yos.music.player.ui.pages.ListenStatsPage
import yos.music.player.ui.pages.NowPlaying
import yos.music.player.ui.pages.NowPlayingPage.Album
import yos.music.player.ui.pages.library.Library
import yos.music.player.ui.pages.library.LibrarySectionPane
import yos.music.player.ui.pages.library.NormalMusic
import yos.music.player.ui.pages.library.albums.AlbumInfo
import yos.music.player.ui.pages.library.albums.LocalAlbums
import yos.music.player.ui.pages.library.artists.ArtistInfo
import yos.music.player.ui.pages.library.artists.LocalArtists
import yos.music.player.ui.pages.library.playlists.PlayLists
import yos.music.player.ui.pages.settings.Settings
import yos.music.player.ui.pages.settings.audio.exoPlayer.ExoPlayerSettings
import yos.music.player.ui.pages.settings.audio.exoPlayer.MediaCodec
import yos.music.player.ui.pages.settings.extend.statusBarLyric.LyricGetter
import yos.music.player.ui.pages.settings.library.LibraryOverview
import yos.music.player.ui.pages.settings.others.About
import yos.music.player.ui.pages.settings.performance.LyricSetting
import yos.music.player.ui.pages.settings.performance.NotificationSetting
import yos.music.player.ui.pages.settings.performance.userinterface.ScreenCornerSetDialog
import yos.music.player.ui.pages.settings.performance.userinterface.UserInterfaceSetting
import yos.music.player.ui.theme.YosMusicTheme
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.isFlamingoInDarkMode
import yos.music.player.ui.theme.isLandscape
import yos.music.player.ui.theme.rememberAdaptive
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.BottomNavigator
import yos.music.player.ui.widgets.basic.SideNavigator
import yos.music.player.ui.widgets.basic.PaneDivider
import yos.music.player.ui.widgets.basic.SideNavigatorWidth
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.NavItem
import yos.music.player.ui.widgets.basic.ShadowImageWithCache
import yos.music.player.ui.widgets.basic.YosWrapper
import java.io.File
import kotlin.math.abs

/*//MediaPlayer全局控制器
var mediaController = yos.music.player.code.MediaController*/

class MainActivity : androidx.activity.ComponentActivity() {

    private val mediaViewModel: MediaViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    @Suppress("DEPRECATION")
    @OptIn(
        ExperimentalAnimationApi::class,
        ExperimentalHazeMaterialsApi::class, ExperimentalSharedTransitionApi::class
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        enableHighRefreshRate()
        setContent {
            // ARR frame-rate votes belong to the actual drawing View; setting
            // only the Window preference does not set the Compose View's vote.
            val drawingView = androidx.compose.ui.platform.LocalView.current
            DisposableEffect(drawingView) {
                val previousRate = if (Build.VERSION.SDK_INT >= 35) drawingView.requestedFrameRate else 0f
                if (Build.VERSION.SDK_INT >= 35) {
                    drawingView.requestedFrameRate = drawingView.display?.supportedModes
                        ?.maxOfOrNull { it.refreshRate } ?: android.view.View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
                }
                onDispose {
                    if (Build.VERSION.SDK_INT >= 35) drawingView.requestedFrameRate = previousRate
                }
            }
            // 横屏隐藏状态栏（滑动可短暂呼出），回竖屏恢复
            val orientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
            val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            DisposableEffect(isLandscape) {
                val window = (drawingView.context as? android.app.Activity)?.window
                if (window != null) {
                    val controller = androidx.core.view.WindowCompat.getInsetsController(window, drawingView)
                    if (isLandscape) {
                        controller.systemBarsBehavior =
                            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    } else {
                        controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    }
                }
                onDispose { }
            }
            YosMusicTheme {
                ProvideWindowInsets {
                    val context = LocalContext.current
                    val density = LocalDensity.current

                    val offsetY = remember("MainActivity_offsetY") { Animatable(0f) }
                    val parentHeight =
                        remember("MainActivity_parentHeight") { mutableIntStateOf(0) }
                    val screenCorner = remember("MainActivity_screenCorner") {
                        val corner = SettingsLibrary.ScreenCorner.toInt()
                        if (corner == 0) 1 else corner
                    }

                    val scope = rememberCoroutineScope()
                    // 实时导航栏高度（底栏留白显式求和用）
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                    /*val parentHeightDp = remember(parentHeight.intValue) {
                        with(density) {
                            parentHeight.intValue.toDp()
                        }
                    }*/


                    /*Surface(
                        modifier = Modifier
                            .fillMaxSize(),
                        color = Color.Transparent,
                        contentColor = Color.Black withNight Color.White
                    ) {*/
                        val miniPlayerHeight = rememberAdaptive(62)
                        val height = remember("MainActivity_height") { mutableIntStateOf(0) }

                        // 横屏：底部 tab 栏移到左侧，页面内容右移让位；
                        // 迷你条收起态平移到侧栏右侧、上滑展开时滑回全宽
                        val isLandscapeNow = isLandscape()

                        val navHeight = remember("MainActivity_navHeight") {
                            with(density) {
                                height.intValue.toDp().plus(miniPlayerHeight)
                            }
                        }
                        // 逻辑初始化区域
                        val navController = rememberNavController()
                        val navSpec = spring(
                            stiffness = 400f,
                            dampingRatio = 1f,
                            visibilityThreshold = 0.001f
                        )
                        // Match the critically damped cover-to-lyrics motion, with
                        // a responsive nonlinear transition for mini-to-full travel.
                        val playerOpenSpec = spring<Float>(
                            stiffness = 320f,
                            dampingRatio = 1f,
                            visibilityThreshold = 0.001f
                        )
                        // Gesture settling must respond equally in both directions;
                        // click and drag both preserve velocity and interruption.
                        val playerDragSpec = spring<Float>(
                            stiffness = 300f,
                            dampingRatio = 1f,
                            visibilityThreshold = 0.001f
                        )
                        // val scaffoldState = rememberBottomSheetScaffoldState()
                        val route = rememberSaveable(key = "MainActivity_route") {
                            mutableStateOf(UI.HomePage)
                        }
                        // 记录当前路线

                        YosWrapper {
                            val backstackEntry =
                                navController.currentBackStackEntryAsState()
                            route.value =
                                backstackEntry.value?.destination?.route ?: UI.HomePage
                        }

                        // 显示控制区域
                        val yosBottomSheetConfig = object {
                            val progress
                                get() = if (parentHeight.intValue == 0) 0f else abs(offsetY.value / parentHeight.intValue).coerceIn(
                                    0f,
                                    1f
                                )
                            val menuAlpha
                                get() = 1f - progress
                            val mainContainerCardScale
                                get() = 0.9f + (0.1f * menuAlpha)
                            val thisShowCorner
                                get() = progress > 0f

                            val barShowCorner
                                get() = progress < 1f
                            val showMenu
                                get() = progress < 1f

                            val barShapeValue
                                get() = lerp(12, screenCorner, progress)

                            val RTCorner
                                get() = screenCorner == 0
                        }

                        val showNowPlaying = remember("MainActivity_showNowPlaying") {
                            derivedStateOf {
                                // 阈值取高位：收起动画接近完成（menuAlpha>0.9）才退出展开态，
                                // 避免系统栏图标明暗在收起早期翻转、触发系统侧重绘底栏造成闪烁
                                yosBottomSheetConfig.menuAlpha < 0.9f
                            }
                        }

                        val nowPageNowPlaying =
                            rememberSaveable(key = "MainActivity_nowPageNowPlaying") {
                                mutableStateOf(Album)
                            }

                        val hazeState = remember { HazeState() }

                        YosWrapper {
                            val isNight = isFlamingoInDarkMode()
                            if (showNowPlaying.value) {
                                rememberSystemUiController().run {
                                    setNavigationBarColor(
                                        Color.Transparent,
                                        darkIcons = false,
                                        navigationBarContrastEnforced = false
                                    )
                                    setStatusBarColor(Color.Transparent, darkIcons = false)
                                    var activity = LocalContext.current as? Activity
                                    DisposableEffect(Unit) {
                                        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                        onDispose {
                                            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                            activity = null
                                        }
                                    }
                                }
                            } else {
                                rememberSystemUiController().run {
                                    setNavigationBarColor(
                                        color = Color.Transparent,
                                        darkIcons = !isNight,
                                        navigationBarContrastEnforced = false
                                    )

                                    setStatusBarColor(Color.Transparent, darkIcons = !isNight)
                                }
                            }
                        }

                        // 导航区域
                        val defaultHome = stringResource(id = R.string.page_home_title)

                        val nowLabel = rememberSaveable(key = "MainActivity_nowLabel") {
                            mutableStateOf(defaultHome)
                        }

                        val pagerState = rememberPagerState(pageCount = { 2 })

                        // 以下为实际显示

                        // 主界面
                        YosWrapper {

                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val thisMainContainerCardScale =
                                            yosBottomSheetConfig.mainContainerCardScale
                                        scaleX = thisMainContainerCardScale
                                        scaleY = thisMainContainerCardScale
                                    }
                                    .graphicsLayer {
                                        //compositingStrategy = CompositingStrategy.Offscreen
                                        //transformOrigin = TransformOrigin(0.5f, 1f)

                                        // 合成策略全程保持 Offscreen，仅在展开时追加圆角裁剪：
                                        // 收起结束帧若从 Offscreen 切回 ModulateAlpha，会连带
                                        // 底栏 haze 模糊层的合成结果跳变一帧（表现为底栏透明度闪没）
                                        compositingStrategy = CompositingStrategy.Offscreen
                                        val cornered =
                                            yosBottomSheetConfig.thisShowCorner && !yosBottomSheetConfig.RTCorner
                                        clip = cornered
                                        if (cornered) {
                                            shape = YosRoundedCornerShape(screenCorner.dp)
                                        }

                                        if (!yosBottomSheetConfig.barShowCorner) {
                                            alpha = 0f
                                        }
                                    }

                                ,
                                color = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ) {
                                // 主界面本体
                                SharedTransitionLayout {
                                    YosWrapper {
                                        BackHandler(showNowPlaying.value) {
                                            // println("isVisible: ${showNowPlaying.value}, nowPageNowPlaying.value: ${nowPageNowPlaying.value}")
                                            if (nowPageNowPlaying.value != Album) {
                                                nowPageNowPlaying.value =
                                                    Album
                                            } else {
                                                scope.launch {
                                                    // scaffoldState.bottomSheetState.partialExpand()
                                                    offsetY.animateTo(0f, animationSpec = navSpec)
                                                }
                                            }
                                        }
                                    }

                                    YosWrapper {
                                        val offsetSpec = spring<IntOffset>(stiffness = 300f, dampingRatio = 0.85f)
                                        AnimatedNavHost(
                                            // haze 内容源常驻，不随播放页展开/收起挂摘：
                                            // 否则底栏模糊背景会在切换帧采样不到内容源，出现底部闪条
                                            modifier = Modifier
                                                .then(
                                                    if (SettingsLibrary.BarBlurEffect) {
                                                        Modifier.haze(state = hazeState)
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                // 横屏避开左侧导航栏
                                                .then(
                                                    if (isLandscapeNow) {
                                                        Modifier.padding(start = SideNavigatorWidth)
                                                    } else {
                                                        Modifier
                                                    }
                                                ),
                                            navController = navController,
                                            startDestination = UI.HomePage,
                                            enterTransition = { slideInHorizontally(offsetSpec) { it } },
                                            exitTransition = { slideOutHorizontally(offsetSpec) { -it } },
                                            popEnterTransition = { slideInHorizontally(offsetSpec) { -it } },
                                            popExitTransition = { slideOutHorizontally(offsetSpec) { it } }
                                        ) {

                                            composable(UI.HomePage) {
                                                HomeNav(
                                                    navController,
                                                    pagerState
                                                ) {
                                                    nowLabel.value = it
                                                }
                                            }
                                            composable(UI.Library) {
                                                Library(
                                                    navController
                                                )
                                            }

                                            // 横屏下这些整页路由统一渲染为资料库分栏（带初始分栏映射），
                                            // 竖屏时保持原整页行为
                                            composable(UI.PlayLists) {
                                                if (isLandscapeNow) Library(
                                                    navController,
                                                    initialPane = LibrarySectionPane.Playlists
                                                ) else PlayLists(navController, onBack = { navController.popBackStack() })
                                            }
                                            composable(UI.NormalMusic) {
                                                if (isLandscapeNow) {
                                                    // 歌曲页=歌曲分栏；歌单详情=播放列表分栏压歌单
                                                    val target = LibraryObject.getTargetListWithTitle()
                                                    val songsLabel = context.getString(R.string.page_library_songs)
                                                    if (target.first == songsLabel) Library(navController) else Library(
                                                        navController,
                                                        initialPane = LibrarySectionPane.Playlists,
                                                        initialDetail = target.first
                                                    )
                                                } else NormalMusic(navController, onBack = { navController.popBackStack() })
                                            }
                                            composable(UI.LocalAlbums) {
                                                if (isLandscapeNow) Library(
                                                    navController,
                                                    initialPane = LibrarySectionPane.Albums
                                                ) else LocalAlbums(
                                                    navController,
                                                    this@SharedTransitionLayout,
                                                    this@composable,
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }
                                            composable(UI.LocalArtists) {
                                                if (isLandscapeNow) Library(
                                                    navController,
                                                    initialPane = LibrarySectionPane.Artists
                                                ) else LocalArtists(navController, onBack = { navController.popBackStack() })
                                            }
                                            composable(UI.ArtistInfo) {
                                                if (isLandscapeNow) Library(
                                                    navController,
                                                    initialPane = LibrarySectionPane.Artists,
                                                    initialDetail = LibraryObject.getTargetArtistName()
                                                ) else ArtistInfo(navController, onBack = { navController.popBackStack() })
                                            }

                                            composable(UI.ListenStats) {
                                                ListenStatsPage(navController)
                                            }

                                            composable(UI.AlbumInfo) {
                                                if (isLandscapeNow) Library(
                                                    navController,
                                                    initialPane = LibrarySectionPane.Albums,
                                                    initialDetail = LibraryObject.getTargetAlbumName()
                                                ) else AlbumInfo(
                                                    navController,
                                                    this@SharedTransitionLayout,
                                                    this@composable,
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }

                                            composable(UI.Settings.Main) {
                                                Settings(
                                                    navController
                                                )
                                            }
                                            composable(UI.Settings.LibraryOverview) {
                                                if (isLandscapeNow) Settings(navController, initialPage = UI.Settings.LibraryOverview)
                                                else LibraryOverview(navController)
                                            }
                                            composable(UI.Settings.LyricGetter) {
                                                if (isLandscapeNow) Settings(navController, initialPage = UI.Settings.LyricGetter)
                                                else LyricGetter(navController)
                                            }
                                            composable(ExoplayerSetting) {
                                                ExoPlayerSettings(navController)
                                            }
                                            composable(UI.Settings.About) {
                                                if (isLandscapeNow) Settings(navController, initialPage = UI.Settings.About)
                                                else About(
                                                    navController
                                                )
                                            }
                                            composable(UI.Settings.MediaCodec) {
                                                if (isLandscapeNow) Settings(navController, initialPage = UI.Settings.MediaCodec)
                                                else MediaCodec(navController)
                                            }

                                            composable(UI.Settings.LyricSetting) {
                                                if (isLandscapeNow) Settings(navController, initialPage = UI.Settings.LyricSetting)
                                                else LyricSetting(navController)
                                            }
                                            composable(UI.Settings.UserInterfaceSetting) {
                                                if (isLandscapeNow) Settings(navController, initialPage = UI.Settings.UserInterfaceSetting)
                                                else UserInterfaceSetting(navController)
                                            }
                                            composable(UI.Settings.NotificationSetting) {
                                                if (isLandscapeNow) Settings(navController, initialPage = UI.Settings.NotificationSetting)
                                                else NotificationSetting(navController)
                                            }
                                            composable(UI.Settings.RemoteServerManagement) {
                                                if (isLandscapeNow) Settings(navController, initialPage = UI.Settings.RemoteServerManagement)
                                                else yos.music.player.ui.pages.settings.remote.RemoteServerManagement(navController)
                                            }
                                            composable(
                                                UI.Settings.RemoteFolderPicker,
                                                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
                                            ) {
                                                val serverId = it.arguments?.getString("serverId")
                                                yos.music.player.ui.pages.settings.remote.RemoteFolderPicker(navController, serverId)
                                            }
                                        }


                                    }
                                }

                                // 底部导航栏
                                YosWrapper {
                                    val color =
                                        Color(0xFFF5F5F5) withNight /*Color(0xFF111111)*/ Color.Black
                                    // Keep the fade above the tabs; navigation insets only extend the opaque part.
                                    val bottomMaskHeight = if (isLandscapeNow) 76.dp else navBarBottom + 128.dp
                                    val bottomBarBrush = remember(color, density) {
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.35f to color.copy(alpha = 0.6f),
                                            1f to color,
                                            endY = with(density) { 48.dp.toPx() }
                                        )
                                    }
                                    Box(
                                        Modifier
                                            .fillMaxSize(),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        val settingsLabel = stringResource(id = R.string.page_settings_title)
                                        val navItems = buildList {
                                            add(
                                                NavItem(
                                                    stringResource(id = R.string.page_home_title),
                                                    R.drawable.flamingo_icon
                                                )
                                            )
                                            add(
                                                NavItem(
                                                    stringResource(id = R.string.page_library_title),
                                                    R.drawable.ic_uitabbar_library
                                                )
                                            )
                                            // 横屏侧栏多一个设置入口；竖屏底栏保持两项
                                            if (isLandscapeNow) {
                                                add(NavItem(settingsLabel, R.drawable.ic_tab_settings))
                                            }
                                        }
                                        val onTabSelected: (String) -> Unit = {
                                            nowLabel.value = it

                                            val home =
                                                context.getString(R.string.page_home_title)
                                            val library =
                                                context.getString(R.string.page_library_title)
                                            when (it) {
                                                // 设置不是 pager 页，直接整页路由
                                                settingsLabel -> navController.toUI(UI.Settings.Main)

                                                else -> if (route.value == UI.HomePage) {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(
                                                            when (it) {
                                                                home -> 0
                                                                library -> 1
                                                                else -> 0
                                                            }
                                                        )
                                                    }
                                                } else {
                                                    scope.launch {
                                                        pagerState.scrollToPage(
                                                            when (it) {
                                                                home -> 0
                                                                library -> 1
                                                                else -> 0
                                                            }
                                                        )
                                                    }
                                                    navController.popBackStack(
                                                        UI.HomePage,
                                                        false
                                                    )
                                                }
                                            }
                                        }

                                        // 侧栏高亮跟随真实路由：进入设置（含其子页）时
                                        // 高亮"设置"，返回时按 pager 当前页恢复
                                        val settingsRoutes = remember {
                                            setOf(
                                                UI.Settings.Main,
                                                UI.Settings.LibraryOverview,
                                                UI.Settings.LyricGetter,
                                                UI.Settings.ExoplayerSetting,
                                                UI.Settings.About,
                                                UI.Settings.MediaCodec,
                                                UI.Settings.LyricSetting,
                                                UI.Settings.UserInterfaceSetting,
                                                UI.Settings.NotificationSetting,
                                                UI.Settings.RemoteServerManagement
                                            )
                                        }
                                        LaunchedEffect(route.value, pagerState.currentPage) {
                                            nowLabel.value = when {
                                                route.value in settingsRoutes ||
                                                        route.value?.startsWith(UI.Settings.RemoteFolderPicker) == true ->
                                                    settingsLabel

                                                else -> when (pagerState.currentPage) {
                                                    1 -> context.getString(R.string.page_library_title)
                                                    else -> context.getString(R.string.page_home_title)
                                                }
                                            }
                                        }
                                        // 横屏左侧栏渐变：左缘实色向右淡出（镜像底栏"下实上淡"）
                                        val railBrush = remember(color, density) {
                                            Brush.horizontalGradient(
                                                0f to color,
                                                0.55f to color.copy(alpha = 0.6f),
                                                1f to Color.Transparent,
                                                endX = with(density) { SideNavigatorWidth.toPx() }
                                            )
                                        }
                                        // 背景
                                        // 底层常驻静态渐变；实时模糊层仅在收起静止后 240ms 淡入盖上、
                                        // 展开时淡出。过渡期间露出的是同色系渐变而非页面内容，
                                        // 避免"先变不透明再恢复模糊"的两段式跳变
                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(bottomMaskHeight)
                                                // 横屏遮罩与通栏迷你条同区（内容区宽），不横穿侧栏
                                                .padding(start = if (isLandscapeNow) SideNavigatorWidth else 0.dp)
                                                .background(bottomBarBrush)
                                        )
                                        if (SettingsLibrary.BarBlurEffect) {
                                            val settled = remember("MainActivity_barSettled") {
                                                derivedStateOf { yosBottomSheetConfig.progress == 0f }
                                            }
                                            val blurAlpha = animateFloatAsState(
                                                targetValue = if (settled.value) 1f else 0f,
                                                animationSpec = tween(240),
                                                label = "barBlurAlpha"
                                            )
                                            if (blurAlpha.value > 0.01f) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(bottomMaskHeight)
                                                        .padding(start = if (isLandscapeNow) SideNavigatorWidth else 0.dp)
                                                        .graphicsLayer {
                                                            alpha = blurAlpha.value
                                                            compositingStrategy =
                                                                CompositingStrategy.Offscreen
                                                        }
                                                        // 不透明兜底：模糊效果层偶发缺失的帧显示纯色底
                                                        .background(color)
                                                        .hazeChild(
                                                            hazeState,
                                                            HazeMaterials
                                                                .regular(
                                                                    color
                                                                )
                                                                .copy(
                                                                    blurRadius = 48.dp
                                                                )
                                                        )
                                                        .drawWithCache {
                                                            onDrawWithContent {
                                                                drawContent()
                                                                drawRect(
                                                                    brush = bottomBarBrush,
                                                                    blendMode = BlendMode.DstIn
                                                                )
                                                            }
                                                        }
                                                )
                                            }
                                        }

                                        if (isLandscapeNow) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .fillMaxHeight()
                                                    .width(SideNavigatorWidth)
                                                    .background(railBrush)
                                            ) {
                                                // 侧栏与内容之间的竖线，与分栏竖线同源
                                                PaneDivider(
                                                    modifier = Modifier.align(Alignment.CenterEnd)
                                                )
                                                SideNavigator(
                                                    nowLabel = { nowLabel.value },
                                                    onLabelChange = onTabSelected,
                                                    items = navItems,
                                                    modifier = Modifier
                                                )
                                            }
                                        } else {
                                            BottomNavigator(
                                                nowLabel = { nowLabel.value },
                                                onLabelChange = onTabSelected,
                                                items = navItems,
                                                modifier = Modifier
                                                .onSizeChanged {
                                                    height.intValue = it.height
                                                }
                                        )
                                        }
                                    }
                                }
                            }

                            // 弹窗
                            YosWrapper {
                                val showCornerSetDialog =
                                    remember("MainActivity_showCornerSetDialog") {
                                        mutableStateOf(!SettingsLibrary.ScreenCornerSet)
                                    }

                                if (showCornerSetDialog.value) {
                                    ScreenCornerSetDialog {
                                        showCornerSetDialog.value = false
                                    }
                                } else {
                                    CheckAndRequestPermission()
                                }
                            }
                        }

                        // 播放条&播放界面
                        YosWrapper {
                            if (height.intValue == 0) return@YosWrapper

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // 横屏播放层恒定让位侧栏（静态值，动画无重排）：
                                    // 迷你条与播放页的样式、缩放、阴影与竖屏完全一致，
                                    // 只是整体宽度为内容区宽
                                    .padding(start = if (isLandscapeNow) SideNavigatorWidth else 0.dp)
                                    .onSizeChanged {
                                        val previousHeight = parentHeight.intValue
                                        val nextHeight = it.height
                                        if (previousHeight != nextHeight && nextHeight > 0) {
                                            val progress = if (previousHeight > 0)
                                                (offsetY.value / previousHeight).coerceIn(0f, 1f) else 0f
                                            parentHeight.intValue = nextHeight
                                            if (previousHeight > 0) scope.launch {
                                                // Preserve expanded/collapsed/dragged state when
                                                // a freeform window or split-screen changes size.
                                                offsetY.updateBounds(0f, nextHeight.toFloat())
                                                offsetY.snapTo(progress * nextHeight)
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        //compositingStrategy = CompositingStrategy.Offscreen
                                        val plus =
                                            0.07f * yosBottomSheetConfig.progress
                                        this.scaleX = 0.93f + plus
                                        this.scaleY = 0.93f + plus
                                        // 收起态整体上移给底栏腾位——竖屏专用；
                                        // 横屏底栏在侧边，仅保留一小段缝隙不贴死
                                        this.translationY =
                                            if (isLandscapeNow)
                                                -10.dp.toPx() * (yosBottomSheetConfig.menuAlpha)
                                            else
                                                -(height.intValue + 10) * (yosBottomSheetConfig.menuAlpha)
                                        this.transformOrigin = TransformOrigin(0.5f, 1f)
                                    },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                YosWrapper {
                                    val miniPlayerHeightPx = remember("MainActivity_miniPlayerHeightPx") {
                                        with(density) {
                                            miniPlayerHeight.toPx()
                                        }
                                    }

                                    val miniPlayerShadow = remember("MainActivity_miniPlayerShadow") {
                                        with(density) {
                                            7.5.dp.toPx()
                                        }
                                    }

                                    val color = Color.White withNight Color(0xFF1C1C1E)


                                    val dragState = rememberDraggableState { delta ->
                                        scope.launch {
                                            offsetY.snapTo(offsetY.value + delta)
                                        }
                                    }

                                    /*.graphicsLayer {
                                        if (yosBottomSheetConfig.barShowCorner) {
                                            shadowElevation = 7.5.dp.toPx()
                                            spotShadowColor = Color.Black.copy(alpha = 0.2f)

                                            *//*compositingStrategy =
                                                        CompositingStrategy.Offscreen*//*
                                                }
                                            }*/
                                    /*.drawWithContent {
                                        drawContent()
                                        drawOutline(
                                            outline = navShape.createOutline(size, layoutDirection, this),
                                            color = color.copy(alpha = yosBottomSheetConfig.menuAlpha),
                                            style = Stroke(width = 0.3.dp.toPx())
                                        )
                                    }*/

                                    Surface(
                                        modifier = Modifier.graphicsLayer {
                                            translationY =
                                                ((parentHeight.intValue - miniPlayerHeightPx) * (1 - yosBottomSheetConfig.progress))



                                            if (yosBottomSheetConfig.barShowCorner) {
                                                shape = YosRoundedCornerShape(
                                                    roundSize = yosBottomSheetConfig.barShapeValue.dp,
                                                    mSize = Size(
                                                        width = size.width,
                                                        height = (miniPlayerHeightPx + (parentHeight.intValue - miniPlayerHeightPx) * yosBottomSheetConfig.progress)
                                                    )
                                                )
                                                shadowElevation = miniPlayerShadow
                                                spotShadowColor = Color.Black.copy(alpha = 0.2f)
                                            }
                                        }
                                            .graphicsLayer {
                                                //translationY = ((parentHeight.intValue - miniPlayerHeightPx) * (1 - yosBottomSheetConfig.progress))

                                                if (yosBottomSheetConfig.barShowCorner) {
                                                    compositingStrategy = CompositingStrategy.Offscreen
                                                    clip = true
                                                    shape = YosRoundedCornerShape(
                                                        roundSize = yosBottomSheetConfig.barShapeValue.dp,
                                                        mSize = Size(
                                                            width = size.width,
                                                            height = (miniPlayerHeightPx + (parentHeight.intValue - miniPlayerHeightPx) * yosBottomSheetConfig.progress)
                                                        )
                                                    )
                                                } else {
                                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                                    clip = false
                                                }
                                            }
                                            .draggable(
                                                reverseDirection = true,
                                                orientation = Orientation.Vertical,
                                                state = dragState,
                                                onDragStopped = { velocity ->
                                                    offsetY.updateBounds(
                                                        0f,
                                                        parentHeight.intValue.toFloat()
                                                    )
                                                    scope.launch {
                                                        //println("速度：$velocity 高度：${miniPlayerHeight + (parentHeightDp - miniPlayerHeight) * progress}")
                                                        if (velocity < 0f) {
                                                            offsetY.animateTo(
                                                                0f,
                                                                animationSpec = playerDragSpec,
                                                                initialVelocity = velocity
                                                            )
                                                        } else {
                                                            offsetY.animateTo(
                                                                parentHeight.intValue.toFloat(),
                                                                animationSpec = playerDragSpec,
                                                                initialVelocity = velocity
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                            .layout { measurable, constraints ->
                                                val placeable = measurable.measure(
                                                    constraints.copy(
                                                        minHeight = parentHeight.intValue,
                                                        maxHeight = parentHeight.intValue
                                                    )
                                                )
                                                layout(
                                                    placeable.width,
                                                    placeable.height
                                                ) {
                                                    placeable.placeRelative(0, 0)
                                                }
                                            },
                                        color = Color.Transparent
                                    ) {

                                        val isPlaying = MediaViewModelObject.isPlaying
                                        val playerArtwork = rememberNowPlayingArtwork(MediaController.musicPlaying.value?.thumb)
                                        val artworkBridge = remember { PlayerArtworkBridge { yosBottomSheetConfig.progress } }

                                        // NowPlaying
                                        YosWrapper {
                                            NowPlaying(
                                                artwork = playerArtwork,
                                                artworkBridge = artworkBridge,
                                                mainViewModel = mainViewModel,
                                                mediaViewModel = mediaViewModel,
                                                navController = navController,
                                                isPlayingStatusLambda = { isPlaying.value },
                                                isPlayingOnChanged = {
                                                    isPlaying.value = it
                                                },
                                                nowPageLambda = { nowPageNowPlaying.value },
                                                showMiniPlayer = { yosBottomSheetConfig.showMenu },
                                                closeSheet = { scope.launch { offsetY.animateTo(0f, animationSpec = navSpec) } }
                                            ) {
                                                nowPageNowPlaying.value = it
                                            }
                                        }

                                        // 迷你播放状态
                                        YosWrapper {
                                            //println("变换：菜单透明度 $menuAlpha")
                                            if (yosBottomSheetConfig.showMenu) {
                                                YosWrapper {
                                                    Column(
                                                        Modifier
                                                            .fillMaxSize()
                                                            .graphicsLayer {
                                                                compositingStrategy =
                                                                    CompositingStrategy.ModulateAlpha
                                                                this.alpha =
                                                                    (1f - 4f * yosBottomSheetConfig.progress).coerceIn(0f, 1f)
                                                            }
                                                            .background(color)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(miniPlayerHeight)
                                                                .clickable(
                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                    indication = null,
                                                                    onClick = {
                                                                        scope.launch {
                                                                            offsetY.animateTo(
                                                                                parentHeight.intValue.toFloat(),
                                                                                animationSpec = playerOpenSpec
                                                                            )
                                                                        }
                                                                    })
                                                        ) {

                                                            Row(
                                                                Modifier
                                                                    .height(miniPlayerHeight)
                                                                    .fillMaxWidth()
                                                                    .padding(
                                                                        horizontal = 8.dp
                                                                    )
                                                            ) {
                                                                Row(
                                                                    Modifier
                                                                        .height(
                                                                            miniPlayerHeight
                                                                        )
                                                                        .weight(1f),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    YosWrapper {
                                                                        Spacer(Modifier.size(rememberAdaptive(47))
                                                                            .onGloballyPositioned {
                                                                                artworkBridge.record(MINI_ARTWORK_ENDPOINT, it)
                                                                            })
                                                                    }
                                                                    Column(
                                                                        Modifier
                                                                            .padding(
                                                                                start = 10.dp,
                                                                                end = 5.dp
                                                                            )
                                                                    ) {
                                                                        Text(
                                                                            text = MediaController.musicPlaying.value?.title
                                                                                ?: defaultTitle,
                                                                            fontWeight = FontWeight.Medium,
                                                                            fontSize = 16.sp,
                                                                            lineHeight = 16.sp,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis,
                                                                            color = Color.Black withNight Color.White
                                                                        )
                                                                        /*Text(
                                                                text = musicPlaying.value?.Artist
                                                                    ?: "未知艺术家",
                                                                fontSize = 13.5.sp,
                                                                lineHeight = 13.5.sp,
                                                                modifier = Modifier.alpha(
                                                                    0.6f
                                                                ),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = Color.Black withNight Color.White
                                                            )*/
                                                                    }
                                                                }
                                                                Row(
                                                                    Modifier
                                                                        .fillMaxHeight()
                                                                        .padding(end = 10.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(rememberAdaptive(34))
                                                                            .clickable(
                                                                                interactionSource = remember { MutableInteractionSource() },
                                                                                indication = ripple(
                                                                                    bounded = false
                                                                                ),
                                                                                onClick = {
                                                                                    Vibrator.click(
                                                                                        context
                                                                                    )
                                                                                    isPlaying.value =
                                                                                        !(isPlaying.value || MediaViewModelObject.isBuffering.value)
                                                                                    if (isPlaying.value) {
                                                                                        MediaController.mediaControl?.fadePlay()
                                                                                    } else {
                                                                                        MediaController.mediaControl?.fadePause()
                                                                                    }
                                                                                }),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        if (MediaViewModelObject.isBuffering.value) {
                                                                            androidx.compose.material3.CircularProgressIndicator(
                                                                                modifier = Modifier.size(22.dp),
                                                                                color = Color.Black withNight Color.White,
                                                                                strokeWidth = 2.dp
                                                                            )
                                                                        } else AnimatedContent(
                                                                            targetState = isPlaying.value,
                                                                            transitionSpec = {
                                                                                (scaleIn(
                                                                                    initialScale = 0.3f
                                                                                ) + fadeIn()).togetherWith(
                                                                                    scaleOut(
                                                                                        targetScale = 0.3f
                                                                                    ) + fadeOut()
                                                                                )
                                                                            }) {
                                                                            if (it) {
                                                                                Icon(
                                                                                    painterResource(
                                                                                        id = R.drawable.ic_nowplaying_mp_pause
                                                                                    ),
                                                                                    contentDescription = "Pause",
                                                                                    modifier = Modifier
                                                                                        .fillMaxSize(),
                                                                                    tint = Color.Black withNight Color.White
                                                                                )
                                                                            } else {
                                                                                Icon(
                                                                                    painterResource(
                                                                                        id = R.drawable.ic_nowplaying_mp_play
                                                                                    ),
                                                                                    contentDescription = "Play",
                                                                                    modifier = Modifier
                                                                                        .fillMaxSize(),
                                                                                    tint = Color.Black withNight Color.White
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                    Spacer(
                                                                        modifier = Modifier.width(
                                                                            18.dp
                                                                        )
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(rememberAdaptive(36))
                                                                            .clickable(
                                                                                interactionSource = remember { MutableInteractionSource() },
                                                                                indication = ripple(
                                                                                    bounded = false
                                                                                ),
                                                                                onClick = {
                                                                                    Vibrator.click(
                                                                                        context
                                                                                    )
                                                                                    MediaController.skipToNext()
                                                                                }),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Icon(
                                                                            painterResource(
                                                                                id = R.drawable.ic_nowplaying_mp_fforward
                                                                            ),
                                                                            contentDescription = "Next",
                                                                            modifier = Modifier
                                                                                .fillMaxSize(),
                                                                            tint = Color.Black withNight Color.White
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        PlayerArtworkBridgeOverlay(
                                            artworkBridge, playerArtwork,
                                            destination = { artworkBridge.expandedEndpoint },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    /*}*/
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 小窗/分屏切回全屏时，系统可能重置 edge-to-edge 标志。
        // post 到下一帧执行，等系统完成窗口过渡后再重新应用，避免时序冲突。
        Handler(Looper.getMainLooper()).post {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            ViewCompat.requestApplyInsets(window.decorView)
        }
    }

    @Composable
    fun CheckAndRequestPermission() {
        val context = LocalContext.current
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val isGranted = permissions.entries.all { it.value }
            if (isGranted) {
                // Load music list here
                loadMusic(context, enforce = true)
                sendBroadcast(Intent("yos.music.player.BLUETOOTH_STATUS_REFRESH"))
            } else {
                // Set music list to empty if permission is denied
                // mainMusicList.value = mutableListOf()
            }
        }

        YosWrapper {
            LaunchedEffect(Unit) {

                var permissions = emptyArray<String>()

                if ((hasScopedStorageWithMediaTypes()
                            && ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_MEDIA_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED)
                    /*|| (!hasScopedStorageV2()
                            && ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED)*/
                    || (!hasScopedStorageWithMediaTypes()
                            && ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED)
                ) {
                    permissions += arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

                    if (hasScopedStorageWithMediaTypes()) {
                        permissions += arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                    }
                }

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissions += Manifest.permission.BLUETOOTH_CONNECT
                }

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions += Manifest.permission.POST_NOTIFICATIONS
                }

                if (permissions.isNotEmpty()) {
                    requestPermissionLauncher.launch(permissions)
                }
                else {
                    loadMusic(context)
                }
            }
        }
    }

    private fun loadMusic(context: Context, enforce: Boolean = false) {
        val needRefresh = SettingsLibrary.RefreshEveryTime
        if (needRefresh || enforce) {
            mediaViewModel.viewModelScope.launch(Dispatchers.IO) {
                // Application中已还原，这里算是后台扫描
                // mainMusicList.value = MusicScanner(context).getMusicList()
                MusicLibrary.scanMedia(context)
            }
        }
    }

    private fun enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = windowManager.defaultDisplay
            val modes = display?.supportedModes ?: return
            val maxRefreshRate = modes.maxOfOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply {
                preferredRefreshRate = maxRefreshRate
            }
        }
    }

}

fun readFile(path: String): String? {
    return try {
        File(path).readText()
    } catch (e: Exception) {
        //e.printStackTrace()
        null
    }
}
