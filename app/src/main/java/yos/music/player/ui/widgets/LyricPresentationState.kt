package yos.music.player.ui.widgets

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.data.objects.NowPlayingLyricState

internal data class LyricPresentationState(
    val trackKey: String?,
    val entries: List<YosLyricLine>,
    val activeLineIndexes: Set<Int>,
    val generation: Int
)

internal fun lyricPresentationState(source: () -> NowPlayingLyricState): State<LyricPresentationState> =
    derivedStateOf(structuralEqualityPolicy()) {
        val state = source()
        LyricPresentationState(state.trackKey, state.entries, state.activeLineIndexes, state.generation)
    }
