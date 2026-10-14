package yos.music.player.ui.widgets

import android.graphics.Typeface
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.YosLyricToken
import yos.music.player.data.libraries.SettingsLibrary
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import java.io.File
import java.text.BreakIterator

private const val LYRIC_LAST_LINE_DURATION_MS = 4000f
private const val LYRIC_POSITION_BACKWARD_JITTER_MS = 500
private const val LYRIC_MAX_BOUNDARY_EXTRA_MS = 500f
private const val LYRIC_INNER_PAUSE_GAP_MS = 180f
private const val UPLIFT_HEIGHT_PX = 10f
private const val UPLIFT_STAGGER_LEAD_RATIO = 0.30f
private const val UPLIFT_STAGGER_TAIL_RATIO = 0.20f
private const val UPLIFT_STAGGER_MAX_LEAD_MS = 160f
private const val UPLIFT_STAGGER_MAX_TAIL_MS = 80f

// 行首光晕预热：第一字开始前 240ms 让 maskEdge 从 lineLeft - glow 渐进推到 lineLeft，
// 视觉上光晕从行外滑入贴近第一字左缘，避免"光晕在第一字突然出现"。
internal const val LYRIC_LINE_PREROLL_MS = 240f
// 字符裁剪区域的水平缓冲，解决字形溢出（如 J 的左侧投影）导致的跨字符高亮泄漏
private const val CHAR_CLIP_HORIZONTAL_PAD_PX = 2f

/** Physical parameters used by the APK's WavyLazyColumn (mass, damping, stiffness). */
internal data class WavySpringParams(
    val mass: Float,
    val damping: Float,
    val stiffness: Float
) {
    fun asComposeSpec(): SpringSpec<Float> {
        val physicalRatio = damping / (2f * sqrt(stiffness * mass))
        // C0972t0 switches every ratio >= 1 to its critically-damped branch;
        // it does not continue with a conventional over-damped solution.
        return spring(
            stiffness = stiffness / mass,
            dampingRatio = if (physicalRatio >= 1f) 1f else physicalRatio,
            visibilityThreshold = 0.01f
        )
    }
}

internal fun wavySpringParams(lines: List<YosLyricLine>, index: Int): WavySpringParams {
    val line = lines.getOrNull(index)
    if (index <= 0 || line == null || line.text.isBlank()) {
        return WavySpringParams(mass = 0.9f, damping = 15f, stiffness = 90f)
    }

    val gapMs = (line.startMs - lines[index - 1].startMs).coerceIn(100f, 800f)
    val proximity = (1f - (gapMs - 100f) / 700f).coerceIn(0f, 1f)
    val stiffness = 170f + 50f * proximity.pow(0.2f)
    return WavySpringParams(
        mass = 0.9f,
        damping = sqrt(stiffness) * 2.2f,
        stiffness = stiffness
    )
}

internal fun wavyRowDelayMs(index: Int, anchorIndex: Int): Long {
    var stepMs = 24f
    var delayMs = 0f
    repeat((index - anchorIndex).coerceIn(0, 8)) {
        delayMs += stepMs
        stepMs /= 1.08f
    }
    return delayMs.toLong()
}

private data class CachedLyricFont(val path: String, val modified: Long, val length: Long, val family: FontFamily?)
private var cachedLyricFont: CachedLyricFont? = null

@Composable
internal fun rememberLyricFontFamily(): FontFamily? {
    val path = SettingsLibrary.LyricCustomFontPath
    val enabled = SettingsLibrary.LyricCustomFontEnabled
    return remember(path, enabled) {
        if (!enabled || path.isBlank()) null
        else {
            val file = File(path)
            val modified = file.lastModified()
            val length = file.length()
            val cached = cachedLyricFont
            if (cached != null && cached.path == path && cached.modified == modified && cached.length == length) cached.family
            else runCatching { FontFamily(Typeface.createFromFile(file)) }.getOrNull().also {
                // Keep only one custom face across player composition disposal;
                // replacing the file at the same path still invalidates it.
                cachedLyricFont = CachedLyricFont(path, modified, length, it)
            }
        }
    }
}


// ── Pre-computed per-character layout metrics ──
// Computed once from TextLayoutResult; reused every frame for lerp-based mask animation.
@Stable
internal data class CharLayoutInfo(
    val textOffset: Int,
    val debugChar: Char?,
    val isWhitespace: Boolean,
    val startMs: Float,
    val endMs: Float,
    val xStartPx: Float,
    val xEndPx: Float,
    val visualLine: Int,
    val upliftStartMs: Float,
    val upliftEndMs: Float,
    val upliftXStartPx: Float,
    val upliftXEndPx: Float,
) {
    fun upliftKey(): String {
        return "$visualLine:$upliftStartMs:$upliftEndMs:$upliftXStartPx:$upliftXEndPx"
    }
}

