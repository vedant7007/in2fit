package io.github.vedant7007.katori.ml.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import io.github.vedant7007.katori.domain.ModelFamily
import io.github.vedant7007.katori.domain.ModelHandle
import io.github.vedant7007.katori.domain.ModelLoader
import java.io.File

/**
 * The ASR branch of the arbiter's [ModelLoader]: opens a NeMo CTC model in sherpa-onnx.
 *
 * This is the only file in `ml/asr` that imports sherpa-onnx. It refuses every family but ASR
 * with an exception naming the gap, exactly as `LlamaCppModelLoader` does for its family, so the
 * loader bound in `AppModule` can route on family and nothing above it changes.
 *
 * WHAT IT DOES NOT DO: load anything on its own initiative. It is called by the arbiter, off the
 * main thread, inside admission; the engine never sees it.
 *
 * EVERY PATH IS CHECKED HERE, BEFORE SHERPA-ONNX SEES IT. On a missing file sherpa-onnx either
 * returns a null pointer, which its Kotlin wrapper turns into an exception, or logs one line and
 * calls `_Exit(-1)`, which on Android is the process gone with nothing for the arbiter to catch.
 * An exception thrown from here is caught by the arbiter and becomes MODEL_LOAD_FAILED with the
 * path in its detail. That is the failure the contract asks for.
 *
 * NEMO CTC, NOT WHISPER. `0005`: these are NeMo conformers and load through the `nemo` slot of
 * the model config; feeding them to the Whisper slot looks like a broken model. Metadata the
 * runtime reads from the file itself (`vocab_size`, `subsampling_factor`, `normalize_type`) was
 * confirmed present on all three files on the desktop; `0021`.
 *
 * [threads] is a starting point, not a measurement. Four because the desktop figures in `0021`
 * were taken at four; nothing about the model's speed on the device's 2+6 big.LITTLE part is
 * known, and the thread count is the first knob Rao's probe should turn.
 */
class SherpaOnnxAsrLoader(
    private val modelsDir: File,
    private val threads: Int = 4,
) : ModelLoader {

    override suspend fun load(handle: ModelHandle): Any {
        require(handle.family == ModelFamily.ASR) {
            "${handle.family} is not this loader's family; SherpaOnnxAsrLoader loads ASR only"
        }
        val model = File(modelsDir, handle.relativePath)
        val tokens = File(modelsDir, AsrModels.tokensFileFor(handle.relativePath))
        require(model.isFile && model.length() > 0L) { "ASR model missing: $model" }
        require(tokens.isFile && tokens.length() > 0L) { "tokens.txt missing beside the ASR model: $tokens" }

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE_HZ, featureDim = FEATURE_DIM, dither = 0.0f),
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = model.absolutePath),
                tokens = tokens.absolutePath,
                numThreads = threads,
                debug = false,
                provider = "cpu",
            ),
            decodingMethod = "greedy_search",
        )
        // The wrapper's init throws IllegalArgumentException when the native side hands back a
        // null pointer. That propagates to the arbiter as-is; there is nothing to add to it here.
        return Decoder(OfflineRecognizer(assetManager = null, config = config))
    }

    override fun unload(handle: ModelHandle, native: Any) {
        (native as? Decoder)?.close()
    }

    /** One recognizer, valid until the arbiter unloads it. */
    class Decoder internal constructor(private val recognizer: OfflineRecognizer) : AsrDecoder {

        @Volatile
        private var closed = false

        override fun decode(samples: ShortArray, sampleRateHz: Int): AsrDecoder.Decoded {
            check(!closed) { "recognizer was released; an AsrDecoder is only valid inside the lease that produced it" }
            val floats = FloatArray(samples.size) { samples[it] / PCM16_FULL_SCALE }
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(floats, sampleRateHz)
                recognizer.decode(stream)
                val result = recognizer.getResult(stream)
                return AsrDecoder.Decoded(text = result.text, tokens = result.tokens.toList())
            } finally {
                stream.release()
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            recognizer.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FEATURE_DIM = 80
        const val PCM16_FULL_SCALE = 32_768f
    }
}
