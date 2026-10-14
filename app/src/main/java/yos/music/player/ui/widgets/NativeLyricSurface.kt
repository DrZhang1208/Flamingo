package yos.music.player.ui.widgets

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor

/** Independent Latin letters cannot share fi/fl shaping ligatures while moving. */
internal fun nativeIndependentGlyphStyle(style: TextStyle): TextStyle = style.copy(
    fontFeatureSettings = listOfNotNull(style.fontFeatureSettings?.takeIf { it.isNotBlank() },
        "'liga' 0, 'clig' 0").joinToString(", "))

/** Rasterize shaped ink once at its baseline, then filter its fractional motion.
 * Drawing text at a new baseline on every frame lets Android snap slow vertical
 * motion to the glyph raster grid. Moving a cached surface avoids that snapping.
 * The cache belongs to a measured paragraph, so font/width/density changes replace
 * it and the virtualized lyric rows bound the number of retained surfaces.
 */
internal class NativeLyricSurface(private val layout: TextLayoutResult,
    private val emphasisPixelLimit: Long = 2_097_152L) {
    private val strokeWidth = 0.9f * layout.layoutInput.density.density
    private val padding = ceil(strokeWidth + 2f).toInt()
    // Malformed timed lyrics can contain an entire song in one paragraph. Keep
    // them on the vector path rather than allocating an unbounded GPU texture.
    private val rasterizable = layout.size.width <= 4096 && layout.size.height <= 4096 &&
        (layout.size.width + 2L * padding) * (layout.size.height + 2L * padding) <= 1_048_576L
    private val fill by lazy(LazyThreadSafetyMode.NONE) { rasterize(outline = false) }
    private val outline by lazy(LazyThreadSafetyMode.NONE) { rasterize(outline = true) }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val savedFieldPlacement = Matrix()
    private val localFieldPlacement = Matrix()
    private val fieldOrigin = Matrix()
    private val fillSampler by lazy(LazyThreadSafetyMode.NONE) { MaskSampler(fill) }
    private val outlineSampler by lazy(LazyThreadSafetyMode.NONE) { MaskSampler(outline) }

    private class MaskSampler(val bitmap: Bitmap) {
        private val mask = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        init {
            // ComposeShader does not reliably inherit Paint's bitmap filtering
            // on recent Android versions. Keep fractional glyph motion filtered.
            if (android.os.Build.VERSION.SDK_INT >= 33)
                mask.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
        private val placement = Matrix()
        private var field: Shader? = null
        private var composed: Shader? = null
        fun at(color: Shader, x: Float, y: Float): Shader {
            placement.setTranslate(x, y)
            mask.setLocalMatrix(placement)
            if (field !== color) {
                field = color
                composed = ComposeShader(color, mask, PorterDuff.Mode.DST_IN)
            }
            return composed!!
        }
    }

    private fun drawShadedMask(scope: DrawScope, sampler: MaskSampler, color: Shader,
        x: Float, y: Float, alpha: Float = 1f) {
        // Draw a paragraph-coordinate rectangle. Only the mask receives the
        // bitmap placement; drawBitmap(A8, x, y, shader) can move the colour field
        // into bitmap-local coordinates differently on CPU and GPU backends.
        paint.shader = sampler.at(color, x, y)
        paint.color = android.graphics.Color.WHITE
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        scope.drawContext.canvas.nativeCanvas.drawRect(x, y,
            x + sampler.bitmap.width, y + sampler.bitmap.height, paint)
    }
    private val fragments = HashMap<NativeLyricGlyph, Fragment>()
    private val fallbackGlyphs = HashSet<NativeLyricGlyph>()
    private val glyphMeasurer by lazy(LazyThreadSafetyMode.NONE) {
        TextMeasurer(layout.layoutInput.fontFamilyResolver, layout.layoutInput.density,
            layout.layoutInput.layoutDirection, cacheSize = 32)
    }
    private val isolatedStyle by lazy(LazyThreadSafetyMode.NONE) {
        nativeIndependentGlyphStyle(layout.layoutInput.style).copy(textAlign = TextAlign.Start)
    }
    private fun isolatedLayout(glyph: NativeLyricGlyph): TextLayoutResult = glyphMeasurer.measure(
        layout.layoutInput.text.text.substring(glyph.textOffset, glyph.textEnd),
        isolatedStyle,
        softWrap = false, maxLines = 1)

    private var alphaScratch = ByteArray(0)
    private var fragmentPixels = 0L
    internal val cachedEmphasisFragmentCount: Int get() = fragments.size
    internal val cachedEmphasisPixelCount: Long get() = fragmentPixels
    private val fontPx = with(layout.layoutInput.density) {
        if (layout.layoutInput.style.fontSize.isSp) layout.layoutInput.style.fontSize.toPx() else 16f * density * fontScale
    }
    private val glowRadius = (fontPx * 0.11f).coerceIn(
        layout.layoutInput.density.density * 1.5f, layout.layoutInput.density.density * 4f)

    private class Fragment(val fill: Bitmap, val glow: Bitmap, val x: Float, val y: Float,
        val glowX: Float, val glowY: Float) {
        val sampler = MaskSampler(fill)
    }

    private fun fragment(glyph: NativeLyricGlyph): Fragment? {
        if (!rasterizable || glyph in fallbackGlyphs) return null
        fragments[glyph]?.let { return it }
        val isolated = if (glyph.independentInk) isolatedLayout(glyph) else null
        // Glyph advances are not ink bounds: italic f/j and tightly kerned pairs
        // can cross them. Rasterize just the owned grapheme with overhang space.
        val overhang = if (isolated != null) ceil(fontPx * 0.6f + padding) else 0f
        val x = floor(if (isolated != null) glyph.inkLeft - overhang else glyph.left)
        val y = floor(if (isolated != null) glyph.top - padding else glyph.top)
        val width = (if (isolated != null) ceil(glyph.inkLeft + isolated.size.width + overhang) - x
            else ceil(glyph.right) - x).toInt().coerceAtLeast(1)
        val height = (ceil(glyph.bottom + if (isolated != null) padding.toFloat() else 0f) - y)
            .toInt().coerceAtLeast(1)
        if (emphasisPixelLimit <= 0L || width.toLong() * height > 1_048_576L) return null
        var ink = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply {
            density = Bitmap.DENSITY_NONE
        }
        if (isolated != null) {
            CanvasDrawScope().draw(layout.layoutInput.density, layout.layoutInput.layoutDirection,
                Canvas(ink.asImageBitmap()), Size(width.toFloat(), height.toFloat())) {
                drawText(isolated, color = Color.White,
                    topLeft = Offset(glyph.inkLeft - x, glyph.baseline - isolated.firstBaseline - y),
                    alpha = 1f, shadow = Shadow.None, drawStyle = Fill)
            }
        } else android.graphics.Canvas(ink).apply {
            translate(-x, -y)
            clipRect(glyph.left, glyph.top, glyph.right, glyph.bottom)
            drawBitmap(fill, -padding.toFloat(), -padding.toFloat(), null)
        }
        // Overhang padding protects f/j, but should not consume the retained
        // halo budget. Keep only owned ink plus a transparent filtering gutter.
        val rawWidth = ink.width; val rawHeight = ink.height; val stride = ink.rowBytes
        val bytes = stride * rawHeight
        if (alphaScratch.size < bytes) alphaScratch = ByteArray(maxOf(bytes, alphaScratch.size * 2))
        ink.copyPixelsToBuffer(ByteBuffer.wrap(alphaScratch))
        var left = rawWidth; var right = -1; var top = rawHeight; var bottom = -1
        for (row in 0 until rawHeight) {
            val rowStart = row * stride
            for (column in 0 until rawWidth) {
                if ((alphaScratch[rowStart + column].toInt() and 0xff) != 0) {
                    left = minOf(left, column); right = maxOf(right, column)
                    top = minOf(top, row); bottom = maxOf(bottom, row)
                }
            }
        }
        if (right < left) {
            ink.recycle()
            if (fragmentPixels + 2 > emphasisPixelLimit) {
                fallbackGlyphs += glyph
                return null
            }
            val empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8).apply { density = Bitmap.DENSITY_NONE }
            fragmentPixels += 2
            return Fragment(empty, empty, x, y, x, y).also { fragments[glyph] = it }
        }
        left = (left - 2).coerceAtLeast(0); top = (top - 2).coerceAtLeast(0)
        right = (right + 3).coerceAtMost(ink.width); bottom = (bottom + 3).coerceAtMost(ink.height)
        val trimmed = Bitmap.createBitmap(ink, left, top, right - left, bottom - top)
            .apply { density = Bitmap.DENSITY_NONE }
        if (trimmed !== ink) ink.recycle()
        ink = trimmed
        val inkX = x + left; val inkY = y + top
        val extent = ceil(glowRadius * 3f + 3f).toLong()
        val required = ink.width.toLong() * ink.height +
            (ink.width + extent * 2) * (ink.height + extent * 2)
        if (fragmentPixels + required > emphasisPixelLimit) {
            ink.recycle()
            fallbackGlyphs += glyph
            return null
        }
        val offsets = IntArray(2)
        val halo = ink.extractAlpha(Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
        }, offsets).apply { density = Bitmap.DENSITY_NONE }
        fragmentPixels += ink.width.toLong() * ink.height + halo.width.toLong() * halo.height
        return Fragment(ink, halo, inkX, inkY, inkX + offsets[0], inkY + offsets[1]).also { fragments[glyph] = it }
    }

    /** Latin masks contain only the owned grapheme, including its overhang.
     * Neither scaling nor blur can reveal pixels from an adjacent letter. */
    fun drawEmphasized(scope: DrawScope, glyph: NativeLyricGlyph, color: Shader?, solid: Color,
        accent: Color, rise: Float, glow: Float) {
        val part = fragment(glyph)
        if (part == null) {
            val isolated = isolatedLayout(glyph)
            val at = Offset(glyph.inkLeft, glyph.baseline - isolated.firstBaseline - rise)
            val halo = if (glow > 0f) Shadow(accent.copy(alpha = accent.alpha * glow.coerceIn(0f, 1f)),
                Offset.Zero, glowRadius) else Shadow.None
            if (color == null) scope.drawText(isolated, color = solid, topLeft = at,
                alpha = 1f, shadow = halo)
            else {
                // drawText paints its paragraph at a translated local origin.
                // Preserve the same field coordinates in the vector fallback.
                savedFieldPlacement.reset()
                color.getLocalMatrix(savedFieldPlacement)
                fieldOrigin.setTranslate(-at.x, -at.y)
                localFieldPlacement.setConcat(fieldOrigin, savedFieldPlacement)
                color.setLocalMatrix(localFieldPlacement)
                try {
                    scope.drawText(isolated, brush = ShaderBrush(color), topLeft = at,
                        alpha = 1f, shadow = halo)
                } finally {
                    color.setLocalMatrix(savedFieldPlacement)
                }
            }
            return
        }
        val canvas = scope.drawContext.canvas.nativeCanvas
        if (glow > 0f) {
            // The local glyph phase already follows the sung position. Applying
            // the paragraph sweep a second time suppresses most of its halo.
            paint.shader = null
            paint.color = accent.toArgb()
            paint.alpha = (glow.coerceIn(0f, 1f) * accent.alpha * 255f).toInt()
            canvas.drawBitmap(part.glow, part.glowX, part.glowY - rise, paint)
        }
        if (color != null) drawShadedMask(scope, part.sampler, color, part.x, part.y - rise)
        else {
            paint.shader = null
            paint.color = solid.toArgb()
            canvas.drawBitmap(part.fill, part.x, part.y - rise, paint)
        }
    }

    private fun rasterize(outline: Boolean): Bitmap {
        val bitmap = ImageBitmap((layout.size.width + 2 * padding).coerceAtLeast(1),
            (layout.size.height + 2 * padding).coerceAtLeast(1), config = ImageBitmapConfig.Alpha8)
        CanvasDrawScope().draw(layout.layoutInput.density, layout.layoutInput.layoutDirection,
            Canvas(bitmap), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            drawText(layout, color = Color.White, topLeft = Offset(padding.toFloat(), padding.toFloat()),
                alpha = 1f, shadow = Shadow.None,
                drawStyle = if (outline) Stroke(width = strokeWidth, join = StrokeJoin.Round) else Fill)
            if (outline) {
                // Keep the filled silhouette entirely independent of the glow.
                drawText(layout, color = Color.White, topLeft = Offset(padding.toFloat(), padding.toFloat()),
                    alpha = 1f, shadow = Shadow.None, drawStyle = Fill, blendMode = BlendMode.DstOut)
            }
        }
        // Geometry is already measured in physical pixels. Do not let Canvas
        // apply a second density conversion when drawing the mask on a device.
        return bitmap.asAndroidBitmap().apply { density = Bitmap.DENSITY_NONE }
    }

    fun draw(scope: DrawScope, color: Shader, rise: Float, outlineOnly: Boolean = false, alpha: Float = 1f) {
        if (!rasterizable) {
            with(scope) {
                if (outlineOnly) drawContext.canvas.saveLayer(Rect(0f, -rise,
                    layout.size.width.toFloat(), layout.size.height - rise), androidx.compose.ui.graphics.Paint())
                try {
                    drawText(layout, brush = ShaderBrush(color), topLeft = Offset(0f, -rise),
                        alpha = alpha, shadow = Shadow.None,
                        drawStyle = if (outlineOnly) Stroke(width = strokeWidth, join = StrokeJoin.Round) else Fill)
                    if (outlineOnly) drawText(layout, color = Color.White, topLeft = Offset(0f, -rise),
                        alpha = 1f, shadow = Shadow.None, drawStyle = Fill, blendMode = BlendMode.DstOut)
                } finally {
                    if (outlineOnly) drawContext.canvas.restore()
                }
            }
            return
        }
        drawShadedMask(scope, if (outlineOnly) outlineSampler else fillSampler,
            color, -padding.toFloat(), -rise - padding, alpha)
    }

    fun drawSolid(scope: DrawScope, color: Color, rise: Float) {
        if (!rasterizable) {
            scope.drawText(layout, color = color, topLeft = Offset(0f, -rise),
                alpha = 1f, shadow = Shadow.None, drawStyle = Fill)
            return
        }
        paint.shader = null
        paint.color = color.toArgb()
        scope.drawContext.canvas.nativeCanvas.drawBitmap(fill, -padding.toFloat(), -rise - padding, paint)
    }
}
