package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.CandidateFood
import io.github.vedant7007.katori.domain.LifeContext
import io.github.vedant7007.katori.domain.model.Nutrient

/**
 * Spec 4.3: the constrained candidate list per life context, read from the `candidates`
 * table the importer builds from `data-authoring/candidates.csv`.
 *
 * THE RULE: a food or recipe that is not in the table is an INGREDIENT, not a candidate. The
 * engine never ranks it and the model never names it. Before this list every row of the
 * database was a candidate, and the ranked list for anaemia topped out at cumin, turmeric and
 * bay leaf (per 100 g), then at raw cowpea, raw urad and raw masoor (per serving, a raw pulse's
 * serving being a 200 g cup). "You should drink more water" as the answer to an iron question
 * ends the pitch more efficiently than a crash would. Raw grains and pulses are ingredients,
 * not candidates: ruled by Vedant, 20 September 2026.
 *
 * WHAT A ROW CARRIES. The contexts the food is AVAILABLE in (budget, kitchen access and
 * schedule, from the spec 4.2 table), the name a suggestion shows ("Boiled egg", not "Egg,
 * whole, raw") and, where the class's usual unit is wrong for a suggestion, what one helping
 * weighs (a handful of roasted peanuts is 30 g, not a spice-class teaspoon). Merit is the rules
 * engine's ranking; availability is the file.
 *
 * [allowed] is the caller's diet and avoidance filter over (key, description, class), applied
 * to a food and to every ingredient of a recipe, as `RoomUserContextSource` does today; the
 * default admits everything, which is what the JVM demo run uses for a profile with no diet.
 */
object CandidateCatalogue {

    fun load(
        foods: FoodDbSource,
        allowed: (key: String, description: String, foodClass: String?) -> Boolean = { _, _, _ -> true },
    ): List<CandidateFood> {
        data class Row(val contexts: MutableSet<LifeContext> = mutableSetOf(), var suggestAs: String? = null, var servingGrams: Double? = null)
        val listed = LinkedHashMap<String, Row>()
        for (r in foods.query("SELECT key, context, suggest_as, serving_g FROM candidates ORDER BY key, context")) {
            val ctx = LifeContext.entries.firstOrNull { it.name == r.str("context") } ?: continue
            val row = listed.getOrPut(r.str("key")) { Row() }
            row.contexts += ctx
            r.strOrNull("suggest_as")?.let { row.suggestAs = it }
            r.dblOrNull("serving_g")?.let { row.servingGrams = it }
        }
        if (listed.isEmpty()) return emptyList()

        fun nutrient(name: String) = Nutrient.entries.firstOrNull { it.name == name }
        // The usual serving per class, from the same table the resolver converts spoken units with.
        val servingByClass = foods.query("SELECT unit, food_class, grams FROM unit_conversions")
            .filter { it.str("unit") == USUAL_UNIT[it.str("food_class")] }
            .associate { it.str("food_class") to it.dbl("grams") }

        val foodNutrients = foods.query("SELECT food_key, nutrient, amount FROM food_nutrients WHERE state = 'MEASURED'")
            .groupBy({ it.str("food_key") }) { nutrient(it.str("nutrient"))?.let { n -> n to it.dbl("amount") } }
        val foodRows = foods.query("SELECT food_key, display_name, usda_description, food_class FROM foods")
            .filter { it.str("food_key") in listed && allowed(it.str("food_key"), it.str("display_name") + " " + it.str("usda_description"), it.str("food_class")) }
            .map { r ->
                val key = r.str("food_key"); val row = listed.getValue(key)
                CandidateFood(
                    key, row.suggestAs ?: r.str("display_name"), row.contexts, foodNutrients[key].orEmpty().filterNotNull().toMap(),
                    servingGrams = row.servingGrams ?: servingByClass[r.str("food_class")] ?: 100.0,
                )
            }

        val recipeNutrients = foods.query("SELECT recipe_key, nutrient, amount_per_100g FROM recipe_nutrients WHERE state = 'MEASURED'")
            .groupBy({ it.str("recipe_key") }) { nutrient(it.str("nutrient"))?.let { n -> n to it.dbl("amount_per_100g") } }
        // A recipe is forbidden when any ingredient is: chicken biryani has chicken in it.
        val recipeIngredients = foods.query(
            "SELECT ri.recipe_key AS recipe_key, f.display_name AS name, f.usda_description AS description, f.food_class AS food_class FROM recipe_ingredients ri JOIN foods f ON f.food_key = ri.food_key"
        ).groupBy({ it.str("recipe_key") }) { Triple(it.str("name"), it.str("description"), it.str("food_class")) }
        val recipeRows = foods.query("SELECT recipe_key, display_name, servings, yield_g FROM recipes")
            .filter { r ->
                val key = r.str("recipe_key")
                key in listed && allowed(key, r.str("display_name"), null) &&
                    recipeIngredients[key].orEmpty().all { (name, description, cls) -> allowed(key, "$name $description", cls) }
            }
            .map { r ->
                val key = r.str("recipe_key"); val row = listed.getValue(key)
                val servings = r.dbl("servings")
                CandidateFood(
                    key, row.suggestAs ?: r.str("display_name"), row.contexts, recipeNutrients[key].orEmpty().filterNotNull().toMap(),
                    servingGrams = row.servingGrams ?: if (servings > 0.0) r.dbl("yield_g") / servings else 100.0,
                )
            }
        return foodRows + recipeRows
    }

    /** The household unit a class is usually served in; the same defaults the resolver assumes. */
    internal val USUAL_UNIT = mapOf(
        "PULSE_COOKED" to "katori", "GRAIN_COOKED" to "katori", "VEGETABLE" to "katori", "DAIRY" to "katori",
        "BEVERAGE" to "glass", "FAT_OIL" to "teaspoon", "SPICE" to "teaspoon",
        "GRAIN_RAW" to "cup", "PULSE_RAW" to "cup", "COMPOSED_DISH" to "piece",
    )
}
