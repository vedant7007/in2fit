package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Confidence
import io.github.vedant7007.katori.domain.model.Nutrient
import java.time.Instant
import java.time.LocalDate

/**
 * The deterministic core of the product.
 *
 * WHY THIS EXISTS
 * Spec 11.5 forbids the LLM from computing anything, and spec 4.3 constrains suggestions to a
 * bundled ingredient list. Taken together, what actually changes the advice at demo beat 4 is not
 * generation: a lab value crosses a threshold, that fires a rule, the rule imposes a constraint,
 * the constraint filters and ranks candidate swaps, and only then does the LLM phrase the result.
 * That chain is this interface. It was missing from the first draft of the architecture and it is
 * the component the entire demo rests on.
 *
 * It also solves a demo problem. LLM output varies run to run even at low temperature, so "the
 * advice changed because of the report" cannot be proved from prose. [RuleEvaluation.trigger] is a
 * templated sentence produced HERE, from evidence, with no model involved. That sentence is what
 * makes beat 4 defensible when a judge asks how you know the report caused the change.
 *
 * DETERMINISM CONTRACT, ABSOLUTE
 * - Pure function of [RuleInput]. Equal inputs produce equal outputs, on every device, in every
 *   build, forever.
 * - No clock. The evaluation time is passed in as [RuleInput.evaluatedAt]; implementations must not
 *   read a system clock, a timezone, or a locale.
 * - No randomness. No `Random`, no hash-order iteration, no `Set` iteration order dependence.
 * - No I/O. No database reads, no file reads, no network. Everything needed arrives in [RuleInput].
 *   The caller assembles the input; this engine only decides.
 * - No LLM. Not for ranking, not for wording, not for tie-breaking.
 * - Total ordering. [RuleEvaluation.rankedCandidates] is sorted by a total order with an explicit
 *   tie-break on a stable identifier, so two equally-scored candidates never swap between runs.
 * - Not suspending, because it must never need to wait for anything. If an implementation wants to
 *   suspend, it is reaching for I/O and has broken the contract.
 *
 * HEALTH SAFETY CONTRACT, per spec 15.1
 * - This engine never names a disease that is not present in [RuleInput.declaredConditions]. A lab
 *   value above its printed range produces a statement about the value and its range, never a
 *   diagnosis.
 * - It never emits a medication, a dosage or a treatment.
 * - Every [FiredRule] carries its [Evidence], so every downstream claim can be traced back to a
 *   specific number on a specific report or a condition the user declared themselves.
 * - Trigger sentences come from a fixed, reviewed catalogue of templates. They are not assembled
 *   ad hoc, so the safety language in spec 15.2 can be reviewed once per template rather than
 *   audited per output.
 *
 * FAILURE MODES
 * - No rule fires: returns an evaluation with empty [RuleEvaluation.firedRules] and a null
 *   [RuleEvaluation.trigger]. That is a valid result, not an error. The UI shows general guidance
 *   and says nothing has changed.
 * - No candidate survives the constraints: returns empty [RuleEvaluation.rankedCandidates] and a
 *   non-null trigger explaining why. The UI must say it has no suggestion rather than relaxing the
 *   constraints to find one.
 * - Conflicting constraints, e.g. a declared allergy that removes the only iron-rich candidate:
 *   the allergy always wins. Safety constraints are never relaxed to produce a suggestion.
 */
interface RulesEngine {

    /**
     * Evaluate the full profile state and produce the constraints, the ranked candidates and the
     * triggering sentence.
     *
     * @return never null; an empty evaluation is a valid outcome.
     */
    fun evaluate(input: RuleInput): RuleEvaluation

    /**
     * The catalogue this engine evaluates, exposed for tests and for the "why am I seeing this"
     * screen. Stable across a build; a rule's [RuleId] never changes meaning once shipped, because
     * stored suggestions reference it (see the `suggestions` table, spec 8.3).
     */
    val rules: List<RuleDescriptor>
}

/**
 * Everything the engine is allowed to see. Assembled by the orchestrator from the local store.
 *
 * Every field is a value type. Nothing here is a live query, a cursor or a lazy sequence, because
 * that would reintroduce I/O and destroy determinism.
 */
