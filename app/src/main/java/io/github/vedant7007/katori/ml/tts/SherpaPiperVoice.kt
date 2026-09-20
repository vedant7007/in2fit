package io.github.vedant7007.katori.ml.tts

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * A Piper voice running in sherpa-onnx. The one real [PiperVoice].
 *
 * This is the TTS branch of the arbiter's `ModelLoader`: [load] is what that loader calls for
 * `ModelFamily.TTS`, and [close] is its unload. Nothing else in the app constructs one.
 *
 * sherpa-onnx is used with `assetManager = null`: the voice lives in the models directory, where
 * the other models are staged, and espeak-ng needs a real directory for its data in any case.
 */
class SherpaPiperVoice private constructor(private val tts: OfflineTts) : PiperVoice {

    override val sampleRateHz: Int = tts.sampleRate()

    @Volatile
    private var closed = false

    override fun synthesise(text: String, keepGoing: () -> Boolean): FloatArray {
        check(!closed) { "voice was released; a PiperVoice is only valid inside the lease that produced it" }
        // sherpa-onnx splits the text into sentences itself and hands each one's audio to the
        // callback, then returns all of it concatenated. It never truncates, which the TtsEngine
        // contract requires. The callback's return is its only cancellation point: 1 continues,
        // 0 stops after the current sentence, and what came back so far is discarded upstream.
        return tts.generateWithCallback(text, sid = 0, speed = TtsFlags.SPEECH_RATE) { if (keepGoing()) 1 else 0 }.samples
    }

    override fun close() {
        if (closed) return
        closed = true
        tts.release()
    }

    companion object {
        /**
         * Open [modelFile] with its sibling `tokens.txt` and [espeakDataDir].
         *
         * EVERY PATH IS CHECKED HERE, BEFORE SHERPA-ONNX SEES IT. sherpa-onnx reports a missing
         * file or missing metadata by logging one line and calling `_Exit(-1)`, which on Android
         * is the process gone with no exception for the arbiter to catch. An exception thrown here
         * is caught by the arbiter and becomes MODEL_LOAD_FAILED with the path in its detail.
         *
         * [threads] is a starting point, not a measurement: nothing about Piper's speed on the
         * device is known yet. Two because the test device has two big cores and synthesis runs
         * beside playback and the UI.
         */
        fun load(modelFile: File, espeakDataDir: File, threads: Int = 2): SherpaPiperVoice {
            val tokens = File(modelFile.parentFile, "tokens.txt")
            require(modelFile.isFile) { "voice model missing: $modelFile" }
            require(tokens.isFile) { "tokens.txt missing beside the voice: $tokens; produce it with stamp_piper_voice.py" }
            for (name in EspeakData.REQUIRED_FILES) {
                val f = File(espeakDataDir, name)
                require(f.isFile) { "espeak-ng-data incomplete: $f missing" }
            }
            val meta = PiperModelFile.metadata(modelFile)
            require(meta["comment"] == "piper" && "sample_rate" in meta && "n_speakers" in meta) {
                "$modelFile carries no sherpa-onnx metadata (keys: ${meta.keys}); this is the raw " +
                    "piper-voices download, stamp it with stamp_piper_voice.py"
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelFile.absolutePath,
                        tokens = tokens.absolutePath,
                        dataDir = espeakDataDir.absolutePath,
                    ),
                    numThreads = threads,
                ),
            )
            // OfflineTts's init throws IllegalArgumentException when the native side returns a
            // null pointer, which is the remaining failure path and is also caught by the arbiter.
            return SherpaPiperVoice(OfflineTts(assetManager = null, config = config))
        }
    }
}
