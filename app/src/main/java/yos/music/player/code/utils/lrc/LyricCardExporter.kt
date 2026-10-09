package yos.music.player.code.utils.lrc

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File

/** Produces the PNG lyric cards shared by the APK instead of exporting raw text. */
object LyricCardExporter {
    private const val CARD_WIDTH = 1080
    private const val CARD_PADDING = 72
    private const val COVER_SIZE = 176
    private const val MAX_CARD_HEIGHT = 2400

    fun render(
        context: Context,
        lines: List<YosLyricLine>,
        title: String?,
        artist: String?,
        artworkUri: Uri?,
        includeTranslation: Boolean
    ): Bitmap {
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xA6FFFFFF.toInt()
            textSize = 34f
        }
        val textWidth = CARD_WIDTH - CARD_PADDING * 2
        val layouts = lines.map { line ->
            val main = layout(line.text, lyricPaint, textWidth)
            val translation = line.translation.takeIf { includeTranslation && it.isNotBlank() }
                ?.let { layout(it, translationPaint, textWidth) }
            main to translation
        }
        val contentHeight = layouts.sumOf { (main, translation) ->
            main.height + (translation?.height?.plus(16) ?: 0) + 34
        }
        val height = (CARD_PADDING + COVER_SIZE + 62 + contentHeight + 110)
            .coerceIn(1080, MAX_CARD_HEIGHT)
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                CARD_WIDTH.toFloat(),
                height.toFloat(),
                intArrayOf(0xFF321D45.toInt(), 0xFF15151D.toInt(), 0xFF090A0D.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), height.toFloat(), background)

        val coverRect = RectF(
            CARD_PADDING.toFloat(),
            CARD_PADDING.toFloat(),
            (CARD_PADDING + COVER_SIZE).toFloat(),
            (CARD_PADDING + COVER_SIZE).toFloat()
        )
        val cover = loadArtwork(context, artworkUri)
        if (cover != null) {
            canvas.save()
            canvas.clipPath(Path().apply {
                addRoundRect(coverRect, 34f, 34f, Path.Direction.CW)
            })
            canvas.drawBitmap(cover, null, coverRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            canvas.drawRoundRect(coverRect, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xA6FFFFFF.toInt()
            textSize = 31f
        }
        val headerLeft = (CARD_PADDING + COVER_SIZE + 38).toFloat()
        canvas.drawText(title.orEmpty().ifBlank { "Flamingo" }.take(22), headerLeft, 142f, titlePaint)
        canvas.drawText(artist.orEmpty().take(28), headerLeft, 197f, artistPaint)

        var y = (CARD_PADDING + COVER_SIZE + 62).toFloat()
        for ((main, translation) in layouts) {
            if (y + main.height > height - 100) break
            canvas.save()
            canvas.translate(CARD_PADDING.toFloat(), y)
            main.draw(canvas)
            canvas.restore()
            y += main.height + 12
            if (translation != null && y + translation.height <= height - 100) {
                canvas.save()
                canvas.translate(CARD_PADDING.toFloat(), y)
                translation.draw(canvas)
                canvas.restore()
                y += translation.height + 16
            }
            y += 22
        }
        canvas.drawText("FLAMINGO", CARD_PADDING.toFloat(), height - 54f, artistPaint)
        return bitmap
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

    private fun loadArtwork(context: Context, uri: Uri?): Bitmap? {
        if (uri == null) return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                ?: uri.path?.let(BitmapFactory::decodeFile)
        }.getOrNull()
    }
}
