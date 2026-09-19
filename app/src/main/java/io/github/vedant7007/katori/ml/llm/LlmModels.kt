package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.domain.DeviceTier
import io.github.vedant7007.katori.domain.ModelFamily
import io.github.vedant7007.katori.domain.ModelHandle

/**
 * The language model, as the arbiter sees it.
 *
 * One file, staged at `models/qwen2.5-1.5b-instruct-q4_k_m.gguf` on the device, which is where
 * the hardware probe and the arbiter calibration have loaded it from since `0010`.
 *
 * The estimate is NOT the file size. It is the resident peak the arbiter measured for this model
 * alone on the test device on 20 Sep (`0013`: 1,887.6 MB, of which the GGUF is 1,117 MB and the
 * rest is the KV cache and the runtime), because the estimate is what admission arithmetic
 * subtracts before the measurement exists, and a file-size estimate would under-count by 770 MB.
 * The measurement replaces it every run.
 */
object LlmModels {

    private const val MEASURED_RESIDENT_BYTES = 1_979_000_000L

    val QWEN_2_5_1_5B_Q4_K_M = ModelHandle(
        id = "llm.qwen2.5-1.5b-q4km",
        family = ModelFamily.LLM,
        relativePath = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        estimatedResidentBytes = MEASURED_RESIDENT_BYTES,
        minimumTier = DeviceTier.LOW,
    )
}
