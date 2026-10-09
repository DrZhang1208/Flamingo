package yos.music.player.code.utils.lrc

import androidx.compose.runtime.Immutable

@Immutable
data class YosLyricToken(
    val startMs: Float,
    val endMs: Float,
    val text: String,
    val hasSpace: Boolean = text.lastOrNull()?.isWhitespace() == true
)

@Immutable
data class YosLyricLine(
    val startMs: Float,
    val endMs: Float,
    val tokens: List<YosLyricToken>,
    val translation: String = "",
    val otherSide: Boolean = false,
    val agent: String = DEFAULT_AGENT,
    val lyricsTextLine: String? = null,
    val otherLrcEntries: List<YosLyricLine> = emptyList()
) {
    val text: String
        get() = lyricsTextLine ?: tokens.joinToString(separator = "") { it.text }

    val isCountdown: Boolean
        get() = agent == COUNTDOWN_AGENT

    val hasWordTiming: Boolean
        get() = tokens.isNotEmpty() && tokens.any { it.endMs > it.startMs }

    companion object {
        const val DEFAULT_AGENT = "v1"
        const val COUNTDOWN_AGENT = "countdown"
    }
}
