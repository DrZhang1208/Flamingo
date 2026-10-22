package yos.music.player.code

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import yos.music.player.code.datasource.RemoteDataSourceFactory
import java.io.File
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import yos.music.player.MainActivity
import yos.music.player.R
import yos.music.player.code.MediaController.mediaControl
import yos.music.player.code.MediaController.mediaSession
import yos.music.player.code.MediaController.uiRefreshTrigger
import yos.music.player.code.MediaController.musicPlaying
import yos.music.player.code.MediaController.playingMusicList
import yos.music.player.code.MediaController.shuffleEnabled
import yos.music.player.code.MediaController.toggleShuffle
import yos.music.player.code.utils.lrc.LyricSource
import yos.music.player.code.utils.lrc.LocalLyricsLoader
import yos.music.player.code.utils.lrc.LyricTrackInfo
import yos.music.player.code.utils.lrc.LyricsController
import yos.music.player.code.utils.lrc.StatusBarLyricsPublisher
import yos.music.player.code.utils.player.FadeExo
import yos.music.player.code.utils.player.playbackIsWaiting
import yos.music.player.code.utils.player.retryableNetworkPlaybackFailure
import yos.music.player.code.utils.player.networkPlaybackRetryDelayMs
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.code.utils.player.FadeExo.fadePlay
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.MusicLibrary.toMediaItem
import yos.music.player.data.libraries.MusicLibrary.toYosMediaItem
import yos.music.player.data.libraries.toUriStrings
import yos.music.player.data.libraries.PlayListV1
import yos.music.player.data.libraries.PlayStatus
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.uri
import yos.music.player.data.objects.MainViewModelObject
import yos.music.player.data.objects.MediaViewModelObject

@Stable
object MediaController {
    @Stable
    val mainMusicList: List<YosMediaItem>
        get() = MusicLibrary.songs

    @Stable
    var playingMusicList = mutableStateOf<List<YosMediaItem>?>(null)

    @Stable
    var mediaControl: MediaController? = null

    @Stable
    var musicPlaying = mutableStateOf<YosMediaItem?>(null)

    @Stable
    var mediaSession: MediaSession? = null

    @Stable
    var appContext: android.content.Context? = null

    /** 全局 UI 刷新计数器——扫描器/播放回调更新标签/歌词/封面后自增 */
    @Volatile var uiRefreshTrigger = 0

    /** 当前正在追踪播放次数的歌曲 URI（切歌时重置，过半后计数并清空） */
    internal var countingUri: String? = null

    /** 应用层随机播放标志。ExoPlayer 始终工作在顺序模式。 */
    @Stable
    var shuffleEnabled = mutableStateOf(false)

    /**
     * 漫游模式：队列只保留当前一首，下一首由随机算法产生（不重复），禁用上一首。
     * 任何普通 prepare 调用都会退出漫游模式。使用 Compose 状态以便 UI 置灰上一首按钮。
     */
    val wanderMode = mutableStateOf(false)

    /** 漫游会话中已播放过的 URI，全部播完后重新开始一轮 */
    private val wanderPlayedUris = mutableSetOf<String>()

    /** 漫游随机算法：排除本会话已播曲目；全部播过后清空重来 */
    private fun pickNextWanderTrack(currentUri: String?): YosMediaItem? {
        val songs = MusicLibrary.songs
        if (songs.isEmpty()) return null
        currentUri?.let { wanderPlayedUris.add(it) }
        val candidates = songs.filter { it.uri?.toString() !in wanderPlayedUris }
        val pool = if (candidates.isEmpty()) {
            wanderPlayedUris.clear()
            songs
        } else candidates
        return pool.random()
    }

    /** 开始漫游：随机选一首立即播放 */
    suspend fun startWander() {
        prepareMutex.withLock {
            val songs = MusicLibrary.songs
            if (songs.isEmpty()) return@withLock
            wanderMode.value = true
            wanderPlayedUris.clear()
            val first = songs.random()
            first.uri?.toString()?.let { wanderPlayedUris.add(it) }
            shuffleEnabled.value = false
            sourceMusicList = listOf(first)
            val item = first.toMediaItem()
            withContext(Dispatchers.Main) {
                playingMusicList.value = listOf(first)
                mediaControl?.shuffleModeEnabled = false
                mediaControl?.repeatMode = REPEAT_MODE_OFF
                mediaControl?.setMediaItems(listOf(item), 0, 0L)
                mediaControl?.prepare()
                mediaControl?.fadePlay()
                musicPlaying.value = first
                mediaControl?.let { YosPlaybackService.instance?.setCustomButtons(it) }
            }
            countingUri = first.uri?.toString()
            syncState()
        }
    }

