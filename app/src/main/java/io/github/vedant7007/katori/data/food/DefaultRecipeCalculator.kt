package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientProfile
import io.github.vedant7007.katori.domain.model.NutrientTotal
import io.github.vedant7007.katori.domain.model.NutrientValue

/**
 * Portion nutrition from a recipe, as plain arithmetic.
 *
 * WHY THIS EXISTS SEPARATELY FROM THE SHIPPED TABLE. The bundled database already holds a
 * per-100 g figure for every authored recipe, and [SqliteFoodLookup.nutrientsFor] reads it. That
 * path is fast and it is what the demo uses. But the moment the user edits a recipe, which spec
 * 13.2 requires them to be able to do, the shipped figure is wrong and there is nothing to read.
 * This calculator is the path for their version, and it is deliberately the same arithmetic, so
 * the two can be cross-checked against each other. A test does exactly that: if the shipped table
 * ever drifts from what the ingredient rows actually add up to, the test fails.
 *
 * THE NUMBER IS NEVER INVENTED. Every input is either a USDA value or a weight the user typed.
 * No model is consulted, no gap is filled, and an ingredient with no value for a nutrient makes
 * the total a FLOOR rather than a total. See [NutrientTotal].
 *
 * CONTRACT
 * - Pure. No clock, no I/O, no randomness. Same inputs, same output, on every device.
 * - [ingredientProfiles] are PER 100 g of each ingredient. Obtain them with
 *   `lookup.nutrientsFor(code, 100.0)` and nothing else; passing a profile for some other weight
 *   silently scales the whole dish.
 * - An ingredient missing from the map is treated as [NutrientValue.Unknown] for every nutrient
 *   and named in [NutrientTotal.unknownContributors]. It is never skipped quietly, because a
 *   skipped ingredient is an undercount that looks like a total.
 * - [NutrientValue.AssumedZero] contributes zero and does NOT make the total partial. That
 *   distinction is the entire reason the three-state type exists.
 */
class DefaultRecipeCalculator : RecipeCalculator {

    override fun nutrientsForServing(
        recipe: ReferenceRecipe,
        ingredientProfiles: Map<FoodCode, NutrientProfile>,
        servings: Double,
    ): Map<Nutrient, NutrientTotal> {
        require(servings > 0.0 && servings.isFinite()) { "servings must be positive and finite, was $servings" }
        require(recipe.servings > 0.0) { "recipe ${recipe.code.id} states ${recipe.servings} servings" }

        // The share of the whole batch this person ate.
        val share = servings / recipe.servings

        return Nutrient.entries.associateWith { nutrient ->
            var amount = 0.0
            var known = 0
            val unknown = mutableListOf<String>()

            for (ingredient in recipe.ingredients) {
                val profile = ingredientProfiles[ingredient.code]
                when (val v = profile?.get(nutrient) ?: NutrientValue.Unknown) {
                    is NutrientValue.Measured -> {
                        amount += v.amount * (ingredient.grams / 100.0)
                        known++
                    }
                    NutrientValue.AssumedZero -> known++
                    NutrientValue.Unknown -> unknown += ingredient.displayName
                }
            }

            NutrientTotal(
                nutrient = nutrient,
                amount = if (known == 0) 0.0 else amount * share,
                unit = nutrient.unit,
                completeness = when {
                    known == 0 -> Completeness.NONE
                    unknown.isEmpty() -> Completeness.COMPLETE
                    else -> Completeness.PARTIAL
                },
                // Sorted and deduplicated so two runs over the same recipe compare equal.
                unknownContributors = unknown.distinct().sorted(),
            )
        }
    }
}
