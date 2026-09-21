package io.github.vedant7007.katori.data.local

import io.github.vedant7007.katori.data.local.dao.ProfileDao
import io.github.vedant7007.katori.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow
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
class ProfileStore(private val dao: ProfileDao) {

    @Inject constructor(db: KatoriDatabase) : this(db.profileDao())

    val profile: Flow<ProfileEntity?> = dao.observe()

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
