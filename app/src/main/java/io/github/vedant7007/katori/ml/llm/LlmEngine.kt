package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.domain.RuleEvaluation
import io.github.vedant7007.katori.domain.model.Outcome

/**
 * The local language model. Two paths, and nothing else.
 *
 * THE HARD BOUNDARY, spec 11.5
 * The model EXTRACTS and EXPLAINS. It never computes a number. Every calorie, gram and percentage
 * comes from the database and from code. This is not a guideline in this codebase, it is enforced
 * three ways:
 *
 *  1. There is no general `complete(prompt)` method. The only entry points are [extract] and
 *     [phrase]. A third path cannot be added without changing this interface, which goes to the
 *     integrator.
 *  2. [PhrasingRequest] hands the model figures as PRE-FORMATTED STRINGS in [DisplayFigure], not as
 *     numbers. There is nothing typed as a number for it to do arithmetic on.
 *  3. [phrase] implementations MUST run [NumericGuard] over the output and reject any response
 *     containing a numeric token that did not appear in the input. A model that invents "about 450
 *     calories" fails validation and the call returns Unavailable rather than showing the invention.
 *
 * The third of these is the one that actually catches mistakes at runtime, and it is a required
 * post-condition, not an optimisation.
 *
 * CONTRACT
 * - Both paths are suspending and run off the main thread, via the ModelArbiter.
 * - Temperature, sampling and prompt text are implementation details, but [extract] must be
 *   deterministic enough that the same transcript yields the same items in testing; it is validated
 *   against a schema regardless.
 * - The two prompt paths stay separate. A single prompt doing both jobs degrades both (spec 11.6).
 *
 * FAILURE MODES
 * - Extraction output fails schema validation after the permitted re-asks: SCHEMA_VALIDATION_FAILED.
 *   The orchestrator then asks the user rather than guessing (spec 10.7). It never repairs the JSON
 *   by inference.
 * - Phrasing output fails [NumericGuard]: INTERNAL_ERROR with the offending token in `detail`. The
 *   UI shows the rules engine's own trigger sentence, which needs no model, and omits the prose.
 * - Model not admitted: propagated from the arbiter.
 */
interface LlmEngine {

    /**
     * Transcript in, structured items out. Strict JSON, schema-validated.
     *
     * @return items with quantities and units as the user said them. Unit conversion and food
     *         matching happen later in data/food; this path does not resolve foods and must not
     *         invent a quantity the speaker did not give.
     */
    suspend fun extract(request: ExtractionRequest): Outcome<ExtractionResult>

    /**
     * Turn an already-decided result into a sentence in the user's language.
     *
     * The decisions are already made when this is called: the rules engine chose the constraints and
     * ranked the candidates, and the database produced the figures. This path only puts words around
     * them.
     */
    suspend fun phrase(request: PhrasingRequest): Outcome<PhrasedText>
}

data class ExtractionRequest(
    val transcript: String,
    val languageTag: String,
    /** How many times a schema-invalid response may be re-asked before failing. Small, e.g. 2. */
    val maxReasks: Int = 2,
)

data class ExtractionResult(
    val items: List<ExtractedItem>,
    /** Raw model output, kept for the unmatched-utterance log and for debugging. */
    val rawJson: String,
)

data class ExtractedItem(
    val name: String,
    /** Null when the speaker did not state a quantity. NEVER filled in by the model. */
    val quantity: Double?,
    /** The unit as spoken: "katori", "plate", "spoon", "g". Not normalised here. */
    val unit: String?,
    /** Cooking method if the speaker mentioned one: fried, boiled, steamed, tempered. */
    val cookingMethod: String?,
)

data class PhrasingRequest(
    val evaluation: RuleEvaluation,
    /**
     * The trigger sentence, ALREADY RENDERED in the user's language by `TriggerText` from the
     * string table. The engine emits a template id and evidence, not words, so the caller renders
     * before phrasing. Null when nothing fired. Every number in it came from the evidence, which
     * is why the numeric guard is allowed to permit exactly its figures.
     */
    val triggerText: String?,
    /** Figures as strings, already formatted with units. The model cannot do arithmetic on these. */
    val figures: List<DisplayFigure>,
    val languageTag: String,
    /** Names the model may suggest. It picks from this list and explains; it does not invent. */
    val allowedFoodNames: List<String>,
)

/**
 * A figure as it will appear on screen, e.g. "about 320 kcal" or "12 g protein".
 *
 * Deliberately a String and not a Double. Handing the model a typed number is an invitation to
 * manipulate it; handing it the final display text is not.
 */
@JvmInline
value class DisplayFigure(val text: String)

data class PhrasedText(
    val text: String,
    /** True when [NumericGuard] passed. A false here must never reach the UI. */
    val numericGuardPassed: Boolean,
)

/**
 * Rejects generated text that contains a number the model was not given.
 *
 * RULE: every numeric token in the output must appear as a numeric token in the input, where input
 * means the request's figures, the trigger sentence and the allowed food names. Ordinals and small
 * cardinals written as words are out of scope and handled by the safety string review instead.
 *
 * This is the last line of defence behind spec 11.5, and it is required, not advisory.
 */
interface NumericGuard {
    /** @return the first offending token, or null when the output is clean. */
    fun firstInventedNumber(output: String, permittedFrom: List<String>): String?
}
