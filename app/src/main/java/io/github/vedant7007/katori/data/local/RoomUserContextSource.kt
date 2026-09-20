package io.github.vedant7007.katori.data.local

import io.github.vedant7007.katori.data.food.FoodDbSource
import io.github.vedant7007.katori.data.food.dbl
import io.github.vedant7007.katori.data.food.str
import io.github.vedant7007.katori.data.local.dao.NutrientTotalRow
import io.github.vedant7007.katori.domain.CandidateFood
import io.github.vedant7007.katori.domain.ConditionSource
import io.github.vedant7007.katori.domain.DeclaredCondition
import io.github.vedant7007.katori.domain.DietType
import io.github.vedant7007.katori.domain.Goal
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.LifeContext
import io.github.vedant7007.katori.domain.LoggedMeal
import io.github.vedant7007.katori.domain.MealItemSnapshot
import io.github.vedant7007.katori.domain.MealSnapshot
import io.github.vedant7007.katori.domain.Period
import io.github.vedant7007.katori.domain.PeriodTotals
import io.github.vedant7007.katori.domain.ProfileSnapshot
import io.github.vedant7007.katori.domain.Sex
import io.github.vedant7007.katori.domain.UserContext
import io.github.vedant7007.katori.domain.UserContextSource
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientTotal
import io.github.vedant7007.katori.domain.model.NutrientUnit
import io.github.vedant7007.katori.domain.model.NutritionFigure
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [UserContextSource] over the user database and the bundled food database.
 *
 * Everything the orchestrator needs to make a turn about THIS person, read fresh each time:
 * profile, declared conditions, every lab value, the last [RECENT_MEALS] meals with their derived
 * totals, and the candidate foods the rules engine may rank.
 *
 * TOTALS ARE DERIVED, and the derivation is the DAO's: `nutrientTotalsForMeal` sums measured
 * contributors and counts unknown ones, and `unknownContributors` names them, so a figure with a hole
 * in it reaches the request as "at least 11 g (no value for dal)" and never as 11 g.
 *
 * CANDIDATES. Spec 4.3's constrained ingredient list per life context has no authored file yet,
 * so every bundled food and recipe is a candidate for every context, minus what the person has
 * removed for their context and minus what their diet type forbids. That is the honest state,
 * not a placeholder: the rules engine ranks on nutrients, and the ranking is real; what is
 * missing is the per-context pruning, and the day it is authored it plugs in here.
 *
 * THE SERVING each candidate is ranked by is the one the database models: a recipe's yield
 * over its servings; for a plain food, the household unit its class is usually served in
 * (`unit_conversions`: a katori of dal, a katori of sabzi, a teaspoon of a spice), 100 g when
 * the class has no such unit. Ranking per serving is what stops a spice topping an iron list.
 *
 * ponytail: the diet filter is a word match over the USDA description, because the bundled
 * database has no diet column and classifies egg as DAIRY and meats as COMPOSED_DISH. It errs
 * towards excluding: a vegetable with "meat" in its USDA name ("Coconut meat") is dropped for a
 * vegetarian, which costs one candidate and nothing else; an animal food that slipped through
 * would cost a vegetarian being told to eat goat for iron. The upgrade is a `diet_class` column
 * in `ingredients.csv` and `recipes.csv`, which is an authoring change, not a code one.
 */
