package yos.music.player.ui.widgets.effects

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import android.graphics.Shader
import android.os.Build
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flaviofaria.kenburnsview.KenBurnsView
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.code.utils.others.BitmapResolver
import yos.music.player.data.libraries.SettingsLibrary.NowplayingBackgroundEffect
import yos.music.player.ui.pages.NowPlayingPage
import yos.music.player.ui.widgets.basic.YosWrapper

@Stable
private enum class Option {
    Set,
    Pause,
    Resume,
    Init
}

@Composable
fun YosFloatingLight(
    modifier: Modifier,
    album: () -> Uri?,
    isPlaying: () -> Boolean,
    nowPage: () -> String,
    showMiniPlayer: () -> Boolean
) {
    val context = LocalContext.current
    val processedDrawable = remember { mutableStateOf<Drawable?>(null) }
    val displayAlbum = remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(album()) { album()?.let { displayAlbum.value = it } }
    val imageLoader = remember { ImageLoader.Builder(context).crossfade(true).build() }

    LaunchedEffect(displayAlbum.value) {
        val uri = displayAlbum.value ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val request = ImageRequest.Builder(context).data(uri).size(256).build()
            val bitmap = imageLoader.execute(request).drawable?.toBitmap() ?: return@withContext
            val processed = imageResolve(bitmap)
            val drawable = BitmapDrawable(context.resources, processed)
            withContext(Dispatchers.Main) { processedDrawable.value = drawable }
        }
    }

    val bgBitmap = remember { mutableStateOf<ImageBitmap?>(value = null) }

    LaunchedEffect(processedDrawable.value) {
        val drawable = processedDrawable.value ?: return@LaunchedEffect
        val bitmap = withContext(Dispatchers.Default) {
            drawable.toBitmap().asImageBitmap()
        }
        withContext(Dispatchers.Main) {
            bgBitmap.value = bitmap
        }
    }

    YosWrapper {
        val useBackground = remember { derivedStateOf { bgBitmap.value == null } }

        Box(modifier = modifier) {
            Crossfade(
                targetState = bgBitmap.value,
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            ) { bg ->
                if (bg != null) {
                    Image(bitmap = bg, contentDescription = null, contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.tint(Color(0x33000000), BlendMode.Overlay),
                        modifier = Modifier.fillMaxSize())
                }
            }
            Box(modifier = Modifier.fillMaxSize().drawWithCache {
                onDrawBehind {
                    if (useBackground.value) drawRect(Color.Black)
                    drawRect(Color(0x40000000), blendMode = BlendMode.Overlay)
                }
            })
        }
    }
}

@Composable
private fun KenBurnsBackground(
    drawable: Drawable?,
    useBackground: Boolean,
    modifier: Modifier = Modifier,
    isPlaying: () -> Boolean = { true },
    active: Boolean = true
) {
    val lastOption = remember { mutableStateOf(Option.Init.name) }
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val isActive = lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED) && active
    AndroidView(factory = {
        KenBurnsView(it).apply {
            setTransitionGenerator(
                RandomTransitionGenerator(12000, AccelerateDecelerateInterpolator())
            )
        }
    }, modifier = modifier.drawWithCache {
        onDrawBehind { if (useBackground) drawRect(Color.Black) }
    }) {
        if (drawable != null) {
            if (it.drawable != drawable) {
                if (lastOption.value == Option.Set.name) return@AndroidView
                it.setImageDrawable(drawable)
                lastOption.value = Option.Set.name
            } else if (!isPlaying() || !isActive) {
                if (lastOption.value == Option.Pause.name) return@AndroidView
                it.pause()
                lastOption.value = Option.Pause.name
            } else {
                if (lastOption.value == Option.Resume.name) return@AndroidView
                it.resume()
                lastOption.value = Option.Resume.name
            }
        }
    }
}

fun imageResolve(image: Bitmap, moreLight: Boolean = false): Bitmap {
    var resizedBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
    resizedBitmap.applyCanvas {
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.isDither = true

        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(1.5f)

        paint.colorFilter = ColorMatrixColorFilter(saturationMatrix)
        drawBitmap(resizedBitmap, 0f, 0f, paint)

        if (moreLight) {
            drawColor((0x1AFFFFFF).toInt())
            drawColor((0xFFFFFFFF).toInt(), PorterDuff.Mode.OVERLAY)
            drawColor((0x52FFFFFF).toInt())
            drawColor((0xBFFFFFFF).toInt(), PorterDuff.Mode.OVERLAY)
        } else {
            drawColor((0x33000000).toInt(), PorterDuff.Mode.OVERLAY)
            drawColor((0x40000000).toInt())
        }
    }
    resizedBitmap = Toolkit.blur(resizedBitmap, 25)
    return resizedBitmap
}