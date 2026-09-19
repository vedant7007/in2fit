package io.github.vedant7007.katori.ml.asr

import io.github.vedant7007.katori.ml.asr.EnergyEndpointer.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The endpointer is a state machine over frame levels, and every threshold in it is a knob. These
 * tests pin the machine's shape: what starts an utterance, what ends it, what never counts. They
 * say nothing about whether the default knobs suit a real room; the recorded set does that.
 */
class EnergyEndpointerTest {

    // 20 ms frames, so 50 frames a second.
    private fun ep() = EnergyEndpointer(
        frameMs = 20, calibrationMs = 200, onsetMs = 60, hangoverMs = 400,
        maxWaitMs = 4_000, maxUtteranceMs = 3_000, thresholdOverFloor = 3f, floorMin = 0.004f,
    )

    private fun EnergyEndpointer.run(levels: List<Float>): List<Step> = levels.map { feed(it) }

    private fun frames(n: Int, level: Float) = List(n) { level }

    @Test
    fun `silence alone gives up at maxWait and never starts`() {
        val e = ep()
        val steps = e.run(frames(250, 0.001f))
        assertTrue(Step.STARTED !in steps)
        assertEquals(Step.GAVE_UP, steps.last())
        // 4,000 ms at 20 ms a frame is frame 200; nothing after it changes.
        assertEquals(Step.GAVE_UP, steps[199])
        assertEquals(Step.WAITING, steps[198])
        assertEquals(-1, e.onsetFrame)
    }

    @Test
    fun `a single loud frame is a click, not an onset`() {
        val e = ep()
        val steps = e.run(frames(20, 0.001f) + listOf(0.5f) + frames(20, 0.001f))
        assertTrue(Step.STARTED !in steps)
        assertTrue(steps.all { it == Step.WAITING })
    }

    @Test
    fun `speech starts after onsetMs above threshold and the onset frame is the first loud one`() {
        val e = ep()
        val quiet = frames(20, 0.001f)          // 400 ms of room
        val steps = e.run(quiet + frames(3, 0.2f))
        // Three loud frames = 60 ms = onsetMs, so the third one starts it.
        assertEquals(Step.STARTED, steps.last())
        assertEquals(Step.WAITING, steps[steps.size - 2])
        assertEquals(20, e.onsetFrame)
    }

    @Test
    fun `speech ends after hangoverMs of silence and speechMs excludes the hangover`() {
        val e = ep()
        val steps = e.run(frames(20, 0.001f) + frames(50, 0.2f) + frames(20, 0.001f))
        // 50 loud frames = 1,000 ms of speech, then 20 quiet frames = 400 ms = hangover.
        assertEquals(Step.ENDED, steps.last())
        assertEquals(Step.SPEAKING, steps[steps.size - 2])
        assertEquals(1_000, e.speechMs)
        // Terminal: more frames change nothing.
        assertEquals(Step.ENDED, e.feed(0.9f))
    }

    @Test
    fun `a short pause inside the utterance does not end it`() {
        val e = ep()
        val steps = e.run(frames(20, 0.001f) + frames(25, 0.2f) + frames(15, 0.001f) + frames(25, 0.2f) + frames(20, 0.001f))
        // 300 ms pause < 400 ms hangover: still one utterance, 500 + 300 + 500 = 1,300 ms long.
        assertEquals(1, steps.count { it == Step.STARTED })
        assertEquals(Step.ENDED, steps.last())
        assertEquals(1_300, e.speechMs)
    }

    @Test
    fun `nothing starts during calibration however loud`() {
        val e = ep()
        // calibrationMs = 200 = 10 frames. Loud from the first frame: the floor learns the noise
        // and the threshold climbs with it, so this reads as a loud room, not as speech.
        val steps = e.run(frames(10, 0.3f))
        assertTrue(Step.STARTED !in steps)
    }

    @Test
    fun `the floor adapts to steady noise so it is not speech, and real speech above it is`() {
        val e = ep()
        val noise = frames(50, 0.03f)            // a fan, 1,000 ms
        var steps = e.run(noise)
        assertTrue("steady noise must not start an utterance", Step.STARTED !in steps)
        assertTrue("threshold has risen above the noise", e.threshold > 0.03f)
        steps = e.run(frames(3, 0.3f))           // ten times the fan
        assertEquals(Step.STARTED, steps.last())
    }

    @Test
    fun `an utterance is forced closed at maxUtteranceMs`() {
        val e = ep()
        val steps = e.run(frames(20, 0.001f) + frames(200, 0.2f))
        // 3,000 ms = 150 loud frames after onset closes it; frames after that change nothing.
        assertEquals(Step.ENDED, steps.last())
        val ended = steps.indexOf(Step.ENDED)
        assertEquals(20 + 150 - 1, ended)
        assertEquals(3_000, e.speechMs)
    }

    @Test
    fun `rms is on a full scale of one`() {
        assertEquals(0f, EnergyEndpointer.rms(ShortArray(0)), 0f)
        assertEquals(0f, EnergyEndpointer.rms(ShortArray(320)), 0f)
        val full = ShortArray(320) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        assertEquals(1f, EnergyEndpointer.rms(full), 0.001f)
    }
}
