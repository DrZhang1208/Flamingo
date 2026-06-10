package yos.music.player.data.objects

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import yos.music.player.code.utils.lrc.YosLyricLine

@Stable
object MediaViewModelObject {
    val lrcEntries: MutableState<List<YosLyricLine>> = mutableStateOf(listOf())
    val otherSideForLines = mutableStateListOf<Boolean>()

    // var mainLyricLines = mutableStateListOf<AnnotatedString>()

    val bitmap: MutableState<Uri?> = mutableStateOf(null)

    val isPlaying: MutableState<Boolean> = mutableStateOf(false)

    val bitrate = mutableIntStateOf(0)
    val samplingRate = mutableIntStateOf(0)
    val isDolby = mutableStateOf(false)

    // 内存歌词缓存，key 为 URI 字符串
    private val lrcCache = LinkedHashMap<String, List<YosLyricLine>>(50, 0.75f, true)

    fun getCachedLrc(uri: String?): List<YosLyricLine>? {
        return uri?.let { lrcCache[it] }
    }

    fun cacheLrc(uri: String?, entries: List<YosLyricLine>) {
        if (uri != null && entries.isNotEmpty()) {
            lrcCache[uri] = entries
        }
    }

    // val songSort = mutableStateOf(SettingData.getString("yos_player_song_sort", "MUSIC_TITLE"))
    // val enableDescending = mutableStateOf(SettingData.get("yos_player_enable_descending", false))
}
