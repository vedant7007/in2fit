package io.github.vedant7007.katori.data.local

import io.github.vedant7007.katori.data.local.entity.LabValueEntity
import io.github.vedant7007.katori.data.local.entity.SuggestionEntity
import io.github.vedant7007.katori.domain.AdviceStore
import io.github.vedant7007.katori.domain.LabStore
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.RuleId
import io.github.vedant7007.katori.domain.StoredAdvice
import io.github.vedant7007.katori.domain.model.Outcome
import java.time.Instant

/**
 * [AdviceStore] over the `suggestions` table. One row per generation, never edited: the two most
 * recent rows for a meal with different `input_digest` values ARE beat 4's proof (spec 8.3).
 * `profile_state_json` was meant for a serialised profile; the digest already covers the
 * profile, so it carries the digest again rather than a second serialisation to drift.
 */
class RoomAdviceStore(private val db: KatoriDatabase) : AdviceStore {

    override suspend fun latest(mealId: Long): StoredAdvice? =
        db.suggestionDao().lastTwoForMeal(mealId).firstOrNull()?.let {
            StoredAdvice(
                phrased = it.advice_text.ifEmpty { null },
                triggerText = it.trigger_text,
                triggerRuleId = it.triggering_rule_id?.let(::RuleId),
                inputDigest = it.input_digest,
                createdAt = Instant.ofEpochMilli(it.created_at_epoch_ms),
            )
        }

    override suspend fun save(mealId: Long, advice: StoredAdvice) {
        db.suggestionDao().insert(
            SuggestionEntity(
                meal_id = mealId,
                advice_text = advice.phrased.orEmpty(),
                trigger_text = advice.triggerText,
                triggering_rule_id = advice.triggerRuleId?.value,
                input_digest = advice.inputDigest,
                profile_state_json = advice.inputDigest,
                created_at_epoch_ms = advice.createdAt.toEpochMilli(),
            )
        )
    }
}

/** [LabStore] over `lab_values`. The printed range travels as read; a null bound stays null. */
class RoomLabStore(private val db: KatoriDatabase) : LabStore {
    override suspend fun save(values: List<LabValue>): Outcome<Int> {
        val now = System.currentTimeMillis()
        db.labValueDao().insertAll(
            values.map {
                LabValueEntity(
                    test_name = it.testName, value = it.value, unit = it.unit,
                    reference_low = it.referenceLow, reference_high = it.referenceHigh,
                    report_date = it.reportDate.toString(), captured_at_epoch_ms = now,
                )
            }
        )
        return Outcome.Ok(values.size)
    }
}
