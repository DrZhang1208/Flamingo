package yos.music.player.ui.widgets

import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.abs
import kotlin.math.sqrt

/** All visible rows share one animation driver. Retargeting preserves sampled velocity. */
internal class NativeLyricScrollMotion {
    private class Row(val index: Int, val params: WavySpringParams, travel: Float, reset: Int) {
        var offset = 0f
        var velocity = 0f
        var consumedTravel = travel
        var consumedReset = reset
        var origin = 0f
        var originVelocity = 0f
        var startNanos = 0L
        var moving = false
    }
    private val rows = LinkedHashMap<Any, Row>()
    private val revision = mutableIntStateOf(0)

    fun register(key: Any, index: Int, params: WavySpringParams, travel: Float, reset: Int) {
        rows[key] = Row(index, params, travel, reset)
    }
    fun unregister(key: Any) { rows.remove(key) }

    fun displacement(key: Any, travel: Float, reset: Int): Float {
        revision.intValue // One draw-layer invalidation source for every row.
        val row = rows[key] ?: return 0f
        return resolveWaveOffset(row.offset, row.consumedTravel, row.consumedReset, travel, reset)
    }

    fun retarget(travel: Float, reset: Int, anchor: Int, nowNanos: Long) {
        rows.values.forEach { row ->
            if (row.consumedReset != reset) row.velocity = 0f
            row.offset = resolveWaveOffset(row.offset, row.consumedTravel, row.consumedReset, travel, reset)
            row.consumedTravel = travel
            row.consumedReset = reset
            row.origin = row.offset
            row.originVelocity = row.velocity
            row.startNanos = nowNanos + wavyRowDelayMs(row.index, anchor) * 1_000_000L
            row.moving = abs(row.offset) >= 0.01f || abs(row.velocity) >= 0.01f
        }
        revision.intValue++
    }

    fun frame(nowNanos: Long): Boolean {
        var moving = false
        rows.values.forEach { row ->
            if (!row.moving) return@forEach
            if (nowNanos >= row.startNanos) {
                val omega = sqrt(row.params.stiffness / row.params.mass)
                val damping = row.params.damping / (2f * sqrt(row.params.stiffness * row.params.mass))
                val sample = sampleSpring(row.origin, row.originVelocity,
                    (nowNanos - row.startNanos) / 1_000_000_000f, omega, damping)
                row.offset = sample.position
                row.velocity = sample.velocity
                if (abs(row.offset) < 0.05f && abs(row.velocity) < 0.05f) {
                    row.offset = 0f; row.velocity = 0f; row.moving = false
                }
            }
            moving = moving || row.moving
        }
        revision.intValue++
        return moving
    }
}
