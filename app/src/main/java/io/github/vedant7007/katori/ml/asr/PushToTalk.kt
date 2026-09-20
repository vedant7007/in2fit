package io.github.vedant7007.katori.ml.asr

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Press, speak, release. The clip between press and release goes to [AsrEngine.transcribe]; no
 * endpointer decides where the sentence ends, because the thumb already did.
 *
 * RULED for the voice beats by Vedant, 20 Sep 2026 (`0031`, "The crowded hall"). Open listening
 * was measured at 8 of the ten demo sentences in babble the recogniser is comfortable in, and
 * 0 of ten the moment one laugh landed 300 ms after the sentence: the energy endpointer waits for
 * 700 ms of quiet, a laugh is not quiet, and the laugh went to the recogniser with the sentence.
 * With the clip cut by the button the "cut right" column applies, which held 8, 8, 8, 7 of ten down
 * to a voice only 10 dB above the room. Nobody controls when the next table laughs.
 *
 * SAME EVENTS AS [AsrEngine.listen], so a screen that handles that flow handles this one and only
 * the gesture changes: [AsrEvent.SpeechStarted] on press, [AsrEvent.Level] per frame while held,
 * [AsrEvent.SpeechEnded] on release, [AsrEvent.Transcribing], then exactly one [AsrEvent.Result]
 * or [AsrEvent.Unavailable]. A hold shorter than [MIN_HOLD_MS] is a tap, not a sentence, and is
 * INPUT_NOT_USABLE exactly as a cough is on the open-listening path. A hold longer than
 * [MAX_HOLD_MS] is cut there and transcribed anyway; a meal log is not dictation.
 *
 * THE RELEASE TAIL. Recording continues for [RELEASE_TAIL_MS] after the thumb lifts. Measured
 * (`logs/asr-tail-after-last-word.log`, hi checkpoint, ten demo sentences, clean and in +15 dB
 * babble): a cut on the last syllable scores the full 8 of ten, and so does any cut up to a
 * second late; a cut 100 ms INSIDE the last word scores 8 and 7, 200 ms inside 6 and 5, 300 ms
 * inside 0 and 0. So a late thumb costs nothing and an early one costs the sentence, and 300 ms
 * of tail turns a thumb that lifts on the last word into one that lifted after it. The presenter's
 * instruction is "finish the word, then let go"; the tail is for the times he does not.
 *
 * WHY RELEASE IS A CALL AND NOT A CANCELLATION. Cancelling the collector is what the screen does
 * when the person navigates away: the microphone is released and nothing is transcribed. Letting
 * go of the button must do the opposite, keep the clip and transcribe it, so it is a method the
 * screen calls from the touch handler, and the flow sees it within one frame.
 *
 * This class does not touch the frozen `AsrEngine` interface and loads nothing: the model comes
 * through the engine's `transcribe`, which leases it from the arbiter.
 */
class PushToTalk(
    private val engine: AsrEngine,
    private val audio: AudioSource,
) {
    private val held = MutableStateFlow(false)
    private val active = AtomicBoolean(false)

    /** Let go. Safe to call at any time, including before or after a hold; only a live hold notices. */
    fun release() {
        held.value = false
    }

    /**
     * The press. Returns the flow to collect for the duration of the gesture; cancelling the
     * collector abandons the clip and frees the microphone.
     *
     * The held flag is raised HERE, at the press, not when collection starts, so a thumb that lets
     * go before the coroutine has begun collecting is seen as the tap it was and not held for
     * [MAX_HOLD_MS] because its release arrived first and was overwritten.
     */
    fun hold(language: SpeechLanguage): Flow<AsrEvent> {
        held.value = true
        return holdFlow(language)
    }

    private fun holdFlow(language: SpeechLanguage): Flow<AsrEvent> = flow {
        var tailLeftMs = RELEASE_TAIL_MS
        if (!audio.canRecord()) {
            emit(unavailable(UnavailableReason.PERMISSION_DENIED, "RECORD_AUDIO is not granted"))
            return@flow
        }
        if (!active.compareAndSet(false, true)) {
            emit(unavailable(UnavailableReason.INTERNAL_ERROR, "a hold is already in progress"))
            return@flow
        }
        try {
            emit(AsrEvent.SpeechStarted)
            val frames = ArrayList<ShortArray>()
            var heldMs = 0
            try {
                audio.frames(DefaultAsrEngine.SAMPLE_RATE_HZ, FRAME_MS)
                    // While held, up to the cap; after release, the tail. heldMs counts only the hold,
                    // so a tap plus its tail is still a tap.
                    .takeWhile { (held.value && heldMs < MAX_HOLD_MS) || tailLeftMs > 0 }
                    .collect { frame ->
                        frames += frame
                        if (held.value && heldMs < MAX_HOLD_MS) heldMs += FRAME_MS else tailLeftMs -= FRAME_MS
                        emit(AsrEvent.Level(EnergyEndpointer.rms(frame)))
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(unavailable(UnavailableReason.INTERNAL_ERROR, "capture failed: ${e.message}"))
                return@flow
            }
            emit(AsrEvent.SpeechEnded)
            if (heldMs < MIN_HOLD_MS) {
                emit(unavailable(UnavailableReason.INPUT_NOT_USABLE, "held for $heldMs ms, under $MIN_HOLD_MS: a tap, not a sentence"))
                return@flow
            }
            val samples = ShortArray(frames.sumOf { it.size })
            var at = 0
            for (f in frames) { f.copyInto(samples, at); at += f.size }
            emit(AsrEvent.Transcribing)
            when (val result = engine.transcribe(AudioClip(samples, DefaultAsrEngine.SAMPLE_RATE_HZ), language)) {
                is Outcome.Ok -> emit(AsrEvent.Result(result.value))
                is Outcome.Unavailable -> emit(AsrEvent.Unavailable(result))
                is Outcome.NotImplemented -> emit(unavailable(UnavailableReason.INTERNAL_ERROR, "not implemented: ${result.component}"))
            }
        } finally {
            held.value = false
            active.set(false)
        }
    }

    private fun unavailable(reason: UnavailableReason, detail: String) =
        AsrEvent.Unavailable(Outcome.Unavailable(reason, detail))

    companion object {
        /** Frame size, and therefore how long after [release] the flow notices: one frame. */
        const val FRAME_MS = 20

        /** Under this the button was tapped, not held through a sentence. Same figure as the open-listening minimum. */
        const val MIN_HOLD_MS = DefaultAsrEngine.MIN_SPEECH_MS

        /**
         * A thumb that never lets go still gets a transcript of this much and no more. Thirty, not
         * twenty: joining the presenter's own recordings past a 20 s cap cut foods off the END of the
         * utterance and they were counted as missed when they had simply not been recorded
         * (`0031`, "Insertions"); a sentence to a judge is never thirty seconds, so the cap cannot
         * reach a real sentence, and only a stuck thumb finds it.
         */
        const val MAX_HOLD_MS = 30_000

        /** Recorded after the thumb lifts. Covers a thumb up to this early on the last word; see the class comment. */
        const val RELEASE_TAIL_MS = 300
    }
}
