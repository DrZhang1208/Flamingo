package yos.music.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.funny.data_saver.core.DataSaverConverter.registerTypeConverters
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yos.music.player.code.MediaController.mediaControl
import yos.music.player.code.MediaController.musicPlaying
import yos.music.player.code.MediaController.playingMusicList
import yos.music.player.code.YosPlaybackService
import yos.music.player.data.libraries.Folder
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.YosStringWrapper
import kotlin.system.exitProcess

class YosBasicApplication : Application() {
    override fun onCreate() {

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            CrashActivity.startActivity(this, e.stackTraceToString())
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }

        // 初始化 MMKV
        MMKV.initialize(this)

        // 初始化远程服务器管理器（加密凭据存储）
        yos.music.player.data.remote.RemoteServerManager.init(this)
        // 远程标签数据库（延迟初始化，避免二次启动崩溃）
        yos.music.player.data.remote.RemoteTagDatabase.init(this)

        val gson =
            GsonBuilder()
            //.registerTypeAdapter(Uri::class.java, UriSerializer())
            //registerTypeAdapter(Uri::class.java, UriDeserializer())
            .registerTypeAdapter(Uri::class.java, UriTypeAdapter())
            .create()

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, Folder::class.java) }
        )

        /*registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, ImmutableList::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, ArrayList::class.java) }
        )*/

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, PlayList::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, IntArray::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, YosMediaItem::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, YosStringWrapper::class.java) }
        )

        // 初始化媒体控制器
        val sessionToken = SessionToken(this, ComponentName(this, YosPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        controllerFuture.addListener(
            {
                mediaControl = controllerFuture.get()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val playListData = MusicLibrary.loadPlayList()
                        val playStatusData = MusicLibrary.loadPlayStatus()

                        val savedMusic = playStatusData.music
                        val savedPlayingMusicList = playListData.playingMusicList

                        // 切到主线程修改 Compose State，避免 snapshot 锁竞争
                        if (savedMusic != null) {
                            mainHandler.post { musicPlaying.value = savedMusic }
                        }

                        if (savedPlayingMusicList != null && savedPlayingMusicList.isNotEmpty()) {
                            if (savedMusic != null) {
                                val savedSourceList = playListData.sourceMusicList
                                yos.music.player.code.MediaController.prepare(
                                    savedMusic, savedPlayingMusicList,
                                    playStatusData.position,
                                    playStatusData.shuffleModeEnabled,
                                    playStatusData.repeatMode,
                                    false,
                                    savedSourceList
                                )
                            }
                            mainHandler.post { playingMusicList.value = savedPlayingMusicList }
                        }

                        // 清理过期标签缓存（超过 30 天未更新的标签）
                        try { yos.music.player.data.remote.RemoteTagDatabase.cleanup(30) } catch (_: Exception) {}

                        // 重建远程文件夹并恢复未完成的扫描
                        try {
                            MusicLibrary.rebuildRemoteFolders()
                            // 加载服务器配置并连接
                            val savedConfigs = MusicLibrary.loadRemoteServers()
                            if (!savedConfigs.isNullOrBlank()) {
                                yos.music.player.data.remote.RemoteServerManager.loadConfigs(savedConfigs)
                            }
                            val pendingSongs = MusicLibrary.songs.filter {
                                it.serverId != null && (it.tagScanStatus == "PENDING" ||
                                    yos.music.player.data.remote.RemoteRetryManager.isRecoverable(it.tagScanStatus))
                            }
                            // 启动时恢复可重试状态
                            pendingSongs.forEach {
                                it.uri?.toString()?.let { uri ->
                                    yos.music.player.data.remote.RemoteRetryManager.restoreFromTagStatus(
                                        uri, it.serverId ?: "", it.uri?.path ?: "", it.tagScanStatus
                                    )
                                }
                            }
                            if (pendingSongs.isNotEmpty()) {
                                val groups = pendingSongs.groupBy { it.serverId!! }
                                for ((serverId, items) in groups) {
                                    // 先连接服务器
                                    try { yos.music.player.data.remote.RemoteServerManager.connect(serverId) } catch (_: Exception) {}
                                    yos.music.player.data.remote.RemoteMetadataScanner.startBackgroundScan(
                                        items, serverId
                                    ) { updated ->
                                        // 仅在提取到有效数据时才存储
                                        if (updated.title != null) {
                                            MusicLibrary.updateSongInFullList(updated)
                                            yos.music.player.data.remote.RemoteTagDatabase.put(
                                                updated.uri?.toString() ?: "", yos.music.player.data.remote.CachedTags(
                                                    uri = updated.uri?.toString() ?: "",
                                                    title = updated.title, artist = updated.artists,
                                                    album = updated.album,
                                                    albumArtist = updated.albumArtists,
                                                    genre = updated.genre,
                                                    year = updated.releaseYear ?: updated.recordingYear,
                                                    duration = if (updated.duration > 0) updated.duration else null,
                                                    trackNumber = updated.trackNumber,
                                                    discNumber = updated.discNumber,
                                                    composer = updated.composer,
                                                    bitrate = updated.bitrate,
                                                    sampleRate = updated.sampleRate,
                                                    channels = updated.channels,
                                                    fileSize = updated.fileSize,
                                                    coverPath = updated.thumb?.toString()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            { command -> mainHandler.post { command.run() } }  // 修复: 使用主线程 executor
        )

        // 立即在后台线程预加载 MMKV 数据，避免主线程首次访问时触发磁盘 IO + Gson 反序列化
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT)
            try {
                yos.music.player.data.libraries.SettingsLibrary.preload()
                yos.music.player.data.libraries.MusicLibrary.preload()
            } catch (_: Exception) {}
        }.start()

        super.onCreate()
    }
}


/*
class ImmutableListTypeAdapter<T> : JsonSerializer<ImmutableList<T>>,
    JsonDeserializer<ImmutableList<T>> {
    override fun serialize(src: ImmutableList<T>?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return context?.serialize(src?.toList()) ?: JsonNull.INSTANCE
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ImmutableList<T> {
        val listType = object : TypeToken<List<T>>() {}.type
        val list = context?.deserialize<List<T>>(json, listType)
        return ImmutableList.copyOf(list)
    }
}*/

class UriTypeAdapter : TypeAdapter<Uri>() {
    override fun write(out: JsonWriter, value: Uri?) {
        out.value(value.toString())
    }

    override fun read(`in`: JsonReader): Uri {
        return Uri.parse(`in`.nextString())
    }
}

/*
class UriSerializer : JsonSerializer<Uri> {
    override fun serialize(src: Uri?, typeOfSrc: Type?, context: com.google.gson.JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src.toString())
    }
}

class UriDeserializer : JsonDeserializer<Uri> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: com.google.gson.JsonDeserializationContext?): Uri {
        return Uri.parse(json?.asString)
    }
}*/
