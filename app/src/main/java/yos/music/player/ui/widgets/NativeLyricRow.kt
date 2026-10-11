package yos.music.player.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class NativeLyricGlyph(
    val left: Float, val right: Float, val top: Float, val bottom: Float,
    val motion: NativeWordMotion, val whitespace: Boolean,
    val highlightStart: Float = 0f, val highlightEnd: Float = 1f,
    val upliftMotion: NativeWordMotion = motion, val rtl: Boolean = false,
    val textOffset: Int = 0, val emphasisDelayMs: Float = 0f
)

internal data class NativeLyricVisualRow(val glyphs: List<NativeLyricGlyph>, val left: Float,
    val right: Float, val feather: Float, val rtl: Boolean)
internal data class NativeLyricParagraph(val layout: TextLayoutResult, val glyphs: List<NativeLyricGlyph>,
    val rows: List<NativeLyricVisualRow> = emptyList()) {
    // Prepared with shaping, rather than scanned for every frame/redraw.
    val quietBeforeMs = glyphs.minOfOrNull { it.motion.startMs }?.minus(1000f) ?: Float.NEGATIVE_INFINITY
    val quietAfterMs = glyphs.maxOfOrNull { it.motion.endMs }?.plus(1000f) ?: Float.POSITIVE_INFINITY
}

