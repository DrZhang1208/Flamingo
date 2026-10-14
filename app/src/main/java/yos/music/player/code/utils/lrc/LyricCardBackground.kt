package yos.music.player.code.utils.lrc

import kotlin.math.pow
import kotlin.math.roundToInt

internal data class LyricCardPalette(
    val corners: IntArray,
    val textColor: Int,
    val translationColor: Int,
    val secondaryColor: Int,
    val usesDarkText: Boolean
)

/** Keep the player's colour field, removing only image detail and unreadable extremes. */
internal object LyricCardBackground {
    private const val LIGHT_TEXT = 0xFFFFF9E9.toInt()
    private const val DARK_TEXT = 0xFF241F26.toInt()

    fun palette(pixels: IntArray, width: Int, height: Int): LyricCardPalette {
        require(width >= 2 && height >= 2 && pixels.size == width * height)
        val sums = Array(4) { DoubleArray(4) }
        for (y in 0 until height) for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            val alpha = (pixel ushr 24) / 255.0
            val sum = sums[(if (y < height / 2) 0 else 2) + if (x < width / 2) 0 else 1]
            for (channel in 0..2) sum[channel] += channel(pixel, channel) * alpha
            sum[3] += alpha
        }
        val weight = sums.sumOf { it[3] }
        val fallback = doubleArrayOf(28.0, 30.0, 38.0)
        val mean = DoubleArray(3) { c ->
            if (weight > 0) sums.sumOf { it[c] } / weight else fallback[c]
        }
        val original = IntArray(4) { corner ->
            val sum = sums[corner]
            // Retain local colour and the player's gentle downward exposure fade.
            val exposure = if (corner < 2) 0.80 else 0.68
            rgb(IntArray(3) { c ->
                val local = if (sum[3] > 0) sum[c] / sum[3] else mean[c]
                ((mean[c] * 0.20 + local * 0.80) * exposure).roundToInt()
            })
        }
        val light = adjustForText(original, LIGHT_TEXT)
        val dark = adjustForText(original, DARK_TEXT)
        // Choose the foreground that requires the least change to the real colours.
        val usesDarkText = changeCost(original, dark) < changeCost(original, light)
        val text = if (usesDarkText) DARK_TEXT else LIGHT_TEXT
        return LyricCardPalette(
            if (usesDarkText) dark else light, text,
            (text and 0xFFFFFF) or (235 shl 24),
            (text and 0xFFFFFF) or (219 shl 24), usesDarkText
        )
    }

    private fun adjustForText(original: IntArray, text: Int): IntArray {
        val neutral = if (text == DARK_TEXT) 255 else 0
        fun mix(retained: Double) = IntArray(4) { corner ->
            rgb(IntArray(3) { c -> (channel(original[corner], c) * retained + neutral * (1 - retained)).roundToInt() })
        }
        // Test the interpolated field as well as its corners. Different saturated
        // colours can produce a darker midpoint even when both endpoints are bright.
        fun readable(colours: IntArray): Boolean {
            for (y in 0..8) for (x in 0..8) {
                val u = x / 8.0
                val v = y / 8.0
                val sample = IntArray(3) { c ->
                    val top = channel(colours[0], c) * (1 - u) + channel(colours[1], c) * u
                    val bottom = channel(colours[2], c) * (1 - u) + channel(colours[3], c) * u
                    (top * (1 - v) + bottom * v).roundToInt()
                }
                val ink = IntArray(3) { c -> (channel(text, c) * 235 + sample[c] * 20) / 255 }
                val background = luminance(sample)
                val foreground = luminance(ink)
                val ratio = (maxOf(background, foreground) + 0.05) / (minOf(background, foreground) + 0.05)
                if (ratio < 4.6) return false
            }
            return true
        }
        if (readable(original)) return original
        var low = 0.0
        var high = 1.0
        repeat(12) {
            val mid = (low + high) / 2
            if (readable(mix(mid))) low = mid else high = mid
        }
        return mix(low)
    }

    private fun changeCost(original: IntArray, adjusted: IntArray): Int = original.indices.sumOf { i ->
        (0..2).sumOf { c -> val delta = channel(original[i], c) - channel(adjusted[i], c); delta * delta }
    }
    private fun channel(colour: Int, channel: Int) = (colour ushr (16 - channel * 8)) and 255
    private fun rgb(channels: IntArray) = (255 shl 24) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
    private fun luminance(rgb: IntArray): Double = rgb.map {
        val srgb = it / 255.0
        if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
    }.let { it[0] * 0.2126 + it[1] * 0.7152 + it[2] * 0.0722 }
}
