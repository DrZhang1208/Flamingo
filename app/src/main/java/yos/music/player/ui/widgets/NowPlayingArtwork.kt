package yos.music.player.ui.widgets

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import yos.music.player.R
import kotlin.math.max
import kotlin.math.roundToInt

/** Resolved images only: drawing at two shared-element sizes cannot restart loading. */
internal class ArtworkBlendPainter(
    private val previous: Painter,
    private val next: Painter,
    private val progress: () -> Float
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        val fraction = progress().coerceIn(0f, 1f)
        if (previous === next || fraction >= 1f) crop(next, 1f)
        else {
            // Keep the old image opaque underneath, avoiding a dark mid-fade frame.
            crop(previous, 1f)
            if (fraction > 0f) crop(next, fraction)
        }
    }

    private fun DrawScope.crop(painter: Painter, alpha: Float) {
        val intrinsic = painter.intrinsicSize
        val target = if (intrinsic != Size.Unspecified &&
            intrinsic.width.isFinite() && intrinsic.height.isFinite() &&
            intrinsic.width > 0f && intrinsic.height > 0f) {
            intrinsic * max(size.width / intrinsic.width, size.height / intrinsic.height)
        } else size
        clipRect {
            translate((size.width - target.width) / 2f, (size.height - target.height) / 2f) {
                with(painter) { draw(target, alpha = alpha) }
            }
        }
    }
}

private data class ArtworkLayers(
    val previous: Painter,
    val next: Painter,
    val progress: Animatable<Float, AnimationVector1D> = Animatable(1f)
)

internal fun nowPlayingArtworkRequest(context: android.content.Context, data: Any?, width: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(data ?: R.drawable.placeholder_music_default_artwork)
        .size(width).precision(Precision.INEXACT)
        .crossfade(false)
        .placeholder(R.drawable.placeholder_music_default_artwork)
        .error(R.drawable.placeholder_music_default_artwork)
        .build()

/** One loading request and one interruptible fade belong to the whole player. */
@Composable
internal fun rememberNowPlayingArtwork(
    data: Any?, imageLoader: ImageLoader = LocalContext.current.imageLoader
): Painter {
    val context = LocalContext.current
    val width = (LocalConfiguration.current.screenWidthDp * LocalDensity.current.density).roundToInt().coerceAtLeast(1)
    val request = remember(context, data, width) {
        nowPlayingArtworkRequest(context, data, width)
    }
    val loaderPainter = rememberAsyncImagePainter(request, imageLoader = imageLoader)
    val fallback = painterResource(R.drawable.placeholder_music_default_artwork)
    val loaded = when (val state = loaderPainter.state) {
        is AsyncImagePainter.State.Success -> state.painter
        is AsyncImagePainter.State.Error -> state.painter ?: fallback
        else -> null
    }
    var layers by remember { mutableStateOf(ArtworkLayers(fallback, fallback)) }
    var hasArtwork by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        if (loaded != null) {
            // A restored cover is an initial state, not a track-change animation.
            if (!hasArtwork) {
                layers = ArtworkLayers(loaded, loaded)
                hasArtwork = data != null && loaderPainter.state is AsyncImagePainter.State.Success
                return@LaunchedEffect
            }
            if (loaded !== layers.next) {
                // Each transition owns its clock: resetting a shared clock would redraw
                // the previous surface at zero before recomposition installs the snapshot.
                val current = layers
                val frozen = current.progress.value
                val snapshot = ArtworkBlendPainter(current.previous, current.next) { frozen }
                layers = ArtworkLayers(snapshot, loaded, Animatable(0f))
            }
            val current = layers
            if (current.progress.value < 1f) {
                current.progress.animateTo(1f, tween(300))
                layers = ArtworkLayers(loaded, loaded)
            }
        }
    }
    return remember(layers) {
        val current = layers
        ArtworkBlendPainter(current.previous, current.next) { current.progress.value }
    }
}
