package io.github.vedant7007.katori.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.vedant7007.katori.domain.model.ConfidenceBand

/**
 * The user's writable database: the eleven tables of spec 8.3, plus one normalising table
 * documented at [MealItemNutrientEntity].
 *
 * SEPARATE FROM THE BUNDLED FOOD DATABASE. Foods, nutrients, portions, authored recipes and the
 * shipped default unit conversions live in a read-only SQLite file inside the APK, which is not a
 * Room database and has no migrations. This database holds only what the user produced or corrected.
 *
 * TOTALS ARE DERIVED, NEVER STORED. There is no totals column on [MealEntity], deliberately. Storing
 * a total beside its components guarantees the two drift apart after any edit, and in a health app
 * the visible number is then wrong in a way nothing detects. Totals are computed by query; see
 * MealDao.
 */

@Entity(
    tableName = "meals",
    indices = [Index("logged_at_epoch_ms")],
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logged_at_epoch_ms: Long,
    /** Exactly what the recogniser returned. Kept verbatim for correction and for the test set. */
    val raw_transcript: String?,
    /** The worst band across this meal's items, recomputed on every edit. Never a percentage. */
    val confidence_band: ConfidenceBand,
    val language_tag: String?,
)

@Entity(
    tableName = "meal_items",
    foreignKeys = [ForeignKey(
        entity = MealEntity::class,
        parentColumns = ["id"],
        childColumns = ["meal_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("meal_id")],
)
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meal_id: Long,
    /** The item as the user said it, before matching. Never overwritten by the match. */
    val spoken_name: String,
    val quantity: Double?,
    val unit: String?,
    val grams: Double?,
    /** Null when nothing matched. A null here plus a row in unmatched_utterances is the gap record. */
    val food_source: String?,
    val food_id: String?,
    val confidence_band: ConfidenceBand,
    /** Machine-readable reasons behind the band, comma-separated ConfidenceReason names. */
    val confidence_reasons: String,
)

/**
 * Per-item nutrition, one row per nutrient.
 *
 * DEVIATION FROM SPEC 8.3, STATED: spec lists per-item nutrition as columns on `meal_items`. It is a
 * separate table here for two reasons. First, the three-state distinction between a measured value,
 * a genuine zero and an unknown cannot be expressed in one nullable column without an encoding that
 * a future reader will misread, and collapsing unknown to zero is the most consequential bug this
 * project can ship. Second, totals must be derived, and deriving them in SQL with a correct partial
 * flag needs the values as rows. Adding a nutrient is then a data change, not a migration.
 */
@Entity(
    tableName = "meal_item_nutrients",
    primaryKeys = ["meal_item_id", "nutrient"],
    foreignKeys = [ForeignKey(
        entity = MealItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["meal_item_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class MealItemNutrientEntity(
    val meal_item_id: Long,
    /** Nutrient enum name. */
    val nutrient: String,
    /** MEASURED, ASSUMED_ZERO or UNKNOWN. Never inferred from a null amount. */
    val state: String,
    /** Set only when state is MEASURED. */
    val amount: Double?,
    val unit: String,
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val age_years: Int?,
    val weight_kg: Double?,
    val height_cm: Double?,
    val sex: String?,
    val goal: String?,
    /** LifeContext enum name. Declared by the user at onboarding, never inferred (spec 4.2). */
    val life_context: String?,
    val diet_type: String?,
    val updated_at_epoch_ms: Long,
)

/**
 * Conditions. [source] is a safety-relevant field: a REPORT_DERIVED flag may be acted on but must
 * never be worded as a diagnosis (spec 15.1, 15.2).
 */
@Entity(tableName = "conditions", indices = [Index("name", unique = true)])
data class ConditionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** USER_DECLARED or REPORT_DERIVED. */
    val source: String,
    val recorded_at_epoch_ms: Long,
)

/**
 * Values read off a report.
 *
 * The reference range is the one PRINTED ON THAT REPORT and may be null when it could not be read.
 * The app carries no reference ranges of its own, because they differ by laboratory and assay. No
 * rule fires on a value whose range is null.
 */
@Entity(tableName = "lab_values", indices = [Index("report_date"), Index("test_name")])
data class LabValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val test_name: String,
    val value: Double,
    val unit: String,
    val reference_low: Double?,
    val reference_high: Double?,
    /** ISO-8601 date as printed on the report. */
    val report_date: String,
    val captured_at_epoch_ms: Long,
)

@Entity(tableName = "activity", indices = [Index("date", unique = true)])
data class ActivityEntity(
    @PrimaryKey val date: String,
    val steps: Int?,
    val active_minutes: Int?,
    val heart_rate_bpm: Int?,
)

@Entity(tableName = "exercise_sessions", indices = [Index("started_at_epoch_ms")])
data class ExerciseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val movement: String,
    val reps: Int,
    /** Rule-derived score from measured joint angles, not a learned judgement. */
    val form_score: Int?,
    val corrections_given: String?,
    val started_at_epoch_ms: Long,
)

/**
 * What was suggested, and the state that produced it.
 *
 * This table is what makes demo beat 4 verifiable rather than asserted. [triggering_rule_id] and
 * [input_digest] come from the rules engine, so the before and after can be shown as two rows with
 * different digests and a named rule between them, instead of two pieces of prose that merely read
 * differently.
 */
@Entity(tableName = "suggestions", indices = [Index("created_at_epoch_ms")])
data class SuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meal_id: Long?,
    val advice_text: String,
    /** The rules-engine sentence. Templated, not generated. Shown above the advice at beat 4. */
    val trigger_text: String?,
    val triggering_rule_id: String?,
    /** Digest of the RuleInput that produced this. Equal digests must mean equal advice. */
    val input_digest: String,
    /** Serialised ProfileSnapshot at generation time, for the before-and-after comparison. */
    val profile_state_json: String,
    val created_at_epoch_ms: Long,
)

/**
 * USER OVERRIDES ONLY. The shipped defaults live in the bundled read-only database.
 *
 * DEVIATION NOTE: spec 8.3 lists unit_conversions as a table in the local store. Splitting shipped
 * defaults from user corrections is what lets a data refresh replace the defaults without destroying
 * a correction the user made, and what lets a correction outlive a bundled-database update.
 */
@Entity(tableName = "unit_conversions", primaryKeys = ["unit", "food_class"])
data class UnitConversionOverrideEntity(
    val unit: String,
    val food_class: String,
    val grams: Double,
    val updated_at_epoch_ms: Long,
)

/**
 * USER OVERRIDES ONLY, same split as above. The shipped constrained ingredient list per life context
 * (spec 4.3) is bundled; this table records foods the user added or removed for themselves.
 */
@Entity(tableName = "context_foods", primaryKeys = ["life_context", "food_source", "food_id"])
data class ContextFoodOverrideEntity(
    val life_context: String,
    val food_source: String,
    val food_id: String,
    /** True when the user added this food, false when they removed a bundled one. */
    val included: Boolean,
)

/**
 * Every utterance the matcher failed on.
 *
 * Required by spec 13.5. This is how coverage gaps get closed against what people actually say,
 * rather than against what the team imagines they will say, and it is the input to the measured
 * match rate that replaced the unfalsifiable "handles anything from A to Z" target.
 */
@Entity(tableName = "unmatched_utterances", indices = [Index("recorded_at_epoch_ms")])
data class UnmatchedUtteranceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spoken_name: String,
    val full_transcript: String?,
    val language_tag: String,
    val recorded_at_epoch_ms: Long,
)
