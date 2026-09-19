package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.SpeechLanguage

/**
 * One [TtsEngine] for the app, over the engines that can speak, in order of preference.
 *
 * For each call the engines that claim the language are tried in the order given. An engine
 * that answers MODEL_NOT_LOADED has no voice for that language ON THIS DEVICE (the platform
 * engine without an installed offline Telugu voice, a Piper voice not yet staged), and the next
 * one is tried; any other answer is final. So the platform engine can go first, with Piper as
 * the fallback for a device that lacks the voice, without either knowing about the other.
 *
 * The user picks the voice language. A language no engine can speak here is MODEL_NOT_LOADED,
 * and never another language's voice: Telugu read with Hindi phonology is worse than silence.
 */
class RoutingTtsEngine(private val engines: List<TtsEngine>) : TtsEngine {

    override val supportedLanguages: Set<SpeechLanguage>
        get() = engines.flatMapTo(mutableSetOf()) { it.supportedLanguages }

    override suspend fun prepare(language: SpeechLanguage): Outcome<Unit> =
        firstWithVoice(language) { it.prepare(language) }

    override suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit> =
        firstWithVoice(language) { it.speak(text, language) }

    override suspend fun stop() {
        engines.forEach { it.stop() }
    }

    private suspend fun firstWithVoice(language: SpeechLanguage, call: suspend (TtsEngine) -> Outcome<Unit>): Outcome<Unit> {
        val tried = mutableListOf<String>()
        for (engine in engines) {
            if (language !in engine.supportedLanguages) continue
            val outcome = call(engine)
            if (outcome is Outcome.Unavailable && outcome.reason == UnavailableReason.MODEL_NOT_LOADED) {
                tried += "${engine::class.java.simpleName}: ${outcome.detail}"
                continue
            }
            return outcome
        }
        return Outcome.Unavailable(
            UnavailableReason.MODEL_NOT_LOADED,
            if (tried.isEmpty()) "no TTS engine claims $language; available: $supportedLanguages"
            else "no voice for $language on this device; tried $tried",
        )
    }
}