data class RuleInput(
    val profile: ProfileSnapshot,
    val declaredConditions: List<DeclaredCondition>,
    val labValues: List<LabValue>,
    /** The meal under consideration, or null when evaluating profile-level guidance. */
    val meal: MealSnapshot?,
    /** The constrained ingredient list for this profile's context (spec 4.3), already loaded. */
    val candidates: List<CandidateFood>,
    /**
     * Passed in, never read from a clock. Used for recency comparisons on lab reports, for example
     * preferring the most recent report for a test.
     *
     * EXCLUDED FROM [RuleEvaluation.inputDigest]. See that field for why.
     *
     * Note the consequence: a rule whose outcome depends on elapsed time, such as "this report is
     * more than a year old", makes the evaluation time-varying while the digest stays constant. Such
     * a rule must derive its comparison from a date IN THE DATA, for example the report date against
     * another report date, rather than from the wall clock. If a genuinely clock-dependent rule is
     * ever needed, it goes to the integrator, because it changes what the digest can prove.
     */
    val evaluatedAt: Instant,
)

/** The result. Immutable, comparable, and storable: it is what the `suggestions` table records. */
data class RuleEvaluation(
    val firedRules: List<FiredRule>,
    val constraints: List<Constraint>,
    val rankedCandidates: List<RankedCandidate>,
    /**
     * The human-readable sentence that explains what changed and why, or null if nothing fired.
     *
     * This is rule output, not generation. It is what the UI shows at beat 4 above the suggestions,
     * and it is what makes the change attributable.
     */
    val trigger: TriggerStatement?,
    /**
     * A stable digest of the SEMANTICALLY RELEVANT parts of [RuleInput]. Two evaluations with the
     * same digest must have produced the same output; a differing digest is the proof that the
     * input, not the model, changed the advice. Stored alongside the suggestion so the before and
     * after at beat 4 is verifiable rather than asserted.
     *
     * THE CLOCK IS EXCLUDED. [RuleInput.evaluatedAt] must NOT contribute to this digest. If it did,
     * every evaluation would differ trivially, every pair of stored suggestions would show a
     * different digest, and the digest would prove nothing at all, which is the opposite of why it
     * exists.
     *
     * COVERED: the profile snapshot, declared conditions, lab values, the meal snapshot, and the
     * candidate food codes, each serialised in a canonical order so that map and set iteration
     * order cannot change the result.
     *
     * NOT COVERED: [RuleInput.evaluatedAt], and anything else that moves without the user's data
     * having changed.
     *
     * REQUIRED TEST, not optional: evaluating an identical input at two different [RuleInput.evaluatedAt]
     * values produces an identical digest AND an identical [RuleEvaluation]. A build where that test
     * does not exist is a build where beat 4 cannot be defended.
     */
    val inputDigest: String,
) {
    /**
     * True when any fired rule is [Severity.ESCALATE]. The UI must then show [trigger], which is
     * the referral sentence, above anything else in the response, and TTS must speak it. `0015`.
     */
    val referralRequired: Boolean get() = firedRules.any { it.severity == Severity.ESCALATE }
}

/** A rule that fired, and the specific evidence that fired it. */
data class FiredRule(
    val id: RuleId,
    val evidence: Evidence,
    val severity: Severity,
)

/**
 * How strongly a fired rule should be acted on.
 *
 * [ESCALATE] means A REFERRAL IS MANDATORY IN THIS RESPONSE. A value far outside its printed range
 * produces "this is worth showing to a doctor", and that sentence is the trigger whatever else
 * fired, and the UI must show it.
 *
 * AMENDED BY `0015`. This used to read "escalate, do not handle": no suggestion at all alongside
 * a referral. That made the app useless at exactly the moment someone needed it most, and the
 * ruling is now that serious matters get a referral ALONGSIDE help, not instead of it. So an
 * escalation no longer suppresses the ranking adjustments the same value would have produced at
 * a milder level; it adds the referral on top and makes it non-optional. An implementation that
 * drops the referral when other rules fire has broken the safety contract; one that drops the
 * help has broken the product.
 */
enum class Severity { INFORM, ADJUST, ESCALATE }