    /** 统一的“下一首”入口：普通模式走队列，漫游模式立即触发下一次漫游选曲（暂停/缓冲态亦可） */
    fun skipToNext() {
        if (wanderMode.value) {
            wanderSkipToNext()
        } else {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                mediaControl?.seekToNextMediaItem()
            }
        }
    }

    /** 漫游中切换下一首（点下一首按钮或当前曲目自然播完） */
    fun wanderSkipToNext() {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            prepareMutex.withLock {
                if (!wanderMode.value) return@withLock
                val next = pickNextWanderTrack(musicPlaying.value?.uri?.toString()) ?: return@withLock
                next.uri?.toString()?.let { wanderPlayedUris.add(it) }
                val mc = mediaControl ?: return@withLock
                mc.addMediaItem(next.toMediaItem())
                if (mc.mediaItemCount > 1) mc.removeMediaItem(0)
                mc.prepare()
                mc.fadePlay()
                playingMusicList.value = listOf(next)
                sourceMusicList = listOf(next)
                countingUri = next.uri?.toString()
                musicPlaying.value = next
                syncState()
            }
        }
    }

    /** 原始播放列表（未打乱），用于关闭随机时恢复自然顺序 */
    var sourceMusicList: List<YosMediaItem>? = null

    internal val restoredPlayStatus = mutableStateOf<PlayStatus?>(null)

    internal fun playbackProgress(): yos.music.player.code.utils.player.PlaybackProgressSnapshot {
        val live = mediaControl?.takeIf { it.currentMediaItem != null }
        val song = musicPlaying.value
        val restored = restoredPlayStatus.value?.takeIf { it.music?.uri == song?.uri }
        return yos.music.player.code.utils.player.playbackProgressSnapshot(
            live?.currentPosition, live?.duration, restored?.position ?: 0L, song?.duration ?: 0L)
    }

    private val prepareMutex = Mutex()



    suspend fun prepare(
        music: YosMediaItem,
        thisMusicList: List<YosMediaItem>,
        position: Long = 0L,
        shuffleModeEnabled: Boolean = false,
        repeatMode: Int = REPEAT_MODE_ALL,
        play: Boolean = true,
        sourceList: List<YosMediaItem>? = null
    ) {
        prepareMutex.withLock {
            // A user may have started another song while the saved queue was loading.
            if (!play && withContext(Dispatchers.Main) { (mediaControl?.mediaItemCount ?: 0) > 0 }) return@withLock
            // 普通播放请求退出漫游模式
            wanderMode.value = false
            shuffleEnabled.value = shuffleModeEnabled

            if (thisMusicList != playingMusicList.value) {

                // 恢复时（play=false）：列表已保存时就是最终顺序，直接使用
                // 新播放时（play=true）：按需打乱
                val playbackList: List<YosMediaItem>
                val actualSourceList: List<YosMediaItem>

                if (!play && sourceList != null) {
                    // 恢复场景：使用已保存的原始列表和播放列表，不打乱
                    actualSourceList = sourceList
                    playbackList = thisMusicList
                } else if (shuffleModeEnabled) {
                    // 新播放 + 随机：打乱并保持当前曲目在首位
                    actualSourceList = thisMusicList
                    val others = thisMusicList.filter { it.uri != music.uri }.shuffled()
                    playbackList = listOf(music) + others
                } else {
                    // 新播放 + 顺序
                    actualSourceList = thisMusicList
                    playbackList = thisMusicList
                }

                sourceMusicList = actualSourceList

                val startIndex = if (shuffleModeEnabled && play) {
                    0
                } else {
                    playbackList.indexOfFirst { it.uri == music.uri }.coerceAtLeast(0)
                }

                val itemList = playbackList.map { it.toMediaItem() }

                withContext(Dispatchers.Main) {
                    playingMusicList.value = playbackList
                    mediaControl?.shuffleModeEnabled = false
                    mediaControl?.setMediaItems(itemList, startIndex, position)
                    mediaControl?.prepare()
                }

                if (!play) {
                    withContext(Dispatchers.Main) {
                        MediaViewModelObject.isPlaying.value = false
                        musicPlaying.value = music
                    }
                    refresh(music)
                }

                withContext(Dispatchers.Main) {
                    mediaControl?.repeatMode = repeatMode
                    mediaControl?.let { YosPlaybackService.instance?.setCustomButtons(it) }
                }

                if (play) {
                    withContext(Dispatchers.Main) {
                        mediaControl?.fadePlay()
                    }
                }

                // Immediately persist state on every prepare
                syncState()

            } else {
                val index = thisMusicList.indexOf(music)
                withContext(Dispatchers.Main) {
                    mediaControl?.seekToDefaultPosition(index)
                    mediaControl?.fadePlay()
                }
            }
        }
    }

    suspend fun syncState() {
        val music = musicPlaying.value ?: return
        val control = mediaControl ?: return
        val playlist = playingMusicList.value ?: listOf(music)
        val pos = withContext(Dispatchers.Main) { control.currentPosition }
        val shuffle = shuffleEnabled.value
        val repeat = withContext(Dispatchers.Main) { control.repeatMode }
        MusicLibrary.updatePlayStatus(PlayStatus(music, pos, shuffle, repeat))
        MusicLibrary.updatePlayList(PlayListV1(
            mainMusicList.toUriStrings(), playlist, sourceMusicList))
    }

    fun toggleShuffle() {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            prepareMutex.withLock {
                // 漫游模式本身就是随机播放，切换随机开关会造成状态分裂，忽略
                if (wanderMode.value) return@withLock
                val mc = mediaControl ?: return@withLock
                if (!mc.isConnected) return@withLock
                val actual = mc.currentMediaItem ?: return@withLock
                val uri = actual.localConfiguration?.uri ?: return@withLock
                val queue = playingMusicList.value ?: return@withLock
                val current = queue.firstOrNull { it.uri == uri } ?: actual.toYosMediaItem()
                val enabled = !shuffleEnabled.value
                val tail = yos.music.player.code.utils.player.playbackTail(
                    queue, sourceMusicList, uri, enabled
                ) { it.uri }
                // Build and commit without suspending, so a transition cannot change the head.
                val tailItems = tail.map { it.toMediaItem() }
                val index = mc.currentMediaItemIndex
                if (index > 0) mc.moveMediaItem(index, 0)
                if (mc.mediaItemCount > 1) mc.removeMediaItems(1, mc.mediaItemCount)
                if (tailItems.isNotEmpty()) mc.addMediaItems(1, tailItems)
                playingMusicList.value = listOf(current) + tail
                shuffleEnabled.value = enabled
                YosPlaybackService.instance?.setCustomButtons(mc)
                yos.music.player.code.utils.player.optionalPlaybackTask(
                    onFailure = { android.util.Log.e("Flamingo", "随机播放状态保存失败", it) }
                ) { syncState() }
            }
        }
    }

    fun onCase(mediaItem: YosMediaItem) {
        refresh(mediaItem)
    }

    internal var refreshJob: CompletableJob? = null

    /** RemoteRetryManager 回调：应用已提取的标签 */
    fun applyExtractedTags(
        uri: String, serverId: String, remotePath: String,
        result: yos.music.player.data.remote.RemoteTagExtractor.ExtractedResult
    ) {
        val cur = musicPlaying.value ?: return
        if (cur.uri?.toString() != uri) return
        yos.music.player.data.remote.RemoteTagDatabase.put(uri, yos.music.player.data.remote.CachedTags(
            uri = uri,
            title = result.title ?: cur.title,
            artist = result.artist ?: cur.artists,
            album = result.album ?: cur.album,
            albumArtist = result.albumArtist ?: cur.albumArtists,
            genre = result.genre ?: cur.genre,
            year = result.year ?: cur.releaseYear ?: cur.recordingYear,
            duration = if ((result.duration ?: 0) > 0) result.duration else null,
            trackNumber = result.trackNumber ?: cur.trackNumber,
            discNumber = result.discNumber ?: cur.discNumber,
            composer = result.composer ?: cur.composer,
            coverPath = result.coverUri?.toString(),
            lyrics = result.lyrics,
            bitrate = AudioMetadataUtils.reliableBitrateKbps(result.bitrate ?: cur.bitrate, cur.fileSize, result.duration ?: cur.duration),
            sampleRate = result.sampleRate ?: cur.sampleRate,
            channels = result.channels ?: cur.channels,
            fileSize = cur.fileSize
        ))
        val reliableBitrate = AudioMetadataUtils.reliableBitrateKbps(result.bitrate ?: cur.bitrate, cur.fileSize, result.duration ?: cur.duration)
        val newDuration = result.duration
        val u = cur.copy(
            title = result.title ?: cur.title,
            artists = result.artist ?: cur.artists,
            album = result.album ?: cur.album,
            albumArtists = result.albumArtist ?: cur.albumArtists,
            genre = result.genre ?: cur.genre,
            composer = result.composer ?: cur.composer,
            thumb = result.coverUri ?: cur.thumb,
            releaseYear = result.year ?: cur.releaseYear,
            recordingYear = result.year ?: cur.recordingYear,
            trackNumber = result.trackNumber ?: cur.trackNumber,
            discNumber = result.discNumber ?: cur.discNumber,
            bitrate = reliableBitrate ?: result.bitrate ?: cur.bitrate,
            sampleRate = result.sampleRate ?: cur.sampleRate,
            channels = result.channels ?: cur.channels,
            tagScanStatus = "COMPLETE",
            duration = if (newDuration != null && newDuration > 0 && newDuration != cur.duration) newDuration else cur.duration
        )
        musicPlaying.value = u
        refreshJob?.cancel()
        if (u.thumb != null && u.thumb != cur.thumb) {
            MediaViewModelObject.updateNowPlayingBackgroundIfCurrent(u.uri?.toString(), u.thumb)
        }
        if (u.thumb != cur.thumb || u.title != cur.title || u.artists != cur.artists || u.album != cur.album) {
            val idx = mediaControl?.currentMediaItemIndex
            if (idx != null && idx >= 0) {
                mediaControl?.replaceMediaItem(idx, u.toMediaItem())
            }
        }
        MusicLibrary.updateSongInFullList(u)
        uiRefreshTrigger++
        LyricsController.submitRawLyrics(
            trackKey = uri,
            rawLyrics = result.lyrics,
            currentPositionMs = mediaControl?.currentPosition?.toInt() ?: 0,
            source = LyricSource.REMOTE_SCAN
        )
    }

    private fun refresh(music: YosMediaItem) {
        refreshJob?.cancel()
        val job = Job()
        refreshJob = job
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        yos.music.player.code.utils.player.postPlaybackUpdate(job, { handler.post(it) }) {
            val trackKey = music.uri?.toString()
            val actualKey = mediaControl?.currentMediaItem?.localConfiguration?.uri?.toString()
            if (actualKey != null && actualKey != trackKey) return@postPlaybackUpdate
            musicPlaying.value = music
            MediaViewModelObject.setNowPlayingBackground(trackKey, music.thumb)
            appContext?.let {
                yos.music.player.ui.widgets.effects.preloadNowPlayingBackground(it, trackKey, music.thumb)
            }
            LyricsController.activateTrack(trackKey, mediaControl?.currentPosition?.toInt() ?: 0)
        }
    }

}

