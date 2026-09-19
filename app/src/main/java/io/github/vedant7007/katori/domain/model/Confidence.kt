package io.github.vedant7007.katori.domain.model

/**
 * Qualitative confidence, per spec 15.1.1.
 *
 * CONTRACT
 * - Confidence is THREE BANDS. There is no numeric confidence anywhere in this type, and no
 *   percentage is ever shown to the user, because no defensible formula exists for composing
 *   ASR confidence, name-match confidence, unit-conversion error and recipe variance into one
 *   number. Inventing such a number to fill the UI is the fabrication failure that spec 11.5
 *   exists to prevent.
 * - The band is COMPUTED, not chosen. Construct only through [ConfidenceRules.of]; the
 *   constructor is not to be called directly outside tests.
 * - Every figure shown to the user carries its band AND its reasons, available on tap
 *   (spec 15.3). A figure with no band is a bug.
 *
 * ORDERING NOTE
 * Declaration order is best to worst, so [ConfidenceBand] compares naturally as
 * GOOD < APPROXIMATE < ROUGH, and "the worst band wins" is `maxOf`. Do not reorder these
 * constants; [ConfidenceRules] depends on the order.
 */
enum class ConfidenceBand {
    /** Exact match, explicit quantity, convertible unit, strong recognition. */
    GOOD,

    /** Fuzzy match, a default unit conversion, or a reference recipe. */
    APPROXIMATE,

    /** Uncorrected camera guess, inferred quantity, or a category-level match only. */
    ROUGH,
}

/**
 * A reason contributing to a confidence band. Each reason carries a CEILING: the best band a
 * figure can reach while that reason applies.
 *
 * To add a reason, add it here with its ceiling and add a user-facing sentence for it in the UI
 * string table. A reason with no sentence is a bug, because spec 15.1.1 requires the reason to be
 * visible, not just the band.
 */
enum class ConfidenceReason(val ceiling: ConfidenceBand) {

    // --- matching ---
    /** Resolved to one food code with an exact name match. */
    EXACT_FOOD_MATCH(ConfidenceBand.GOOD),

    /** Resolved by fuzzy or multilingual name matching. */
    FUZZY_FOOD_MATCH(ConfidenceBand.APPROXIMATE),

    /** Resolved only to a food category, not a specific food. */
    CATEGORY_LEVEL_MATCH(ConfidenceBand.ROUGH),

    // --- quantity ---
    /** The user stated the quantity explicitly in a unit we can convert. */
    QUANTITY_STATED(ConfidenceBand.GOOD),

    /** The user gave a household unit and we applied a shipped default conversion. */
    HOUSEHOLD_UNIT_DEFAULT(ConfidenceBand.APPROXIMATE),

    /**
     * The quantity was not stated and was inferred. Spec 13.4 forbids silently guessing quantity,
     * so this reason may only be used alongside a visible, correctable inference in the UI.
     */
    QUANTITY_INFERRED(ConfidenceBand.ROUGH),

    // --- composition ---
    /**
     * Values come from a reference recipe the team authored. Always Approximate by ruling: it is a
     * stated composition, shown and editable, not a measurement of this user's cooking.
     */
    AUTHORED_REFERENCE_RECIPE(ConfidenceBand.APPROXIMATE),

    /**
     * The user edited the recipe to match how they cook. Still approximate, since their amounts are
     * estimates too, but strictly better than the shipped default.
     */
    USER_EDITED_RECIPE(ConfidenceBand.APPROXIMATE),

    // --- source record quality, per the USDA FoodData Central sourcing spike ---
    /** The underlying source record is label-derived or rests on very few analytical samples. */
    WEAK_SOURCE_RECORD(ConfidenceBand.APPROXIMATE),

    /** The source food is a near-substitute rather than the same food, e.g. a different species. */
    SUBSTITUTE_FOOD_RECORD(ConfidenceBand.ROUGH),

    // --- input quality ---
    /** Speech recognition reported low confidence for this utterance. */
    LOW_ASR_CONFIDENCE(ConfidenceBand.ROUGH),

    /** A camera first guess that voice has not yet corrected (spec 12.4). */
    UNCORRECTED_CAMERA_GUESS(ConfidenceBand.ROUGH),
}

/**
 * A band together with every reason that produced it.
 *
 * Immutable and value-comparable, so two runs over the same inputs produce equal values. Reasons
 * are deduplicated and stored in declaration order, so equality does not depend on the order in
 * which callers happened to collect them.
 */
data class Confidence internal constructor(
    val band: ConfidenceBand,
    val reasons: List<ConfidenceReason>,
)

/**
 * The deterministic banding rule. This is the whole of it, deliberately.
 *
 * RULE: the worst ceiling wins. A figure is only as good as its weakest link. A GOOD food match
 * with an inferred quantity is ROUGH, because the number on screen is wrong by whatever the
 * quantity is wrong by.
 *
 * CONTRACT
 * - Pure. Same reasons in, same band out, on every device and in every build.
 * - Order-independent and duplicate-insensitive.
 * - An empty reason list is a programming error, not a GOOD figure. It throws, because silently
 *   claiming GOOD is the dangerous direction to fail in.
 */
object ConfidenceRules {

    fun bandFor(reasons: Collection<ConfidenceReason>): ConfidenceBand {
        require(reasons.isNotEmpty()) { "A confidence band needs at least one reason" }
        // Declaration order is best to worst, so the worst band is the maximum.
        return reasons.maxOf { it.ceiling }
    }

    fun of(vararg reasons: ConfidenceReason): Confidence = of(reasons.toList())

    fun of(reasons: Collection<ConfidenceReason>): Confidence {
        val normalised = reasons.distinct().sortedBy { it.ordinal }
        return Confidence(band = bandFor(normalised), reasons = normalised)
    }

    /** Combine confidences of contributing parts, e.g. every item in a meal. Worst still wins. */
    fun combine(parts: Collection<Confidence>): Confidence {
        require(parts.isNotEmpty()) { "Cannot combine an empty set of confidences" }
        return of(parts.flatMap { it.reasons })
    }
}
