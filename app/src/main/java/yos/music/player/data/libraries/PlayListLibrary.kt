package yos.music.player.data.libraries

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Stable
import com.funny.data_saver.core.mutableDataSaverListStateOf
import kotlinx.parcelize.Parcelize
import yos.music.player.data.PlayListSaver
import java.util.UUID

@Stable
@Parcelize
data class PlayList(
    val listID: String,
    val name: String,
    val songDataList: List<Uri>
) : Parcelable

@Stable
object PlayListLibrary {

    @Stable
    var playList by mutableDataSaverListStateOf(
        dataSaverInterface = PlayListSaver,
        key = "yos_play_list",
        initialValue = listOf<PlayList>()
    )
        private set

    fun PlayList.addMusic(music: YosMediaItem) {
        val id = this.listID
        val index = playList.indexOfFirst { it.listID == id }
        if (index < 0) return
        val uri = music.uri ?: return
        val currentList = playList[index].songDataList
        if (currentList.contains(uri)) return  // 已存在，不重复添加
        val new = playList.toMutableList().also {
            it[index] = it[index].copy(songDataList = currentList.toMutableList().apply { add(uri) })
        }
        playList = new
    }

    fun PlayList.removeMusic(music: YosMediaItem) {
        val id = this.listID
        val index = playList.indexOfFirst { it.listID == id }
        if (index < 0) return
        val new = playList.toMutableList().also {
            it[index] = it[index].copy(songDataList = it[index].songDataList.toMutableList().apply {
                removeAll { it == music.uri }
            })
        }
        playList = new
    }

    fun PlayList.rename(name: String) {
        val id = this.listID
        val index = playList.indexOfFirst { it.listID == id }
        if (index < 0) return
        val new = playList.toMutableList().also { it[index] = it[index].copy(name = name) }
        playList = new
    }

    fun create(name: String) {
        if (!playList.any { it.name == name }) {
            playList += PlayList(UUID.randomUUID().toString(), name, listOf())
        }
    }

    fun remove(list: PlayList) {
        playList -= list
    }
}

@Stable
object FavPlayListLibrary {
    @Stable
    var favPlayList by mutableDataSaverListStateOf(
        dataSaverInterface = PlayListSaver,
        key = "yos_fav_play_list",
        initialValue = listOf<YosMediaItem>()
    )
        private set

    fun addMusic(music: YosMediaItem) {
        if (!favPlayList.any { it.uri == music.uri }) {
            favPlayList += music
        }
    }

    fun removeMusic(music: YosMediaItem) {
        favPlayList -= music
    }

    fun isFavorite(music: YosMediaItem): Boolean = favPlayList.any { it.uri == music.uri }
}