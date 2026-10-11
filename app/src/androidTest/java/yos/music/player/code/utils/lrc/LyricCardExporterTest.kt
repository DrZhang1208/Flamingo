package yos.music.player.code.utils.lrc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricCardExporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun line(text: String) = YosLyricLine(0f, 1000f, listOf(YosLyricToken(0f, 1000f, text)))

    @Test fun cardHasTransparentPaddingSmoothCornersAndBottomRightCover() = runBlocking {
        val cover = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        cover.eraseColor(Color.BLUE)
        Canvas(cover).drawRect(20f, 0f, 100f, 80f, Paint().apply { color = 0xFFAB5200.toInt() })
        val file = File.createTempFile("lyric-card-cover", ".png", context.cacheDir)
        try {
            file.outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val result = LyricCardExporter.render(context, List(8) { line("淘洗一切想要的生活") },
                "淘金小镇", "周杰伦", Uri.fromFile(file), false)
            assertEquals(2160, result.width)
            assertEquals(0, result.height % 2)
            assertTrue(result.height > 2000)
            assertTrue(result.hasAlpha())
            // Four equal transparent insets; old vertical padding was 128 output pixels.
            fun opaque(x: Int, y: Int) = Color.alpha(result.getPixel(x, y)) > 0
            val left = (0 until result.width).first { opaque(it, result.height / 2) }
            val right = (0 until result.width).first { opaque(result.width - 1 - it, result.height / 2) }
            val top = (0 until result.height).first { opaque(result.width / 2, it) }
            val bottom = (0 until result.height).first { opaque(result.width / 2, result.height - 1 - it) }
            assertEquals(102, top)
            assertEquals(top, left)
            assertEquals(top, right)
            assertEquals(top, bottom)
            assertEquals(0, Color.alpha(result.getPixel(0, 0)))
            assertEquals(0, Color.alpha(result.getPixel(100, result.height / 2)))
            assertEquals(0, Color.alpha(result.getPixel(1080, 100)))
            assertEquals(0, Color.alpha(result.getPixel(1080, result.height - 102)))
            assertEquals(result.getPixel(0, 0), result.getPixel(102, 102))
            assertNotEquals(result.getPixel(0, 0), result.getPixel(1080, 200))
            assertEquals("Artwork belongs in the footer and must be centre-cropped",
                0xFFAB5200.toInt(), result.getPixel(1802, result.height - 326))
            // Both saving and sharing use this lossless PNG encoder.
            val bytes = java.io.ByteArrayOutputStream().use {
                assertTrue(result.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(0, Color.alpha(decoded.getPixel(0, 0)))
            assertEquals(255, Color.alpha(decoded.getPixel(1080, 200)))
            decoded.recycle()
            result.recycle()
        } finally { file.delete(); cover.recycle() }
    }

    @Test fun longSelectionsGrowRatherThanDroppingSelectedLines() = runBlocking {
        val short = LyricCardExporter.render(context, listOf(line("一行歌词")), "标题", "歌手", null, false)
        val long = LyricCardExporter.render(context, List(14) { line("很长的歌词要保留完整内容 ".repeat(15)) },
            "很长的歌曲名称".repeat(10), "很长的歌手名称".repeat(10), null, false)
        assertTrue(long.height > short.height * 2)
        assertTrue(long.height <= 16384)
        short.recycle(); long.recycle()
    }
}
