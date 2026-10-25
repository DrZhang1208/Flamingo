package yos.music.player.ui.pages.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.PersonCropCircle
import yos.music.player.R
import yos.music.player.data.libraries.MusicLibrary.songs
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.pages.library.albums.AlbumInfo
import yos.music.player.ui.pages.library.albums.LocalAlbums
import yos.music.player.ui.pages.library.artists.ArtistInfo
import yos.music.player.ui.pages.library.artists.LocalArtists
import yos.music.player.ui.pages.library.playlists.PlayLists
import yos.music.player.ui.theme.isLandscape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.PaneDivider
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.YosWrapper
import androidx.compose.animation.ExperimentalSharedTransitionApi

/** 横屏右栏导航栈的层级：分类列表在栈底，详情页压栈、右栏内切换 */
/** 横屏从整页路由进入分栏时的初始分栏（用于路由→分栏形态映射） */
enum class LibrarySectionPane { Playlists, Artists, Albums, Songs }

private sealed interface LibraryPane {
    data object Playlists : LibraryPane
    data object Artists : LibraryPane
    data object Albums : LibraryPane
    data object Songs : LibraryPane
    data class PlaylistSongs(val title: String) : LibraryPane
    data class Artist(val name: String) : LibraryPane
    data class Album(val name: String) : LibraryPane
}

@Composable
fun Library(
    navController: NavController,
    initialPane: LibrarySectionPane = LibrarySectionPane.Songs,
    // 从专辑/艺人/歌单整页路由进入时的详情名（压在初始分栏之上）
    initialDetail: String? = null
) {
    if (isLandscape()) {
        LibraryLandscape(navController, initialPane, initialDetail)
    } else {
        LibraryPortrait(navController)
    }
}
/** 竖屏：原有入口列表，逐项 push 路由 */
@Composable
private fun LibraryPortrait(navController: NavController) =
    Column(
        Modifier
            .fillMaxSize()
    ) {
        Title(
            title = stringResource(id = R.string.page_library_title),
            rightIcon = CupertinoIcons.Default.PersonCropCircle,
            onRightIcon = {
                navController.toUI(UI.Settings.Main)
            }
        ) {
            item("Library") {
                Column(
                    Modifier
                        .fillMaxSize(),
                ) {
                    SmallLabelItem(
                        icon = painterResource(id = R.drawable.ic_library_link_icon_playlists),
                        label = stringResource(
                            id = R.string.page_library_playlists
                        )
                    ) {
                        navController.toUI(UI.PlayLists)
                    }
                    LibraryDivider()
                    SmallLabelItem(
                        icon = painterResource(id = R.drawable.ic_library_link_icon_artists),
                        label = stringResource(
                            id = R.string.page_library_artists
                        )
                    ) {
                        navController.toUI(UI.LocalArtists)
                    }
                    LibraryDivider()
                    SmallLabelItem(
                        icon = painterResource(id = R.drawable.ic_library_link_icon_album),
                        label = stringResource(
                            id = R.string.page_library_albums
                        )
                    ) {
                        navController.toUI(UI.LocalAlbums)
                    }
                    LibraryDivider()

                    YosWrapper {
                        val targetTitle = stringResource(
                            id = R.string.page_library_songs
                        )
                        val targetList = songs
                        SmallLabelItem(
                            icon = painterResource(id = R.drawable.ic_library_link_icon_songs),
                            label = targetTitle
                        ) {
                            LibraryObject.setTargetListWithTitle(targetTitle, targetList)
                            navController.toUI(UI.NormalMusic)
                        }
                    }

                    LibraryDivider()
                }
            }
        }
    }

