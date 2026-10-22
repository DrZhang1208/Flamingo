package yos.music.player.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.*
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.theme.YosRoundedCornerShape
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class NativeLyricGlyph(
    val left: Float, val right: Float, val top: Float, val bottom: Float,
    val motion: NativeWordMotion, val whitespace: Boolean,
    val highlightStart: Float = 0f, val highlightEnd: Float = 1f,
    val upliftMotion: NativeWordMotion = motion, val rtl: Boolean = false,
    val textOffset: Int = 0, val emphasisDelayMs: Float = 0f,
    val emphasisMotion: NativeWordMotion = motion,
    val emphasisIndex: Int = 0, val emphasisCount: Int = 1,
    val emphasisCenterX: Float = (left + right) * 0.5f,
    val emphasisWordWidth: Float = right - left,
    val textEnd: Int = textOffset + 1,
    val inkLeft: Float = left, val baseline: Float = bottom,
    val independentInk: Boolean = false
)

internal data class NativeLyricVisualRow(val glyphs: List<NativeLyricGlyph>, val left: Float,
    val right: Float, val feather: Float, val rtl: Boolean) {
    private var gradient: android.graphics.LinearGradient? = null
    private var fromColor = 0
    private var toColor = 0
    private val fieldPlacement = android.graphics.Matrix()
    fun colorField(edge: Float, feather: Float, from: Int, to: Int): android.graphics.Shader {
        if (gradient == null || fromColor != from || toColor != to) {
            fromColor = from; toColor = to
            gradient = android.graphics.LinearGradient(0f, 0f, 1f, 0f,
                from, to, android.graphics.Shader.TileMode.CLAMP)
        }
        fieldPlacement.setScale(feather * 2f, 1f)
        fieldPlacement.postTranslate(edge - feather, 0f)
        gradient!!.setLocalMatrix(fieldPlacement)
        return gradient!!
    }
}
internal data class NativeLyricParagraph(val layout: TextLayoutResult, val glyphs: List<NativeLyricGlyph>,
    val rows: List<NativeLyricVisualRow> = emptyList()) {
    val surface by lazy(LazyThreadSafetyMode.NONE) { NativeLyricSurface(layout) }
    // Prepared with shaping, rather than scanned for every frame/redraw.
    val quietBeforeMs = glyphs.minOfOrNull { it.motion.startMs }?.minus(1000f) ?: Float.NEGATIVE_INFINITY
    val quietAfterMs = glyphs.maxOfOrNull { it.motion.endMs }?.plus(1000f) ?: Float.POSITIVE_INFINITY
    val fontSizePx = with(layout.layoutInput.density) {
        if (layout.layoutInput.style.fontSize.isSp) layout.layoutInput.style.fontSize.toPx() else 16.sp.toPx()
    }
    val lastNoteEnd = glyphs.maxOfOrNull { it.motion.endMs } ?: 0f
    val emphasisQuietAfterMs = maxOf(quietAfterMs, glyphs.asSequence()
        .filter { !it.whitespace && !it.rtl }
        .maxOfOrNull {
            nativeTtmlEmphasisEndMs(it.emphasisMotion.startMs, it.emphasisMotion.endMs,
                it.emphasisCount, it.emphasisMotion.endMs >= lastNoteEnd - 1f)
        } ?: quietAfterMs)
}

private val nativeLatinWords = Regex("[\\p{IsLatin}\\p{M}\\p{N}]+(?:['’\\-][\\p{IsLatin}\\p{M}\\p{N}]+)*")

