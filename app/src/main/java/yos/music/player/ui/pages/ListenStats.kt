package yos.music.player.ui.pages

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.CalendarBadgePlus
import io.github.alexzhirkevich.cupertino.icons.outlined.ClockArrowCirclepath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.FavPlayListLibrary
import yos.music.player.data.libraries.ListenStatsLibrary
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultAlbum
import yos.music.player.data.libraries.defaultArtists
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.rememberAdaptive
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImageWithCache
import yos.music.player.ui.widgets.basic.SongMenuIcon
import yos.music.player.ui.widgets.basic.Title
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// 数据模型与计算
// ---------------------------------------------------------------------------

@androidx.compose.runtime.Stable
data class TopSongEntry(val song: YosMediaItem, val plays: Int, val durationMs: Long)

@androidx.compose.runtime.Stable
data class TopArtistEntry(val name: String, val plays: Int, val thumb: Uri?)

@androidx.compose.runtime.Stable
data class TopAlbumEntry(val name: String, val plays: Int, val representative: YosMediaItem)

@androidx.compose.runtime.Stable
data class ListenStatsUiData(
    val totalDurationMs: Long,
    val totalPlays: Int,
    val totalDistinctSongs: Int,
    val weekDurationMs: Long,
    val weekPlays: Int,
    val weekDistinctSongs: Int,
    val weekDaily: List<Pair<Long, Long>>,
    val recentSongs: List<YosMediaItem>,
    val recentAlbums: List<TopAlbumEntry>,
    val recentSongCount: Int,
    val topSongsAll: List<TopSongEntry>,
    val topSongsWeek: List<TopSongEntry>,
    val topArtistsAll: List<TopArtistEntry>,
    val topArtistsWeek: List<TopArtistEntry>,
    val topAlbumsAll: List<TopAlbumEntry>,
    val topAlbumsWeek: List<TopAlbumEntry>
)

