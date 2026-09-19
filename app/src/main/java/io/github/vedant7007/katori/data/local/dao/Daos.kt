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

    @Query("DELETE FROM meal_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)
}

/** Projection for a derived total. Carries the completeness counts, never a bare number. */
data class NutrientTotalRow(
    val nutrient: String,
    val unit: String,
    val total: Double,
    val unknown_count: Int,
    val measured_count: Int,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

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
