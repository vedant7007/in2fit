package io.github.vedant7007.katori.ui

import io.github.vedant7007.katori.data.local.Diary
import io.github.vedant7007.katori.data.local.entity.LabValueEntity
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientUnit
import io.github.vedant7007.katori.domain.model.NutritionFigure
import java.time.Instant
import java.time.LocalDate

/**
 * What the Today, Diary, Trends and Reports screens are handed (21 Sep): every number here was
 * produced by a query or the engine and carries its band, its completeness and its sources; a
 * screen shows it or shows nothing. There is no arithmetic on these in `ui/`.
 */

/** One derived total. [text] is the same sentence the orchestrator speaks for it. */
data class ShownFigure(
    val nutrient: Nutrient,
    /** The amount; a FLOOR when [completeness] is PARTIAL, and not a figure at all when NONE. */
    val amount: Double,
    val unit: NutrientUnit,
    val completeness: Completeness,
    /** Items with no value for this nutrient, named, when PARTIAL or NONE. */
    val missing: List<String>,
    val band: ConfidenceBand,
    val sources: List<DataSource>,
    val text: String,
)

fun NutritionFigure.shown(contextText: ContextText) = ShownFigure(
    total.nutrient, total.amount, total.unit, total.completeness, total.unknownContributors, confidence.band, sources, contextText.figure(this),
)

/** One item of a logged meal, as stored. */
data class ShownItem(
    val id: Long,
    /** The catalogue's name when the item resolved, else the word as said. */
    val name: String,
    val spokenName: String,
    /** The amount as said, or the household amount assumed and written back (0035). */
    val quantity: Double?,
    val unit: String?,
    /** What the figures were computed from; null for a no-data item. */
    val grams: Double?,
    val inferred: Boolean,
    val band: ConfidenceBand,
    /** Measured amounts only, for the per-item lines; an unknown nutrient is absent. */
    val nutrients: Map<Nutrient, Double>,
)

data class ShownMeal(
    val id: Long,
    val loggedAt: Instant,
    val items: List<ShownItem>,
    val figures: List<ShownFigure>,
    val rawTranscript: String?,
) {
    /** The energy figure, if the meal has one. */
    val energy: ShownFigure? get() = figures.firstOrNull { it.nutrient == Nutrient.ENERGY }
}

fun Diary.Meal.shown(contextText: ContextText) = ShownMeal(
    id, loggedAt,
    items = items.map {
        val reasons = it.confidence_reasons.split(',').map(String::trim)
        ShownItem(
            id = it.id,
            name = it.display_name ?: it.spoken_name,
            spokenName = it.spoken_name,
            quantity = it.quantity, unit = it.unit, grams = it.grams,
            inferred = ConfidenceReason.QUANTITY_INFERRED.name in reasons,
            band = it.confidence_band,
            nutrients = itemNutrients[it.id].orEmpty(),
        )
    },
    figures = figures.map { it.shown(contextText) },
    rawTranscript = rawTranscript,
)

data class ShownDay(val date: LocalDate, val meals: List<ShownMeal>, val figures: List<ShownFigure>) {
    val energy: ShownFigure? get() = figures.firstOrNull { it.nutrient == Nutrient.ENERGY }
}

fun Diary.Day.shown(contextText: ContextText) = ShownDay(date, meals.map { it.shown(contextText) }, figures.map { it.shown(contextText) })

/**
 * Against the range PRINTED ON THE REPORT and nothing else (spec 15.1, 0018): the screen says
 * "below the printed range", never "low", never a condition. NO_RANGE when the report printed
 * none, and then no flag at all.
 */
enum class LabStatus { BELOW, ABOVE, WITHIN, NO_RANGE }

data class LabRow(
    val id: Long,
    val testName: String,
    val value: Double,
    val unit: String,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    val reportDate: LocalDate,
    val status: LabStatus,
)

fun LabValueEntity.shown() = LabRow(
    id, test_name, value, unit, reference_low, reference_high, LocalDate.parse(report_date),
    status = when {
        reference_low == null && reference_high == null -> LabStatus.NO_RANGE
        reference_low != null && value < reference_low -> LabStatus.BELOW
        reference_high != null && value > reference_high -> LabStatus.ABOVE
        else -> LabStatus.WITHIN
    },
)