/** 在后台线程计算全部统计数据；没有任何数据时返回 null（UI 隐藏相关区块） */
fun computeListenStatsUiData(songs: List<YosMediaItem>): ListenStatsUiData? {
    val uriToSong = HashMap<String, YosMediaItem>(songs.size * 2)
    for (song in songs) {
        song.uri?.toString()?.let { uriToSong[it] = song }
    }

    val recentUris = ListenStatsLibrary.getRecentUris(200)
    val weekCounts = ListenStatsLibrary.getPlayCountsInRecentDays(7)

    // 全时段播放次数（沿用 incrementPlayCount 的历史数据；一次快照避免逐首加锁）
    val playCountSnapshot = MusicLibrary.getPlayCountSnapshot()
    val countedAll = ArrayList<Pair<YosMediaItem, Int>>(64)
    for (song in songs) {
        val count = song.uri?.toString()?.let { playCountSnapshot[it] } ?: 0
        if (count > 0) countedAll.add(song to count)
    }
    val totalDurationMs = ListenStatsLibrary.getTotalDuration()
    if (countedAll.isEmpty() && recentUris.isEmpty() && totalDurationMs <= 0L) return null

    val countedWeek = ArrayList<Pair<YosMediaItem, Int>>(32)
    for ((uri, count) in weekCounts) {
        uriToSong[uri]?.let { countedWeek.add(it to count) }
    }

    fun buildTopSongs(counted: List<Pair<YosMediaItem, Int>>, withDuration: Boolean): List<TopSongEntry> =
        counted.sortedByDescending { it.second }
            .take(10)
            .map { (song, count) ->
                TopSongEntry(
                    song = song,
                    plays = count,
                    durationMs = if (withDuration) ListenStatsLibrary.getListenDuration(song.uri?.toString() ?: "") else 0L
                )
            }

    fun buildTopArtists(counted: List<Pair<YosMediaItem, Int>>): List<TopArtistEntry> {
        val playsByArtist = LinkedHashMap<String, Int>()
        // 代表曲目：优先取有封面的最高播放次数歌曲，否则取最高播放次数歌曲
        val best = HashMap<String, Pair<YosMediaItem, Int>>()
        val bestWithArt = HashMap<String, Pair<YosMediaItem, Int>>()
        for ((song, count) in counted) {
            for (artist in song.artistsList ?: defaultArtists) {
                playsByArtist[artist] = (playsByArtist[artist] ?: 0) + count
                val current = best[artist]
                if (current == null || count > current.second) best[artist] = song to count
                if (song.thumb != null) {
                    val currentArt = bestWithArt[artist]
                    if (currentArt == null || count > currentArt.second) bestWithArt[artist] = song to count
                }
            }
        }
        return playsByArtist.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (artist, count) ->
                TopArtistEntry(
                    name = artist,
                    plays = count,
                    thumb = bestWithArt[artist]?.first?.thumb ?: best[artist]?.first?.thumb
                )
            }
    }

    fun buildTopAlbums(counted: List<Pair<YosMediaItem, Int>>): List<TopAlbumEntry> {
        val playsByAlbum = LinkedHashMap<String, Int>()
        val best = HashMap<String, Pair<YosMediaItem, Int>>()
        val bestWithArt = HashMap<String, Pair<YosMediaItem, Int>>()
        for ((song, count) in counted) {
            val album = song.album ?: defaultAlbum
            playsByAlbum[album] = (playsByAlbum[album] ?: 0) + count
            val current = best[album]
            if (current == null || count > current.second) best[album] = song to count
            if (song.thumb != null) {
                val currentArt = bestWithArt[album]
                if (currentArt == null || count > currentArt.second) bestWithArt[album] = song to count
            }
        }
        return playsByAlbum.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (album, count) ->
                TopAlbumEntry(
                    name = album,
                    plays = count,
                    representative = bestWithArt[album]?.first ?: best[album]?.first ?: counted.first().first
                )
            }
    }

    // 最近播放：歌曲（去重、最新在前，主页两列卡片最多 6 首）与专辑（按最近播放顺序）
    val recentSongs = recentUris.mapNotNull { uriToSong[it] }
    val recentAlbums = ArrayList<TopAlbumEntry>(10)
    val seenAlbums = HashSet<String>()
    for (song in recentSongs) {
        val album = song.album ?: defaultAlbum
        if (seenAlbums.add(album)) {
            recentAlbums.add(TopAlbumEntry(album, 0, song))
            if (recentAlbums.size >= 10) break
        }
    }

    return ListenStatsUiData(
        totalDurationMs = totalDurationMs,
        totalPlays = countedAll.sumOf { it.second },
        totalDistinctSongs = countedAll.size,
        weekDurationMs = ListenStatsLibrary.getDurationInRecentDays(7),
        weekPlays = countedWeek.sumOf { it.second },
        weekDistinctSongs = countedWeek.size,
        weekDaily = ListenStatsLibrary.getDailyDurations(7),
        recentSongs = recentSongs.take(12),
        recentAlbums = recentAlbums,
        recentSongCount = recentSongs.size,
        topSongsAll = buildTopSongs(countedAll, withDuration = true),
        topSongsWeek = buildTopSongs(countedWeek, withDuration = false),
        topArtistsAll = buildTopArtists(countedAll),
        topArtistsWeek = buildTopArtists(countedWeek),
        topAlbumsAll = buildTopAlbums(countedAll),
        topAlbumsWeek = buildTopAlbums(countedWeek)
    )
}

/** 进程级统计缓存：返回主页重建组合时作为首帧数据，避免区块先消失一帧再出现造成的底部闪烁 */
@Volatile
private var listenStatsCache: ListenStatsUiData? = null

