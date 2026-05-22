package yos.music.player.data.remote

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class CachedTags(
    val uri: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val duration: Long? = null,
    val coverPath: String? = null,
    val lyrics: String? = null,
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

object RemoteTagDatabase {
    private const val DB_NAME = "remote_tags.db"
    private const val DB_VERSION = 2
    private var db: SQLiteDatabase? = null

    fun init(context: Context) {
        if (db != null) return
        db = TagDBHelper(context).writableDatabase
    }

    fun get(uri: String): CachedTags? {
        val d = db ?: return null
        val c = d.query("remote_tags", null, "uri=?", arrayOf(uri), null, null, null)
        val result = if (c.moveToFirst()) {
            CachedTags(
                uri = c.getString(c.getColumnIndexOrThrow("uri")),
                title = c.getString(c.getColumnIndexOrThrow("title")),
                artist = c.getString(c.getColumnIndexOrThrow("artist")),
                album = c.getString(c.getColumnIndexOrThrow("album")),
                year = c.getInt(c.getColumnIndexOrThrow("year")).takeIf { it != 0 },
                duration = c.getLong(c.getColumnIndexOrThrow("duration")).takeIf { it != 0L },
                coverPath = c.getString(c.getColumnIndexOrThrow("cover_path")),
                lyrics = c.getString(c.getColumnIndexOrThrow("lyrics")),
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
            )
        } else null
        c.close()
        return result
    }

    fun put(uri: String, tags: CachedTags) {
        val d = db ?: return
        val cv = ContentValues().apply {
            put("uri", uri)
            tags.title?.let { put("title", it) }
            tags.artist?.let { put("artist", it) }
            tags.album?.let { put("album", it) }
            tags.year?.let { put("year", it) }
            tags.duration?.let { put("duration", it) }
            tags.coverPath?.let { put("cover_path", it) }
            tags.lyrics?.let { put("lyrics", it) }
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        d.insertWithOnConflict("remote_tags", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(uri: String) {
        db?.delete("remote_tags", "uri=?", arrayOf(uri))
    }

    fun cleanup(maxDays: Int = 30) {
        val cutoff = System.currentTimeMillis() / 1000 - maxDays * 86400L
        db?.delete("remote_tags", "updated_at < ?", arrayOf(cutoff.toString()))
    }

    private class TagDBHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE remote_tags (
                    uri TEXT PRIMARY KEY,
                    title TEXT,
                    artist TEXT,
                    album TEXT,
                    year INTEGER,
                    duration INTEGER,
                    cover_path TEXT,
                    lyrics TEXT,
                    updated_at INTEGER
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            if (old < 2) db.execSQL("ALTER TABLE remote_tags ADD COLUMN lyrics TEXT")
        }
    }
}
