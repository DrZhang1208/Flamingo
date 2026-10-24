package yos.music.player.logic.mss

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VocalAdjustmentMode { VOCAL, ACCOMPANIMENT }
enum class VocalSeparationStatus { UNAVAILABLE, READY, ACTIVE, FAILED }

data class VocalSeparationState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val vocalGain: Float = 0.3f,
    val accompanimentGain: Float = 1f,
    val mode: VocalAdjustmentMode = VocalAdjustmentMode.VOCAL,
    val status: VocalSeparationStatus = VocalSeparationStatus.UNAVAILABLE,
    val usingGpu: Boolean = false,
    // 音频链路是否已跑过一次可用性判定（配置完成），供 UI 与歌词按钮同步出场
    val decided: Boolean = false
) {
    val selectedGain: Float
        get() = if (mode == VocalAdjustmentMode.VOCAL) vocalGain else accompanimentGain
}

/** Thread-safe control plane shared by Compose and the playback audio thread. */
object VocalSeparationController {
    private val mutableState = MutableStateFlow(VocalSeparationState())
    val state: StateFlow<VocalSeparationState> = mutableState.asStateFlow()

    /** 新曲目开始：重置可用性判定，待音频链路重新 configure 后再置位，
     *  供歌词翻译按钮与人声按钮在同一时机出场 */
    fun beginTrack() {
        val current = mutableState.value
        if (!current.decided) return
        mutableState.value = current.copy(decided = false)
    }

    fun toggle() = setEnabled(!mutableState.value.enabled)

    fun setEnabled(enabled: Boolean) {
        val current = mutableState.value
        if (!current.available) return
        val vocal = if (enabled && !current.enabled) 0.3f else current.vocalGain
        val accompaniment = if (enabled && !current.enabled) 1f else current.accompanimentGain
        mutableState.value = current.copy(
            enabled = enabled,
            vocalGain = vocal,
            accompanimentGain = accompaniment,
            status = if (enabled) VocalSeparationStatus.ACTIVE else VocalSeparationStatus.READY
        )
    }

    fun selectMode(mode: VocalAdjustmentMode) {
        mutableState.value = mutableState.value.copy(mode = mode)
    }

    fun setSelectedGain(gain: Float) {
        val value = gain.coerceIn(0f, 1f)
        mutableState.value = when (mutableState.value.mode) {
            VocalAdjustmentMode.VOCAL -> mutableState.value.copy(vocalGain = value)
            VocalAdjustmentMode.ACCOMPANIMENT -> mutableState.value.copy(accompanimentGain = value)
        }
    }

    internal fun reportAvailable(available: Boolean, usingGpu: Boolean = false) {
        val current = mutableState.value
        mutableState.value = current.copy(
            available = available,
            enabled = current.enabled && available,
            status = if (!available) VocalSeparationStatus.UNAVAILABLE
            else if (current.enabled) VocalSeparationStatus.ACTIVE else VocalSeparationStatus.READY,
            usingGpu = usingGpu,
            decided = true
        )
    }

    internal fun reportFailure() {
        mutableState.value = mutableState.value.copy(
            enabled = false,
            available = false,
            status = VocalSeparationStatus.FAILED
        )
    }
}
