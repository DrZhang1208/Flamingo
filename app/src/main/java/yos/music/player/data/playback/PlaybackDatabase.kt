package yos.music.player.data.playback

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaybackSongEntity::class, PlaybackQueueEntity::class, PlaybackStateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PlaybackDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao

    companion object {
        @Volatile private var instance: PlaybackDatabase? = null

        fun get(context: Context): PlaybackDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlaybackDatabase::class.java,
                    "playback.db"
                ).build().also { instance = it }
            }
    }
}