/** Before layout, source-token bounds also cover words joined across syllable tokens. */
internal fun nativeLyricEmphasisEndMs(tokens: List<YosLyricToken>): Float {
    val lastNoteEnd = tokens.maxOfOrNull { it.endMs } ?: return Float.NEGATIVE_INFINITY
    var end = lastNoteEnd
    var offset = 0
    val sources = tokens.map { token ->
        val range = offset until offset + token.text.length
        offset += token.text.length
        val count = lyricGraphemeRanges(token.text).count { part ->
            token.text.substring(part.first, part.last + 1).any { !it.isWhitespace() }
        }
        if (count > 0) end = maxOf(end, nativeTtmlEmphasisEndMs(token.startMs, token.endMs,
            count, token.endMs >= lastNoteEnd - 1f))
        range to token
    }
    for (word in nativeLatinWords.findAll(tokens.joinToString("") { it.text })) {
        val contributing = sources.filter { (range, _) -> range.first <= word.range.last && range.last >= word.range.first }
        if (contributing.isEmpty()) continue
        val start = contributing.minOf { it.second.startMs }
        val finish = contributing.maxOf { it.second.endMs }
        val count = contributing.sumOf { (range, token) ->
            lyricGraphemeRanges(token.text).count { range.first + it.first in word.range }
        }
        end = maxOf(end, nativeTtmlEmphasisEndMs(start, finish, count, finish >= lastNoteEnd - 1f))
    }
    return end
}

