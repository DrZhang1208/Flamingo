package yos.music.player.code.utils.player

import kotlinx.coroutines.Job

/** A queued UI update must still belong to an active refresh when it executes. */
internal fun postPlaybackUpdate(job: Job, post: (() -> Unit) -> Unit, update: () -> Unit) {
    post { if (job.isActive) update() }
}

/** Construct the tail from the current player identity, not a previous UI snapshot. */
internal fun <T, K> playbackTail(
    queue: List<T>, source: List<T>?, current: K, shuffled: Boolean, key: (T) -> K
): List<T> {
    if (shuffled) return queue.filter { key(it) != current }.shuffled()
    val ordered = source ?: queue
    val index = ordered.indexOfFirst { key(it) == current }
    if (index < 0) return ordered.filter { key(it) != current }
    return ordered.drop(index + 1) + ordered.take(index)
}
