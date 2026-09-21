package io.github.vedant7007.katori.data.local

import io.github.vedant7007.katori.data.food.FoodDbSource
import io.github.vedant7007.katori.data.food.str
import io.github.vedant7007.katori.data.local.dao.NutrientTotalRow
import io.github.vedant7007.katori.data.local.entity.LabValueEntity
import io.github.vedant7007.katori.data.local.entity.MealEntity
import io.github.vedant7007.katori.data.local.entity.MealItemEntity
import io.github.vedant7007.katori.data.local.entity.MealItemNutrientEntity
import io.github.vedant7007.katori.data.local.entity.WaterEntity
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientTotal
import io.github.vedant7007.katori.domain.model.NutrientUnit
import io.github.vedant7007.katori.domain.model.NutritionFigure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * THE DIARY AS THE SCREENS READ IT (21 Sep): meals with their items and derived figures, a day,
 * a run of days, the lab values, and the one delete. One derivation, the DAO's, shared with the
 * orchestrator's context source ([figureOf]), so a screen and a spoken answer never disagree
 * about a total.
 *
 * NOTHING HERE IS ARITHMETIC ON A HEALTH FIGURE. Totals come from `nutrientTotalsForRange` and
 * `nutrientTotalsForMeal`; a day's figure is that query over the day's bounds, not a sum of the
 * meals' figures. A partial total keeps its holes named, and a total with no measured
 * contributor is absent, never zero.
 */
class Diary(private val db: KatoriDatabase, private val foods: FoodDbSource?, private val clock: Clock) {

    @Inject constructor(db: KatoriDatabase, foods: FoodDbSource) : this(db, foods, Clock.systemDefaultZone())

    /** One logged meal: the rows as stored, the figures as derived, each item's measured amounts by item id. */
    data class Meal(
        val id: Long,
        val loggedAt: Instant,
        val items: List<MealItemEntity>,
        val itemNutrients: Map<Long, Map<Nutrient, Double>>,
        val figures: List<NutritionFigure>,
        val rawTranscript: String?,
        /** "SPOKEN", "TYPED" or null when the row does not say. */
        val source: String?,
    )

    /** One local day, its meals in the order they were logged, and its own derived figures. */
    data class Day(val date: LocalDate, val meals: List<Meal>, val figures: List<NutritionFigure>)

    fun today(): LocalDate = LocalDate.now(clock)

    val zone: ZoneId get() = clock.zone

    /** An item's nutrient rows as stored: state and amount, an Unknown with no amount. */
    suspend fun nutrientRows(itemId: Long): List<MealItemNutrientEntity> = db.mealDao().nutrientRows(itemId)

    /** Local midnight to the last millisecond of the day, in the epoch millis the DAO takes. */
    fun bounds(date: LocalDate): LongRange {
        val start = date.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli() - 1
        return start..end
    }

    fun meals(range: LongRange): Flow<List<Meal>> =
        db.mealDao().mealsInRange(range.first, range.last).map { rows -> rows.map { meal(it) } }

    fun lastMeal(): Flow<Meal?> = db.mealDao().recentMeals(1).map { it.firstOrNull()?.let { m -> meal(m) } }

    /** The range's derived totals, one figure per nutrient that has any contributor. */
    fun totals(range: LongRange): Flow<List<NutritionFigure>> {
        val dao = db.mealDao()
        return combine(dao.nutrientTotalsForRange(range.first, range.last), dao.mealsInRange(range.first, range.last)) { rows, meals ->
            val items = meals.flatMap { dao.itemsFor(it.id) }
            val unknown = dao.unknownContributorsInRange(range.first, range.last).groupBy({ it.nutrient }, { it.spoken_name })
            rows.mapNotNull { figureOf(it, unknown[it.nutrient].orEmpty().distinct(), reasonsOf(items), sourcesOf(items)) }
        }
    }

    fun day(date: LocalDate): Flow<Day> {
        val range = bounds(date)
        return combine(meals(range), totals(range)) { meals, figures -> Day(date, meals.sortedBy { it.loggedAt }, figures) }
    }

    /** [count] days ending on [endingOn], oldest first. */
    fun days(count: Int, endingOn: LocalDate = today()): Flow<List<Day>> =
        combine((count - 1 downTo 0).map { day(endingOn.minusDays(it.toLong())) }) { it.toList() }

    fun labs(): Flow<List<LabValueEntity>> = db.labValueDao().observeAll()

    /** One nudge as the page shows it: what was said, for which meal, when. Nothing here is generated at display time. */
    data class Nudge(
        val id: Long,
        val mealId: Long,
        val mealLoggedAt: Instant,
        val saidAt: Instant,
        /** The engine's own sentence, when a rule fired. */
        val trigger: String?,
        /** The model's guarded sentence, when one passed. */
        val phrased: String?,
        val ruleId: String?,
    )

    /** The stored advice over time, newest first: what was actually said to the person and when. */
    fun recentAdvice(limit: Int): Flow<List<Nudge>> = db.suggestionDao().recent(limit).map { rows ->
        rows.map { Nudge(it.id, it.meal_id, Instant.ofEpochMilli(it.meal_logged_at_epoch_ms), Instant.ofEpochMilli(it.created_at_epoch_ms), it.trigger_text, it.advice_text.ifEmpty { null }, it.triggering_rule_id) }
    }