/** Geometry is built only when text, font or constraints change. Shaping remains paragraph based. */
internal fun nativeLyricParagraph(layout: TextLayoutResult, tokens: List<YosLyricToken>): NativeLyricParagraph {
    val text = layout.layoutInput.text.text
    val glyphs = ArrayList<NativeLyricGlyph>()
    var tokenOffset = 0
    var previousEnd = Float.NEGATIVE_INFINITY
    val lastNoteEnd = tokens.maxOfOrNull { max(it.endMs, it.startMs + 1f) } ?: 0f
    tokens.forEach { token ->
        val startOffset = tokenOffset
        tokenOffset += token.text.length
        val firstGlyph = glyphs.size
        val motion = NativeWordMotion(token.startMs, max(token.endMs, token.startMs + 1f),
            if (previousEnd == Float.NEGATIVE_INFINITY || token.startMs - previousEnd <= 180f) 160f else 0f)
        previousEnd = max(previousEnd, token.endMs)
        lyricGraphemeRanges(token.text).forEach { range ->
            val offset = startOffset + range.first
            if (offset >= text.length) return@forEach
            val bounds = layout.getBoundingBox(offset)
            val visualLine = layout.getLineForOffset(offset)
            // A single paragraph still handles ligatures, bidi and fallback fonts.
            var left = bounds.left
            var right = bounds.right
            for (part in range.first..range.last) {
                val partOffset = startOffset + part
                if (partOffset < text.length && layout.getLineForOffset(partOffset) == visualLine) {
                    val box = layout.getBoundingBox(partOffset)
                    left = min(left, box.left); right = max(right, box.right)
                }
            }
            if (right > left) glyphs += NativeLyricGlyph(left, right, layout.getLineTop(visualLine),
                layout.getLineBottom(visualLine), motion,
                token.text.substring(range.first, range.last + 1).all(Char::isWhitespace),
                rtl = layout.getBidiRunDirection(offset) == ResolvedTextDirection.Rtl, textOffset = offset,
                textEnd = (startOffset + range.last + 1).coerceAtMost(text.length),
                inkLeft = left, baseline = layout.getLineBaseline(visualLine),
                independentInk = token.text.substring(range.first, range.last + 1).any { it.code in 33..126 })
        }
        // A source word can span spaces and visual lines. Allocate its sweep over
        // measured advances while keeping one uplift envelope for the whole word.
        val emphasizedGlyphs = (firstGlyph until glyphs.size).filter { !glyphs[it].whitespace }
        val emphasisWidth = emphasizedGlyphs.sumOf { (glyphs[it].right - glyphs[it].left).toDouble() }.toFloat()
        emphasizedGlyphs.forEachIndexed { index, glyphIndex ->
            glyphs[glyphIndex] = glyphs[glyphIndex].copy(
                emphasisDelayMs = nativeTtmlGlyphDelayMs(index, emphasizedGlyphs.size, motion.endMs - motion.startMs,
                    terminal = motion.endMs >= lastNoteEnd - 1f),
                emphasisIndex = index, emphasisCount = emphasizedGlyphs.size, emphasisWordWidth = emphasisWidth)
        }
        val total = (firstGlyph until glyphs.size).sumOf { (glyphs[it].right - glyphs[it].left).toDouble() }.toFloat()
        var advance = 0f
        for (index in firstGlyph until glyphs.size) {
            val glyph = glyphs[index]
            val width = glyph.right - glyph.left
            val firstFraction = advance / total.coerceAtLeast(1f)
            val lastFraction = (advance + width) / total.coerceAtLeast(1f)
            val duration = motion.endMs - motion.startMs
            glyphs[index] = glyph.copy(highlightStart = firstFraction, highlightEnd = lastFraction,
                upliftMotion = NativeWordMotion(motion.startMs + duration * firstFraction,
                    motion.startMs + duration * lastFraction,
                    if (index == firstGlyph) motion.leadMs else 200f))
            advance += width
        }
    }
    // Latin letters must retain one shaped surface while moving. Source timing can
    // split a word into multiple tokens, so group by paragraph text instead.
    for (word in nativeLatinWords.findAll(text)) {
        val indices = glyphs.indices.filter { glyphs[it].textOffset in word.range }
        if (indices.isEmpty()) continue
        val start = indices.minOf { glyphs[it].upliftMotion.startMs }
        val end = indices.maxOf { glyphs[it].upliftMotion.endMs }
        val motion = NativeWordMotion(start, end, glyphs[indices.first()].upliftMotion.leadMs)
        val wordWidth = indices.sumOf { (glyphs[it].right - glyphs[it].left).toDouble() }.toFloat()
        for ((order, index) in indices.withIndex()) glyphs[index] = glyphs[index].copy(
            upliftMotion = motion, emphasisMotion = motion, independentInk = true,
            emphasisDelayMs = nativeTtmlGlyphDelayMs(order, indices.size, end - start,
                terminal = end >= lastNoteEnd - 1f),
            emphasisIndex = order, emphasisCount = indices.size, emphasisWordWidth = wordWidth)
    }
    // Split whitespace at its centre and let each half move with its neighbour.
    // Cuts now fall in empty space, preserving j/f overhangs and word-edge ink.
    val spaced = glyphs.groupBy { it.top }.values.flatMap { row ->
        val ordered = row.sortedBy { it.left }
        ordered.flatMapIndexed { index, glyph ->
            if (!glyph.whitespace) listOf(glyph) else {
                val before = ordered.take(index).lastOrNull { !it.whitespace }
                val after = ordered.drop(index + 1).firstOrNull { !it.whitespace }
                val middle = (glyph.left + glyph.right) / 2f
                val fraction = (glyph.highlightStart + glyph.highlightEnd) / 2f
                listOf(glyph.copy(right = middle, highlightEnd = fraction,
                    upliftMotion = (before ?: after)?.upliftMotion ?: glyph.upliftMotion,
                    independentInk = (before ?: after)?.independentInk ?: false,
                    emphasisDelayMs = (before ?: after)?.emphasisDelayMs ?: glyph.emphasisDelayMs,
                    emphasisMotion = (before ?: after)?.emphasisMotion ?: glyph.emphasisMotion,
                    emphasisCenterX = (before ?: after)?.emphasisCenterX ?: glyph.emphasisCenterX,
                    emphasisIndex = (before ?: after)?.emphasisIndex ?: glyph.emphasisIndex,
                    emphasisCount = (before ?: after)?.emphasisCount ?: glyph.emphasisCount,
                    emphasisWordWidth = (before ?: after)?.emphasisWordWidth ?: glyph.emphasisWordWidth),
                    glyph.copy(left = middle, highlightStart = fraction,
                        upliftMotion = (after ?: before)?.upliftMotion ?: glyph.upliftMotion,
                        independentInk = (after ?: before)?.independentInk ?: false,
                        emphasisDelayMs = (after ?: before)?.emphasisDelayMs ?: glyph.emphasisDelayMs,
                        emphasisMotion = (after ?: before)?.emphasisMotion ?: glyph.emphasisMotion,
                        emphasisCenterX = (after ?: before)?.emphasisCenterX ?: glyph.emphasisCenterX,
                        emphasisIndex = (after ?: before)?.emphasisIndex ?: glyph.emphasisIndex,
                        emphasisCount = (after ?: before)?.emphasisCount ?: glyph.emphasisCount,
                        emphasisWordWidth = (after ?: before)?.emphasisWordWidth ?: glyph.emphasisWordWidth))
            }
        }
    }
    // Sort by visual coordinates, not logical order, so RTL clips partition the surface too.
    val partitioned = spaced.groupBy { it.top }.values.flatMap { row ->
        val ordered = row.sortedBy { it.left }
        ordered.mapIndexed { index, glyph ->
            glyph.copy(left = if (index == 0) layout.getLineLeft(layout.getLineForVerticalPosition(glyph.top))
                else (ordered[index - 1].right + glyph.left) / 2f,
                right = if (index == ordered.lastIndex) layout.getLineRight(layout.getLineForVerticalPosition(glyph.top))
                else (glyph.right + ordered[index + 1].left) / 2f)
        }
    }
    val rows = partitioned.groupBy { it.top }.values.map { row ->
        NativeLyricVisualRow(row, row.first().left, row.last().right,
            row.maxOf { it.right - it.left } * 1.2f, row.count { it.rtl } > row.size / 2)
    }
    return NativeLyricParagraph(layout, partitioned, rows)
}

