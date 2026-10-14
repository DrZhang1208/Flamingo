package yos.music.player.logic.mss

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.annotation.Keep
import java.nio.ByteBuffer

/** JNI surface used by the MSS engine bundled with the same-source APK. */
@Keep
class MssNativeBridge(context: Context, useGpu: Boolean) : AutoCloseable {
    private var handle: Long = nativeCreate(context.assets, useGpu && gpuRuntimeLoaded)

    val isCreated: Boolean get() = handle != 0L
    val status: Int get() = if (handle == 0L) STATUS_RELEASED else nativeGetStatus(handle)
    val failureReason: Int get() = if (handle == 0L) FAILURE_NONE else nativeFailureReason(handle)
    val isDrained: Boolean get() = handle == 0L || nativeIsDrained(handle)
    val hasPendingData: Boolean get() = handle != 0L && nativeHasPendingData(handle)

    fun configure(sampleRate: Int, channelCount: Int): Boolean =
        handle != 0L && nativeConfigure(handle, sampleRate, channelCount)

    fun setControl(enabled: Boolean, vocalGain: Float, accompanimentGain: Float) {
        if (handle != 0L) nativeSetControl(handle, enabled, vocalGain, accompanimentGain)
    }

    fun queueInput(buffer: ByteBuffer, frames: Int, generation: Long): Int =
        if (handle == 0L) -1 else nativeQueueInput(handle, buffer, frames, generation)

    fun dequeueOutput(buffer: ByteBuffer, frames: Int, generation: Long): Int =
        if (handle == 0L) 0 else nativeDequeueOutput(handle, buffer, frames, generation)

    fun flush(generation: Long) {
        if (handle != 0L) nativeFlush(handle, generation)
    }

    fun queueEndOfStream() {
        if (handle != 0L) nativeQueueEndOfStream(handle)
    }

    fun gpuBackendStatus(): Int = if (handle == 0L) GPU_UNAVAILABLE else nativeGpuBackendStatus(handle)
    fun inferenceBackend(): Int = if (handle == 0L) BACKEND_CPU else nativeInferenceBackend(handle)

    override fun close() {
        val current = handle
        handle = 0L
        if (current != 0L) nativeRelease(current)
    }

    private external fun nativeCreate(assetManager: AssetManager, useGpu: Boolean): Long
    private external fun nativeConfigure(handle: Long, sampleRate: Int, channelCount: Int): Boolean
    private external fun nativeSetControl(handle: Long, enabled: Boolean, vocalGain: Float, accompanimentGain: Float)
    private external fun nativeQueueInput(handle: Long, buffer: ByteBuffer, frames: Int, generation: Long): Int
    private external fun nativeDequeueOutput(handle: Long, buffer: ByteBuffer, frames: Int, generation: Long): Int
    private external fun nativeFlush(handle: Long, generation: Long)
    private external fun nativeQueueEndOfStream(handle: Long)
    private external fun nativeIsDrained(handle: Long): Boolean
    private external fun nativeHasPendingData(handle: Long): Boolean
    private external fun nativeGetStatus(handle: Long): Int
    private external fun nativeFailureReason(handle: Long): Int
    private external fun nativeGpuBackendStatus(handle: Long): Int
    private external fun nativeInferenceBackend(handle: Long): Int
    private external fun nativeRelease(handle: Long)

    companion object {
        const val BACKEND_CPU = 0
        const val BACKEND_GPU = 1
        const val STATUS_READY = 0
        const val STATUS_UNSUPPORTED_FORMAT = 1
        const val STATUS_RUNTIME_FAILURE = 2
        const val STATUS_RELEASED = 3
        const val FAILURE_NONE = 0
        const val GPU_ACTIVE = 1
        const val GPU_UNAVAILABLE = 2

        // Keep a class-loader-owned reference to both runtimes. The engine's dlclose
        // on reset must not unload their process-lifetime TLS state: repeated loads
        // consume pthread keys until LiteRT's emutls initialization calls abort().
        // GPU loading remains optional, so devices without it can still use CPU.
        private val gpuRuntimeLoaded: Boolean by lazy {
            try {
                System.loadLibrary("LiteRtClGlAccelerator")
                true
            } catch (error: UnsatisfiedLinkError) {
                Log.w("MssNativeBridge", "Unable to load MSS GPU runtime; using CPU", error)
                false
            }
        }

        init {
            System.loadLibrary("mss_engine")
            System.loadLibrary("LiteRt")
        }
    }
}
