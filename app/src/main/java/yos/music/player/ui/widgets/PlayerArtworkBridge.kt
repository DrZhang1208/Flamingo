package yos.music.player.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import yos.music.player.ui.theme.YosRoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Bounds are in the moving sheet's own coordinates, so dragging/reversing never
 * starts a second animation or interpolates against a moving screen origin. */
internal class PlayerElementAnchors {
    private var owner: LayoutCoordinates? = null
    private val coordinates = mutableMapOf<String, LayoutCoordinates>()
    internal val bounds = mutableStateMapOf<String, Rect>()
    internal fun owner(coordinates: LayoutCoordinates) {
        owner = coordinates
        this.coordinates.forEach { (key, child) -> update(key, child) }
    }
    internal fun record(key: String, coordinates: LayoutCoordinates) {
        this.coordinates[key] = coordinates
        update(key, coordinates)
    }
    private fun update(key: String, child: LayoutCoordinates) {
        val parent = owner ?: return
        if (!parent.isAttached || !child.isAttached) return
        val rect = parent.localBoundingBoxOf(child, clipBounds = false)
        if (rect.width > 0f && rect.height > 0f) bounds[key] = rect
    }
}

class PlayerArtworkBridge(internal val progress: () -> Float) {
    internal var pauseProgress: () -> Float = { 0f }
    private val anchors = PlayerElementAnchors()
    internal val bounds get() = anchors.bounds
    internal fun owner(coordinates: LayoutCoordinates) = anchors.owner(coordinates)
    internal fun record(key: String, coordinates: LayoutCoordinates) = anchors.record(key, coordinates)
}

internal const val MINI_ARTWORK_ENDPOINT = "mini"
internal const val ALBUM_ARTWORK_ENDPOINT = "album"
internal const val COMPACT_ARTWORK_ENDPOINT = "compact"

internal fun playerArtworkBounds(mini: Rect, expanded: Rect, progress: Float): Rect =
    lerp(mini, expanded, progress.coerceIn(0f, 1f))

