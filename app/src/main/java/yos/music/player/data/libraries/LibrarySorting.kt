package yos.music.player.data.libraries

import com.github.promeg.pinyinhelper.Pinyin
import java.util.Locale

internal enum class ArtistSort { NAME, SONG_COUNT, ALBUM_COUNT }
internal enum class AlbumSort { NAME, YEAR, UPDATED, ARTIST, TRACK_COUNT }

internal data class ArtistLibraryEntry(val name: String, val songs: List<YosMediaItem>) {
    val albumCount = songs.map { it.album ?: defaultAlbum }.toSet().size
    val sortName = libraryNameKey(name)
}

internal data class AlbumLibraryEntry(val name: String, val songs: List<YosMediaItem>) {
    val sortName = libraryNameKey(name)
    val year = songs.mapNotNull { it.releaseYear ?: it.recordingYear }.filter { it > 0 }
        .groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()?.key
    // MediaStore dates are seconds; remote file timestamps are milliseconds.
    val updated = songs.mapNotNull { song ->
        (song.modifiedDate?.takeIf { it > 0 } ?: song.addDate?.takeIf { it > 0 })?.let {
            if (it < 100_000_000_000L) it * 1000L else it
        }
    }.maxOrNull()
    private val albumArtist = songs.mapNotNull { it.albumArtists?.trim()?.takeIf(String::isNotEmpty) }
        .groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { libraryNameKey(it.key) })
        .firstOrNull()?.key
    val sortArtist = libraryNameKey(albumArtist ?: songs.flatMap { it.artistsList ?: defaultArtists }
        .minByOrNull(::libraryNameKey).orEmpty())
}

internal fun libraryNameKey(name: String): String =
    name.map { Pinyin.toPinyin(it) }.joinToString("").lowercase(Locale.ROOT)

internal fun artistLibraryEntries(songs: List<YosMediaItem>): List<ArtistLibraryEntry> {
    val groups = linkedMapOf<String, MutableList<YosMediaItem>>()
    songs.forEach { song ->
        (song.artistsList ?: defaultArtists).distinct().forEach { artist ->
            groups.getOrPut(artist) { mutableListOf() }.add(song)
        }
    }
    return groups.map { (name, tracks) -> ArtistLibraryEntry(name, tracks) }
}

internal fun albumLibraryEntries(songs: List<YosMediaItem>): List<AlbumLibraryEntry> =
    songs.groupBy { it.album ?: defaultAlbum }.map { (name, tracks) -> AlbumLibraryEntry(name, tracks) }

internal fun sortArtists(entries: List<ArtistLibraryEntry>, mode: ArtistSort, descending: Boolean): List<ArtistLibraryEntry> =
    entries.sortedWith { a, b ->
        val primary = when (mode) {
            ArtistSort.NAME -> a.sortName.compareTo(b.sortName)
            ArtistSort.SONG_COUNT -> a.songs.size.compareTo(b.songs.size)
            ArtistSort.ALBUM_COUNT -> a.albumCount.compareTo(b.albumCount)
        }
        (if (descending) -primary else primary).takeIf { it != 0 }
            ?: compareValuesBy(a, b, { it.sortName }, { it.name })
    }

/** Unknown dates stay last in either direction; equal values have stable name ordering. */
private fun <T : Comparable<T>> compareOptional(a: T?, b: T?, descending: Boolean): Int = when {
    a == null && b == null -> 0
    a == null -> 1
    b == null -> -1
    descending -> b.compareTo(a)
    else -> a.compareTo(b)
}

internal fun sortAlbums(entries: List<AlbumLibraryEntry>, mode: AlbumSort, descending: Boolean): List<AlbumLibraryEntry> =
    entries.sortedWith { a, b ->
        val primary = when (mode) {
            AlbumSort.YEAR -> compareOptional(a.year, b.year, descending)
            AlbumSort.UPDATED -> compareOptional(a.updated, b.updated, descending)
            else -> {
                val value = when (mode) {
                    AlbumSort.NAME -> a.sortName.compareTo(b.sortName)
                    AlbumSort.ARTIST -> a.sortArtist.compareTo(b.sortArtist)
                    else -> a.songs.size.compareTo(b.songs.size)
                }
                if (descending) -value else value
            }
        }
        primary.takeIf { it != 0 } ?: compareValuesBy(a, b, { it.sortName }, { it.name })
    }

/** Build off the UI thread once per library change; selecting an order is a constant-time lookup. */
internal class LibrarySortIndex<T, M>(
    entries: List<T>, modes: List<M>, sort: (List<T>, M, Boolean) -> List<T>
) {
    private val orders = modes.flatMap { mode ->
        listOf((mode to false) to sort(entries, mode, false),
            (mode to true) to sort(entries, mode, true))
    }.toMap()

    fun ordered(mode: M, descending: Boolean): List<T> = orders.getValue(mode to descending)
}
