package yos.music.player.code.utils.lrc

import androidx.compose.runtime.Immutable

@Immutable
data class YosLyricToken(
    val startMs: Float,
    val endMs: Float,
    val text: String
)

@Immutable
data class YosLyricLine(
    val startMs: Float,
    val endMs: Float,
    val tokens: List<YosLyricToken>,
    val translation: String = "",
    val otherSide: Boolean = false
) {
    val text: String
        get() = tokens.joinToString(separator = "") { it.text }
}
