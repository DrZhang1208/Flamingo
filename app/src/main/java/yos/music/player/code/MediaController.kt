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
import yos.music.player.code.MediaController.metadataRefreshTrigger
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

    /** UI 刷新触发器，每次 ExoPlayer 提取到新元数据时自增 */
    @Volatile var metadataRefreshTrigger = 0

    /** 应用层随机播放标志。ExoPlayer 始终工作在顺序模式。 */
    @Stable
    var shuffleEnabled = mutableStateOf(false)

    /** 原始播放列表（未打乱），用于关闭随机时恢复自然顺序 */
    private var sourceMusicList: List<YosMediaItem>? = null

    fun onServiceRunning() {
        val handler by lazy { Handler(Looper.getMainLooper()) }
        val lyricAPI by lazy { API() }
        var lastLyric = listOf<Pair<Float, String>>()
        val base64 = Tools.drawableToBase64(getDrawable(R.drawable.flamingo_icon_notification)!!)
        var statusBarLyricEnabled: Boolean
        var hooked = false

        val checkHookStatusRunnable = object : Runnable {
            override fun run() {
                hooked = lyricAPI.hasEnable
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
                                            lyricAPI.sendLyric(
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
        play: Boolean = true
    ) {
        println("prepare $music")
        val effectiveShuffle = shuffleModeEnabled || shuffleEnabled.value
        shuffleEnabled.value = effectiveShuffle

        if (thisMusicList != playingMusicList.value) {

            // 保存原始列表，用于关闭随机时恢复
            sourceMusicList = thisMusicList

            // 应用层随机：打乱列表，保持当前曲目在首位
            val playbackList = if (effectiveShuffle) {
                val others = thisMusicList.filter { it.uri != music.uri }.shuffled()
                listOf(music) + others
            } else {
                thisMusicList
            }

            val startIndex = if (effectiveShuffle) {
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

            println("prepare 调用切列表")
            if (!play && playingMusicList.value == null) {
                musicPlaying.value = music
                refresh(music)
                withContext(Dispatchers.Main) {
                    mediaControl?.repeatMode = repeatMode
                    mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
                }
            }

            if (play) {
                withContext(Dispatchers.Main) {
                    mediaControl?.fadePlay()
                }
            }

            // Immediately persist state on every prepare
            syncState()

        } else {
            println("prepare 调用非切列表")
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
        MusicLibrary.updatePlayList(PlayListV1(mainMusicList, playlist))
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
        MusicLibrary.incrementPlayCount(mediaItem.uri)
        // 远程文件：ExoPlayer 提取标签后更新数据库
        if (mediaItem.serverId != null && mediaItem.title != null) {
            val uri = mediaItem.uri?.toString() ?: ""
            val cached = yos.music.player.data.remote.RemoteTagDatabase.get(uri)
            if (cached?.title != mediaItem.title || cached?.artist != mediaItem.artists) {
                yos.music.player.data.remote.RemoteTagDatabase.put(uri, yos.music.player.data.remote.CachedTags(
                    uri = uri,
                    title = mediaItem.title,
                    artist = mediaItem.artists,
                    album = mediaItem.album,
                    year = mediaItem.releaseYear ?: mediaItem.recordingYear,
                    duration = if (mediaItem.duration > 0) mediaItem.duration else null
                ))
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            refresh(mediaItem)
        }
    }

    private var refreshJob: CompletableJob? = null

    private fun refresh(music: YosMediaItem) {
        refreshJob?.cancel()
        refreshJob = Job()

        val scope = CoroutineScope(Dispatchers.IO + refreshJob!!)

        scope.launch {
            println("prepare 刷新UI状态 $music")
            musicPlaying.value = music
            println(musicPlaying.value)
        }

        scope.launch {
            // val bitmap: MutableState<String?> = MediaViewModelObject.bitmap
            // bitmap.value = music.thumb
            MediaViewModelObject.bitmap.value = music.thumb
        }

        scope.launch {
            MainViewModelObject.syncLyricIndex.intValue = -1
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
        println("持久化 尝试保存播放状态")
        val music = musicPlaying.value ?: return
        val playlist = playingMusicList.value ?: listOf(music)
        val control = mediaControl
        val pos = runCatching { control?.currentPosition ?: 0 }.getOrDefault(0)
        val shuffle = shuffleEnabled.value
        val repeat = runCatching { control?.repeatMode ?: REPEAT_MODE_ALL }.getOrDefault(REPEAT_MODE_ALL)
        println("持久化 保存播放状态 playlist=${playlist.size}")
        MusicLibrary.updatePlayStatus(PlayStatus(music, pos, shuffle, repeat))
        MusicLibrary.updatePlayList(PlayListV1(yos.music.player.code.MediaController.mainMusicList, playlist))
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        yos.music.player.code.MediaController.appContext = this
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        // 音频缓存（可配置大小 LRU）
        val cacheDir = File(cacheDir, "audio_cache")
        val cacheSizeBytes = SettingsLibrary.RemoteCacheSizeMB.toLong() * 1024L * 1024L
        val cache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(cacheSizeBytes), androidx.media3.database.StandaloneDatabaseProvider(this))

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
                    CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(RemoteDataSourceFactory(this))
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
                return FadeExo.targetStatus != 0
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

                        println("质量分析 内置实现获取")
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

                        // 1. 尝试从文件提取内嵌歌词
                        var lrcContent: String? = null
                        if (thisPath != null) {
                            lrcContent = AudioMetadataUtils.extractEmbeddedLyrics(thisPath)
                        }

                        // 2. 回退到外部 LRC 文件读取
                        val finalLrcContent = if (!lrcContent.isNullOrBlank()) {
                            lrcContent
                        } else {
                            val lrcPath = "${thisPath?.substringBeforeLast(".")}.lrc"
                            println("无内嵌歌词，读取外部文件：$lrcPath")
                            AudioMetadataUtils.loadLrcFile(this@YosPlaybackService, lrcPath) ?: ""
                        }

                        val lrcFactory = YosLrcFactory()
                        var parsedEntries = lrcFactory.formatLrcEntries(finalLrcContent)
                        println("歌词解析 完成，共 ${parsedEntries.size} 行")

                        if (parsedEntries.isNotEmpty()) lrcEntries.value = parsedEntries

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

                        MediaViewModelObject.isDolby.value = haveJOC
                        MediaViewModelObject.samplingRate.intValue = samplingRate
                        MediaViewModelObject.bitrate.intValue = bitrate

                        println("质量分析 采样率：${MediaViewModelObject.samplingRate.intValue}，比特率：${MediaViewModelObject.bitrate.intValue}")
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.let {
                        val yosItem = it.toYosMediaItem()
                        // 优先从数据库加载缓存标签
                        if (yosItem.serverId != null) {
                            val cached = yos.music.player.data.remote.RemoteTagDatabase.get(yosItem.uri?.toString() ?: "")
                            if (cached != null) {
                                val withCached = yosItem.copy(
                                    title = cached.title ?: yosItem.title,
                                    artists = cached.artist ?: yosItem.artists,
                                    album = cached.album ?: yosItem.album,
                                    thumb = cached.coverPath?.let { android.net.Uri.parse(it) } ?: yosItem.thumb
                                )
                                musicPlaying.value = withCached
                                if (!cached.lyrics.isNullOrBlank()) {
                                    val lrcFactory = yos.music.player.code.utils.lrc.YosLrcFactory()
                                    val entries = lrcFactory.formatLrcEntries(cached.lyrics)
                                    if (entries.isNotEmpty()) {
                                        MediaViewModelObject.lrcEntries.value = entries
                                    } else {
                                        val lines = cached.lyrics.lines().filter { it.isNotBlank() }
                                        if (lines.isNotEmpty()) {
                                            MediaViewModelObject.lrcEntries.value = listOf(lines.map { 0f to it })
                                        }
                                    }
                                }
                                yos.music.player.code.MediaController.onCase(withCached)
                                return@let
                            }
                        }
                        yos.music.player.code.MediaController.onCase(yosItem)
                    }
                    super.onMediaItemTransition(mediaItem, reason)
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
                    // 诊断：复制到剪贴板
                    val diagMsg = "onMeta: changed=$changed curTitle=${current.title} curArtist=${current.artists} curThumb=${current.thumb} newTitle=$newTitle newArtist=$newArtist newAlbum=$newAlbum newArtwork=$newArtwork serverId=${current.serverId}"
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        yos.music.player.code.MediaController.appContext?.let { ctx ->
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("meta", diagMsg))
                            android.widget.Toast.makeText(ctx, "onMeta 已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (changed) {
                        val updated = current.copy(
                            title = newTitle ?: current.title,
                            artists = newArtist ?: current.artists,
                            album = newAlbum ?: current.album,
                            thumb = newArtwork ?: current.thumb,
                            releaseYear = metadata.releaseYear ?: current.releaseYear,
                            recordingYear = metadata.recordingYear ?: current.recordingYear,
                            trackNumber = metadata.trackNumber ?: current.trackNumber,
                            genre = metadata.genre?.toString() ?: current.genre
                        )
                        musicPlaying.value = updated
                        metadataRefreshTrigger++
                        if (current.serverId != null) {
                            val uri = current.uri?.toString() ?: ""
                            yos.music.player.data.remote.RemoteTagDatabase.put(uri, yos.music.player.data.remote.CachedTags(
                                uri = uri, title = updated.title, artist = updated.artists,
                                album = updated.album, coverPath = updated.thumb?.toString()
                            ))
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    MediaViewModelObject.isPlaying.value = isPlaying
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("FlamingoDS", "ExoPlayer error: ${error.message} code=${error.errorCode}")
                    super.onPlayerError(error)
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    super.onEvents(player, events)

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
        saveData()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
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

