package yos.music.player.code.utils.lrc

import android.content.Context
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider

/** APK-compatible Lyricon provider lifecycle and rich lyric publication. */
internal class LyriconLyricsPublisher(private val context: Context) {
    private var provider: LyriconProvider? = null
    private var publishedTrack: String? = null
    private var publishedGeneration = -1
    private var translationVisible: Boolean? = null

    fun update(
        enabled: Boolean,
        track: LyricTrackInfo?,
        state: yos.music.player.data.objects.NowPlayingLyricState,
        positionMs: Long,
        playing: Boolean,
        showTranslation: Boolean
    ) {
        if (!enabled) {
            stop()
            return
        }
        val activeProvider = provider ?: runCatching {
            LyriconFactory.createProvider(context).also { it.register() }
        }.getOrNull()?.also { provider = it } ?: return

        val trackChanged = track?.mediaUri != publishedTrack
        if (track != null && state.entries.isNotEmpty() && (trackChanged || state.generation != publishedGeneration)) {
            runCatching { activeProvider.player.setSong(buildLyriconSong(track, state.entries)) }
            publishedTrack = track.mediaUri
            publishedGeneration = state.generation
        } else if (track == null && publishedTrack != null) {
            runCatching { activeProvider.player.setSong(null) }
            publishedTrack = null
            publishedGeneration = -1
        }
        if (translationVisible != showTranslation) {
            runCatching { activeProvider.player.setDisplayTranslation(showTranslation) }
            translationVisible = showTranslation
        }
        runCatching { activeProvider.player.setPosition(positionMs.coerceAtLeast(0L)) }
        runCatching { activeProvider.player.setPlaybackState(playing) }
    }

    fun stop() {
        provider?.let { active ->
            runCatching { active.player.setSong(null) }
            runCatching { active.destroy() }
        }
        provider = null
        publishedTrack = null
        publishedGeneration = -1
        translationVisible = null
    }
}

internal fun buildLyriconSong(track: LyricTrackInfo, entries: List<YosLyricLine>): Song =
    Song().apply {
        id = track.mediaId
        name = track.title.ifBlank { "Unknown Work" }
        artist = track.artist.ifBlank { "Unknown Artist" }
        duration = track.durationMs.coerceAtLeast(0L)
        lyrics = entries.map(::toLyriconLine)
    }

private fun toLyriconLine(line: YosLyricLine): RichLyricLine {
    if (line.isCountdown) return RichLyricLine()
    val background = line.otherLrcEntries.lastOrNull()
    return RichLyricLine().apply {
        begin = line.startMs.toLong()
        end = line.endMs.toLong()
        duration = (end - begin).coerceAtLeast(0L)
        isAlignedRight = line.otherSide
        text = line.text
        words = line.tokens.map(::toLyriconWord)
        secondary = background?.text.orEmpty()
        secondaryWords = background?.tokens?.map(::toLyriconWord)
        translation = line.translation
    }
}

private fun toLyriconWord(token: YosLyricToken): LyricWord = LyricWord().apply {
    begin = token.startMs.toLong()
    end = token.endMs.toLong()
    duration = (end - begin).coerceAtLeast(0L)
    text = token.text + if (token.hasSpace) " " else ""
}
