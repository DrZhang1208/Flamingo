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
    // Virtualization follows the moving viewport, not only its destination.
    private val viewport = Row(-1, WavySpringParams(0.9f, 24f, 170f), 0f, 0)
    private var anchor = -1
    private var lastFrameNanos = 0L

    fun register(key: Any, index: Int, params: WavySpringParams, travel: Float, reset: Int) {
        if (rows.containsKey(key)) return
        val row = Row(index, params, viewport.consumedTravel, viewport.consumedReset)
        if (viewport.consumedReset == reset && viewport.moving) {
            row.origin = viewport.origin
            row.originVelocity = viewport.originVelocity
            row.startNanos = viewport.startNanos + wavyRowDelayMs(index, anchor) * 1_000_000L
            row.offset = row.origin
            row.velocity = row.originVelocity
            row.moving = true
            sample(row, lastFrameNanos)
        }
        rows[key] = row
    }
    fun unregister(key: Any) { rows.remove(key) }

    fun displacement(key: Any, travel: Float, reset: Int,
        index: Int? = null, params: WavySpringParams? = null): Float {
        revision.intValue // One draw-layer invalidation source for every row.
        // Subcomposed rows can be measured before DisposableEffect commits. Seed
        // their very first layer read too, so no final-position frame can escape.
        if (rows[key] == null && index != null && params != null) register(key, index, params, travel, reset)
        val row = rows[key] ?: return 0f
        return resolveWaveOffset(row.offset, row.consumedTravel, row.consumedReset, travel, reset)
    }

    fun visibleDisplacements(travel: Float, reset: Int): List<Float> {
        revision.intValue
        val offsets = rows.values.map {
            resolveWaveOffset(it.offset, it.consumedTravel, it.consumedReset, travel, reset)
        }
        return listOf(
            resolveWaveOffset(viewport.offset, viewport.consumedTravel, viewport.consumedReset, travel, reset),
            offsets.minOrNull() ?: 0f,
            offsets.maxOrNull() ?: 0f
        )
    }

    fun retarget(travel: Float, reset: Int, anchor: Int, nowNanos: Long) {
        this.anchor = anchor
        lastFrameNanos = nowNanos
        (rows.values + viewport).forEach { row ->
            if (row.consumedReset != reset) row.velocity = 0f
            row.offset = resolveWaveOffset(row.offset, row.consumedTravel, row.consumedReset, travel, reset)
            row.consumedTravel = travel
            row.consumedReset = reset
            row.origin = row.offset
            row.originVelocity = row.velocity
            row.startNanos = nowNanos + (if (row === viewport) 0L else wavyRowDelayMs(row.index, anchor)) * 1_000_000L
            row.moving = abs(row.offset) >= 0.01f || abs(row.velocity) >= 0.01f
        }
        revision.intValue++
    }

    private fun sample(row: Row, nowNanos: Long) {
        if (!row.moving || nowNanos < row.startNanos) return
        val omega = sqrt(row.params.stiffness / row.params.mass)
        val damping = row.params.damping / (2f * sqrt(row.params.stiffness * row.params.mass))
        val value = sampleSpring(row.origin, row.originVelocity,
            (nowNanos - row.startNanos) / 1_000_000_000f, omega, damping)
        row.offset = value.position
        row.velocity = value.velocity
        if (abs(row.offset) < 0.05f && abs(row.velocity) < 0.05f) {
            row.offset = 0f
            row.velocity = 0f
            row.moving = false
        }
    }

    fun frame(nowNanos: Long): Boolean {
        lastFrameNanos = nowNanos
        sample(viewport, nowNanos)
        rows.values.forEach { sample(it, nowNanos) }
        revision.intValue++
        return viewport.moving || rows.values.any { it.moving }
    }
}