class RoomUserContextSource(
    private val db: KatoriDatabase,
    private val foods: FoodDbSource,
    private val clock: Clock = Clock.systemDefaultZone(),
) : UserContextSource {

    override suspend fun current(): UserContext {
        val profile = db.profileDao().observe().first()
        val context = profile?.life_context?.let { enumOrNull<LifeContext>(it) }
        val diet = profile?.diet_type?.let { enumOrNull<DietType>(it) }
        val avoided = context?.let { c ->
            db.overridesDao().contextFoods(c.name).filterNot { it.included }.map { it.food_id }.toSet()
        } ?: emptySet()

        return UserContext(
            profile = ProfileSnapshot(
                ageYears = profile?.age_years,
                weightKg = profile?.weight_kg,
                heightCm = profile?.height_cm,
                sex = profile?.sex?.let { enumOrNull<Sex>(it) },
                goal = profile?.goal?.let { enumOrNull<Goal>(it) },
                context = context,
                dietType = diet,
                avoidedFoodCodes = avoided,
            ),
            declaredConditions = db.conditionDao().observeAll().first().map {
                DeclaredCondition(it.name, enumOrNull<ConditionSource>(it.source) ?: ConditionSource.USER_DECLARED)
            },
            labValues = db.labValueDao().observeAll().first().map {
                LabValue(it.test_name, it.value, it.unit, it.reference_low, it.reference_high, LocalDate.parse(it.report_date))
            },
            recentMeals = recentMeals(),
            periodTotals = periodTotals(),
            candidates = candidates(diet, avoided),
        )
    }

    private suspend fun recentMeals(): List<LoggedMeal> {
        val meals = db.mealDao()
        return meals.recentMeals(RECENT_MEALS).first().map { meal ->
            val items = meals.itemsFor(meal.id)
            val reasons = items.flatMap { it.confidence_reasons.split(',') }
                .mapNotNull { enumOrNull<ConfidenceReason>(it.trim()) }
            val sources = items.mapNotNull { it.food_source?.let { s -> enumOrNull<DataSource>(s) } }.distinct()
            val unknown = meals.unknownContributors(meal.id).groupBy({ it.nutrient }, { it.spoken_name })
            LoggedMeal(
                snapshot = MealSnapshot(
                    mealId = meal.id,
                    items = items.map { MealItemSnapshot(it.spoken_name, it.food_id, it.grams, emptyMap()) },
                    loggedAt = Instant.ofEpochMilli(meal.logged_at_epoch_ms),
                ),
                figures = meals.nutrientTotalsForMeal(meal.id).first().mapNotNull { row ->
                    figure(row, unknown[row.nutrient].orEmpty(), reasons, sources)
                },
            )
        }
    }

    /** Today from local midnight, and the seven days ending now. Both from the DAO's derivation. */
    private suspend fun periodTotals(): List<PeriodTotals> {
        val meals = db.mealDao()
        val now = clock.instant()
        val zone: ZoneId = clock.zone
        val startOfToday = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val weekAgo = now.minusSeconds(7L * 24 * 3600)
        suspend fun over(period: Period, from: Instant): PeriodTotals {
            val unknown = meals.unknownContributorsInRange(from.toEpochMilli(), now.toEpochMilli())
                .groupBy({ it.nutrient }, { it.spoken_name })
            val rows = meals.nutrientTotalsForRange(from.toEpochMilli(), now.toEpochMilli()).first()
            // Reasons and sources over a period are the union across every item in it: the band
            // is the worst, and every source that contributed is cited.
            val items = meals.mealsInRange(from.toEpochMilli(), now.toEpochMilli()).first().flatMap { meals.itemsFor(it.id) }
            val reasons = items.flatMap { it.confidence_reasons.split(',') }.mapNotNull { enumOrNull<ConfidenceReason>(it.trim()) }
            val sources = items.mapNotNull { it.food_source?.let { s -> enumOrNull<DataSource>(s) } }.distinct()
            return PeriodTotals(period, rows.mapNotNull { figure(it, unknown[it.nutrient].orEmpty().distinct(), reasons, sources) })
        }
        return listOf(over(Period.TODAY, startOfToday), over(Period.LAST_SEVEN_DAYS, weekAgo))
            .filter { it.figures.isNotEmpty() }
    }

    private fun figure(row: NutrientTotalRow, unknown: List<String>, reasons: List<ConfidenceReason>, sources: List<DataSource>): NutritionFigure? {
        val nutrient = enumOrNull<Nutrient>(row.nutrient) ?: return null
        val completeness = when {
            row.measured_count == 0 && row.unknown_count > 0 -> Completeness.NONE
            row.unknown_count > 0 -> Completeness.PARTIAL
            else -> Completeness.COMPLETE
        }
        return NutritionFigure(
            total = NutrientTotal(nutrient, row.total, enumOrNull<NutrientUnit>(row.unit) ?: nutrient.unit, completeness, unknown),
            // A meal written before reasons were recorded still has a band; it is not GOOD by default.
            confidence = ConfidenceRules.of(reasons.ifEmpty { listOf(ConfidenceReason.CATEGORY_LEVEL_MATCH) }),
            sources = sources,
        )
    }

    private fun candidates(diet: DietType?, avoided: Set<String>): List<CandidateFood> {
        val forbidden = FORBIDDEN_WORDS[diet].orEmpty()
        fun allowed(key: String, description: String, foodClass: String?): Boolean {
            if (key in avoided) return false
            // A raw ingredient has no eaten serving: MEASURED on the ten demo sentences (20 Sep),
            // ranked per a cup of raw pulse, "Cowpeas (catjang), raw" and "Mungo beans (urad), raw"
            // topped the iron list and were spoken as advice. The cooked food or the dish is
            // the candidate; the flour and the dry dal are what it is made from.
            if (foodClass == "GRAIN_RAW" || foodClass == "PULSE_RAW") return false
            if (diet == DietType.VEGAN && foodClass == "DAIRY") return false
            val words = description.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
            return forbidden.none { it in words }
        }
        val everywhere = LifeContext.entries.toSet()
        // The usual serving per class, from the same table the resolver converts spoken units with.
        val servingByClass: Map<String, Double> = foods.query("SELECT unit, food_class, grams FROM unit_conversions")
            .filter { it.str("unit") == USUAL_UNIT[it.str("food_class")] }
            .associate { it.str("food_class") to it.dbl("grams") }

        val foodNutrients = foods.query("SELECT food_key, nutrient, amount FROM food_nutrients WHERE state = 'MEASURED'")
            .groupBy({ it.str("food_key") }) { enumOrNull<Nutrient>(it.str("nutrient"))?.let { n -> n to it.dbl("amount") } }
        val foodRows = foods.query("SELECT food_key, display_name, usda_description, food_class FROM foods")
            .filter { allowed(it.str("food_key"), it.str("display_name") + " " + it.str("usda_description"), it.str("food_class")) }
            .map { row ->
                val key = row.str("food_key")
                CandidateFood(
                    key, row.str("display_name"), everywhere, foodNutrients[key].orEmpty().filterNotNull().toMap(),
                    servingGrams = servingByClass[row.str("food_class")] ?: 100.0,
                )
            }

        val recipeNutrients = foods.query("SELECT recipe_key, nutrient, amount_per_100g FROM recipe_nutrients WHERE state = 'MEASURED'")
            .groupBy({ it.str("recipe_key") }) { enumOrNull<Nutrient>(it.str("nutrient"))?.let { n -> n to it.dbl("amount_per_100g") } }
        // A recipe is forbidden when any ingredient is: chicken biryani has chicken in it.
        val recipeIngredientNames = foods.query(
            "SELECT ri.recipe_key AS recipe_key, f.display_name AS name, f.usda_description AS description, f.food_class AS food_class FROM recipe_ingredients ri JOIN foods f ON f.food_key = ri.food_key"
        ).groupBy({ it.str("recipe_key") }) { Triple(it.str("name"), it.str("description"), it.str("food_class")) }
        val recipeRows = foods.query("SELECT recipe_key, display_name, servings, yield_g FROM recipes")
            .filter { row ->
                val key = row.str("recipe_key")
                allowed(key, row.str("display_name"), null) &&
                    recipeIngredientNames[key].orEmpty().all { (name, description, cls) -> allowed(key, "$name $description", cls) }
            }
            .map { row ->
                val key = row.str("recipe_key")
                val servings = row.dbl("servings")
                CandidateFood(
                    key, row.str("display_name"), everywhere, recipeNutrients[key].orEmpty().filterNotNull().toMap(),
                    servingGrams = if (servings > 0.0) row.dbl("yield_g") / servings else 100.0,
                )
            }

        return foodRows + recipeRows
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }

    private companion object {
        const val RECENT_MEALS = 6

        /** The household unit a class is usually served in; the same defaults the resolver assumes. */
        val USUAL_UNIT = mapOf(
            "PULSE_COOKED" to "katori", "GRAIN_COOKED" to "katori", "VEGETABLE" to "katori", "DAIRY" to "katori",
            "BEVERAGE" to "glass", "FAT_OIL" to "teaspoon", "SPICE" to "teaspoon",
            "GRAIN_RAW" to "cup", "PULSE_RAW" to "cup", "COMPOSED_DISH" to "piece",
        )

        /** Words in a USDA description that mark a food a diet forbids. Errs towards excluding. */
        val FORBIDDEN_WORDS: Map<DietType?, Set<String>> = run {
            val animal = setOf("chicken", "goat", "mutton", "lamb", "beef", "pork", "fish", "carp", "shrimp", "prawn", "prawns", "meat", "egg", "eggs")
            val noEgg = animal - setOf("egg", "eggs")
            val jain = animal + setOf("onion", "onions", "garlic", "potato", "potatoes", "carrot", "carrots", "beet", "beets", "radish", "ginger", "turnip")
            mapOf(
                DietType.VEGETARIAN to animal,
                DietType.VEGAN to animal,
                DietType.JAIN to jain,
                DietType.EGGETARIAN to noEgg,
                DietType.NON_VEGETARIAN to emptySet(),
                null to emptySet(),
            )
        }
    }
}
