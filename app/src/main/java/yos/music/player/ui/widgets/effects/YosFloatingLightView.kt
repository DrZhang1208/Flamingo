package yos.music.player.ui.widgets.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import yos.music.player.data.libraries.SettingsLibrary.NowplayingBackgroundEffect
import yos.music.player.data.objects.NowPlayingBackgroundState

/**
 * 播放页背景组件。
 * 静态模式：保留相同柔化色场与曝光，停止形变帧循环。
 * 动态模式：四层等比封面 + AGSL 扭曲 + GPU 模糊，最后调整曝光并消除色阶。
 */
@Composable
fun YosFloatingLight(
    modifier: Modifier,
    backgroundState: () -> NowPlayingBackgroundState,
    isPlaying: () -> Boolean,
    nowPage: () -> String,
    showMiniPlayer: () -> Boolean,
    expansionProgress: (() -> Float)? = null
) {
    val context = LocalContext.current
    val dynamicEnabled = NowplayingBackgroundEffect

    val background = backgroundState()
    val trackKey = background.trackKey
    val requestKey = background.requestKey
    val settledFrame = remember {
        mutableStateOf(requestKey?.let { key -> backgroundAssetCache.peek(key)?.let {
            BackgroundRenderFrame.asset(trackKey, key, it)
        } } ?: BackgroundRenderFrame.fallback(trackKey))
    }

    LaunchedEffect(trackKey, requestKey) {
        if (background.artworkUri == null || requestKey == null) {
            settledFrame.value = BackgroundRenderFrame.fallback(trackKey)
            return@LaunchedEffect
        }

        val result = loadBackgroundAssets(
            context = context,
            uri = background.artworkUri,
            key = requestKey
        )

        if (result != null) {
            settledFrame.value = BackgroundRenderFrame.asset(trackKey, requestKey, result)
        } else {
            settledFrame.value = BackgroundRenderFrame.fallback(trackKey)
        }
    }

    val displayFrame = settledFrame.value

    Box(modifier = modifier) {
        if (Build.VERSION.SDK_INT >= 33) {
            // Keep one renderer across track/page changes so its motion never restarts.
            val fallbackTexture = remember {
                Bitmap.createBitmap(ARTWORK_TEXTURE_SIZE, ARTWORK_TEXTURE_SIZE, Bitmap.Config.RGBA_F16).apply {
                    eraseColor(android.graphics.Color.rgb(28, 30, 38))
                }
            }
            FlowingArtworkBackground(displayFrame.flow ?: fallbackTexture, isPlaying, showMiniPlayer,
                motionEnabled = dynamicEnabled, expansionProgress = expansionProgress)
        } else {
            val fallbackTexture = remember {
                Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.rgb(28, 30, 38))
                }
            }
            StaticArtworkBackground(displayFrame.flow ?: fallbackTexture, isPlaying,
                showMiniPlayer, expansionProgress)
        }

        // The GPU path applies exposure before its final dither pass. A later
        // dark overlay would quantize that carefully distributed noise again.
        if (Build.VERSION.SDK_INT < 33) {
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.15f)) })
        }
    }
}

@Composable
private fun StaticArtworkBackground(
    texture: Bitmap,
    isPlaying: () -> Boolean,
    showMiniPlayer: () -> Boolean,
    expansionProgress: (() -> Float)?
) {
    val mode = rememberArtworkTransitionMode(isPlaying, showMiniPlayer, expansionProgress)
    val layers = rememberArtworkLayers(texture, mode, 700)
    Box(Modifier.fillMaxSize()) {
        StaticFlowLayer(layers.previous.asImageBitmap())
        if (layers.previous !== layers.next) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = layers.progress.value }) {
                StaticFlowLayer(layers.next.asImageBitmap())
            }
        }
    }
}

