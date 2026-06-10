@file:Suppress("SameParameterValue")

package yos.music.player.data.libraries

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastMap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.funny.data_saver.core.mutableDataSaverListStateOf
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import uk.akane.libphonograph.constructor.ItemConstructor
import uk.akane.libphonograph.reader.Reader
import uk.akane.libphonograph.reader.ReaderConfiguration
import uk.akane.libphonograph.reader.ReaderResult
import yos.music.player.UriTypeAdapter
import yos.music.player.code.MediaController
import yos.music.player.data.NormalSaver
import yos.music.player.data.SongListSaver

/*@Parcelize
@Stable
data class Music(
    val title: String,
    val artist: List<String>,
    val album: String,
    val path: String,
    val date: Long,
    val id: Long,
    var thumb: String?,
    val duration: Long = 0,
    val bitrate: Int,
    val samplingRate: Int
) : Parcelable {
    fun artistsToString(): String {
        return artist.joinToString("、")
    }
}*/

@Stable
data class Time(
    val min: String,
    val sec: String
)
@Parcelize
@Stable
data class PlayListV1(
    val mainMusicList: List<String>?,
    val playingMusicList: List<YosMediaItem>?,
    val sourceMusicList: List<YosMediaItem>? = null  // 打乱前的原始顺序
) : Parcelable

@Parcelize
@Stable
data class PlayStatus(
    val music: YosMediaItem?,
    val position: Long,
    val shuffleModeEnabled: Boolean,
    val repeatMode: Int
) : Parcelable

@Parcelize
@Stable
data class Folder(val name: String, val path: String, val songs: List<YosMediaItem>, val source: String? = null, val serverId: String? = null) : Parcelable

@Parcelize
@Stable
data class YosMediaItem(
    val uri: Uri?,
    val mediaId: String?,
    val mimeType: String?,
    val title: String?,
    val writer: String?,
    val compilation: String?,
    val composer: String?,
    val artists: String?,
    val album: String?,
    val albumArtists: String?,
    val thumb: Uri?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val genre: String?,
    val recordingDay: Int?,
    val recordingMonth: Int?,
    val recordingYear: Int?,
    val releaseYear: Int?,
    val artistId: Long?,
    val albumId: Long?,
    val genreId: Long?,
    val author: String?,
    val addDate: Long?,
    val duration: Long,
    val modifiedDate: Long?,
    val cdTrackNumber: Int?,
    val serverId: String? = null,
    val tagScanStatus: String? = null,  // "PENDING"/"SCANNING"/"COMPLETE"/"FAILED"，null 等同于 COMPLETE
    val bitrate: Int? = null,   // kbps，远程扫描填充
    val sampleRate: Int? = null, // Hz，远程扫描填充
    val channels: Int? = null,   // 声道数，远程扫描填充
    val fileSize: Long? = null   // bytes，远程文件列表填充
) : Parcelable {

    val effectiveTagScanStatus: String get() = tagScanStatus ?: "COMPLETE"
}

@Stable
@Parcelize
data class YosStringWrapper(val value: String) : Parcelable

@Stable
object MusicLibrary {
    // yos_player_core 负责歌曲列表 V1、播放状态记录

    private const val mmkvID = "yos_player_core"
    private const val playListKey = "yos_play_list_v1"
    private const val playStatusKey = "yos_player_play_status"
    private const val playCountKey = "yos_play_count"
    private const val remoteServersKey = "yos_remote_servers"
    private const val remoteFoldersMetaKey = "yos_remote_folders_meta"

    var hideSongs by mutableDataSaverListStateOf(
        dataSaverInterface = SongListSaver,
        key = "hide_songs",
        initialValue = listOf<YosMediaItem>()
    )
        private set

    var folders by mutableDataSaverListStateOf(
        dataSaverInterface = SongListSaver,
        key = "folders",
        initialValue = listOf<Folder>()
    )
        private set

    private var hideFoldersSaver by mutableDataSaverListStateOf(
        dataSaverInterface = NormalSaver, key = "hide_folders", initialValue = listOf<YosStringWrapper>()
    )

    val hideFolders: List<String>
        get() = hideFoldersSaver.map { it.value }

    private var songSaver by mutableDataSaverListStateOf(
        dataSaverInterface = SongListSaver, key = "songs", initialValue = listOf<YosMediaItem>()
    )