// ── Per-frame highlight mask state ──
// innerFeatherPx & glowWidthPx are dynamically sized based on character duration:
//   - slow/long syllables → wide feather + broad glow (soft, atmospheric)
//   - fast/short syllables → tight feather + narrow glow (sharp, precise)
//
// activeVisualLine:     the visual line (wrapped line index) currently being sung.
//                       -1 means "nothing active yet", Int.MAX_VALUE means "all done".
// fullyDoneVisualLines: the wrapped lines whose characters are all sung out; the
//                       overlay draws them solid bright in one pass (no mask edge).
// The mask edge only lives on the activeVisualLine.
internal data class HighlightMask(
    val completed: Boolean,
    val activeVisualLine: Int,
    val fullyDoneVisualLines: IntRange,
    val maskRightPx: Float,
    val innerFeatherPx: Float,
    val glowWidthPx: Float,
    val highlightLimitPx: Float = Float.POSITIVE_INFINITY,
) {
    /** Back-compat view: the visual line carrying the mask edge. */
    val visualLine: Int get() = activeVisualLine
}

// ── Mutual-exclusive line state machine ──
// Modeled after AMLL's timeline.ts: at any instant a lyric line is exactly one of
// Past / Active / Future, decided purely by the (already unique) currentIndex.
// This replaces the old absolute-time-based showHighLight/isActiveLine judgement,
// which caused cascading highlight flips on seek (the "rows flash bright then dim
// when seeking" bug) and double-highlight on line transitions.
enum class LineState { Past, Active, Future }

fun computeLineState(index: Int, currentIndex: Int): LineState = when {
    index < currentIndex -> LineState.Past
    index == currentIndex -> LineState.Active
    else -> LineState.Future
}

/**
 * YosLyricView 主控件
  * @param lyricStateLambda 当前播放歌词状态
 * @param liveTimeLambda 当前歌曲进度
 * @param mediaEvent YosLyricView 媒体事件
 * @param translationLambda 是否开启翻译
 * @param blurLambda 是否启用模糊效果
 * @param uiConfig YosLyricView UI 控制，仅管理在日常使用中不经常调节的选项
 */
/*@Composable
fun Dp.toPx(): Float {
    val density = LocalDensity.current
    return this.value * density.density
}*/

@Composable
fun Float.toDp(): Dp {
    val density = LocalDensity.current
    return (this / density.density).dp
}

@Composable
fun mainTextStyle(
    fontFamily: FontFamily? = rememberLyricFontFamily(),
    fontSize: Float = SettingsLibrary.LyricFontSize * SettingsLibrary.LyricFontScale.coerceIn(0.8f, 1.2f)
): TextStyle = TextStyle(
    fontSize = fontSize.sp,
    lineHeight = (fontSize + 8.5f).sp,
    fontWeight =
    when (SettingsLibrary.LyricFontWeight) {
        "Thin" -> FontWeight.Thin
        "ExtraLight" -> FontWeight.ExtraLight
        "Light" -> FontWeight.Light
        "Regular" -> FontWeight.Normal
        "Medium" -> FontWeight.Medium
        "SemiBold" -> FontWeight.SemiBold
        "Bold" -> FontWeight.Bold
        "ExtraBold" -> FontWeight.ExtraBold
        "Black" -> FontWeight.Black
        else -> FontWeight.ExtraBold
    },
    fontFamily = fontFamily,
    letterSpacing = 0.05.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    ),
    lineBreak = LineBreak(
        strategy = if (SettingsLibrary.LyricLineBalance) LineBreak.Strategy.Balanced else LineBreak.Strategy.Simple,
        LineBreak.Strictness.Default,
        LineBreak.WordBreak.Default
    )
)

/*val BackgroundTextStyle = TextStyle(
    fontSize = 34.sp,
    lineHeight = 42.sp,
    fontWeight = FontWeight.Bold
).copy(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)*/

internal fun lyricBoundaryExtraMs(tokens: List<YosLyricToken>): Float {
    val longestCharDuration = tokens.maxOfOrNull { token ->
        val length = token.text.length.coerceAtLeast(1)
        ((token.endMs - token.startMs).coerceAtLeast(0f) / length)
    } ?: 0f
    return (longestCharDuration * 0.5f).coerceIn(0f, LYRIC_MAX_BOUNDARY_EXTRA_MS)
}