    fun labHistory(testName: String): Flow<List<LabValueEntity>> = db.labValueDao().history(testName)

    /** The meal, its items, their nutrients and the advice stored for it. Nothing else remembers it. */
    suspend fun deleteMeal(mealId: Long) = db.mealDao().deleteMealAndAdvice(mealId)

    /** One item; the meal's band is re-derived from what remains, and a meal left empty goes. */
    suspend fun deleteItem(itemId: Long) = db.mealDao().deleteItemAndRederive(itemId)

    /** Water logged in the range, and its total in ml: null when nothing was logged, never 0. */
    fun water(range: LongRange): Flow<List<WaterEntity>> = db.waterDao().rows(range.first, range.last)
    fun waterTotal(range: LongRange): Flow<Int?> = db.waterDao().totalForRange(range.first, range.last)

    suspend fun logWater(ml: Int, source: String, at: Instant = clock.instant()) {
        require(ml > 0) { "water is logged in whole millilitres above zero" }
        db.waterDao().insert(WaterEntity(ml = ml, logged_at_epoch_ms = at.toEpochMilli(), source = source))
    }

    suspend fun deleteWater(id: Long) = db.waterDao().delete(id)

    /** The catalogue names of the foods logged most often, most often first. */
    suspend fun frequentFoodNames(limit: Int): List<String> {
        val catalogue = foods ?: return emptyList()
        return db.mealDao().frequentFoods(limit).mapNotNull { row ->
            catalogue.query(
                "SELECT display_name FROM foods WHERE food_key = ? UNION ALL SELECT display_name FROM recipes WHERE recipe_key = ?",
                listOf(row.food_id, row.food_id),
            ).firstOrNull()?.str("display_name")
        }
    }

    suspend fun meal(row: MealEntity): Meal {
        val dao = db.mealDao()
        val items = dao.itemsFor(row.id)
        val unknown = dao.unknownContributors(row.id).groupBy({ it.nutrient }, { it.spoken_name })
        val figures = dao.nutrientTotalsForMeal(row.id).first().mapNotNull { figureOf(it, unknown[it.nutrient].orEmpty(), reasonsOf(items), sourcesOf(items)) }
        val perItem = dao.measuredNutrientsFor(row.id)
            .groupBy({ it.meal_item_id }) { enumName<Nutrient>(it.nutrient)?.let { n -> n to it.amount } }
            .mapValues { (_, pairs) -> pairs.filterNotNull().toMap() }
        return Meal(row.id, Instant.ofEpochMilli(row.logged_at_epoch_ms), items, perItem, figures, row.raw_transcript, row.source)
    }

    companion object {
        /** Consecutive days with at least one meal, ending on the LAST day of [days]; 0 when that day has none. */
        fun streak(days: List<Day>): Int = days.asReversed().takeWhile { it.meals.isNotEmpty() }.size

        /** The band over a set of items is the worst across them; a meal written before reasons were recorded is not GOOD by default. */
        fun reasonsOf(items: List<MealItemEntity>): List<ConfidenceReason> =
            items.flatMap { it.confidence_reasons.split(',') }.mapNotNull { enumName<ConfidenceReason>(it.trim()) }

        fun sourcesOf(items: List<MealItemEntity>): List<DataSource> =
            items.mapNotNull { it.food_source?.let { s -> enumName<DataSource>(s) } }.distinct()
    }
}

/**
 * A DAO total row as a figure: the completeness from the counts, the band from the items'
 * reasons, the sources cited. Shared by the diary and the orchestrator's context source.
 */
internal fun figureOf(row: NutrientTotalRow, unknown: List<String>, reasons: List<ConfidenceReason>, sources: List<DataSource>): NutritionFigure? {
    val nutrient = enumName<Nutrient>(row.nutrient) ?: return null
    val completeness = when {
        row.measured_count == 0 && row.unknown_count > 0 -> Completeness.NONE
        row.unknown_count > 0 -> Completeness.PARTIAL
        else -> Completeness.COMPLETE
    }
    return NutritionFigure(
        total = NutrientTotal(nutrient, row.total, enumName<NutrientUnit>(row.unit) ?: nutrient.unit, completeness, unknown),
        // A meal written before reasons were recorded still has a band; it is not GOOD by default.
        confidence = ConfidenceRules.of(reasons.ifEmpty { listOf(ConfidenceReason.CATEGORY_LEVEL_MATCH) }),
        sources = sources,
    )
}

internal inline fun <reified E : Enum<E>> enumName(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

/**
 * The household unit a food class is usually served in, the same table the context source
 * ranks candidates per serving with and the search shows a portion in. A class not listed is
 * shown per 100 g.
 */
internal val USUAL_UNIT = mapOf(
    "PULSE_COOKED" to "katori", "GRAIN_COOKED" to "katori", "VEGETABLE" to "katori", "DAIRY" to "katori",
    "BEVERAGE" to "glass", "FAT_OIL" to "teaspoon", "SPICE" to "teaspoon",
    "GRAIN_RAW" to "cup", "PULSE_RAW" to "cup", "COMPOSED_DISH" to "piece",
)
