package yos.music.player.ui.widgets

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.sin

internal const val NATIVE_TTML_MIN_NOTE_MS = 1000f

private val emphasisIn = CubicBezierEasing(0.2f, 0.4f, 0.58f, 1f)
private val emphasisOut = CubicBezierEasing(0.3f, 0f, 0.58f, 1f)

internal fun nativeTtmlEmphasisDurationMs(durationMs: Float, terminal: Boolean = false): Float =
    durationMs.coerceAtLeast(NATIVE_TTML_MIN_NOTE_MS) * if (terminal) 1.2f else 1f

internal fun nativeTtmlGlyphStepMs(durationMs: Float, glyphCount: Int, terminal: Boolean = false): Float =
    nativeTtmlEmphasisDurationMs(durationMs, terminal) / (2.5f * glyphCount.coerceAtLeast(1))

internal fun nativeTtmlGlowStrength(startMs: Float, endMs: Float, timeMs: Float, terminal: Boolean = false): Float {
    val duration = endMs - startMs
    if (duration < NATIVE_TTML_MIN_NOTE_MS) return 0f
    val progress = (timeMs - startMs) / nativeTtmlEmphasisDurationMs(duration, terminal)
    if (progress <= 0f || progress >= 1f) return 0f
    return if (progress < 0.5f) emphasisIn.transform(progress * 2f)
        else 1f - emphasisOut.transform((progress - 0.5f) * 2f)
}

internal fun nativeTtmlLiftStrength(startMs: Float, endMs: Float, timeMs: Float, terminal: Boolean = false): Float {
    val duration = endMs - startMs
    if (duration < NATIVE_TTML_MIN_NOTE_MS) return 0f
    val progress = (timeMs - startMs + 400f) / (nativeTtmlEmphasisDurationMs(duration, terminal) * 1.4f)
    if (progress <= 0f || progress >= 1f) return 0f
    return sin(progress * PI).toFloat()
}

internal fun nativeTtmlEmphasisEndMs(startMs: Float, endMs: Float, glyphCount: Int = 1, terminal: Boolean = false): Float {
    val duration = endMs - startMs
    if (duration < NATIVE_TTML_MIN_NOTE_MS) return endMs
    val emphasisDuration = nativeTtmlEmphasisDurationMs(duration, terminal)
    val lastDelay = nativeTtmlGlyphDelayMs(glyphCount - 1, glyphCount, duration, terminal)
    return startMs + lastDelay + maxOf(emphasisDuration, emphasisDuration * 1.4f - 400f)
}

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

internal fun nativeTtmlGlyphDelayMs(index: Int, count: Int, durationMs: Float = 2000f, terminal: Boolean = false): Float =
    if (count <= 1) 0f else index.coerceIn(0, count - 1) * nativeTtmlGlyphStepMs(durationMs, count, terminal)