    // 缓存过滤结果，使用版本号避免 O(n) hashCode 计算
    @Volatile private var songsCacheVersion = -1L
    @Volatile private var songsCache: List<YosMediaItem> = emptyList()
    @Volatile private var folderListVersionForCache = -1

    /** 使 songs 缓存失效，在数据变更时调用 */
    private fun invalidateSongsCache() { songsCacheVersion = -1L }

    /** 在后台线程预加载 songSaver，避免主线程首次访问时触发 MMKV + Gson 反序列化 */
    fun preload() {
        songSaver.hashCode()  // 触发 lazy MMKV 加载 + Gson 反序列化
        songs.size           // 预热缓存
        folders.size
        hideSongs.size
        hideFolders.size
    }

    val songs: List<YosMediaItem>
        get() {
            val currentSongVersion = songsDataVersion
            val currentFolderVersion = folderListVersion
            if (songsCacheVersion == currentSongVersion && folderListVersionForCache == currentFolderVersion) {
                return songsCache
            }

            val hiddenUris = lazy {
                val hideFolderPaths = hideFolders.toSet()
                allFolders.filter { it.path in hideFolderPaths }
                    .flatMap { it.songs }
                    .mapNotNull { it.uri }
                    .toSet()
            }
            val hideSongSet = lazy { hideSongs.toSet() }
            val excludeShort = SettingsLibrary.EnableExcludeSongsUnderOneMinute

            val result = songSaver.filter {
                it !in hideSongSet.value &&
                it.uri !in hiddenUris.value &&
                (it.serverId != null || !excludeShort || it.duration >= 60000)
            }

            songsCache = result
            songsCacheVersion = currentSongVersion
            folderListVersionForCache = currentFolderVersion
            return result
        }

    /** 简单自增版本号，替代 O(n) 的 songSaver.hashCode() */
    @Volatile private var songsDataVersion = 0L

    private fun bumpSongsVersion() {
        songsDataVersion++
        invalidateSongsCache()
    }

    val artists
        get() = songs/*.distinctBy { it.artist }.map { it.artist }*/.flatMap {
            it.artistsList ?: defaultArtists
        }
            .distinct()
            .sorted()

    val albums
        get() = songs.distinctBy { it.album ?: defaultAlbum }.map { it.album ?: defaultAlbum }.sorted()

    @Stable
    object Album {
        operator fun get(albumName: String) =
            songs.filter { (it.album ?: defaultAlbum) == albumName }
    }

    @Stable
    object Artist {
        operator fun get(artistName: String) =
            songs.filter { (it.artistsList ?: defaultArtists).contains(artistName) }
    }

    fun updatePlayList(playListV1: PlayListV1) {
        updateData(playListKey, playListV1)
    }

    fun loadPlayList(): PlayListV1 {
        return try {
            loadData(playListKey) ?: PlayListV1(null, null)
        } catch (_: Exception) {
            // 旧格式（完整 YosMediaItem 对象）反序列化失败，返回空
            PlayListV1(null, null)
        }
    }

    fun MediaItem.toYosMediaItem(): YosMediaItem {
        // 从 URI 提取远程 serverId
        val rawUri = this.localConfiguration?.uri?.toString() ?: ""
        val serverId = when {
            rawUri.startsWith("webdav://") -> rawUri.substringAfter("webdav://").substringBefore("/")
            else -> null
        }
        return YosMediaItem(
            uri = this.localConfiguration?.uri,
            mediaId = this.mediaId,
            mimeType = this.localConfiguration?.mimeType,
            title = this.title,
            writer = this.writer,
            compilation = this.compilation,
            composer = this.composer,
            artists = this.artistsName,
            album = this.album,
            albumArtists = this.albumArtists,
            thumb = this.thumb,
            trackNumber = this.trackNumber,
            discNumber = this.discNumber,
            genre = this.genre,
            recordingDay = this.recordingDay,
            recordingMonth = this.recordingMonth,
            recordingYear = this.recordingYear,
            releaseYear = this.releaseYear,
            artistId = this.artistId,
            albumId = this.albumId,
            genreId = this.genreId,
            author = this.author,
            addDate = this.addDate,
            duration = this.duration,
            modifiedDate = this.modifiedDate,
            cdTrackNumber = this.cdTrackNumber,
            serverId = serverId,
            fileSize = this.mediaMetadata.extras?.getLong("FileSize")?.takeIf { it > 0L }
        )
    }

