package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.MealItemSnapshot
import io.github.vedant7007.katori.domain.MealResolver
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.ResolvedItem
import io.github.vedant7007.katori.domain.ResolvedMeal
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientProfile
import io.github.vedant7007.katori.domain.model.NutrientTotal
import io.github.vedant7007.katori.domain.model.NutrientValue
import io.github.vedant7007.katori.domain.model.NutritionFigure
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason

/**
 * [MealResolver] over [FoodLookup]: each spoken item to a food, a weight and a nutrient profile,
 * then the per-nutrient totals with their completeness.
 *
 * WHAT IT REFUSES, per the lookup's own contract. An item nothing matches is NO_MATCH for the
 * whole plate, and the orchestrator asks; the answer is never the nearest food. An item the
 * database deliberately holds no figures for (ragi, jaggery, curry leaves: KNOWN_ITEM_NO_DATA)
 * is NOT a refusal: it is kept on the plate with every nutrient Unknown, which makes each total a
 * floor naming it. That is the honest record of a meal with ragi in it; dropping the item or
 * failing the plate would both be wrong.
 *
 * QUANTITY IS NEVER INVENTED SILENTLY. A stated quantity in a household unit goes through
 * [FoodLookup.resolveUnit], which says whether a shipped default conversion was used. No
 * quantity at all is ONE of the item's usual unit and carries QUANTITY_INFERRED, which caps the
 * figure at Rough and, by spec 13.4, must be shown as a correctable value. A unit the class has
 * no conversion for leaves the item's weight unknown, and its nutrients with it.
 *
 * AN UNSTATED AMOUNT IS NEVER QUANTITY_STATED, ruled by Vedant on 20 September from Beat 1:
 * "थोड़ी दाल", a little dal, reached here as `1.0` with no unit (the model wrote the number),
 * and left as one stated piece of the dal recipe at full confidence, which is an invented amount
 * presented as the person's own. Now: a number with no unit is a stated amount only when the
 * dish is COUNTED, a roti, a vada, an idli, an egg ("two rotis" says how much); for anything
 * served, a gravy, a rice dish, a drink, a plain food, the unit is ours, the item carries
 * QUANTITY_INFERRED and HOUSEHOLD_UNIT_DEFAULT, and the assumed quantity and unit are written
 * back onto the parsed item so the plate can show them ("dal · 1 katori · taken as 180 g") for
 * the person to correct. The model's half, not writing a number it was not given, is the
 * integrator's; this half holds whichever way the model behaves.
 *
 * ponytail: one household unit per class is assumed when none was spoken (katori for cooked
 * pulses, grains, vegetables and dairy; piece for a dish; glass for a drink). The per-food
 * usual portion in the USDA portions table is the upgrade when a figure is visibly off.
 */
class LookupMealResolver(private val lookup: FoodLookup) : MealResolver {

