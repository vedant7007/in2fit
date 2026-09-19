package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.ModelFamily
import io.github.vedant7007.katori.domain.ModelHandle
import io.github.vedant7007.katori.domain.ModelLoader
import java.io.File

/**
 * The `ModelFamily.TTS` branch of the arbiter's loader.
 *
 * `LlamaCppModelLoader` in ml/llm owns the family switch and delegates TTS here, the same shape
 * as the ASR loader in ml/asr. Any other family is refused, so a mis-wired branch fails with a
 * sentence rather than loading the wrong runtime.
 *
 * [espeakDataDir] is where [EspeakData.install] put the shipped tables; the caller that builds
 * the arbiter installs them first, so this loader never needs a Context.
 */
class PiperVoiceLoader(
    private val modelsDir: File,
    private val espeakDataDir: File,
    private val threads: Int = 2,
) : ModelLoader {

    override suspend fun load(handle: ModelHandle): Any {
        require(handle.family == ModelFamily.TTS) { "${handle.id} is ${handle.family}; this loader binds TTS only" }
        return SherpaPiperVoice.load(File(modelsDir, handle.relativePath), espeakDataDir, threads)
    }

    override fun unload(handle: ModelHandle, native: Any) {
        (native as? PiperVoice)?.close()
    }
}
