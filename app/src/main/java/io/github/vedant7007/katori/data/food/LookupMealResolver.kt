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
 * [FoodLookup.resolveUnit], which says whether a shipped default conversion was used; a stated
 * quantity with no unit is taken as pieces for a dish and as one household unit otherwise; no
 * quantity at all is ONE of the item's usual unit and carries QUANTITY_INFERRED, which caps the
 * figure at Rough and, by spec 13.4, must be shown as a correctable value. A unit the class has
 * no conversion for leaves the item's weight unknown, and its nutrients with it.
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
                    items += r.value
                    parsedItems += item.copy(
                        matchedFoodCode = r.value.snapshot.foodCode,
                        confidence = r.value.confidence,
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

    private suspend fun one(item: ParsedItem, languageTag: String): Outcome<ResolvedItem> {
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
                    )
                )
            } else {
                Outcome.Unavailable(m.reason, m.detail ?: item.spokenName)
            }
            is Outcome.NotImplemented -> return m
        }

        val reasons = (item.confidence.reasons + match.confidence.reasons).toMutableList()
        val grams: Double? = weigh(item, match, reasons)
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
            )
        )
    }

    /** Grams for the item, or null when the unit cannot be converted for this class. */
    private suspend fun weigh(item: ParsedItem, match: FoodMatch, reasons: MutableList<ConfidenceReason>): Double? {
        val quantity = item.quantity ?: 1.0.also { reasons += ConfidenceReason.QUANTITY_INFERRED }
        val unit = item.unit ?: usualUnit(match.foodClass).also {
            // "two dal" has a quantity but no unit; the katori is ours, and it is shown as such.
            if (item.quantity != null && match.foodClass != FoodClass.COMPOSED_DISH) {
                reasons += ConfidenceReason.QUANTITY_INFERRED
            }
        }
        // A dish counted in pieces weighs what its authored recipe says one serving weighs: one
        // roti is the roti recipe's yield over its servings, not the class-wide 50 g default.
        // A dish in a household unit the class table does not carry ("a katori of dal", where
        // dal is the tadka recipe) is ALSO one serving per unit, marked as a shipped default:
        // the recipe's serving is the only authored figure there is for a bowl of that dish.
        val serving = if (match.code.source == DataSource.AUTHORED_RECIPE) {
            (lookup.recipe(match.code) as? Outcome.Ok)?.value?.takeIf { it.servings > 0.0 }?.let { it.totalYieldGrams / it.servings }
        } else {
            null
        }
        if (serving != null && FoodTextMatching.normalise(unit) in PIECE_WORDS) {
            reasons += ConfidenceReason.QUANTITY_STATED
            return quantity * serving
        }
        return when (val w = lookup.resolveUnit(unit, match.foodClass)) {
            is Outcome.Ok -> {
                reasons += if (w.value.isDefaultConversion) ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT else ConfidenceReason.QUANTITY_STATED
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
    }

    private companion object {
        val PIECE_WORDS = setOf("piece", "pieces", "pc", "no", "nos", "number", "serving", "servings", "plate", "plates")
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
