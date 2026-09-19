package io.github.vedant7007.katori.ml.asr

import kotlin.math.sqrt

/**
 * Voice-activity detection by signal energy: speech starts when the level stays above an adaptive
 * threshold, and ends when it stays below it for [hangoverMs]. Pure, one frame at a time.
 *
 * WHY ENERGY AND NOT A NEURAL VAD. sherpa-onnx ships Silero VAD, but that is another model file
 * to stage, admit through the arbiter and account for in `0013`'s memory table, for a demo that
 * is one person speaking a three to six second sentence into a phone held to their face. The
 * contract asks that the utterance end on silence; this does that in forty lines with no file.
 * ponytail: energy VAD, false endpoints in a loud room are the known ceiling; Silero through the
 * arbiter is the upgrade if the recorded set shows clips being cut.
 *
 * EVERY NUMBER HERE IS A KNOB, NOT A MEASUREMENT. The defaults are what makes a first run
 * plausible; a real microphone in a real room has a floor and a gain nobody has measured yet, and
 * the recorded meal logs are what will tune them. Nothing here has run on a phone.
 *
 * The noise floor adapts while waiting (a slow average of frames below threshold), so a fan
 * that is on when the app opens does not read as speech. It does not adapt during speech.
 *
 * @param frameMs how much audio each [feed] represents.
 * @param calibrationMs no onset is accepted before this much audio has been heard; the floor
 *   needs a few frames before the threshold means anything.
 * @param onsetMs the level must stay above threshold this long before speech is declared, so a
 *   door slam does not start a recording.
 * @param hangoverMs the level must stay below threshold this long before the utterance ends.
 *   Long enough to survive the pause between "two rotis" and "and pappu".
 * @param maxWaitMs give up if nobody has started speaking by this point.
 * @param maxUtteranceMs force the end of an utterance this long; a meal log is not dictation.
 * @param thresholdOverFloor speech is a level this many times the noise floor.
 * @param floorMin the floor is never taken below this, so a very quiet room does not make every
 *   breath an onset. Levels are RMS on a full-scale of 1.0.
 */
class EnergyEndpointer(
    val frameMs: Int = 20,
    val calibrationMs: Int = 300,
    val onsetMs: Int = 60,
    val hangoverMs: Int = 700,
    val maxWaitMs: Int = 8_000,
    val maxUtteranceMs: Int = 15_000,
    val thresholdOverFloor: Float = 3.0f,
    val floorMin: Float = 0.004f,
) {
    enum class Step {
        /** No speech yet. */
        WAITING,
        /** This frame crossed the onset. Emitted once. */
        STARTED,
        /** Inside the utterance. */
        SPEAKING,
        /** This frame closed the utterance. Terminal. */
        ENDED,
        /** [maxWaitMs] passed with no speech. Terminal. */
        GAVE_UP,
    }

    private var elapsedMs = 0
    private var floor = -1f
    private var aboveMs = 0
    private var belowMs = 0
    private var step = Step.WAITING

    /** Index of the first frame of speech, for the caller's pre-roll cut. -1 until [Step.STARTED]. */
    var onsetFrame: Int = -1
        private set

    /** Speech from onset to the frame that closed it, hangover excluded. 0 until [Step.ENDED]. */
    var speechMs: Int = 0
        private set

    /** The level a frame must reach to count as speech, given what has been heard so far. */
    val threshold: Float get() = maxOf(floorMin, floor) * thresholdOverFloor

    fun feed(rms: Float): Step {
        if (step == Step.ENDED || step == Step.GAVE_UP) return step
        val frameIndex = elapsedMs / frameMs
        elapsedMs += frameMs
        if (floor < 0f) floor = rms

        return when (step) {
            Step.WAITING -> {
                if (rms > threshold && elapsedMs > calibrationMs) {
                    aboveMs += frameMs
                    if (aboveMs >= onsetMs) {
                        onsetFrame = frameIndex - (aboveMs / frameMs) + 1
                        step = Step.SPEAKING
                        return Step.STARTED
                    }
                } else {
                    aboveMs = 0
                    floor += (rms - floor) * FLOOR_ADAPTATION
                }
                if (elapsedMs >= maxWaitMs) step = Step.GAVE_UP
                step
            }
            Step.SPEAKING -> {
                if (rms > threshold) belowMs = 0 else belowMs += frameMs
                val spoken = elapsedMs - onsetFrame * frameMs
                if (belowMs >= hangoverMs || spoken >= maxUtteranceMs) {
                    speechMs = spoken - belowMs
                    step = Step.ENDED
                }
                step
            }
            else -> step
        }
    }

    companion object {
        /** Per-frame weight of the running noise floor. 0.05 at 20 ms frames is about a 400 ms window. */
        const val FLOOR_ADAPTATION = 0.05f

        /** RMS of a 16-bit PCM frame on a full scale of 1.0. The level the recording meter shows. */
        fun rms(frame: ShortArray): Float {
            if (frame.isEmpty()) return 0f
            var sum = 0.0
            for (s in frame) { val v = s / 32_768.0; sum += v * v }
            return sqrt(sum / frame.size).toFloat()
        }
    }
}
