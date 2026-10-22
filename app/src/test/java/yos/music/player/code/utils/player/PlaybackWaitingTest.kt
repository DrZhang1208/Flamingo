package yos.music.player.code.utils.player

import androidx.media3.common.PlaybackException
import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class PlaybackWaitingTest {
    @Test fun bufferAndNetworkRetryBothShowLoadingWithoutRequiringActualPlaying() {
        assertTrue(playbackIsWaiting(true, false, true, false))
        assertTrue(playbackIsWaiting(true, false, false, true))
        assertFalse(playbackIsWaiting(true, false, false, false))
    }
    @Test fun userPauseSuppressesLoadingImmediatelyEvenDuringFadeOut() {
        assertFalse(playbackIsWaiting(true, true, true, true))
        assertFalse(playbackIsWaiting(false, false, true, true))
    }
    @Test fun remoteConnectionFailuresAreRetryableButLocalAndTerminalErrorsAreNot() {
        val failed = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        assertTrue(retryableNetworkPlaybackFailure(failed, "https", null))
        assertTrue(retryableNetworkPlaybackFailure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, "webdav", null))
        assertFalse(retryableNetworkPlaybackFailure(failed, "file", null))
        assertFalse(retryableNetworkPlaybackFailure(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, "webdav", null))
        assertFalse(retryableNetworkPlaybackFailure(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "https", null))
    }
    @Test fun wrappedWebDavDnsFailureRetriesWhileHttpErrorDoesNot() {
        val code = PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        assertTrue(retryableNetworkPlaybackFailure(code, "webdav", IOException("open", UnknownHostException("offline"))))
        assertFalse(retryableNetworkPlaybackFailure(code, "webdav", IOException("HTTP 403")))
    }
    @Test fun retriesBackOffAndStayBoundedDuringLongOutage() {
        assertEquals(listOf(2000L, 4000L, 8000L, 10000L, 10000L), (0..4).map(::networkPlaybackRetryDelayMs))
    }
}
