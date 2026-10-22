package yos.music.player.ui.widgets

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.sin

internal const val NATIVE_TTML_MIN_NOTE_MS = 1000f

private val emphasisIn = CubicBezierEasing(0.2f, 0.4f, 0.58f, 1f)
private val emphasisOut = CubicBezierEasing(0.3f, 0f, 0.58f, 1f)

// 回落截止：辉光在窗口的比例位置处归零。普通词 0.85（音符结束前收干净）；
// 行尾词窗口放宽 1.2 倍是为长尾音保留更久的峰值，但截止提前到 0.7，
// 绝对结束点 0.84×时长，同样落在音符结束之前。
private const val GLOW_FALL_END = 0.85f
private const val TERMINAL_GLOW_FALL_END = 0.7f

internal fun nativeTtmlEmphasisDurationMs(durationMs: Float, terminal: Boolean = false): Float =
    durationMs.coerceAtLeast(NATIVE_TTML_MIN_NOTE_MS) * if (terminal) 1.2f else 1f

internal fun nativeTtmlGlyphStepMs(durationMs: Float, glyphCount: Int, terminal: Boolean = false): Float =
    nativeTtmlEmphasisDurationMs(durationMs, terminal) / (2.5f * glyphCount.coerceAtLeast(1))

internal fun nativeTtmlGlowStrength(startMs: Float, endMs: Float, timeMs: Float, terminal: Boolean = false): Float {
    val duration = endMs - startMs
    if (duration < NATIVE_TTML_MIN_NOTE_MS) return 0f
    val fallEnd = if (terminal) TERMINAL_GLOW_FALL_END else GLOW_FALL_END
    val progress = (timeMs - startMs) / nativeTtmlEmphasisDurationMs(duration, terminal)
    if (progress <= 0f || progress >= fallEnd) return 0f
    return if (progress < 0.5f) emphasisIn.transform(progress * 2f)
        else 1f - emphasisOut.transform((progress - 0.5f) / (fallEnd - 0.5f))
}

internal fun nativeTtmlLiftStrength(startMs: Float, endMs: Float, timeMs: Float, terminal: Boolean = false): Float {
    val duration = endMs - startMs
    if (duration < NATIVE_TTML_MIN_NOTE_MS) return 0f
    val fallEnd = if (terminal) TERMINAL_GLOW_FALL_END else GLOW_FALL_END
    // 升腾保留 400ms 预备量，但与辉光同点归零：回落不再越过音符拖尾。
    val span = nativeTtmlEmphasisDurationMs(duration, terminal) * fallEnd + 400f
    val progress = (timeMs - startMs + 400f) / span
    if (progress <= 0f || progress >= 1f) return 0f
    return sin(progress * PI).toFloat()
}

internal fun nativeTtmlEmphasisEndMs(startMs: Float, endMs: Float, glyphCount: Int = 1, terminal: Boolean = false): Float {
    val duration = endMs - startMs
    if (duration < NATIVE_TTML_MIN_NOTE_MS) return endMs
    val fallEnd = if (terminal) TERMINAL_GLOW_FALL_END else GLOW_FALL_END
    val lastDelay = nativeTtmlGlyphDelayMs(glyphCount - 1, glyphCount, duration, terminal)
    return startMs + lastDelay + nativeTtmlEmphasisDurationMs(duration, terminal) * fallEnd
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