/** Older devices use the same soft colour texture and exposure without AGSL motion. */
@Composable
private fun StaticFlowLayer(bitmap: ImageBitmap) {
    Box(Modifier.fillMaxSize()) {
        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop,
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

// Reopening the player reuses unmodified F16 artwork. Evicted textures can still
// be displayed, so let their owners/GC release them rather than recycling here.
private val backgroundAssetScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
private val backgroundAssetCache = BackgroundAssetStore<BackgroundAssets>(16, backgroundAssetScope)
private val backgroundPreloadLock = Any()
private var backgroundPreloadJob: Job? = null

/**
 * 切歌后预热动态背景纹理：打开播放页时直接命中缓存，避免现场解码+生成
 * 纹理造成的首帧卡顿。防抖 400ms 吸收连续切歌风暴，仅预热最终停留曲目。
 */
internal fun preloadNowPlayingBackground(context: Context, trackKey: String?, artworkUri: Uri?) {
    val appContext = context.applicationContext
    synchronized(backgroundPreloadLock) {
        // Also cancel stale preloads when the new item is cached or has no artwork.
        backgroundPreloadJob?.cancel()
        backgroundPreloadJob = null
        if (trackKey == null || artworkUri == null) return
        val requestKey = "$trackKey|$artworkUri"
        if (backgroundAssetCache.peek(requestKey) != null) return
        backgroundPreloadJob = backgroundAssetScope.launch {
            kotlinx.coroutines.delay(400)
            loadBackgroundAssets(appContext, artworkUri, requestKey)
        }
    }
}

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
    uri: Uri,
    key: String
): BackgroundAssets? {
    val appContext = context.applicationContext
    val targetSize = ARTWORK_TEXTURE_SIZE
    return backgroundAssetCache.getOrLoad(key) {
        val request = ImageRequest.Builder(appContext)
            .data(uri)
            .size(targetSize)
            .allowHardware(false)
            .build()
        val drawable = Coil.imageLoader(appContext).execute(request).drawable
            ?: return@getOrLoad null
        currentCoroutineContext().ensureActive()
        val source = toSoftware(drawable.toBitmap())
        currentCoroutineContext().ensureActive()

        val texture = if (Build.VERSION.SDK_INT >= 33) prepareArtworkTexture(source)
            else prepareFlowTexture(source)
        texture.prepareToDraw()
        BackgroundAssets(key = key, flow = texture)
    }
}

internal const val ARTWORK_TEXTURE_SIZE = 256

/** 封面纹理缩到 24px 双重模糊的色场：小控件（翻译/人声按钮）的磨砂底。
 *  复用背景资源缓存（getOrLoad 命中即零额外 IO），加载与模糊均在后台线程。 */
@Composable
internal fun rememberFrostedBackdrop(backgroundState: () -> NowPlayingBackgroundState): ImageBitmap? {
    val context = LocalContext.current
    val background = backgroundState()
    val requestKey = background.requestKey
    val artworkUri = background.artworkUri
    val frosted = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(requestKey) {
        frosted.value = null
        if (requestKey == null || artworkUri == null) return@LaunchedEffect
        val assets = loadBackgroundAssets(context, artworkUri, requestKey) ?: return@LaunchedEffect
        val texture = assets.flow ?: return@LaunchedEffect
        frosted.value = withContext(Dispatchers.Default) {
            runCatching {
                // 纹理可能是 F16 配置，Toolkit 模糊需要 8888
                val software = if (texture.config == Bitmap.Config.RGBA_F16)
                    texture.copy(Bitmap.Config.ARGB_8888, false) else texture
                val small = Bitmap.createScaledBitmap(software, 24, 24, true)
                Toolkit.blur(Toolkit.blur(small, 6), 6).asImageBitmap()
            }.getOrNull()
        }
    }
    return frosted.value
}

/** Keep the original colour detail until the layered GPU field has been blurred.
 * Center-crop rather than distort non-square artwork. F16 avoids rounding each
 * resampling / interrupted cover blend back into an 8-bit intermediate bitmap.
 */
@RequiresApi(26)
internal fun prepareArtworkTexture(source: Bitmap): Bitmap {
    val size = ARTWORK_TEXTURE_SIZE.toFloat()
    val scale = size / minOf(source.width, source.height)
    val width = source.width * scale
    val height = source.height * scale
    return Bitmap.createBitmap(ARTWORK_TEXTURE_SIZE, ARTWORK_TEXTURE_SIZE, Bitmap.Config.RGBA_F16).apply {
        Canvas(this).apply {
            drawColor(android.graphics.Color.rgb(28, 28, 32))
            drawBitmap(source, null, RectF((size - width) * 0.5f, (size - height) * 0.5f,
                (size + width) * 0.5f, (size + height) * 0.5f), Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
}

/** Legacy soft colour field also used by the separately tuned lyric share card. */
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
