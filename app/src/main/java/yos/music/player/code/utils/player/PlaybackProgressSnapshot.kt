package yos.music.player.code.utils.player

internal data class PlaybackProgressSnapshot(val positionMs: Long, val durationMs: Long)

/** A real zero is authoritative; only an absent player may use the startup preview. */
internal fun playbackProgressSnapshot(
    playerPositionMs: Long?, playerDurationMs: Long?, savedPositionMs: Long, songDurationMs: Long
): PlaybackProgressSnapshot {
    val duration = playerDurationMs?.takeIf { it > 0L } ?: songDurationMs.coerceAtLeast(0L)
    val position = (playerPositionMs ?: savedPositionMs).coerceAtLeast(0L)
    return PlaybackProgressSnapshot(if (duration > 0L) position.coerceAtMost(duration) else position, duration)
}
