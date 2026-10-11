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
            assertEquals(0, Color.alpha(result.getPixel(0, 0)))
            assertEquals(0, Color.alpha(result.getPixel(214, result.height / 2)))
            assertEquals(0, Color.alpha(result.getPixel(1080, 126)))
            assertEquals(0, Color.alpha(result.getPixel(1080, result.height - 128)))
            assertEquals(result.getPixel(0, 0), result.getPixel(216, 128))
            assertNotEquals(result.getPixel(0, 0), result.getPixel(1080, 200))
            assertEquals("Artwork belongs in the footer and must be centre-cropped",
                0xFFAB5200.toInt(), result.getPixel(1688, result.height - 352))
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
