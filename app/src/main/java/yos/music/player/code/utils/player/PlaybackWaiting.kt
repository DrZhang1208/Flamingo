package yos.music.player.code.utils.player

import androidx.media3.common.PlaybackException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun playbackIsWaiting(
    playWhenReady: Boolean, pausePending: Boolean, buffering: Boolean, networkRetry: Boolean
): Boolean = playWhenReady && !pausePending && (buffering || networkRetry)

/** Retry transport failures only, not missing files, HTTP errors or bad decoders. */
internal fun retryableNetworkPlaybackFailure(code: Int, scheme: String?, cause: Throwable?): Boolean {
    if (scheme !in setOf("http", "https", "webdav")) return false
    if (code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) return true
    if (code != PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return false
    var error = cause
    repeat(16) {
        if (error is SocketException || error is SocketTimeoutException || error is UnknownHostException) return true
        error = error?.cause
    }
    return false
}

internal fun networkPlaybackRetryDelayMs(attempt: Int): Long = when (attempt) {
    0 -> 2000L
    1 -> 4000L
    2 -> 8000L
    else -> 10000L
}
