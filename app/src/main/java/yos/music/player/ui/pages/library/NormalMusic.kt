package yos.music.player.ui.pages.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.github.promeg.pinyinhelper.Pinyin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.SettingsLibrary.EnableDescending
import yos.music.player.data.libraries.SettingsLibrary.SongSort
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.defaultArtists
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.pages.library.albums.NormalButton
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.OptionDialog
import yos.music.player.ui.widgets.basic.SearchTextField
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.TitleBarIcon
import yos.music.player.ui.widgets.basic.YosWrapper

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NormalMusic(navController: NavController) {
    val context = LocalContext.current
    // 捕获首次进入时的标题，后续 refreshTrigger 变化不改变标题
    val pageTitle = rememberSaveable { LibraryObject.getTargetListWithTitle().first }

    Column(
        Modifier
            .fillMaxSize()
        /*.statusBarsPadding()*/
    ) {
        val currentList = LibraryObject.getTargetListWithTitle().second

        val searchText = remember("NormalMusic_searchText") {
            mutableStateOf("")
        }

        val showMusic = remember("NormalMusic_showMusic") {
            derivedStateOf {
                currentList.isEmpty()
            }
        }
        if (showMusic.value) {
            val message =
                if (currentList == null) stringResource(id = R.string.tip_scanning) else stringResource(
                    id = R.string.tip_no_song
                )
            Title(
                title = pageTitle, onBack = {
                    navController.popBackStack()
                }
            ) {
                item("tip_no_song") {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    ) {
                        Text(text = message, fontSize = 18.sp, modifier = Modifier.alpha(0.6f))
                    }
                }
            }
        } else {
            val useSearch = remember { derivedStateOf { searchText.value.isNotEmpty() } }
            val list: MutableState<List<YosMediaItem>> = remember { mutableStateOf(currentList.sortX()) }

            val trigger = LibraryObject.refreshTrigger.value
            // 加入排序和搜索关键字作为 key，确保这些变更时立即重新过滤/排序
            LaunchedEffect(trigger, searchText.value, SettingsLibrary.SongSort, SettingsLibrary.EnableDescending) {
                withContext(Dispatchers.IO) {
                    val currentMusicList = LibraryObject.getTargetListWithTitle().second
                    val filteredList = if (useSearch.value) {
                        currentMusicList.asSequence().filter { song ->
                            (song.title ?: defaultTitle).contains(searchText.value, ignoreCase = true) ||
                            (song.artistsList ?: defaultArtists).any { it.contains(searchText.value, ignoreCase = true) }
                        }.toList()
                    } else {
                        currentMusicList
                    }
                    list.value = filteredList.sortX()
                }
            }

            val scope = rememberCoroutineScope()

            val showSortDialog = remember { mutableStateOf(false) }

            Box(Modifier.fillMaxSize()) {
                if (showSortDialog.value) {
                    val sortOptions = listOf(
                        SettingsLibrary.SongSortEnum.MUSIC_TITLE.ordinal to "标题",
                        SettingsLibrary.SongSortEnum.MUSIC_ALBUM.ordinal to "专辑",
                        SettingsLibrary.SongSortEnum.ARTIST_NAME.ordinal to "艺术家",
                        SettingsLibrary.SongSortEnum.MODIFIED_DATE.ordinal to "修改日期",
                        SettingsLibrary.SongSortEnum.MUSIC_ADD_DATE.ordinal to "添加时间",
                        SettingsLibrary.SongSortEnum.PLAY_COUNT.ordinal to "播放次数"
                    )
                    val sortIcons = mapOf(
                        SettingsLibrary.SongSortEnum.MUSIC_TITLE.ordinal to Icons.Filled.MusicNote,
                        SettingsLibrary.SongSortEnum.MUSIC_ALBUM.ordinal to Icons.Filled.Album,
                        SettingsLibrary.SongSortEnum.MODIFIED_DATE.ordinal to Icons.Outlined.Edit,
                        SettingsLibrary.SongSortEnum.MUSIC_ADD_DATE.ordinal to Icons.Outlined.AccessTime,
                        SettingsLibrary.SongSortEnum.PLAY_COUNT.ordinal to Icons.Filled.Star
                    )
                    val groupShape = YosRoundedCornerShape(9.dp)
                    val groupBg = MaterialTheme.colorScheme.onSecondary
                    OptionDialog(
                        icon = { Spacer(Modifier.size(0.dp)) },
                        title = "排序方式",
                        content = { dismiss ->
                            Column {
                                Text("排序依据", fontSize = 13.sp, modifier = Modifier.alpha(0.5f).padding(bottom = 8.dp, start = 2.dp))
                                Column(
                                    Modifier.fillMaxWidth().clip(groupShape).background(groupBg)
                                ) {
                                    sortOptions.forEachIndexed { index, (ordinal, label) ->
                                        if (index > 0) Spacer(Modifier.fillMaxWidth().alpha(0.08f).height(0.5.dp).background(Color.Black withNight Color.White))
                                        Row(
                                            Modifier.fillMaxWidth().height(48.dp).clickable {
                                                SongSort = ordinal; dismiss()
                                            }.padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f),
                                                color = if (SongSort == ordinal) MaterialTheme.colorScheme.primary else Color.Unspecified)
                                            val iconTint = if (SongSort == ordinal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                            if (ordinal == SettingsLibrary.SongSortEnum.ARTIST_NAME.ordinal) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_material_symbol_artist),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp),
                                                    tint = iconTint
                                                )
                                            } else {
                                                Icon(sortIcons[ordinal] ?: Icons.Filled.Check, null, Modifier.size(22.dp), tint = iconTint)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                                Text("排序顺序", fontSize = 13.sp, modifier = Modifier.alpha(0.5f).padding(bottom = 8.dp, start = 2.dp))
                                val descChecked = remember { mutableStateOf(EnableDescending) }
                                Row(
                                    Modifier.fillMaxWidth().height(48.dp)
                                        .clip(groupShape).background(groupBg)
                                        .clickable {
                                            descChecked.value = !descChecked.value
                                            EnableDescending = descChecked.value
                                        }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("降序", fontSize = 16.sp, modifier = Modifier.weight(1f))
                                    io.github.alexzhirkevich.cupertino.CupertinoSwitch(
                                        checked = descChecked.value,
                                        onCheckedChange = {
                                            descChecked.value = !descChecked.value
                                            EnableDescending = descChecked.value
                                        },
                                        modifier = Modifier.height(25.dp)
                                    )
                                }
                            }
                        },
                        onDismissRequest = { showSortDialog.value = false }
                    )
                }

                Title(
                    title = pageTitle, onBack = {
                        navController.popBackStack()
                    },
                    rightBarIcon = {
                        TitleBarIcon(
                            icon = Icons.Rounded.Sort,
                            onBack = {
                                showSortDialog.value = true
                            }
                        )
                    }
                ) {
                    item("SearchField") {
                        val keyboardController = LocalSoftwareKeyboardController.current

                        SearchTextField(
                            text = searchText.value,
                            placeholder = stringResource(id = R.string.page_library_search_songs),
                            onValueChange = {
                                searchText.value = it
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .padding(top = 5.dp),
                            onSearch = {
                                if (searchText.value.isNotEmpty()) {
                                    keyboardController?.hide()
                                }
                            })
                    }
                    item("Options") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 15.dp)
                        ) {
                            NormalButton(
                                icon = painterResource(id = R.drawable.button_icon_play),
                                label = stringResource(id = R.string.normal_button_play),
                                modifier = Modifier.weight(1f)
                            ) {
                                val items = list.value
                                if (items.isNotEmpty()) {
                                    scope.launch(Dispatchers.IO) {
                                        MediaController.prepare(
                                            items.first(),
                                            items
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(15.dp))
                            NormalButton(
                                icon = painterResource(id = R.drawable.button_icon_shuffle),
                                label = stringResource(id = R.string.normal_button_shuffle),
                                modifier = Modifier.weight(1f)
                            ) {
                                val items = list.value
                                if (items.isNotEmpty()) {
                                    scope.launch(Dispatchers.IO) {
                                        MediaController.prepare(
                                            items.random(),
                                            items,
                                            shuffleModeEnabled = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(
                        list.value,
                        key = { index, music -> music.uri?.toString() ?: music.mediaId ?: "music_$index" }
                    ) { index, music ->
                        MusicList(
                            music,
                            navController,
                            showRemoveFromPlaylist = true
                        ) {
                            scope.launch(Dispatchers.IO) {
                                MediaController.prepare(
                                    music,
                                    list.value
                                )
                            }
                        }

                        key(index) {
                            val needDivider = index < list.value.size - 1
                            if (needDivider) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 88.dp)
                                        .alpha(0.15f)
                                        .height(0.5.dp)
                                        .background(Color.Black withNight Color.White)
                                )
                            }
                        }
                    }

                    /*item("blank") {
                    Spacer(modifier = Modifier.navigationBarsHeight(15.dp))
                }*/
                }
            }
        }
    }
}

private fun List<YosMediaItem>.sortX() =
    this.sortedBy { song ->
        when (SongSort) {
            SettingsLibrary.SongSortEnum.MUSIC_TITLE.ordinal -> Pinyin.toPinyin(
                (song.title ?: defaultTitle).firstOrNull() ?: ' '
            )

            SettingsLibrary.SongSortEnum.MUSIC_DURATION.ordinal -> song.duration
            SettingsLibrary.SongSortEnum.ARTIST_NAME.ordinal -> Pinyin.toPinyin(
                (song.artistsList ?: defaultArtists).firstOrNull()?.firstOrNull() ?: ' '
            )

            SettingsLibrary.SongSortEnum.MODIFIED_DATE.ordinal -> song.modifiedDate ?: 0
            SettingsLibrary.SongSortEnum.MUSIC_ADD_DATE.ordinal -> song.addDate ?: 0
            SettingsLibrary.SongSortEnum.MUSIC_ALBUM.ordinal -> Pinyin.toPinyin((song.album?.firstOrNull()) ?: ' ')
            SettingsLibrary.SongSortEnum.PLAY_COUNT.ordinal -> MusicLibrary.getPlayCount(song.uri)
            else -> Pinyin.toPinyin((song.title ?: defaultTitle).firstOrNull() ?: ' ')
        }.toString()
    }.let {
        if (EnableDescending) {
            it.reversed()
        } else {
            it
        }
    }
