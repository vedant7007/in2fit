package io.github.vedant7007.katori.ml.asr

/**
 * What a loaded ASR model can do. The arbiter's `LoadedModel.native` is one of these for
 * `ModelFamily.ASR`, and it is valid only inside the lease that produced it.
 *
 * The seam exists for the same reason `LlamaRuntime` and `ModelLoader` do: everything worth
 * testing in [DefaultAsrEngine] is endpointing, clip handling and how a decoder's output becomes
 * an `Outcome`, and none of that needs a 197 MB model or a phone. The one real implementation is
 * [SherpaOnnxAsrLoader.Decoder]; tests script this interface.
 */
interface AsrDecoder : AutoCloseable {

    /**
     * Transcribe one complete clip. Blocking CPU work: on the desktop 100-180 ms for a 3-4 s
     * clip, on the phone unmeasured. Callers run it off the main thread.
     *
     * [sampleRateHz] may differ from the model's 16 kHz; the implementation resamples.
     */
    fun decode(samples: ShortArray, sampleRateHz: Int): Decoded

    /**
     * What the model emitted. [tokens] are the model's own pieces, kept so the engine can measure
     * the output rather than trust it: the CTC model emits one or two phantom pieces on silence
     * and on quiet noise, so [text] being non-empty does not mean anyone spoke.
     */
    data class Decoded(val text: String, val tokens: List<String>)
}
