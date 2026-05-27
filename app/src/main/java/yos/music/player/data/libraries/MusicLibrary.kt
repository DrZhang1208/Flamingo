@file:Suppress("SameParameterValue")

package yos.music.player.data.libraries

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.compose.runtime.Stable
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
    val mainMusicList: List<YosMediaItem>?,
    val playingMusicList: List<YosMediaItem>?
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
    val tagScanStatus: String? = null  // "PENDING"/"SCANNING"/"COMPLETE"/"FAILED"，null 等同于 COMPLETE
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

    val songs: List<YosMediaItem>
        get() = songSaver
            .filter { it !in hideSongs && it.uri !in allFolders.filter { thisFolders -> thisFolders.path in hideFolders }
                .flatMap { thisFlatMap -> thisFlatMap.songs }
                .map { thisMap -> thisMap.uri } &&
                (it.serverId != null || !SettingsLibrary.EnableExcludeSongsUnderOneMinute || it.duration >= 60000)
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
        val loadedData = loadData(playListKey) ?: PlayListV1(null, null)
        return loadedData
    }

    fun MediaItem.toYosMediaItem(): YosMediaItem {
        // 从 URI 提取远程 serverId
        val rawUri = this.localConfiguration?.uri?.toString() ?: ""
        val serverId = when {
            rawUri.startsWith("webdav://") -> rawUri.substringAfter("webdav://").substringBefore("/")
            rawUri.startsWith("smb://") -> rawUri.substringAfter("smb://").substringBefore("/")
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
            serverId = serverId
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
                    .setComposer(this.composer)
                    .setArtist(displayArtist)
                    .setAlbumTitle(displayAlbum)
                    .setAlbumArtist(this.albumArtists)
                    .setArtworkUri(this.thumb)
                    .setTrackNumber(this.trackNumber)
                    .setDiscNumber(this.discNumber)
                    .setGenre(this.genre)
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

    fun updateSongSaver(songs: List<YosMediaItem>) { songSaver = songs }

    /** 直接更新 songSaver 中某首歌，绕过 hideSongs/hideFolders 过滤 */
    fun updateSongInFullList(updated: YosMediaItem) {
        val all = songSaver.toMutableList()
        val i = all.indexOfFirst { it.uri == updated.uri }
        if (i >= 0) { all[i] = updated; songSaver = all }
    }

    fun updateFolderSongs(serverId: String, folderName: String, updatedSong: YosMediaItem) {
        val rIdx = remoteFolders.indexOfFirst { it.serverId == serverId && it.name == folderName }
        if (rIdx >= 0) {
            val songs = remoteFolders[rIdx].songs.toMutableList()
            val sIdx = songs.indexOfFirst { it.uri == updatedSong.uri }
            if (sIdx >= 0) { songs[sIdx] = updatedSong; remoteFolders[rIdx] = remoteFolders[rIdx].copy(songs = songs) }
        }
    }

    @Volatile var folderListVersion = 0

    // 远程文件夹独立追踪（绕开 mutableDataSaverListStateOf 的 Gson 序列化问题）
    private val remoteFolders = mutableListOf<Folder>()
    val allFolders: List<Folder> get() {
        val remotePaths = remoteFolders.map { it.path }.toSet()
        return remoteFolders + folders.filter { it.path !in remotePaths }
    }

    fun mountRemoteFolder(folder: Folder) {
        remoteFolders.add(folder)
        folders = folders + folder.copy(serverId = null)  // folders 存储时去掉 serverId 避免序列化问题
        songSaver = songSaver + folder.songs
        folderListVersion++
    }

    fun rebuildRemoteFolders() {
        // 从 songSaver 中 serverId != null 的歌曲重建远程文件夹
        remoteFolders.clear()
        val remoteSongs = songSaver.filter { it.serverId != null }
        val grouped = remoteSongs.groupBy { it.serverId to it.uri?.path?.substringBeforeLast('/') }
        for ((key, songs) in grouped) {
            val (sid, path) = key
            if (sid != null && path != null) {
                val name = path.substringAfterLast('/').ifEmpty { path }
                remoteFolders.add(Folder(name, path, songs, serverId = sid))
            }
        }
    }

    fun unmountRemoteFolder(folderName: String, serverId: String) {
        // 从 remoteFolders 查找实际的歌曲 URI（folders 中 serverId 为 null，无法匹配）
        val rf = remoteFolders.find { it.name == folderName && it.serverId == serverId }
        val urisToRemove = rf?.songs?.mapNotNull { it.uri?.toString() } ?: emptyList()
        remoteFolders.removeAll { it.name == folderName && it.serverId == serverId }
        folders = folders.filter { it.name != folderName }
        songSaver = songSaver.filter { it.uri?.toString() !in urisToRemove }
        folderListVersion++
    }

    private inline fun <reified T> updateData(key: String, value: T) {
        val gson = GsonBuilder().registerTypeAdapter(Uri::class.java, UriTypeAdapter()).create()
        val mmkv = MMKV.mmkvWithID(mmkvID)
        val json = gson.toJson(value)
        mmkv.encode(key, json)
    }

    private inline fun <reified T> loadData(key: String): T? {
        val gson = GsonBuilder().registerTypeAdapter(Uri::class.java, UriTypeAdapter()).create()
        val mmkv = MMKV.mmkvWithID(mmkvID)
        val json = mmkv.decodeString(key)
        return json?.let {
            val type = object : TypeToken<T>() {}.type
            gson.fromJson(it, type)
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
    }

    fun unHideSong(song: YosMediaItem) {
        hideSongs = hideSongs - song
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

            // 有层级结构的result.folderStructure.folderList[""].folderList
            // val folderList = result.folderStructure.folderList

            // 保留远程挂载的歌曲（不受本地 MediaStore 扫描影响）
            val remoteSongs = songSaver.filter { it.serverId != null }
            songSaver = result.songList.fastMap {
                it.toYosMediaItem()
            } + remoteSongs

            result.shallowFolder.folderList.map {
                val name = it.key
                val path = it.value.songList.first().uri?.path?.substringBeforeLast("/")?:""
                val songs = it.value.songList.fastMap { thisSong ->
                    thisSong.toYosMediaItem()
                }
                Folder(name, path, songs)
            }.let { localFolders ->
                // 保留已挂载的远程文件夹副本，避免被本地扫描覆盖
                val remotePaths = remoteFolders.map { it.path }.toSet()
                val preservedRemote = folders.filter { it.path in remotePaths }
                folders = localFolders + preservedRemote
            }

            /*folders = folderList.map { (path, fileNode) ->
                Folder(
                    path,
                    fileNode.songList.toList()
                )
            }.filter { folder ->
                folder !in hideFolders
            }*/



            updatePlayList(
                PlayListV1(
                    MediaController.mainMusicList,
                    MediaController.playingMusicList.value ?: emptyList()
                )
            )

            result
        }
    }
}
