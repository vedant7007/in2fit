package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.domain.ModelFamily
import io.github.vedant7007.katori.domain.ModelHandle
import io.github.vedant7007.katori.domain.ModelLoader
import java.io.File

/**
 * [ModelLoader] for the ONE runtime that exists.
 *
 * LLM ONLY, AND SAID SO. ASR and TTS have no runtime yet; sherpa-onnx is not integrated. This
 * loader refuses those families with an exception that names the gap, which the arbiter turns into
 * MODEL_LOAD_FAILED with that message in `detail`. It does not return a placeholder session,
 * because a placeholder that loads successfully and does nothing is how a not-implemented state
 * becomes a fake one, and the co-residency measurement would then be measuring nothing.
 *
 * When sherpa-onnx lands, its families are added HERE, one branch each, and nothing above this
 * file changes. That is what the seam is for.
 *
 * The thread count matches the figure `0014` was measured at.
 */
class LlamaCppModelLoader(
    private val modelsDir: File,
    private val threads: Int = 8,
    private val contextTokens: Int = 2048,
) : ModelLoader {

    override suspend fun load(handle: ModelHandle): Any = when (handle.family) {
        ModelFamily.LLM -> LlamaCppRuntime.load(File(modelsDir, handle.relativePath), contextTokens, threads)
        else -> throw UnsupportedOperationException(
            "${handle.family} has no runtime bound; only LLM does until sherpa-onnx is integrated"
        )
    }

    override fun unload(handle: ModelHandle, native: Any) {
        (native as? LlamaCppRuntime)?.close()
    }
}