/** 订阅歌曲库与统计版本号，在后台线程计算；无数据时为 null */
@Composable
fun rememberListenStatsUiData(): ListenStatsUiData? {
    val songs = MusicLibrary.songs
    val version = ListenStatsLibrary.version
    return produceState<ListenStatsUiData?>(listenStatsCache, version, songs) {
        val fresh = withContext(Dispatchers.Default) {
            runCatching { computeListenStatsUiData(songs) }.getOrNull()
        }
        // 重算结果为 null（无任何数据）时保留旧缓存意义不大，但也不用刻意清除：
        // 版本号再变化时会重新计算覆盖
        if (fresh != null) {
            listenStatsCache = fresh
        }
        value = fresh
    }.value
}

// ---------------------------------------------------------------------------
// 格式化
// ---------------------------------------------------------------------------

private enum class ListenDurationUnit { HOUR, MINUTE }

private fun unitForMs(ms: Long) = if (ms >= 3_600_000L) ListenDurationUnit.HOUR else ListenDurationUnit.MINUTE

private fun formatDurationWithUnit(context: Context, ms: Long, unit: ListenDurationUnit): String {
    val unitRes = when (unit) {
        ListenDurationUnit.HOUR -> R.string.listen_stats_unit_hour
        ListenDurationUnit.MINUTE -> R.string.listen_stats_unit_minute
    }
    val number = when (unit) {
        ListenDurationUnit.HOUR -> {
            val hours = ms / 3_600_000.0
            if (hours >= 100) hours.toInt().toString() else String.format(Locale.US, "%.1f", hours)
        }
        ListenDurationUnit.MINUTE -> (ms / 60_000L).toString()
    }
    return context.getString(R.string.listen_stats_duration_value, number, context.getString(unitRes))
}