    fun YosMediaItem.toMediaItem(): MediaItem {
        // 远程文件：优先从标签数据库加载缓存标签
        val cachedTags = if (serverId != null) {
            yos.music.player.data.remote.RemoteTagDatabase.get(this.uri?.toString() ?: "")
        } else null

        val displayTitle = cachedTags?.title ?: this.title
        val displayArtist = cachedTags?.artist ?: this.artists
        val displayAlbum = cachedTags?.album ?: this.album
        val displayAlbumArtist = cachedTags?.albumArtist ?: this.albumArtists
        val displayComposer = cachedTags?.composer ?: this.composer
        val displayGenre = cachedTags?.genre ?: this.genre
        val displayTrackNumber = cachedTags?.trackNumber ?: this.trackNumber
        val displayDiscNumber = cachedTags?.discNumber ?: this.discNumber
        val displayYear = cachedTags?.year ?: this.releaseYear ?: this.recordingYear

        return MediaItem.Builder()
            .setUri(this.uri)
            .setMediaId(this.mediaId ?: this.uri?.toString() ?: this.uri?.lastPathSegment ?: "0")
            .setMimeType(this.mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(displayTitle)
                    .setWriter(this.writer)
                    .setCompilation(this.compilation)
                    .setComposer(displayComposer)
                    .setArtist(displayArtist)
                    .setAlbumTitle(displayAlbum)
                    .setAlbumArtist(displayAlbumArtist)
                    .setArtworkUri(this.thumb)
                    .setTrackNumber(displayTrackNumber)
                    .setDiscNumber(displayDiscNumber)
                    .setGenre(displayGenre)
                    .setRecordingDay(this.recordingDay)
                    .setRecordingMonth(this.recordingMonth)
                    .setRecordingYear(displayYear ?: this.recordingYear)
                    .setReleaseYear(displayYear ?: this.releaseYear)
                    .setExtras(Bundle().apply {
                        this@toMediaItem.artistId?.let { putLong("ArtistId", it) }
                        this@toMediaItem.albumId?.let { putLong("AlbumId", it) }
                        this@toMediaItem.genreId?.let { putLong("GenreId", it) }
                        putString("Author", this@toMediaItem.author)
                        this@toMediaItem.addDate?.let { putLong("AddDate", it) }
                        putLong("Duration", this@toMediaItem.duration)
                        this@toMediaItem.modifiedDate?.let { putLong("ModifiedDate", it) }
                        this@toMediaItem.cdTrackNumber?.let { putInt("CdTrackNumber", it) }
                        this@toMediaItem.fileSize?.let { putLong("FileSize", it) }
                        //this@toMediaItem.samplingRate?.let { putInt("SamplingRate", it) }
                        //this@toMediaItem.bitrate?.let { putInt("Bitrate", it) }
                    })
                    .build()
            )
            .build()
    }

    fun updatePlayStatus(playStatus: PlayStatus) {
        updateData(playStatusKey, playStatus)
    }

    fun loadPlayStatus(): PlayStatus {
        return loadData(playStatusKey) ?: PlayStatus(null, 0L, false, 0)
    }

    private val playCountMap: MutableMap<String, Int> by lazy {
        (loadData<MutableMap<String, Int>>(playCountKey) ?: mutableMapOf())
    }

    fun incrementPlayCount(uri: Uri?) {
        val key = uri?.toString() ?: return
        playCountMap[key] = (playCountMap[key] ?: 0) + 1
        updateData(playCountKey, playCountMap)
    }

    fun getPlayCount(uri: Uri?): Int {
        return uri?.toString()?.let { playCountMap[it] } ?: 0
    }

    // --- 远程服务器配置持久化 ---

    fun saveRemoteServers(json: String) {
        updateData(remoteServersKey, json)
    }

    fun loadRemoteServers(): String? {
        return loadData<String>(remoteServersKey)
    }

    fun updateSongSaver(songs: List<YosMediaItem>) { songSaver = songs; bumpSongsVersion() }

