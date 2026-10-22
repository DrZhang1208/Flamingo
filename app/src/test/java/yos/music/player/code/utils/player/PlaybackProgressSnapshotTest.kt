package yos.music.player.code.utils.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressSnapshotTest {
    @Test fun restoredPositionAndDurationAreAvailableBeforeControllerConnects() {
        assertEquals(PlaybackProgressSnapshot(42000, 180000),
            playbackProgressSnapshot(null, null, 42000, 180000))
    }

    @Test fun anActualSeekToZeroDoesNotFallBackToOldSavedPosition() {
        assertEquals(PlaybackProgressSnapshot(0, 180000),
            playbackProgressSnapshot(0, 180000, 42000, 180000))
    }

    @Test fun liveSessionWinsAndUnknownPlayerDurationUsesSongMetadata() {
        assertEquals(PlaybackProgressSnapshot(48000, 180000),
            playbackProgressSnapshot(48000, -9223372036854775807L, 42000, 180000))
        assertEquals(PlaybackProgressSnapshot(48000, 200000),
            playbackProgressSnapshot(48000, 200000, 42000, 180000))
    }

    @Test fun malformedSavedPositionsAreClampedWithoutDiscardingUnknownDuration() {
        assertEquals(PlaybackProgressSnapshot(180000, 180000),
            playbackProgressSnapshot(null, null, 200000, 180000))
        assertEquals(PlaybackProgressSnapshot(0, 180000),
            playbackProgressSnapshot(null, null, -100, 180000))
        assertEquals(PlaybackProgressSnapshot(42000, 0),
            playbackProgressSnapshot(null, null, 42000, 0))
    }
}