/** 时长数字动画：首次从 0 数到目标值，之后目标变化时平滑过渡；单位按目标值锁定 */
@Composable
fun AnimatedListenDurationText(
    ms: Long,
    fontSize: TextUnit,
    fontWeight: FontWeight? = FontWeight.Bold,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val animated = remember { Animatable(0f) }
    LaunchedEffect(ms) {
        val target = ms.toFloat()
        if (animated.value < 1f) {
            animated.animateTo(target, tween(durationMillis = 950, easing = FastOutSlowInEasing))
        } else if (animated.value != target) {
            animated.animateTo(target, tween(durationMillis = 450, easing = FastOutSlowInEasing))
        }
    }
    Text(
        text = formatDurationWithUnit(context, animated.value.toLong().coerceAtLeast(0), unitForMs(ms)),
        fontSize = fontSize,
        lineHeight = fontSize,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------
// 听歌统计详情页
// ---------------------------------------------------------------------------

@Composable
fun ListenStatsPage(navController: NavController) {
    val stats = rememberListenStatsUiData()
    var weekMode by rememberSaveable { mutableStateOf(true) }

    Title(
        title = stringResource(id = R.string.listen_stats_title),
        onBack = { navController.popBackStack() }
    ) {
        if (!SettingsLibrary.ListenHistory) {
            item("listen_stats_disabled") {
                ListenStatsTip(text = stringResource(id = R.string.listen_stats_disabled))
            }
        } else if (stats == null) {
            item("listen_stats_empty") {
                ListenStatsTip(text = stringResource(id = R.string.listen_stats_empty))
            }
        } else {
            item("listen_range_selector") {
                ListenRangeSelector(
                    weekMode = weekMode,
                    onSelect = { weekMode = it }
                )
            }

            item("listen_overview") {
                ListenOverviewCard(stats, weekMode)
            }

            if (weekMode) {
                item("listen_week_chart") {
                    ListenWeekChartCard(stats)
                }
            }

            val topSongs = if (weekMode) stats.topSongsWeek else stats.topSongsAll
            if (topSongs.isNotEmpty()) {
                item("listen_top_songs_header") {
                    ListenSectionHeader(text = stringResource(id = R.string.listen_stats_top_songs))
                }
                itemsIndexed(topSongs, key = { _, entry -> entry.song.uri ?: entry.song.hashCode() }) { index, entry ->
                    TopSongRow(
                        entry = entry,
                        rank = index + 1,
                        showDuration = !weekMode,
                        queue = topSongs.map { it.song }
                    )
                }
            }

            val topArtists = if (weekMode) stats.topArtistsWeek else stats.topArtistsAll
            if (topArtists.isNotEmpty()) {
                item("listen_top_artists_header") {
                    ListenSectionHeader(text = stringResource(id = R.string.listen_stats_top_artists))
                }
                item("listen_top_artists_row") {
                    TopArtistRow(navController, topArtists)
                }
            }

            val topAlbums = if (weekMode) stats.topAlbumsWeek else stats.topAlbumsAll
            if (topAlbums.isNotEmpty()) {
                item("listen_top_albums_header") {
                    ListenSectionHeader(text = stringResource(id = R.string.listen_stats_top_albums))
                }
                item("listen_top_albums_row") {
                    TopAlbumRow(navController, topAlbums, showPlays = true)
                }
            }
        }
    }
}

@Composable
private fun ListenStatsTip(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .alpha(0.6f)
    )
}

/** 周一……iOS 风格的分段切换（本周 / 全部） */
@Composable
private fun ListenRangeSelector(weekMode: Boolean, onSelect: (Boolean) -> Unit) {
    val shape = YosRoundedCornerShape(12.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(38.dp)
            .clip(shape)
            .background((Color.Black withNight Color.White).copy(alpha = 0.07f))
    ) {
        val halfWidth = maxWidth / 2
        val indicatorX by animateDpAsState(
            targetValue = if (weekMode) 0.dp else halfWidth,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            label = "listen_range_indicator"
        )
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = indicatorX)
                .width(halfWidth - 6.dp)
                .height(32.dp)
                .clip(YosRoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Row(Modifier.fillMaxSize()) {
            SelectorOption(
                text = stringResource(id = R.string.listen_stats_week),
                selected = weekMode,
                modifier = Modifier.weight(1f)
            ) { onSelect(true) }
            SelectorOption(
                text = stringResource(id = R.string.listen_stats_all),
                selected = !weekMode,
                modifier = Modifier.weight(1f)
            ) { onSelect(false) }
        }
    }
}

@Composable
private fun SelectorOption(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else (Color.Black withNight Color.White).copy(alpha = 0.55f),
        animationSpec = tween(200),
        label = "selector_text_color"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun ListenOverviewCard(stats: ListenStatsUiData, weekMode: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(YosRoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                    )
                )
            )
            .border(0.5.dp, Color.DarkGray.copy(alpha = 0.10f), YosRoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(id = if (weekMode) R.string.listen_stats_week else R.string.listen_stats_total),
            fontSize = 14.sp,
            color = (Color.Black withNight Color.White).copy(alpha = 0.55f)
        )
        AnimatedListenDurationText(
            ms = if (weekMode) stats.weekDurationMs else stats.totalDurationMs,
            fontSize = 32.sp
        )
        Row(
            Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ListenStatPill(
                text = stringResource(
                    id = R.string.listen_stats_plays_count,
                    if (weekMode) stats.weekPlays else stats.totalPlays
                )
            )
            ListenStatPill(
                text = stringResource(
                    id = R.string.listen_stats_songs_count,
                    if (weekMode) stats.weekDistinctSongs else stats.totalDistinctSongs
                )
            )
        }
    }
}

@Composable
private fun ListenStatPill(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = (Color.Black withNight Color.White).copy(alpha = 0.65f),
        modifier = Modifier
            .clip(YosRoundedCornerShape(9.dp))
            .background((Color.Black withNight Color.White).copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun ListenWeekChartCard(stats: ListenStatsUiData) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(id = R.string.listen_stats_last_7_days),
            fontSize = 14.sp,
            color = (Color.Black withNight Color.White).copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        ListenBarsChart(
            daily = stats.weekDaily,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            barHeight = 130.dp,
            showLabels = true
        )
    }
}