@OptIn(UnstableApi::class)
private class TickerMediaNotificationProvider(
    private val delegate: DefaultMediaNotificationProvider,
    private val tickerProvider: () -> String?
) : MediaNotification.Provider {
    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val result = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            onNotificationChangedCallback
        )
        val ticker = tickerProvider()
        result.notification.tickerText = ticker
        if (!ticker.isNullOrBlank()) {
            result.notification.extras.putInt("ticker_icon", R.drawable.flamingo_ticker_icon)
            result.notification.extras.putBoolean("ticker_icon_switch", false)
        }
        return result
    }

    override fun handleCustomCommand(mediaSession: MediaSession, action: String, extras: Bundle): Boolean =
        delegate.handleCustomCommand(mediaSession, action, extras)
}

class YosPlaybackService : MediaSessionService() {
    private var focusPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    fun updateAudioFocusHandling(enabled: Boolean) {
        focusPlayer?.let { it.setAudioAttributes(it.audioAttributes, enabled) }
    }

    private val notificationID = 1145
    private val channelID = "YosMediaControllerChannel"

    private val shuffleMode = "shuffle_mode"
    private val repeatMode = "repeat_mode"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private var networkPlaybackRetry: Job? = null
    private var listenDurationJob: Job? = null
    private var networkRetryUri: String? = null
    private var networkRetryAttempt = 0

    private fun cancelNetworkPlaybackRetry() {
        networkPlaybackRetry?.cancel()
        networkPlaybackRetry = null
        networkRetryUri = null
        networkRetryAttempt = 0
    }

    private val localLyricsLoader by lazy { LocalLyricsLoader(this, serviceScope) }

    /**
     * 队列顺序与播放器条目一致：切歌后预解码下一首的封面、背景纹理与本地歌词，
     * 换歌首帧全部命中缓存（内存换流畅的主项）。
     */
    private fun prefetchNextTrack(currentUri: String) {
        val queue = playingMusicList.value ?: return
        if (queue.size < 2) return
        val index = queue.indexOfFirst { it.uri?.toString() == currentUri }
        if (index < 0) return
        val next = queue.getOrNull(index + 1)
            ?: queue.firstOrNull().takeIf { mediaControl?.repeatMode == REPEAT_MODE_ALL }
            ?: return
        val key = next.uri?.toString() ?: return
        val thumb = next.thumb ?: return
        // 服务自身即 Context；应用级 ImageLoader 单例持有预解码结果
        val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        coil.Coil.imageLoader(this).enqueue(
            yos.music.player.ui.widgets.nowPlayingArtworkRequest(this, thumb, width))
        yos.music.player.ui.widgets.effects.preloadNowPlayingBackground(this, key, thumb)
        if (next.serverId == null) {
            next.uri?.path?.let { path ->
                localLyricsLoader.prefetchNext(key, path, next.duration ?: 0L)
            }
        }
    }    private lateinit var statusBarLyricsPublisher: StatusBarLyricsPublisher
    @Volatile private var tickerLyric: String? = null