/** Fixed center, floating-point inset: the pause clock never changes layout. */
internal fun pausedArtworkBounds(bounds: Rect, progress: Float, maxInset: Float): Rect {
    val inset = minOf(maxInset, bounds.width * 0.125f) * progress.coerceIn(-0.08f, 1f)
    return Rect(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
}

@Composable
internal fun PlayerArtworkBridgeOverlay(
    bridge: PlayerArtworkBridge, artwork: Painter, destination: () -> String,
    modifier: Modifier = Modifier
) {
    // Page changes animate once; playback already supplies animated bounds.
    // Applying another spring to every measured playback frame causes visible lag.
    val transition = remember { Animatable(1f, visibilityThreshold = 0.00001f) }
    var source by remember { mutableStateOf(Rect.Zero) }
    var initialized by remember { mutableStateOf(false) }
    val endpoint = destination()
    val target = bridge.bounds[endpoint]
    var previousEndpoint by remember { mutableStateOf<String?>(null) }
    val lastFull = remember { arrayOf(Rect.Zero) }
    LaunchedEffect(endpoint, target != null) {
        if (target != null) {
            if (!initialized) {
                source = target
                initialized = true
            } else if (previousEndpoint != endpoint) {
                source = lastFull[0]
                transition.snapTo(0f)
                previousEndpoint = endpoint
                transition.animateTo(1f, spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = 0.00001f))
            }
            previousEndpoint = endpoint
        }
    }
    Box(modifier.onGloballyPositioned { bridge.owner(it) }) {
        Canvas(Modifier.fillMaxSize()
            .layout { measurable, constraints ->
                // One stable drawing surface. Motion lives in the graphics layer,
                // preserving subpixel movement instead of rounding each frame's size.
                val side = constraints.maxWidth.coerceAtLeast(1)
                val child = measurable.measure(Constraints.fixed(side, side))
                layout(constraints.maxWidth, constraints.maxHeight) { child.place(0, 0) }
            }
            .graphicsLayer {
                val progress = bridge.progress().coerceIn(0f, 1f)
                val mini = bridge.bounds[MINI_ARTWORK_ENDPOINT]
                val endpoint = destination()
                val measuredTarget = bridge.bounds[endpoint]
                val liveTarget = measuredTarget?.let {
                    if (endpoint == ALBUM_ARTWORK_ENDPOINT)
                        pausedArtworkBounds(it, bridge.pauseProgress(), 37.dp.toPx()) else it
                }
                val destinationRect = if (initialized && endpoint != previousEndpoint) lastFull[0]
                else if (initialized && liveTarget != null)
                    playerArtworkBounds(source, liveTarget, transition.value) else mini ?: Rect.Zero
                lastFull[0] = destinationRect
                val rect = if (mini != null) playerArtworkBounds(mini, destinationRect, progress) else destinationRect
                val scale = (rect.width / size.width).coerceAtLeast(0.00001f)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
                translationX = rect.left
                translationY = rect.top
                alpha = if (rect.width > 0f) 1f else 0f
                // The artwork alone is clipped in the draw pass. Keeping this
                // shared layer unclipped allows its own shadow to extend outside.
                clip = false
            }
        ) {
            if (initialized || bridge.bounds[MINI_ARTWORK_ENDPOINT] != null) {
                val progress = bridge.progress().coerceIn(0f, 1f)
                // Use the exact rectangle from the visible artwork transform.
                // Shadow and cover now share one node, origin and animation clock.
                val mini = bridge.bounds[MINI_ARTWORK_ENDPOINT]
                val endpoint = destination()
                val liveTarget = bridge.bounds[endpoint]?.let {
                    if (endpoint == ALBUM_ARTWORK_ENDPOINT)
                        pausedArtworkBounds(it, bridge.pauseProgress(), 37.dp.toPx()) else it
                }
                val fullRect = if (initialized && endpoint != previousEndpoint) lastFull[0]
                    else if (initialized && liveTarget != null) playerArtworkBounds(source, liveTarget, transition.value)
                    else mini ?: Rect.Zero
                val visible = if (mini != null) playerArtworkBounds(mini, fullRect, progress) else fullRect
                val scale = (visible.width / size.width).coerceAtLeast(0.00001f)
                val radius = 6.dp.toPx() / scale * (1f - progress) + size.minDimension * 0.04f * progress
                drawPlayerArtworkShadow(size, progress * 0.5f)
                val outline = YosRoundedCornerShape(
                    roundSize = radius.coerceAtMost(size.minDimension / 2f).toDp(),
                    n = 1f - 0.5f * progress
                ).createOutline(size, layoutDirection, this)
                val path = when (outline) {
                    is Outline.Generic -> outline.path
                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                }
                clipPath(path) { with(artwork) { draw(size) } }
            }
        }
    }
}

/** Draw in the cover's local surface before its clipped image, never in a sibling
 * whose coordinate invalidation can differ from the animated graphics layer. */
private object ArtworkShadowMask {
    const val SIDE = 256f
    const val PAD = 80f
    // Software Gaussian blur is prepared once. Page/playback frames only scale
    // this texture with the cover; no bitmap creation or blur work during motion.
    val bitmap: android.graphics.Bitmap by lazy {
        android.graphics.Bitmap.createBitmap(416, 416, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            fun lobe(blur: Float, offset: Float, alpha: Int) {
                paint.color = android.graphics.Color.argb(alpha, 0, 0, 0)
                paint.maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
                canvas.drawRoundRect(android.graphics.RectF(PAD, PAD + offset,
                    PAD + SIDE, PAD + SIDE + offset), 10f, 10f, paint)
            }
            lobe(18f, 14f, 50)
            lobe(7f, 8f, 24)
        }
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
}

internal fun DrawScope.drawPlayerArtworkShadow(coverSize: Size, opacity: Float) {
    if (opacity <= 0f) return
    val padX = coverSize.width * ArtworkShadowMask.PAD / ArtworkShadowMask.SIDE
    val padY = coverSize.height * ArtworkShadowMask.PAD / ArtworkShadowMask.SIDE
    ArtworkShadowMask.paint.alpha = (255f * opacity.coerceIn(0f, 1f)).toInt()
    drawIntoCanvas {
        it.nativeCanvas.drawBitmap(ArtworkShadowMask.bitmap, null,
            android.graphics.RectF(-padX, -padY, coverSize.width + padX, coverSize.height + padY),
            ArtworkShadowMask.paint)
    }
}
