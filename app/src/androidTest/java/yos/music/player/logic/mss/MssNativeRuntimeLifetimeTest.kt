package yos.music.player.logic.mss

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Native regression: audio-sink resets must not unload the runtimes' TLS state. */
class MssNativeRuntimeLifetimeTest {
    @Test
    fun cpuRuntimeRemainsMappedAcrossEngineResets() = checkEngineResets(useGpu = false)

    @Test
    fun gpuRuntimeRemainsMappedAcrossEngineResets() = checkEngineResets(useGpu = true)

    private fun checkEngineResets(useGpu: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val libraries = if (useGpu) {
            listOf("libLiteRt.so", "libLiteRtClGlAccelerator.so")
        } else {
            listOf("libLiteRt.so")
        }
        var originalMappings: Map<String, List<String>>? = null
        repeat(24) { cycle ->
            val bridge = MssNativeBridge(context, useGpu)
            try {
                assertTrue("Engine creation failed at cycle $cycle", bridge.isCreated)
                assertTrue(bridge.configure(if (cycle % 2 == 0) 48_000 else 44_100, 2))
                bridge.flush(cycle.toLong())
                val mappings = libraryMappings(libraries)
                mappings.forEach { (library, ranges) ->
                    assertTrue("$library is not mapped at cycle $cycle", ranges.isNotEmpty())
                }
                if (originalMappings == null) originalMappings = mappings
                assertEquals("Runtime reloaded at cycle $cycle", originalMappings, mappings)
            } finally {
                bridge.close()
            }
            bridge.close() // Release stays idempotent; the model is not kept alive.
            assertFalse(bridge.isCreated)
            assertEquals(MssNativeBridge.STATUS_RELEASED, bridge.status)
            assertEquals("Runtime unloaded after cycle $cycle", originalMappings, libraryMappings(libraries))
        }
    }

    private fun libraryMappings(libraries: List<String>): Map<String, List<String>> {
        val maps = File("/proc/self/maps").readLines()
        return libraries.associateWith { library ->
            maps.filter { it.substringAfterLast('/') == library }
                .map { it.substringBefore(' ') }
        }
    }
}
