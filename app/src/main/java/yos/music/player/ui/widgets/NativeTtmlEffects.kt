package yos.music.player.ui.widgets

internal const val NATIVE_TTML_MIN_NOTE_MS = 1000f

/** A broad cosine-fourth wave: smooth tails and constant summed crest weight across glyphs. */
internal fun nativeTtmlGlyphStepMs(durationMs: Float, glyphCount: Int): Float =
    (durationMs.coerceAtLeast(0f) / glyphCount.coerceAtLeast(1)).coerceAtMost(if (glyphCount <= 1) 320f else 160f)

internal fun nativeTtmlGlowStrength(startMs: Float, endMs: Float, timeMs: Float, glyphCount: Int = 1, terminal: Boolean = false): Float {
    val duration = endMs - startMs
    if (duration < NATIVE_TTML_MIN_NOTE_MS) return 0f
    val step = nativeTtmlGlyphStepMs(duration, glyphCount)
    val peak = startMs + step * 0.5f
    val offset = timeMs - peak
    // Keep the incoming overlap broad at the sentence end, only shorten its exit.
    val radius = if (terminal && glyphCount <= 1 && offset > 0f) minOf(400f, step * 3f) else step * 3f
    val phase = offset / radius
    if (kotlin.math.abs(phase) >= 1f) return 0f
    val crest = kotlin.math.cos(phase * (kotlin.math.PI / 2.0)).toFloat()
    val squared = crest * crest
    return squared * squared
}

internal fun nativeTtmlLiftStrength(startMs: Float, endMs: Float, timeMs: Float, glyphCount: Int = 1, terminal: Boolean = false): Float =
    nativeTtmlGlowStrength(startMs, endMs, timeMs, glyphCount, terminal)

/** Uniform swell, capped both proportionally and by total word growth. */
internal fun nativeTtmlEmphasisScale(
    strength: Float, durationMs: Float = 2000f, isLastWord: Boolean = false, wordWidthEm: Float = 1f
): Float {
    if (durationMs < NATIVE_TTML_MIN_NOTE_MS) return 1f
    val amount = (0.035f + 0.035f * nativeSmoothStep((durationMs - 1000f) / 2000f)) *
        if (isLastWord) 1.15f else 1f
    val bounded = minOf(0.08f, amount, 0.28f / wordWidthEm.coerceAtLeast(1f))
    return 1f + bounded * strength.coerceIn(0f, 1f)
}

internal fun nativeTtmlExtraRiseDp(strength: Float): Float =
    1.5f * strength.coerceIn(0f, 1f)

internal fun nativeTtmlGlyphDelayMs(index: Int, count: Int, durationMs: Float = 2000f): Float =
    if (count <= 1) 0f else index.coerceIn(0, count - 1) * nativeTtmlGlyphStepMs(durationMs, count)
