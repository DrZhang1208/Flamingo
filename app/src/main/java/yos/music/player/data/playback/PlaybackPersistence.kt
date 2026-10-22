package yos.music.player.data.playback

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import yos.music.player.UriTypeAdapter
import yos.music.player.data.libraries.PlayListV1
import yos.music.player.data.libraries.PlayStatus
import yos.music.player.data.libraries.YosMediaItem

interface PlaybackPersistence {
    suspend fun savePlayList(playList: PlayListV1)
    suspend fun loadPlayList(): PlayListV1
    suspend fun savePlayStatus(status: PlayStatus)
    suspend fun loadPlayStatus(): PlayStatus
}

/**
 * Room-backed playback storage. Old MMKV values are imported once and deliberately
 * retained so a previous app build can still be used as a rollback path.
 */
class RoomPlaybackPersistence(context: Context) : PlaybackPersistence {
    private val appContext = context.applicationContext
    private val database = PlaybackDatabase.get(appContext)
    private val dao = database.playbackDao()
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriTypeAdapter())
        .create()
    private val migrationPreferences = appContext.getSharedPreferences(MIGRATION_PREFERENCES, Context.MODE_PRIVATE)
    private val migrationMutex = Mutex()
    private val statusMutex = Mutex()

    override suspend fun savePlayList(playList: PlayListV1) {
        database.withTransaction {
            val songs = (playList.playingMusicList.orEmpty() + playList.sourceMusicList.orEmpty())
                .distinctBy(::songStorageKey)
                .map(::toEntity)
            if (songs.isNotEmpty()) dao.upsertSongs(songs)

            replaceQueue(
                PlaybackQueueType.MAIN,
                playList.mainMusicList.orEmpty().mapIndexed { index, uri ->
                    PlaybackQueueEntity(PlaybackQueueType.MAIN, index, null, null, uri)
                }
            )
            replaceQueue(PlaybackQueueType.PLAYING, songQueue(PlaybackQueueType.PLAYING, playList.playingMusicList))
            replaceQueue(PlaybackQueueType.SOURCE, songQueue(PlaybackQueueType.SOURCE, playList.sourceMusicList))
        }
        migrationPreferences.edit().putBoolean(PLAYLIST_MIGRATED, true).apply()
    }

    override suspend fun loadPlayList(): PlayListV1 {
        migrateLegacyPlayListIfNeeded()
        val main = dao.getQueue(PlaybackQueueType.MAIN).mapNotNull { it.uri }
        val playingRefs = dao.getQueue(PlaybackQueueType.PLAYING)
        val sourceRefs = dao.getQueue(PlaybackQueueType.SOURCE)
        val songsByKey = dao.getSongs(
            (playingRefs + sourceRefs).mapNotNull { it.storageKey }.distinct()
        ).associateBy { it.storageKey }

        return PlayListV1(
            mainMusicList = main.ifEmpty { null },
            playingMusicList = decodeQueue(playingRefs, songsByKey).ifEmpty { null },
            sourceMusicList = decodeQueue(sourceRefs, songsByKey).ifEmpty { null }
        )
    }

    override suspend fun savePlayStatus(status: PlayStatus) = statusMutex.withLock {
        database.withTransaction {
            status.music?.let { dao.upsertSongs(listOf(toEntity(it))) }
            dao.upsertState(
                PlaybackStateEntity(
                    currentStorageKey = status.music?.let(::songStorageKey),
                    positionMs = status.position.coerceAtLeast(0L),
                    shuffleEnabled = status.shuffleModeEnabled,
                    repeatMode = status.repeatMode,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        runCatching { PlaybackStartupCache.write(status) }
        migrationPreferences.edit().putBoolean(STATUS_MIGRATED, true).apply()
    }

    override suspend fun loadPlayStatus(): PlayStatus {
        migrateLegacyStatusIfNeeded()
        val state = dao.getState() ?: return emptyStatus()
        val music = state.currentStorageKey
            ?.let { dao.getSongs(listOf(it)).firstOrNull() }
            ?.let(::decodeSong)
        return PlayStatus(music, state.positionMs, state.shuffleEnabled, state.repeatMode)
    }

    private suspend fun migrateLegacyPlayListIfNeeded() = migrationMutex.withLock {
        if (migrationPreferences.getBoolean(PLAYLIST_MIGRATED, false)) return
        val legacy = loadLegacy<PlayListV1>(LEGACY_PLAYLIST_KEY) ?: PlayListV1(null, null)
        if (!legacy.mainMusicList.isNullOrEmpty() || !legacy.playingMusicList.isNullOrEmpty()) {
            savePlayList(legacy)
        } else {
            migrationPreferences.edit().putBoolean(PLAYLIST_MIGRATED, true).apply()
        }
    }

    private suspend fun migrateLegacyStatusIfNeeded() = migrationMutex.withLock {
        if (migrationPreferences.getBoolean(STATUS_MIGRATED, false)) return
        val legacy = loadLegacy<PlayStatus>(LEGACY_STATUS_KEY)
        if (legacy != null) savePlayStatus(legacy)
        else migrationPreferences.edit().putBoolean(STATUS_MIGRATED, true).apply()
    }

    private suspend fun replaceQueue(type: String, entries: List<PlaybackQueueEntity>) {
        dao.deleteQueue(type)
        if (entries.isNotEmpty()) dao.upsertQueue(entries)
    }

    private fun songQueue(type: String, songs: List<YosMediaItem>?): List<PlaybackQueueEntity> =
        songs.orEmpty().mapIndexed { index, song ->
            PlaybackQueueEntity(type, index, songStorageKey(song), song.mediaId, song.uri?.toString())
        }

    private fun decodeQueue(
        references: List<PlaybackQueueEntity>,
        songsByKey: Map<String, PlaybackSongEntity>
    ): List<YosMediaItem> = references.mapNotNull { reference ->
        reference.storageKey?.let(songsByKey::get)?.let(::decodeSong)
    }

    private fun toEntity(song: YosMediaItem) = PlaybackSongEntity(
        storageKey = songStorageKey(song),
        mediaId = song.mediaId,
        uri = song.uri?.toString(),
        payload = gson.toJson(song),
        updatedAt = System.currentTimeMillis()
    )

    private fun decodeSong(entity: PlaybackSongEntity): YosMediaItem? =
        runCatching { gson.fromJson(entity.payload, YosMediaItem::class.java) }.getOrNull()

    private fun songStorageKey(song: YosMediaItem): String =
        song.uri?.toString()?.takeIf { it.isNotBlank() }
            ?: song.mediaId?.takeIf { it.isNotBlank() }
            ?: "anonymous:${gson.toJson(song).hashCode()}"

    private inline fun <reified T> loadLegacy(key: String): T? {
        val raw = MMKV.mmkvWithID(LEGACY_MMKV_ID).decodeString(key) ?: return null
        return runCatching {
            val type = object : TypeToken<T>() {}.type
            gson.fromJson<T>(raw, type)
        }.getOrNull()
    }

    private fun emptyStatus() = PlayStatus(null, 0L, false, 0)

    private companion object {
        const val LEGACY_MMKV_ID = "yos_player_core"
        const val LEGACY_PLAYLIST_KEY = "yos_play_list_v1"
        const val LEGACY_STATUS_KEY = "yos_player_play_status"
        const val MIGRATION_PREFERENCES = "playback_room_migration"
        const val PLAYLIST_MIGRATED = "playlist_migrated_v1"
        const val STATUS_MIGRATED = "status_migrated_v1"
    }
}
