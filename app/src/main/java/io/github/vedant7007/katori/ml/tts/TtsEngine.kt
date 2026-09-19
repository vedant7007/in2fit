package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.asr.SpeechLanguage

/**
 * Text to speech, fully offline, in the user's language.
 *
 * CONTRACT
 * - Speaks the text it is given, unchanged. It does not summarise, translate or rephrase, because
 *   the words the user hears must be the words that passed the safety review (spec 15.2). Any
 *   rewriting happens upstream in ml/llm, under the rules engine's constraints.
 * - [speak] suspends until playback finishes or is cancelled, so callers can sequence a spoken
 *   confirmation followed by a prompt without racing.
 * - Cancellation stops audio immediately and releases the audio focus.
 * - Models come from the ModelArbiter, same as ASR.
 *
 * FAILURE MODES
 * - Voice for the language is not present: MODEL_NOT_LOADED. The caller falls back to showing the
 *   text on screen; it does NOT fall back to a different language's voice, which would read Telugu
 *   words with Hindi phonology and sound worse than silence.
 * - Audio focus denied, e.g. a call in progress: CANCELLED, and the UI keeps the text visible.
 * - Text longer than the per-utterance limit: the implementation splits on sentence boundaries. It
 *   never truncates, because a truncated health sentence can invert its meaning.
 */
interface TtsEngine {

    val supportedLanguages: Set<SpeechLanguage>

    suspend fun prepare(language: SpeechLanguage): Outcome<Unit>

    /** Synthesise and play. Suspends until playback completes. */
    suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit>

    /** Stop any playback in progress. Safe to call when nothing is playing. */
    suspend fun stop()
}