/** 横屏：左列分类菜单（竖屏同款行 + 选中态），右栏为内容栈，
 *  点专辑/艺人/歌单在右栏内切换，各栏独立滚动互不带动 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryLandscape(
    navController: NavController,
    initialPane: LibrarySectionPane = LibrarySectionPane.Songs,
    initialDetail: String? = null
) {
    val songsTitle = stringResource(id = R.string.page_library_songs)
    val stack = remember {
        // 数据源在事件期/初始化期设置，避免组合期副作用
        if (initialPane == LibrarySectionPane.Songs) {
            LibraryObject.setTargetListWithTitle(songsTitle, songs)
        }
        mutableStateListOf(
            when (initialPane) {
                LibrarySectionPane.Playlists -> LibraryPane.Playlists
                LibrarySectionPane.Artists -> LibraryPane.Artists
                LibrarySectionPane.Albums -> LibraryPane.Albums
                LibrarySectionPane.Songs -> LibraryPane.Songs
            }
        ).also { st ->
            when (initialPane) {
                LibrarySectionPane.Artists -> initialDetail?.let { st.add(LibraryPane.Artist(it)) }
                LibrarySectionPane.Albums -> initialDetail?.let { st.add(LibraryPane.Album(it)) }
                LibrarySectionPane.Playlists -> initialDetail?.let { st.add(LibraryPane.PlaylistSongs(it)) }
                LibrarySectionPane.Songs -> {}
            }
        }
    }
    val popPane: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1, onBack = popPane)

    val select: (LibraryPane) -> Unit = { pane ->
        if (pane is LibraryPane.Songs) {
            LibraryObject.setTargetListWithTitle(songsTitle, songs)
        }
        stack.clear()
        stack.add(pane)
    }

    // 分栏：左栏与右栏都用原版 Title 顶栏样式（大标题随滚动收起、
    // 上滑出现顶栏并在顶栏显示标题），各栏独立滚动；竖线全高
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(0.95f)
                .fillMaxHeight()
        ) {
            Title(
                title = stringResource(id = R.string.page_library_title)
            ) {
                item("library_menu") {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(6.dp))
                        // 与设置左列同款：圆角矩形分组卡内的菜单行
                        RoundColumn {
                            LibraryMenuRow(
                                icon = painterResource(id = R.drawable.ic_library_link_icon_playlists),
                                label = stringResource(id = R.string.page_library_playlists),
                                selected = stack.last().matches(LibraryPane.Playlists)
                            ) { select(LibraryPane.Playlists) }

                            MenuRowDivider()

                            LibraryMenuRow(
                                icon = painterResource(id = R.drawable.ic_library_link_icon_artists),
                                label = stringResource(id = R.string.page_library_artists),
                                selected = stack.last().matches(LibraryPane.Artists)
                            ) { select(LibraryPane.Artists) }

                            MenuRowDivider()

                            LibraryMenuRow(
                                icon = painterResource(id = R.drawable.ic_library_link_icon_album),
                                label = stringResource(id = R.string.page_library_albums),
                                selected = stack.last().matches(LibraryPane.Albums)
                            ) { select(LibraryPane.Albums) }

                            MenuRowDivider()

                            LibraryMenuRow(
                                icon = painterResource(id = R.drawable.ic_library_link_icon_songs),
                                label = stringResource(id = R.string.page_library_songs),
                                selected = stack.last().matches(LibraryPane.Songs)
                            ) { select(LibraryPane.Songs) }
                        }
                    }
                }
            }
        }
        PaneDivider()
        Box(
            Modifier
                .weight(2.05f)
                .fillMaxHeight()
        ) {
            AnimatedContent(
                targetState = stack.last(),
                transitionSpec = {
                    // 压栈（列表→详情）新内容从右滑入；退栈/切分类从左滑回
                    val targetIdx = stack.indexOf(targetState)
                    val initialIdx = stack.indexOf(initialState)
                    val forward = initialIdx < 0 || (targetIdx >= 0 && targetIdx > initialIdx)
                    if (forward) {
                        (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(160)))
                    }
                },
                label = "library_pane"
            ) { pane ->
                when (pane) {
                    LibraryPane.Playlists -> PlayLists(
                        navController,
                        onOpenPlaylist = { title, list ->
                            LibraryObject.setTargetListWithTitle(title, list)
                            stack.add(LibraryPane.PlaylistSongs(title))
                        }
                    )
                    LibraryPane.Artists -> LocalArtists(
                        navController,
                        onOpenArtist = { name ->
                            LibraryObject.setTargetArtistName(name)
                            stack.add(LibraryPane.Artist(name))
                        }
                    )
                    LibraryPane.Albums -> LocalAlbums(
                        navController,
                        onOpenAlbum = { name ->
                            LibraryObject.setTargetAlbumName(name)
                            stack.add(LibraryPane.Album(name))
                        }
                    )
                    LibraryPane.Songs -> NormalMusic(navController)
                    is LibraryPane.PlaylistSongs -> NormalMusic(navController, onBack = popPane)
                    is LibraryPane.Artist -> ArtistInfo(
                        navController,
                        onBack = popPane,
                        onOpenAlbum = { name ->
                            LibraryObject.setTargetAlbumName(name)
                            stack.add(LibraryPane.Album(name))
                        }
                    )
                    is LibraryPane.Album -> AlbumInfo(
                        navController,
                        onBack = popPane,
                        onOpenArtist = { name ->
                            LibraryObject.setTargetArtistName(name)
                            stack.add(LibraryPane.Artist(name))
                        }
                    )
                }
            }
        }
    }
}

/** 左栏菜单行之间的分割线，与设置分组内分割线同款 */
@Composable
private fun MenuRowDivider() =
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 15.dp)
            .alpha(0.2f)
            .height(0.3.dp)
            .background(Color.Black withNight Color.White)
    )

/** 栈顶内容是否属于给定分类（含其详情层级），用于左栏菜单高亮联动 */
private fun LibraryPane.matches(section: LibraryPane): Boolean = when (section) {
    LibraryPane.Playlists -> this is LibraryPane.Playlists || this is LibraryPane.PlaylistSongs
    LibraryPane.Artists -> this is LibraryPane.Artists || this is LibraryPane.Artist
    LibraryPane.Albums -> this is LibraryPane.Albums || this is LibraryPane.Album
    LibraryPane.Songs -> this is LibraryPane.Songs
    else -> false
}

/** 与竖屏 SmallLabelItem 同款的分类菜单行，叠加选中态（浅红底 + 珊瑚红文字） */
@Composable
private fun LibraryMenuRow(
    icon: Painter,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            // 48dp：四行在横屏小屏（320dp 高）也能整体露出在底栏之上
            .height(48.dp)
            .then(
                if (selected) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 15.dp, end = 4.dp)
                .size(36.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_action_next),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 21.dp)
                .height(12.dp)
                .alpha(if (selected) 0.75f else 0.3f),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
        )
    }
}
