package io.github.vedant7007.katori.ml.llm

import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * [LlamaRuntime] over the native llama.cpp shim in `app/src/main/cpp/katori_llama.cpp`.
 *
 * NOTHING HERE HAS RUN ON A PHONE. It compiles and links; that is the only claim available. No
 * figure for load time, tokens per second, memory or output quality may be quoted from anywhere
 * until it has run on the Realme 11 and the number has been read out of a log.
 *
 * WHY THIS CLASS IS THIN. Everything that can be wrong without a device is above it, in
 * [LlamaCppLlmEngine] and its tests. This file holds only what genuinely needs the native library,
 * so that the part which cannot be tested yet is also the part with the least in it.
 *
 * LIFECYCLE. [close] frees the native model and context and is idempotent. Using the instance
 * afterwards throws rather than returning an empty string, because a silent empty completion would
 * surface as a schema failure and send someone hunting for a prompt bug.
 */
class LlamaCppRuntime private constructor(private var handle: Long) : LlamaRuntime {

    override fun generate(prompt: String, maxTokens: Int, stop: List<String>): String {
        val h = handle
        check(h != 0L) { "this runtime has been closed" }
        return nativeGenerate(h, prompt, maxTokens, stop.toTypedArray())
    }

    override fun close() {
        val h = handle
        if (h == 0L) return
        handle = 0L
        nativeFree(h)
    }

    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, stop: Array<String>): String
    private external fun nativeFree(handle: Long)

    companion object {
        private val loaded = AtomicLong(0)

        /**
         * Loads a GGUF model.
         *
         * @param contextTokens the context window. The shim REFUSES a prompt that would not fit
         *   rather than trimming it, because trimming removes the system instructions first, and
         *   those are the rules that tell the model not to invent numbers.
         * @throws IllegalStateException if the file is missing or the model fails to load. It does
         *   not fall back to anything; the arbiter reports MODEL_LOAD_FAILED and the app says so.
         */
        fun load(modelFile: File, contextTokens: Int = 2048, threads: Int = 4): LlamaCppRuntime {
            check(modelFile.isFile) { "no model file at ${modelFile.absolutePath}" }
            ensureLibrary()
            // nativeLoad is an instance method purely so its JNI symbol stays
            // Java_..._LlamaCppRuntime_nativeLoad. Declaring it on the companion would rename the
            // symbol to include 00024Companion, which is a mangling nobody reading the C++ would
            // expect. It does not touch the handle field, so calling it on a zero instance is safe.
            val loader = LlamaCppRuntime(0L)
            val h = loader.nativeLoad(modelFile.absolutePath, contextTokens, threads)
            check(h != 0L) { "the native loader returned no handle" }
            return LlamaCppRuntime(h)
        }

        private fun ensureLibrary() {
            if (loaded.compareAndSet(0L, 1L)) {
                // Order matters: the dependencies have to be resident before the shim that links
                // against them, because Android's loader does not search the APK's lib directory
                // for transitive dependencies on every API level this app supports.
                listOf("ggml-base", "ggml-cpu", "ggml", "llama", "katori_llama")
                    .forEach { System.loadLibrary(it) }
            }
        }
    }

    private external fun nativeLoad(modelPath: String, contextTokens: Int, threads: Int): Long
}