    override suspend fun resolve(parsed: ParsedMeal, languageTag: String): Outcome<ResolvedMeal> {
        if (parsed.items.isEmpty()) {
            return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "no items to resolve")
        }
        val items = mutableListOf<ResolvedItem>()
        val parsedItems = mutableListOf<ParsedItem>()
        for (item in parsed.items) {
            when (val r = one(item, languageTag)) {
                is Outcome.Ok -> {
                    val (resolved, assumed) = r.value
                    items += resolved
                    // The parsed item comes back with what was ASSUMED filled in beside what was
                    // said, so the plate can show "1 katori" and the band says it was ours.
                    parsedItems += item.copy(
                        quantity = assumed.quantity,
                        unit = assumed.unit,
                        matchedFoodCode = resolved.snapshot.foodCode,
                        confidence = resolved.confidence,
                    )
                }
                is Outcome.Unavailable -> return r
                is Outcome.NotImplemented -> return r
            }
        }
        return Outcome.Ok(
            ResolvedMeal(
                parsed = parsed.copy(
                    items = parsedItems,
                    confidence = ConfidenceRules.combine(items.map { it.confidence }),
                ),
                items = items,
                figures = totals(items),
            )
        )
    }

    /** The quantity and unit the plate shows: what was said, or what was assumed in its place. */
    private data class Assumed(val quantity: Double?, val unit: String?)

    private suspend fun one(item: ParsedItem, languageTag: String): Outcome<Pair<ResolvedItem, Assumed>> {
        val match = when (val m = lookup.resolve(FoodQuery(item.spokenName, languageTag))) {
            is Outcome.Ok -> m.value
            is Outcome.Unavailable -> return if (m.reason == UnavailableReason.KNOWN_ITEM_NO_DATA) {
                // Recognised, and we hold nothing for it. On the plate, every nutrient Unknown.
                Outcome.Ok(
                    ResolvedItem(
                        snapshot = MealItemSnapshot(item.spokenName, foodCode = null, grams = null, nutrients = emptyMap()),
                        source = null,
                        nutrients = NutrientProfile(emptyMap()),
                        confidence = ConfidenceRules.of(item.confidence.reasons + ConfidenceReason.EXACT_FOOD_MATCH),
                    ) to Assumed(item.quantity, item.unit)
                )
            } else {
                Outcome.Unavailable(m.reason, m.detail ?: item.spokenName)
            }
            is Outcome.NotImplemented -> return m
        }

        val reasons = (item.confidence.reasons + match.confidence.reasons).toMutableList()
        val weighed = weigh(item, match, reasons)
        val grams: Double? = weighed.grams
        val profile: NutrientProfile = if (grams == null) {
            NutrientProfile(emptyMap())
        } else {
            when (val n = lookup.nutrientsFor(match.code, grams)) {
                is Outcome.Ok -> n.value
                is Outcome.Unavailable -> return Outcome.Unavailable(n.reason, n.detail ?: match.displayName)
                is Outcome.NotImplemented -> return n
            }
        }
        return Outcome.Ok(
            ResolvedItem(
                snapshot = MealItemSnapshot(
                    displayName = match.displayName,
                    foodCode = match.code.id,
                    grams = grams,
                    nutrients = profile.values.mapNotNull { (k, v) ->
                        when (v) {
                            is NutrientValue.Measured -> k to v.amount
                            NutrientValue.AssumedZero -> k to 0.0
                            NutrientValue.Unknown -> null
                        }
                    }.toMap(),
                ),
                source = match.code.source,
                nutrients = profile,
                confidence = ConfidenceRules.of(reasons),
            ) to Assumed(weighed.quantity, weighed.unit)
        )
    }

    private data class Weighed(val grams: Double?, val quantity: Double, val unit: String)

    /** Grams for the item (null when the unit cannot be converted for this class), with the quantity and unit used. */
    private suspend fun weigh(item: ParsedItem, match: FoodMatch, reasons: MutableList<ConfidenceReason>): Weighed {
        val recipe = if (match.code.source == DataSource.AUTHORED_RECIPE) (lookup.recipe(match.code) as? Outcome.Ok)?.value else null
        // One serving of an authored dish is its yield over its servings: the only authored figure
        // there is for one roti, or for a bowl of the dal recipe.
        val serving = recipe?.takeIf { it.servings > 0.0 }?.let { it.totalYieldGrams / it.servings }
        // COUNTED or SERVED. A roti, a vada, an idli, an omelette, a dosa is counted, and so is a
        // plain food whose class unit is a piece (an egg, a piece of chicken): "two" says how much.
        // A gravy, a rice dish, a drink, a chutney, a katori of anything is served: "one dal"
        // says nothing about how much, and the amount is ours.
        val counted = if (recipe != null) recipe.moistureClass in COUNTED_CLASSES else match.foodClass == FoodClass.COMPOSED_DISH

        val quantity = item.quantity ?: 1.0.also { reasons += ConfidenceReason.QUANTITY_INFERRED }
        val unit = item.unit ?: run {
            if (!counted) {
                // AN UNSTATED AMOUNT IS NEVER QUANTITY_STATED (Vedant, 20 Sep): whatever number
                // stood beside the food, the unit is assumed, so the amount is.
                reasons -= ConfidenceReason.QUANTITY_STATED
                reasons += ConfidenceReason.QUANTITY_INFERRED
            }
            when {
                counted -> "piece"
                recipe != null -> SERVED_UNIT[recipe.moistureClass] ?: "katori"
                else -> usualUnit(match.foodClass)
            }
        }
        // A dish counted in pieces weighs what its recipe says one serving weighs. A dish in a
        // household unit the class table does not carry ("a katori of dal") is ALSO one serving
        // per unit, marked as a shipped default: the recipe's serving is the only figure there is.
        if (serving != null && FoodTextMatching.normalise(unit) in PIECE_WORDS) {
            // "two rotis" is a stated count; a bare "roti" is one assumed piece and stays inferred.
            if (item.quantity != null) reasons += ConfidenceReason.QUANTITY_STATED
            return Weighed(quantity * serving, quantity, unit)
        }
        val grams = when (val w = lookup.resolveUnit(unit, match.foodClass)) {
            is Outcome.Ok -> {
                // A conversion of a unit nobody spoke is a default by nature, whatever the table says of the unit.
                reasons += if (w.value.isDefaultConversion || item.unit == null) ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT else ConfidenceReason.QUANTITY_STATED
                quantity * w.value.grams
            }
            is Outcome.Unavailable, is Outcome.NotImplemented -> if (serving != null) {
                reasons += ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT
                quantity * serving
            } else {
                // No conversion for this unit in this class: the weight is unknown, not guessed.
                reasons += ConfidenceReason.QUANTITY_INFERRED
                null
            }
        }
        return Weighed(grams, quantity, unit)
    }

    private companion object {
        val PIECE_WORDS = setOf("piece", "pieces", "pc", "no", "nos", "number", "serving", "servings", "plate", "plates")

        /** The importer's moisture classes whose dishes are counted: a number beside them is the amount. */
        val COUNTED_CLASSES = setOf("GRIDDLE_BREAD", "DEEP_FRIED", "STEAMED", "EGG", "MIXED_PLATE")

        /** The household word for a served dish when none was spoken; the grams are the recipe's serving either way. */
        val SERVED_UNIT = mapOf(
            "GRAVY" to "katori", "DRY_FRY" to "katori", "RAW_SALAD" to "katori", "SOFT_GRAIN" to "plate",
            "THIN_SOUP" to "cup", "THIN_DRINK" to "glass", "CHUTNEY" to "spoon",
        )
    }

    private fun usualUnit(foodClass: FoodClass): String = when (foodClass) {
        FoodClass.COMPOSED_DISH -> "piece"
        FoodClass.BEVERAGE -> "glass"
        FoodClass.FAT_OIL, FoodClass.SPICE -> "teaspoon"
        FoodClass.GRAIN_RAW, FoodClass.PULSE_RAW -> "cup"
        else -> "katori"
    }

    /**
     * Per-nutrient totals over the items. The three states sum as [NutrientTotal]'s contract
     * says: Measured and AssumedZero add, Unknown makes the figure a floor and is named, and a
     * nutrient with no measured contributor at all is [Completeness.NONE], never a zero. An item
     * whose weight is unknown contributes Unknown to every nutrient.
     */
    private fun totals(items: List<ResolvedItem>): List<NutritionFigure> {
        val confidence = ConfidenceRules.combine(items.map { it.confidence })
        val sources = items.mapNotNull { it.source }.distinct()
        return Nutrient.entries.map { nutrient ->
            var sum = 0.0
            var measured = 0
            val unknown = mutableListOf<String>()
            for (item in items) {
                when (val v = item.nutrients[nutrient]) {
                    is NutrientValue.Measured -> { sum += v.amount; measured++ }
                    NutrientValue.AssumedZero -> Unit
                    NutrientValue.Unknown -> unknown += item.snapshot.displayName
                }
            }
            val completeness = when {
                measured == 0 && unknown.isNotEmpty() -> Completeness.NONE
                unknown.isNotEmpty() -> Completeness.PARTIAL
                else -> Completeness.COMPLETE
            }
            NutritionFigure(
                total = NutrientTotal(nutrient, sum, nutrient.unit, completeness, unknown),
                confidence = confidence,
                sources = sources,
            )
        }
    }
}