/** UTF-16 offsets are layout indices, never independently timed halves of a glyph. */
internal fun lyricGraphemeRanges(text: String): List<IntRange> {
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    return buildList {
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            add(start until end)
            start = end
            end = iterator.next()
        }
    }
}

private fun precomputeCharLayout(
    tokens: List<YosLyricToken>,
    layout: TextLayoutResult,
): List<CharLayoutInfo> {
    val text = layout.layoutInput.text.text
    if (tokens.isEmpty() || text.isEmpty()) return emptyList()
    val result = mutableListOf<CharLayoutInfo>()
    var textOffset = 0
    for (token in tokens) {
        val ranges = lyricGraphemeRanges(token.text)
        val tokenDuration = (token.endMs - token.startMs).coerceAtLeast(0f)
        val glyphDuration = tokenDuration / ranges.size.coerceAtLeast(1)
        ranges.forEachIndexed { index, range ->
            val offset = textOffset + range.first
            if (offset >= text.length) return@forEachIndexed
            val boxes = range.mapNotNull { localOffset ->
                (textOffset + localOffset).takeIf { it < text.length }?.let(layout::getBoundingBox)
            }
            if (boxes.isEmpty()) return@forEachIndexed
            val start = token.startMs + glyphDuration * index
            val end = if (index == ranges.lastIndex) token.endMs else start + glyphDuration
            val left = boxes.minOf { it.left }
            val right = boxes.maxOf { it.right }
            result += CharLayoutInfo(
                textOffset = offset,
                debugChar = text[offset],
                isWhitespace = range.all { token.text[it].isWhitespace() },
                startMs = start,
                endMs = end,
                xStartPx = left,
                xEndPx = right,
                visualLine = layout.getLineForOffset(offset),
                upliftStartMs = start,
                upliftEndMs = end,
                upliftXStartPx = left,
                upliftXEndPx = right,
            )
        }
        textOffset += token.text.length
    }
    // Merge words after layout, so an English token wrapping across lines cannot
    // give both fragments a shared, cross-line clipping rectangle.
    return staggerUpliftTiming(mergeAsciiWordUplift(result, text))
}

private fun mergeAsciiWordUplift(
    chars: List<CharLayoutInfo>,
    text: String,
): List<CharLayoutInfo> {
    if (chars.isEmpty() || text.isEmpty()) return chars
    val merged = chars.toMutableList()
    var index = 0
    while (index < merged.size) {
        val current = merged[index]
        val currentChar = text.getOrNull(current.textOffset)
        if (currentChar?.isAsciiWordChar() != true) {
            index++
            continue
        }

        var endIndex = index + 1
        while (endIndex < merged.size) {
            val previous = merged[endIndex - 1]
            val candidate = merged[endIndex]
            val candidateChar = text.getOrNull(candidate.textOffset)
            val isSameAsciiRun = candidateChar?.isAsciiWordChar() == true &&
                candidate.textOffset == previous.textOffset + 1 &&
                candidate.visualLine == previous.visualLine
            if (!isSameAsciiRun) break
            endIndex++
        }

        if (endIndex - index > 1) {
            val wordStart = merged[index]
            val wordEnd = merged[endIndex - 1]
            for (runIndex in index until endIndex) {
                merged[runIndex] = merged[runIndex].copy(
                    upliftStartMs = wordStart.startMs,
                    upliftEndMs = wordEnd.endMs,
                    upliftXStartPx = wordStart.xStartPx,
                    upliftXEndPx = wordEnd.xEndPx,
                )
            }
        }
        index = endIndex
    }
    return merged
}

private data class UpliftRun(
    val startIndex: Int,
    val endExclusive: Int,
    val visualLine: Int,
    val startMs: Float,
    val endMs: Float,
)

