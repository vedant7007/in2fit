package io.github.vedant7007.katori.ml.llm

/**
 * The narrowest possible seam over the native llama.cpp runtime.
 *
 * WHY THIS EXISTS. Everything worth testing on the LLM path is above this line: prompt
 * construction, strict schema validation, the re-ask budget, and the numeric guard that decides
 * whether generated prose is allowed to reach the screen. None of that involves a model. Behind
 * this seam it all runs as a plain JVM unit test against a scripted runtime that returns exactly
 * the malformed, truncated and invented output a real model produces on a bad day, which is far
 * stronger than waiting for a phone to produce one by chance.
 *
 * It is the same seam as `FoodDbSource` in the data layer, for the same reason, and it earned its
 * keep there.
 *
 * CONTRACT
 * - [generate] is blocking and must be called off the main thread. The caller holds a ModelArbiter
 *   lease for the whole call.
 * - It returns the raw completion, with the prompt removed. No parsing, no repair, no trimming
 *   beyond what the runtime itself does. Interpretation belongs above this line.
 * - It has no `complete`-shaped escape hatch to the rest of the app: the only implementation of
 *   [LlmEngine] holds this privately, so spec 11.5's two-paths rule is not weakened by its
 *   existence.
 * - Implementations are single-threaded per instance.
 */
interface LlamaRuntime {

    /**
     * @param stop sequences that end generation. The caller relies on these; without them a model
     *             cheerfully writes a second JSON object after the first.
     */
    fun generate(prompt: String, maxTokens: Int, stop: List<String>): String

    /** Releases native memory. After this the instance is unusable. */
    fun close()
}
