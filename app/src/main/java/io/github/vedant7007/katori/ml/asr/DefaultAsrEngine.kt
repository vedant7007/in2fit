package io.github.vedant7007.katori.ml.asr

import io.github.vedant7007.katori.domain.ModelArbiter
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [AsrEngine] over whatever [AsrDecoder] the arbiter hands out. Today that is IndicConformer in
 * sherpa-onnx ([SherpaOnnxAsrLoader]); the model family is a loader detail, as the contract asks.
 *
 * NOTHING HERE HAS RUN ON A PHONE. The event sequence, the endpointing and the judgement of a
 * decoder's output are unit tested against a scripted decoder and synthetic frames. The model
 * itself was smoke-tested on the desktop (`0021`); its speed and memory on the device are not
 * known and no claim about them is made in this file.
 *
 * ### Why one model per selected language, and not one model for everything
 *
 * `0022`, decided by Vedant on 20 Sep 2026. The user's profile language selects the checkpoint;
 * words from another language arrive transliterated into that language's script, and the food
 * matcher carries those renderings. No IndicConformer emits two scripts in one utterance, and the
 * one engine that does (Omnilingual, `0022` §3) chose the wrong Indic script on most mixed clips.
 * Reopened only if recorded speakers show this failing on natural code-mixed speech.
 *
 * ### Where the lease sits
 *
 * [listen] records first and only then asks the arbiter for the model. A lease may not span
 * waiting for a person (`ModelArbiter`), and a recording is exactly that wait. The cost is a
 * model load after the utterance if nothing has warmed it, which is what [prepare] is for and
 * what the orchestrator's round-trip lease already does when it holds ASR, LLM and TTS together.
 *
 * ### What HIGH and LOW mean here, honestly
 *
 * sherpa-onnx's Kotlin API returns text and pieces and no acoustic score, so the band cannot
 * come from the model's own certainty. It comes from two things that were MEASURED on the
 * desktop (`0021`) rather than assumed:
 *
 *  - The CTC model emits one or two phantom pieces on silence and on quiet noise: 3 s of digital
 *    silence decoded to two pieces, noise to one. Real speech decodes to 8-10 pieces a second in
 *    Telugu and 4-9 in English. So a clip whose piece density is under [MIN_TOKENS_PER_SECOND]
 *    is not speech and is refused as INPUT_NOT_USABLE. An empty-string check alone would have
 *    let 'ఈారు' through as a meal.
 *  - A piece the model could not place is `<unk>`. Any `<unk>` in the output is LOW.
 *
 * Everything else is HIGH, and HIGH means "dense, in-vocabulary output", not "the model is sure".
 * At 0 dB SNR the desktop test got two of six words wrong at full density; the band did not see
 * it, and the food matcher downstream is what turns a wrong word into an honest NO_MATCH rather
 * than a wrong number. The recorded meal logs are what will tell whether a better signal is
 * needed, and if one is, it goes here and nowhere else.
 */
