package yos.music.player.ui.widgets.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yos.music.player.data.libraries.SettingsLibrary.NowplayingBackgroundEffect
import yos.music.player.data.objects.NowPlayingBackgroundState

/**
 * 播放页背景组件。
 * 静态模式：保留相同柔化色场与曝光，停止形变帧循环。
 * 动态模式：预柔化封面纹理 + AGSL 连续形变，不做逐帧大范围模糊。
 */
@Composable
fun YosFloatingLight(
    modifier: Modifier,
    backgroundState: () -> NowPlayingBackgroundState,
    isPlaying: () -> Boolean,
    nowPage: () -> String,
    showMiniPlayer: () -> Boolean
) {
    val context = LocalContext.current
    val dynamicEnabled = NowplayingBackgroundEffect

    val assetCache = remember { mutableStateMapOf<String, BackgroundAssets>() }
    val settledFrame = remember { mutableStateOf(BackgroundRenderFrame.fallback(trackKey = null)) }
    val imageLoader = remember { ImageLoader.Builder(context).crossfade(true).build() }

    val background = backgroundState()
    val trackKey = background.trackKey
    val requestKey = background.requestKey

    LaunchedEffect(trackKey, requestKey) {
        if (background.artworkUri == null || requestKey == null) {
            settledFrame.value = BackgroundRenderFrame.fallback(trackKey)
            return@LaunchedEffect
        }

        assetCache[requestKey]?.let { cached ->
            settledFrame.value = BackgroundRenderFrame.asset(trackKey, requestKey, cached)
            return@LaunchedEffect
        }

        val result = loadBackgroundAssets(
            context = context,
            imageLoader = imageLoader,
            uri = background.artworkUri,
            key = requestKey
        )

        if (result != null) {
            // Bound retained cover textures instead of accumulating every played track.
            if (assetCache.size >= 6) assetCache.keys.firstOrNull()?.let { assetCache.remove(it) }
            assetCache[requestKey] = result
            settledFrame.value = BackgroundRenderFrame.asset(trackKey, requestKey, result)
        } else {
            settledFrame.value = BackgroundRenderFrame.fallback(trackKey)
        }
    }

    DisposableEffect(imageLoader) {
        onDispose { imageLoader.shutdown() }
    }

    val displayFrame = settledFrame.value

    Box(modifier = modifier) {
        if (Build.VERSION.SDK_INT >= 33) {
            // Keep one renderer across track/page changes so its motion never restarts.
            val fallbackTexture = remember {
                Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.rgb(28, 30, 38))
                }
            }
            FlowingArtworkBackground(displayFrame.flow ?: fallbackTexture, isPlaying, showMiniPlayer,
                motionEnabled = dynamicEnabled)
        } else Crossfade(
            targetState = displayFrame,
            animationSpec = tween(700, easing = FastOutSlowInEasing)
        ) { target ->
            val texture = target.flow
            if (texture != null) StaticFlowLayer(texture.asImageBitmap()) else FallbackLayer()
        }

        // Apply the same exposure reduction to dynamic and static artwork.
        // This sits inside the background, never over controls or cover art.
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.15f)) })
    }
}

/** Older devices use the same soft colour texture and exposure without AGSL motion. */
@Composable
private fun StaticFlowLayer(bitmap: ImageBitmap) {
    Box(Modifier.fillMaxSize()) {
        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f),
                Color.Black.copy(alpha = 0.41f))))
        })
    }
}

@Composable
private fun FallbackLayer() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF18181B),
                            Color(0xFF111827),
                            Color(0xFF09090B)
                        )
                    )
                )
                drawRect(Color(0x66000000))
            }
    )
}

private data class BackgroundAssets(
    val key: String? = null,
    val flow: Bitmap
)

private data class BackgroundRenderFrame(
    val frameKey: String,
    val trackKey: String?,
    val flow: Bitmap? = null
) {
    companion object {
        fun fallback(trackKey: String?) = BackgroundRenderFrame(
            frameKey = "fallback:${trackKey ?: "none"}",
            trackKey = trackKey
        )

        fun asset(trackKey: String?, requestKey: String, assets: BackgroundAssets) = BackgroundRenderFrame(
            frameKey = "asset:$requestKey",
            trackKey = trackKey,
            flow = assets.flow
        )
    }
}

private suspend fun loadBackgroundAssets(
    context: Context,
    imageLoader: ImageLoader,
    uri: Uri,
    key: String?
): BackgroundAssets? {
    val targetSize = 224
    return withContext(Dispatchers.Default) {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(targetSize)
            .allowHardware(false)
            .build()
        val source = imageLoader.execute(request).drawable?.toBitmap()
            ?.let { toSoftware(it) } ?: return@withContext null

        val scale = targetSize.toFloat() / maxOf(source.width, source.height)
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
        BackgroundAssets(
            key = key,
            flow = prepareFlowTexture(scaled)
        )
    }
}

/** Compact, opaque colour field: retain cover colours but remove recognisable details. */
internal fun prepareFlowTexture(source: Bitmap): Bitmap {
    val square = Bitmap.createScaledBitmap(source, 96, 96, true)
    val opaque = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    Canvas(opaque).apply {
        drawColor(android.graphics.Color.rgb(28, 28, 32))
        drawBitmap(square, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.7f) })
        })
    }
    if (square !== source) square.recycle()
    val firstPass = Toolkit.blur(opaque, 20)
    opaque.recycle()
    return Toolkit.blur(firstPass, 20).also { firstPass.recycle() }
}

private fun toSoftware(bitmap: Bitmap): Bitmap =
    if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE)
        bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap

// region === Home.kt 兼容 ===

/**
 * 首页背景模糊（保留兼容）。原始实现，供 Home.kt 等非播放页场景使用。
 */
fun imageResolve(image: Bitmap, moreLight: Boolean = false): Bitmap {
    var resizedBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
    android.graphics.Canvas(resizedBitmap).let { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
            val matrix = ColorMatrix()
            matrix.setSaturation(1.5f)
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(resizedBitmap, 0f, 0f, paint)
        if (moreLight) {
            canvas.drawColor((0x1AFFFFFF).toInt())
            canvas.drawColor((0xFFFFFFFF).toInt(), PorterDuff.Mode.OVERLAY)
            canvas.drawColor((0x52FFFFFF).toInt())
            canvas.drawColor((0xBFFFFFFF).toInt(), PorterDuff.Mode.OVERLAY)
        } else {
            canvas.drawColor((0x33000000).toInt(), PorterDuff.Mode.OVERLAY)
            canvas.drawColor((0x40000000).toInt())
        }
    }
    resizedBitmap = Toolkit.blur(Toolkit.blur(resizedBitmap, 25), 25)
    return resizedBitmap
}

// endregion
