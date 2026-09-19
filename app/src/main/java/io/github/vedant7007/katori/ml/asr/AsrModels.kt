package io.github.vedant7007.katori.ml.asr

import io.github.vedant7007.katori.domain.DeviceTier
import io.github.vedant7007.katori.domain.ModelFamily
import io.github.vedant7007.katori.domain.ModelHandle

/**
 * The ASR model for each [SpeechLanguage], as the arbiter sees it.
 *
 * LAYOUT, mirrored from `data-sources/models/asr/indicconformer/` and staged the same way on the
 * device: `asr/<lang>/model.int8.onnx` with its `tokens.txt` beside it. te and hi share one
 * 5,633-piece vocabulary (the repository's root `tokens.txt`, twelve scripts, `<blk>` last), so
 * both directories carry a copy of the same file; en has its own 1,025-piece one. The path a
 * handle carries is the model file; [tokensFileFor] derives the other.
 *
 * SIZES are the byte counts `logs/model-fetch.log` recorded for each file, used only for
 * admission arithmetic as the contract says. The real resident cost is what `canCoReside`
 * measures: `0013` saw a raw ORT session on the te file cost 165-260 MB, which is why the
 * arbiter measures instead of believing this figure.
 *
 * PROVENANCE, read from each file's ONNX metadata rather than from the repository's README:
 *  - te, hi: `model_author=ai4bharat`, `EncDecCTCModelBPE`, subsampling 4. IndicConformer.
 *  - en: `model_author=NeMo`, `stt_en_fastconformer_hybrid_large_pc`, subsampling 8. This is
 *    NVIDIA's stock English FastConformer, NOT an AI4Bharat model, and its id says so. `0021`.
 */
object AsrModels {

    private const val TE_BYTES = 197_595_693L
    private const val HI_BYTES = 197_595_593L
    private const val EN_BYTES = 174_610_057L

    fun handleFor(language: SpeechLanguage): ModelHandle = when (language) {
        SpeechLanguage.TELUGU -> handle("asr.indicconformer-te", "te", TE_BYTES)
        SpeechLanguage.HINDI -> handle("asr.indicconformer-hi", "hi", HI_BYTES)
        SpeechLanguage.ENGLISH_INDIA -> handle("asr.fastconformer-en", "en", EN_BYTES)
    }

    /** `asr/te/model.int8.onnx` -> `asr/te/tokens.txt`. */
    fun tokensFileFor(modelRelativePath: String): String =
        modelRelativePath.substringBeforeLast('/') + "/tokens.txt"

    private fun handle(id: String, dir: String, bytes: Long) = ModelHandle(
        id = id,
        family = ModelFamily.ASR,
        relativePath = "asr/$dir/model.int8.onnx",
        estimatedResidentBytes = bytes,
        minimumTier = DeviceTier.LOW,
    )
}
