package yos.music.player.ui.widgets

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.data.objects.NowPlayingLyricState

class LyricPresentationStateTest {
    @Test fun playbackTicksDoNotInvalidateTheLyricPresentation() {
        val source = mutableStateOf(NowPlayingLyricState(
            trackKey = "track", entries = listOf(YosLyricLine(0f, 4000f, emptyList())),
            activeLineIndexes = setOf(0), generation = 1
        ))
        val presentation = lyricPresentationState { source.value }
        val initial = presentation.value
        var invalidations = 0
        val observer = SnapshotStateObserver { it() }
        val scope = Any()
        observer.start()
        try {
            observer.observeReads(scope, { _: Any -> invalidations++ }) { presentation.value }
            for (position in 100L..1000L step 100) {
                source.value = source.value.copy(effectivePositionMs = position)
                Snapshot.sendApplyNotifications()
            }
            assertEquals(0, invalidations)
            assertEquals(initial, presentation.value)
            assertSame(source.value.entries, presentation.value.entries)

            source.value = source.value.copy(activeLineIndexes = emptySet())
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
            assertEquals(emptySet<Int>(), presentation.value.activeLineIndexes)
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test fun documentReplacementsAndTrackChangesReachThePresentation() {
        val source = mutableStateOf(NowPlayingLyricState(trackKey = "first", generation = 1))
        val presentation = lyricPresentationState { source.value }
        val initial = presentation.value
        val entries = listOf(YosLyricLine(1000f, 3000f, emptyList()))
        source.value = source.value.copy(entries = entries, generation = 2)
        val replaced = presentation.value
        assertNotSame(initial, replaced)
        assertSame(entries, replaced.entries)
        assertEquals(2, replaced.generation)

        source.value = NowPlayingLyricState(trackKey = "second", generation = 3)
        assertEquals("second", presentation.value.trackKey)
        assertEquals(emptyList<YosLyricLine>(), presentation.value.entries)
        assertEquals(3, presentation.value.generation)
    }
}
