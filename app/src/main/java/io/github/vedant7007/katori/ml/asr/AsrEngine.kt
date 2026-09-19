package io.github.vedant7007.katori.ml.asr

import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Speech to text, fully offline.
 *
 * DESIGN RULINGS BAKED INTO THIS CONTRACT
 * - WHOLE-UTTERANCE, NOT STREAMING. Whisper is a non-streaming encoder-decoder over padded
 *   30-second windows and sherpa-onnx exposes it as offline only, so partial transcripts are not
 *   available. There is deliberately NO `Partial` variant in [AsrEvent]. Adding one is an interface
 *   change and goes to the integrator, not a local edit (spec 10.2 ruling).
 * - Perceived responsiveness comes from [AsrEvent.Level] during speech and an immediate
 *   [AsrEvent.SpeechEnded] at endpoint. On a three to six second meal utterance that reads as live.
 * - LANGUAGE IS A PARAMETER, NEVER DETECTED. The user picks their language and can switch it. There
 *   is no language-identification subsystem in this app, and this interface has no API that would
 *   let one appear by accident.
 * - PLUGGABLE BY CONSTRUCTION. Which model family wins on real code-mixed speech is an open question
 *   to be settled by word error rate against the recorded test set, not by this document. Every
 *   implementation detail, model family included, sits behind this interface so the swap is a
 *   configuration change.
 *
 * CONTRACT
 * - [listen] performs voice-activity detection, ends the utterance on silence, transcribes the whole
 *   clip, and emits exactly one [AsrEvent.Result] or one [AsrEvent.Unavailable] before completing.
 * - The engine never fabricates a transcript. Silence, noise or an unintelligible clip produce
 *   [AsrEvent.Unavailable] with INPUT_NOT_USABLE, not an empty or invented string.
 * - Confidence is reported as a band, not a number, so no percentage can leak into a health figure.
 * - Models are obtained through the ModelArbiter. This engine never loads a file itself and never
 *   holds a model reference beyond an inference.
 *
 * FAILURE MODES
 * - Microphone permission missing: PERMISSION_DENIED before any recording starts.
 * - Clip shorter than the minimum, or pure silence: INPUT_NOT_USABLE.
 * - Model not admitted: MODEL_NOT_LOADED or INSUFFICIENT_MEMORY, propagated from the arbiter.
 * - Another capture already running: the second call fails rather than interleaving audio.
 */
interface AsrEngine {

    val supportedLanguages: Set<SpeechLanguage>

    /** Admit and warm the model for [language] ahead of use. Safe to call repeatedly. */
    suspend fun prepare(language: SpeechLanguage): Outcome<Unit>

    /**
     * Record with VAD endpointing and transcribe. Cancelling the collector stops the capture and
     * releases the microphone.
     */
    fun listen(language: SpeechLanguage): Flow<AsrEvent>

    /** Transcribe an already-captured clip. Used by the offline accuracy harness (spec 18.3). */
    suspend fun transcribe(clip: AudioClip, language: SpeechLanguage): Outcome<Transcript>
}

sealed interface AsrEvent {
    /** Microphone level for the recording meter. Emitted while speech is being captured. */
    data class Level(val rms: Float) : AsrEvent
    data object SpeechStarted : AsrEvent
    /** VAD detected end of utterance. The UI acknowledges here, before transcription finishes. */
    data object SpeechEnded : AsrEvent
    data object Transcribing : AsrEvent
    data class Result(val transcript: Transcript) : AsrEvent
    data class Unavailable(val outcome: Outcome.Unavailable) : AsrEvent
}

data class Transcript(
    val text: String,
    val language: SpeechLanguage,
    val confidence: AsrConfidence,
    val audioDurationMs: Long,
)

/**
 * Deliberately two bands, not a float. A raw acoustic score is not meaningful to a user and must
 * never be composed into a nutrition confidence percentage. LOW maps to
 * [io.github.vedant7007.katori.domain.model.ConfidenceReason.LOW_ASR_CONFIDENCE] downstream.
 */
enum class AsrConfidence { HIGH, LOW }

enum class SpeechLanguage(val tag: String) {
    TELUGU("te"),
    HINDI("hi"),
    ENGLISH_INDIA("en-IN"),
}

/** PCM audio. 16 kHz mono 16-bit unless an implementation states otherwise in its own docs. */
data class AudioClip(
    val samples: ShortArray,
    val sampleRateHz: Int,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is AudioClip && sampleRateHz == other.sampleRateHz && samples.contentEquals(other.samples))

    override fun hashCode(): Int = 31 * samples.contentHashCode() + sampleRateHz
}
