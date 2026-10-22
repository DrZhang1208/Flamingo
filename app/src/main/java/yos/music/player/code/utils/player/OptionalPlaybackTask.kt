package yos.music.player.code.utils.player

import kotlinx.coroutines.CancellationException

/** Failure of optional metadata/persistence work must not terminate playback. */
internal suspend fun <T> optionalPlaybackTask(
    onFailure: (Exception) -> Unit,
    block: suspend () -> T
): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    onFailure(failure)
    null
}
