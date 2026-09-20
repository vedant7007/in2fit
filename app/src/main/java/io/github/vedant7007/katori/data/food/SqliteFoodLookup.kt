package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.model.Confidence
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientProfile
import io.github.vedant7007.katori.domain.model.NutrientUnit
import io.github.vedant7007.katori.domain.model.NutrientValue
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason

/**
 * [FoodLookup] over the bundled read-only database built by tools/build_food_db.py.
 *
 * Everything here is deliberately boring and refusable. It never reaches for a nearby answer when
 * the right one is missing, and it never turns an absent nutrient into a zero.
 */
class SqliteFoodLookup(
    private val db: FoodDbSource,
    private val unmatched: UnmatchedSink = UnmatchedSink.None,
) : FoodLookup {

    /** Where a failed match is recorded, so coverage gaps close against what people actually say. */
    interface UnmatchedSink {
        suspend fun record(query: FoodQuery)
        object None : UnmatchedSink {
            override suspend fun record(query: FoodQuery) = Unit
        }
    }

    private val aliasCache: Map<String, String> by lazy {
        db.query("SELECT alias_norm, food_key FROM food_aliases")
            .associate { it.str("alias_norm") to it.str("food_key") }
    }

    private val noDataAliasCache: Map<String, String> by lazy {
        db.query("SELECT alias_norm, item_key FROM no_data_aliases")
            .associate { it.str("alias_norm") to it.str("item_key") }
    }

    private val recipeAliasCache: Map<String, String> by lazy {
        db.query("SELECT alias_norm, recipe_key FROM recipe_aliases")
            .associate { it.str("alias_norm") to it.str("recipe_key") }
    }

    override suspend fun resolve(query: FoodQuery): Outcome<FoodMatch> {
        // A deliberately no-data item is checked FIRST. Otherwise a fuzzy match could pull "ragi"
        // onto some other grain, which is exactly the substitution that is forbidden.
        noDataMatch(query)?.let { return it }

        val dish = FoodTextMatching.match(query.spokenName, recipeAliasCache)
        val food = FoodTextMatching.match(query.spokenName, aliasCache)

        return pick(dish, food)?.let { Outcome.Ok(it) }
            ?: Outcome.Unavailable(UnavailableReason.NO_MATCH, query.spokenName)
    }

    override suspend fun candidates(query: FoodQuery, limit: Int): Outcome<List<FoodMatch>> {
        noDataMatch(query)?.let { return it }

        val q = FoodTextMatching.normalise(query.spokenName)
        if (q.isEmpty()) return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE)

        val scored = mutableListOf<Pair<Boolean, FoodTextMatching.Candidate>>()
        for ((alias, key) in recipeAliasCache) {
            FoodTextMatching.match(q, mapOf(alias to key))?.let { scored += true to it }
        }
        for ((alias, key) in aliasCache) {
            FoodTextMatching.match(q, mapOf(alias to key))?.let { scored += false to it }
        }
        val ordered = scored
            // Same ordering as resolve, and for the same reason: strength, then distance, then a
            // dish ahead of one of its own ingredients.
            .sortedWith(
                compareBy(
                    { it.second.strength.ordinal },
                    { it.second.distance },
                    { if (it.first) 0 else 1 },
                    { it.second.key },
                )
            )
            .distinctBy { it.first to it.second.key }
            .take(limit)

        if (ordered.isEmpty()) return Outcome.Unavailable(UnavailableReason.NO_MATCH, query.spokenName)
        return Outcome.Ok(ordered.map { (isDish, c) -> if (isDish) toRecipeMatch(c) else toFoodMatch(c) })
    }

    override suspend fun nutrientsFor(code: FoodCode, grams: Double): Outcome<NutrientProfile> {
        if (grams <= 0.0 || !grams.isFinite()) {
            return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "grams=$grams")
        }
        return if (code.source == DataSource.AUTHORED_RECIPE) {
            recipeNutrients(code, grams)
        } else {
            foodNutrients(code, grams)
        }
    }

    override suspend fun resolveUnit(unit: String, foodClass: FoodClass): Outcome<GramWeight> {
        val u = FoodTextMatching.normalise(unit)
        if (u.isEmpty()) return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE)

        // Grams are the unit the database is in, so there is nothing to convert.
        GRAM_SYNONYMS[u]?.let { return Outcome.Ok(GramWeight(it, false, DataSource.USER_PROVIDED)) }

        // Millilitres are converted at a density of 1.0, which is a SHIPPED DEFAULT and is
        // flagged as one: oil is about 0.92 g/ml, so a spoken volume of oil taken as grams is
        // ~8% off, and a figure that far off must not reach GOOD. The flag attaches
        // HOUSEHOLD_UNIT_DEFAULT downstream, capping it at APPROXIMATE and showing the assumed
        // grams for correction. A per-class density would need a sourced table; there is none.
        VOLUME_SYNONYMS[u]?.let { return Outcome.Ok(GramWeight(it, true, DataSource.USER_PROVIDED)) }

        val row = db.query(
            "SELECT grams FROM unit_conversions WHERE unit = ? AND food_class = ?",
            listOf(u, foodClass.name),
        ).firstOrNull()
            ?: return Outcome.Unavailable(
                UnavailableReason.NO_MATCH,
                "no conversion for '$u' in class ${foodClass.name}",
            )

        return Outcome.Ok(
            GramWeight(
                grams = row.dbl("grams"),
                isDefaultConversion = true,
                source = DataSource.AUTHORED_RECIPE,
            )
        )
    }

    /**
     * The authored reference recipe behind a composed dish.
     *
     * This is a STATED COMPOSITION, and returning it is the whole point: the user can see every
     * ingredient and every weight, and change them. A figure the user cannot inspect is a figure
     * they cannot correct.
     */
    override suspend fun recipe(code: FoodCode): Outcome<ReferenceRecipe> {
        if (code.source != DataSource.AUTHORED_RECIPE) {
            return Outcome.Unavailable(
                UnavailableReason.NO_MATCH,
                "${code.id} is not a reference recipe; its source is ${code.source.name}",
            )
        }
        val r = db.query(
            "SELECT display_name, servings, yield_g, moisture_class FROM recipes WHERE recipe_key = ?",
            listOf(code.id),
        ).firstOrNull()
            ?: return Outcome.Unavailable(UnavailableReason.NO_MATCH, "no reference recipe for ${code.id}")

        val ingredients = db.query(
            """
            SELECT ri.food_key, ri.grams, ri.role, f.display_name, f.source
            FROM recipe_ingredients ri
            JOIN foods f ON f.food_key = ri.food_key
            WHERE ri.recipe_key = ?
            """.trimIndent(),
            listOf(code.id),
        )
        // A recipe row with no ingredient rows means the import dropped them. That is a broken
        // build, not a dish made of nothing, and it must not present as an empty composition.
        if (ingredients.isEmpty()) {
            return Outcome.Unavailable(
                UnavailableReason.INTERNAL_ERROR,
                "reference recipe ${code.id} has no ingredient rows",
            )
        }

        val aliases = db.query(
            "SELECT alias FROM recipe_aliases WHERE recipe_key = ? ORDER BY alias",
            listOf(code.id),
        ).map { it.str("alias") }

        return Outcome.Ok(
            ReferenceRecipe(
                code = code,
                displayName = r.str("display_name"),
                aliases = aliases,
                ingredients = ingredients
                    .sortedWith(compareBy({ roleRank(it.str("role")) }, { -it.dbl("grams") }, { it.str("food_key") }))
                    .map { row ->
                        IngredientAmount(
                            code = FoodCode(DataSource.valueOf(row.str("source")), row.str("food_key")),
                            displayName = row.str("display_name"),
                            grams = row.dbl("grams"),
                            isAbsorbedFat = row.str("role") in RETAINED_FAT_ROLES,
                        )
                    },
                totalYieldGrams = r.dbl("yield_g"),
                servings = r.dbl("servings"),
                // The recipe key IS the row id in data-authoring/recipes.csv, where the absorbed
                // fraction and its reasoning are recorded for a reviewer to challenge.
                authoringNoteId = code.id,
                moistureClass = r.str("moisture_class"),
            )
        )
    }

    override suspend fun recordUnmatched(query: FoodQuery) = unmatched.record(query)

    // --- internals ------------------------------------------------------------------------

    /**
     * Chooses between the dish reading and the ingredient reading of one utterance.
     *
     * Both tables are searched and the STRONGER match wins. Neither order is safe on its own:
     * searching recipes first would let a fuzzy dish name beat an exact ingredient name, and
     * searching foods first would let "dal tadka" collapse onto plain dal, which is the
     * dish-to-ingredient substitution that produced the biryani-to-bay-leaf class of bug.
     *
     * An exact tie cannot happen at EXACT strength, because the import asserts no spoken name
     * means both a food and a dish. Where a tie does occur further down, the dish wins: a person
     * who says a dish name has named the whole thing, not one part of it.
     */
    private fun pick(
        dish: FoodTextMatching.Candidate?,
        food: FoodTextMatching.Candidate?,
    ): FoodMatch? = when {
        dish == null && food == null -> null
        food == null -> toRecipeMatch(dish!!)
        dish == null -> toFoodMatch(food)
        else -> {
            val cmp = compareValuesBy(dish, food, { it.strength.ordinal }, { it.distance })
            if (cmp <= 0) toRecipeMatch(dish) else toFoodMatch(food)
        }
    }

    private fun foodNutrients(code: FoodCode, grams: Double): Outcome<NutrientProfile> {
        val rows = db.query(
            "SELECT nutrient, state, amount, unit FROM food_nutrients WHERE food_key = ?",
            listOf(code.id),
        )
        if (rows.isEmpty()) {
            return Outcome.Unavailable(UnavailableReason.NO_MATCH, "no nutrients for ${code.id}")
        }
        return Outcome.Ok(scale(rows, "amount", grams))
    }

    /**
     * Nutrition for a weight of a finished dish, from the per-100 g figures computed at import.
     *
     * One unknown ingredient makes the whole dish unknown for that nutrient. Summing the rest and
     * presenting it as the dish's value would understate it silently, and a single NutrientValue
     * has no way to say "at least". The user-editable path in [DefaultRecipeCalculator] returns a
     * [io.github.vedant7007.katori.domain.model.NutrientTotal] instead, which CAN say so, and names
     * the ingredients responsible.
     */
    private fun recipeNutrients(code: FoodCode, grams: Double): Outcome<NutrientProfile> {
        val rows = db.query(
            "SELECT nutrient, state, amount_per_100g, unit FROM recipe_nutrients WHERE recipe_key = ?",
            listOf(code.id),
        )
        if (rows.isEmpty()) {
            return Outcome.Unavailable(UnavailableReason.NO_MATCH, "no nutrients for recipe ${code.id}")
        }
        return Outcome.Ok(scale(rows, "amount_per_100g", grams))
    }

    private fun scale(rows: List<Map<String, Any?>>, amountCol: String, grams: Double): NutrientProfile {
        val factor = grams / 100.0
        val values = mutableMapOf<Nutrient, NutrientValue>()
        for (r in rows) {
            val nutrient = runCatching { Nutrient.valueOf(r.str("nutrient")) }.getOrNull() ?: continue
            values[nutrient] = when (r.str("state")) {
                "MEASURED" -> NutrientValue.Measured(
                    amount = r.dbl(amountCol) * factor,
                    unit = NutrientUnit.valueOf(r.str("unit")),
                )
                // A genuine zero stays zero at any portion size.
                "ASSUMED_ZERO" -> NutrientValue.AssumedZero
                else -> NutrientValue.Unknown
            }
        }
        // Nutrients with no row are NOT added. NutrientProfile reads a missing key as Unknown,
        // which is the whole point: absent is not zero.
        return NutrientProfile(values)
    }

    private fun noDataMatch(query: FoodQuery): Outcome.Unavailable? {
        // allowFuzzy = false: see FoodTextMatching.match. Refusing a food because its name
        // merely resembles a no-data item is worse than missing it.
        val hit = FoodTextMatching.match(query.spokenName, noDataAliasCache, allowFuzzy = false)
            ?: return null
        val row = db.query(
            "SELECT display_name, reason FROM no_data_items WHERE item_key = ?",
            listOf(hit.key),
        ).firstOrNull() ?: return null
        return Outcome.Unavailable(
            UnavailableReason.KNOWN_ITEM_NO_DATA,
            "${row.str("display_name")}: ${row.str("reason")}",
        )
    }

    private fun strengthReason(strength: FoodTextMatching.MatchStrength): ConfidenceReason =
        when (strength) {
            FoodTextMatching.MatchStrength.EXACT -> ConfidenceReason.EXACT_FOOD_MATCH
            FoodTextMatching.MatchStrength.CONTAINED -> ConfidenceReason.FUZZY_FOOD_MATCH
            FoodTextMatching.MatchStrength.FUZZY -> ConfidenceReason.FUZZY_FOOD_MATCH
        }

    private fun kindOf(strength: FoodTextMatching.MatchStrength): MatchKind =
        if (strength == FoodTextMatching.MatchStrength.EXACT) MatchKind.EXACT else MatchKind.FUZZY

    private fun toFoodMatch(hit: FoodTextMatching.Candidate): FoodMatch {
        val row = db.query(
            "SELECT fdc_id, source, display_name, food_class, band_reason, disclosure FROM foods WHERE food_key = ?",
            listOf(hit.key),
        ).first()

        val reasons = mutableListOf(strengthReason(hit.strength))
        // A per-record caveat authored in ingredients.csv, e.g. ghee's label-derived values or
        // ridge gourd being a different Luffa species, travels with every figure from that record.
        row.strOrNull("band_reason")?.let { name ->
            runCatching { ConfidenceReason.valueOf(name) }.getOrNull()?.let { reasons += it }
        }

        return FoodMatch(
            code = FoodCode(DataSource.valueOf(row.str("source")), hit.key),
            displayName = row.str("display_name"),
            foodClass = FoodClass.valueOf(row.str("food_class")),
            matchKind = kindOf(hit.strength),
            confidence = ConfidenceRules.of(reasons),
            disclosure = row.strOrNull("disclosure"),
        )
    }

    /**
     * A composed dish resolved to its authored reference recipe.
     *
     * AUTHORED_REFERENCE_RECIPE is attached unconditionally, so the band can never read better
     * than Approximate however exact the name match was. The name being right says nothing about
     * whether this person's sambar is our sambar.
     */
    private fun toRecipeMatch(hit: FoodTextMatching.Candidate): FoodMatch {
        val row = db.query(
            "SELECT display_name FROM recipes WHERE recipe_key = ?",
            listOf(hit.key),
        ).first()

        return FoodMatch(
            code = FoodCode(DataSource.AUTHORED_RECIPE, hit.key),
            displayName = row.str("display_name"),
            foodClass = FoodClass.COMPOSED_DISH,
            matchKind = kindOf(hit.strength),
            confidence = ConfidenceRules.of(
                strengthReason(hit.strength),
                ConfidenceReason.AUTHORED_REFERENCE_RECIPE,
            ),
        )
    }

    private fun roleRank(role: String): Int = when (role) {
        "MAIN" -> 0
        "TEMPER" -> 1
        "SHALLOW_FRY_RETAINED" -> 2
        "ABSORBED_FAT" -> 3
        else -> 4
    }

    private companion object {
        /** Roles whose grams are what the dish RETAINS, not what the pan held. */
        val RETAINED_FAT_ROLES = setOf("ABSORBED_FAT", "SHALLOW_FRY_RETAINED")

        val GRAM_SYNONYMS = mapOf(
            "g" to 1.0, "gram" to 1.0, "grams" to 1.0, "gm" to 1.0,
            "kg" to 1000.0, "kilo" to 1000.0, "kilogram" to 1000.0,
        )

        /** Grams per unit AT DENSITY 1.0. See resolveUnit for why this is a default. */
        val VOLUME_SYNONYMS = mapOf(
            "ml" to 1.0, "millilitre" to 1.0, "milliliter" to 1.0,
            "l" to 1000.0, "litre" to 1000.0, "liter" to 1000.0,
        )
    }
}

/** Confidence for a figure, combining the match with how the quantity was obtained. */
fun figureConfidence(match: FoodMatch, weight: GramWeight, quantityStated: Boolean): Confidence {
    val reasons = match.confidence.reasons.toMutableList()
    reasons += if (quantityStated) ConfidenceReason.QUANTITY_STATED else ConfidenceReason.QUANTITY_INFERRED
    if (weight.isDefaultConversion) reasons += ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT
    return ConfidenceRules.of(reasons)
}