/** 最近 7 天柱状图；每根柱子以弹性动画从底部生长，逐个错开 */
@Composable
fun ListenBarsChart(
    daily: List<Pair<Long, Long>>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 44.dp,
    barWidth: Dp = 6.dp,
    showLabels: Boolean = false
) {
    val maxMs = remember(daily) { daily.maxOfOrNull { it.second } ?: 0L }
    val todayIndex = daily.size - 1
    val labelFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }

    val progresses = remember(daily) { List(daily.size) { Animatable(0f) } }
    LaunchedEffect(daily) {
        progresses.forEachIndexed { index, animatable ->
            launch {
                delay(index * 45L)
                animatable.animateTo(
                    1f,
                    spring(dampingRatio = 0.8f, stiffness = 380f)
                )
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        daily.forEachIndexed { index, (_, ms) ->
            val fraction = if (maxMs > 0) 0.10f + 0.90f * (ms.toFloat() / maxMs) else 0.10f
            val isToday = index == todayIndex
            val color = if (isToday) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)

            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .height(barHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .width(barWidth)
                            .fillMaxHeight(fraction)
                            .graphicsLayer {
                                scaleY = progresses[index].value
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .background(
                                color,
                                RoundedCornerShape(topStart = barWidth / 2, topEnd = barWidth / 2)
                            )
                    )
                }
                if (showLabels) {
                    Text(
                        text = labelFormat.format(Date(daily[index].first)).uppercase(Locale.getDefault()),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        color = if (isToday) MaterialTheme.colorScheme.primary
                        else (Color.Black withNight Color.White).copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenSectionHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 6.dp)
    )
}

@Composable
private fun TopSongRow(entry: TopSongEntry, rank: Int, showDuration: Boolean, queue: List<YosMediaItem>) {
    val scope = rememberCoroutineScope()
    val isPlaying = MediaController.musicPlaying.value?.uri == entry.song.uri
    val highlightColor = MaterialTheme.colorScheme.primary
    val normalColor = Color.Black withNight Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                scope.launch(Dispatchers.IO) {
                    MediaController.prepare(entry.song, queue)
                }
            }
            .padding(horizontal = 20.dp)
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPlaying) highlightColor else normalColor.copy(alpha = 0.30f),
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.Center
        )
        ShadowImageWithCache(
            dataLambda = { entry.song.thumb }, contentDescription = null,
            modifier = Modifier.size(50.dp), cornerRadius = 6.dp,
            shadowAlpha = 0f, imageQuality = ImageQuality.LOW
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = entry.song.title ?: defaultTitle,
                fontSize = 15.5.sp,
                lineHeight = 15.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) highlightColor else normalColor
            )
            Text(
                text = entry.song.artistsName ?: defaultArtistsName,
                fontSize = 12.5.sp,
                lineHeight = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .alpha(0.5f)
                    .padding(top = 3.dp),
                color = if (isPlaying) highlightColor.copy(alpha = 0.7f) else normalColor
            )
        }
        val context = LocalContext.current
        Text(
            text = if (showDuration && entry.durationMs > 0) {
                context.getString(
                    R.string.listen_stats_duration_and_plays,
                    formatDurationWithUnit(context, entry.durationMs, unitForMs(entry.durationMs)),
                    entry.plays
                )
            } else {
                context.getString(R.string.listen_stats_plays_count, entry.plays)
            },
            fontSize = 11.5.sp,
            lineHeight = 11.5.sp,
            maxLines = 1,
            modifier = Modifier.alpha(0.45f)
        )
    }
}

@Composable
private fun TopArtistRow(navController: NavController, artists: List<TopArtistEntry>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(artists, key = { _, entry -> entry.name }) { _, entry ->
            Column(
                Modifier
                    .width(rememberAdaptive(82))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        LibraryObject.setTargetArtistName(entry.name)
                        navController.toUI(UI.ArtistInfo)
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ArtistCircleAvatar(
                    thumb = entry.thumb,
                    diameter = rememberAdaptive(76),
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = entry.name,
                    fontSize = 12.5.sp,
                    lineHeight = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp),
                    color = if (MediaController.musicPlaying.value?.artistsList?.contains(entry.name) == true)
                        MaterialTheme.colorScheme.primary else (Color.Black withNight Color.White)
                )
                Text(
                    text = stringResource(id = R.string.listen_stats_plays_count, entry.plays),
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .alpha(0.45f),
                    color = Color.Black withNight Color.White
                )
            }
        }
    }
}

