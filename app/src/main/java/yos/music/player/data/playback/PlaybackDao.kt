package yos.music.player.data.playback

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaybackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<PlaybackSongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueue(entries: List<PlaybackQueueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: PlaybackStateEntity)

    @Query("DELETE FROM playback_queue WHERE queueType = :queueType")
    suspend fun deleteQueue(queueType: String)

    @Query("SELECT * FROM playback_queue WHERE queueType = :queueType ORDER BY queueIndex")
    suspend fun getQueue(queueType: String): List<PlaybackQueueEntity>

    @Query("SELECT * FROM playback_songs WHERE storageKey IN (:keys)")
    suspend fun getSongs(keys: List<String>): List<PlaybackSongEntity>

    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun getState(): PlaybackStateEntity?
}
