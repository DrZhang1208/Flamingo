package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test

class NativeTtmlEffectsTest {
    @Test fun extraLiftRisesAndReturnsToOrdinaryWordMotion() {
        assertEquals(0f, nativeTtmlExtraRiseDp(nativeTtmlGlowStrength(1000f, 3000f, 1000f)), 0f)
        assertEquals(3f, nativeTtmlExtraRiseDp(nativeTtmlGlowStrength(1000f, 3000f, 2000f)), 0f)
        assertTrue(nativeTtmlExtraRiseDp(nativeTtmlGlowStrength(1000f, 3000f, 2880f)) < 3f)
        assertEquals(0f, nativeTtmlExtraRiseDp(nativeTtmlGlowStrength(1000f, 3000f, 3000f)), 0f)
    }
    @Test fun liftHasIndependentGentleRecoveryAndContinuousVelocity() {
        assertTrue(nativeTtmlLiftStrength(1000f, 3000f, 1000f) > 0f)
        assertEquals(1f, nativeTtmlLiftStrength(1000f, 3000f, 3000f), 0f)
        assertTrue(nativeTtmlLiftStrength(1000f, 3000f, 3120f) > 0f)
        assertEquals(0f, nativeTtmlLiftStrength(1000f, 3000f, 3320f), 0f)
        val frames = (800..3340 step 8).map { nativeTtmlLiftStrength(1000f, 3000f, it.toFloat()) }
        val velocities = frames.zipWithNext().map { it.second - it.first }
        assertTrue(velocities.zipWithNext().all { kotlin.math.abs(it.second - it.first) < 0.004f })
        assertEquals(0f, nativeTtmlLiftStrength(1000f, 1500f, 1250f), 0f)
    }
    @Test fun returnDurationDoesNotStretchWithLongNotes() {
        for (duration in listOf(900f, 2000f, 5000f)) {
            val peak = 1000f + duration
            assertEquals(1f, nativeTtmlLiftStrength(1000f, 1000f + duration, peak), 0.00001f)
            assertEquals(0.5f, nativeTtmlLiftStrength(1000f, 1000f + duration, peak + 160f), 0.00001f)
            assertEquals(0f, nativeTtmlLiftStrength(1000f, 1000f + duration, peak + 320f), 0f)
        }
    }
    @Test fun emphasisCompletesItsRiseBeforeReturning() {
        for (duration in listOf(900f, 2000f, 5000f)) {
            val end = 1000f + duration
            var previous = 0f
            for (time in 820..end.toInt() step 8) {
                val lift = nativeTtmlLiftStrength(1000f, end, time.toFloat())
                assertTrue("Lift must never start returning before its rise ends", lift >= previous)
                previous = lift
            }
            previous = 1f
            for (offset in 0..320 step 8) {
                val lift = nativeTtmlLiftStrength(1000f, end, end + offset)
                assertTrue("Return must start immediately and move only downwards", lift <= previous)
                assertEquals(1f + 0.025f * lift, nativeTtmlEmphasisScale(lift), 0.00001f)
                previous = lift
            }
        }
    }
    @Test fun graphemesReturnInReadingOrderWithOverlappingMotion() {
        val delays = (0..3).map { nativeTtmlGlyphDelayMs(it, 4) }
        assertTrue(delays.zipWithNext().all { it.second > it.first })
        val strengths = delays.map { nativeTtmlLiftStrength(1000f, 3000f, 3160f - it) }
        assertTrue(strengths.zipWithNext().all { it.second > it.first })
        assertEquals(0f, nativeTtmlGlyphDelayMs(0, 1), 0f)
        assertTrue(nativeTtmlGlyphDelayMs(99, 100) <= 260f)
    }
    @Test fun shortWordsAndTimesOutsideHeldNoteNeverGlow() {
        assertEquals(0f, nativeTtmlGlowStrength(1000f, 1500f, 1250f), 0f)
        for (time in listOf(0f, 1000f, 3000f, 5000f))
            assertEquals(0f, nativeTtmlGlowStrength(1000f, 3000f, time), 0f)
    }
    @Test fun emphasisReturnsToOriginalSizeAndIsBoundedDuringSeek() {
        for (time in listOf(0f, 1000f, 3000f, 4000f))
            assertEquals(1f, nativeTtmlEmphasisScale(nativeTtmlGlowStrength(1000f, 3000f, time)), 0f)
        assertEquals(1.025f, nativeTtmlEmphasisScale(nativeTtmlGlowStrength(1000f, 3000f, 2000f)), 0.0001f)
        val phases = (1000..3000 step 10).map { nativeTtmlEmphasisScale(nativeTtmlGlowStrength(1000f, 3000f, it.toFloat())) }
        assertTrue(phases.all { it in 1f..1.025f })
        assertTrue(phases.zipWithNext().all { kotlin.math.abs(it.first - it.second) < 0.005f })
    }
    @Test fun heldNoteFadesInAndOutAndSeekingIsDeterministic() {
        assertEquals(1f, nativeTtmlGlowStrength(1000f, 3000f, 2000f), 0f)
        val entering = nativeTtmlGlowStrength(1000f, 3000f, 1100f)
        assertTrue(entering > 0f && entering < 1f)
        val leaving = nativeTtmlGlowStrength(1000f, 3000f, 2880f)
        assertTrue(leaving > 0f && leaving < 1f)
        assertEquals(entering, nativeTtmlGlowStrength(1000f, 3000f, 1100f), 0f)
    }
}
