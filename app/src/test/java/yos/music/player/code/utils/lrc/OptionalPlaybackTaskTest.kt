package yos.music.player.code.utils.lrc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import yos.music.player.code.utils.player.optionalPlaybackTask

class OptionalPlaybackTaskTest {
    @Test fun failedOptionalSourceAllowsFallback() = runBlocking {
        val failures = mutableListOf<Exception>()
        val result = optionalPlaybackTask(failures::add) { throw SecurityException("permission revoked") }
            ?: optionalPlaybackTask(failures::add) { "embedded lyrics" }
        assertEquals("embedded lyrics", result)
        assertEquals(1, failures.size)
    }

    @Test fun cancellationCannotStartFallbackOrBeReportedAsFailure() = runBlocking {
        val cancellation = CancellationException("track changed")
        var reported = false
        try {
            optionalPlaybackTask({ reported = true }) { throw cancellation }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertFalse(reported)
    }

    @Test fun fatalErrorsAreNotHidden() = runBlocking {
        val fatal = AssertionError("invariant failed")
        try {
            optionalPlaybackTask({ fail("Must not swallow Errors") }) { throw fatal }
            fail("Error must propagate")
        } catch (actual: AssertionError) {
            assertSame(fatal, actual)
        }
    }
}