/** A stable rule identifier. Referenced by stored suggestions, so it is append-only in practice. */
@JvmInline
value class RuleId(val value: String)

/** Documentation for one rule, for tests and for the explanation screen. */
data class RuleDescriptor(
    val id: RuleId,
    /** Plain-language statement of what the rule does. Reviewed against spec 15.2 language rules. */
    val summary: String,
    val severity: Severity,
)

/**
 * Why a rule fired. Every variant names a concrete, user-verifiable fact.
 *
 * Note what is absent: there is no `Inference` or `ModelSuggested` variant. If the engine cannot
 * point at a number the user can see on their own report, or something they typed themselves, it
 * does not fire.
 */
sealed interface Evidence {

    /**
     * A value on a lab report sits outside the reference range PRINTED ON THAT REPORT.
     *
     * The range comes from the report, never from a range the app carries, because reference ranges
     * differ between laboratories and assay methods. If the report shows no range, this evidence
     * cannot be constructed and no rule fires on that value.
     */
    data class LabValueOutsideRange(
        val testName: String,
        val value: Double,
        val unit: String,
        val referenceLow: Double?,
        val referenceHigh: Double?,
        val reportDate: LocalDate,
    ) : Evidence

    /** The user declared this condition themselves. The app never adds to this list on its own. */
    data class UserDeclaredCondition(val condition: DeclaredCondition) : Evidence

    /** The user's stated life context constrains what advice is usable (spec 4.2). */
    data class ProfileContext(val context: LifeContext) : Evidence

    /** Something about the logged meal itself, e.g. its dominant source of carbohydrate. */
    data class MealComposition(
        val nutrient: Nutrient,
        val shareOfMeal: Double,
        val dominantItem: String,
    ) : Evidence

    /** A pattern across the stored timeline, computed by code before evaluation, never by a model. */
    data class TimelinePattern(
        val description: String,
        val daysObserved: Int,
    ) : Evidence
}

/**
 * A restriction on what may be suggested. Constraints only ever remove or deprioritise candidates;
 * there is no constraint that adds one, so the candidate list can never grow beyond the bundled,
 * context-appropriate set and the model can never be handed a food that is not available to this
 * user.
 */
sealed interface Constraint {
    val sourceRule: RuleId

    /** Exclude outright. Allergies, declared avoidances, diet type. Never relaxed. */
    data class Exclude(
        override val sourceRule: RuleId,
        val foodCodes: Set<String>,
        val reason: String,
    ) : Constraint

    /** Prefer candidates that are higher or lower in a nutrient. */
    data class PreferNutrient(
        override val sourceRule: RuleId,
        val nutrient: Nutrient,
        val direction: Direction,
        val weight: Int,
    ) : Constraint {
        enum class Direction { HIGHER, LOWER }
    }

    /** Restrict to what this life context can actually obtain and cook (spec 4.2, 4.3). */
    data class RestrictToContext(
        override val sourceRule: RuleId,
        val context: LifeContext,
    ) : Constraint
}

/**
 * A food that may be suggested: from the bundled context list, never invented.
 *
 * RANKED PER SERVING, NEVER PER 100 g. Ruled 20 Sep after the first end-to-end run: ranked per
 * 100 g, the top four foods for a person with low haemoglobin were cumin, turmeric, bay leaf
 * and fenugreek, because a spice is iron-rich per 100 g and nobody eats 100 g of cumin. The
 * wrong denominator, not a missing exclusion list. [servingGrams] is the serving the database
 * actually models for this food (a recipe's yield over its servings; a household unit of the
 * food's class otherwise), and the engine ranks nutrient per THAT.
 */
data class CandidateFood(
    val foodCode: String,
    val displayName: String,
    val contexts: Set<LifeContext>,
    val nutrientsPer100g: Map<Nutrient, Double>,
    /** The serving the database models for this food, in grams. What one helping weighs. */
    val servingGrams: Double = 100.0,
)

/** A candidate after filtering and ranking. */
data class RankedCandidate(
    val candidate: CandidateFood,
    /**
     * Ranking score. Internal only. This number must never reach the UI, because it is an ordering
     * device and not a measurement of anything a user would recognise.
     */
    val score: Int,
    /** Which constraints contributed, so the suggestion can explain itself (spec 15.3). */
    val appliedConstraints: List<Constraint>,
    val confidence: Confidence,
)

