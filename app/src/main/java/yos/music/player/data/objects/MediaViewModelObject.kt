package yos.music.player.data.objects

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import yos.music.player.code.utils.lrc.YosLyricLine
import yos.music.player.code.utils.lrc.LyricDocumentIdentity
import yos.music.player.code.utils.lrc.LyricSource

@Immutable
data class NowPlayingBackgroundState(
    val trackKey: String? = null,
    val artworkUri: Uri? = null,
    val status: BackgroundStateStatus = BackgroundStateStatus.IDLE
) {
    val requestKey: String?
        get() = artworkUri?.toString()?.let { uri ->
            buildString {
                append(trackKey ?: "no-track")
                append('|')
                append(uri)
            }
        }
}

enum class BackgroundStateStatus {
    IDLE,
    AVAILABLE,
    UNAVAILABLE
}

@Immutable
data class NowPlayingLyricState(
    val trackKey: String? = null,
    val entries: List<YosLyricLine> = emptyList(),
    val currentLineIndex: Int = -1,
    val activeLineIndexes: Set<Int> = emptySet(),
    val effectivePositionMs: Long = 0L,
    val nextBoundaryMs: Long = Long.MAX_VALUE,
    val documentIdentity: LyricDocumentIdentity? = null,
    val source: LyricSource? = null,
    val generation: Int = 0,
    val isLoading: Boolean = false
)

@Stable
object MediaViewModelObject {
    val nowPlayingBackground: MutableState<NowPlayingBackgroundState> =
        mutableStateOf(NowPlayingBackgroundState())
    val nowPlayingLyrics: MutableState<NowPlayingLyricState> =
        mutableStateOf(NowPlayingLyricState())

    val isPlaying: MutableState<Boolean> = mutableStateOf(false)
    val isBuffering: MutableState<Boolean> = mutableStateOf(false)

    val bitrate = mutableIntStateOf(0)
    val samplingRate = mutableIntStateOf(0)
    val isDolby = mutableStateOf(false)

    fun setNowPlayingBackground(trackKey: String?, artworkUri: Uri?) {
        nowPlayingBackground.value = NowPlayingBackgroundState(
            trackKey = trackKey,
            artworkUri = artworkUri,
            status = if (artworkUri != null) BackgroundStateStatus.AVAILABLE else BackgroundStateStatus.UNAVAILABLE
        )
    }

    fun updateNowPlayingBackgroundIfCurrent(trackKey: String?, artworkUri: Uri?) {
        if (nowPlayingBackground.value.trackKey == trackKey) {
            setNowPlayingBackground(trackKey, artworkUri)
        }
    }

    // val songSort = mutableStateOf(SettingData.getString("yos_player_song_sort", "MUSIC_TITLE"))
    // val enableDescending = mutableStateOf(SettingData.get("yos_player_enable_descending", false))
}
