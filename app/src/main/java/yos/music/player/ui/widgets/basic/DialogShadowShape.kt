package yos.music.player.ui.widgets.basic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

internal class DialogShadowShape(
    private val surfaceShape: Shape,
    private val inset: Dp,
    private val sheetOffset: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val padding = with(density) { inset.toPx() }
        val surfaceSize = Size(
            (size.width - padding * 2f).coerceAtLeast(0f),
            (size.height - padding * 2f).coerceAtLeast(0f)
        )
        val outline = surfaceShape.createOutline(surfaceSize, layoutDirection, density)
        return Outline.Generic(Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
            // Material positions its clipped Surface inside the outer shadow layer.
            translate(Offset(padding, padding + sheetOffset))
        })
    }
}