/**
 * The sentence shown at beat 4.
 *
 * CONTRACT
 * - [text] is produced by filling a reviewed template with values taken from [evidence]. No model
 *   writes it and no model rewrites it.
 * - It states what the data says. It does not name a disease, recommend a treatment, or instruct.
 * - Example shape: "Your report from 12 September shows fasting glucose at 142 mg/dL, above the
 *   115 printed on it, so carbohydrate-heavy swaps are ranked differently now."
 */
data class TriggerStatement(
    val ruleId: RuleId,
    /** WHICH sentence. The words live in the string table, in the user's language, not here. */
    val template: TriggerTemplate,
    /** The arguments. Every number and date the sentence cites comes from this and nowhere else. */
    val evidence: Evidence,
)

/**
 * The eight sentences the rules engine can ask for, by identity rather than by wording.
 *
 * WHY AN ENUM AND NOT A STRING. The engine is pure and knows no locale, and the sentences it
 * chooses are the health sentences, the ones that most need a fluent Telugu reviewer. English
 * inside the engine could never be localised at all. So the engine says which sentence and hands
 * over the evidence; [TriggerText] puts the words around it from the string table.
 *
 * One string-table key per constant, `trigger_<name lowercased>`, the same rule `0017` applies to
 * [io.github.vedant7007.katori.domain.model.ConfidenceReason]. A constant with no key is a bug.
 *
 * Changing what a template MEANS is a health-safety change and goes through review. The wording
 * in each language is reviewed by a speaker of that language; this list is reviewed here.
 */
enum class TriggerTemplate {
    /** Far outside the printed range, above it. Referral is mandatory alongside whatever else. */
    ESCALATE_ABOVE_RANGE,
    /** Far outside the printed range, below it. */
    ESCALATE_BELOW_RANGE,
    /** Above the printed range; ranking adjusted. */
    LAB_ABOVE_RANGE,
    /** Below the printed range; ranking adjusted. */
    LAB_BELOW_RANGE,
    /** The user declared a condition themselves, so it may be named back to them. */
    DECLARED_CONDITION,
    /** One item dominates a nutrient in this meal. */
    MEAL_COMPOSITION,
    /** Suggestions limited by what this life context can obtain and cook. */
    LIFE_CONTEXT,
    /** A pattern across days. */
    TIMELINE,
}

// --- supporting value types -------------------------------------------------------------------

data class ProfileSnapshot(
    val ageYears: Int?,
    val weightKg: Double?,
    val heightCm: Double?,
    val sex: Sex?,
    val goal: Goal?,
    val context: LifeContext?,
    val dietType: DietType?,
    val avoidedFoodCodes: Set<String>,
)

enum class Sex { FEMALE, MALE, OTHER, NOT_STATED }
enum class Goal { MAINTAIN, LOSE_WEIGHT, GAIN_WEIGHT, BUILD_MUSCLE, NOT_STATED }
enum class DietType { VEGETARIAN, NON_VEGETARIAN, EGGETARIAN, JAIN, VEGAN }

/** Spec 4.2. The user declares this; the app never infers it. */
enum class LifeContext { HOSTEL_STUDENT, PG_OWN_COOKING, FIELD_OR_MANUAL_WORKER, DESK_PROFESSIONAL, HOMEMAKER }

/**
 * A condition the user declared, or one derived from a report they scanned.
 *
 * [source] matters for safety: the app may act on a report-derived flag, but it must never present
 * one as a diagnosis, and the wording differs between the two cases (spec 15.2).
 */
data class DeclaredCondition(
    val name: String,
    val source: ConditionSource,
)

enum class ConditionSource { USER_DECLARED, REPORT_DERIVED }

data class LabValue(
    val testName: String,
    val value: Double,
    val unit: String,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    val reportDate: LocalDate,
)

data class MealSnapshot(
    val mealId: Long,
    val items: List<MealItemSnapshot>,
    val loggedAt: Instant,
)

data class MealItemSnapshot(
    val displayName: String,
    val foodCode: String?,
    val grams: Double?,
    val nutrients: Map<Nutrient, Double>,
)
