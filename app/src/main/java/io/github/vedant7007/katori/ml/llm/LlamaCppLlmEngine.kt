package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [LlmEngine] over a local llama.cpp runtime.
 *
 * NOTHING HERE HAS RUN ON A PHONE. The logic in this file is unit tested against a scripted
 * runtime; the native side behind [LlamaRuntime] is not, and no claim about speed, memory or
 * output quality on a device may be made until it has.
 *
 * The two paths of spec 11.5 stay separate, and each is defended differently:
 *
 *  - [extract] is defended by a STRICT SCHEMA. Anything that is not exactly the agreed shape is
 *    refused and re-asked, and when the budget runs out the orchestrator asks the person. The
 *    JSON is never repaired by inference.
 *  - [phrase] is defended by the NUMERIC GUARD. Figures go in as finished strings, and any
 *    numeric token in the output that was not in the input fails the call. The caller then shows
 *    the rules engine's own sentence, which needs no model.
 *
 * ### Why the numeric guard is not also applied to extraction
 *
 * It was considered and it does not work there. A person says "two rotis", and the correct
 * extraction is a quantity of 2 with no digit anywhere in the transcript. A digit-based guard
 * would reject the right answer, in every language, constantly. Number words across nine
 * languages are exactly what the guard's contract says it will not attempt.
 *
 * What protects the quantity instead is already in the design: a quantity the speaker did not
 * state carries `QUANTITY_INFERRED`, which caps the figure at Rough, and spec 13.4 requires it to
 * be shown as a correctable value rather than applied silently. An invented quantity therefore
 * surfaces to the person as a visible, editable, Rough number. That is the designed mitigation
 * and it is better than a guard that fires on correct output.
 *
 * Grounding the extracted NAME in the transcript was considered too, and rejected for a related
 * reason: a model correctly rendering an utterance in one script as its English name would fail
 * it, and refusing a correct translation would push the work back onto the person for no safety
 * gain.
 *
 * ### There is deliberately no guard on the FOODS named in generated prose
 *
 * A guard that parsed the sentence for food nouns would need a food vocabulary of the whole
 * language and would reject correct sentences constantly. What holds the line instead is
 * structural: the prompt is given the allowed list and nothing else, and the UI renders
 * suggestions from the rules engine's ranked candidates, never from the model's text. A
 * half-working noun matcher here would read as a guarantee it cannot give, which is worse than
 * no matcher, so there is none.
 */
class LlamaCppLlmEngine(
    private val runtime: LlamaRuntime,
    private val guard: NumericGuard = DefaultNumericGuard(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LlmEngine {

    override suspend fun extract(request: ExtractionRequest): Outcome<ExtractionResult> {
        if (request.transcript.isBlank()) {
            return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "empty transcript")
        }
        val attempts = 1 + request.maxReasks.coerceIn(0, MAX_REASK_CEILING)
        var lastRejection = "no attempt was made"

        for (attempt in 0 until attempts) {
            val prompt = Prompts.extraction(request.transcript, request.languageTag, retryOf = lastRejection.takeIf { attempt > 0 })
            val raw = withContext(dispatcher) {
                runtime.generate(prompt, EXTRACTION_MAX_TOKENS, EXTRACTION_STOPS)
            }
            when (val parsed = ExtractionJson.parse(raw)) {
                is ExtractionJson.Result.Parsed ->
                    return Outcome.Ok(ExtractionResult(parsed.items, parsed.normalisedJson))
                is ExtractionJson.Result.Rejected -> lastRejection = parsed.why
            }
        }
        // The budget is spent. This is NOT the place to salvage something from the last response:
        // the orchestrator asks the person, which spec 10.7 already requires for an uncertain parse.
        return Outcome.Unavailable(
            UnavailableReason.SCHEMA_VALIDATION_FAILED,
            "after $attempts attempts: $lastRejection",
        )
    }

    override suspend fun phrase(request: PhrasingRequest): Outcome<PhrasedText> {
        // Everything the model is allowed to have a number from. The trigger sentence is in here
        // because it legitimately carries lab figures the prose may repeat, e.g. "142 mg/dL".
        val permitted = buildList {
            request.figures.forEach { add(it.text) }
            request.triggerText?.let { add(it) }
            addAll(request.allowedFoodNames)
        }

        val prompt = Prompts.phrasing(request, permitted)
        val raw = withContext(dispatcher) {
            runtime.generate(prompt, PHRASING_MAX_TOKENS, PHRASING_STOPS)
        }
        val text = raw.trim()
        if (text.isEmpty()) {
            return Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "the model returned nothing")
        }

        // The required post-condition, not an optimisation. A single invented figure fails the
        // whole response; the caller shows the trigger sentence instead and loses only the prose.
        guard.firstInventedNumber(text, permitted)?.let { offending ->
            return Outcome.Unavailable(
                UnavailableReason.INTERNAL_ERROR,
                "the model invented the number '$offending'",
            )
        }

        return Outcome.Ok(PhrasedText(text = text, numericGuardPassed = true))
    }

    private companion object {
        const val EXTRACTION_MAX_TOKENS = 256
        const val PHRASING_MAX_TOKENS = 160

        /** A hard ceiling on re-asks whatever the caller passes. Each one costs a model round trip. */
        const val MAX_REASK_CEILING = 3

        /**
         * "```" IS DELIBERATELY NOT A STOP HERE, AND MUST NOT BE ADDED BACK.
         *
         * It used to be, and it made [ExtractionJson]'s fence handling unreachable. When the
         * model chose to wrap its answer in a markdown fence, the very first thing it emitted
         * was "```", which matched the stop, was trimmed off as the stop, and left an empty
         * string. The strict reader then refused "empty response", the engine re-asked, and
         * extraction failed after three full prompt evaluations against a model that had in fact
         * produced perfectly good JSON on the first attempt.
         *
         * [ExtractionJson.parse] already strips a fence that wraps the whole response, on the
         * stated grounds that removing an envelope is not guessing at contents, and there is a
         * test for it. Stopping at the fence guaranteed that code never ran.
         *
         * Whether the model fences is a property of the prompt wording, not of the schema, so it
         * is not something the stop list should be enforcing. The prompt asks for no markdown;
         * if it gets markdown anyway, the reader copes.
         */
        val EXTRACTION_STOPS = listOf("\n\n", "</s>", "<|im_end|>")
        val PHRASING_STOPS = listOf("\n\n", "</s>", "<|im_end|>")

    }
}