internal fun staggerUpliftTiming(chars: List<CharLayoutInfo>): List<CharLayoutInfo> {
    if (chars.isEmpty()) return chars
    val staggered = chars.toMutableList()
    val runs = mutableListOf<UpliftRun>()
    var runStart = 0
    while (runStart < staggered.size) {
        val current = staggered[runStart]
        var runEnd = runStart + 1
        while (runEnd < staggered.size && staggered[runEnd].upliftKey() == current.upliftKey()) runEnd++
        runs += UpliftRun(
            startIndex = runStart,
            endExclusive = runEnd,
            visualLine = current.visualLine,
            startMs = current.upliftStartMs,
            endMs = current.upliftEndMs,
        )
        runStart = runEnd
    }

    runs.forEachIndexed { index, run ->
        val durationMs = (run.endMs - run.startMs).coerceAtLeast(0f)
        val leadMs = minOf(durationMs * UPLIFT_STAGGER_LEAD_RATIO, UPLIFT_STAGGER_MAX_LEAD_MS)
        val tailMs = minOf(durationMs * UPLIFT_STAGGER_TAIL_RATIO, UPLIFT_STAGGER_MAX_TAIL_MS)
        val previous = runs.getOrNull(index - 1)
        val next = runs.getOrNull(index + 1)
        val adjustedStart = if (previous != null && run.startMs - previous.endMs < LYRIC_INNER_PAUSE_GAP_MS) {
            maxOf(run.startMs - leadMs, previous.startMs)
        } else {
            run.startMs
        }
        val adjustedEnd = if (next != null && next.startMs - run.endMs < LYRIC_INNER_PAUSE_GAP_MS) {
            maxOf(run.endMs + tailMs, adjustedStart + 0.001f)
        } else {
            maxOf(run.endMs, adjustedStart + 0.001f)
        }
        for (charIndex in run.startIndex until run.endExclusive) {
            staggered[charIndex] = staggered[charIndex].copy(
                upliftStartMs = adjustedStart,
                upliftEndMs = adjustedEnd,
            )
        }
    }
    return staggered
}

private fun Char.isAsciiWordChar(): Boolean {
    return this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '\''
}

// ── Per-frame: calculate highlight mask using linear interpolation ──
// X_current = X_start + α × (X_end − X_start), where α = (t − T_begin) / (T_end − T_begin)
//
// 重写（参考 AMLL generateWebAnimationBasedMaskImage）：
// 按 visual line 分段。找出哪些折行已全部唱完（fullyDoneVisualLines）和
// 哪一行正在唱（activeVisualLine）。正在唱的行内 mask 边缘起点固定为该行
// 最左字符的 xStartPx，不再沿用上一行的 lastCompletedX，消除跨行脆弱性。
// 零宽 token（charStart == charEnd）直接当作已唱完，不做 0.001 强行插值，
// 消除换行首字瞬跳。

// 字内羽化宽度随时长连续变化，避免跨字跨桶导致 glow 宽度阶跃 +
// EMA 多帧追赶造成的"呼吸式闪烁"。系数 25 = 经验值，1000ms ≈ 40px。
private fun featherForDuration(durationMs: Float): Float =
    (durationMs / 25f).coerceIn(8f, 42f)

internal fun lyricGlowWidth(durationMs: Float, glyphWidthPx: Float): Float =
    maxOf((durationMs / 12f).coerceIn(36f, 144f), glyphWidthPx * 1.6f)

private const val LYRIC_PROGRESS_EASE_FULL_MS = 90f
private const val LYRIC_PROGRESS_EASE_NONE_MS = 260f

