package yos.music.player.data.playback

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_songs",
    indices = [Index("mediaId"), Index("uri")]
)
data class PlaybackSongEntity(
    @PrimaryKey val storageKey: String,
    val mediaId: String?,
    val uri: String?,
    val payload: String,
    val updatedAt: Long
)

@Entity(
    tableName = "playback_queue",
    primaryKeys = ["queueType", "queueIndex"],
    indices = [Index("storageKey")]
)
data class PlaybackQueueEntity(
    val queueType: String,
    val queueIndex: Int,
    val storageKey: String?,
    val mediaId: String?,
    val uri: String?
)

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val currentStorageKey: String?,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    val updatedAt: Long
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

internal object PlaybackQueueType {
    const val MAIN = "main"
    const val PLAYING = "playing"
    const val SOURCE = "source"
}
