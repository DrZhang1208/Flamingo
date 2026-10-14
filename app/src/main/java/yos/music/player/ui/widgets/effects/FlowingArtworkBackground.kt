package yos.music.player.ui.widgets.effects

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RenderEffect
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Small GPU artwork/blur surface -> linear upscale -> full-size exposure/dither.
 * Independently implemented from the publicly described Apple Music Web approach;
 * mobile framing and timing are our own. No CPU bitmap processing happens per frame.
 */
internal const val FLOWING_ARTWORK_SHADER = """
uniform shader previous;
uniform shader next;
uniform float2 resolution;
uniform float phase;
uniform float blend;
uniform float2 textureSize;

float2 rotatePoint(float2 p, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return float2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float2 twistPoint(float2 p, float2 center, float radius, float angle) {
    float2 delta = p - center;
    float falloff = max(0.0, 1.0 - length(delta) / radius);
    return center + rotatePoint(delta, angle * falloff * falloff);
}

float4 artworkLayer(float2 p, float2 center, float extent, float angle) {
    float2 uv = rotatePoint(p - center, angle) / extent + 0.5;
    float edge = max(abs(uv.x - 0.5), abs(uv.y - 0.5));
    float coverage = 1.0 - smoothstep(0.40, 0.5, edge);
    float2 tex = clamp(uv * textureSize, float2(0.5), textureSize - 0.5);
    float3 color = mix(float3(previous.eval(tex).rgb), float3(next.eval(tex).rgb), blend);
    return float4(color, coverage);
}

half4 main(float2 p) {
    // One unit has the same physical length on both axes. A square cover stays
    // square in portrait, landscape and split-screen instead of stretching.
    float2 q = (p - resolution * 0.5) / max(resolution.x, resolution.y);
    float t = phase * 0.80;
    q = twistPoint(q, float2(-0.18, -0.12), 0.85, 1.8 + 0.35 * sin(t * 0.07));
    q = twistPoint(q, float2(0.22, 0.25), 0.65, -1.4 + 0.30 * cos(t * 0.09));

    // The largest square covers even the viewport's rotated corners. The other
    // three follow the 80%, 50%, 25% sizes described for the web renderer.
    float3 c = artworkLayer(q, float2(0.0), 1.55, t * 0.055 + 0.4).rgb;
    float4 layer = artworkLayer(q, float2(0.06, -0.08), 0.80, -t * 0.075 + 2.1);
    c = mix(c, layer.rgb, layer.a * 0.65);
    float2 orbit = float2(0.23 * cos(t * 0.065 + 0.8), 0.28 * sin(t * 0.065 + 0.8));
    layer = artworkLayer(q, orbit, 0.50, t * 0.10 + 4.2);
    c = mix(c, layer.rgb, layer.a * 0.65);
    orbit = float2(0.30 * cos(-t * 0.085 + 3.0), 0.35 * sin(-t * 0.085 + 3.0));
    layer = artworkLayer(q, orbit, 0.25, -t * 0.13 + 1.3);
    c = mix(c, layer.rgb, layer.a * 0.50);
    return half4(half3(c), 1.0);
}
"""

/** This pass must run AFTER blur and exposure, otherwise blur removes the dither.
 * Screen-anchored noise is independent of time: paused backgrounds never shimmer.
 */
internal const val ARTWORK_FINISH_SHADER = """
uniform shader field;
uniform float height;
half4 main(float2 p) {
    float3 c = float3(field.eval(p).rgb);
    float luma = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = clamp(mix(float3(luma), c, 1.35), 0.0, 1.0);
    float shade = (0.78 - 0.14 * smoothstep(0.25, 1.0, p.y / height)) * 0.85;
    c *= shade;
    float noise = fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))) - 0.5;
    c = clamp(c + noise * (1.2 / 255.0), 0.0, 1.0);
    return half4(half3(c), 1.0);
}
"""

@RequiresApi(33)
private fun artworkFinishEffect(height: Float): RenderEffect {
    val finish = RuntimeShader(ARTWORK_FINISH_SHADER).apply {
        setFloatUniform("height", height.coerceAtLeast(1f))
    }
    return RenderEffect.createRuntimeShaderEffect(finish, "field")
}

private data class FlowLayers(
    val previous: Bitmap,
    val next: Bitmap,
    val progress: Animatable<Float, AnimationVector1D> = Animatable(1f)
)

/** Freeze colour mixing, not the deformation phase, when another cover arrives. */
internal fun freezeArtworkBlend(previous: Bitmap, next: Bitmap, progress: Float): Bitmap {
    // Preserve texture precision when a rapid track change interrupts a fade.
    val result = Bitmap.createBitmap(previous.width, previous.height, previous.config ?: Bitmap.Config.ARGB_8888)
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
    motionEnabled: Boolean = true,
    // The full-size reference is used by device pixel regressions only.
    maxRenderDimension: Int = ARTWORK_RENDER_LONG_EDGE
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
    var renderSize by remember { mutableStateOf(IntSize.Zero) }
    val effect = remember(renderSize) {
        artworkFinishEffect(renderSize.height.toFloat()).asComposeRenderEffect()
    }
    val brush = remember(shader, current) {
        shader.apply {
            setFloatUniform("textureSize", current.next.width.toFloat(), current.next.height.toFloat())
            setInputShader("previous", BitmapShader(current.previous, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            })
            setInputShader("next", BitmapShader(current.next, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            })
        }
        ShaderBrush(shader)
    }
    // Give the native child layer a genuinely small layout size, then scale its
    // filtered result. The surrounding layer only handles exposure and dither.
    Layout(modifier = modifier.fillMaxSize().onSizeChanged { renderSize = it }.graphicsLayer {
        // The finish shader emits opaque pixels. Confine the image filter to
        // this viewport so it cannot paint black over neighbouring content.
        clip = true
        renderEffect = effect
    }, content = {
        Canvas(Modifier) {
            shader.setFloatUniform("resolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            shader.setFloatUniform("phase", phase.floatValue)
            shader.setFloatUniform("blend", current.progress.value)
            drawRect(brush)
        }
    }) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val target = artworkRenderSize(width, height, maxRenderDimension)
        val child = measurables.single().measure(Constraints.fixed(target.width, target.height))
        layout(width, height) {
            child.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = width.toFloat() / target.width
                scaleY = height.toFloat() / target.height
                val radius = (minOf(target.width, target.height) * 0.18f).coerceAtLeast(1f)
                renderEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
            }
        }
    }
}