/** 圆形歌手头像，与资料库歌手列表相同的裁剪与描边方式 */
@Composable
private fun ArtistCircleAvatar(thumb: Uri?, diameter: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(data = thumb).crossfade(true)
            .error(R.drawable.songcredits_monogram_person)
            .placeholder(R.drawable.songcredits_monogram_person)
            .fallback(R.drawable.songcredits_monogram_person)
            .allowHardware(true)
            .precision(Precision.INEXACT)
            .size(256)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(diameter)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
                this.shape = CircleShape
            }
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    val outline = CircleShape.createOutline(
                        Size(size.width, size.height),
                        LayoutDirection.Ltr,
                        density
                    )
                    drawOutline(
                        outline = outline,
                        color = Color.DarkGray.copy(alpha = 0.08f),
                        style = Stroke(width = 6f)
                    )
                    drawOutline(
                        outline = outline,
                        color = Color.DarkGray.copy(alpha = 0.4f),
                        style = Stroke(width = 6f),
                        blendMode = BlendMode.Overlay
                    )
                }
            }
    )
}

@Composable
private fun TopAlbumRow(
    navController: NavController,
    albums: List<TopAlbumEntry>,
    showPlays: Boolean,
    topPadding: Dp = 0.dp
) {
    LazyRow(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(albums, key = { _, entry -> entry.name }) { _, entry ->
            Column(
                Modifier
                    .width(rememberAdaptive(140))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        LibraryObject.setTargetAlbumName(entry.name)
                        navController.toUI(UI.AlbumInfo)
                    }
            ) {
                ShadowImageWithCache(
                    dataLambda = { entry.representative.thumb }, contentDescription = null,
                    modifier = Modifier.width(rememberAdaptive(140)), cornerRadius = 12.dp,
                    shadowAlpha = 0f, imageQuality = ImageQuality.HIGH
                )
                Text(
                    text = entry.name,
                    fontSize = 13.5.sp,
                    lineHeight = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                    color = Color.Black withNight Color.White
                )
                if (showPlays) {
                    Text(
                        text = stringResource(id = R.string.listen_stats_plays_count, entry.plays),
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .alpha(0.45f),
                        color = Color.Black withNight Color.White
                    )
                } else {
                    Text(
                        text = entry.representative.artistsName ?: defaultArtistsName,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .alpha(0.45f),
                        color = Color.Black withNight Color.White
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 主页区块
// ---------------------------------------------------------------------------

/** 主页快捷入口：两列渐变卡片（漫游 / 收藏 / 最近播放 / 最近添加） */
@Composable
fun HomeQuickEntries(
    navController: NavController,
    songs: List<YosMediaItem>,
    stats: ListenStatsUiData?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favCount = FavPlayListLibrary.favPlayList.size

    fun openListPage(title: String, load: () -> List<YosMediaItem>) {
        scope.launch(Dispatchers.IO) {
            val list = runCatching { load() }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                LibraryObject.setTargetListWithTitle(title, list)
                navController.toUI(UI.NormalMusic)
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickCard(
                colors = listOf(Color(0xFFF54047), Color(0xFFFF7A6B)),
                painter = painterResource(id = R.drawable.button_icon_shuffle),
                title = stringResource(id = R.string.home_quick_shuffle),
                subtitle = stringResource(id = R.string.home_quick_shuffle_desc),
                modifier = Modifier.weight(1f)
            ) {
                scope.launch(Dispatchers.IO) {
                    MediaController.startWander()
                }
            }
            QuickCard(
                colors = listOf(Color(0xFFFF375F), Color(0xFFFF7B9C)),
                painter = painterResource(id = R.drawable.ic_nowplaying_favorite),
                title = stringResource(id = R.string.home_quick_favorites),
                subtitle = stringResource(id = R.string.home_entry_songs_count, favCount),
                modifier = Modifier.weight(1f)
            ) {
                openListPage(context.getString(R.string.page_library_playlists_fav_title)) {
                    FavPlayListLibrary.favPlayList
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickCard(
                colors = listOf(Color(0xFF5E5CE6), Color(0xFF8E7CFF)),
                painter = rememberVectorPainter(CupertinoIcons.Outlined.ClockArrowCirclepath),
                title = stringResource(id = R.string.listen_stats_recent_songs),
                subtitle = stringResource(
                    id = R.string.home_entry_songs_count, stats?.recentSongCount ?: 0
                ),
                modifier = Modifier.weight(1f)
            ) {
                openListPage(context.getString(R.string.listen_stats_recent_songs)) {
                    val uriToSong = HashMap<String, YosMediaItem>()
                    for (song in MusicLibrary.songs) {
                        song.uri?.toString()?.let { uriToSong[it] = song }
                    }
                    ListenStatsLibrary.getRecentUris(200).mapNotNull { uriToSong[it] }
                }
            }
            QuickCard(
                colors = listOf(Color(0xFF30B94C), Color(0xFF5ED8A6)),
                painter = rememberVectorPainter(CupertinoIcons.Outlined.CalendarBadgePlus),
                title = stringResource(id = R.string.home_quick_recent_added),
                subtitle = stringResource(id = R.string.home_entry_songs_count, songs.size),
                modifier = Modifier.weight(1f)
            ) {
                openListPage(context.getString(R.string.home_quick_recent_added)) {
                    MusicLibrary.songs.sortedByDescending { it.addDate ?: 0L }
                }
            }
        }
    }
}

/** 渐变色块 + 图标 + 标题/数量副标题 的入口卡片 */
@Composable
private fun QuickCard(
    colors: List<Color>,
    painter: Painter,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(rememberAdaptive(64))
            .clip(YosRoundedCornerShape(14.dp))
            .background((Color.Black withNight Color.White).copy(alpha = 0.05f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(rememberAdaptive(64))
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                lineHeight = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.Black withNight Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                lineHeight = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .alpha(0.5f),
                color = Color.Black withNight Color.White
            )
        }
    }
}

/** 入场动画只在进程首次进入主页时播放；之后返回主页直接呈现，避免区块重复淡入被看成闪烁 */
private var homeEntrancePlayed = false

/** 区块入场动画：淡入 + 轻微上移，按顺序错开（仅首次） */
@Composable
fun HomeSectionEntrance(index: Int, content: @Composable () -> Unit) {
    if (homeEntrancePlayed) {
        content()
        return
    }
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(Unit) {
        // 最后一项动画结束后置位，之后返回主页不再重播
        delay(600L + index * 90L)
        homeEntrancePlayed = true
    }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 400, delayMillis = index * 90, easing = FastOutSlowInEasing)
        ) + slideInVertically(
            animationSpec = tween(durationMillis = 400, delayMillis = index * 90, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 10 }
        )
    ) {
        content()
    }
}

@Composable
private fun HomeSectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

/** 主页听歌统计概览卡片 */
@Composable
fun HomeListenStatsCard(navController: NavController, stats: ListenStatsUiData) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(YosRoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                    )
                )
            )
            .border(0.5.dp, Color.DarkGray.copy(alpha = 0.10f), YosRoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                navController.toUI(UI.ListenStats)
            }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(id = R.string.listen_stats_title),
                fontSize = 15.sp,
                lineHeight = 15.sp,
                color = (Color.Black withNight Color.White).copy(alpha = 0.60f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_action_next),
                contentDescription = null,
                modifier = Modifier
                    .height(12.dp)
                    .alpha(0.30f),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Row(
            Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                AnimatedListenDurationText(
                    ms = stats.totalDurationMs,
                    fontSize = 26.sp
                )
                val weekDurationText = formatDurationWithUnit(
                    LocalContext.current, stats.weekDurationMs, unitForMs(stats.weekDurationMs)
                )
                Text(
                    text = stringResource(
                        id = R.string.listen_stats_week_and_plays,
                        weekDurationText,
                        stats.weekPlays
                    ),
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .alpha(0.50f),
                    color = Color.Black withNight Color.White
                )
            }
            ListenBarsChart(
                daily = stats.weekDaily,
                modifier = Modifier.width(rememberAdaptive(110)),
                barHeight = 44.dp,
                barWidth = rememberAdaptive(6)
            )
        }
    }
}