    companion object {
        @Volatile var instance: YosPlaybackService? = null

        @Volatile private var sharedCache: SimpleCache? = null
        private val sharedCacheLock = Any()

        private fun getOrCreateCache(context: android.content.Context, desiredBytes: Long): SimpleCache? {
            val finalBytes = if (desiredBytes <= 0L) Long.MAX_VALUE else desiredBytes
            synchronized(sharedCacheLock) {
                sharedCache?.let { return it }
                return runCatching {
                    val dir = File(context.cacheDir, "audio_cache")
                    dir.mkdirs()
                    SimpleCache(
                        dir,
                        LeastRecentlyUsedCacheEvictor(finalBytes),
                        androidx.media3.database.StandaloneDatabaseProvider(context)
                    ).also { sharedCache = it }
                }.getOrNull()
            }
        }

        private fun releaseSharedCache() {
            synchronized(sharedCacheLock) {
                runCatching { sharedCache?.release() }
                sharedCache = null
            }
        }

        fun removeCachedResources(resourceKeys: Collection<String>) {
            synchronized(sharedCacheLock) {
                val cache = sharedCache ?: return
                resourceKeys.forEach { key ->
                    runCatching { cache.removeResource(key) }
                }
            }
        }

        fun clearCachedResources() {
            synchronized(sharedCacheLock) {
                val cache = sharedCache ?: return
                runCatching {
                    cache.keys.toList().forEach { key ->
                        runCatching { cache.removeResource(key) }
                    }
                }
            }
        }

        fun getCachedResourceBytes(): Long {
            synchronized(sharedCacheLock) {
                return runCatching { sharedCache?.cacheSpace ?: 0L }.getOrDefault(0L)
            }
        }

        /** Local files no longer route through the cache; drop their stale entries once. */
        private fun pruneLocalCacheEntries(cache: androidx.media3.datasource.cache.Cache) {
            // One-shot bounded cleanup; the cache is not yet serving playback at onCreate.
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching {
                    cache.keys.toList().forEach { key ->
                        val scheme = runCatching { android.net.Uri.parse(key).scheme }.getOrNull()
                        if (!yos.music.player.code.datasource.shouldRouteToPlaybackCache(key, scheme)) {
                            runCatching { cache.removeResource(key) }
                        }
                    }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun setCustomButtons(player: ForwardingPlayer) {
        if (SettingsLibrary.NotificationEnableIcon) {
            val useSmallerIcon = SettingsLibrary.NotificationSmallerIcon

            val shuffleButtonIcon =
                if (shuffleEnabled.value) {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle else R.drawable.ic_shuffle
                } else {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle_off else R.drawable.ic_shuffle_off
                }
            val shuffleButton = CommandButton.Builder()
                .setIconResId(shuffleButtonIcon)
                .setDisplayName(shuffleMode)
                .setSessionCommand(SessionCommand(shuffleMode, Bundle()))
                .build()

            val repeatButtonIcon =
                when (player.repeatMode) {
                    REPEAT_MODE_ONE -> if (useSmallerIcon) R.drawable.ic_mini_repeat_one else R.drawable.ic_repeat_one
                    REPEAT_MODE_ALL -> if (useSmallerIcon) R.drawable.ic_mini_repeat else R.drawable.ic_repeat
                    else -> if (useSmallerIcon) R.drawable.ic_mini_repeat_off else R.drawable.ic_repeat_off
                }
            val repeatButton = CommandButton.Builder()
                .setIconResId(repeatButtonIcon)
                .setDisplayName(repeatMode)
                .setSessionCommand(SessionCommand(repeatMode, Bundle()))
                .build()

            mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton, repeatButton))
        } else {
            mediaSession?.setCustomLayout(emptyList())
        }
    }

    fun setCustomButtons(player: MediaController) {
        if (SettingsLibrary.NotificationEnableIcon) {
            val useSmallerIcon = SettingsLibrary.NotificationSmallerIcon

            val shuffleButtonIcon =
                if (shuffleEnabled.value) {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle else R.drawable.ic_shuffle
                } else {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle_off else R.drawable.ic_shuffle_off
                }
            val shuffleButton = CommandButton.Builder()
                .setIconResId(shuffleButtonIcon)
                .setDisplayName(shuffleMode)
                .setSessionCommand(SessionCommand(shuffleMode, Bundle()))
                .build()

            val repeatButtonIcon =
                when (player.repeatMode) {
                    REPEAT_MODE_ONE -> if (useSmallerIcon) R.drawable.ic_mini_repeat_one else R.drawable.ic_repeat_one
                    REPEAT_MODE_ALL -> if (useSmallerIcon) R.drawable.ic_mini_repeat else R.drawable.ic_repeat
                    else -> if (useSmallerIcon) R.drawable.ic_mini_repeat_off else R.drawable.ic_repeat_off
                }
            val repeatButton = CommandButton.Builder()
                .setIconResId(repeatButtonIcon)
                .setDisplayName(repeatMode)
                .setSessionCommand(SessionCommand(repeatMode, Bundle()))
                .build()

            mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton, repeatButton))
        } else {
            mediaSession?.setCustomLayout(emptyList())
        }
    }

    private var saveJob: Job? = null

    fun saveDataNow() {
        saveDataAsync(delayMs = 0)
    }

    fun saveDataWithDelay() {
        saveDataAsync(delayMs = 200)
    }

    private fun saveDataAsync(delayMs: Long) {
        saveJob?.cancel()
        saveJob = serviceScope.launch {
            // 在主线程读取所有需要的值
            val music = musicPlaying.value ?: return@launch
            val control = mediaControl?.takeIf {
                it.currentMediaItem?.localConfiguration?.uri == music.uri && it.currentMediaItem != null
            } ?: return@launch // The startup preview is not a live session to save back as zero.
            val playlist = playingMusicList.value ?: listOf(music)
            val pos = control.currentPosition
            val shuffle = shuffleEnabled.value
            val repeat = runCatching { mediaControl?.repeatMode ?: REPEAT_MODE_ALL }.getOrDefault(REPEAT_MODE_ALL)
            val mainListUris = yos.music.player.code.MediaController.mainMusicList.toUriStrings()
            val srcList = yos.music.player.code.MediaController.sourceMusicList
            if (delayMs > 0) delay(delayMs)
            // 序列化和 MMKV 写入移到 IO 线程
            withContext(Dispatchers.IO) {
                yos.music.player.code.utils.player.optionalPlaybackTask(
                    onFailure = { android.util.Log.e("Flamingo", "播放状态保存失败，继续播放", it) }
                ) {
                    MusicLibrary.updatePlayStatus(PlayStatus(music, pos, shuffle, repeat))
                    MusicLibrary.updatePlayList(PlayListV1(mainListUris, playlist, srcList))
                }
            }
        }
    }

    private fun saveData() {
        // 同步保存，仅用于 onDestroy/onTaskRemoved
        val music = musicPlaying.value ?: return
        val playlist = playingMusicList.value ?: listOf(music)
        val control = mediaControl?.takeIf {
            it.currentMediaItem?.localConfiguration?.uri == music.uri && it.currentMediaItem != null
        } ?: return
        val pos = control.currentPosition
        val shuffle = shuffleEnabled.value
        val repeat = runCatching { control?.repeatMode ?: REPEAT_MODE_ALL }.getOrDefault(REPEAT_MODE_ALL)
        runBlocking(Dispatchers.IO) {
            yos.music.player.code.utils.player.optionalPlaybackTask(
                onFailure = { android.util.Log.e("Flamingo", "播放状态保存失败", it) }
            ) {
                MusicLibrary.updatePlayStatus(PlayStatus(music, pos, shuffle, repeat))
                MusicLibrary.updatePlayList(PlayListV1(
                    yos.music.player.code.MediaController.mainMusicList.toUriStrings(),
                    playlist,
                    yos.music.player.code.MediaController.sourceMusicList))
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        yos.music.player.code.MediaController.appContext = this
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val cacheSizeBytes = SettingsLibrary.RemoteCacheSizeMB.toLong() * 1024L * 1024L
        val cache = getOrCreateCache(this, cacheSizeBytes)
        val upstreamFactory = RemoteDataSourceFactory(this)
        if (cache != null) pruneLocalCacheEntries(cache)

        val player = ExoPlayer.Builder(
            this,
            YosRenderFactory(this)
                .setEnableAudioFloatOutput(
                    SettingsLibrary.AudioFloatOutput
                )
                .setEnableDecoderFallback(true)
                .setEnableAudioTrackPlaybackParams(
                    SettingsLibrary.HardwareAudioTrackPlayBackParams
                )
                .setExtensionRendererMode(
                    when (SettingsLibrary.Codec) {
                        "Auto" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                        "System" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                        else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    }
                )
        )
            .setMediaSourceFactory(
                ProgressiveMediaSource.Factory(
                    if (cache != null) {
                        yos.music.player.code.datasource.CacheRoutingDataSourceFactory(
                            upstreamFactory,
                            CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstreamFactory)
                        )
                    } else {
                        upstreamFactory
                    }
                )
            )
            .setAudioAttributes(
                audioAttributes,
                // Media3 owns pause/duck/resume. Permanent focus loss waits for explicit play.
                SettingsLibrary.AudioAttributes
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        focusPlayer = player

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun play() {
                player.fadePlay()
            }

            override fun pause() {
                player.fadePause()
            }

            override fun isPlaying(): Boolean {
                return player.isPlaying
            }

            // 漫游模式：禁用上一首，下一首走漫游随机算法
            override fun getAvailableCommands(): Player.Commands {
                val base = super.getAvailableCommands()
                return if (yos.music.player.code.MediaController.wanderMode.value) {
                    base.buildUpon()
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .build()
                } else base
            }

            override fun isCommandAvailable(command: Int): Boolean {
                if (yos.music.player.code.MediaController.wanderMode.value &&
                    (command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                        command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                ) {
                    return false
                }
                return super.isCommandAvailable(command)
            }

            override fun seekToPreviousMediaItem() {
                if (!yos.music.player.code.MediaController.wanderMode.value) super.seekToPreviousMediaItem()
            }

            override fun seekToPrevious() {
                if (!yos.music.player.code.MediaController.wanderMode.value) super.seekToPrevious()
            }

            override fun hasPreviousMediaItem(): Boolean {
                return !yos.music.player.code.MediaController.wanderMode.value && super.hasPreviousMediaItem()
            }

            override fun seekToNextMediaItem() {
                if (yos.music.player.code.MediaController.wanderMode.value) {
                    yos.music.player.code.MediaController.wanderSkipToNext()
                } else super.seekToNextMediaItem()
            }

            override fun seekToNext() {
                if (yos.music.player.code.MediaController.wanderMode.value) {
                    yos.music.player.code.MediaController.wanderSkipToNext()
                } else super.seekToNext()
            }
        }

        forwardingPlayer.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    if (tracks.isEmpty) return

                    val path = player.currentMediaItem?.uri
                    val thisPath = path?.path
                    val rawUri = path?.toString() ?: ""
                    val isRemote = yos.music.player.data.remote.RemoteUri.parseServerId(rawUri) != null

                    var samplingRate = 0
                    var bitrate = 0
                    var haveJOC = false

                    for (i in tracks.groups) {
                        for (j in 0 until i.length) {
                            if (!i.isTrackSelected(j)) continue
                            val trackFormat = i.getTrackFormat(j)
                            samplingRate = trackFormat.sampleRate
                            bitrate = AudioMetadataUtils.normalizeBitrateKbps(trackFormat.bitrate)
                            haveJOC =
                                trackFormat.sampleMimeType?.contains("-joc", ignoreCase = true)
                                    ?: false
                            break
                        }
                    }

                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    val playerDuration = player.duration
                    handler.post {
                        if (player.currentMediaItem?.uri?.toString() != rawUri) return@post
                        MediaViewModelObject.isDolby.value = haveJOC
                        if (samplingRate > 0) MediaViewModelObject.samplingRate.intValue = samplingRate
                        if (bitrate > 0) MediaViewModelObject.bitrate.intValue = bitrate
                        // 用 ExoPlayer 实际时长修正存储的 duration，合并在同一个 handler post 中避免多次重组
                        if (playerDuration > 0L && !isRemote) {
                            val cur = musicPlaying.value
                            if (cur != null && cur.uri?.toString() == rawUri && cur.serverId == null && cur.duration != playerDuration) {
                                val corrected = cur.copy(duration = playerDuration)
                                musicPlaying.value = corrected
                                MusicLibrary.updateSongInFullList(corrected)
                            }
                        }
                    }

                    // 将文件 I/O 移到后台线程，避免阻塞 UI
                    if (!isRemote && thisPath != null) {
                        val capturedUri = rawUri // 捕获当前曲目 URI，用于回调时校验
                        localLyricsLoader.load(
                            trackKey = capturedUri,
                            audioPath = thisPath,
                            durationMs = playerDuration.coerceAtLeast(0L),
                            currentPositionMs = player.currentPosition.toInt()
                        )
                        prefetchNextTrack(capturedUri)
                        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching {
                                if (samplingRate == 0 || bitrate == 0) {
                                    val audioInfo = AudioMetadataUtils.getQualityInfos(thisPath)
                                    handler.post {
                                        if (player.currentMediaItem?.uri?.toString() != capturedUri) return@post
                                        if (samplingRate == 0) MediaViewModelObject.samplingRate.intValue = audioInfo.second
                                        if (bitrate == 0) MediaViewModelObject.bitrate.intValue = audioInfo.first
                                    }
                                }
                                // 从文件中提取音轨号和碟号，补充未扫描歌曲的缺失字段
                                val cur = musicPlaying.value
                                if (cur != null && (cur.trackNumber == null || cur.discNumber == null)) {
                                    val trackNo = AudioMetadataUtils.getTrackNumber(thisPath)
                                    val discNo = AudioMetadataUtils.getDiscNumber(thisPath)
                                    if (trackNo != null || discNo != null) {
                                        handler.post {
                                            if (player.currentMediaItem?.uri?.toString() == capturedUri) {
                                                applyTags(cur, "TAGLIB",
                                                    title = null, artist = null, album = null,
                                                    thumb = null, year = null, lyrics = null,
                                                    trackNumber = trackNo, discNumber = discNo)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (isRemote && (samplingRate == 0 || bitrate == 0)) {
                        // 远程歌曲：ExoPlayer TrackFormat 未提供时，从 YosMediaItem 或缓存回退
                        val cur = musicPlaying.value
                        val capturedUri = rawUri
                        if (cur != null && ((cur.bitrate ?: 0) > 0 || (cur.sampleRate ?: 0) > 0)) {
                            handler.post {
                                if (player.currentMediaItem?.uri?.toString() != capturedUri) return@post
                                if (samplingRate == 0) MediaViewModelObject.samplingRate.intValue = cur.sampleRate ?: 0
                                if (bitrate == 0) MediaViewModelObject.bitrate.intValue =
                                    AudioMetadataUtils.reliableBitrateKbps(cur.bitrate, cur.fileSize, cur.duration) ?: 0
                            }
                        } else {
                            serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val cached = yos.music.player.data.remote.RemoteTagDatabase.get(capturedUri)
                                handler.post {
                                    if (player.currentMediaItem?.uri?.toString() != capturedUri) return@post
                                    if (samplingRate == 0) MediaViewModelObject.samplingRate.intValue = cached?.sampleRate ?: 0
                                    if (bitrate == 0) MediaViewModelObject.bitrate.intValue =
                                        AudioMetadataUtils.reliableBitrateKbps(cached?.bitrate, cached?.fileSize ?: cur?.fileSize, cached?.duration ?: cur?.duration) ?: 0
                                }
                            }
                        }
                    }
                }

                /** TAGLIB 来源可覆盖所有字段；其他来源永不覆盖已有数据 */
                private fun applyTags(
                    item: yos.music.player.data.libraries.YosMediaItem,
                    source: String,
                    title: String?,
                    artist: String?,
                    album: String?,
                    thumb: android.net.Uri?,
                    year: Int?,
                    lyrics: String?,
                    duration: Long? = null,
                    trackNumber: Int? = null,
                    discNumber: Int? = null,
                    albumArtist: String? = null,
                    genre: String? = null,
                    composer: String? = null,
                    bitrate: Int? = null,
                    sampleRate: Int? = null,
                    channels: Int? = null
                ) {
                    val cur = musicPlaying.value ?: return
                    if (item.uri != null && cur.uri != item.uri) return
                    val overwrite = source != "EXOPLAYER"
                    val u = cur.copy(
                        title = if (overwrite && title != null) title else cur.title,
                        artists = if (overwrite && artist != null) artist else cur.artists,
                        album = if (overwrite && album != null) album else cur.album,
                        albumArtists = if (overwrite && albumArtist != null) albumArtist else cur.albumArtists,
                        genre = if (overwrite && genre != null) genre else cur.genre,
                        composer = if (overwrite && composer != null) composer else cur.composer,
                        thumb = if (overwrite && thumb != null) thumb else cur.thumb,
                        releaseYear = year ?: cur.releaseYear, recordingYear = year ?: cur.recordingYear,
                        trackNumber = if (overwrite && trackNumber != null) trackNumber else cur.trackNumber,
                        discNumber = if (overwrite && discNumber != null) discNumber else cur.discNumber,
                            bitrate = AudioMetadataUtils.reliableBitrateKbps(bitrate ?: cur.bitrate, cur.fileSize, duration ?: cur.duration) ?: bitrate ?: cur.bitrate,
                        sampleRate = sampleRate ?: cur.sampleRate,
                        channels = channels ?: cur.channels,
                        tagScanStatus = if (overwrite) "COMPLETE" else cur.tagScanStatus,
                        duration = if (overwrite && duration != null && duration > 0 && duration != cur.duration) duration else cur.duration
                    )
                    musicPlaying.value = u
                    // EXOPLAYER 源不覆盖 thumb，跳过背景更新避免晚到元数据覆写当前曲目背景。
                    if (source != "EXOPLAYER") {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            MediaViewModelObject.updateNowPlayingBackgroundIfCurrent(
                                trackKey = u.uri?.toString(),
                                artworkUri = u.thumb
                            )
                        }
                    }
                    // 标签变更后同步更新 Media3 通知栏 Metadata
                    if (u.thumb != cur.thumb || u.title != cur.title || u.artists != cur.artists || u.album != cur.album) {
                        val idx = yos.music.player.code.MediaController.mediaControl?.currentMediaItemIndex
                        if (idx != null && idx >= 0) {
                            yos.music.player.code.MediaController.mediaControl?.replaceMediaItem(idx, u.toMediaItem())
                        }
                    }
                    MusicLibrary.updateSongInFullList(u)
                    uiRefreshTrigger++
                    LyricsController.submitRawLyrics(
                        item.uri?.toString(),
                        lyrics,
                        mediaControl?.currentPosition?.toInt() ?: 0,
                        when (source) {
                            "DB_CACHE" -> LyricSource.REMOTE_CACHE
                            "REMOTE_SCAN" -> LyricSource.REMOTE_SCAN
                            else -> LyricSource.EMBEDDED_TAG
                        }
                    )
                    if (item.serverId != null) {
                        val tagUri = item.uri?.toString() ?: ""
                        val cachedForMerge = yos.music.player.data.remote.RemoteTagDatabase.get(tagUri)
                        yos.music.player.data.remote.RemoteTagDatabase.put(tagUri, yos.music.player.data.remote.CachedTags(
                            uri = tagUri,
                            title = u.title,
                            artist = u.artists,
                            album = u.album,
                            albumArtist = u.albumArtists,
                            genre = u.genre,
                            year = u.releaseYear ?: u.recordingYear,
                            duration = if (u.duration > 0) u.duration else null,
                            trackNumber = u.trackNumber,
                            discNumber = u.discNumber,
                            composer = u.composer,
                            coverPath = u.thumb?.toString() ?: cachedForMerge?.coverPath,
                            lyrics = lyrics ?: cachedForMerge?.lyrics,
                            bitrate = u.bitrate,
                            sampleRate = u.sampleRate,
                            channels = u.channels,
                            fileSize = u.fileSize
                        ))
                    }
                    yos.music.player.data.objects.LibraryObject.updateSongInTargetList(u)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    cancelNetworkPlaybackRetry()
                    yos.music.player.data.remote.RemoteRetryManager.onTrackChanged(
                        mediaItem?.localConfiguration?.uri?.toString()
                    )
                    
                    // 定时关闭: 切歌时检查是否需要取消
                    // reason == TRANSITION_REASON_AUTO 表示歌曲自然播放结束，不算切歌
                    val newUri = mediaItem?.localConfiguration?.uri?.toString()
                    SleepTimerManager.onTrackChanged(newUri, reason)
                    
                    // 定时关闭: 歌曲自然播放完毕时检查是否应该退出
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        SleepTimerManager.checkFinishCurrentSong(newUri)
                    }
                    
                    mediaItem?.let {
                        val yosItem = it.toYosMediaItem()
                        // 开始追踪新歌的播放次数
                        yos.music.player.code.MediaController.countingUri = yosItem.uri?.toString()
                        val handler = android.os.Handler(android.os.Looper.getMainLooper())
                        if (yosItem.serverId != null) {
                            val cached = yos.music.player.data.remote.RemoteTagDatabase.get(yosItem.uri?.toString() ?: "")
                            // 验证封面缓存文件是否仍然存在（系统清除缓存会删除 cover 文件）
                            val coverMissing = cached?.coverPath != null && !java.io.File(cached.coverPath.removePrefix("file://").removePrefix("file:")).exists()
                            val coverUri = if (!coverMissing) cached?.coverPath?.let { android.net.Uri.parse(it) } else null
                            val hasTags = cached != null && (cached.artist != null || cached.album != null || cached.lyrics != null)
                            // 投递到主线程消息队列，避免在组合帧内触发重组导致 deactivated node
                            handler.post {
                                if (mediaControl?.currentMediaItem?.localConfiguration?.uri != yosItem.uri) return@post
                                val trackKey = yosItem.uri?.toString()
                                LyricsController.activateTrack(
                                    trackKey,
                                    mediaControl?.currentPosition?.toInt() ?: 0
                                )
                                prefetchNextTrack(trackKey ?: "")
                                musicPlaying.value = yosItem
                                MediaViewModelObject.setNowPlayingBackground(
                                    trackKey = yosItem.uri?.toString(),
                                    artworkUri = coverUri ?: yosItem.thumb
                                )
                                // 远程曲目与本地一致预载背景纹理，避免打开播放页时首帧现算闪黑
                                yos.music.player.ui.widgets.effects.preloadNowPlayingBackground(
                                    this@YosPlaybackService, trackKey, coverUri ?: yosItem.thumb)
                                if (hasTags) {
                                    applyTags(
                                        yosItem,
                                        "DB_CACHE",
                                        cached!!.title,
                                        cached.artist,
                                        cached.album,
                                        coverUri,
                                        cached.year,
                                        cached.lyrics,
                                        cached.duration,
                                        cached.trackNumber,
                                        cached.discNumber,
                                        cached.albumArtist,
                                        cached.genre,
                                        cached.composer,
                                        cached.bitrate,
                                        cached.sampleRate,
                                        cached.channels
                                    )
                                }
                                // 从缓存恢复质量信息，避免后台扫描结果丢失
                                val q = cached
                                if (q != null && ((q.bitrate ?: 0) > 0 || (q.sampleRate ?: 0) > 0)) {
                                    val updated = (musicPlaying.value ?: yosItem).copy(
                                        bitrate = AudioMetadataUtils.reliableBitrateKbps(q.bitrate ?: yosItem.bitrate, q.fileSize ?: yosItem.fileSize, q.duration ?: yosItem.duration) ?: q.bitrate ?: yosItem.bitrate,
                                        sampleRate = q.sampleRate ?: yosItem.sampleRate,
                                        channels = q.channels ?: yosItem.channels
                                    )
                                    musicPlaying.value = updated
                                    MusicLibrary.updateSongInFullList(updated)
                                    MediaViewModelObject.samplingRate.intValue = q.sampleRate ?: 0
                                    MediaViewModelObject.bitrate.intValue =
                                        AudioMetadataUtils.reliableBitrateKbps(q.bitrate, q.fileSize ?: yosItem.fileSize, q.duration ?: yosItem.duration) ?: 0
                                }
                            }
                            // 始终后台提取最新标签（远程文件标签可能已更新），缓存先展示
                            serviceScope.launch(Dispatchers.IO) { extractAndApplyTags(yosItem) }
                        } else {
                            yos.music.player.code.MediaController.onCase(yosItem)
                        }
                    }
                    super.onMediaItemTransition(mediaItem, reason)
                }

                private suspend fun extractAndApplyTags(item: yos.music.player.data.libraries.YosMediaItem) {
                    val path = item.uri?.path ?: return
                    val serverId = item.serverId ?: return
                    val uri = item.uri?.toString() ?: return
                    try {
                        if (!yos.music.player.data.remote.RemoteServerManager.isConnected(serverId)) {
                            yos.music.player.data.remote.RemoteServerManager.connect(serverId)
                        }
                        val result = yos.music.player.data.remote.RemoteTagExtractor.extract(serverId, path, uri)
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        yos.music.player.data.remote.RemoteTagDatabase.put(uri, yos.music.player.data.remote.CachedTags(
                            uri = uri,
                            title = result.title ?: item.title,
                            artist = result.artist ?: item.artists,
                            album = result.album ?: item.album,
                            albumArtist = result.albumArtist ?: item.albumArtists,
                            genre = result.genre ?: item.genre,
                            year = result.year ?: item.releaseYear ?: item.recordingYear,
                            duration = if ((result.duration ?: 0) > 0) result.duration else null,
                            trackNumber = result.trackNumber ?: item.trackNumber,
                            discNumber = result.discNumber ?: item.discNumber,
                            composer = result.composer ?: item.composer,
                            coverPath = result.coverUri?.toString(),
                            lyrics = result.lyrics,
                            bitrate = AudioMetadataUtils.reliableBitrateKbps(result.bitrate, item.fileSize, result.duration ?: item.duration),
                            sampleRate = result.sampleRate,
                            channels = result.channels,
                            fileSize = item.fileSize
                        ))
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (musicPlaying.value?.uri != item.uri) return@withContext
                            yos.music.player.data.remote.RemoteRetryManager.onTagExtractSuccess(uri)
                            yos.music.player.code.MediaController.refreshJob?.cancel()
                            applyTags(item, "TAGLIB",
                                result.title ?: item.title,
                                result.artist ?: item.artists,
                                result.album ?: item.album,
                                result.coverUri, result.year, result.lyrics,
                                result.duration,
                                result.trackNumber,
                                result.discNumber,
                                result.albumArtist,
                                result.genre,
                                result.composer,
                                result.bitrate,
                                result.sampleRate,
                                result.channels)
                            // 同步更新质量信息到 musicPlaying
                            val q = musicPlaying.value
                            if (q != null && ((result.bitrate ?: 0) > 0 || (result.sampleRate ?: 0) > 0 || (result.channels ?: 0) > 0)) {
                                musicPlaying.value = q.copy(
                                    bitrate = AudioMetadataUtils.reliableBitrateKbps(result.bitrate ?: q.bitrate, q.fileSize, result.duration ?: q.duration) ?: result.bitrate ?: q.bitrate,
                                    sampleRate = result.sampleRate ?: q.sampleRate,
                                    channels = result.channels ?: q.channels
                                )
                            }
                            musicPlaying.value?.let { MusicLibrary.updateSongInFullList(it) }
                            uiRefreshTrigger++
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        yos.music.player.data.remote.RemoteRetryManager.onTagExtractFailed(uri, serverId, path)
                    }
                }

                /*override fun onIsPlayingChanged(isPlaying: Boolean) {
                    saveData()
                    super.onIsPlayingChanged(isPlaying)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    saveData()
                    super.onRepeatModeChanged(repeatMode)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    saveData()
                    super.onShuffleModeEnabledChanged(shuffleModeEnabled)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_BUFFERING) {
                        saveData()
                    }
                    super.onPlaybackStateChanged(playbackState)
                }*/

                override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                    super.onMediaMetadataChanged(metadata)
                    val current = musicPlaying.value ?: return
                    val newTitle = metadata.title?.toString()
                    val newArtist = metadata.artist?.toString()
                    val newAlbum = metadata.albumTitle?.toString()
                    val newArtwork = metadata.artworkUri
                    val newTrackNumber = metadata.trackNumber?.toString()?.toIntOrNull()
                    val newDiscNumber = metadata.discNumber?.toString()?.toIntOrNull()
                    val newYear = metadata.releaseYear ?: metadata.recordingYear
                    val changed = (newTitle != null && newTitle != current.title) ||
                                  (newArtist != null && newArtist != current.artists) ||
                                  (newAlbum != null && newAlbum != current.album) ||
                                  (newArtwork != null && newArtwork != current.thumb)
                    val needTrackDisc = (newTrackNumber != null && current.trackNumber == null) ||
                                        (newDiscNumber != null && current.discNumber == null)
                    val needYear = newYear != null && current.releaseYear != newYear && current.recordingYear != newYear
                    if (changed || needYear) {
                        // EXOPLAYER 源只填充空字段，不覆盖已有标签
                        applyTags(current, "EXOPLAYER", newTitle, newArtist, newAlbum, newArtwork,
                            newYear, null)
                    }
                    if (needTrackDisc) {
                        // 音轨号/碟号为 null 时从 ExoPlayer 元数据补充
                        applyTags(current, "TAGLIB",
                            title = null, artist = null, album = null, thumb = null,
                            year = null, lyrics = null,
                            trackNumber = if (current.trackNumber == null) newTrackNumber else null,
                            discNumber = if (current.discNumber == null) newDiscNumber else null)
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                        SleepTimerManager.onManualPause()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    // 只在主动暂停时（playWhenReady=false）同步 UI，避免切歌/缓冲/拖进度条误触发
                    // Buffering may finish during fade-out after an explicit pause.
                    // Do not replace that requested pause with a transient playing event.
                    if (!yos.music.player.code.utils.player.shouldPublishPlaybackState(
                            isPlaying, player.playWhenReady, FadeExo.isPausePending
                        )) return
                    MediaViewModelObject.isPlaying.value = isPlaying
                    
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("Flamingo", "播放错误: ${error.errorCodeName}", error)
                    super.onPlayerError(error)
                    val uri = player.currentMediaItem?.localConfiguration?.uri
                    if (uri != null && player.playWhenReady && !FadeExo.isPausePending &&
                        retryableNetworkPlaybackFailure(error.errorCode, uri.scheme, error.cause)) {
                        networkPlaybackRetry?.cancel()
                        networkRetryUri = uri.toString()
                        val retryDelay = networkPlaybackRetryDelayMs(networkRetryAttempt++)
                        MediaViewModelObject.isBuffering.value = true
                        networkPlaybackRetry = serviceScope.launch {
                            delay(retryDelay)
                            networkPlaybackRetry = null
                            // Never resume a paused track or prepare an old song after a switch.
                            if (networkRetryUri == uri.toString() && player.playWhenReady &&
                                !FadeExo.isPausePending &&
                                player.currentMediaItem?.localConfiguration?.uri == uri) {
                                player.prepare()
                            }
                        }
                    } else {
                        cancelNetworkPlaybackRetry()
                        MediaViewModelObject.isBuffering.value = false
                        MediaViewModelObject.isPlaying.value = false
                    }
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    if (!player.playWhenReady || FadeExo.isPausePending ||
                        (networkRetryUri != null && player.currentMediaItem?.localConfiguration?.uri?.toString() != networkRetryUri) ||
                        player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED) {
                        cancelNetworkPlaybackRetry()
                    }
                    MediaViewModelObject.isBuffering.value = playbackIsWaiting(
                        player.playWhenReady, FadeExo.isPausePending,
                        player.playbackState == Player.STATE_BUFFERING, networkRetryUri != null)
                    super.onEvents(player, events)

                    // 定时关闭: 检测歌曲播放完毕(STATE_ENDED)
                    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                        if (player.playbackState == Player.STATE_ENDED) {
                            val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                            SleepTimerManager.checkFinishCurrentSong(currentUri)
                            // 漫游模式：当前曲目自然播完后接续随机下一首。
                            // 定时关闭处于"播完当前歌退出"状态时不接续，否则切歌事件
                            // 会被定时器误判为手动切歌而取消退出。
                            if (yos.music.player.code.MediaController.wanderMode.value &&
                                !SleepTimerManager.isFinishingAfterCurrentSong()
                            ) {
                                yos.music.player.code.MediaController.wanderSkipToNext()
                            }
                        }
                    }

                    // 播放次数统计：播放超过一半时长才算一次有效播放
                    val counting = yos.music.player.code.MediaController.countingUri
                    if (counting != null) {
                        val duration = player.duration
                        val position = player.currentPosition
                        if (duration > 0 && position >= duration / 2) {
                            val uri = player.currentMediaItem?.localConfiguration?.uri
                            if (uri != null && SettingsLibrary.ListenHistory) {
                                MusicLibrary.incrementPlayCount(uri)
                                yos.music.player.data.libraries.ListenStatsLibrary.onValidPlay(uri.toString())
                            }
                            yos.music.player.code.MediaController.countingUri = null
                        }
                    }

                    if (events.containsAny(
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_REPEAT_MODE_CHANGED,
                            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
                        )
                    ) {
                        saveDataWithDelay()
                    }
                }

            }
        )

        // 听歌统计：每秒累计一次真实聆听的墙钟时间。严格 isPlaying 判定：
        // 暂停、缓冲、音频焦点被瞬时抢占（如导航播报）与淡出过程都不计入；
        // 倍速不放大，听一小时记一小时。
        listenDurationJob?.cancel()
        listenDurationJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val uri = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: continue
                if (!player.isPlaying) continue
                if (FadeExo.isPausePending) continue
                if (!SettingsLibrary.ListenHistory) continue
                yos.music.player.data.libraries.ListenStatsLibrary.addListenDuration(
                    uri, 1000L
                )
            }
        }

        /*val repeatButton = CommandButton.Builder()
            .setIconResId(android.R.drawable.ic_media_rew)
            .setSessionCommand(SessionCommand(SAVE_TO_FAVORITES, Bundle()))
            .build()*/

        @Suppress("DEPRECATION")
        class YosMediaSessionCallback : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands =
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(shuffleMode, Bundle.EMPTY))
                        .add(SessionCommand(repeatMode, Bundle.EMPTY))
                        .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == shuffleMode) {
                    toggleShuffle()
                    setCustomButtons(forwardingPlayer)
                } else if (customCommand.customAction == repeatMode) {
                    when (player.repeatMode) {
                        REPEAT_MODE_OFF -> {
                            player.repeatMode = REPEAT_MODE_ALL
                        }

                        REPEAT_MODE_ALL -> {
                            player.repeatMode = REPEAT_MODE_ONE
                        }

                        else -> {
                            player.repeatMode = REPEAT_MODE_OFF
                        }
                    }
                    setCustomButtons(forwardingPlayer)
                }
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_SUCCESS)
                )
            }
            /*override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            player.fadePlay()
                        }

                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player.fadePause()
                        }

                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            player.seekToNextMediaItem()
                        }

                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            player.seekToPreviousMediaItem()
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }*/
        }

        mediaSession =
            MediaSession
                .Builder(this, forwardingPlayer)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .setShowPlayButtonIfPlaybackIsSuppressed(true)
                .setCallback(YosMediaSessionCallback())
                .build()
        /*
                val mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java)
                val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                mediaButtonIntent.component = mediaButtonReceiver
                val pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                mediaSession.setMediaButtonReceiver(pendingIntent)
        */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Flamingo Media Control"
            val descriptionText = "Flamingo Media Control Notification Channel"
            val importance = NotificationManager.IMPORTANCE_NONE
            val channel = NotificationChannel(channelID, name, importance).apply {
                description = descriptionText
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
                setSound(null, null)
            }
            val notificationManager: NotificationManager =
                ContextCompat.getSystemService(
                    this,
                    NotificationManager::class.java
                ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationProvider =
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(notificationID)
                .setChannelId(channelID)
                .build()

        /*DefaultMediaNotificationProvider(
            this,
            {
                notificationID
            },
            channelID,
            notificationID
        )*/

        notificationProvider.setSmallIcon(R.drawable.flamingo_icon_notification)

        this.setMediaNotificationProvider(
            TickerMediaNotificationProvider(notificationProvider) { tickerLyric }
        )

        setCustomButtons(forwardingPlayer)

        statusBarLyricsPublisher = StatusBarLyricsPublisher(
            context = this,
            scope = serviceScope,
            trackKeyProvider = { musicPlaying.value?.uri?.toString() },
            // 服务端直读进程内 ExoPlayer：发布器 10Hz 轮询不再走会话 binder 往返。
            positionProvider = { player.currentPosition },
            playingProvider = { player.isPlaying },
            trackInfoProvider = {
                musicPlaying.value?.let { music ->
                    LyricTrackInfo(
                        title = music.title.orEmpty(),
                        artist = music.artists.orEmpty(),
                        mediaId = music.mediaId ?: music.uri?.toString().orEmpty(),
                        mediaUri = music.uri?.toString().orEmpty(),
                        durationMs = music.duration
                    )
                }
            },
            mediaSessionPublisher = { line, subtitle, lyricInfo ->
                publishMediaSessionLyrics(forwardingPlayer, line, subtitle, lyricInfo)
            },
            tickerPublisher = { line ->
                if (tickerLyric != line) {
                    tickerLyric = line
                    mediaSession?.let { onUpdateNotification(it, false) }
                }
            }
        ).also { it.start() }
    }

    private fun publishMediaSessionLyrics(
        player: Player,
        line: String?,
        subtitle: String?,
        lyricInfo: String?
    ) {
        val index = player.currentMediaItemIndex.takeIf { it >= 0 } ?: return
        val current = player.currentMediaItem ?: return
        val music = musicPlaying.value
        val title = line ?: music?.title ?: current.mediaMetadata.title
        val artist = subtitle ?: music?.artists ?: current.mediaMetadata.artist
        val oldExtras = current.mediaMetadata.extras
        val extras = Bundle(oldExtras ?: Bundle.EMPTY).apply {
            if (SettingsLibrary.ColorOsLockScreenLyricEnabled && !lyricInfo.isNullOrBlank()) {
                putString("lyricInfo", lyricInfo)
            } else {
                remove("lyricInfo")
            }
        }
        if (
            current.mediaMetadata.title == title &&
            current.mediaMetadata.artist == artist &&
            oldExtras?.getString("lyricInfo") == extras.getString("lyricInfo")
        ) return

        val metadata = current.mediaMetadata.buildUpon()
            .setTitle(title)
            .setArtist(artist)
            .setExtras(extras)
            .build()
        player.replaceMediaItem(index, current.buildUpon().setMediaMetadata(metadata).build())
    }

    override fun onDestroy() {
        focusPlayer = null
        cancelNetworkPlaybackRetry()
        listenDurationJob?.cancel()
        yos.music.player.data.libraries.ListenStatsLibrary.flush()
        if (::statusBarLyricsPublisher.isInitialized) statusBarLyricsPublisher.stop()
        localLyricsLoader.cancel()
        yos.music.player.code.MediaController.refreshJob?.cancel()
        instance = null
        MediaViewModelObject.isBuffering.value = false
        yos.music.player.data.remote.RemoteRetryManager.clearPlaybackRetries()
        saveData()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        releaseSharedCache()
        FadeExo.cancelAnimator()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        yos.music.player.data.libraries.ListenStatsLibrary.flush()
        saveData()
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession
}
