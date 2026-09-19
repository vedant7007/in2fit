package io.github.vedant7007.katori.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.vedant7007.katori.data.local.dao.ActivityDao
import io.github.vedant7007.katori.data.local.dao.ConditionDao
import io.github.vedant7007.katori.data.local.dao.ExerciseSessionDao
import io.github.vedant7007.katori.data.local.dao.HouseholdRecipeDao
import io.github.vedant7007.katori.data.local.dao.LabValueDao
import io.github.vedant7007.katori.data.local.dao.MealDao
import io.github.vedant7007.katori.data.local.dao.OverridesDao
import io.github.vedant7007.katori.data.local.dao.ProfileDao
import io.github.vedant7007.katori.data.local.dao.SuggestionDao
import io.github.vedant7007.katori.data.local.dao.UnmatchedUtteranceDao
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
import io.github.vedant7007.katori.domain.model.ConfidenceBand

/**
 * The user's writable database. Everything the user produced or corrected lives here.
 *
 * NOT the food database. Foods, aliases, authored recipes and the shipped unit conversions live
 * in a read-only SQLite file in assets, which has no migrations and is replaced wholesale. Mixing
 * the two means a data refresh either destroys user data or cannot ship.
 *
 * NO DESTRUCTIVE MIGRATION, EVER. This database holds meals, lab values and declared conditions
 * that the user typed in and cannot get back. `fallbackToDestructiveMigration` would silently
 * delete a person's health history on a schema change, so it is not called anywhere and must not
 * be added. Schemas are exported to app/schemas and committed, so a change is a reviewable diff.
 */
@Database(
    entities = [
        MealEntity::class,
        MealItemEntity::class,
        MealItemNutrientEntity::class,
        ProfileEntity::class,
        ConditionEntity::class,
        LabValueEntity::class,
        ActivityEntity::class,
        ExerciseSessionEntity::class,
        SuggestionEntity::class,
        UnitConversionOverrideEntity::class,
        ContextFoodOverrideEntity::class,
        UnmatchedUtteranceEntity::class,
        HouseholdRecipeEntity::class,
        HouseholdRecipeIngredientEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(KatoriConverters::class)
abstract class KatoriDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun profileDao(): ProfileDao
    abstract fun conditionDao(): ConditionDao
    abstract fun labValueDao(): LabValueDao
    abstract fun activityDao(): ActivityDao
    abstract fun exerciseSessionDao(): ExerciseSessionDao
    abstract fun suggestionDao(): SuggestionDao
    abstract fun overridesDao(): OverridesDao
    abstract fun unmatchedUtteranceDao(): UnmatchedUtteranceDao
    abstract fun householdRecipeDao(): HouseholdRecipeDao

    companion object {
        const val NAME = "katori-user.db"

        /**
         * Version 1 to 2: the per-household recipe tables from `0015`.
         *
         * Hand-written CREATE statements, matching what Room generates for the entities exactly,
         * because Room validates the migrated schema against the entities when the database opens
         * and refuses to run on a mismatch. That refusal is the point: it is what stops a wrong
         * migration from quietly corrupting a person's health history. The exported
         * `schemas/.../2.json` is the reference the SQL below was checked against.
         *
         * No `fallbackToDestructiveMigration` here or anywhere. See the class comment.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `household_recipes` (" +
                        "`recipe_key` TEXT NOT NULL, `yield_g` REAL NOT NULL, " +
                        "`servings` REAL NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`recipe_key`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `household_recipe_ingredients` (" +
                        "`recipe_key` TEXT NOT NULL, `food_key` TEXT NOT NULL, " +
                        "`food_source` TEXT NOT NULL, `grams` REAL NOT NULL, `role` TEXT NOT NULL, " +
                        "PRIMARY KEY(`recipe_key`, `food_key`))"
                )
            }
        }

        /** Every migration, in order. AppModule passes this to the builder. */
        val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)
    }
}

/**
 * Converters.
 *
 * Deliberately minimal. Enums are stored by NAME rather than ordinal, because an ordinal silently
 * changes meaning when someone reorders the enum, and here that would relabel every stored
 * confidence band on every meal the user has logged.
 */
class KatoriConverters {
    @TypeConverter
    fun bandToString(band: ConfidenceBand?): String? = band?.name

    @TypeConverter
    fun stringToBand(value: String?): ConfidenceBand? =
        value?.let { runCatching { ConfidenceBand.valueOf(it) }.getOrNull() }
}
