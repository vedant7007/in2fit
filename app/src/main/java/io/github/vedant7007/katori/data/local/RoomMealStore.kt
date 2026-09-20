package io.github.vedant7007.katori.data.local

import androidx.room.withTransaction
import io.github.vedant7007.katori.data.local.entity.MealEntity
import io.github.vedant7007.katori.data.local.entity.MealItemEntity
import io.github.vedant7007.katori.data.local.entity.MealItemNutrientEntity
import io.github.vedant7007.katori.domain.MealItemSnapshot
import io.github.vedant7007.katori.domain.MealSnapshot
import io.github.vedant7007.katori.domain.MealStore
import io.github.vedant7007.katori.domain.ResolvedMeal
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientValue
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * [MealStore] over Room: the one write to the timeline, in one transaction.
 *
 * WHAT IS WRITTEN PER NUTRIENT. One row per nutrient per item, in the item's three states:
 * MEASURED with the amount, ASSUMED_ZERO with none, UNKNOWN with none. A nutrient the profile
 * does not mention is UNKNOWN and is still written, so the DAO's totals count it as a hole; an
 * absent row would read as "no contributor" and the total would silently become a total.
 *
 * Nothing here computes a total. The meal's band is the worst across its items, as the entity
 * says, and it is recomputed from the items' reasons on every edit by whoever edits.
 */
class RoomMealStore(private val db: KatoriDatabase, private val languageTag: () -> String? = { null }) : MealStore {

    override suspend fun save(meal: ResolvedMeal, loggedAt: Instant): Outcome<Long> {
        if (meal.items.isEmpty()) {
            return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "a meal with no items is not a meal")
        }
        val id = db.withTransaction {
            val dao = db.mealDao()
            val mealId = dao.insertMeal(
                MealEntity(
                    logged_at_epoch_ms = loggedAt.toEpochMilli(),
                    raw_transcript = meal.parsed.rawTranscript,
                    confidence_band = meal.parsed.confidence.band,
                    language_tag = languageTag(),
                )
            )
            val itemIds = dao.insertItems(
                meal.items.mapIndexed { i, item ->
                    val parsed = meal.parsed.items.getOrNull(i)
                    MealItemEntity(
                        meal_id = mealId,
                        spoken_name = parsed?.spokenName ?: item.snapshot.displayName,
                        quantity = parsed?.quantity,
                        unit = parsed?.unit,
                        grams = item.snapshot.grams,
                        food_source = item.source?.name,
                        food_id = item.snapshot.foodCode,
                        confidence_band = item.confidence.band,
                        confidence_reasons = item.confidence.reasons.joinToString(",") { it.name },
                    )
                }
            )
            dao.insertNutrients(
                meal.items.flatMapIndexed { i, item ->
                    Nutrient.entries.map { n ->
                        val (state, amount) = when (val v = item.nutrients[n]) {
                            is NutrientValue.Measured -> "MEASURED" to v.amount
                            NutrientValue.AssumedZero -> "ASSUMED_ZERO" to null
                            NutrientValue.Unknown -> "UNKNOWN" to null
                        }
                        MealItemNutrientEntity(meal_item_id = itemIds[i], nutrient = n.name, state = state, amount = amount, unit = n.unit.name)
                    }
                }
            )
            mealId
        }
        return Outcome.Ok(id)
    }

    /** The meal as the rules engine sees it: measured amounts only; an Unknown is simply absent from the map. */
    override suspend fun meal(mealId: Long): MealSnapshot? {
        val dao = db.mealDao()
        val meal = dao.meal(mealId) ?: return null
        val items = dao.itemsFor(mealId)
        val nutrients = dao.measuredNutrientsFor(mealId).groupBy({ it.meal_item_id }) { it.nutrient to it.amount }
        return MealSnapshot(
            mealId = meal.id,
            items = items.map { i ->
                MealItemSnapshot(
                    displayName = i.spoken_name, foodCode = i.food_id, grams = i.grams,
                    nutrients = nutrients[i.id].orEmpty().mapNotNull { (n, a) -> Nutrient.entries.firstOrNull { it.name == n }?.let { it to a } }.toMap(),
                )
            },
            loggedAt = Instant.ofEpochMilli(meal.logged_at_epoch_ms),
        )
    }

    override suspend fun latestMealId(): Long? = db.mealDao().recentMeals(1).first().firstOrNull()?.id
}
