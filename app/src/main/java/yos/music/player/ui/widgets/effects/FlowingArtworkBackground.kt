package yos.music.player.ui.widgets.effects

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

/** Original AGSL implementation inspired by AMLL's softened artwork / deforming mesh approach.
 * Texture preparation is off the UI thread; frames only update GPU uniforms.
 */
internal const val FLOWING_ARTWORK_SHADER = """
uniform shader previous;
uniform shader next;
uniform float2 resolution;
uniform float phase;
uniform float blend;
half4 main(float2 p) {
    float2 uv = p / resolution;
    float t = phase;
    // Smooth, independent waves deform the colour field without a ping-pong endpoint.
    float2 q = uv;
    q.x += 0.16 * sin(uv.y * 4.7 + t * 0.31) + 0.09 * cos(uv.x * 3.1 - t * 0.23);
    q.y += 0.17 * cos(uv.x * 4.2 - t * 0.27) + 0.08 * sin(uv.y * 3.8 + t * 0.19);
    q = 0.5 + (q - 0.5) * 0.78;
    // Both sources have the same 96px square domain, including interrupted blends.
    float2 tex = clamp(q, 0.01, 0.99) * 96.0;
    half3 c = mix(previous.eval(tex).rgb, next.eval(tex).rgb, half(blend));
    float shade = 0.78 - 0.19 * smoothstep(0.25, 1.0, uv.y);
    return half4(c * half(shade), 1.0);
}
"""

private data class FlowLayers(
    val previous: Bitmap,
    val next: Bitmap,
    val progress: Animatable<Float, AnimationVector1D> = Animatable(1f)
)

/** Freeze colour mixing, not the deformation phase, when another cover arrives. */
internal fun freezeArtworkBlend(previous: Bitmap, next: Bitmap, progress: Float): Bitmap {
    val result = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    canvas.drawBitmap(previous, 0f, 0f, null)
    canvas.drawBitmap(next, 0f, 0f, Paint().apply { alpha = (progress.coerceIn(0f, 1f) * 255).toInt() })
    return result
}

@RequiresApi(33)
@Composable
internal fun FlowingArtworkBackground(
    texture: Bitmap,
    isPlaying: () -> Boolean,
    showMiniPlayer: () -> Boolean,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true
) {
    val owner = LocalLifecycleOwner.current
    var foreground by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var layers by remember { mutableStateOf(FlowLayers(texture, texture)) }
    LaunchedEffect(texture) {
        val old = layers
        if (texture !== old.next) {
            val visible = if (old.progress.value >= 1f) old.next
                else freezeArtworkBlend(old.previous, old.next, old.progress.value)
            layers = FlowLayers(visible, texture, Animatable(0f))
        }
        val current = layers
        if (current.progress.value < 1f) {
            current.progress.animateTo(1f, tween(900))
            layers = FlowLayers(texture, texture)
        }
    }
    val phase = remember { mutableFloatStateOf(0f) }
    // Keep this renderer and its phase alive across the preference toggle. Static
    // mode freezes the exact same field instead of switching to a blurred photo.
    val active = motionEnabled && foreground && !showMiniPlayer() && isPlaying()
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var previous = 0L
        var elapsed = phase.floatValue
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L) elapsed += ((now - previous) / 1_000_000_000f).coerceAtMost(0.1f)
                previous = now
                phase.floatValue = elapsed
            }
            // Do not subscribe to all 120 display callbacks just to discard three
            // out of four. Wake near the next 30Hz sample, then align to vsync.
            delay(30)
        }
    }
    val current = layers
    val shader = remember { RuntimeShader(FLOWING_ARTWORK_SHADER) }
    val brush = remember(shader, current) {
        shader.apply {
            setInputShader("previous", BitmapShader(current.previous, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            })
            setInputShader("next", BitmapShader(current.next, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            })
        }
        ShaderBrush(shader)
    }
    Canvas(modifier.fillMaxSize()) {
        shader.setFloatUniform("resolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
        shader.setFloatUniform("phase", phase.floatValue)
        shader.setFloatUniform("blend", current.progress.value)
        drawRect(brush)
    }
}
