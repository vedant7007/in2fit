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

    override suspend fun resolve(query: FoodQuery): Outcome<FoodMatch> {
        // A deliberately no-data item is checked FIRST. Otherwise a fuzzy match could pull "ragi"
        // onto some other grain, which is exactly the substitution that is forbidden.
        noDataMatch(query)?.let { return it }

        val hit = FoodTextMatching.match(query.spokenName, aliasCache)
            ?: return Outcome.Unavailable(UnavailableReason.NO_MATCH, query.spokenName)

        return Outcome.Ok(toMatch(hit))
    }

    override suspend fun candidates(query: FoodQuery, limit: Int): Outcome<List<FoodMatch>> {
        noDataMatch(query)?.let { return it }

        val q = FoodTextMatching.normalise(query.spokenName)
        if (q.isEmpty()) return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE)

        val scored = aliasCache.entries.mapNotNull { (alias, key) ->
            FoodTextMatching.match(q, mapOf(alias to key))
        }
        val ordered = scored
            .sortedWith(compareBy({ it.strength.ordinal }, { it.distance }, { it.key }))
            .distinctBy { it.key }
            .take(limit)

        if (ordered.isEmpty()) return Outcome.Unavailable(UnavailableReason.NO_MATCH, query.spokenName)
        return Outcome.Ok(ordered.map { toMatch(it) })
    }

    override suspend fun nutrientsFor(code: FoodCode, grams: Double): Outcome<NutrientProfile> {
        if (grams <= 0.0 || !grams.isFinite()) {
            return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "grams=$grams")
        }
        val rows = db.query(
            "SELECT nutrient, state, amount, unit FROM food_nutrients WHERE food_key = ?",
            listOf(code.id),
        )
        if (rows.isEmpty()) {
            return Outcome.Unavailable(UnavailableReason.NO_MATCH, "no nutrients for ${code.id}")
        }

        val factor = grams / 100.0
        val values = mutableMapOf<Nutrient, NutrientValue>()
        for (r in rows) {
            val nutrient = runCatching { Nutrient.valueOf(r.str("nutrient")) }.getOrNull() ?: continue
            values[nutrient] = when (r.str("state")) {
                "MEASURED" -> NutrientValue.Measured(
                    amount = r.dbl("amount") * factor,
                    unit = NutrientUnit.valueOf(r.str("unit")),
                )
                // A genuine zero stays zero at any portion size.
                "ASSUMED_ZERO" -> NutrientValue.AssumedZero
                else -> NutrientValue.Unknown
            }
        }
        // Nutrients with no row are NOT added. NutrientProfile reads a missing key as Unknown,
        // which is the whole point: absent is not zero.
        return Outcome.Ok(NutrientProfile(values))
    }

    override suspend fun resolveUnit(unit: String, foodClass: FoodClass): Outcome<GramWeight> {
        val u = FoodTextMatching.normalise(unit)
        if (u.isEmpty()) return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE)

        // Grams and millilitres are not household units and need no table.
        GRAM_SYNONYMS[u]?.let { return Outcome.Ok(GramWeight(it, false, DataSource.USER_PROVIDED)) }

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

    override suspend fun recipe(code: FoodCode): Outcome<ReferenceRecipe> =
        // No reference recipes are authored yet. This is an explicit not-implemented state, never
        // a computed stand-in built out of ingredients we happened to have.
        Outcome.NotImplemented("data.food.ReferenceRecipes")

    override suspend fun recordUnmatched(query: FoodQuery) = unmatched.record(query)

    // --- internals ------------------------------------------------------------------------

    private fun <T> noDataMatch(query: FoodQuery): Outcome<T>? {
        val hit = FoodTextMatching.match(query.spokenName, noDataAliasCache) ?: return null
        val row = db.query(
            "SELECT display_name, reason FROM no_data_items WHERE item_key = ?",
            listOf(hit.key),
        ).firstOrNull() ?: return null
        return Outcome.Unavailable(
            UnavailableReason.KNOWN_ITEM_NO_DATA,
            "${row.str("display_name")}: ${row.str("reason")}",
        )
    }

    private fun toMatch(hit: FoodTextMatching.Candidate): FoodMatch {
        val row = db.query(
            "SELECT fdc_id, source, display_name, food_class, band_reason FROM foods WHERE food_key = ?",
            listOf(hit.key),
        ).first()

        val reasons = mutableListOf<ConfidenceReason>()
        reasons += when (hit.strength) {
            FoodTextMatching.MatchStrength.EXACT -> ConfidenceReason.EXACT_FOOD_MATCH
            FoodTextMatching.MatchStrength.CONTAINED -> ConfidenceReason.FUZZY_FOOD_MATCH
            FoodTextMatching.MatchStrength.FUZZY -> ConfidenceReason.FUZZY_FOOD_MATCH
        }
        // A per-record caveat authored in ingredients.csv, e.g. ghee's label-derived values or
        // ridge gourd being a different Luffa species, travels with every figure from that record.
        row.strOrNull("band_reason")?.let { name ->
            runCatching { ConfidenceReason.valueOf(name) }.getOrNull()?.let { reasons += it }
        }

        return FoodMatch(
            code = FoodCode(DataSource.valueOf(row.str("source")), hit.key),
            displayName = row.str("display_name"),
            foodClass = FoodClass.valueOf(row.str("food_class")),
            matchKind = when (hit.strength) {
                FoodTextMatching.MatchStrength.EXACT -> MatchKind.EXACT
                else -> MatchKind.FUZZY
            },
            confidence = ConfidenceRules.of(reasons),
        )
    }

    private companion object {
        val GRAM_SYNONYMS = mapOf(
            "g" to 1.0, "gram" to 1.0, "grams" to 1.0, "gm" to 1.0,
            "ml" to 1.0, "millilitre" to 1.0, "milliliter" to 1.0,
            "kg" to 1000.0, "kilo" to 1000.0, "kilogram" to 1000.0,
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
