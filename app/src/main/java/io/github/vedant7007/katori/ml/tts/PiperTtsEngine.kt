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
                sink.play(normalisePeak(pcm), voice.sampleRateHz)
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

/**
 * Scale [pcm] so its loudest sample sits at [target] of full scale.
 *
 * A handset speaker in a crowded hall is close to inaudible, and the Piper voices measured on
 * the desktop peak anywhere from 0.41 to 0.81 of full scale depending on the voice and the
 * text (`0019` addendum 6). Whatever ships must sit at the top of the phone's range without
 * clipping, and VITS output is bounded, so a peak normalisation is exact: no sample exceeds
 * [target] afterwards. Gain is capped at [maxGain] so an utterance that is mostly silence is
 * not turned into amplified noise. Already-loud audio is turned DOWN to the same ceiling, which
 * is what keeps two voices at one level.
 *
 * ponytail: peak, not loudness. Two utterances at the same peak can differ in perceived
 * loudness; if listeners report the level wandering between voices, the upgrade is an RMS or
 * LUFS target with a limiter. Not before a phone has been heard.
 */
internal fun normalisePeak(pcm: FloatArray, target: Float = 0.89f, maxGain: Float = 8f): FloatArray {
    var peak = 0f
    for (s in pcm) { val a = if (s < 0f) -s else s; if (a > peak) peak = a }
    if (peak == 0f) return pcm
    val gain = minOf(target / peak, maxGain)
    if (gain == 1f) return pcm
    return FloatArray(pcm.size) { pcm[it] * gain }
}