/**
 * 主页“最近播放”歌曲：横向滑动的分列布局——每列 3 行（从上到下、从左到右），
 * 仅露出下一列封面起始侧的 12–16dp，所有列均吸附到相同的起始位置（RTL 镜像）
 */
@Composable
fun HomeRecentSongsSection(navController: NavController, stats: ListenStatsUiData) {
    val songs = stats.recentSongs
    if (songs.isEmpty()) return
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(vertical = 10.dp)) {
        HomeSectionTitle(text = stringResource(id = R.string.listen_stats_recent_songs))
        val columns = remember(songs) { songs.chunked(3) }
        val listState = rememberLazyListState()
        // 起始留白放在滚动视口外，吸附后前一列的菜单不会露在留白区内。
        val startPadding = rememberAdaptive(20)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
        ) {
            // 视口贴满屏幕宽，每列自带起始留白：吸附按列布局边界对齐 0，
            // 吸附后内容左缘（含列内 20dp 留白）与第一列起点一致，且不会把
            // 前一列的菜单按钮滚进屏幕。条目封面另有 8dp 内边距。
            val peek = 24.dp
            val columnWidth = with(LocalDensity.current) {
                (constraints.maxWidth - startPadding.roundToPx() - peek.roundToPx())
                    .coerceAtLeast(0).toDp()
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(
                    lazyListState = listState,
                    snapPosition = SnapPosition.Start
                )
            ) {
                items(columns, key = { it.firstOrNull()?.uri ?: it.hashCode().toString() }) { column ->
                    Column(
                        Modifier
                            .width(columnWidth)
                            .padding(start = startPadding),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        column.forEach { song ->
                            RecentSongGridItem(song, navController) {
                                scope.launch(Dispatchers.IO) {
                                    MediaController.prepare(song, songs)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSongGridItem(song: YosMediaItem, navController: NavController?, onClick: () -> Unit) {
    val isPlaying = MediaController.musicPlaying.value?.uri == song.uri
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(YosRoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShadowImageWithCache(
            dataLambda = { song.thumb }, contentDescription = null,
            modifier = Modifier.size(46.dp), cornerRadius = 8.dp,
            shadowAlpha = 0f, imageQuality = ImageQuality.HIGH
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 2.dp)
        ) {
            Text(
                text = song.title ?: defaultTitle,
                fontSize = 13.5.sp,
                lineHeight = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else (Color.Black withNight Color.White)
            )
            Text(
                text = song.artistsName ?: defaultArtistsName,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .alpha(0.45f),
                color = if (isPlaying) MaterialTheme.colorScheme.primary else (Color.Black withNight Color.White)
            )
        }
        SongMenuIcon(music = song, navController = navController)
    }
}

/** 主页“最近播放的专辑”横滑列表 */
@Composable
fun HomeRecentAlbumsSection(navController: NavController, stats: ListenStatsUiData) {
    Column(Modifier.padding(vertical = 10.dp)) {
        HomeSectionTitle(text = stringResource(id = R.string.listen_stats_recent_albums))
        TopAlbumRow(navController, stats.recentAlbums, showPlays = false, topPadding = 12.dp)
    }
}
