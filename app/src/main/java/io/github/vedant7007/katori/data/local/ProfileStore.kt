package io.github.vedant7007.katori.data.local

import io.github.vedant7007.katori.data.local.dao.ConditionDao
import io.github.vedant7007.katori.data.local.dao.ProfileDao
import io.github.vedant7007.katori.data.local.dao.ReminderDao
import io.github.vedant7007.katori.data.local.dao.WeightDao
import io.github.vedant7007.katori.data.local.entity.ConditionEntity
import io.github.vedant7007.katori.data.local.entity.ProfileEntity
import io.github.vedant7007.katori.data.local.entity.ReminderEntity
import io.github.vedant7007.katori.data.local.entity.WeightEntity
import io.github.vedant7007.katori.domain.ConditionSource
import io.github.vedant7007.katori.domain.DietType
import io.github.vedant7007.katori.domain.Goal
import io.github.vedant7007.katori.domain.LifeContext
import io.github.vedant7007.katori.domain.ProfileSnapshot
import io.github.vedant7007.katori.domain.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * THE PROFILE'S ONE WRITE PATH (21 Sep). First run and settings write through here and nowhere
 * else; the row had no writer from any screen before this. The ViewModels read [profile] and
 * show what is there: an absent field is shown as absent, never as a default, because every
 * value in the row is one the person declared.
 *
 * THE SPEECH LANGUAGE HAS ONE HOME, this row (07:33), and [speechLanguage] is the only reader
 * that supplies the default: Hindi, ruled 21 Sep, so a fresh install and a restart come back in
 * Hindi without a tap.
 */
class ProfileStore(
    private val dao: ProfileDao,
    private val conditionDao: ConditionDao?,
    private val weightDao: WeightDao? = null,
    private val reminderDao: ReminderDao? = null,
) {

    @Inject constructor(db: KatoriDatabase) : this(db.profileDao(), db.conditionDao(), db.weightDao(), db.reminderDao())

    /** Every weight the person entered, oldest first; the profile's `weight_kg` is the latest of them. */
    val weights: Flow<List<WeightEntity>> = weightDao?.history() ?: flowOf(emptyList())

    /** Writes the history row and moves the profile's weight to it, in that order. */
    suspend fun recordWeight(kg: Double) {
        require(kg > 0) { "a weight is above zero" }
        weightDao?.insert(WeightEntity(kg = kg, recorded_at_epoch_ms = System.currentTimeMillis(), source = "USER_ENTERED"))
        update { it.copy(weight_kg = kg) }
    }

    suspend fun deleteWeight(id: Long) { weightDao?.delete(id) }

    /** An in-app list, never a system notification (no POST_NOTIFICATIONS in the whitelist). */
    val reminders: Flow<List<ReminderEntity>> = reminderDao?.observeAll() ?: flowOf(emptyList())

    suspend fun setReminder(reminder: ReminderEntity) {
        require(reminder.hour in 0..23 && reminder.minute in 0..59) { "a reminder is a time of day" }
        reminderDao?.upsert(reminder)
    }

    suspend fun deleteReminder(id: Long) { reminderDao?.delete(id) }

    val profile: Flow<ProfileEntity?> = dao.observe()

    /** Declared by the person, or derived from a report; the source travels with each (spec 15.2). */
    val conditions: Flow<List<ConditionEntity>> = conditionDao?.observeAll() ?: flowOf(emptyList())

    suspend fun declareCondition(name: String) {
        val n = name.trim().takeIf(String::isNotEmpty) ?: return
        conditionDao?.upsert(ConditionEntity(name = n, source = ConditionSource.USER_DECLARED.name, recorded_at_epoch_ms = System.currentTimeMillis()))
    }

    suspend fun removeCondition(id: Long) { conditionDao?.delete(id) }

    /** A `SpeechLanguage.tag`: the person's choice, or [DEFAULT_LANGUAGE] until they make one. */
    val speechLanguage: Flow<String> = profile.map { it?.speech_language_tag ?: DEFAULT_LANGUAGE }

    suspend fun setSpeechLanguage(tag: String) = update { it.copy(speech_language_tag = tag) }

    /** First run and settings: every field as the person gave it; null clears it. */
    suspend fun save(
        name: String?,
        ageYears: Int?,
        weightKg: Double?,
        heightCm: Double?,
        sex: String?,
        activity: String?,
        goal: String?,
        lifeContext: String?,
        dietType: String?,
    ) = update {
        it.copy(
            name = name?.trim()?.takeIf(String::isNotEmpty),
            age_years = ageYears, weight_kg = weightKg, height_cm = heightCm, sex = sex,
            activity = activity, goal = goal, life_context = lifeContext, diet_type = dietType,
        )
    }

    private suspend fun update(change: (ProfileEntity) -> ProfileEntity) {
        val current = dao.get() ?: EMPTY
        dao.upsert(change(current).copy(updated_at_epoch_ms = System.currentTimeMillis()))
    }

    companion object {
        const val DEFAULT_LANGUAGE = "hi"

        /** The row before anything was declared: every field absent. */
        val EMPTY = ProfileEntity(
            id = 1, age_years = null, weight_kg = null, height_cm = null, sex = null, goal = null,
            life_context = null, diet_type = null, updated_at_epoch_ms = 0L,
        )
    }
}

/** The row as the rules engine reads it. A null row is a profile with nothing declared. */
fun ProfileEntity?.toSnapshot(avoidedFoodCodes: Set<String> = emptySet()): ProfileSnapshot = ProfileSnapshot(
    ageYears = this?.age_years,
    weightKg = this?.weight_kg,
    heightCm = this?.height_cm,
    sex = this?.sex?.let { enumName<Sex>(it) },
    goal = this?.goal?.let { enumName<Goal>(it) },
    context = this?.life_context?.let { enumName<LifeContext>(it) },
    dietType = this?.diet_type?.let { enumName<DietType>(it) },
    avoidedFoodCodes = avoidedFoodCodes,
)