    /** 直接更新 songSaver 中某首歌，绕过 hideSongs/hideFolders 过滤 */
    fun updateSongInFullList(updated: YosMediaItem) {
        val all = songSaver.toMutableList()
        val i = all.indexOfFirst { it.uri == updated.uri }
        if (i >= 0) { all[i] = updated; songSaver = all; bumpSongsVersion() }
    }

    fun updateFolderSongs(serverId: String, folderPath: String, updatedSong: YosMediaItem) {
        val normalizedPath = normalizePath(folderPath)
        val rIdx = remoteFolders.indexOfFirst { it.serverId == serverId && normalizePath(it.path) == normalizedPath }
        if (rIdx >= 0) {
            val songs = remoteFolders[rIdx].songs.toMutableList()
            val sIdx = songs.indexOfFirst { it.uri == updatedSong.uri }
            if (sIdx >= 0) {
                songs[sIdx] = updatedSong
                remoteFolders[rIdx] = remoteFolders[rIdx].copy(songs = songs)
                folderListVersion++
            }
        }
    }

    var folderListVersion by mutableIntStateOf(0)
        private set

    // 远程文件夹独立追踪（绕开 mutableDataSaverListStateOf 的 Gson 序列化问题）
    private val remoteFolders = mutableListOf<Folder>()
    val allFolders: List<Folder> get() {
        val remotePaths = remoteFolders.map { normalizePath(it.path) }.toSet()
        // 去重安全网：确保任何来源的重复都不会出现在最终结果中
        return (remoteFolders + folders.filter { normalizePath(it.path) !in remotePaths })
            .distinctBy { it.serverId to normalizePath(it.path) }
    }

    // 远程文件夹元数据持久化结构（独立于 songSaver，防止竞态导致丢失）
    data class RemoteFolderMeta(val serverId: String, val name: String, val path: String)

    /** 统一路径格式，去除前后斜杠 */
    private fun normalizePath(path: String) = path.trim('/')

    private fun saveRemoteFoldersMeta() {
        // 按 (serverId, normalizedPath) 去重，防止重复保存
        val metas = remoteFolders
            .distinctBy { it.serverId to normalizePath(it.path) }
            .map { RemoteFolderMeta(it.serverId ?: "", it.name, normalizePath(it.path)) }
        updateData(remoteFoldersMetaKey, metas)
    }

    private fun loadRemoteFoldersMeta(): List<RemoteFolderMeta> {
        return loadData<List<RemoteFolderMeta>>(remoteFoldersMetaKey) ?: emptyList()
    }

    fun mountRemoteFolder(folder: Folder) {
        synchronized(this) {
            val serverId = folder.serverId ?: return
            val normalizedPath = normalizePath(folder.path)
            // 同一路径的远程文件夹只挂载一次：已存在则更新歌曲列表，不存在则新增
            val existingIdx = remoteFolders.indexOfFirst {
                it.serverId == serverId && normalizePath(it.path) == normalizedPath
            }
            if (existingIdx >= 0) {
                // 更新：合并新增歌曲到已有文件夹
                val existing = remoteFolders[existingIdx]
                val existingUris = existing.songs.mapNotNull { it.uri?.toString() }.toSet()
                val newSongs = folder.songs.filter { it.uri?.toString() !in existingUris }
                val updated = existing.copy(songs = existing.songs + newSongs)
                remoteFolders[existingIdx] = updated
                songSaver = songSaver + newSongs
                removeLegacyRemoteFolderMirror(serverId, normalizedPath)
            } else {
                remoteFolders.add(folder.copy(path = normalizedPath))
                songSaver = songSaver + folder.songs
                removeLegacyRemoteFolderMirror(serverId, normalizedPath)
            }
            saveRemoteFoldersMeta()
            folderListVersion++
            bumpSongsVersion()
        }
    }

