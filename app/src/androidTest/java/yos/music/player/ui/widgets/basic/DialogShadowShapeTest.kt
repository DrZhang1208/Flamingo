package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import yos.music.player.ui.theme.YosRoundedCornerShape

class DialogShadowShapeTest {
    @Test fun shadowMatchesTheInsetCardDuringShowingAndHidingAtDifferentDensities() {
        for (density in listOf(1f, 2.75f, 3.5f)) {
            for (offset in listOf(0f, 150f, 800f)) {
                val outline = DialogShadowShape(RectangleShape, 5.dp, offset)
                    .createOutline(Size(300f * density, 400f * density), LayoutDirection.Ltr, Density(density))
                val bounds = (outline as Outline.Generic).path.getBounds()
                assertEquals(5f * density, bounds.left, 0.001f)
                assertEquals(295f * density, bounds.right, 0.001f)
                assertEquals(offset + 5f * density, bounds.top, 0.001f)
                assertEquals(offset + 395f * density, bounds.bottom, 0.001f)
            }
        }
    }

    @Test fun roundedAndFlamingoOutlinesRetainTheirShapeAndFollowMaterialOffset() {
        val shapes = listOf(RoundedCornerShape(20.dp), YosRoundedCornerShape(20.dp))
        for (shape in shapes) {
            for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                val outline = DialogShadowShape(shape, 5.dp, 123f)
                    .createOutline(Size(300f, 400f), direction, Density(1f))
                val bounds = (outline as Outline.Generic).path.getBounds()
                assertEquals(5f, bounds.left, 0.001f)
                assertEquals(295f, bounds.right, 0.001f)
                assertEquals(128f, bounds.top, 0.001f)
                assertEquals(518f, bounds.bottom, 0.001f)
            }
        }
    }
}
