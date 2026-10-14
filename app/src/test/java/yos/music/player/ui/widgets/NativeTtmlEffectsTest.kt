package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class NativeTtmlEffectsTest {
    @Test fun heldFinalGlyphHasOneCrestAndReturnsWithoutWaitingForItsNoteEnd() {
        for (duration in listOf(1000f, 2000f, 5000f, 20000f)) {
            val start = 1000f; val end = start + duration
            assertEquals(0f, nativeTtmlGlowStrength(start, end, 200f, terminal = true), 0f)
            assertEquals(1f, nativeTtmlGlowStrength(start, end, 1160f, terminal = true), 0.00001f)
            assertTrue(nativeTtmlGlowStrength(start, end, 1200f, terminal = true) < 1f)
            assertTrue(nativeTtmlGlowStrength(start, end, 1400f, terminal = true) < 0.5f)
            assertEquals(0f, nativeTtmlGlowStrength(start, end, 1800f, terminal = true), 0f)
            assertEquals(0f, nativeTtmlLiftStrength(start, end, end, terminal = true), 0f)
        }
    }
    @Test fun waveHasNoPeakPlateauAndSmoothlyCrossfadesToTheNextGlyph() {
        assertEquals(0f, nativeTtmlLiftStrength(1000f, 3000f, 200f), 0f)
        assertTrue(nativeTtmlLiftStrength(1000f, 3000f, 1000f) > 0f)
        for (delta in listOf(8f, 32f, 80f)) {
            val before = nativeTtmlGlowStrength(1000f, 3000f, 1160f - delta)
            val after = nativeTtmlGlowStrength(1000f, 3000f, 1160f + delta)
            assertTrue(before < 1f)
            assertEquals(before, after, 0.00001f)
        }
        val samples = (740..1700 step 8).map { nativeTtmlLiftStrength(1000f, 3000f, it.toFloat()) }
        val velocities = samples.zipWithNext().map { it.second - it.first }
        assertTrue(velocities.zipWithNext().all { abs(it.second - it.first) < 0.015f })
    }
    @Test fun shortNotesNeverTriggerEmphasis() {
        for (time in 0..4000 step 50) {
            assertEquals(0f, nativeTtmlGlowStrength(1000f, 1999f, time.toFloat()), 0f)
            assertEquals(0f, nativeTtmlLiftStrength(1000f, 1999f, time.toFloat()), 0f)
        }
        assertEquals(1f, nativeTtmlEmphasisScale(1f, 999f), 0f)
    }
    @Test fun durationControlsSwellAndLongWordsCannotPushTheirNeighboursFar() {
        val short = nativeTtmlEmphasisScale(1f, 1000f)
        val held = nativeTtmlEmphasisScale(1f, 3000f)
        assertTrue(short in 1.03f..1.05f)
        assertTrue(held > short)
        assertTrue(nativeTtmlEmphasisScale(1f, 3000f, true) > held)
        assertTrue(nativeTtmlEmphasisScale(1f, 9000f, true) <= 1.08f)
        assertTrue((nativeTtmlEmphasisScale(1f, 9000f, true, 20f) - 1f) * 20f <= 0.28001f)
    }
    @Test fun seekingAndReversePlaybackProduceTheSameGeometryAtTheSameTime() {
        val times = (0..5000 step 20).map(Int::toFloat)
        val forward = times.map { nativeTtmlEmphasisScale(nativeTtmlGlowStrength(1000f, 3000f, it)) }
        val reverse = times.reversed().map { nativeTtmlEmphasisScale(nativeTtmlGlowStrength(1000f, 3000f, it)) }.reversed()
        assertEquals(forward, reverse)
        assertEquals(1f, forward.first(), 0f); assertEquals(1f, forward.last(), 0f)
        assertTrue(forward.zipWithNext().all { abs(it.second - it.first) < 0.015f })
    }
    @Test fun glyphWaveIsOrderedAndBoundedEvenForMalformedLongTokens() {
        for (count in listOf(1, 3, 7, 1000)) {
            val delays = (0 until count).map { nativeTtmlGlyphDelayMs(it, count, 5000f) }
            assertTrue(delays.zipWithNext().all { it.second > it.first })
            assertTrue(delays.last() < 5000f)

        }
    }
    @Test fun glowAndLiftLeaveNoStaleEmphasisAfterASeekOrNoteEnd() {
        for (time in listOf(-1000f, 0f, 4000f, 10000f)) {
            assertEquals(0f, nativeTtmlGlowStrength(1000f, 3000f, time), 0f)
            assertEquals(0f, nativeTtmlLiftStrength(1000f, 3000f, time), 0f)
        }
    }
    @Test fun neighbouringGlyphsRemainJoinedWhileTheCrestMovesForward() {
        for (count in listOf(2, 7, 24)) {
            val duration = 6000f
            val slot = nativeTtmlGlyphStepMs(duration, count)
            for (index in 1 until count) {
                val time = 1000f + nativeTtmlGlyphDelayMs(index, count, duration) + slot * 0.5f
                val previous = nativeTtmlGlowStrength(1000f, 7000f,
                    time - nativeTtmlGlyphDelayMs(index - 1, count, duration), count)
                val current = nativeTtmlGlowStrength(1000f, 7000f,
                    time - nativeTtmlGlyphDelayMs(index, count, duration), count)
                assertTrue("The outgoing glyph should be on the broad shoulder at the next peak", previous <= 0.57f)
                assertEquals(1f, current, 0.00001f)
            }
            for (time in 1000..7000 step 10) {
                val enlarged = (0 until count).count { index ->
                    nativeTtmlGlowStrength(1000f, 7000f,
                        time - nativeTtmlGlyphDelayMs(index, count, duration), count) > 0.1f
                }
                assertTrue("Only a local cluster may overlap, never a whole row", enlarged <= 4)
            }
        }
    }

    @Test fun adjacentGlyphsReallyOverlapWithOneFallingWhileTheOtherRises() {
        for (count in listOf(2, 7, 24)) {
            val slot = nativeTtmlGlyphStepMs(6000f, count)
            val handoff = 1000f + slot
            fun previous(time: Float) = nativeTtmlGlowStrength(1000f, 7000f, time, count)
            fun next(time: Float) = nativeTtmlGlowStrength(1000f, 7000f, time - slot, count)
            assertTrue(previous(handoff) in 0.1f..0.95f)
            assertTrue(next(handoff) in 0.1f..0.95f)
            assertTrue(previous(handoff + 5f) < previous(handoff - 5f))
            assertTrue(next(handoff + 5f) > next(handoff - 5f))
        }
    }

    @Test fun overlapLastsLongerWithoutKeepingTheWholeWordEnlarged() {
        val count = 7
        val duration = 2000f
        val slot = nativeTtmlGlyphStepMs(duration, count)
        fun strength(index: Int, time: Float) = nativeTtmlGlowStrength(1000f, 3000f,
            time - nativeTtmlGlyphDelayMs(index, count, duration), count)
        val overlapFrames = (800..3400 step 5).count { time ->
            strength(2, time.toFloat()) > 0.1f && strength(3, time.toFloat()) > 0.1f
        }
        assertTrue("Adjacent visible overlap should last at least 200ms", overlapFrames * 5 >= 200)
        for (time in 800..3400 step 5) {
            val strengths = (0 until count).map { strength(it, time.toFloat()) }
            assertTrue("At most six glyphs may have any tail", strengths.count { it > 0f } <= 6)
            assertTrue("At most four glyphs may have visible tails", strengths.count { it > 0.1f } <= 4)
        }
    }

    @Test fun finalGlyphOfAWordDoesNotKeepItsCrestOrGlowAfterTheWavePasses() {
        for (count in listOf(1, 3, 7, 20)) {
            val duration = 12000f
            val delay = nativeTtmlGlyphDelayMs(count - 1, count, duration)
            val step = nativeTtmlGlyphStepMs(duration, count)
            val peak = 1000f + delay + step * 0.5f
            assertEquals(1f, nativeTtmlGlowStrength(1000f, 13000f, peak - delay, count), 0.00001f)
            for (time in listOf(peak + step * 3f, 13000f, 14000f)) {
                assertEquals(0f, nativeTtmlGlowStrength(1000f, 13000f, time - delay, count), 0.00001f)
            }
        }
    }

    @Test fun travellingWaveHasConstantTotalStrengthAcrossEveryHandoff() {
        val count = 16
        val duration = 5000f
        val step = nativeTtmlGlyphStepMs(duration, count)
        for (frame in 0..120) {
            val time = 1000f + (6.5f + frame / 120f) * step
            val strengths = (0 until count).map { index ->
                nativeTtmlGlowStrength(1000f, 6000f,
                    time - nativeTtmlGlyphDelayMs(index, count, duration), count)
            }
            assertEquals("Wave strength must not pulse as its crest changes glyphs",
                2.25f, strengths.sum(), 0.00001f)
            assertTrue("The strongest crest must stay local", strengths.count { it > 0.5f } <= 3)
            assertTrue("The row must never be enlarged together", strengths.count { it > 0f } <= 6)
        }
    }

    @Test fun waveIsCohesiveRatherThanAnIsolatedGlyphPulse() {
        val count = 12
        val duration = 6000f
        val step = nativeTtmlGlyphStepMs(duration, count)
        assertTrue("Neighbour phase must be close enough to form one wave", step <= 160f)
        val crest = 1000f + nativeTtmlGlyphDelayMs(5, count, duration) + step * 0.5f
        val strengths = (0 until count).map { index -> nativeTtmlGlowStrength(1000f, 7000f,
            crest - nativeTtmlGlyphDelayMs(index, count, duration), count) }
        assertEquals(1f, strengths[5], 0.00001f)
        assertTrue("Both neighbouring glyphs must visibly join the crest", strengths[4] > 0.55f && strengths[6] > 0.55f)
        assertTrue("The next shoulders must softly join too", strengths[3] > 0.06f && strengths[7] > 0.06f)
        assertTrue("Distant glyphs must remain at their original size", strengths.take(2).all { it == 0f })
    }

    @Test fun separateSingleCharacterNotesAlsoHaveLongOverlapButTheFinalNoteReturnsEarly() {
        var overlap = 0
        for (time in 0..5000 step 5) {
            val first = nativeTtmlGlowStrength(1000f, 2200f, time.toFloat())
            val next = nativeTtmlGlowStrength(2200f, 3400f, time.toFloat())
            if (first > 0.02f && next > 0.02f) overlap += 5
        }
        assertTrue("Single-character timing must also have a joined wave", overlap >= 200)
        assertEquals(0f, nativeTtmlGlowStrength(2200f, 10000f, 2800f, terminal = true), 0f)
    }

    @Test fun extraRiseKeepsThePreviouslyReducedAmplitude() {
        assertEquals(0f, nativeTtmlExtraRiseDp(0f), 0f)
        assertEquals(1.5f, nativeTtmlExtraRiseDp(1f), 0f)
    }
}