    fun rebuildRemoteFolders() {
        synchronized(this) {
            remoteFolders.clear()
            val remoteSongs = songSaver.filter { it.serverId != null }

            val metaByKey = loadRemoteFoldersMeta().associateBy { it.serverId to normalizePath(it.path) }
            val grouped = remoteSongs.groupBy { song ->
                val path = normalizePath(song.uri?.path?.substringBeforeLast('/') ?: "")
                song.serverId to path
            }
            val keysFromSongs = grouped.keys.toSet()

            for ((key, songs) in grouped) {
                val (sid, path) = key
                if (sid != null && path.isNotEmpty()) {
                    val name = path.substringAfterLast('/').ifEmpty { path }
                    if (remoteFolders.none { it.serverId == sid && normalizePath(it.path) == path }) {
                        remoteFolders.add(Folder(name, path, songs, serverId = sid))
                    }
                }
            }

            for ((key, meta) in metaByKey) {
                val (_, path) = key
                if (key !in keysFromSongs && remoteFolders.none {
                    it.serverId == meta.serverId && normalizePath(it.path) == path
                }) {
                    remoteFolders.add(Folder(meta.name, path, emptyList(), serverId = meta.serverId))
                }
            }
            saveRemoteFoldersMeta()
            folderListVersion++
        }
    }

    fun unmountRemoteFolder(serverId: String, folderPath: String): Boolean {
        val normalizedPath = normalizePath(folderPath)
        val rf = remoteFolders.find { it.serverId == serverId && normalizePath(it.path) == normalizedPath } ?: return false
        val urisToRemove = rf.songs.mapNotNull { it.uri?.toString() }
        remoteFolders.removeAll { it.serverId == serverId && normalizePath(it.path) == normalizedPath }
        removeLegacyRemoteFolderMirror(serverId, normalizedPath)
        removeHiddenFolderPath(normalizedPath)
        songSaver = songSaver.filter { it.uri?.toString() !in urisToRemove }
        saveRemoteFoldersMeta()
        folderListVersion++
        bumpSongsVersion()
        return true
    }

    fun unmountRemoteServer(serverId: String): Int {
        val foldersToRemove = remoteFolders.filter { it.serverId == serverId }
        val urisToRemove = songSaver
            .filter { it.serverId == serverId }
            .mapNotNull { it.uri?.toString() }
            .toSet()
        if (foldersToRemove.isEmpty() && urisToRemove.isEmpty()) return 0

        remoteFolders.removeAll { it.serverId == serverId }
        foldersToRemove.forEach { removeHiddenFolderPath(normalizePath(it.path)) }
        folders = folders.filterNot { folder ->
            folder.songs.any { it.serverId == serverId || it.uri?.toString() in urisToRemove }
        }
        songSaver = songSaver.filter { it.serverId != serverId && it.uri?.toString() !in urisToRemove }
        urisToRemove.forEach { uri ->
            runCatching { yos.music.player.data.remote.RemoteTagDatabase.delete(uri) }
        }
        saveRemoteFoldersMeta()
        folderListVersion++
        bumpSongsVersion()
        return foldersToRemove.size
    }

    private fun removeLegacyRemoteFolderMirror(serverId: String, normalizedPath: String) {
        folders = folders.filterNot { folder ->
            normalizePath(folder.path) == normalizedPath &&
                folder.songs.any { it.serverId == serverId }
        }
    }

    private fun removeHiddenFolderPath(normalizedPath: String) {
        hideFoldersSaver = hideFoldersSaver.filterNot { normalizePath(it.value) == normalizedPath }
    }

    private val cachedGson by lazy {
        GsonBuilder().registerTypeAdapter(Uri::class.java, UriTypeAdapter()).create()
    }
    private val cachedMmkv by lazy { MMKV.mmkvWithID(mmkvID) }

    private inline fun <reified T> updateData(key: String, value: T) {
        val json = cachedGson.toJson(value)
        cachedMmkv.encode(key, json)
    }

    private inline fun <reified T> loadData(key: String): T? {
        val json = cachedMmkv.decodeString(key)
        return json?.let {
            val type = object : TypeToken<T>() {}.type
            cachedGson.fromJson(it, type)
        }
    }

    fun hideFolder(folder: Folder) {
        updateFolderVisibility(folder, hide = true)
    }

    fun unHideFolder(folder: Folder) {
        updateFolderVisibility(folder, hide = false)
    }

    fun hideSong(song: YosMediaItem) {
        hideSongs = hideSongs + song
        invalidateSongsCache()
    }

    fun unHideSong(song: YosMediaItem) {
        hideSongs = hideSongs - song
        invalidateSongsCache()
    }

    /*fun removeSong(song: YosMediaItem) {
        folders = folders.map {
            if (it.songs.contains(song)) {
                return@map it.copy(
                    name = it.name,
                    songs = it.songs.toMutableList().apply { remove(song) })
            }
            it
        }
        hideSongs = hideSongs - song
    }*/

