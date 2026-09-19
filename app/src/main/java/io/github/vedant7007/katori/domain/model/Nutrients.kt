package io.github.vedant7007.katori.domain.model

/**
 * Nutrient identity. Only what the app actually shows (spec 5.2), plus what the rules engine
 * reasons over. Adding one means adding it to the bundled database build too.
 */
enum class Nutrient(val unit: NutrientUnit) {
    ENERGY(NutrientUnit.KCAL),
    PROTEIN(NutrientUnit.GRAM),
    CARBOHYDRATE(NutrientUnit.GRAM),
    FAT(NutrientUnit.GRAM),
    FIBRE(NutrientUnit.GRAM),
    IRON(NutrientUnit.MILLIGRAM),
    VITAMIN_B12(NutrientUnit.MICROGRAM),
    SODIUM(NutrientUnit.MILLIGRAM),
}

enum class NutrientUnit { KCAL, GRAM, MILLIGRAM, MICROGRAM }

/**
 * The value of one nutrient in one food.
 *
 * THIS TYPE IS THE MOST IMPORTANT ONE IN THE DATA LAYER. Read the contract before using it.
 *
 * CONTRACT
 * Source databases distinguish three states, and so must we:
 *
 *  - [Measured]     the source reports a value.
 *  - [AssumedZero]  the source states this nutrient is genuinely absent from this food. This is
 *                   how B12 is recorded on plant foods. It is trustworthy and sums as zero.
 *  - [Unknown]      the source has no value for this nutrient in this food. USDA states plainly
 *                   that a missing value "does not indicate that the value is zero; it means that
 *                   no value for that nutrient in that food or food product is available".
 *
 * NEVER collapse [Unknown] to zero. Not on import, not in a sum, not in the UI. Doing so converts
 * "we do not know" into "there is none" across thousands of cells and then adds them into a daily
 * total that looks authoritative. For B12 in an app used by vegetarians, that is the single most
 * consequential bug available to this project.
 *
 * There is deliberately no `getOrZero()` helper and no default value. If you want a number you
 * must handle [Unknown] explicitly, at the call site, where the decision is visible.
 */
sealed interface NutrientValue {

    data class Measured(val amount: Double, val unit: NutrientUnit) : NutrientValue {
        init {
            require(amount.isFinite()) { "Nutrient amount must be finite" }
            require(amount >= 0.0) { "Nutrient amount must not be negative" }
        }
    }

    /** The source states this nutrient is genuinely absent from this food. Sums as zero. */
    data object AssumedZero : NutrientValue

    /** The source holds no value. Renders as "not known", never as 0. */
    data object Unknown : NutrientValue
}

/**
 * Every nutrient for one food or one portion. A nutrient absent from the map is [NutrientValue.Unknown];
 * callers must treat a missing key and an explicit Unknown identically.
 */
data class NutrientProfile(
    val values: Map<Nutrient, NutrientValue>,
) {
    operator fun get(nutrient: Nutrient): NutrientValue =
        values[nutrient] ?: NutrientValue.Unknown
}

/**
 * The result of summing one nutrient across several contributors.
 *
 * CONTRACT
 * - [amount] is the sum of the [NutrientValue.Measured] and [NutrientValue.AssumedZero] contributors only.
 * - If ANY contributor was [NutrientValue.Unknown], [completeness] is [Completeness.PARTIAL] and
 *   [unknownContributors] names them. The UI must then show the figure as a floor, worded as
 *   "at least", with the unknown items listed. It must not present a partial total as a total.
 * - A total whose contributors are ALL Unknown is not a zero. It is [Completeness.NONE] and the UI
 *   shows "not known".
 */
data class NutrientTotal(
    val nutrient: Nutrient,
    val amount: Double,
    val unit: NutrientUnit,
    val completeness: Completeness,
    val unknownContributors: List<String>,
)

enum class Completeness {
    /** Every contributor had a value. The total is a total. */
    COMPLETE,

    /** At least one contributor was Unknown. The figure is a floor, not a total. */
    PARTIAL,

    /** No contributor had a value. There is no figure to show. */
    NONE,
}

/**
 * A nutrition figure as presented, always paired with its confidence and its source.
 *
 * Spec 15.1 requires both: every figure carries a band, and every figure cites its source. This
 * type makes it impossible to carry one without the other.
 */
data class NutritionFigure(
    val total: NutrientTotal,
    val confidence: Confidence,
    val sources: List<DataSource>,
)

/**
 * Where a number came from. Shown to the user (spec 15.1, "always show the source") and used to
 * keep the data layer swappable: adding a licensed source later is a new constant here plus new
 * rows, not a change to any interface.
 */
enum class DataSource(val displayName: String, val attribution: String) {
    USDA_SR_LEGACY(
        displayName = "USDA SR Legacy",
        attribution = "U.S. Department of Agriculture, Agricultural Research Service. " +
            "FoodData Central, SR Legacy. fdc.nal.usda.gov",
    ),
    USDA_FOUNDATION(
        displayName = "USDA Foundation Foods",
        attribution = "U.S. Department of Agriculture, Agricultural Research Service. " +
            "FoodData Central, Foundation Foods. fdc.nal.usda.gov",
    ),
    OPEN_FOOD_FACTS(
        displayName = "Open Food Facts",
        attribution = "Open Food Facts contributors, made available under the Open Database License",
    ),

    /**
     * A reference recipe authored by this team: a stated composition, shown to the user and
     * editable by them, computed against public-domain ingredient values. Always carries
     * [ConfidenceReason.AUTHORED_REFERENCE_RECIPE].
     */
    AUTHORED_RECIPE(
        displayName = "Reference recipe",
        attribution = "Reference recipe composed by the app team from public-domain ingredient data",
    ),

    /** The user's own edit to a recipe or a quantity. Their number beats ours. */
    USER_PROVIDED(
        displayName = "Your correction",
        attribution = "Entered by you",
    ),
}
