package io.github.vedant7007.katori.orchestration

import android.util.Log
import io.github.vedant7007.katori.domain.ModelArbiter
import io.github.vedant7007.katori.ml.asr.AsrEngine
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import io.github.vedant7007.katori.ml.llm.LlamaRuntime
import io.github.vedant7007.katori.ml.llm.LlmModels
import io.github.vedant7007.katori.ml.tts.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 0032, the engine side: at app start, before the presenter touches anything, every model the
 * voice round trip needs is loaded and has run one throwaway inference, off the main thread and
 * off the critical path. LLM first (largest), then the recogniser for the speech language, then
 * the voice. Each `prepare` takes and releases its own lease; nothing is pinned across the wait
 * (the arbiter's rule), so the models stay resident and evictable.
 *
 * What it gives the day: checklist row 9 ("App WARM, not cold") stops being a person running a
 * sentence by hand, and the failure playbook's reopen on stage is a launch that warms itself.
 * `tools/cold-phone.ps1` waits for the `katori-warmup` line in logcat as its proof of "resident
 * and warm". The UI side (a "getting ready" state and a microphone that refuses, never queues,
 * before [ready]) is Arjun's; [ready] is the signal for it.
 */
@Singleton
class WarmUp @Inject constructor(
    private val arbiter: ModelArbiter,
    private val asr: AsrEngine,
    private val tts: TtsEngine,
) {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(language: SpeechLanguage) {
        if (_ready.value) return
        // An x86 emulator runs the arm64 libraries through translation and dies with SIGILL in
        // the first model load, before the first frame (Ira, 21 Sep 12:53). The emulator is a
        // layout tool; it gets no warm-up and says so. On an arm64 phone nothing changes.
        if (android.os.Build.SUPPORTED_ABIS.firstOrNull() != "arm64-v8a") {
            Log.i(TAG, "skipped: primary ABI ${android.os.Build.SUPPORTED_ABIS.firstOrNull()} is not arm64-v8a (translation); the models load on first use")
            return
        }
        scope.launch {
            val t0 = System.nanoTime()
            fun ms() = (System.nanoTime() - t0) / 1_000_000
            // One token from a two-word prompt: the weights get their first touch here, not on
            // the judge's sentence. The prompt is not the system prompt; the touch is what counts.
            val llm = arbiter.withModel(LlmModels.QWEN_2_5_1_5B_Q4_K_M) { (it.native as LlamaRuntime).generate("Hello.", 1, emptyList()) }
            val llmMs = ms()
            val asrOk = asr.prepare(language)
            val asrMs = ms() - llmMs
            val ttsOk = tts.prepare(language)
            val ttsMs = ms() - llmMs - asrMs
            Log.i(TAG, "llm ${llmMs} ms ($llm), asr ${language.name} ${asrMs} ms ($asrOk), tts ${ttsMs} ms ($ttsOk), total ${ms()} ms")
            _ready.value = true
        }
    }

    private companion object { const val TAG = "katori-warmup" }
}
