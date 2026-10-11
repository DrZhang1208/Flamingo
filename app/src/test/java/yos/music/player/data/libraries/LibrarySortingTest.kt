package yos.music.player.data.libraries

import org.junit.Assert.*
import org.junit.Test

class LibrarySortingTest {
    private fun song(artist: String? = "A", album: String? = "First", year: Int? = null,
                     modified: Long? = null, added: Long? = null, albumArtist: String? = null) = YosMediaItem(
        uri = null, mediaId = null, mimeType = null, title = null, writer = null, compilation = null,
        composer = null, artists = artist, album = album, albumArtists = albumArtist, thumb = null,
        trackNumber = null, discNumber = null, genre = null, recordingDay = null, recordingMonth = null,
        recordingYear = null, releaseYear = year, artistId = null, albumId = null, genreId = null,
        author = null, addDate = added, duration = 1000, modifiedDate = modified, cdTrackNumber = null)

    @Test fun collaboratorsGetTheirOwnCountsAndDuplicateNamesDoNotDoubleCountTracks() {
        val entries = artistLibraryEntries(listOf(song("A & B", "First"), song("A", "First"),
            song("A & A", "Second"), song(null, null)))
        val a = entries.single { it.name == "A" }
        assertEquals(3, a.songs.size)
        assertEquals(2, a.albumCount)
        assertEquals(1, entries.single { it.name == "B" }.songs.size)
        assertEquals(1, entries.single { it.name == defaultArtistsName }.albumCount)
        assertEquals(listOf("A", "B", defaultArtistsName), sortArtists(entries, ArtistSort.NAME, false).map { it.name })
        assertEquals("A", sortArtists(entries, ArtistSort.SONG_COUNT, true).first().name)
        assertEquals("A", sortArtists(entries, ArtistSort.ALBUM_COUNT, true).first().name)
        assertEquals(defaultArtistsName, sortArtists(entries, ArtistSort.NAME, true).first().name)
    }

    @Test fun namesUseFullPinyinAndCaseInsensitiveOrdering() {
        val entries = artistLibraryEntries(listOf(song("周杰伦"), song("陈奕迅"), song("adele")))
        assertEquals(listOf("adele", "陈奕迅", "周杰伦"), sortArtists(entries, ArtistSort.NAME, false).map { it.name })
    }

    @Test fun allAlbumSortsUseMetadataAndKeepTiesDeterministic() {
        val entries = albumLibraryEntries(listOf(
            song("Z", "First", 2020, 1_700_000_000L),
            song("Z", "First", 2020, 1_700_000_001L),
            song("Z", "First", 1990),
            song("A", "Second", 2024, 1_800_000_000_000L),
            song("B", "Unknown")))
        assertEquals(listOf("First", "Second", "Unknown"), sortAlbums(entries, AlbumSort.NAME, false).map { it.name })
        assertEquals(listOf("Second", "First", "Unknown"), sortAlbums(entries, AlbumSort.YEAR, true).map { it.name })
        assertEquals(listOf("First", "Second", "Unknown"), sortAlbums(entries, AlbumSort.YEAR, false).map { it.name })
        assertEquals(listOf("Second", "First", "Unknown"), sortAlbums(entries, AlbumSort.UPDATED, true).map { it.name })
        assertEquals(listOf("First", "Second", "Unknown"), sortAlbums(entries, AlbumSort.UPDATED, false).map { it.name })
        assertEquals(listOf("Second", "Unknown", "First"), sortAlbums(entries, AlbumSort.ARTIST, false).map { it.name })
        assertEquals("First", sortAlbums(entries, AlbumSort.TRACK_COUNT, true).first().name)
        assertEquals(2020, entries.single { it.name == "First" }.year)
    }

    @Test fun datesNormaliseSecondsAndMillisecondsAndFallBackToAddedTime() {
        val entry = AlbumLibraryEntry("Mixed", listOf(song(modified = 1_800_000_000L),
            song(modified = 1_700_000_000_000L), song(modified = 0, added = 1_850_000_000_000L)))
        assertEquals(1_850_000_000_000L, entry.updated)
        assertNull(AlbumLibraryEntry("Missing", listOf(song(year = 0, modified = -1))).year)
        assertNull(AlbumLibraryEntry("Missing", listOf(song(modified = -1))).updated)
    }

    @Test fun albumArtistTakesPrecedenceOverFeaturedArtistsAndYearFallsBackToRecordingYear() {
        val entry = AlbumLibraryEntry("Duet", listOf(song("A & Z", albumArtist = "Z", year = null)
            .copy(recordingYear = 2009)))
        assertEquals("z", entry.sortArtist)
        assertEquals(2009, entry.year)
    }

    @Test fun rebuildingAfterLibraryChangeUpdatesCountsAndUnknownAlbumGrouping() {
        val initial = listOf(song(album = null))
        assertEquals(defaultAlbum, albumLibraryEntries(initial).single().name)
        val refreshed = artistLibraryEntries(initial + song(album = "Added")).single()
        assertEquals(2, refreshed.songs.size)
        assertEquals(2, refreshed.albumCount)
        assertTrue(sortAlbums(emptyList(), AlbumSort.UPDATED, true).isEmpty())
    }

    @Test fun switchingSortUsesPreparedOrdersWithoutRepeatingSorting() {
        val entries = artistLibraryEntries(listOf(song("Z", "One"), song("Z", "Two"), song("A", "One")))
        var sorts = 0
        val index = LibrarySortIndex(entries, ArtistSort.entries) { source, mode, descending ->
            sorts++
            sortArtists(source, mode, descending)
        }
        assertEquals(6, sorts)
        val alphabetical = index.ordered(ArtistSort.NAME, false)
        assertEquals(listOf("A", "Z"), alphabetical.map { it.name })
        repeat(1000) {
            assertEquals("Z", index.ordered(ArtistSort.SONG_COUNT, true).first().name)
            assertEquals("Z", index.ordered(ArtistSort.ALBUM_COUNT, true).first().name)
            assertSame(alphabetical, index.ordered(ArtistSort.NAME, false))
        }
        assertEquals(6, sorts)
    }

    @Test fun albumIndexRefreshesAllOrdersWhenLibraryChanges() {
        val initial = albumLibraryEntries(listOf(song(album = "A", year = 2000)))
        val before = LibrarySortIndex(initial, AlbumSort.entries, ::sortAlbums)
        val updated = albumLibraryEntries(listOf(song(album = "A", year = 2000),
            song(album = "B", year = 2025), song(album = "B", year = 2025)))
        val after = LibrarySortIndex(updated, AlbumSort.entries, ::sortAlbums)
        assertEquals(1, before.ordered(AlbumSort.NAME, false).size)
        for (mode in AlbumSort.entries) for (descending in listOf(false, true)) {
            assertEquals(sortAlbums(updated, mode, descending), after.ordered(mode, descending))
        }
        assertEquals("B", after.ordered(AlbumSort.TRACK_COUNT, true).first().name)
        assertEquals("B", after.ordered(AlbumSort.YEAR, true).first().name)
    }

}