class DefaultAsrEngine(
    private val arbiter: ModelArbiter,
    private val audio: AudioSource,
    private val newEndpointer: () -> EnergyEndpointer = { EnergyEndpointer() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AsrEngine {

    override val supportedLanguages: Set<SpeechLanguage> = SpeechLanguage.entries.toSet()

    private val capturing = AtomicBoolean(false)

    override suspend fun prepare(language: SpeechLanguage): Outcome<Unit> =
        arbiter.preload(AsrModels.handleFor(language))

    override fun listen(language: SpeechLanguage): Flow<AsrEvent> = flow {
        if (!audio.canRecord()) {
            emit(unavailable(UnavailableReason.PERMISSION_DENIED, "RECORD_AUDIO is not granted"))
            return@flow
        }
        // A second listener would interleave two captures on one microphone. It fails instead.
        if (!capturing.compareAndSet(false, true)) {
            emit(unavailable(UnavailableReason.INTERNAL_ERROR, "a capture is already running"))
            return@flow
        }
        try {
            val clip = when (val captured = capture()) {
                is Captured.Clip -> captured.clip
                is Captured.Failed -> { emit(AsrEvent.Unavailable(captured.outcome)); return@flow }
            }
            emit(AsrEvent.Transcribing)
            when (val result = transcribe(clip, language)) {
                is Outcome.Ok -> emit(AsrEvent.Result(result.value))
                is Outcome.Unavailable -> emit(AsrEvent.Unavailable(result))
                // AsrEvent has no NotImplemented variant and this engine never produces one; if the
                // arbiter ever did, the component name is the diagnostic.
                is Outcome.NotImplemented -> emit(unavailable(UnavailableReason.INTERNAL_ERROR, "not implemented: ${result.component}"))
            }
        } finally {
            capturing.set(false)
        }
    }

    override suspend fun transcribe(clip: AudioClip, language: SpeechLanguage): Outcome<Transcript> {
        val durationMs = clip.samples.size * 1000L / clip.sampleRateHz
        if (durationMs < MIN_CLIP_MS) {
            return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "clip is $durationMs ms, under $MIN_CLIP_MS")
        }
        val handle = AsrModels.handleFor(language)
        val decoded = arbiter.withModel(handle) { loaded ->
            val decoder = loaded.native as? AsrDecoder
                ?: error("${handle.id} was loaded as ${loaded.native::class.java.name}, not an AsrDecoder")
            withContext(dispatcher) { decoder.decode(clip.samples, clip.sampleRateHz) }
        }
        return when (decoded) {
            is Outcome.Ok -> judge(decoded.value, language, durationMs)
            is Outcome.Unavailable -> decoded
            is Outcome.NotImplemented -> decoded
        }
    }

    /** Turns what the model emitted into a transcript, or refuses it. See the class comment. */
    private fun judge(decoded: AsrDecoder.Decoded, language: SpeechLanguage, durationMs: Long): Outcome<Transcript> {
        val text = decoded.text.trim()
        val density = decoded.tokens.size * 1000f / durationMs
        if (text.isEmpty() || density < MIN_TOKENS_PER_SECOND) {
            return Outcome.Unavailable(
                UnavailableReason.INPUT_NOT_USABLE,
                "${decoded.tokens.size} pieces in $durationMs ms (${"%.1f".format(density)}/s) is not speech",
            )
        }
        val band = if (decoded.tokens.any { it == UNKNOWN_PIECE }) AsrConfidence.LOW else AsrConfidence.HIGH
        return Outcome.Ok(Transcript(text, language, band, durationMs))
    }

    // --- capture -----------------------------------------------------------------------------

    private sealed interface Captured {
        class Clip(val clip: AudioClip) : Captured
        class Failed(val outcome: Outcome.Unavailable) : Captured
    }

    private class Heard(val samples: ShortArray, val level: Float, val step: EnergyEndpointer.Step)

    /**
     * Record until the endpointer closes the utterance, emitting the meter level and the
     * start/end events as they happen. Returns the clip from [PRE_ROLL_MS] before onset to the
     * end of the hangover, so a leading consonant is not cut and a trailing one is not either.
     */
    private suspend fun FlowCollector<AsrEvent>.capture(): Captured {
        val endpointer = newEndpointer()
        val frames = ArrayList<ShortArray>()
        try {
            audio.frames(SAMPLE_RATE_HZ, endpointer.frameMs)
                .map { frame -> val level = EnergyEndpointer.rms(frame); Heard(frame, level, endpointer.feed(level)) }
                .transformWhile { heard -> emit(heard); heard.step != EnergyEndpointer.Step.ENDED && heard.step != EnergyEndpointer.Step.GAVE_UP }
                .collect { heard ->
                    frames += heard.samples
                    emit(AsrEvent.Level(heard.level))
                    when (heard.step) {
                        EnergyEndpointer.Step.STARTED -> emit(AsrEvent.SpeechStarted)
                        EnergyEndpointer.Step.ENDED -> emit(AsrEvent.SpeechEnded)
                        else -> Unit
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The microphone failed underneath us. Not a transcript, not a crash: a reason.
            return Captured.Failed(Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "capture failed: ${e.message}"))
        }

        if (endpointer.onsetFrame < 0) {
            return Captured.Failed(Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "no speech within ${endpointer.maxWaitMs} ms"))
        }
        if (endpointer.speechMs < MIN_SPEECH_MS) {
            return Captured.Failed(Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "speech lasted ${endpointer.speechMs} ms, under $MIN_SPEECH_MS"))
        }
        val from = maxOf(0, endpointer.onsetFrame - PRE_ROLL_MS / endpointer.frameMs)
        val kept = frames.subList(from, frames.size)
        val samples = ShortArray(kept.sumOf { it.size })
        var at = 0
        for (f in kept) { f.copyInto(samples, at); at += f.size }
        return Captured.Clip(AudioClip(samples, SAMPLE_RATE_HZ))
    }

    private fun unavailable(reason: UnavailableReason, detail: String) =
        AsrEvent.Unavailable(Outcome.Unavailable(reason, detail))

    companion object {
        /** What the models were trained on and what [AudioClip] documents as the default. */
        const val SAMPLE_RATE_HZ = 16_000

        /** Audio kept before the detected onset. */
        const val PRE_ROLL_MS = 200

        /** Speech shorter than this is a cough. A knob; the recorded set tunes it. */
        const val MIN_SPEECH_MS = 300

        /** A clip too short to carry a feature frame is refused before the model is asked. */
        const val MIN_CLIP_MS = 200

        /**
         * Below this many pieces per second of clip, the model heard nothing. Desktop, `0021`:
         * silence and noise gave 0.0-0.7/s; speech gave 4-10/s. A knob, from synthetic data.
         */
        const val MIN_TOKENS_PER_SECOND = 2.0f

        const val UNKNOWN_PIECE = "<unk>"
    }
}
