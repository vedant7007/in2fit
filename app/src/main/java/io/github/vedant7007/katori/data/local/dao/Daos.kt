package io.github.vedant7007.katori.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.vedant7007.katori.data.local.entity.ActivityEntity
import io.github.vedant7007.katori.data.local.entity.ConditionEntity
import io.github.vedant7007.katori.data.local.entity.ContextFoodOverrideEntity
import io.github.vedant7007.katori.data.local.entity.ExerciseSessionEntity
import io.github.vedant7007.katori.data.local.entity.HouseholdRecipeEntity
import io.github.vedant7007.katori.data.local.entity.HouseholdRecipeIngredientEntity
import io.github.vedant7007.katori.data.local.entity.LabValueEntity
import io.github.vedant7007.katori.data.local.entity.MealEntity
import io.github.vedant7007.katori.data.local.entity.MealItemEntity
import io.github.vedant7007.katori.data.local.entity.MealItemNutrientEntity
import io.github.vedant7007.katori.data.local.entity.ProfileEntity
import io.github.vedant7007.katori.data.local.entity.SuggestionEntity
import io.github.vedant7007.katori.data.local.entity.UnitConversionOverrideEntity
import io.github.vedant7007.katori.data.local.entity.UnmatchedUtteranceEntity
import io.github.vedant7007.katori.data.local.entity.WaterEntity
import io.github.vedant7007.katori.data.local.entity.WeightEntity
import io.github.vedant7007.katori.data.local.entity.ReminderEntity
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import kotlinx.coroutines.flow.Flow

/**
 * DAO contracts for the user database.
 *
 * TOTALS ARE DERIVED. Nothing here writes a total. [MealDao.nutrientTotalsForRange] computes them,
 * and it reports whether any contributor was UNKNOWN so the caller can present a partial figure as
 * a floor rather than as a total. A DAO that gains a `SELECT stored_total` is a bug.
 *
 * All reads that the UI observes return [Flow] so a correction anywhere updates every screen
 * showing that figure, which spec 15.3 requires to always be possible.
 */

