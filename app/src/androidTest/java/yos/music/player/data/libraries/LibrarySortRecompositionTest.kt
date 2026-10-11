package yos.music.player.data.libraries

import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.junit.Rule
import org.junit.Test

/** Exercise the real preference getters inside remember, including compiler stability analysis. */
class LibrarySortRecompositionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preferenceChangesInvalidateBothPagesWithoutOtherStateChanges() {
        val artistMode = SettingsLibrary.ArtistSortMode
        val artistDescending = SettingsLibrary.ArtistSortDescending
        val albumMode = SettingsLibrary.AlbumSortMode
        val albumDescending = SettingsLibrary.AlbumSortDescending
        try {
            SettingsLibrary.ArtistSortMode = 0
            SettingsLibrary.ArtistSortDescending = false
            SettingsLibrary.AlbumSortMode = 0
            SettingsLibrary.AlbumSortDescending = false
            compose.setContent {
                val artistSort = SettingsLibrary.ArtistSortMode
                val artistDesc = SettingsLibrary.ArtistSortDescending
                val albumSort = SettingsLibrary.AlbumSortMode
                val albumDesc = SettingsLibrary.AlbumSortDescending
                val artists = remember(artistSort, artistDesc) { "$artistSort:$artistDesc" }
                val albums = remember(albumSort, albumDesc) { "$albumSort:$albumDesc" }
                Text("$artists/$albums", Modifier.testTag("order"))
            }
            compose.onNodeWithTag("order").assertTextEquals("0:false/0:false")
            for (mode in 1..2) {
                compose.runOnIdle { SettingsLibrary.ArtistSortMode = mode }
                compose.onNodeWithTag("order").assertTextEquals("$mode:false/0:false")
            }
            compose.runOnIdle { SettingsLibrary.ArtistSortDescending = true }
            compose.onNodeWithTag("order").assertTextEquals("2:true/0:false")
            for (mode in 1..4) {
                compose.runOnIdle { SettingsLibrary.AlbumSortMode = mode }
                compose.onNodeWithTag("order").assertTextEquals("2:true/$mode:false")
            }
            compose.runOnIdle { SettingsLibrary.AlbumSortDescending = true }
            compose.onNodeWithTag("order").assertTextEquals("2:true/4:true")
        } finally {
            compose.runOnIdle {
                SettingsLibrary.ArtistSortMode = artistMode
                SettingsLibrary.ArtistSortDescending = artistDescending
                SettingsLibrary.AlbumSortMode = albumMode
                SettingsLibrary.AlbumSortDescending = albumDescending
            }
        }
    }
}
