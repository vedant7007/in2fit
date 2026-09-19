package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.DeviceTier
import io.github.vedant7007.katori.domain.ModelFamily
import io.github.vedant7007.katori.domain.ModelHandle
import io.github.vedant7007.katori.ml.asr.SpeechLanguage

/**
 * A loaded Piper voice: what the arbiter hands back as `LoadedModel.native` for a TTS handle.
 *
 * The seam between [PiperTtsEngine] and sherpa-onnx, for the reason `LlamaRuntime` is one:
 * everything worth testing in the engine is lease use, language routing, cancellation and the
 * text going through unchanged, none of which needs a JNI library. [SherpaPiperVoice] is the one
 * real implementation and is exercised only on a device.
 */
interface PiperVoice : AutoCloseable {
    val sampleRateHz: Int

    /**
     * Synthesise [text] to mono float PCM in -1..1. Blocking and CPU-bound, so it is called off
     * the main thread. [keepGoing] is polled as audio is produced; returning false abandons the
     * rest, which is how a cancelled speak() stops paying for audio nobody will hear.
     */
    fun synthesise(text: String, keepGoing: () -> Boolean): FloatArray
}

/**
 * The voices this app ships, as arbiter handles.
 *
 * ON-DEVICE LAYOUT. A voice is a directory under the models directory holding `model.onnx` (the
 * Piper voice with sherpa-onnx metadata stamped in) and `tokens.txt` beside it. Both are produced
 * by `stamp_piper_voice.py` from the files rhasspy/piper-voices publishes; the
 * unstamped download does not load. `relativePath` points at the model and the loader finds the
 * tokens beside it.
 *
 * `estimatedResidentBytes` is the stamped file's size on disk, which is the only manifest figure
 * there is. It is admission arithmetic, not a measurement: the resident cost with the session,
 * the phonemiser and espeak-ng loaded is what `ModelArbiter.canCoReside` measures and appends to
 * the measurement log, and `0013` is where that row goes.
 */
object PiperVoices {
    /** `te_IN-padmavathi-medium`, CC-BY-4.0, `docs/decisions/0005`. */
    val TELUGU = ModelHandle(
        id = "tts.piper-te_IN-padmavathi-medium",
        family = ModelFamily.TTS,
        relativePath = "tts/te_IN-padmavathi-medium/model.onnx",
        estimatedResidentBytes = 63_516_206L,
        minimumTier = DeviceTier.LOW,
    )

    /** `hi_IN-pratham-medium`, CC-BY-NC-SA-4.0, in the non-commercial register in `0005`. */
    val HINDI = ModelHandle(
        id = "tts.piper-hi_IN-pratham-medium",
        family = ModelFamily.TTS,
        relativePath = "tts/hi_IN-pratham-medium/model.onnx",
        estimatedResidentBytes = 63_516_205L,
        minimumTier = DeviceTier.LOW,
    )

    val byLanguage: Map<SpeechLanguage, ModelHandle> = mapOf(
        SpeechLanguage.TELUGU to TELUGU,
        SpeechLanguage.HINDI to HINDI,
    )
}
