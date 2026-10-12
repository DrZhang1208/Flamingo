package yos.music.player.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun seekCancelArcTop(width: Float, height: Float, x: Float, activation: Float = 0f): Float {
    val t = 2f * (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f) - 1f
    val rise = height * 0.06f * activation.coerceIn(0f, 1f)
    return -rise + (height * 0.55f + rise) * t * t
}

/** Hit the painted arc, including its animated translation, rather than a rectangle. */
internal class SeekCancelArcHitRegion {
    private var coordinates: LayoutCoordinates? = null
    private var activation: () -> Float = { 0f }
    fun update(value: LayoutCoordinates, activation: () -> Float = { 0f }) {
        coordinates = value
        this.activation = activation
    }
    fun contains(root: Offset): Boolean {
        val layout = coordinates?.takeIf { it.isAttached } ?: return false
        val local = layout.localPositionOf(layout.findRootCoordinates(), root)
        val width = layout.size.width.toFloat()
        val height = layout.size.height.toFloat()
        return local.x in 0f..width && local.y in seekCancelArcTop(width, height, local.x, activation())..height
    }
}

@Composable
internal fun SeekCancelArc(visible: Boolean, cancelled: Boolean, compact: Boolean,
    region: SeekCancelArcHitRegion, modifier: Modifier = Modifier) {
    // Spatial springs preserve velocity when a quick drag reverses an unfinished animation.
    // The large surface is critically damped; only the small icon has a little rebound.
    val progress = animateFloatAsState(if (visible) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 700f, visibilityThreshold = 0.001f),
        label = "seek cancel entrance")
    val activation = animateFloatAsState(if (cancelled) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 950f, visibilityThreshold = 0.001f),
        label = "seek cancel surface response")
    val iconResponse = animateFloatAsState(if (cancelled) 1f else 0f,
        spring(dampingRatio = 0.68f, stiffness = 750f, visibilityThreshold = 0.001f),
        label = "seek cancel icon response")
    Box(modifier.fillMaxWidth().height(if (compact) 63.dp else 87.dp)
        .graphicsLayer { translationY = size.height * (1f - progress.value) }
        .onGloballyPositioned { region.update(it) { activation.value } },
        contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxSize().drawWithCache {
            val edge = size.height * 0.55f
            val path = Path()
            val surface = Brush.verticalGradient(listOf(Color(0xFFF6F2EB), Color(0xFFE7E1D7)))
            onDrawBehind {
                val rise = size.height * 0.06f * activation.value.coerceIn(0f, 1f)
                // Lift the centre while keeping the base sealed against the screen bottom.
                // Reuse the path and gradient; animation only invalidates drawing.
                path.rewind()
                path.moveTo(0f, edge)
                path.quadraticTo(size.width / 2f, -edge - 2f * rise, size.width, edge)
                path.lineTo(size.width, size.height)
                path.lineTo(0f, size.height)
                path.close()
                drawPath(path, surface)
            }
        })
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding().padding(bottom = if (compact) 6.dp else 16.dp)) {
            SeekCancelUndoIcon(activation, iconResponse)
            Text("撤销进度调整", color = Color(0xFF302C27), fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SeekCancelUndoIcon(activation: State<Float>, response: State<Float>) {
    Box(Modifier.size(36.dp)
        .graphicsLayer {
            scaleX = 1f + response.value * 0.12f
            scaleY = scaleX
            rotationZ = -18f * response.value
            translationY = -2.dp.toPx() * response.value
        }
        .drawBehind {
            drawCircle(androidx.compose.ui.graphics.lerp(
                Color(0xFFE0D9CE), Color(0xFFAD3D39), activation.value.coerceIn(0f, 1f)))
        }, contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null,
            tint = androidx.compose.ui.graphics.lerp(
                Color(0xFFAD3D39), Color.White, activation.value.coerceIn(0f, 1f)),
            modifier = Modifier.size(21.dp))
    }
}
