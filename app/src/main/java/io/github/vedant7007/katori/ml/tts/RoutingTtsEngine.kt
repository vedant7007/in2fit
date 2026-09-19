package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.SpeechLanguage

/**
 * One [TtsEngine] for the app, over one engine per voice source.
 *
 * Telugu and Hindi are Piper voices through sherpa-onnx; English is the platform's own
 * TextToSpeech, which ships with the OS and has no licence question. The user picks the voice
 * language, and this routes each call to the first engine that claims it. A language nobody
 * claims is MODEL_NOT_LOADED, and never another language's voice.
 */
class RoutingTtsEngine(private val engines: List<TtsEngine>) : TtsEngine {

    override val supportedLanguages: Set<SpeechLanguage>
        get() = engines.flatMapTo(mutableSetOf()) { it.supportedLanguages }

    override suspend fun prepare(language: SpeechLanguage): Outcome<Unit> =
        engineFor(language)?.prepare(language) ?: nobody(language)

    override suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit> =
        engineFor(language)?.speak(text, language) ?: nobody(language)

    override suspend fun stop() {
        engines.forEach { it.stop() }
    }

    private fun engineFor(language: SpeechLanguage) = engines.firstOrNull { language in it.supportedLanguages }

    private fun nobody(language: SpeechLanguage) = Outcome.Unavailable(
        UnavailableReason.MODEL_NOT_LOADED,
        "no TTS engine speaks $language; available: $supportedLanguages",
    )
}