@Dao
interface MealDao {

    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    @Insert
    suspend fun insertItems(items: List<MealItemEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNutrients(rows: List<MealItemNutrientEntity>)

    @Transaction
    @Query("SELECT * FROM meals WHERE id = :mealId")
    suspend fun meal(mealId: Long): MealEntity?

    @Query("SELECT * FROM meals ORDER BY logged_at_epoch_ms DESC LIMIT :limit")
    fun recentMeals(limit: Int): Flow<List<MealEntity>>

    @Query(
        """
        SELECT * FROM meals
        WHERE logged_at_epoch_ms BETWEEN :fromEpochMs AND :toEpochMs
        ORDER BY logged_at_epoch_ms DESC
        """
    )
    fun mealsInRange(fromEpochMs: Long, toEpochMs: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal_items WHERE meal_id = :mealId")
    suspend fun itemsFor(mealId: Long): List<MealItemEntity>

    /**
     * Derived totals for a time range.
     *
     * `total` sums MEASURED and ASSUMED_ZERO contributors only. `unknown_count` counts contributors
     * with no value. When `unknown_count` is greater than zero the figure is a FLOOR, and the UI must
     * word it as "at least" and list what is missing. When `measured_count` is zero there is no
     * figure at all and the UI shows "not known", never a zero.
     */
    @Query(
        """
        SELECT n.nutrient AS nutrient,
               n.unit AS unit,
               SUM(CASE WHEN n.state = 'MEASURED' THEN n.amount ELSE 0 END) AS total,
               SUM(CASE WHEN n.state = 'UNKNOWN' THEN 1 ELSE 0 END) AS unknown_count,
               SUM(CASE WHEN n.state = 'MEASURED' THEN 1 ELSE 0 END) AS measured_count
        FROM meal_item_nutrients n
        JOIN meal_items i ON i.id = n.meal_item_id
        JOIN meals m ON m.id = i.meal_id
        WHERE m.logged_at_epoch_ms BETWEEN :fromEpochMs AND :toEpochMs
        GROUP BY n.nutrient, n.unit
        """
    )
    fun nutrientTotalsForRange(fromEpochMs: Long, toEpochMs: Long): Flow<List<NutrientTotalRow>>

    /** Same derivation, scoped to one meal. */
    @Query(
        """
        SELECT n.nutrient AS nutrient,
               n.unit AS unit,
               SUM(CASE WHEN n.state = 'MEASURED' THEN n.amount ELSE 0 END) AS total,
               SUM(CASE WHEN n.state = 'UNKNOWN' THEN 1 ELSE 0 END) AS unknown_count,
               SUM(CASE WHEN n.state = 'MEASURED' THEN 1 ELSE 0 END) AS measured_count
        FROM meal_item_nutrients n
        JOIN meal_items i ON i.id = n.meal_item_id
        WHERE i.meal_id = :mealId
        GROUP BY n.nutrient, n.unit
        """
    )
    fun nutrientTotalsForMeal(mealId: Long): Flow<List<NutrientTotalRow>>

    /**
     * Which items had no value for which nutrient, so a partial total can NAME what is missing
     * ("at least 11 g; no value for dal") rather than only count it.
     */
    @Query(
        """
        SELECT n.nutrient AS nutrient, i.spoken_name AS spoken_name
        FROM meal_item_nutrients n
        JOIN meal_items i ON i.id = n.meal_item_id
        WHERE i.meal_id = :mealId AND n.state = 'UNKNOWN'
        ORDER BY i.id
        """
    )
    suspend fun unknownContributors(mealId: Long): List<UnknownContributorRow>

    /** The measured amounts of a meal's items, for the snapshot the rules engine re-evaluates. */
    @Query(
        """
        SELECT n.meal_item_id AS meal_item_id, n.nutrient AS nutrient, n.amount AS amount
        FROM meal_item_nutrients n
        JOIN meal_items i ON i.id = n.meal_item_id
        WHERE i.meal_id = :mealId AND n.state = 'MEASURED' AND n.amount IS NOT NULL
        """
    )
    suspend fun measuredNutrientsFor(mealId: Long): List<MeasuredNutrientRow>

    /** The same holes, over a time range, for the period totals. */
    @Query(
        """
        SELECT n.nutrient AS nutrient, i.spoken_name AS spoken_name
        FROM meal_item_nutrients n
        JOIN meal_items i ON i.id = n.meal_item_id
        JOIN meals m ON m.id = i.meal_id
        WHERE m.logged_at_epoch_ms BETWEEN :fromEpochMs AND :toEpochMs AND n.state = 'UNKNOWN'
        ORDER BY i.id
        """
    )
    suspend fun unknownContributorsInRange(fromEpochMs: Long, toEpochMs: Long): List<UnknownContributorRow>

    @Query("DELETE FROM meal_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Query("UPDATE meals SET confidence_band = :band WHERE id = :mealId")
    suspend fun setBand(mealId: Long, band: ConfidenceBand)

    @Query("SELECT meal_id FROM meal_items WHERE id = :itemId")
    suspend fun mealIdOf(itemId: Long): Long?

    /** An item's nutrient rows as stored, every state, for the export. */
    @Query("SELECT * FROM meal_item_nutrients WHERE meal_item_id = :itemId")
    suspend fun nutrientRows(itemId: Long): List<MealItemNutrientEntity>

    /**
     * Removes one item and keeps the meal honest: its band is the worst across what remains, and
     * a meal with nothing left is not a meal (`RoomMealStore.save` refuses one). The advice stored
     * for the meal was phrased for a plate that no longer exists and goes too.
     * @return the meal's id while it still exists, null when it went with its last item.
     */
    @Transaction
    suspend fun deleteItemAndRederive(itemId: Long): Long? {
        val mealId = mealIdOf(itemId) ?: return null
        deleteItem(itemId)
        deleteSuggestionsFor(mealId)
        val remaining = itemsFor(mealId)
        if (remaining.isEmpty()) { deleteMeal(mealId); return null }
        setBand(mealId, remaining.maxOf { it.confidence_band })
        return mealId
    }

    /** The meal and, by the foreign keys, its items and their nutrients. The advice stored for it goes with it. */
    @Query("DELETE FROM meals WHERE id = :mealId")
    suspend fun deleteMeal(mealId: Long)

    @Query("DELETE FROM suggestions WHERE meal_id = :mealId")
    suspend fun deleteSuggestionsFor(mealId: Long)

    @Transaction
    suspend fun deleteMealAndAdvice(mealId: Long) {
        deleteSuggestionsFor(mealId)
        deleteMeal(mealId)
    }

    /** The foods logged most often, by resolved id; a row that never matched has no id and is not counted. */
    @Query("SELECT food_id AS food_id, COUNT(*) AS times FROM meal_items WHERE food_id IS NOT NULL GROUP BY food_id ORDER BY times DESC, food_id LIMIT :limit")
    suspend fun frequentFoods(limit: Int): List<FrequentFoodRow>

    /** The count of meals in a range that were logged by voice, from the rows that say so. */
    @Query("SELECT COUNT(*) FROM meals WHERE logged_at_epoch_ms BETWEEN :fromEpochMs AND :toEpochMs AND source = 'SPOKEN'")
    fun spokenMealCount(fromEpochMs: Long, toEpochMs: Long): Flow<Int>
}

data class FrequentFoodRow(val food_id: String, val times: Int)

/** One piece of stored advice with the time of the meal it was for. `advice_text` empty: every phrasing failed a guard, the trigger sentence was what was said. */
data class AdviceRow(
    val id: Long,
    val meal_id: Long,
    val meal_logged_at_epoch_ms: Long,
    val advice_text: String,
    val trigger_text: String?,
    val triggering_rule_id: String?,
    val created_at_epoch_ms: Long,
)

/** Projection for a derived total. Carries the completeness counts, never a bare number. */
data class NutrientTotalRow(
    val nutrient: String,
    val unit: String,
    val total: Double,
    val unknown_count: Int,
    val measured_count: Int,
)

/** One measured amount of one item. */
data class MeasuredNutrientRow(
    val meal_item_id: Long,
    val nutrient: String,
    val amount: Double,
)

/** One hole in a derived total: this item had no value for this nutrient. */
data class UnknownContributorRow(
    val nutrient: String,
    val spoken_name: String,
)

@Dao
interface WaterDao {
    @Insert
    suspend fun insert(row: WaterEntity): Long

    @Query("SELECT * FROM water WHERE logged_at_epoch_ms BETWEEN :fromEpochMs AND :toEpochMs ORDER BY logged_at_epoch_ms")
    fun rows(fromEpochMs: Long, toEpochMs: Long): Flow<List<WaterEntity>>

    /** The range's total in ml, or null when nothing was logged: absent, not zero. */
    @Query("SELECT SUM(ml) FROM water WHERE logged_at_epoch_ms BETWEEN :fromEpochMs AND :toEpochMs")
    fun totalForRange(fromEpochMs: Long, toEpochMs: Long): Flow<Int?>

    @Query("DELETE FROM water WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WeightDao {
    @Insert
    suspend fun insert(row: WeightEntity): Long

    @Query("SELECT * FROM weights ORDER BY recorded_at_epoch_ms ASC")
    fun history(): Flow<List<WeightEntity>>

    @Query("DELETE FROM weights WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ReminderDao {
    @Upsert
    suspend fun upsert(row: ReminderEntity): Long

    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface ConditionDao {
    @Query("SELECT * FROM conditions")
    fun observeAll(): Flow<List<ConditionEntity>>

    @Upsert
    suspend fun upsert(condition: ConditionEntity)

    /** Removing a condition is always the user's action. The app never removes one on its own. */
    @Query("DELETE FROM conditions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LabValueDao {
    @Insert
    suspend fun insertAll(values: List<LabValueEntity>)

    @Query("SELECT * FROM lab_values ORDER BY report_date DESC, captured_at_epoch_ms DESC")
    fun observeAll(): Flow<List<LabValueEntity>>

    /** Movement in one value over time, for spec 5.6. Ordered oldest first for charting. */
    @Query("SELECT * FROM lab_values WHERE test_name = :testName ORDER BY report_date ASC")
    fun history(testName: String): Flow<List<LabValueEntity>>
}

@Dao
interface ActivityDao {
    @Upsert
    suspend fun upsert(activity: ActivityEntity)

    @Query("SELECT * FROM activity WHERE date BETWEEN :fromDate AND :toDate ORDER BY date")
    fun range(fromDate: String, toDate: String): Flow<List<ActivityEntity>>
}

@Dao
interface ExerciseSessionDao {
    @Insert
    suspend fun insert(session: ExerciseSessionEntity): Long

    @Query("SELECT * FROM exercise_sessions ORDER BY started_at_epoch_ms DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ExerciseSessionEntity>>
}

@Dao
interface SuggestionDao {
    @Insert
    suspend fun insert(suggestion: SuggestionEntity): Long

    @Query("SELECT * FROM suggestions WHERE meal_id = :mealId ORDER BY created_at_epoch_ms DESC")
    fun forMeal(mealId: Long): Flow<List<SuggestionEntity>>

    /** What was actually said to the person and when, newest first: the stored advice with its meal's time (the Nudges page, 21 Sep). */
    @Query(
        """
        SELECT s.id AS id, s.meal_id AS meal_id, m.logged_at_epoch_ms AS meal_logged_at_epoch_ms,
               s.advice_text AS advice_text, s.trigger_text AS trigger_text, s.triggering_rule_id AS triggering_rule_id,
               s.created_at_epoch_ms AS created_at_epoch_ms
        FROM suggestions s JOIN meals m ON m.id = s.meal_id
        ORDER BY s.created_at_epoch_ms DESC LIMIT :limit
        """
    )
    fun recent(limit: Int): Flow<List<AdviceRow>>

    /**
     * The two most recent suggestions for a meal, which is exactly the before-and-after pair shown
     * at demo beat 4. Differing `input_digest` values are the proof that the input changed.
     */
    @Query(
        "SELECT * FROM suggestions WHERE meal_id = :mealId ORDER BY created_at_epoch_ms DESC LIMIT 2"
    )
    suspend fun lastTwoForMeal(mealId: Long): List<SuggestionEntity>
}

@Dao
interface OverridesDao {
    @Upsert
    suspend fun upsertUnitConversion(override: UnitConversionOverrideEntity)

    @Query("SELECT * FROM unit_conversions")
    suspend fun allUnitConversions(): List<UnitConversionOverrideEntity>

    @Upsert
    suspend fun upsertContextFood(override: ContextFoodOverrideEntity)

    @Query("SELECT * FROM context_foods WHERE life_context = :context")
    suspend fun contextFoods(context: String): List<ContextFoodOverrideEntity>
}

@Dao
interface HouseholdRecipeDao {

    @Query("SELECT * FROM household_recipes WHERE recipe_key = :recipeKey")
    fun observe(recipeKey: String): Flow<HouseholdRecipeEntity?>

    @Query("SELECT * FROM household_recipe_ingredients WHERE recipe_key = :recipeKey")
    suspend fun ingredients(recipeKey: String): List<HouseholdRecipeIngredientEntity>

    @Upsert
    suspend fun upsertHeader(recipe: HouseholdRecipeEntity)

    @Upsert
    suspend fun upsertIngredients(rows: List<HouseholdRecipeIngredientEntity>)

    @Query("DELETE FROM household_recipe_ingredients WHERE recipe_key = :recipeKey")
    suspend fun deleteIngredients(recipeKey: String)

    @Query("DELETE FROM household_recipes WHERE recipe_key = :recipeKey")
    suspend fun deleteHeader(recipeKey: String)

    /**
     * Replace this household's version of a dish, whole.
     *
     * Ingredient rows are deleted and rewritten rather than upserted over, because an ingredient
     * the household REMOVED must go, and an upsert cannot express absence. One transaction, so a
     * reader never sees the header for a new version with the rows of the old one.
     */
    @Transaction
    suspend fun replace(recipe: HouseholdRecipeEntity, rows: List<HouseholdRecipeIngredientEntity>) {
        deleteIngredients(recipe.recipe_key)
        upsertHeader(recipe)
        upsertIngredients(rows)
    }

    /** Back to the bundled reference recipe. */
    @Transaction
    suspend fun revert(recipeKey: String) {
        deleteIngredients(recipeKey)
        deleteHeader(recipeKey)
    }
}

@Dao
interface UnmatchedUtteranceDao {
    @Insert
    suspend fun insert(row: UnmatchedUtteranceEntity)

    @Query("SELECT * FROM unmatched_utterances ORDER BY recorded_at_epoch_ms DESC")
    suspend fun all(): List<UnmatchedUtteranceEntity>

    /** Feeds the measured match rate that replaced the "handles anything from A to Z" target. */
    @Query("SELECT COUNT(*) FROM unmatched_utterances")
    suspend fun count(): Int
}
