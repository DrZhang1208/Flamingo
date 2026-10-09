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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
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
    val upliftMotion: NativeWordMotion = motion, val rtl: Boolean = false
)

internal data class NativeLyricVisualRow(val glyphs: List<NativeLyricGlyph>, val left: Float,
    val right: Float, val feather: Float, val rtl: Boolean)
internal data class NativeLyricParagraph(val layout: TextLayoutResult, val glyphs: List<NativeLyricGlyph>,
    val rows: List<NativeLyricVisualRow> = emptyList())

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
                rtl = layout.getBidiRunDirection(offset) == ResolvedTextDirection.Rtl)
        }
        // A source word can span spaces and visual lines. Allocate its sweep over
        // measured advances while keeping one uplift envelope for the whole word.
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
    // Sort by visual coordinates, not logical order, so RTL clips partition the surface too.
    val partitioned = glyphs.groupBy { it.top }.values.flatMap { row ->
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
    upliftPx: Float, highlight: Boolean = true, feathered: Boolean = true
) {
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
        val brush = if (complete || untouched) null else
            Brush.horizontalGradient(if (row.rtl) listOf(dim, bright) else listOf(bright, dim),
                edge - feather, edge + feather)
        var index = 0
        while (index < glyphs.size) {
            val first = glyphs[index]
            val rise = first.upliftMotion.uplift(timeMs) * upliftPx
            var end = index + 1
            while (end < glyphs.size &&
                abs(glyphs[end].upliftMotion.uplift(timeMs) * upliftPx - rise) <= 0.01f) end++
            clipRect(first.left, first.top - rise, glyphs[end - 1].right, first.bottom - rise) {
                if (brush == null) drawText(paragraph.layout,
                    color = if (complete) bright else dim, topLeft = Offset(0f, -rise))
                else drawText(paragraph.layout, brush = brush, alpha = 1f, topLeft = Offset(0f, -rise))
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
    highlight: Boolean, feathered: Boolean, uplift: Boolean
) {
    val cache = remember { NativeParagraphCache() }
    val timeRef = rememberUpdatedState(time)
    val text = remember(tokens) { tokens.joinToString("") { it.text } }
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val density = LocalDensity.current
    // Explicit padding reserves ascender space, without a second size callback/layout pass.
    val risePx = with(density) { 6.dp.toPx() }
    Layout(content = {
        Canvas(Modifier.fillMaxWidth()) {
            val paragraph = cache.paragraph ?: return@Canvas
            translate(top = risePx) {
                if (staticText) drawText(paragraph.layout, color = bright)
                else drawNativeLyricParagraph(paragraph, timeRef.value(), bright, dim,
                    if (uplift) risePx else 0f, highlight, feathered)
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
    time: () -> Float, showTranslation: Boolean, mainColor: Color, subColor: Color,
    measurer: TextMeasurer, fontFamily: FontFamily?, onClick: () -> Unit, onLongClick: () -> Unit
) {
    val focus by animateFloatAsState(if (focused) 1f else 0f,
        spring(dampingRatio = 0.9f, stiffness = 320f), label = "NativeLyricFocus")
    val alpha by animateFloatAsState(if (active || staticText || !past) 1f else 0.3f,
        spring(dampingRatio = 1f, stiffness = 260f), label = "NativeLyricEmphasis")
    val scale = SettingsLibrary.LyricFontScale.coerceIn(0.8f, 1.2f)
    val side = line.otherSide || line.agent == "bg"
    val style = mainTextStyle(fontFamily, SettingsLibrary.LyricFontSize * scale)
        .copy(textAlign = if (side) TextAlign.End else TextAlign.Start)
    val highlight = SettingsLibrary.DebugLyricEnableHighlight
    val feathered = SettingsLibrary.DebugLyricEnableGlow
    val uplift = SettingsLibrary.DebugLyricEnableUplift
    val end = nextStart?.takeIf { it > line.startMs } ?: max(line.endMs, line.startMs + 1f)
    Column(Modifier
        .then(if (line.isCountdown) Modifier.lyricCountdownLayout(time, line.startMs, end, centerContent = true) else Modifier)
        .padding(horizontal = 29.dp)
        .graphicsLayer {
            scaleX = 1f + focus * 0.015f; scaleY = scaleX
            transformOrigin = TransformOrigin(if (side) 1f else 0f, 0.5f)
        }
        .then(if (blur > 0f && !staticText) Modifier.blur(blur.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
        .combinedClickable(indication = null, interactionSource = remember { MutableInteractionSource() },
            onClick = onClick, onLongClick = onLongClick)
        .padding(top = 12.dp, bottom = 18.dp)) {
        if (line.isCountdown) {
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                drawNativeCountdown(time(), line.startMs, end, mainColor)
            }
        } else {
            Box(Modifier.graphicsLayer { this.alpha = if (staticText) 1f else alpha }) {
                NativeLyricText(tokens, style, measurer, time, mainColor, mainColor.copy(alpha = 0.28f),
                    staticText, highlight, feathered, uplift)
            }
            if (showTranslation && line.translation.isNotBlank()) {
                Text(line.translation, fontSize = (SettingsLibrary.TranslationFontSize * scale).sp,
                    lineHeight = (SettingsLibrary.TranslationFontSize * scale + 5f).sp,
                    color = subColor, fontFamily = fontFamily, textAlign = style.textAlign,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp).graphicsLayer {
                        this.alpha = 0.35f + focus * 0.4f
                    })
            }
        }
    }
}
