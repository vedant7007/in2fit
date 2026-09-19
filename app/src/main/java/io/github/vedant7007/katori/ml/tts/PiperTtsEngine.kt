package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.ModelArbiter
import io.github.vedant7007.katori.domain.ModelHandle
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * [TtsEngine] over Piper voices in sherpa-onnx, for Telugu and Hindi.
 *
 * The voice comes from the [ModelArbiter] for exactly the length of one [speak]: synthesis and
 * playback happen inside the lease, and the voice is unpinned when the audio has been heard.
 * Nothing here loads a file or keeps a reference to a voice, per the contract. A caller that
 * wants the voice warm before the user speaks calls [prepare], which is the arbiter's preload.
 *
 * The text is handed to the voice unchanged. sherpa-onnx splits it into sentences internally and
 * never truncates, which is the one transformation the contract permits.
 */
class PiperTtsEngine(
    private val arbiter: ModelArbiter,
    private val sink: AudioSink,
    private val voices: Map<SpeechLanguage, ModelHandle> = PiperVoices.byLanguage,
    private val synthesisDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TtsEngine {

    override val supportedLanguages: Set<SpeechLanguage> get() = voices.keys

    /** Bumped by [stop], so a synthesis already under way is abandoned rather than played late. */
    private val stops = AtomicInteger()

    override suspend fun prepare(language: SpeechLanguage): Outcome<Unit> {
        val handle = voices[language] ?: return noVoice(language)
        return arbiter.preload(handle)
    }

    override suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit> {
        val handle = voices[language] ?: return noVoice(language)
        if (text.isBlank()) return Outcome.Ok(Unit)
        val epoch = stops.get()

        val leased = arbiter.withModel(handle) { loaded ->
            val voice = loaded.native as PiperVoice
            val pcm = withContext(synthesisDispatcher) {
                voice.synthesise(text) { isActive && stops.get() == epoch }
            }
            currentCoroutineContext().ensureActive()
            if (stops.get() != epoch) {
                Outcome.Unavailable(UnavailableReason.CANCELLED, "stopped during synthesis")
            } else {
                sink.play(pcm, voice.sampleRateHz)
            }
        }
        return when (leased) {
            is Outcome.Ok -> leased.value
            is Outcome.Unavailable -> leased
            is Outcome.NotImplemented -> leased
        }
    }

    override suspend fun stop() {
        stops.incrementAndGet()
        sink.stop()
    }

    private fun noVoice(language: SpeechLanguage) = Outcome.Unavailable(
        UnavailableReason.MODEL_NOT_LOADED,
        "no Piper voice for $language; this engine has ${voices.keys}",
    )
}