/** Geometry is built only when text, font or constraints change. Shaping remains paragraph based. */
internal fun nativeLyricParagraph(layout: TextLayoutResult, tokens: List<YosLyricToken>): NativeLyricParagraph {
    val text = layout.layoutInput.text.text
    val glyphs = ArrayList<NativeLyricGlyph>()
    var tokenOffset = 0
    var previousEnd = Float.NEGATIVE_INFINITY
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
                rtl = layout.getBidiRunDirection(offset) == ResolvedTextDirection.Rtl, textOffset = offset)
        }
        // A source word can span spaces and visual lines. Allocate its sweep over
        // measured advances while keeping one uplift envelope for the whole word.
        val emphasizedGlyphs = (firstGlyph until glyphs.size).filter { !glyphs[it].whitespace }
        emphasizedGlyphs.forEachIndexed { index, glyphIndex ->
            glyphs[glyphIndex] = glyphs[glyphIndex].copy(
                emphasisDelayMs = nativeTtmlGlyphDelayMs(index, emphasizedGlyphs.size))
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
    val latinWords = Regex("[\\p{IsLatin}\\p{M}\\p{N}]+(?:['’\\-][\\p{IsLatin}\\p{M}\\p{N}]+)*")
    for (word in latinWords.findAll(text)) {
        val indices = glyphs.indices.filter { glyphs[it].textOffset in word.range }
        if (indices.isEmpty()) continue
        val start = indices.minOf { glyphs[it].upliftMotion.startMs }
        val end = indices.maxOf { glyphs[it].upliftMotion.endMs }
        val motion = NativeWordMotion(start, end, glyphs[indices.first()].upliftMotion.leadMs)
        for (index in indices) glyphs[index] = glyphs[index].copy(upliftMotion = motion)
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
                    emphasisDelayMs = (before ?: after)?.emphasisDelayMs ?: glyph.emphasisDelayMs),
                    glyph.copy(left = middle, highlightStart = fraction,
                        upliftMotion = (after ?: before)?.upliftMotion ?: glyph.upliftMotion,
                        emphasisDelayMs = (after ?: before)?.emphasisDelayMs ?: glyph.emphasisDelayMs))
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
    lineHighlight: NativeLineHighlight? = null, ttmlEffects: Boolean = false
) {
    // Settled rows have one colour and one vertical offset. Draw the shaped
    // paragraph once instead of clipping/redrawing it for each visual row/word.
    if (timeMs <= paragraph.quietBeforeMs || timeMs >= paragraph.quietAfterMs) {
        val finished = timeMs >= paragraph.quietAfterMs
        val lit = highlight && (lineHighlight?.activeAt(timeMs) ?: finished)
        drawText(paragraph.layout, color = if (lit) bright else dim,
            topLeft = Offset(0f, if (finished) -upliftPx else 0f),
            alpha = 1f, drawStyle = Fill, shadow = Shadow.None)
        return
    }
    for (row in paragraph.rows) {
        val glyphs = row.glyphs
        val feather = if (feathered) max(row.feather, 8.dp.toPx()) else 0.1f
        var cursor = if (row.rtl) row.right else row.left
        if (highlight && lineHighlight != null && lineHighlight.activeAt(timeMs)) {
            cursor = if (row.rtl) row.left else row.right
        }
        if (highlight && lineHighlight == null) for (glyph in glyphs) {
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
        val brush = if (complete || untouched) null else
            Brush.horizontalGradient(if (row.rtl) listOf(dim, bright) else listOf(bright, dim),
                edge - feather, edge + feather)
        var index = 0
        while (index < glyphs.size) {
            val first = glyphs[index]
            val rise = first.upliftMotion.uplift(timeMs) * upliftPx
            var end = index + 1
            while (end < glyphs.size &&
                abs(glyphs[end].upliftMotion.uplift(timeMs) * upliftPx - rise) <= 0.01f &&
                (!ttmlEffects || (glyphs[end].motion == first.motion &&
                    glyphs[end].emphasisDelayMs == first.emphasisDelayMs))) end++
            val glow = if (ttmlEffects && highlight && lineHighlight == null)
                nativeTtmlGlowStrength(first.motion.startMs, first.motion.endMs, timeMs) else 0f
            val lift = if (ttmlEffects && highlight && lineHighlight == null)
                nativeTtmlLiftStrength(first.motion.startMs, first.motion.endMs, timeMs - first.emphasisDelayMs) else 0f
            val visualRise = rise + if (upliftPx > 0f) nativeTtmlExtraRiseDp(lift).dp.toPx() else 0f
            // Transform the already partitioned shaped word, including its clip.
            // Scaling the text without transforming the clip cuts off its edges;
            // widening a clip instead would expose neighbouring paragraph glyphs.
            val emphasisScale = nativeTtmlEmphasisScale(lift)
            scale(1f, emphasisScale,
                pivot = Offset((first.left + glyphs[end - 1].right) / 2f,
                    (first.top + first.bottom) / 2f - visualRise)) {
                // The source is a whole shaped paragraph: never extend the
                // clip into another visual row, even to accommodate bloom.
                clipRect(first.left, first.top - visualRise,
                    glyphs[end - 1].right, first.bottom - visualRise) {
                    if (glow > 0f && !untouched) {
                        // Stroke is centred on glyph contours. Remove the complete
                        // glyph silhouette in an isolated layer, leaving only its
                        // outside edge, and never stroke the unplayed dim text.
                        val accent = if (complete) Brush.linearGradient(listOf(bright, bright)) else
                            Brush.horizontalGradient(
                                if (row.rtl) listOf(Color.Transparent, bright) else listOf(bright, Color.Transparent),
                                edge - feather, edge + feather)
                        val canvas = drawContext.canvas
                        canvas.saveLayer(Rect(first.left, first.top - visualRise,
                            glyphs[end - 1].right, first.bottom - visualRise), Paint())
                        try {
                            drawText(paragraph.layout, brush = accent, alpha = 1f,
                                topLeft = Offset(0f, -visualRise),
                                drawStyle = Stroke(width = 0.9.dp.toPx() * glow, join = StrokeJoin.Round),
                                shadow = Shadow.None)
                            drawText(paragraph.layout, color = Color.White, alpha = 1f,
                                topLeft = Offset(0f, -visualRise), drawStyle = Fill,
                                shadow = Shadow.None, blendMode = BlendMode.DstOut)
                        } finally {
                            canvas.restore()
                        }
                    }
                    // The ordinary filled surface is always the final pass, with
                    // its original sweep colour and opacity, independent of accents.
                    if (brush == null) drawText(paragraph.layout,
                        color = if (complete) bright else dim, topLeft = Offset(0f, -visualRise),
                        alpha = 1f, drawStyle = Fill, shadow = Shadow.None)
                    else drawText(paragraph.layout, brush = brush, alpha = 1f,
                        topLeft = Offset(0f, -visualRise), drawStyle = Fill, shadow = Shadow.None)
                }
            }
            index = end
        }
    }
}

private class NativeParagraphCache {
    var key: Any? = null
    var paragraph: NativeLyricParagraph? = null
}

@Composable
private fun NativeLyricText(
    tokens: List<YosLyricToken>, style: TextStyle, measurer: TextMeasurer,
    time: () -> Float, bright: Color, dim: Color, staticText: Boolean,
    highlight: Boolean, feathered: Boolean, uplift: Boolean,
    lineHighlight: NativeLineHighlight?, ttmlEffects: Boolean
) {
    val cache = remember { NativeParagraphCache() }
    val timeRef = rememberUpdatedState(time)
    val text = remember(tokens) { tokens.joinToString("") { it.text } }
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val density = LocalDensity.current
    // Explicit padding reserves ascender space, without a second size callback/layout pass.
    val risePx = with(density) { 4.dp.toPx() }
    Layout(content = {
        Canvas(Modifier.fillMaxWidth()) {
            val paragraph = cache.paragraph ?: return@Canvas
            translate(top = risePx) {
                if (staticText) drawText(paragraph.layout, color = bright)
                else drawNativeLyricParagraph(paragraph, timeRef.value(), bright, dim,
                    if (uplift) risePx else 0f, highlight, feathered, lineHighlight, ttmlEffects)
            }
        }
    }) { children, constraints ->
        val layoutKey = listOf(text, tokens, style, constraints.maxWidth, density.density, density.fontScale, direction, staticText)
        if (cache.key != layoutKey) {
            val result = measurer.measure(AnnotatedString(text), style,
                constraints = Constraints(maxWidth = constraints.maxWidth), layoutDirection = direction)
            cache.paragraph = if (staticText) NativeLyricParagraph(result, emptyList()) else nativeLyricParagraph(result, tokens)
            cache.key = layoutKey
        }
        val result = checkNotNull(cache.paragraph).layout
        val height = constraints.constrainHeight(result.size.height + risePx.toInt() + 2)
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
    interactionEnabled: Boolean = true
) {
    val focus by animateFloatAsState(if (focused) 1f else 0f,
        spring(dampingRatio = 0.9f, stiffness = 320f), label = "NativeLyricFocus")
    // A seek changes the sweep immediately. Reset emphasis in the same composition
    // so skipped rows never draw a new sweep through their old opacity animation.
    val alpha by key(seekRevision, SettingsLibrary.EnableWordByWordLyric) {
        animateFloatAsState(if (active || staticText || !past) 1f else 0.3f,
            spring(dampingRatio = 1f, stiffness = 260f), label = "NativeLyricEmphasis")
    }
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
        .then(if (line.isCountdown) Modifier.lyricCountdownLayout(time, line.startMs, end, centerContent = true) else Modifier)
        .padding(start = if (side) trailingInset else 29.dp, end = if (side) 29.dp else trailingInset)
        .graphicsLayer {
            // Keep the leading baseline fixed through emphasis. Growth is
            // reserved at the other edge, so layout and wrapping stay stable.
            scaleX = if (line.isCountdown || staticText) 1f else 1f + focus * (NATIVE_LYRIC_FOCUS_SCALE - 1f)
            scaleY = scaleX
            transformOrigin = TransformOrigin(if (side) 1f else 0f, 0.5f)
        }
        .then(if (blur > 0f && !staticText) Modifier.blur(blur.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
        .combinedClickable(enabled = interactionEnabled, indication = null, interactionSource = remember { MutableInteractionSource() },
            onClick = onClick, onLongClick = onLongClick)
        .padding(top = 10.dp, bottom = 14.dp)) {
        if (line.isCountdown) {
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                drawNativeCountdown(time(), line.startMs, end, mainColor)
            }
        } else {
            Box(Modifier.graphicsLayer { this.alpha = if (staticText || lineByLine) 1f else alpha }) {
                NativeLyricText(tokens, style, measurer, time, mainColor, mainColor.copy(alpha = 0.28f),
                    staticText, highlight, feathered, uplift, lineHighlight, line.ttmlEffects)
            }
            if (showTranslation && line.translation.isNotBlank()) {
                Text(line.translation, fontSize = (SettingsLibrary.TranslationFontSize * scale).sp,
                    lineHeight = (SettingsLibrary.TranslationFontSize * scale + 5f).sp,
                    color = subColor, fontFamily = fontFamily, textAlign = style.textAlign,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).graphicsLayer {
                        this.alpha = 0.35f + focus * 0.4f
                    })
            }
        }
    }
    }
}
