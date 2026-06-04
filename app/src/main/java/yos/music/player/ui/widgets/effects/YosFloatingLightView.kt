package yos.music.player.ui.widgets.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.data.libraries.SettingsLibrary.NowplayingBackgroundEffect

/**
 * 播放页背景组件。
 * 静态模式（默认）：预模糊 + 缓存 + Crossfade，零持续开销。
 * 动态模式：GPU 实时模糊 + 对角线缓慢流动动画。
 */
@Composable
fun YosFloatingLight(
    modifier: Modifier,
    album: () -> Uri?,
    isPlaying: () -> Boolean,
    nowPage: () -> String,
    showMiniPlayer: () -> Boolean
) {
    val context = LocalContext.current
    val dynamicEnabled = NowplayingBackgroundEffect

    // 原始饱和度增强图（供动态模式 GPU 模糊用）
    val rawBg = remember { mutableStateOf<ImageBitmap?>(null) }
    // 静态预模糊图
    val blurredBg = remember { mutableStateOf<ImageBitmap?>(null) }
    val imageLoader = remember { ImageLoader.Builder(context).crossfade(true).build() }

    val albumKey = album()?.toString()
    LaunchedEffect(albumKey) {
        val uri = album() ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val request = ImageRequest.Builder(context).data(uri).size(256)
                .allowHardware(false).build()
            val source = imageLoader.execute(request).drawable?.toBitmap()
                ?.let { toSoftware(it) } ?: return@withContext

            val scale = 256f / maxOf(source.width, source.height)
            val scaled = Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            )
            val saturated = applySaturation(scaled)
            // 动态模式用饱和度图（不模糊，留给 GPU）
            rawBg.value = saturated.asImageBitmap()
            // 静态模式预模糊（CPU 一次，缓存复用）
            blurredBg.value = Toolkit.blur(saturated.copy(Bitmap.Config.ARGB_8888, true), 25).asImageBitmap()
        }
    }

    Box(modifier = modifier) {
        if (dynamicEnabled) {
            Crossfade(
                targetState = rawBg.value,
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            ) { bg ->
                if (bg != null) DynamicBlurLayer(bg, showMiniPlayer) else FallbackLayer()
            }
        } else {
            Crossfade(
                targetState = blurredBg.value,
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            ) { bg ->
                if (bg != null) StaticBlurLayer(bg) else FallbackLayer()
            }
        }
    }
}

/** 静态模式：预模糊图居中裁剪 + 遮罩 */
@Composable
private fun StaticBlurLayer(bitmap: ImageBitmap) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color(0x66000000)) }
        )
    }
}

/** 动态模式:原图 + GPU 模糊 + 缓慢位移。生命周期感知：后台/锁屏/最小化时冻结动画 */
@Composable
private fun DynamicBlurLayer(bitmap: ImageBitmap, showMiniPlayer: () -> Boolean) {
    var isForeground by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isForeground = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tx = remember { Animatable(0f) }
    val ty = remember { Animatable(0f) }

    // 仅当前台且播放页全屏时运行动画
    val shouldAnimate = isForeground && !showMiniPlayer()

    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) return@LaunchedEffect

        val jobX = launch {
            while (isActive) {
                tx.animateTo(-0.15f, tween(6000, easing = LinearEasing))
                tx.animateTo(0.15f, tween(6000, easing = LinearEasing))
            }
        }
        val jobY = launch {
            while (isActive) {
                ty.animateTo(-0.15f, tween(7500, easing = LinearEasing))
                ty.animateTo(0.15f, tween(7500, easing = LinearEasing))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.35f
                    scaleY = 1.35f
                    translationX = tx.value * size.width
                    translationY = ty.value * size.height
                }
                .blur(80.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color(0x66000000)) }
        )
    }
}

@Composable
private fun FallbackLayer() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color(0x66000000)) }
    )
}

private fun applySaturation(source: Bitmap): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        val matrix = ColorMatrix()
        matrix.setSaturation(1.5f)
        colorFilter = ColorMatrixColorFilter(matrix)
    }
    canvas.drawBitmap(source, 0f, 0f, paint)
    return out
}

private fun toSoftware(bitmap: Bitmap): Bitmap =
    if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap

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