private fun NativeLyricGlyph.highlightProgress(timeMs: Float): Float =
    ((motion.highlight(timeMs) - highlightStart) / (highlightEnd - highlightStart).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)

/** Each glyph has one surface: the colour field and rise move together. No dim underlay. */
internal fun DrawScope.drawNativeLyricParagraph(
    paragraph: NativeLyricParagraph, timeMs: Float, bright: Color, dim: Color,
    upliftPx: Float, highlight: Boolean = true, feathered: Boolean = true,
    lineHighlight: NativeLineHighlight? = null, ttmlEffects: Boolean = false, effectStrength: Float = 1f
) {
    if (lineHighlight != null) {
        // Line-only lyrics share one colour across all wrapped fragments. Keep
        // this before the quiet-row shortcut: a long gap can outlive word timing
        // while the outgoing line still needs its full brightness transition.
        val strength = if (highlight) lineHighlight.strengthAt(timeMs) else 0f
        val color = when {
            strength <= 0f -> dim
            strength >= 1f -> bright
            else -> lerp(dim, bright, strength)
        }
        // Line timing changes only the colour; keep the whole paragraph's baseline fixed.
        paragraph.surface.drawSolid(this, color, 0f)
        return
    }
    // Settled rows have one colour and one vertical offset. Draw the shaped
    // paragraph once instead of clipping/redrawing it for each visual row/word.
    // Emphasis tails can outlive the note, so wait for their envelope too.
    val settledAfterMs = if (ttmlEffects && highlight && effectStrength > 0f)
        paragraph.emphasisQuietAfterMs else paragraph.quietAfterMs
    if (timeMs <= paragraph.quietBeforeMs || timeMs >= settledAfterMs) {
        val finished = timeMs >= settledAfterMs
        val lit = highlight && finished
        paragraph.surface.drawSolid(this, if (lit) bright else dim, if (finished) upliftPx else 0f)
        return
    }
    for (row in paragraph.rows) {
        val glyphs = row.glyphs
        val feather = if (feathered) max(row.feather, 8.dp.toPx()) else 0.1f
        var cursor = if (row.rtl) row.right else row.left
        if (highlight) for (glyph in glyphs) {
            val start = glyph.motion.startMs + (glyph.motion.endMs - glyph.motion.startMs) * glyph.highlightStart
            if (timeMs < start) continue
            val progress = glyph.highlightProgress(timeMs)
            val candidate = if (row.rtl) glyph.right - (glyph.right - glyph.left) * progress
                else glyph.left + (glyph.right - glyph.left) * progress
            cursor = if (row.rtl) min(cursor, candidate) else max(cursor, candidate)
        }
        val edge = nativeLineHighlightEdge(row.left, row.right, cursor, feather)
        // The same colour field covers all words; it never restarts at a character
        // boundary. Only the clip/vertical motion differs between neighbouring glyphs.
        val complete = if (row.rtl) cursor <= row.left else cursor >= row.right
        val untouched = if (row.rtl) cursor >= row.right else cursor <= row.left
        // Solid endpoints also avoid shader/solid alpha rounding differences in
        // antialiased text. The animated field approaches these colours continuously.
        val colorField = if (complete || untouched) null else
            row.colorField(edge, feather,
                if (row.rtl) dim.toArgb() else bright.toArgb(),
                if (row.rtl) bright.toArgb() else dim.toArgb())
        var index = 0
        while (index < glyphs.size) {
            val first = glyphs[index]
            val rise = first.upliftMotion.uplift(timeMs) * upliftPx
            val note = first.emphasisMotion
            val noteDuration = note.endMs - note.startMs
            // Keep joined RTL scripts on their existing shaped-word path.
            val emphasize = ttmlEffects && highlight && effectStrength > 0f && !first.rtl &&
                noteDuration >= NATIVE_TTML_MIN_NOTE_MS
            val separateInk = ttmlEffects && first.independentInk && !first.rtl
            var end = index + 1
            while (!separateInk && !emphasize && end < glyphs.size &&
                !(ttmlEffects && glyphs[end].independentInk && !glyphs[end].rtl) &&
                abs(glyphs[end].upliftMotion.uplift(timeMs) * upliftPx - rise) <= 0.01f &&
                (!ttmlEffects || (glyphs[end].motion == first.motion &&
                    glyphs[end].emphasisDelayMs == first.emphasisDelayMs))) end++
            val noteTime = timeMs - first.emphasisDelayMs
            val terminal = note.endMs >= paragraph.lastNoteEnd - 1f
            val glow = if (emphasize) nativeTtmlGlowStrength(note.startMs, note.endMs, noteTime,
                terminal) * effectStrength else 0f
            // Lift runs on its own longer envelope so the rise keeps breathing
            // after the crest has passed, instead of snapping back with it.
            val lift = if (emphasize) nativeTtmlLiftStrength(note.startMs, note.endMs, noteTime,
                terminal) * effectStrength else 0f
            val visualRise = rise + if (upliftPx > 0f) nativeTtmlExtraRiseDp(lift).dp.toPx() else 0f
            val solid = if (complete) bright else dim
            if (separateInk || glow > 0f || lift > 0f) {
                val zoom = nativeTtmlEmphasisScale(glow, noteDuration, terminal,
                    first.emphasisWordWidth / paragraph.fontSizePx.coerceAtLeast(1f))
                scale(zoom, zoom, pivot = Offset(first.emphasisCenterX,
                    (first.top + first.bottom) * 0.5f - visualRise)) {
                    paragraph.surface.drawEmphasized(this, first, colorField, solid, bright, visualRise, glow)
                }
            } else {
                clipRect(first.left, first.top - rise, glyphs[end - 1].right, first.bottom - rise) {
                    if (colorField == null) paragraph.surface.drawSolid(this, solid, rise)
                    else paragraph.surface.draw(this, colorField, rise)
                }
            }
            index = end
        }
    }
}