private fun smoothStep(alpha: Float): Float {
    val t = alpha.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun calculateLyricProgress(
    rawAlpha: Float,
    durationMs: Float,
    smoothTiming: Boolean,
): Float {
    val clampedRaw = rawAlpha.coerceIn(0f, 1f)
    if (!smoothTiming) return clampedRaw
    val eased = smoothStep(clampedRaw)
    val easeBlend = (((LYRIC_PROGRESS_EASE_NONE_MS - durationMs) /
        (LYRIC_PROGRESS_EASE_NONE_MS - LYRIC_PROGRESS_EASE_FULL_MS)).coerceIn(0f, 1f)) * 0.35f
    return androidx.compose.ui.util.lerp(clampedRaw, eased, easeBlend)
}

internal fun calculateHighlightMask(
    chars: List<CharLayoutInfo>,
    currentTimeMs: Float,
    smoothVelocity: Boolean = true,
    enableGlow: Boolean = true,
): HighlightMask {
    val empty = HighlightMask(
        completed = false, activeVisualLine = -1, fullyDoneVisualLines = -1..-2,
        maskRightPx = 0f, innerFeatherPx = 0f, glowWidthPx = 0f
    )
    if (chars.isEmpty()) return empty

    val lastVisualLine = chars.last().visualLine

    // 每个 visualLine 内最长字时长。光晕/羽化宽度按所在行最长字一次性确定，
    // 整行内字间切换 feather 恒定 —— 避免"长字→短字"瞬间 feather 缩水 + EMA
    // 多帧追赶造成的"光晕往回退"视觉。
    val lineMaxDurationMs = HashMap<Int, Float>()
    val lineMaxGlyphWidth = HashMap<Int, Float>()
    for (c in chars) {
        val d = (c.endMs - c.startMs).coerceAtLeast(0f)
        val cur = lineMaxDurationMs[c.visualLine] ?: 0f
        if (d > cur) lineMaxDurationMs[c.visualLine] = d
        if (!c.isWhitespace) {
            lineMaxGlyphWidth[c.visualLine] = maxOf(
                lineMaxGlyphWidth[c.visualLine] ?: 0f, c.xEndPx - c.xStartPx
            )
        }
    }

    // 全部唱完
    val lastCharEnd = chars.last().let { it.endMs.coerceAtLeast(it.startMs) }
    if (currentTimeMs >= lastCharEnd) {
        return HighlightMask(
            completed = true,
            activeVisualLine = Int.MAX_VALUE,
            fullyDoneVisualLines = 0..lastVisualLine,
            maskRightPx = chars.last().xEndPx, innerFeatherPx = 0f, glowWidthPx = 0f,
        )
    }

    // 还没开始唱任何字 —— 行首 preroll：第一字开始前 LYRIC_LINE_PREROLL_MS 内，
    // maskEdge 从 lineLeft - glow 渐进推到 lineLeft，让光晕从行外扫入字 1 左缘。
    // glow 宽度直接给行 max，不再 0 起步，消除 EMA 启动期带来的"光晕渐入"延迟。
    val firstCharStart = chars.first().startMs
    if (currentTimeMs <= firstCharStart) {
        val firstLine = chars.first().visualLine
        val lineDur = lineMaxDurationMs[firstLine] ?: 0f
        val feather = featherForDuration(lineDur)
        val glow = lyricGlowWidth(lineDur, lineMaxGlyphWidth[firstLine] ?: 0f)
        val prerollAlpha = if (currentTimeMs >= firstCharStart - LYRIC_LINE_PREROLL_MS) {
            ((currentTimeMs - (firstCharStart - LYRIC_LINE_PREROLL_MS)) / LYRIC_LINE_PREROLL_MS)
                .coerceIn(0f, 1f)
        } else 0f
        val lineLeft = chars.first().xStartPx
        return HighlightMask(
            completed = false,
            // The same gradient covers preroll and active singing. Before the
            // edge reaches the text, only its feather is visible.
            activeVisualLine = firstLine,
            fullyDoneVisualLines = -1..-2,
            maskRightPx = lineLeft - (1f - prerollAlpha) * if (enableGlow) glow else 0f,
            innerFeatherPx = if (enableGlow) feather else 0f,
            glowWidthPx = if (enableGlow) glow else 0f,
            highlightLimitPx = nextLyricPhraseBoundary(chars, 0),
        )
    }

    // 逐字遍历，定位第一个未唱完的字 → 它所在行即 activeVisualLine，
    // 它之前的所有字所在行（含同行早于它的字）都已唱完。
    // mask 边缘相对「上一个已唱完字的右边界」插值（参考 AMLL generateWebAnimationBasedMaskImage
    // 的 curPos 累计推进），而非从行首 —— 否则正在唱的字会覆盖掉前面已唱完字的高亮。
    var doneLineMax = -1        // 已唱完的最大 visual line（闭区间右端）
    var doneXRight = (chars.firstOrNull { !it.isWhitespace } ?: chars.first()).xStartPx
    var activeLine = -1
    var maskRightPx = 0f
    var innerFeather = 0f
    var glowWidth = 0f
    var highlightLimit = Float.POSITIVE_INFINITY
    for (idx in chars.indices) {
        val char = chars[idx]
        val charStart = char.startMs
        val charEnd = char.endMs.coerceAtLeast(charStart)
        val zeroWidth = (charEnd <= charStart)
        val isDone = if (zeroWidth) currentTimeMs > charStart else currentTimeMs >= charEnd

        if (char.isWhitespace) {
            // 空白字符不应推动高亮边界，也不参与"下一段是否该提前发光"的判定。
            continue
        }

        if (isDone) {
            doneXRight = char.xEndPx
            if (char.visualLine > doneLineMax) doneLineMax = char.visualLine
            continue
        }

        val isActive = if (zeroWidth) false else currentTimeMs > charStart
        // 跨行时 doneXRight 来自上一折行，不能直接作为本行起跳点；
        // 改用本字所属行的行首。
        val startX = if (char.visualLine != doneLineMax.coerceAtLeast(-1)) {
            chars.first { it.visualLine == char.visualLine }.xStartPx
        } else {
            doneXRight
        }
        val charWidth = char.xEndPx - startX
        val previousVisibleChar = (idx - 1 downTo 0)
            .asSequence()
            .map { chars[it] }
            .firstOrNull { !it.isWhitespace }
        val previousVisibleCharEnd = previousVisibleChar?.endMs?.coerceAtLeast(previousVisibleChar.startMs) ?: charStart
        val hasInnerPause = previousVisibleChar != null &&
            charStart - previousVisibleCharEnd >= LYRIC_INNER_PAUSE_GAP_MS
        val followsSpace = idx > 0 && chars[idx - 1].isWhitespace
        // Do not light the next phrase before a pause/space, then erase that
        // light when its entrance starts. Stop this phrase at that boundary.
        highlightLimit = nextLyricPhraseBoundary(chars, idx)

        if (isActive) {
            activeLine = char.visualLine
            val duration = (charEnd - charStart).coerceAtLeast(0.001f)
            val rawAlpha = ((currentTimeMs - charStart) / duration).coerceIn(0f, 1f)
              val smoothAlpha = calculateLyricProgress(
                  rawAlpha = rawAlpha,
                  durationMs = duration,
                  smoothTiming = smoothVelocity,
              )

            maskRightPx = startX + smoothAlpha * charWidth
            val lineDur = lineMaxDurationMs[char.visualLine] ?: (charEnd - charStart)
            innerFeather = if (enableGlow) featherForDuration(lineDur) else 0f
            glowWidth = if (enableGlow) lyricGlowWidth(lineDur, lineMaxGlyphWidth[char.visualLine] ?: 0f) else 0f
            if (hasInnerPause || followsSpace) {
                val entrance = smoothStep((currentTimeMs - charStart) / minOf(240f, duration))
                glowWidth *= entrance
                innerFeather *= entrance
            }
        } else {
            // 尚未开始的字（字间间隙）：mask 边缘停在起跳点
            if (activeLine == -1) {
                activeLine = char.visualLine
                maskRightPx = startX
                if (hasInnerPause || followsSpace) {
                    // 行内长停顿时，后半句不要提前吃到当前行的光晕/羽化。
                    // 已唱部分保持高亮，未唱部分按"下一句尚未开始"的状态处理，
                    // 直到真正进入下一段再开始推进 mask。
                    innerFeather = 0f
                    glowWidth = 0f
                } else {
                    val lineDur = lineMaxDurationMs[char.visualLine] ?: (charEnd - charStart)
                    innerFeather = if (enableGlow) featherForDuration(lineDur) else 0f
                    glowWidth = if (enableGlow) lyricGlowWidth(lineDur, lineMaxGlyphWidth[char.visualLine] ?: 0f) else 0f
                }
            }
        }
        break
    }

    // Trailing whitespace can outlast all visible glyphs. Keep the mask at the
    // final wrapped line edge until that source timing ends.
    if (activeLine == -1) {
        val lastChar = chars.last()
        return HighlightMask(
            completed = false,
            activeVisualLine = lastChar.visualLine,
            fullyDoneVisualLines = 0..(lastChar.visualLine - 1),
            maskRightPx = lastChar.xEndPx,
            innerFeatherPx = 0f, glowWidthPx = 0f,
        )
    }

    return HighlightMask(
        completed = false,
        activeVisualLine = activeLine,
        // activeLine 本身不算 fullyDone；它之前的行算
        fullyDoneVisualLines = 0..(doneLineMax.coerceAtMost(activeLine - 1)),
        maskRightPx = maskRightPx,
        innerFeatherPx = innerFeather,
        glowWidthPx = glowWidth,
        highlightLimitPx = highlightLimit,
    )
}

/**
 * 计算字符的无缝水平裁剪边界，相邻字符的裁剪区域在中点处相接，
 * 消除因字形溢出（如 J 的左侧投影）导致的跨字符高亮泄漏。
 */
private fun nextLyricPhraseBoundary(chars: List<CharLayoutInfo>, index: Int): Float {
    var previous = chars[index]
    var crossedSpace = false
    for (nextIndex in index + 1 until chars.size) {
        val next = chars[nextIndex]
        if (next.visualLine != previous.visualLine) break
        if (next.isWhitespace) { crossedSpace = true; continue }
        if (crossedSpace || next.startMs - previous.endMs >= LYRIC_INNER_PAUSE_GAP_MS) return next.xStartPx
        previous = next
    }
    return Float.POSITIVE_INFINITY
}

private fun seamlessClipBounds(
    sortedChars: List<CharLayoutInfo>,
    index: Int,
    clipLeft: Float,
    clipRight: Float,
): Pair<Float, Float> {
    val char = sortedChars[index]
    val charLeft = char.upliftXStartPx
    val charRight = char.upliftXEndPx
    val left = if (index == 0) {
        charLeft - CHAR_CLIP_HORIZONTAL_PAD_PX
    } else {
        (sortedChars[index - 1].upliftXEndPx + charLeft) / 2f
    }
    val right = if (index == sortedChars.lastIndex) {
        charRight + CHAR_CLIP_HORIZONTAL_PAD_PX
    } else {
        (charRight + sortedChars[index + 1].upliftXStartPx) / 2f
    }
    return maxOf(left, clipLeft) to minOf(right, clipRight)
}

// ── Base layer: dim text with per-character uplift, sharing the same timing as the overlay ──

private fun DrawScope.drawUnplayedChars(
    layout: TextLayoutResult,
    chars: List<CharLayoutInfo>,
    currentTimeMs: Float,
    dimColor: Color,
    enableUplift: Boolean,
) {
    if (chars.isEmpty()) return
    for (line in 0 until layout.lineCount) {
        drawLineChars(layout, chars, currentTimeMs, dimColor, enableUplift, line)
    }
}

// ── Single text surface: brightness is a color field, not a second text layer ──

/** Draw each glyph once, using the same geometry for dim and highlighted portions. */
internal fun DrawScope.drawTimedHighlight(
    layout: TextLayoutResult,
    masks: Map<Int, HighlightMask>,
    chars: List<CharLayoutInfo>,
    currentTimeMs: Float,
    focusedColor: Color,
    dimColor: Color,
    enableUplift: Boolean,
    preparedCharsByLine: Map<Int, List<CharLayoutInfo>>? = null,
    batchEqualUplift: Boolean = true,
) {
    for (line in 0 until layout.lineCount) {
        // Each visual line owns its preroll, so its feather can enter while
        // the preceding line is still singing instead of appearing at the wrap.
        val mask = masks[line] ?: continue
        val lineLeft = layout.getLineLeft(line) - CHAR_CLIP_HORIZONTAL_PAD_PX
        val lineRight = layout.getLineRight(line) + CHAR_CLIP_HORIZONTAL_PAD_PX
        // One glyph surface contains both dim and bright colors. There is no
        // second paragraph underneath whose position/paint can leave a ghost.
        drawLineChars(
                layout = layout,
                chars = chars,
                currentTimeMs = currentTimeMs,
                brush = lyricSurfaceBrush(mask, lineLeft, lineRight, focusedColor, dimColor),
                visualLine = line,
                clipLeft = lineLeft,
                clipRight = lineRight,
                enableUplift = enableUplift,
                preparedChars = preparedCharsByLine?.get(line),
                batchEqualUplift = batchEqualUplift,
            )
    }
}

internal fun lyricSurfaceBrush(mask: HighlightMask, left: Float, right: Float, bright: Color, dim: Color): Brush {
    if (mask.completed) return androidx.compose.ui.graphics.SolidColor(bright)
    val edge = mask.maskRightPx
    val glow = mask.glowWidthPx.coerceAtLeast(0f)
    val start = minOf(left, edge)
    val end = maxOf(right, edge + glow, start + 1f)
    val width = end - start
    fun stop(x: Float) = ((x - start) / width).coerceIn(0f, 1f)
    val limit = mask.highlightLimitPx.coerceAtMost(end)
    if (limit <= start) return androidx.compose.ui.graphics.SolidColor(dim)
    val stops = mutableListOf(0f to bright)
    if (edge < limit) stops += stop(edge) to bright
    val featherEnd = minOf(edge + glow, limit)
    val alpha = if (glow > 0f) (1f - (featherEnd - edge) / glow).coerceIn(0f, 1f) else 1f
    stops += stop(featherEnd) to androidx.compose.ui.graphics.lerp(dim, bright, alpha)
    stops += stop(featherEnd) to dim
    stops += 1f to dim
    return Brush.horizontalGradient(*stops.toTypedArray(), startX = start, endX = end)
}

private fun calculateCharUplift(char: CharLayoutInfo, currentTimeMs: Float, enabled: Boolean = true): Float {
    if (!enabled) return 0f
    return lyricUpliftProgress(currentTimeMs, char.upliftStartMs, char.upliftEndMs) * UPLIFT_HEIGHT_PX
}

private fun DrawScope.drawLineChars(
    layout: TextLayoutResult,
    chars: List<CharLayoutInfo>,
    currentTimeMs: Float,
    color: Color,
    enableUplift: Boolean = true,
    visualLine: Int? = null,
    clipLeft: Float = Float.NEGATIVE_INFINITY,
    clipRight: Float = Float.POSITIVE_INFINITY,
) {
    val sorted = (visualLine?.let { line -> chars.filter { it.visualLine == line } } ?: chars)
        .distinctBy { it.upliftKey() }
    val uniformY = uniformLyricUplift(sorted, currentTimeMs, enableUplift)
    if (visualLine != null && uniformY != null && clipRight > clipLeft) {
        val (top, bottom) = visualLineClip(layout, visualLine, uniformY)
        clipRect(clipLeft, top, clipRight, bottom) {
            drawText(textLayoutResult = layout, color = color, topLeft = Offset(0f, -uniformY))
        }
        return
    }
    for (i in sorted.indices) {
        val char = sorted[i]
        val (left, right) = seamlessClipBounds(sorted, i, clipLeft, clipRight)
        if (right <= left) continue
        val upliftY = calculateCharUplift(char, currentTimeMs, enableUplift)
        val (clipTop, clipBottom) = visualLineClip(layout, char.visualLine, upliftY)
        clipRect(
            left = left,
            top = clipTop,
            right = right,
            bottom = clipBottom,
        ) {
            drawText(textLayoutResult = layout, color = color, topLeft = Offset(0f, -upliftY))
        }
    }
}

private fun DrawScope.drawLineChars(
    layout: TextLayoutResult,
    chars: List<CharLayoutInfo>,
    currentTimeMs: Float,
    brush: Brush,
    visualLine: Int,
    clipLeft: Float,
    clipRight: Float,
    enableUplift: Boolean = true,
    preparedChars: List<CharLayoutInfo>? = null,
    batchEqualUplift: Boolean = true,
) {
    val sorted = preparedChars ?: chars.filter { it.visualLine == visualLine }.distinctBy { it.upliftKey() }
    val uniformY = uniformLyricUplift(sorted, currentTimeMs, enableUplift)
    if (uniformY != null && clipRight > clipLeft) {
        val (top, bottom) = visualLineClip(layout, visualLine, uniformY)
        clipRect(clipLeft, top, clipRight, bottom) {
            drawLyricHighlightText(layout, brush, Offset(0f, -uniformY))
        }
        return
    }
    var first = 0
    while (first < sorted.size) {
        val upliftY = calculateCharUplift(sorted[first], currentTimeMs, enableUplift)
        var last = first
        if (batchEqualUplift) {
            while (last + 1 < sorted.size &&
                abs(calculateCharUplift(sorted[last + 1], currentTimeMs, enableUplift) - upliftY) <= 0.001f
            ) last++
        }
        val left = seamlessClipBounds(sorted, first, clipLeft, clipRight).first
        val right = seamlessClipBounds(sorted, last, clipLeft, clipRight).second
        if (right > left) {
            val (top, bottom) = visualLineClip(layout, visualLine, upliftY)
            clipRect(left, top, right, bottom) {
                drawLyricHighlightText(layout, brush, Offset(0f, -upliftY))
            }
        }
        first = last + 1
    }
}

private fun visualLineClip(layout: TextLayoutResult, visualLine: Int, upliftY: Float): Pair<Float, Float> {
    val line = visualLine.coerceIn(0, layout.lineCount - 1)
    val top = layout.getLineTop(line) - upliftY
    val bottom = layout.getLineBottom(line) - upliftY
    return top to bottom
}

/** Static/equally moving rows need one paragraph draw, not one per character. */
internal fun uniformLyricUplift(chars: List<CharLayoutInfo>, positionMs: Float, enabled: Boolean): Float? {
    var value: Float? = null
    for (char in chars) {
        if (char.isWhitespace) continue
        val y = calculateCharUplift(char, positionMs, enabled)
        if (value != null && kotlin.math.abs(y - value) > 0.001f) return null
        value = y
    }
    return value
}

/** Long notes rise, hold, then settle alongside the next glyph's early start. */
internal fun lyricUpliftProgress(positionMs: Float, startMs: Float, endMs: Float): Float {
    val duration = (endMs - startMs).coerceAtLeast(220f)
    val elapsed = positionMs - startMs
    if (duration <= 900f) return smoothStep(elapsed / duration)
    val rise = smoothStep(elapsed / 650f)
    val settle = smoothStep((elapsed - (duration - 320f)) / 320f)
    return 0.8f * rise + 0.2f * settle
}

internal fun Modifier.lyricCountdownLayout(
    positionMs: () -> Float, startMs: Float, endMs: Float, centerContent: Boolean = false
): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val child = measurable.measure(constraints.copy(minHeight = 0))
        val fraction = countdownLayoutFraction(positionMs(), startMs, endMs)
        val height = (child.height * fraction).toInt()
        layout(child.width, height) {
            child.placeRelative(0, if (centerContent) (height - child.height) / 2 else 0)
        }
    }

internal fun DrawScope.drawLyricHighlightText(
    layout: TextLayoutResult,
    brush: Brush,
    topLeft: Offset = Offset.Zero
) {
    // Dim text and highlight reuse the paragraph's Android TextPaint. A brush
    // with unspecified alpha inherits its last alpha, unlike a solid color pass.
    drawText(textLayoutResult = layout, brush = brush, alpha = 1f, topLeft = topLeft)
}
