package yos.music.player.code.utils.lrc

import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class LyricCardBackgroundTest {
    @Test fun brightAndSaturatedCoversKeepBothTextLevelsReadable() {
        // Include extreme primaries, white, black and intermediate colours.
        for (red in 0..255 step 17) for (green in 0..255 step 17) for (blue in 0..255 step 17) {
            val source = rgb(red, green, blue)
            val palette = LyricCardBackground.palette(IntArray(16) { source }, 4, 4)
            val corners = palette.corners
            for (background in corners) {
                assertTrue("Main text contrast for $source", contrast(palette.textColor, background) >= 4.5)
                assertTrue("Translation contrast for $source", contrast(palette.translationColor, background) >= 4.5)
            }
        }
    }

    @Test fun fineArtworkDetailCollapsesToTheSameQuietField() {
        val stripes = IntArray(16 * 16) { index -> if (index % 2 == 0) rgb(255, 255, 255) else rgb(0, 0, 0) }
        val blocks = IntArray(16 * 16) { index -> if ((index % 16 / 4 + index / 16 / 4) % 2 == 0) rgb(255, 255, 255) else rgb(0, 0, 0) }
        assertArrayEquals(LyricCardBackground.palette(stripes, 16, 16).corners,
            LyricCardBackground.palette(blocks, 16, 16).corners)
    }

    @Test fun regionalColoursStayDistinctAndMonochromeStaysNeutral() {
        val halves = IntArray(16) { if (it % 4 < 2) rgb(0, 0, 255) else rgb(255, 0, 0) }
        val colours = LyricCardBackground.palette(halves, 4, 4).corners
        assertTrue(channel(colours[0], 0) > channel(colours[0], 16))
        assertTrue(channel(colours[1], 16) > channel(colours[1], 0))
        assertTrue(channel(colours[0], 0) - channel(colours[1], 0) >= 120)
        assertTrue(channel(colours[1], 16) - channel(colours[0], 16) >= 120)
        for (grey in 0..255 step 17) {
            LyricCardBackground.palette(IntArray(16) { rgb(grey, grey, grey) }, 4, 4).corners.forEach {
                assertEquals(channel(it, 0), channel(it, 8))
                assertEquals(channel(it, 0), channel(it, 16))
            }
        }
    }

    @Test fun transparentMarginsDoNotPolluteTheArtworkColour() {
        val blue = rgb(0, 0, 255)
        val padded = IntArray(16) { if (it % 5 == 0) blue else 0x00FF0000 }
        assertArrayEquals(LyricCardBackground.palette(IntArray(16) { blue }, 4, 4).corners,
            LyricCardBackground.palette(padded, 4, 4).corners)
        val empty = LyricCardBackground.palette(IntArray(16), 4, 4).corners
        assertTrue(empty.all { it ushr 24 == 255 })
        assertEquals(empty[0], empty[1])
        assertEquals(empty[2], empty[3])
        assertTrue(empty.all { channel(it, 0) < 40 })
    }

    @Test fun brightCoversStayBrightAndDarkCoversKeepTheirIdentity() {
        val white = LyricCardBackground.palette(IntArray(16) { rgb(255, 255, 255) }, 4, 4)
        assertTrue(white.usesDarkText)
        assertTrue(white.corners.all { channel(it, 0) >= 170 })
        val blue = LyricCardBackground.palette(IntArray(16) { rgb(0, 0, 255) }, 4, 4)
        assertFalse(blue.usesDarkText)
        assertTrue(blue.corners.all { channel(it, 0) >= 170 && channel(it, 16) == 0 })
        val black = LyricCardBackground.palette(IntArray(16) { rgb(0, 0, 0) }, 4, 4)
        assertFalse(black.usesDarkText)
        assertTrue(black.corners.all { it == rgb(0, 0, 0) })
    }

    @Test fun mixedBrightAndDarkColoursStayReadableAcrossTheInterpolatedField() {
        val covers = listOf(
            intArrayOf(rgb(255, 255, 0), rgb(0, 0, 255), rgb(255, 0, 0), rgb(0, 255, 255)),
            intArrayOf(rgb(255, 255, 255), rgb(0, 0, 0), rgb(255, 0, 255), rgb(0, 255, 0))
        )
        for (cover in covers) {
            val palette = LyricCardBackground.palette(cover, 2, 2)
            for (y in 0..20) for (x in 0..20) {
                val u = x / 20.0
                val v = y / 20.0
                val channels = IntArray(3) { c ->
                    val shift = 16 - c * 8
                    val top = channel(palette.corners[0], shift) * (1 - u) + channel(palette.corners[1], shift) * u
                    val bottom = channel(palette.corners[2], shift) * (1 - u) + channel(palette.corners[3], shift) * u
                    (top * (1 - v) + bottom * v).toInt()
                }
                val background = rgb(channels[0], channels[1], channels[2])
                assertTrue(contrast(palette.translationColor, background) >= 4.5)
            }
        }
    }

    private fun rgb(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b
    private fun channel(colour: Int, shift: Int) = (colour ushr shift) and 255
    private fun contrast(foreground: Int, background: Int): Double {
        val alpha = (foreground ushr 24) / 255.0
        val visible = IntArray(3) { index ->
            val shift = 16 - index * 8
            (channel(foreground, shift) * alpha + channel(background, shift) * (1 - alpha)).toInt()
        }
        val a = luminance(visible)
        val b = luminance(IntArray(3) { channel(background, 16 - it * 8) })
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
    private fun luminance(rgb: IntArray): Double = rgb.map {
        val srgb = it / 255.0
        if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
    }.let { it[0] * 0.2126 + it[1] * 0.7152 + it[2] * 0.0722 }
}
