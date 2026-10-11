package yos.music.player.ui.widgets

import kotlin.math.sin

/** A continuous pulse with zero velocity at the start, crest and end. */
private fun heldNotePulse(startMs: Float, endMs: Float, timeMs: Float): Float {
    if (timeMs <= startMs || timeMs >= endMs) return 0f
    val phase = (timeMs - startMs) / (endMs - startMs)
    val wave = sin(Math.PI * phase).toFloat()
    return wave * wave
}

/** Glow follows the whole note instead of switching quickly into a static plateau. */
internal fun nativeTtmlGlowStrength(startMs: Float, endMs: Float, timeMs: Float): Float =
    if (endMs - startMs < 900f) 0f else heldNotePulse(startMs, endMs, timeMs)

/** Complete the note-timed rise first, then return immediately with smooth endpoints. */
internal fun nativeTtmlLiftStrength(startMs: Float, endMs: Float, timeMs: Float): Float {
    val duration = endMs - startMs
    if (duration < 900f) return 0f
    val peak = endMs
    val rise = nativeSmoothStep((timeMs - (startMs - 180f)) / (peak - startMs + 180f))
    val settle = 1f - nativeSmoothStep((timeMs - peak) / 320f)
    return rise * settle
}

internal fun nativeTtmlEmphasisScale(strength: Float): Float =
    1f + 0.025f * strength.coerceIn(0f, 1f)

/** Extra lift returns to the ordinary word position without changing horizontal geometry. */
internal fun nativeTtmlExtraRiseDp(strength: Float): Float =
    3f * strength.coerceIn(0f, 1f)

/** Stagger graphemes in reading order, keeping even a long phrase within a short wave. */
internal fun nativeTtmlGlyphDelayMs(index: Int, count: Int): Float =
    if (count <= 1) 0f else index.coerceIn(0, count - 1) * minOf(65f, 260f / (count - 1))
