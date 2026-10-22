package yos.music.player.ui.widgets

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class NativeTtmlEffectsTest {
    @Test fun heldNotesPeakMidNoteAndFallBackBeforeTheNoteEnds() {
        val start = 1000f
        val duration = 5000f
        val end = start + duration
        for (terminal in listOf(false, true)) {
            for (offset in listOf(2000f, 2500f, 3500f)) {
                val glow = nativeTtmlGlowStrength(start, end, start + offset, terminal)
                assertTrue("A held note must stay emphasized mid-note at ${offset}ms, terminal=$terminal", glow > 0.1f)
                assertTrue(nativeTtmlEmphasisScale(glow, duration, terminal) > 1.005f)
            }
            val span = nativeTtmlEmphasisDurationMs(duration, terminal)
            val fallEnd = if (terminal) 0.7f else 0.85f
            assertTrue("Lift must still carry the word past the crest",
                nativeTtmlLiftStrength(start, end, start + duration * 0.4f, terminal) > 0.5f)
            assertEquals("The fall must finish inside the note at the cutoff",
                0f, nativeTtmlGlowStrength(start, end, start + span * fallEnd + 1f, terminal), 0f)
            assertEquals("Nothing may linger once the note itself has ended",
                0f, nativeTtmlGlowStrength(start, end, end, terminal), 0f)
            assertEquals(0f, nativeTtmlLiftStrength(start, end, end, terminal), 0f)
            assertTrue(nativeTtmlLiftStrength(start, end, start + span * fallEnd - 100f, terminal) > 0f)
        }
    }

    @Test fun terminalWindowsStretchThePeakButBothFallsFinishBeforeTheNoteEnds() {
        val start = 1000f
        for (duration in listOf(1000f, 2000f, 5000f, 20000f)) {
            val end = start + duration
            val ordinaryDuration = nativeTtmlEmphasisDurationMs(duration)
            val terminalDuration = nativeTtmlEmphasisDurationMs(duration, terminal = true)
            assertEquals(ordinaryDuration * 1.2f, terminalDuration, 0.001f)
            // 上升段共享同一条曲线：终端词只是把峰值窗口拉长。
            for (phase in listOf(0.1f, 0.25f, 0.5f)) {
                assertEquals("Terminal timing should stretch the same glow envelope",
                    nativeTtmlGlowStrength(start, end, start + ordinaryDuration * phase),
                    nativeTtmlGlowStrength(start, end, start + terminalDuration * phase, terminal = true),
                    0.00001f)
            }
            assertEquals(0f, nativeTtmlGlowStrength(start, end, end), 0f)
            assertEquals("Terminal emphasis must fall back before the note ends",
                0f, nativeTtmlGlowStrength(start, end, end, terminal = true), 0f)
            assertEquals("Ordinary cutoff sits at 85% of its window",
                0f, nativeTtmlGlowStrength(start, end, start + ordinaryDuration * 0.851f), 0f)
            assertTrue(nativeTtmlGlowStrength(start, end, start + ordinaryDuration * 0.8f) > 0f)
            assertEquals(0f,
                nativeTtmlGlowStrength(start, end, start + terminalDuration * 0.71f, terminal = true), 0f)
            assertTrue(nativeTtmlGlowStrength(start, end,
                start + terminalDuration * 0.6f, terminal = true) > 0f)
        }
    }

    @Test fun glowRisesToOneCrestAndFallsWithoutAPlateau() {
        val start = 1000f
        for (duration in listOf(1000f, 5000f, 20000f)) {
            for (terminal in listOf(false, true)) {
                val span = nativeTtmlEmphasisDurationMs(duration, terminal)
                val fallEnd = if (terminal) 0.7f else 0.85f
                val riseSamples = (0..50).map { frame ->
                    nativeTtmlGlowStrength(start, start + duration, start + span * 0.5f * (frame / 50f), terminal)
                }
                val fallSamples = (1..50).map { frame ->
                    nativeTtmlGlowStrength(start, start + duration,
                        start + span * (0.5f + (fallEnd - 0.5f) * (frame / 50f)), terminal)
                }
                val samples = riseSamples + fallSamples
                assertEquals(0f, samples.first(), 0f)
                assertEquals(1f, riseSamples.last(), 0.00001f)
                assertEquals(0f, fallSamples.last(), 0f)
                assertTrue(samples.all { it in 0f..1f })
                assertTrue("Glow must rise strictly all the way to its crest",
                    riseSamples.zipWithNext().all { it.second > it.first })
                assertTrue("Glow must fall immediately after its crest without a hold",
                    fallSamples.zipWithNext().all { it.second < it.first })
                val peak = start + span * 0.5f
                for (frameMs in listOf(1000f / 60f, 1000f / 120f)) {
                    assertTrue("Glow must still be rising in the frame immediately before its crest",
                        nativeTtmlGlowStrength(start, start + duration, peak - frameMs, terminal) < 1f)
                    assertTrue("Glow must already be falling in the frame immediately after its crest",
                        nativeTtmlGlowStrength(start, start + duration, peak + frameMs, terminal) < 1f)
                }
            }
        }
    }

    @Test fun liftPreparesEarlyAndSettlesTogetherWithTheGlow() {
        val start = 1000f
        val end = 6000f
        val liftStart = start - 400f
        for (terminal in listOf(false, true)) {
            val fallEnd = if (terminal) 0.7f else 0.85f
            val glowEnd = start + nativeTtmlEmphasisDurationMs(end - start, terminal) * fallEnd
            val finish = nativeTtmlEmphasisEndMs(start, end, terminal = terminal)
            assertEquals(0f, nativeTtmlLiftStrength(start, end, liftStart, terminal), 0f)
            assertTrue(nativeTtmlLiftStrength(start, end, start - 200f, terminal) > 0f)
            assertEquals(0f, nativeTtmlGlowStrength(start, end, start - 200f, terminal), 0f)
            assertEquals(0f, nativeTtmlGlowStrength(start, end, glowEnd, terminal), 0f)
            assertEquals("The reported end is the common glow/lift cutoff", glowEnd, finish, 0.01f)
            assertTrue("Lift must still be recovering just before the cutoff",
                nativeTtmlLiftStrength(start, end, glowEnd - 100f, terminal) > 0f)
            assertEquals(0f, nativeTtmlLiftStrength(start, end, glowEnd, terminal), 0f)
            assertTrue(finish < end)
            val samples = (0..100).map { frame ->
                nativeTtmlLiftStrength(start, end, liftStart + (finish - liftStart) * (frame / 100f), terminal)
            }
            assertEquals(1f, samples[50], 0.00001f)
            assertEquals(0f, samples.last(), 0.00001f)
            assertTrue(samples.all { it in 0f..1f })
            assertTrue(samples.take(51).zipWithNext().all { it.second > it.first })
            assertTrue(samples.drop(50).zipWithNext().all { it.second < it.first })
        }
    }

    @Test fun geometryChangesContinuouslyAt60And120Hz() {
        val start = 1000f
        for (duration in listOf(1000f, 5000f, 20000f)) {
            for (terminal in listOf(false, true)) {
                val end = start + duration
                val finish = nativeTtmlEmphasisEndMs(start, end, terminal = terminal)
                for (refreshRate in listOf(60, 120)) {
                    val frameMs = 1000f / refreshRate
                    val firstFrame = start - 600f
                    val frameCount = ((finish + 100f - firstFrame) / frameMs).toInt() + 1
                    val geometry = (0..frameCount).map { frame ->
                        val time = firstFrame + frame * frameMs
                        val glow = nativeTtmlGlowStrength(start, end, time, terminal)
                        val lift = nativeTtmlLiftStrength(start, end, time, terminal)
                        nativeTtmlEmphasisScale(glow, duration, terminal) to nativeTtmlExtraRiseDp(lift)
                    }
                    // 回落包络压缩后最陡帧的帧间变化集中在终端最短音符上，
                    // 仍低于每帧 0.25px 的亚像素界限，预算按新包络校准。
                    val maxScaleChange = 0.007f * 60f / refreshRate
                    val maxRiseChange = 0.07f * 60f / refreshRate
                    assertTrue("Scale must not jump between $refreshRate Hz frames, duration=$duration",
                        geometry.zipWithNext().all { abs(it.second.first - it.first.first) < maxScaleChange })
                    assertTrue("Rise must not jump between $refreshRate Hz frames, duration=$duration",
                        geometry.zipWithNext().all { abs(it.second.second - it.first.second) < maxRiseChange })
                }
            }
        }
    }

    @Test fun shortNotesNeverTriggerEmphasis() {
        val start = 1000f
        for (duration in listOf(-100f, 0f, 500f, 999f)) {
            val end = start + duration
            for (terminal in listOf(false, true)) {
                for (time in 0..6000 step 50) {
                    assertEquals(0f, nativeTtmlGlowStrength(start, end, time.toFloat(), terminal), 0f)
                    assertEquals(0f, nativeTtmlLiftStrength(start, end, time.toFloat(), terminal), 0f)
                }
                assertEquals(1f, nativeTtmlEmphasisScale(1f, duration, terminal), 0f)
                assertEquals(end, nativeTtmlEmphasisEndMs(start, end, glyphCount = 24, terminal = terminal), 0f)
            }
        }
        assertEquals(1f, nativeTtmlEmphasisScale(1f, 999f), 0f)
        for (terminal in listOf(false, true)) {
            val peak = start + nativeTtmlEmphasisDurationMs(1000f, terminal) * 0.5f
            assertEquals("A note at the threshold must still receive emphasis", 1f,
                nativeTtmlGlowStrength(start, start + 1000f, peak, terminal), 0.00001f)
        }
    }

    @Test fun durationControlsSwellAndLongWordsCannotPushTheirNeighboursFar() {
        val short = nativeTtmlEmphasisScale(1f, 1000f)
        val held = nativeTtmlEmphasisScale(1f, 3000f)
        assertTrue(short in 1.03f..1.05f)
        assertTrue(held > short)
        assertTrue(nativeTtmlEmphasisScale(1f, 3000f, true) > held)
        assertTrue(nativeTtmlEmphasisScale(1f, 9000f, true) <= 1.08f)
        assertTrue((nativeTtmlEmphasisScale(1f, 9000f, true, 20f) - 1f) * 20f <= 0.28001f)
        for (terminal in listOf(false, true)) {
            for (widthEm in listOf(1f, 20f)) {
                assertEquals(1f, nativeTtmlEmphasisScale(0f, 9000f, terminal, widthEm), 0f)
                assertEquals(1f, nativeTtmlEmphasisScale(-1f, 9000f, terminal, widthEm), 0f)
                assertEquals(nativeTtmlEmphasisScale(1f, 9000f, terminal, widthEm),
                    nativeTtmlEmphasisScale(2f, 9000f, terminal, widthEm), 0f)
            }
        }
    }

    @Test fun seekingAndReversePlaybackProduceTheSameGeometryAtTheSameTime() {
        val start = 1000f
        val duration = 5000f
        val end = start + duration
        for (count in listOf(1, 24)) {
            for (terminal in listOf(false, true)) {
                val finish = nativeTtmlEmphasisEndMs(start, end, count, terminal)
                val lastDelay = nativeTtmlGlyphDelayMs(count - 1, count, duration, terminal)
                fun geometry(time: Float): List<Float> = (0 until count).flatMap { index ->
                    val localTime = time - nativeTtmlGlyphDelayMs(index, count, duration, terminal)
                    val glow = nativeTtmlGlowStrength(start, end, localTime, terminal)
                    val lift = nativeTtmlLiftStrength(start, end, localTime, terminal)
                    listOf(glow, lift, nativeTtmlEmphasisScale(glow, duration, terminal, count.toFloat()),
                        nativeTtmlExtraRiseDp(lift))
                }
                val seeks = listOf(finish + 1000f, start, start + duration * 0.5f + lastDelay,
                    0f, finish, start - 200f, end, start + 4000f, finish - 1f)
                val times = ((0..(finish + 1000f).toInt() step 50).map(Int::toFloat) + seeks).distinct().sorted()
                val forward = times.associateWith(::geometry)
                val reverse = times.asReversed().associateWith(::geometry)
                assertEquals("Playback direction must not change any glyph's geometry", forward, reverse)
                for (time in seeks + seeks.asReversed() + seeks) {
                    assertEquals("Seeking to $time must exactly reproduce the same geometry",
                        forward.getValue(time), geometry(time))
                }
                val baseline = List(count) { listOf(0f, 0f, 1f, 0f) }.flatten()
                assertEquals(baseline, forward.getValue(times.first()))
                assertEquals(baseline, forward.getValue(times.last()))
            }
        }
    }

    @Test fun glyphWaveIsOrderedAndBoundedEvenForMalformedLongTokens() {
        for (terminal in listOf(false, true)) {
            val span = nativeTtmlEmphasisDurationMs(5000f, terminal)
            for (count in listOf(1, 3, 7, 1000)) {
                val delays = (0 until count).map { nativeTtmlGlyphDelayMs(it, count, 5000f, terminal) }
                assertEquals(0f, delays.first(), 0f)
                assertTrue(delays.zipWithNext().all { it.second > it.first })
                assertTrue("Even a huge token must finish moving its crest before the note ends",
                    delays.last() + span * 0.5f < span)
                assertEquals(0f, nativeTtmlGlyphDelayMs(-10, count, 5000f, terminal), 0f)
                assertEquals(delays.last(), nativeTtmlGlyphDelayMs(count + 10, count, 5000f, terminal), 0f)
            }
            for (count in listOf(-7, 0, 1)) {
                for (index in listOf(-10, 0, 10)) {
                    assertEquals(0f, nativeTtmlGlyphDelayMs(index, count, 5000f, terminal), 0f)
                }
                assertEquals(nativeTtmlGlyphStepMs(5000f, 1, terminal),
                    nativeTtmlGlyphStepMs(5000f, count, terminal), 0f)
            }
        }
    }

    @Test fun neighbouringGlyphDelaysScaleWithDurationAndTerminalExtension() {
        for (count in listOf(2, 7, 24)) {
            val shortStep = nativeTtmlGlyphStepMs(2000f, count)
            val heldStep = nativeTtmlGlyphStepMs(6000f, count)
            assertTrue(shortStep > 0f)
            assertEquals("Neighbour timing must follow the held duration instead of a fixed millisecond cap",
                shortStep * 3f, heldStep, 0.001f)
            assertEquals(heldStep * 1.2f, nativeTtmlGlyphStepMs(6000f, count, terminal = true), 0.001f)
            for (index in 0 until count) {
                val shortDelay = nativeTtmlGlyphDelayMs(index, count, 2000f)
                val heldDelay = nativeTtmlGlyphDelayMs(index, count, 6000f)
                assertEquals(shortDelay, nativeTtmlGlyphDelayMs(index, count), 0f)
                assertEquals(shortDelay * 3f, heldDelay, 0.001f)
                assertEquals(heldDelay * 1.2f,
                    nativeTtmlGlyphDelayMs(index, count, 6000f, terminal = true), 0.001f)
            }
        }
    }

    @Test fun neighbouringGlyphsOverlapWithOneFallingWhileTheOtherRises() {
        val start = 1000f
        for (duration in listOf(1000f, 5000f)) {
            for (count in listOf(2, 7, 24)) {
                for (terminal in listOf(false, true)) {
                    val span = nativeTtmlEmphasisDurationMs(duration, terminal)
                    val step = nativeTtmlGlyphStepMs(duration, count, terminal)
                    for (index in 1 until count) {
                        val previousDelay = nativeTtmlGlyphDelayMs(index - 1, count, duration, terminal)
                        val nextDelay = nativeTtmlGlyphDelayMs(index, count, duration, terminal)
                        val handoff = start + span * 0.5f + (previousDelay + nextDelay) * 0.5f
                        fun previous(time: Float) = nativeTtmlGlowStrength(start, start + duration,
                            time - previousDelay, terminal)
                        fun next(time: Float) = nativeTtmlGlowStrength(start, start + duration,
                            time - nextDelay, terminal)
                        assertTrue("Both adjacent glyphs must visibly join the moving crest",
                            previous(handoff) > 0.25f)
                        assertTrue(next(handoff) > 0.5f)
                        assertTrue(previous(handoff) < 1f && next(handoff) < 1f)
                        val delta = step * 0.25f
                        assertTrue(previous(handoff + delta) < previous(handoff - delta))
                        assertTrue(next(handoff + delta) > next(handoff - delta))
                    }
                }
            }
        }
    }

    @Test fun longWordsKeepTheirFinalCrestAndFallUntilTheReportedEnd() {
        val start = 1000f
        for (duration in listOf(1000f, 5000f, 20000f)) {
            val end = start + duration
            for (count in listOf(1, 7, 24, 1000)) {
                for (terminal in listOf(false, true)) {
                    val span = nativeTtmlEmphasisDurationMs(duration, terminal)
                    val lastDelay = nativeTtmlGlyphDelayMs(count - 1, count, duration, terminal)
                    val lastPeak = start + span * 0.5f + lastDelay
                    val finish = nativeTtmlEmphasisEndMs(start, end, count, terminal)
                    val singleFinish = nativeTtmlEmphasisEndMs(start, end, terminal = terminal)
                    assertEquals(1f, nativeTtmlGlowStrength(start, end, lastPeak - lastDelay, terminal), 0.00001f)
                    assertEquals("Word lifetime must include the final glyph's entire envelope",
                        singleFinish + lastDelay, finish, 0.005f)
                    assertTrue("The last glyph's lift must not be clipped before the reported end",
                        nativeTtmlLiftStrength(start, end, finish - 100f - lastDelay, terminal) > 0f)
                    for (time in listOf(finish, finish + 1f, finish + 1000f)) {
                        for (index in 0 until count) {
                            val localTime = time - nativeTtmlGlyphDelayMs(index, count, duration, terminal)
                            val glow = nativeTtmlGlowStrength(start, end, localTime, terminal)
                            val lift = nativeTtmlLiftStrength(start, end, localTime, terminal)
                            assertEquals("No glyph may retain glow after the word's emphasis ends", 0f, glow, 0.00001f)
                            assertEquals("No glyph may retain lift after the word's emphasis ends", 0f, lift, 0.00001f)
                            assertEquals(1f, nativeTtmlEmphasisScale(glow, duration, terminal), 0.00001f)
                            assertEquals(0f, nativeTtmlExtraRiseDp(lift), 0.00001f)
                        }
                    }
                }
            }
        }
    }

    @Test fun glowAndLiftLeaveNoStaleEmphasisAfterASeekOrNoteEnd() {
        val start = 1000f
        for (duration in listOf(1000f, 2000f, 5000f)) {
            val end = start + duration
            for (terminal in listOf(false, true)) {
                val finish = nativeTtmlEmphasisEndMs(start, end, terminal = terminal)
                for (time in listOf(-1000f, 0f, start - 401f, finish, finish + 1f, finish + 10000f)) {
                    val glow = nativeTtmlGlowStrength(start, end, time, terminal)
                    val lift = nativeTtmlLiftStrength(start, end, time, terminal)
                    assertEquals(0f, glow, 0.00001f)
                    assertEquals(0f, lift, 0.00001f)
                    assertEquals(1f, nativeTtmlEmphasisScale(glow, duration, terminal), 0.00001f)
                    assertEquals(0f, nativeTtmlExtraRiseDp(lift), 0.00001f)
                }
            }
        }
    }

    @Test fun extraRiseKeepsThePreviouslyReducedAmplitude() {
        assertEquals(0f, nativeTtmlExtraRiseDp(0f), 0f)
        assertEquals(1.5f, nativeTtmlExtraRiseDp(1f), 0f)
        assertEquals(0.75f, nativeTtmlExtraRiseDp(0.5f), 0f)
        assertEquals(0f, nativeTtmlExtraRiseDp(-1f), 0f)
        assertEquals(1.5f, nativeTtmlExtraRiseDp(2f), 0f)
    }
}
