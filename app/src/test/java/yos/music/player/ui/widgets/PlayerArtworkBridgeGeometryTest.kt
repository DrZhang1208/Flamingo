package yos.music.player.ui.widgets

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class PlayerArtworkBridgeGeometryTest {
    private val mini = Rect(8f, 7f, 55f, 54f)
    private val album = Rect(32f, 180f, 332f, 480f)

    @Test fun sheetOvershootKeepsExactMiniAndAlbumEndpoints() {
        assertEquals(mini, playerArtworkBounds(mini, album, -0.1f))
        assertEquals(mini, playerArtworkBounds(mini, album, 0f))
        assertEquals(album, playerArtworkBounds(mini, album, 1f))
        assertEquals(album, playerArtworkBounds(mini, album, 1.1f))
    }

    @Test fun reversingADragRetracesTheSameCoverWithoutResettingItsSize() {
        val opening = (0..100).map { playerArtworkBounds(mini, album, it / 100f) }
        opening.zipWithNext().forEach { (before, after) ->
            assertTrue(after.width >= before.width)
            assertEquals(after.width, after.height, 0.001f)
            assertTrue(after.top >= before.top)
        }
        for (step in 65 downTo 0) {
            assertEquals(opening[step], playerArtworkBounds(mini, album, step / 100f))
        }
    }
    @Test fun pauseKeepsBothCenterCoordinatesFixedThroughoutTheMotion() {
        for (step in 0..100) {
            val rect = pausedArtworkBounds(album, step / 100f, 37f)
            assertEquals(album.center.x, rect.center.x, 0.0001f)
            assertEquals(album.center.y, rect.center.y, 0.0001f)
            assertEquals(rect.width, rect.height, 0.0001f)
        }
        assertEquals(226f, pausedArtworkBounds(album, 1f, 37f).width, 0.0001f)
    }

    @Test fun smallCoverRetainsSafeSizeAndSubpixelPauseMovement() {
        val small = Rect(10f, 20f, 110f, 120f)
        assertEquals(75f, pausedArtworkBounds(small, 1f, 37f).width, 0.0001f)
        val frame = pausedArtworkBounds(small, 0.001f, 37f)
        assertTrue(frame.left > small.left && frame.left < small.left + 1f)
        assertEquals(small.center.y, frame.center.y, 0.0001f)
    }

    @Test fun playbackOvershootIsSmallAndKeepsTheCoverCentered() {
        val overshoot = pausedArtworkBounds(album, -0.08f, 37f)
        assertEquals(album.center.x, overshoot.center.x, 0.0001f)
        assertEquals(album.center.y, overshoot.center.y, 0.0001f)
        assertTrue(overshoot.width > album.width && overshoot.width < album.width * 1.025f)
        assertEquals(overshoot, pausedArtworkBounds(album, -1f, 37f))
    }

}