private class NativeParagraphCache {
    var key: Any? = null
    var paragraph: NativeLyricParagraph? = null
    var topInsetPx = 0f
    var bottomInsetPx = 0f
}

@Composable
private fun NativeLyricText(
    tokens: List<YosLyricToken>, style: TextStyle, measurer: TextMeasurer,
    time: () -> Float, bright: Color, dim: Color, staticText: Boolean,
    highlight: Boolean, feathered: Boolean, uplift: Boolean,
    lineHighlight: NativeLineHighlight?, ttmlEffects: Boolean, feedback: () -> Float
) {
    val cache = remember { NativeParagraphCache() }
    val preparedCache = LocalNativeLyricRenderCache.current
    val timeRef = rememberUpdatedState(time)
    val feedbackRef = rememberUpdatedState(feedback)
    val text = remember(tokens) { tokens.joinToString("") { it.text } }
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val density = LocalDensity.current
    val independentStyle = remember(style, ttmlEffects, staticText, lineHighlight != null) {
        if (ttmlEffects && !staticText && lineHighlight == null) nativeIndependentGlyphStyle(style) else style
    }
    // Explicit padding reserves ascender space, without a second size callback/layout pass.
    val risePx = with(density) { 4.dp.toPx() }
    Layout(content = {
        Canvas(Modifier.fillMaxWidth()) {
            val paragraph = cache.paragraph ?: return@Canvas
            translate(top = cache.topInsetPx) {
                if (staticText) drawText(paragraph.layout, color = bright)
                else {
                    val held = feedbackRef.value().coerceIn(0f, 1f)
                    // Hover is a readable whole line. Playback timing continues underneath,
                    // while individual word rise/stroke blend gently back to the baseline.
                    drawNativeLyricParagraph(paragraph, timeRef.value(), bright, lerp(dim, bright, held),
                        if (uplift) risePx * 0.5f * (1f - held) else 0f,
                        highlight, feathered, lineHighlight, ttmlEffects, effectStrength = 1f - held)
                }
            }
        }
    }) { children, constraints ->
        val layoutKey = NativeLyricLayoutKey(tokens, independentStyle, constraints.maxWidth,
            density.density, density.fontScale, direction, staticText)
        if (cache.key != layoutKey) {
            cache.paragraph = preparedCache?.borrow(layoutKey) ?: run {
                val result = measurer.measure(AnnotatedString(text), independentStyle,
                    constraints = Constraints(maxWidth = constraints.maxWidth), layoutDirection = direction)
                if (staticText) NativeLyricParagraph(result, emptyList()) else nativeLyricParagraph(result, tokens)
                // 主线程兜底构建的段落也回填仓内：回滚或重开时同样的行秒命中。
            }.also { built -> preparedCache?.put(layoutKey, built) }
            // Halo/scale are drawing effects, not extra line spacing. The row's
            // existing 10dp/14dp outer padding accommodates their visual overflow.
            cache.topInsetPx = risePx
            cache.bottomInsetPx = 2f
            cache.key = layoutKey
        }
        val result = checkNotNull(cache.paragraph).layout
        val height = constraints.constrainHeight(result.size.height + kotlin.math.ceil(cache.topInsetPx + cache.bottomInsetPx).toInt())
        val child = children.single().measure(Constraints.fixed(constraints.maxWidth, height))
        layout(child.width, child.height) { child.place(0, 0) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NativeLyricRow(
    line: YosLyricLine, tokens: List<YosLyricToken>, staticText: Boolean,
    active: Boolean, focused: Boolean, past: Boolean, blur: Float, nextStart: Float?,
    seekRevision: Int = 0, nextIsCountdown: Boolean = false,
    time: () -> Float, showTranslation: Boolean, mainColor: Color, subColor: Color,
    measurer: TextMeasurer, fontFamily: FontFamily?, onClick: () -> Unit, onLongClick: () -> Unit,
    interactionEnabled: Boolean = true, onPressChanged: (Boolean) -> Unit = {},
    animationsEnabled: Boolean = true,
    seekPreview: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val feedbackEnabled = interactionEnabled && !line.isCountdown
    val holding = feedbackEnabled && (pressed || hovered)
    val onPressChangedRef = rememberUpdatedState(onPressChanged)
    LaunchedEffect(feedbackEnabled, pressed) { onPressChangedRef.value(feedbackEnabled && pressed) }
    DisposableEffect(interactionSource) {
        onDispose { onPressChangedRef.value(false) }
    }
    val hoverShape = remember { YosRoundedCornerShape(14.dp) }
    val feedback = animateFloatAsState(if (holding) 1f else 0f,
        if (animationsEnabled) spring(dampingRatio = 1f, stiffness = 650f) else snap(), label = "NativeLyricPress")
    val animatedFocus by animateFloatAsState(if (focused) 1f else 0f,
        if (animationsEnabled) spring(dampingRatio = 0.9f, stiffness = 320f) else snap(), label = "NativeLyricFocus")
    val focus = if (animationsEnabled) animatedFocus else if (focused) 1f else 0f
    // A seek changes the sweep immediately. Reset emphasis in the same composition
    // so skipped rows never draw a new sweep through their old opacity animation.
    val animatedAlpha by key(seekRevision, SettingsLibrary.EnableWordByWordLyric) {
        animateFloatAsState(if (active || staticText || !past) 1f else 0.3f,
            if (animationsEnabled) spring(dampingRatio = 1f, stiffness = 260f) else snap(), label = "NativeLyricEmphasis")
    }
    val alpha = if (animationsEnabled) animatedAlpha else if (active || staticText || !past) 1f else 0.3f
    val scale = SettingsLibrary.LyricFontScale.coerceIn(0.8f, 1.2f)
    val side = line.otherSide || line.agent == "bg"
    val style = mainTextStyle(fontFamily, SettingsLibrary.LyricFontSize * scale)
        .copy(textAlign = if (side) TextAlign.End else TextAlign.Start)
    val lineByLine = !SettingsLibrary.EnableWordByWordLyric || !line.hasWordTiming
    val lineHighlight = remember(lineByLine, line.startMs, nextStart, nextIsCountdown) {
        if (lineByLine) nativePlaybackLineHighlight(line.startMs, nextStart, nextIsCountdown) else null
    }
    val highlight = SettingsLibrary.DebugLyricEnableHighlight
    val feathered = SettingsLibrary.DebugLyricEnableGlow
    val uplift = SettingsLibrary.DebugLyricEnableUplift
    val end = nextStart?.takeIf { it > line.startMs } ?: max(line.endMs, line.startMs + 1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val trailingInset = if (line.isCountdown || staticText) 29.dp else
        with(LocalDensity.current) { nativeLyricFocusTrailingInset(maxWidth.toPx(), 29.dp.toPx()).toDp() }
    Column(Modifier
        // A shrinking countdown slot would reverse the list near its end even
        // while pointer time moves forward. Scrubbing keeps its layout space fixed.
        .then(if (line.isCountdown && !seekPreview) Modifier.lyricCountdownLayout(time, line.startMs, end, centerContent = true) else Modifier)
        .padding(start = if (side) trailingInset else 29.dp, end = if (side) 29.dp else trailingInset)
        .graphicsLayer {
            // Keep the leading baseline fixed through emphasis. Growth is
            // reserved at the other edge, so layout and wrapping stay stable.
            val focusScale = if (line.isCountdown || staticText) 1f else 1f + focus * (NATIVE_LYRIC_FOCUS_SCALE - 1f)
            // Stay inside the existing emphasis margins; pressing must not reflow text.
            scaleX = focusScale * (1f - feedback.value * 0.012f)
            scaleY = scaleX
            translationY = -1.5.dp.toPx() * feedback.value
            transformOrigin = TransformOrigin(if (side) 1f else 0f, 0.5f)
        }
        .drawWithCache {
            val inset = 8.dp.toPx()
            val outline = hoverShape.createOutline(Size(size.width + inset * 2f, size.height), layoutDirection, this)
            onDrawBehind {
                val strength = feedback.value
                if (strength > 0f) translate(left = -inset) {
                    drawOutline(outline, mainColor.copy(alpha = 0.09f * strength))
                }
            }
        }
        .graphicsLayer {
            // Keep this node in place. Removing a blur modifier during DOWN can
            // detach/cancel the click recognizer on quiet, blurred rows.
            renderEffect = if (blur > 0.25f && !staticText && !holding)
                BlurEffect(blur.dp.toPx(), blur.dp.toPx(), TileMode.Decal) else null
        }
        .hoverable(interactionSource, enabled = feedbackEnabled)
        .combinedClickable(enabled = interactionEnabled, indication = null, interactionSource = interactionSource,
            onClick = onClick, onLongClick = onLongClick)
        .padding(top = 10.dp, bottom = 14.dp)) {
        if (line.isCountdown) {
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                drawNativeCountdown(time(), line.startMs, end, mainColor)
            }
        } else {
            Box(Modifier.graphicsLayer {
                val playbackAlpha = if (staticText || lineByLine) 1f else alpha
                this.alpha = playbackAlpha + (1f - playbackAlpha) * feedback.value
            }) {
                NativeLyricText(tokens, style, measurer, time, mainColor, mainColor.copy(alpha = 0.28f),
                    staticText, highlight, feathered, uplift, lineHighlight, line.hasWordTiming,
                    feedback = { feedback.value })
            }
            if (showTranslation && line.translation.isNotBlank()) {
                Text(line.translation, fontSize = (SettingsLibrary.TranslationFontSize * scale).sp,
                    lineHeight = (SettingsLibrary.TranslationFontSize * scale + 5f).sp,
                    color = subColor.copy(alpha = subColor.alpha * 0.35f),
                    fontFamily = fontFamily, fontWeight = style.fontWeight,
                    textAlign = style.textAlign,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
    }
}