    private fun updateFolderVisibility(folder: Folder, hide: Boolean) {
        if (hide) {
            hideFoldersSaver = hideFoldersSaver.plus(YosStringWrapper(folder.path))
        } else {
            hideFoldersSaver = hideFoldersSaver.minus(YosStringWrapper(folder.path))
        }
        invalidateSongsCache()
    }

    private val readerConfiguration = ReaderConfiguration(
        ItemConstructor { uri, mediaId, mimeType, title, writer, compilation,
                          composer, artist, albumTitle, albumArtist, artworkUri,
                          cdTrackNumber, trackNumber, discNumber, genre,
                          recordingDay, recordingMonth, recordingYear, releaseYear,
                          artistId, albumId, genreId, author, addDate,
                          duration, modifiedDate ->
            //val audioProperties = getAudioProperties(uri.path!!)
            return@ItemConstructor MediaItem
                .Builder()
                .setUri(uri)
                .setMediaId(mediaId.toString())
                .setMimeType(mimeType)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setTitle(title)
                        .setWriter(writer)
                        .setCompilation(compilation)
                        .setComposer(composer)
                        .setArtist(artist)
                        .setAlbumTitle(albumTitle)
                        .setAlbumArtist(albumArtist)
                        .setArtworkUri(artworkUri)
                        .setTrackNumber(trackNumber)
                        .setDiscNumber(discNumber)
                        .setGenre(genre)
                        .setRecordingDay(recordingDay)
                        .setRecordingMonth(recordingMonth)
                        .setRecordingYear(recordingYear)
                        .setReleaseYear(releaseYear)
                        .setExtras(Bundle().apply {
                            if (artistId != null) {
                                putLong("ArtistId", artistId)
                            }
                            if (albumId != null) {
                                putLong("AlbumId", albumId)
                            }
                            if (genreId != null) {
                                putLong("GenreId", genreId)
                            }
                            putString("Author", author)
                            if (addDate != null) {
                                putLong("AddDate", addDate)
                            }
                            if (duration != null) {
                                putLong("Duration", duration)
                            }
                            if (modifiedDate != null) {
                                putLong("ModifiedDate", modifiedDate)
                            }
                            cdTrackNumber?.toIntOrNull()
                                ?.let { it1 -> putInt("CdTrackNumber", it1) }
                            /*audioProperties?.let {
                                putInt("SamplingRate", it.first)
                                putInt("Bitrate", it.second)
                            }*/
                        })
                        .build(),
                ).build()
        },
        shouldFetchPlaylist = true,
        shouldIncludeExtraFormat = true
    )

    suspend fun scanMedia(context: Context): ReaderResult<MediaItem> {
        return withContext(Dispatchers.IO) {
            val result = Reader.readFromMediaStore(
                context,
                readerConfiguration
            )

            // 刷新远程文件夹：逐目录重新列出文件，检测新增和删除
            val existingRemoteSongs = songSaver.filter { it.serverId != null }
            val updatedRemoteSongs = refreshRemoteFolders()
            // 安全保护：刷新失败时保留已有的远程歌曲，避免列表突然变空
            val finalRemoteSongs = if (updatedRemoteSongs.isEmpty() && existingRemoteSongs.isNotEmpty()) {
                existingRemoteSongs
            } else {
                updatedRemoteSongs
            }

            songSaver = result.songList.fastMap {
                it.toYosMediaItem()
            } + finalRemoteSongs

            result.shallowFolder.folderList.map {
                val name = it.key
                val path = it.value.songList.first().uri?.path?.substringBeforeLast("/")?:""
                val songs = it.value.songList.fastMap { thisSong ->
                    thisSong.toYosMediaItem()
                }
                Folder(name, path, songs)
            }.let { localFolders ->
                folders = localFolders
            }

            val playing = MediaController.playingMusicList.value
            if (playing != null) {
                updatePlayList(
                    PlayListV1(
                        MediaController.mainMusicList.toUriStrings(),
                        playing,
                        yos.music.player.code.MediaController.sourceMusicList
                    )
                )
            }

            // 去重：清除 folders 中可能累积的重复条目
            folders = folders.distinctBy { normalizePath(it.path) }

            bumpSongsVersion()
            result
        }
    }

    /**
     * 刷新所有已挂载的远程文件夹：重新 PROPFIND 列出文件，合并新增/删除。
     * 返回更新后的远程歌曲列表。新文件统一汇总后启动一次后台标签扫描。
     */
    private suspend fun refreshRemoteFolders(): List<YosMediaItem> {
        // 每次刷新前从持久化数据重建，确保状态干净、去重
        rebuildRemoteFolders()
        val savedConfigs = loadRemoteServers()
        if (savedConfigs.isNullOrBlank()) return songSaver.filter { it.serverId != null }
        yos.music.player.data.remote.RemoteServerManager.loadConfigs(savedConfigs)

        val result = mutableListOf<YosMediaItem>()
        val foldersToRefresh = remoteFolders.toList()
        // 汇总所有文件夹的歌曲，统一启动一次后台标签扫描（避免多次 startBackgroundScan 互相取消）
        val allSongsByServer = mutableMapOf<String, MutableList<YosMediaItem>>()
        val folderPathByUri = mutableMapOf<String, Pair<String, String>>()

        for (folder in foldersToRefresh) {
            val serverId = folder.serverId ?: continue
            val path = folder.path

            runCatching {
                yos.music.player.data.remote.RemoteServerManager.connect(serverId)
                val remoteFiles = yos.music.player.data.remote.RemoteServerManager.listAudioFiles(serverId, path)
                val currentUris = folder.songs.mapNotNull { it.uri?.toString() }.toSet()
                val remoteUris = remoteFiles.map { it.toWebDavUri(serverId) }.toSet()

                // 保留仍然存在的歌曲
                val retained = folder.songs.filter { it.uri?.toString() in remoteUris }
                retained.forEach { song ->
                    song.uri?.toString()?.let { folderPathByUri[it] = serverId to path }
                }
                result.addAll(retained)
                allSongsByServer.getOrPut(serverId) { mutableListOf() }.addAll(retained)

                // 更新 remoteFolders 中的歌曲列表
                val idx = remoteFolders.indexOfFirst { it.serverId == serverId && normalizePath(it.path) == normalizePath(path) }
                if (idx >= 0) {
                    remoteFolders[idx] = remoteFolders[idx].copy(songs = retained)
                }

                // 新文件：远程有但本地没有
                val newFiles = remoteFiles.filter { it.toWebDavUri(serverId) !in currentUris }
                if (newFiles.isNotEmpty()) {
                    val config = yos.music.player.data.remote.RemoteServerManager.getServer(serverId) ?: return@runCatching
                    val newSongs = yos.music.player.data.remote.RemoteMetadataScanner.quickListAudioFiles(
                        serverId, path, config
                    ).filter { it.uri?.toString() !in currentUris }
                    newSongs.forEach { song ->
                        song.uri?.toString()?.let { folderPathByUri[it] = serverId to path }
                    }
                    result.addAll(newSongs)

                    if (idx >= 0) {
                        remoteFolders[idx] = remoteFolders[idx].copy(songs = retained + newSongs)
                    }

                    allSongsByServer.getOrPut(serverId) { mutableListOf() }.addAll(newSongs)
                }
            }.getOrElse {
                // 连接失败时保留原有歌曲
                result.addAll(folder.songs)
            }
        }

        // 汇总所有歌曲，按 serverId 统一启动后台标签扫描（强制重扫全部歌曲）
        for ((serverId, songs) in allSongsByServer) {
            if (songs.isNotEmpty()) {
                yos.music.player.data.remote.RemoteMetadataScanner.startBackgroundScan(
                    songs, serverId
                ) { updated ->
                    updateSongInFullList(updated)
                    val uri = updated.uri?.toString() ?: return@startBackgroundScan
                    val (_, path) = folderPathByUri[uri] ?: return@startBackgroundScan
                    updateFolderSongs(serverId, path, updated)
                }
            }
        }

        saveRemoteFoldersMeta()
        folderListVersion++
        return result
    }

    /** 将 RemoteFile 转为 webdav URI 字符串 */
    private fun yos.music.player.data.remote.RemoteFile.toWebDavUri(serverId: String): String {
        return "webdav://$serverId/${path.trimStart('/')}"
    }
}

fun List<YosMediaItem>.toUriStrings() = mapNotNull { it.uri?.toString() }
