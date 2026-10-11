package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class PlayerWindowLayoutTest {
    @Test fun shortLandscapeFreeformWindowUsesTwoPanes() {
        assertEquals(PlayerWindowLayout(compact = true, split = true), playerWindowLayout(640f, 360f))
        assertEquals(PlayerWindowLayout(compact = true, split = true), playerWindowLayout(480f, 270f))
    }

    @Test fun shortPortraitWindowKeepsSingleColumnWithCompactControls() {
        assertEquals(PlayerWindowLayout(compact = true, split = false), playerWindowLayout(360f, 540f))
    }

    @Test fun fullPhoneAndTallTabletKeepExistingPresentation() {
        assertEquals(PlayerWindowLayout(compact = false, split = false), playerWindowLayout(360f, 800f))
        assertEquals(PlayerWindowLayout(compact = false, split = false), playerWindowLayout(1000f, 700f))
    }
}
