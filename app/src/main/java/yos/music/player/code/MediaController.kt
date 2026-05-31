package yos.music.player.code

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import cn.lyric.getter.api.API
import cn.lyric.getter.api.data.ExtraData
import cn.lyric.getter.api.tools.Tools
import com.blankj.utilcode.util.ResourceUtils.getDrawable
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.MainActivity
import yos.music.player.R
import yos.music.player.code.MediaController.mediaControl
import yos.music.player.code.MediaController.mediaSession
import yos.music.player.code.MediaController.uiRefreshTrigger
import yos.music.player.code.MediaController.musicPlaying
import yos.music.player.code.MediaController.onServiceRunning
import yos.music.player.code.MediaController.playingMusicList
import yos.music.player.code.MediaController.shuffleEnabled
import yos.music.player.code.MediaController.toggleShuffle
import yos.music.player.code.utils.lrc.YosLrcFactory
import yos.music.player.code.utils.player.FadeExo
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.code.utils.player.FadeExo.fadePlay
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.MusicLibrary.toMediaItem
import yos.music.player.data.libraries.MusicLibrary.toYosMediaItem
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

    /** 原始播放列表（未打乱），用于关闭随机时恢复自然顺序 */
    var sourceMusicList: List<YosMediaItem>? = null

    fun onServiceRunning() {
        val handler by lazy { Handler(Looper.getMainLooper()) }
        val lyricAPI by lazy { runCatching { API() }.getOrNull() }
        var lastLyric = listOf<Pair<Float, String>>()
        val base64 = runCatching { Tools.drawableToBase64(getDrawable(R.drawable.flamingo_icon_notification)!!) }.getOrElse { "" }
        var statusBarLyricEnabled: Boolean
        var hooked = false

        val checkHookStatusRunnable = object : Runnable {
            override fun run() {
                hooked = lyricAPI?.hasEnable ?: false
                SettingsLibrary.StatusBarLyricHooked = hooked
                handler.postDelayed(this, 350)
            }
        }

        val updateLyricsRunnable = object : Runnable {
            override fun run() {
                runCatching {
                    var currentLyricIndex: Int
                    var isPlaying: Boolean?
                    var liveTime: Long

                    handler.post {
                        isPlaying = mediaControl?.isPlaying

                        runCatching {
                            currentLyricIndex = MainViewModelObject.syncLyricIndex.intValue

                            if (isPlaying == true) {
                                liveTime = mediaControl?.currentPosition ?: 0

                                val lrcEntries = MediaViewModelObject.lrcEntries.value

                                val nextIndex = lrcEntries.indexOfFirst { line ->
                                    line.first().first >= liveTime
                                }

                                val sendLyric = fun() {
                                    try {
                                        MainViewModelObject.syncLyricIndex.intValue =
                                            currentLyricIndex
                                        statusBarLyricEnabled =
                                            SettingsLibrary.StatusBarLyricEnabled


                                        val line = lrcEntries[currentLyricIndex]
                                        if (line == lastLyric) {
                                            return
                                        }

                                        val lyric = StringBuffer("")
                                        line.forEachIndexed { charIndex, char ->
                                            if (charIndex >= line.size - 1) return@forEachIndexed
                                            lyric.append(char.second)
                                        }

                                        val lyricResult = lyric.toString()

                                        if (statusBarLyricEnabled && hooked) {
                                            lyricAPI?.sendLyric(
                                                lyricResult,
                                                extra = ExtraData().apply {
                                                    customIcon = true
                                                    base64Icon = base64
                                                }
                                            )
                                        }

                                        // YosPlaybackService().sendLyricTicker(lyricResult)

                                        lastLyric = line
                                    } catch (_: Exception) {
                                    }
                                }

                                if (nextIndex != -1) {
                                    if (nextIndex - 1 != currentLyricIndex) {
                                        currentLyricIndex = nextIndex - 1
                                    }
                                    if (currentLyricIndex != -1) {
                                        sendLyric()
                                    }
                                } else if (currentLyricIndex != lrcEntries.size - 1) {
                                    currentLyricIndex = lrcEntries.size - 1
                                    if (currentLyricIndex != -1) {
                                        sendLyric()
                                    }
                                }
                            }
                        }
                    }

                    handler.postDelayed(this, 70)
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            handler.post(checkHookStatusRunnable)
            handler.post(updateLyricsRunnable)
        }
    }



    suspend fun prepare(
        music: YosMediaItem,
        thisMusicList: List<YosMediaItem>,
        position: Long = 0L,
        shuffleModeEnabled: Boolean = false,
        repeatMode: Int = REPEAT_MODE_ALL,
        play: Boolean = true,
        sourceList: List<YosMediaItem>? = null
    ) {
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
                musicPlaying.value = music
                refresh(music)
            }

            withContext(Dispatchers.Main) {
                mediaControl?.repeatMode = repeatMode
                mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
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

    suspend fun syncState() {
        val music = musicPlaying.value ?: return
        val control = mediaControl ?: return
        val playlist = playingMusicList.value ?: listOf(music)
        val pos = withContext(Dispatchers.Main) { control.currentPosition }
        val shuffle = shuffleEnabled.value
        val repeat = withContext(Dispatchers.Main) { control.repeatMode }
        MusicLibrary.updatePlayStatus(PlayStatus(music, pos, shuffle, repeat))
        MusicLibrary.updatePlayList(PlayListV1(mainMusicList, playlist, sourceMusicList))
    }

    fun toggleShuffle() {
        val enabled = !shuffleEnabled.value
        shuffleEnabled.value = enabled

        val current = musicPlaying.value ?: return
        val currentList = playingMusicList.value ?: return
        val srcList = sourceMusicList

        CoroutineScope(Dispatchers.IO).launch {
            // 不触碰当前曲目（位置0），只重建其后的列表，避免播放中断
            val mc = mediaControl ?: return@launch
            val newTail: List<YosMediaItem> = if (enabled) {
                currentList.filter { it.uri != current.uri }.shuffled()
            } else {
                val src = srcList ?: currentList
                val currentIdxInSrc = src.indexOfFirst { it.uri == current.uri }.coerceAtLeast(0)
                // 保持自然顺序连续性：先排当前曲目后面的，再排前面的
                src.drop(currentIdxInSrc + 1) + src.take(currentIdxInSrc)
            }
            val newList = listOf(current) + newTail

            withContext(Dispatchers.Main) {
                // 先把当前曲目移到位置 0（切歌后它可能不在 0 了）
                val currentIdx = mc.currentMediaItemIndex
                if (currentIdx > 0) {
                    mc.moveMediaItem(currentIdx, 0)
                }
                // 移除当前曲目之后的所有项
                val size = mc.mediaItemCount
                if (size > 1) {
                    mc.removeMediaItems(1, size)
                }
                // 追加新顺序的尾部
                if (newTail.isNotEmpty()) {
                    mc.addMediaItems(1, newTail.map { it.toMediaItem() })
                }
            }
            playingMusicList.value = newList
            syncState()
            withContext(Dispatchers.Main) {
                mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
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
            year = result.year ?: cur.releaseYear ?: cur.recordingYear,
            duration = if ((result.duration ?: 0) > 0) result.duration else null,
            coverPath = result.coverUri?.toString(),
            lyrics = result.lyrics
        ))
        val newDuration = result.duration
        val u = cur.copy(
            title = result.title ?: cur.title,
            artists = result.artist ?: cur.artists,
            album = result.album ?: cur.album,
            thumb = result.coverUri ?: cur.thumb,
            releaseYear = result.year ?: cur.releaseYear,
            recordingYear = result.year ?: cur.recordingYear,
            tagScanStatus = "COMPLETE",
            duration = if (newDuration != null && newDuration > 0 && newDuration != cur.duration) newDuration else cur.duration
        )
        musicPlaying.value = u
        refreshJob?.cancel()
        if (u.thumb != null && u.thumb != cur.thumb) {
            MediaViewModelObject.bitmap.value = u.thumb
        }
        if (u.thumb != cur.thumb || u.title != cur.title || u.artists != cur.artists || u.album != cur.album) {
            val idx = mediaControl?.currentMediaItemIndex
            if (idx != null && idx >= 0) {
                mediaControl?.replaceMediaItem(idx, u.toMediaItem())
            }
        }
        MusicLibrary.updateSongInFullList(u)
        uiRefreshTrigger++
        val lyrics = result.lyrics
        if (!lyrics.isNullOrBlank()) {
            val lrcF = yos.music.player.code.utils.lrc.YosLrcFactory()
            val e = lrcF.formatLrcEntries(lyrics)
            if (e.isNotEmpty()) {
                MediaViewModelObject.lrcEntries.value = e
                yos.music.player.data.objects.MediaViewModelObject.cacheLrc(uri, e)
            } else {
                val l = lyrics.lines().filter { it.isNotBlank() }
                if (l.isNotEmpty()) {
                    MediaViewModelObject.lrcEntries.value = listOf(l.map { 0f to it })
                }
            }
        }
    }

    private fun refresh(music: YosMediaItem) {
        refreshJob?.cancel()
        refreshJob = Job()

        val scope = CoroutineScope(Dispatchers.IO + refreshJob!!)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        scope.launch {
            handler.post { musicPlaying.value = music }
        }

        scope.launch {
            handler.post { MediaViewModelObject.bitmap.value = music.thumb }
        }

        // 切歌时优先从缓存加载歌词，缓存未命中才清空
        handler.post {
            val cached = yos.music.player.data.objects.MediaViewModelObject.getCachedLrc(music.uri?.toString())
            MediaViewModelObject.lrcEntries.value = cached ?: listOf()
            yos.music.player.data.objects.MainViewModelObject.syncLyricIndex.intValue = -1
        }
    }
}

class YosPlaybackService : MediaSessionService() {
    private val notificationID = 1145
    private val channelID = "YosMediaControllerChannel"

    private val shuffleMode = "shuffle_mode"
    private val repeatMode = "repeat_mode"

    companion object {
        private const val FLAG_ALWAYS_SHOW_TICKER = 0x1000000
        private const val FLAG_ONLY_UPDATE_TICKER = 0x2000000

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

    /*fun sendLyricTicker(lyric: String) {
        val notification = NotificationCompat.Builder(this, channelID).apply {
            setTicker(lyric)
            setSmallIcon(R.drawable.flamingo_icon_notification)
        }.build().also {
            it.extras.putInt("ticker_icon", R.drawable.flamingo_icon_notification)
            it.extras.putBoolean("ticker_icon_switch", true)
            it.flags = it.flags.or(FLAG_ALWAYS_SHOW_TICKER).or(FLAG_ONLY_UPDATE_TICKER)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(this).notify(notificationID, notification)
    }*/

    private var saveJob: Job? = null

    fun saveDataNow() {
        saveJob?.cancel()
        saveJob = CoroutineScope(Dispatchers.Main).launch {
            saveData()
        }
    }

    fun saveDataWithDelay() {
        saveJob?.cancel()
        saveJob = CoroutineScope(Dispatchers.IO).launch {
            delay(200)
            withContext(Dispatchers.Main) {
                saveData()
            }
        }
    }

    private fun saveData() {
        val music = musicPlaying.value ?: return
        val playlist = playingMusicList.value ?: listOf(music)
        val control = mediaControl
        val pos = runCatching { control?.currentPosition ?: 0 }.getOrDefault(0)
        val shuffle = shuffleEnabled.value
        val repeat = runCatching { control?.repeatMode ?: REPEAT_MODE_ALL }.getOrDefault(REPEAT_MODE_ALL)
        MusicLibrary.updatePlayStatus(PlayStatus(music, pos, shuffle, repeat))
        MusicLibrary.updatePlayList(PlayListV1(yos.music.player.code.MediaController.mainMusicList, playlist, yos.music.player.code.MediaController.sourceMusicList))
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        yos.music.player.code.MediaController.appContext = this
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val cacheSizeBytes = SettingsLibrary.RemoteCacheSizeMB.toLong() * 1024L * 1024L
        val cache = getOrCreateCache(this, cacheSizeBytes)
        val upstreamFactory = RemoteDataSourceFactory(this)

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
                        CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstreamFactory)
                    } else {
                        upstreamFactory
                    }
                )
            )
            .setAudioAttributes(
                audioAttributes,
                SettingsLibrary.AudioAttributes
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

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
        }

        forwardingPlayer.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    runCatching {

                        if (tracks.isEmpty) return@runCatching

                        val lrcEntries: MutableState<List<List<Pair<Float, String>>>> =
                            MediaViewModelObject.lrcEntries

                        val path = player.currentMediaItem?.uri

                        var samplingRate = 0
                        var bitrate = 0
                        var haveJOC = false

                        for (i in tracks.groups) {
                            for (j in 0 until i.length) {
                                if (!i.isTrackSelected(j)) continue
                                val trackFormat = i.getTrackFormat(j)
                                samplingRate = trackFormat.sampleRate
                                bitrate = trackFormat.bitrate / 1000
                                haveJOC =
                                    trackFormat.sampleMimeType?.contains("-joc", ignoreCase = true)
                                        ?: false
                                break
                            }
                        }

                        val thisPath = path?.path
                        val rawUri = path?.toString() ?: ""
                        val isRemote = rawUri.startsWith("smb://") || rawUri.startsWith("webdav://")

                        // 远程文件的歌词由 RemoteTagExtractor 异步提取，不从本地路径读取
                        var lrcContent: String? = null
                        if (!isRemote && thisPath != null) {
                            lrcContent = AudioMetadataUtils.extractEmbeddedLyrics(thisPath)
                        }

                        // 2. 回退到外部 LRC 文件读取（同样仅本地文件）
                        val finalLrcContent = if (!lrcContent.isNullOrBlank()) {
                            lrcContent
                        } else if (!isRemote && thisPath != null) {
                            val lrcPath = "${thisPath.substringBeforeLast(".")}.lrc"
                            AudioMetadataUtils.loadLrcFile(this@YosPlaybackService, lrcPath) ?: ""
                        } else {
                            ""
                        }

                        // 仅当从本地文件成功提取到歌词时才设置，远程文件等 RemoteTagExtractor 结果
                        if (finalLrcContent.isNotBlank()) {
                            val lrcFactory = YosLrcFactory()
                            val parsedEntries = lrcFactory.formatLrcEntries(finalLrcContent)
                            if (parsedEntries.isNotEmpty()) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    lrcEntries.value = parsedEntries
                                    yos.music.player.data.objects.MediaViewModelObject.cacheLrc(rawUri, parsedEntries)
                                }
                            }
                        }

                        if (thisPath != null) {
                            if (samplingRate == 0 || bitrate == 0) {
                                val audioInfo = AudioMetadataUtils.getQualityInfos(thisPath)
                                if (samplingRate == 0) {
                                    samplingRate = audioInfo.second
                                } else {
                                    bitrate = audioInfo.first
                                }
                            }
                        }

                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            MediaViewModelObject.isDolby.value = haveJOC
                            MediaViewModelObject.samplingRate.intValue = samplingRate
                            MediaViewModelObject.bitrate.intValue = bitrate
                        }

                        // 用 ExoPlayer 实际时长修正存储的 duration
                        val playerDuration = player.duration
                        if (playerDuration > 0L && !isRemote) {
                            val cur = musicPlaying.value
                            if (cur != null && cur.serverId == null && cur.duration != playerDuration) {
                                val corrected = cur.copy(duration = playerDuration)
                                musicPlaying.value = corrected
                                MusicLibrary.updateSongInFullList(corrected)
                            }
                        }
                    }
                }

                /** TAGLIB 来源可覆盖所有字段；其他来源永不覆盖已有数据 */
                private fun applyTags(item: yos.music.player.data.libraries.YosMediaItem, source: String, title: String?, artist: String?, album: String?, thumb: android.net.Uri?, year: Int?, lyrics: String?, duration: Long? = null) {
                    val cur = musicPlaying.value ?: return
                    val overwrite = source != "EXOPLAYER"
                    val u = cur.copy(
                        title = if (overwrite && title != null) title else cur.title,
                        artists = if (overwrite && artist != null) artist else cur.artists,
                        album = if (overwrite && album != null) album else cur.album,
                        thumb = if (overwrite && thumb != null) thumb else cur.thumb,
                        releaseYear = year ?: cur.releaseYear, recordingYear = year ?: cur.recordingYear,
                        tagScanStatus = if (overwrite) "COMPLETE" else cur.tagScanStatus,
                        duration = if (overwrite && duration != null && duration > 0 && duration != cur.duration) duration else cur.duration
                    )
                    musicPlaying.value = u
                    // EXOPLAYER 源不覆盖 thumb，跳过 bitmap 更新避免 handler.post 时序覆盖问题
                    if (u.thumb != null && source != "EXOPLAYER") {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            MediaViewModelObject.bitmap.value = u.thumb
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
                    if (!lyrics.isNullOrBlank()) {
                        val lrcF = yos.music.player.code.utils.lrc.YosLrcFactory()
                        val e = lrcF.formatLrcEntries(lyrics)
                        if (e.isNotEmpty()) {
                            MediaViewModelObject.lrcEntries.value = e
                            yos.music.player.data.objects.MediaViewModelObject.cacheLrc(item.uri?.toString(), e)
                        }
                        else { val l = lyrics.lines().filter { it.isNotBlank() }; if (l.isNotEmpty()) {
                            MediaViewModelObject.lrcEntries.value = listOf(l.map { 0f to it })
                            yos.music.player.data.objects.MediaViewModelObject.cacheLrc(item.uri?.toString(), e)
                        } }
                    }
                    if (item.serverId != null) yos.music.player.data.remote.RemoteTagDatabase.put(item.uri?.toString() ?: "", yos.music.player.data.remote.CachedTags(
                        uri = item.uri?.toString() ?: "", title = u.title, artist = u.artists, album = u.album, coverPath = u.thumb?.toString(), lyrics = lyrics))
                    yos.music.player.data.objects.LibraryObject.updateSongInTargetList(u)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    yos.music.player.data.remote.RemoteRetryManager.onTrackChanged(
                        mediaItem?.localConfiguration?.uri?.toString()
                    )
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
                                val memCached = yos.music.player.data.objects.MediaViewModelObject.getCachedLrc(yosItem.uri?.toString())
                                MediaViewModelObject.lrcEntries.value = memCached ?: listOf()
                                yos.music.player.data.objects.MainViewModelObject.syncLyricIndex.intValue = -1
                                musicPlaying.value = yosItem
                                if (hasTags) {
                                    applyTags(yosItem, "DB_CACHE", cached!!.title, cached.artist, cached.album, coverUri, cached.year, cached.lyrics, cached.duration)
                                }
                            }
                            // 始终后台提取最新标签（远程文件标签可能已更新），缓存先展示
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { extractAndApplyTags(yosItem) }
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
                        yos.music.player.data.remote.RemoteTagDatabase.put(uri, yos.music.player.data.remote.CachedTags(
                            uri = uri,
                            title = result.title ?: item.title,
                            artist = result.artist ?: item.artists,
                            album = result.album ?: item.album,
                            year = result.year ?: item.releaseYear ?: item.recordingYear,
                            duration = if ((result.duration ?: 0) > 0) result.duration else null,
                            coverPath = result.coverUri?.toString(),
                            lyrics = result.lyrics
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
                                result.duration)
                            musicPlaying.value?.let { MusicLibrary.updateSongInFullList(it) }
                            uiRefreshTrigger++
                        }
                    } catch (_: Exception) {
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
                    val changed = (newTitle != null && newTitle != current.title) ||
                                  (newArtist != null && newArtist != current.artists) ||
                                  (newAlbum != null && newAlbum != current.album) ||
                                  (newArtwork != null && newArtwork != current.thumb)
                    if (changed) {
                        // EXOPLAYER 源只填充空字段，不覆盖已有标签
                        applyTags(current, "EXOPLAYER", newTitle, newArtist, newAlbum, newArtwork,
                            metadata.releaseYear ?: metadata.recordingYear, null)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    // 只在主动暂停时（playWhenReady=false）同步 UI，避免切歌/缓冲/拖进度条误触发
                    if (!isPlaying && player.playWhenReady) return
                    MediaViewModelObject.isPlaying.value = isPlaying
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("Flamingo", "播放错误: ${error.errorCodeName}", error)
                    super.onPlayerError(error)
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    super.onEvents(player, events)

                    // 播放次数统计：播放超过一半时长才算一次有效播放
                    val counting = yos.music.player.code.MediaController.countingUri
                    if (counting != null) {
                        val duration = player.duration
                        val position = player.currentPosition
                        if (duration > 0 && position >= duration / 2) {
                            val uri = player.currentMediaItem?.localConfiguration?.uri
                            if (uri != null && SettingsLibrary.ListenHistory) MusicLibrary.incrementPlayCount(uri)
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

        this.setMediaNotificationProvider(notificationProvider)

        setCustomButtons(forwardingPlayer)

        onServiceRunning()
    }

    override fun onDestroy() {
        yos.music.player.data.remote.RemoteRetryManager.clearPlaybackRetries()
        saveData()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        releaseSharedCache()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveData()
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession
}
