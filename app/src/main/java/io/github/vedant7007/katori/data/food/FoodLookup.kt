package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.model.Confidence
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientProfile
import io.github.vedant7007.katori.domain.model.Outcome

/**
 * Resolves what a person said into a food, and a food into nutrition.
 *
 * SOURCE-AGNOSTIC BY CONSTRUCTION
 * Which database sits underneath is a data question, not an interface question. Every food is
 * addressed by a [FoodCode] that names its [DataSource], so adding a licensed source later is new
 * rows plus a new [DataSource] constant, with no signature here changing. That property is what made
 * it possible to drop an entire planned data source without touching the architecture, and it is
 * worth protecting.
 *
 * TWO DATABASES, DELIBERATELY SEPARATE
 * - The BUNDLED database is read-only, ships in the APK, and holds foods, nutrients, portions,
 *   authored reference recipes and the default household-unit conversions. It is replaced wholesale
 *   by shipping a new file. It is NOT a Room database and has no migrations.
 * - The USER database is Room, writable, and holds the eleven tables in spec 8.3 including the
 *   user's own overrides. It migrates.
 * Different lifecycle, different migration story, different packaging. Mixing them means a data
 * refresh either destroys user data or cannot ship.
 *
 * CONTRACT
 * - [resolve] returns ONE match only when it is confident enough to act on. Otherwise it returns
 *   candidates and the caller asks. It never returns the best of a bad set as though it were right.
 * - A failed match returns NO_MATCH and the caller offers ingredient-level entry. It never returns a
 *   near-miss food silently; a wrong food is worse than no answer in a health app.
 * - Every unmatched utterance is recorded to `unmatched_utterances` so coverage gaps can be closed
 *   with evidence rather than guesswork (spec 13.5).
 * - Quantity is never invented. [resolveUnit] returns a conversion or it fails; there is no default
 *   portion size hiding in this layer.
 * - Nutrient values preserve the unknown-versus-zero distinction. See
 *   [io.github.vedant7007.katori.domain.model.NutrientValue]. This interface never returns a zero to mean "no data".
 *
 * FAILURE MODES
 * - Bundled database missing or the wrong schema version: INTERNAL_ERROR at startup, surfaced as a
 *   blocking error screen. The app does not run with a partial food database.
 * - Name matched but the food has no value for a requested nutrient: the profile carries Unknown for
 *   that nutrient. That is a successful lookup, not a failure.
 * - Household unit not resolvable for this food class: NO_MATCH from [resolveUnit], and the caller
 *   asks the user for grams or a different unit (spec 13.4).
 */
interface FoodLookup {

    /** Best single match, or Unavailable when nothing is confident enough to act on. */
    suspend fun resolve(query: FoodQuery): Outcome<FoodMatch>

    /** Ranked candidates for disambiguation. Stable order for equal scores. */
    suspend fun candidates(query: FoodQuery, limit: Int = 5): Outcome<List<FoodMatch>>

    /** Nutrition for a given weight of a given food. Grams in, per-portion values out. */
    suspend fun nutrientsFor(code: FoodCode, grams: Double): Outcome<NutrientProfile>

    /**
     * Household unit to grams, for this class of food.
     *
     * A katori of dal and a katori of rice are different weights, so the food class is required.
     * When a default conversion is used, the result says so and the figure is capped at APPROXIMATE
     * and shown to the user as a correctable value (spec 13.4).
     */
    suspend fun resolveUnit(unit: String, foodClass: FoodClass): Outcome<GramWeight>

    /**
     * The reference recipe for a composed dish, if one is authored.
     *
     * Reference recipes are a stated composition: written by the team, computed against
     * public-domain ingredient values, shown to the user and editable by them. They always carry
     * [io.github.vedant7007.katori.domain.model.ConfidenceReason.AUTHORED_REFERENCE_RECIPE] and therefore never rank
     * better than APPROXIMATE.
     */
    suspend fun recipe(code: FoodCode): Outcome<ReferenceRecipe>

    /** Records a miss, for closing coverage gaps against real utterances rather than assumptions. */
    suspend fun recordUnmatched(query: FoodQuery)
}

data class FoodQuery(
    /** The name as spoken or typed, in whatever language and spelling the user used. */
    val spokenName: String,
    val languageTag: String,
    val cookingMethod: String? = null,
)

data class FoodMatch(
    val code: FoodCode,
    val displayName: String,
    val foodClass: FoodClass,
    val matchKind: MatchKind,
    val confidence: Confidence,
    /**
     * What this US record carries that Indian production does not, in one sentence for the
     * screen beside the figures; null for the records the sweep found clean
     * (`data-authoring/us-record-sweep.csv`). White bread: its iron is enrichment, so no iron
     * figure is shown for it.
     */
    val disclosure: String? = null,
)

enum class MatchKind { EXACT, FUZZY, CATEGORY }

/**
 * Identifies a food within a source. The pair is the key; a bare id is meaningless, because two
 * sources reuse numbers.
 */
data class FoodCode(val source: DataSource, val id: String)

/** Coarse class, used for unit conversion and for category-level fallbacks. */
enum class FoodClass { GRAIN_COOKED, GRAIN_RAW, PULSE_COOKED, PULSE_RAW, VEGETABLE, FRUIT, DAIRY, FAT_OIL, SPICE, SWEET, BEVERAGE, SNACK_FRIED, COMPOSED_DISH, PACKAGED }

data class GramWeight(
    val grams: Double,
    /** True when this came from a shipped default rather than a user-stated weight. */
    val isDefaultConversion: Boolean,
    val source: DataSource,
)

/**
 * An authored reference recipe.
 *
 * ABSORBED OIL, NOT OIL USED. [IngredientAmount.grams] for a frying fat records what the dish
 * actually retains, not the volume of the frying bath. Counting the whole bath is the error that
 * made an existing public recipe database read 745 kcal per 100 g for a vada, and repeating it in
 * our own data would be our mistake rather than an inherited one.
 *
 * Every recipe ships with its reasoning recorded in the committed authoring file, not in code
 * comments, so a reviewer can check where each amount came from.
 */
data class ReferenceRecipe(
    val code: FoodCode,
    val displayName: String,
    /** Other names this dish goes by, across languages and spellings. Indexed for matching. */
    val aliases: List<String>,
    val ingredients: List<IngredientAmount>,
    val totalYieldGrams: Double,
    val servings: Double,
    /** Stable id of the row in the authoring file that documents how this recipe was derived. */
    val authoringNoteId: String,
    /**
     * The importer's moisture class (GRIDDLE_BREAD, GRAVY, SOFT_GRAIN, ...), which is also the
     * shape of the dish: a bread, a vada or an idli is COUNTED, a gravy or a rice dish is SERVED.
     * The resolver reads it to know whether "two rotis" states an amount and "one dal" does not.
     */
    val moistureClass: String = "",
)

data class IngredientAmount(
    val code: FoodCode,
    val displayName: String,
    val grams: Double,
    /** Set on a frying fat, to make clear this is retained rather than used. */
    val isAbsorbedFat: Boolean = false,
)

/** Computes portion nutrition from a recipe. Pure arithmetic, in code, never in a model. */
interface RecipeCalculator {
    fun nutrientsForServing(
        recipe: ReferenceRecipe,
        ingredientProfiles: Map<FoodCode, NutrientProfile>,
        servings: Double,
    ): Map<Nutrient, io.github.vedant7007.katori.domain.model.NutrientTotal>
}
