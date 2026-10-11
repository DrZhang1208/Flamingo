package yos.music.player.code.utils.lrc

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import yos.music.player.R
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import yos.music.player.ui.theme.YosRoundedCornerShape
import java.io.File

/** Produces the PNG lyric cards shared by the APK instead of exporting raw text. */
object LyricCardExporter {
    private const val EXPORT_SCALE = 2
    private const val CARD_WIDTH = 1080
    private const val OUTER_MARGIN = 108
    private const val OUTER_VERTICAL = 64
    private const val CARD_PADDING = 64
    private const val COVER_SIZE = 128
    private const val FOOTER_HEIGHT = 224
    private const val MAX_CARD_HEIGHT = 8192

    suspend fun render(
        context: Context,
        lines: List<YosLyricLine>,
        title: String?,
        artist: String?,
        artworkUri: Uri?,
        includeTranslation: Boolean
    ): Bitmap {
        val cover = loadArtwork(context, artworkUri)
        val cardWidth = CARD_WIDTH - OUTER_MARGIN * 2
        val textWidth = cardWidth - CARD_PADDING * 2
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFF9E9.toInt()
            textSize = 48f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3FFF9E9.toInt(); textSize = 29f }
        fun measureLines() = lines.map { line ->
            layout(line.text, lyricPaint, textWidth) to
                line.translation.takeIf { includeTranslation && it.isNotBlank() }
                    ?.let { layout(it, translationPaint, textWidth) }
        }
        fun contentHeight(layouts: List<Pair<StaticLayout, StaticLayout?>>) =
            layouts.sumOf { (main, translation) -> main.height + (translation?.height?.plus(12) ?: 0) } +
                (layouts.size - 1).coerceAtLeast(0) * 30
        var layouts = measureLines()
        // Keep every selected line, reducing type only for unusually long excerpts.
        while (contentHeight(layouts) + 128 + FOOTER_HEIGHT + OUTER_VERTICAL * 2 > MAX_CARD_HEIGHT && lyricPaint.textSize > 28f) {
            lyricPaint.textSize -= 2f
            translationPaint.textSize = lyricPaint.textSize * 29f / 48f
            layouts = measureLines()
        }
        val bodyHeight = (80 + contentHeight(layouts) + 48).coerceAtLeast(280)
        val height = bodyHeight + FOOTER_HEIGHT + OUTER_VERTICAL * 2
        require(height <= MAX_CARD_HEIGHT) { "Selected lyrics exceed the image size limit" }
        val bitmap = Bitmap.createBitmap(CARD_WIDTH * EXPORT_SCALE, height * EXPORT_SCALE, Bitmap.Config.ARGB_8888)
        // Rasterize text, paths and artwork directly at 2x; do not upscale a
        // completed low-resolution card. Layout proportions remain identical.
        val canvas = Canvas(bitmap).apply { scale(EXPORT_SCALE.toFloat(), EXPORT_SCALE.toFloat()) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // ARGB + PNG keeps the padding and corner cutouts genuinely transparent.
        bitmap.eraseColor(Color.TRANSPARENT)
        val card = RectF(OUTER_MARGIN.toFloat(), OUTER_VERTICAL.toFloat(),
            (CARD_WIDTH - OUTER_MARGIN).toFloat(), (height - OUTER_VERTICAL).toFloat())
        val hue = artworkHue(cover)
        fun tone(saturation: Float, value: Float) = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        canvas.save()
        canvas.clipPath(smoothCardPath(card, 52f))
        paint.shader = LinearGradient(card.left, card.top, card.right, card.bottom,
            intArrayOf(tone(0.78f, 0.17f), tone(0.83f, 0.31f), tone(0.8f, 0.19f)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(card, paint)
        paint.shader = android.graphics.RadialGradient(card.left + cardWidth * 0.25f,
            card.top + bodyHeight * 0.75f, cardWidth * 0.85f,
            intArrayOf(Color.HSVToColor(190, floatArrayOf(hue, 0.85f, 0.52f)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(card, paint)
        paint.shader = null
        val left = card.left + CARD_PADDING
        var y = card.top + 80f
        for ((main, translation) in layouts) {
            canvas.save(); canvas.translate(left, y); main.draw(canvas); canvas.restore()
            y += main.height
            if (translation != null) {
                y += 12f
                canvas.save(); canvas.translate(left, y); translation.draw(canvas); canvas.restore()
                y += translation.height
            }
            y += 30f
        }
        val footerTop = card.top + bodyHeight
        paint.color = 0x42000000
        canvas.drawRect(card.left, footerTop, card.right, card.bottom, paint)
        val coverRect = RectF(card.right - CARD_PADDING - COVER_SIZE, footerTop + 48f,
            card.right - CARD_PADDING, footerTop + 48f + COVER_SIZE)
        if (cover != null) {
            canvas.save()
            canvas.clipPath(smoothCardPath(coverRect, 16f))
            val side = minOf(cover.width, cover.height)
            val source = android.graphics.Rect((cover.width - side) / 2, (cover.height - side) / 2,
                (cover.width + side) / 2, (cover.height + side) / 2)
            paint.color = Color.WHITE
            canvas.drawBitmap(cover, source, coverRect, paint)
            canvas.restore()
        } else {
            paint.color = 0x24FFFFFF
            canvas.drawPath(smoothCardPath(coverRect, 16f), paint)
        }
        val infoWidth = (coverRect.left - left - 36f).toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lyricPaint.color; textSize = 32f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFF9E9.toInt(); textSize = 28f }
        fun info(text: String, textPaint: TextPaint, top: Float, inset: Int = 0) {
            val line = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, infoWidth - inset)
                .setIncludePad(false).setMaxLines(1)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            canvas.save(); canvas.translate(left + inset, top); line.draw(canvas); canvas.restore()
        }
        info(title.orEmpty().ifBlank { "Flamingo" }, titlePaint, footerTop + 52f)
        info(artist.orEmpty(), artistPaint, footerTop + 95f)
        artistPaint.textSize = 26f
        // Use the actual application mark, preserving its branded gradient.
        ContextCompat.getDrawable(context, R.drawable.flamingo_icon)?.mutate()?.let { icon ->
            icon.setBounds((left - 9f).toInt(), (footerTop + 128f).toInt(),
                (left + 33f).toInt(), (footerTop + 170f).toInt())
            icon.draw(canvas)
        }
        info("FLAMINGO", artistPaint, footerTop + 138f, inset = 34)
        canvas.restore()
        return bitmap
    }

    /** Share the player's continuous-corner geometry at export-pixel density. */
    private fun smoothCardPath(bounds: RectF, radiusPx: Float): Path {
        val outline = YosRoundedCornerShape(radiusPx.dp).createOutline(
            Size(bounds.width(), bounds.height()), LayoutDirection.Ltr, Density(1f)) as Outline.Generic
        return Path(outline.path.asAndroidPath()).apply { offset(bounds.left, bounds.top) }
    }

    private fun artworkHue(bitmap: Bitmap?): Float {
        if (bitmap == null) return 30f
        val weights = FloatArray(24)
        val hues = FloatArray(24)
        val hsv = FloatArray(3)
        for (row in 0 until 20) for (column in 0 until 20) {
            val pixel = bitmap.getPixel(column * bitmap.width / 20, row * bitmap.height / 20)
            Color.colorToHSV(pixel, hsv)
            val weight = hsv[1] * hsv[1] * hsv[2] * (Color.alpha(pixel) / 255f)
            val bucket = (hsv[0] / 15f).toInt().coerceIn(0, 23)
            weights[bucket] += weight
            hues[bucket] += hsv[0] * weight
        }
        val strongest = weights.indices.maxByOrNull { weights[it] } ?: return 30f
        return if (weights[strongest] > 0.1f) hues[strongest] / weights[strongest] else 30f
    }

    fun saveToPictures(context: Context, bitmap: Bitmap): Uri {
        val fileName = "Flamingo_lyrics_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Flamingo")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("cannot create image")
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: error("cannot open image")
            return uri
        }
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Flamingo").apply { mkdirs() }
        return Uri.fromFile(File(directory, fileName).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        })
    }

    fun createShareIntent(context: Context, bitmap: Bitmap): Intent {
        val directory = File(context.cacheDir, "lyric_cards").apply { mkdirs() }
        val file = File(directory, "Flamingo_lyrics.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, "Flamingo lyrics", uri)
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(8f, 1f)
            .build()

    private suspend fun loadArtwork(context: Context, uri: Uri?): Bitmap? {
        if (uri == null) return null
        return runCatching {
            val result = coil.Coil.imageLoader(context).execute(
                coil.request.ImageRequest.Builder(context).data(uri).size(512)
                    .allowHardware(false).build()
            )
            (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        }.getOrNull()
    }
}
