package yos.music.player.data.objects

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import yos.music.player.data.libraries.Folder
import yos.music.player.data.libraries.YosMediaItem

@Stable
object LibraryObject {
    @Stable
    private val targetAlbumName = mutableStateOf("")
    fun setTargetAlbumName(name: String) {
        targetAlbumName.value = name
    }

    fun getTargetAlbumName(): String {
        return targetAlbumName.value
    }

    @Stable
    private val targetArtistName = mutableStateOf("")
    fun setTargetArtistName(name: String) {
        targetArtistName.value = name
    }

    fun getTargetArtistName(): String {
        return targetArtistName.value
    }

    @Stable
    private val targetList: MutableState<List<YosMediaItem>> = mutableStateOf(emptyList())
    @Stable
    private val targetListTitle = mutableStateOf("")
    @Stable
    val refreshTrigger = mutableStateOf(0)

    fun setTargetListWithTitle(title: String, list: List<YosMediaItem>) {
        targetListTitle.value = title
        targetList.value = list
        refreshTrigger.value++
    }

    fun updateSongInTargetList(updated: YosMediaItem) {
        val current = targetList.value.toMutableList()
        val idx = current.indexOfFirst { it.uri == updated.uri }
        if (idx >= 0) {
            current[idx] = updated
            targetList.value = current
            refreshTrigger.value++
        }
    }

    fun getTargetListWithTitle(): Pair<String, List<YosMediaItem>> {
        return Pair(targetListTitle.value, targetList.value)
    }
}