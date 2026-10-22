package yos.music.player.logic.mss

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import yos.music.player.data.libraries.SettingsLibrary
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Media3 PCM processor backed by the APK's realtime two-stem MSS engine. */
class MssAudioProcessor(context: Context) : BaseAudioProcessor() {
    private val appContext = context.applicationContext
    private var nativeBridge: MssNativeBridge? = null
    private var nativeOutput = AudioProcessor.EMPTY_BUFFER
    private var convertedInput = AudioProcessor.EMPTY_BUFFER
    private var generation = 0L
    private var configured = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        Log.i(
            TAG,
            "configure requested sampleRate=${inputAudioFormat.sampleRate} " +
                "channels=${inputAudioFormat.channelCount} encoding=${inputAudioFormat.encoding}"
        )
        if (inputAudioFormat.encoding !in SUPPORTED_ENCODINGS ||
            inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount !in 1..2
        ) {
            configured = false
            Log.w(TAG, "unsupported input format: $inputAudioFormat")
            VocalSeparationController.reportAvailable(false)
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val bridge = nativeBridge ?: runCatching {
            MssNativeBridge(appContext, SettingsLibrary.MssGpuAccelerationEnabled)
        }.onFailure { Log.e(TAG, "Unable to create MSS engine", it) }.getOrNull().also {
            nativeBridge = it
        }
        configured = bridge?.isCreated == true &&
            bridge.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        Log.i(
            TAG,
            "configure result created=${bridge?.isCreated} configured=$configured " +
                "status=${bridge?.status} failure=${bridge?.failureReason} " +
                "gpu=${bridge?.gpuBackendStatus()} backend=${bridge?.inferenceBackend()}"
        )
        VocalSeparationController.reportAvailable(
            configured,
            usingGpu = configured && bridge?.inferenceBackend() == MssNativeBridge.BACKEND_GPU
        )
        if (!configured) return AudioProcessor.AudioFormat.NOT_SET
        ensureNativeOutput(inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val bridge = nativeBridge
        if (!configured || bridge == null) {
            copyPassthrough(inputBuffer)
            return
        }
        syncControl(bridge)
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        val frameCount = minOf(inputBuffer.remaining() / bytesPerFrame, MAX_FRAMES)
        if (frameCount <= 0) return
        val start = inputBuffer.position()
        val nativeInput = inputBuffer.slice().order(ByteOrder.nativeOrder()).apply {
            limit(frameCount * bytesPerFrame)
        }
        val pcm16Input = convertInputToPcm16(nativeInput, frameCount)
        val accepted = bridge.queueInput(pcm16Input, frameCount, generation)
        if (accepted < 0) {
            copyPassthrough(inputBuffer)
            checkStatus(bridge)
            return
        }
        if (accepted > 0) inputBuffer.position(start + accepted * bytesPerFrame)
        drainNativeOutput(bridge)
        checkStatus(bridge)
    }

    override fun getOutput(): ByteBuffer {
        val pending = super.getOutput()
        if (pending.hasRemaining()) return pending
        nativeBridge?.takeIf { configured }?.let(::drainNativeOutput)
        return super.getOutput()
    }

    override fun isEnded(): Boolean = super.isEnded() && (nativeBridge?.isDrained != false)

    override fun onQueueEndOfStream() {
        nativeBridge?.queueEndOfStream()
    }

    override fun onFlush() {
        generation++
        nativeBridge?.flush(generation)
        VocalSeparationController.state.value.let { state ->
            if (configured) VocalSeparationController.reportAvailable(true, state.usingGpu)
        }
    }

    override fun onReset() {
        generation++
        configured = false
        nativeBridge?.close()
        nativeBridge = null
        nativeOutput = AudioProcessor.EMPTY_BUFFER
        convertedInput = AudioProcessor.EMPTY_BUFFER
        VocalSeparationController.reportAvailable(false)
    }

    private fun syncControl(bridge: MssNativeBridge) {
        val state = VocalSeparationController.state.value
        bridge.setControl(state.enabled, state.vocalGain, state.accompanimentGain)
    }

    private fun checkStatus(bridge: MssNativeBridge) {
        when (bridge.status) {
            MssNativeBridge.STATUS_READY -> VocalSeparationController.reportAvailable(
                true,
                bridge.inferenceBackend() == MssNativeBridge.BACKEND_GPU
            )
            MssNativeBridge.STATUS_RUNTIME_FAILURE -> VocalSeparationController.reportFailure()
            MssNativeBridge.STATUS_UNSUPPORTED_FORMAT -> VocalSeparationController.reportAvailable(false)
        }
    }

    private fun drainNativeOutput(bridge: MssNativeBridge) {
        if (hasPendingOutput()) return
        nativeOutput.clear()
        val frames = bridge.dequeueOutput(nativeOutput, MAX_FRAMES, generation)
        if (frames <= 0) return
        val byteCount = frames * inputAudioFormat.bytesPerFrame
        nativeOutput.position(0)
        nativeOutput.limit(frames * inputAudioFormat.channelCount * PCM16_BYTES_PER_SAMPLE)
        val output = replaceOutputBuffer(byteCount)
        convertPcm16ToOutput(nativeOutput, output, frames)
        output.flip()
    }

    private fun copyPassthrough(inputBuffer: ByteBuffer) {
        replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }

    private fun ensureNativeOutput(channelCount: Int) {
        val capacity = MAX_FRAMES * channelCount * PCM16_BYTES_PER_SAMPLE
        if (nativeOutput.capacity() < capacity) {
            nativeOutput = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
        }
        if (convertedInput.capacity() < capacity) {
            convertedInput = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
        }
    }

    /** Mirrors the source APK's PCM8/16/24/32/float -> signed PCM16 adapter. */
    private fun convertInputToPcm16(input: ByteBuffer, frames: Int): ByteBuffer {
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            return input.apply {
                position(0)
                limit(frames * inputAudioFormat.channelCount * PCM16_BYTES_PER_SAMPLE)
            }
        }
        convertedInput.clear()
        repeat(frames * inputAudioFormat.channelCount) {
            val normalized = when (inputAudioFormat.encoding) {
                C.ENCODING_PCM_8BIT -> ((input.get().toInt() and 0xff) - 128) / 128f
                C.ENCODING_PCM_FLOAT -> input.float
                C.ENCODING_PCM_24BIT -> {
                    val b0 = input.get().toInt() and 0xff
                    val b1 = input.get().toInt() and 0xff
                    val b2 = input.get().toInt() and 0xff
                    var sample = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                        b0 or (b1 shl 8) or (b2 shl 16)
                    } else {
                        (b0 shl 16) or (b1 shl 8) or b2
                    }
                    if (sample and 0x800000 != 0) sample = sample or -0x1000000
                    sample / 8_388_608f
                }
                C.ENCODING_PCM_32BIT -> input.int / 2_147_483_648f
                else -> 0f
            }
            convertedInput.putShort((normalized.coerceIn(-1f, 1f) * 32_767f)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
        return convertedInput.apply { flip() }
    }

    /** Mirrors the source APK's signed PCM16 -> original sink PCM representation adapter. */
    private fun convertPcm16ToOutput(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            output.put(input)
            return
        }
        repeat(frames * inputAudioFormat.channelCount) {
            val normalized = (input.short / 32_768f).coerceIn(-1f, 1f)
            when (inputAudioFormat.encoding) {
                C.ENCODING_PCM_8BIT -> output.put(
                    ((normalized * 127f).toInt() + 128).coerceIn(0, 255).toByte()
                )
                C.ENCODING_PCM_FLOAT -> output.putFloat(normalized)
                C.ENCODING_PCM_24BIT -> {
                    val sample = (normalized * 8_388_607f).toInt()
                    if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                        output.put((sample and 0xff).toByte())
                        output.put((sample shr 8 and 0xff).toByte())
                        output.put((sample shr 16 and 0xff).toByte())
                    } else {
                        output.put((sample shr 16 and 0xff).toByte())
                        output.put((sample shr 8 and 0xff).toByte())
                        output.put((sample and 0xff).toByte())
                    }
                }
                C.ENCODING_PCM_32BIT -> output.putInt((normalized * 2_147_483_647f).toInt())
            }
        }
    }

    private companion object {
        const val TAG = "MssAudioProcessor"
        const val MAX_FRAMES = 32_768
        const val PCM16_BYTES_PER_SAMPLE = 2
        val SUPPORTED_ENCODINGS = setOf(
            C.ENCODING_PCM_8BIT,
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT
        )
    }
}
