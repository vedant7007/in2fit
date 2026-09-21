package io.github.vedant7007.katori.ui

import io.github.vedant7007.katori.data.local.Diary
import io.github.vedant7007.katori.data.local.dao.NutrientTotalRow
import io.github.vedant7007.katori.data.local.entity.LabValueEntity
import io.github.vedant7007.katori.data.local.entity.MealItemEntity
import io.github.vedant7007.katori.data.local.figureOf
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The derivations the screens are handed, on the JVM: a DAO row becomes a figure with its
 * completeness and its holes named; a lab value is flagged only against the range printed on
 * its own report; a streak is consecutive days with a meal ending today. The queries behind them
 * run on the device and are on Rao's list.
 */
class ShownTest {

    private val contextText = ContextText(ContextText.ENGLISH, TriggerText.ENGLISH)

    private fun item(reasons: String, source: String? = "AUTHORED_RECIPE") = MealItemEntity(
        id = 1, meal_id = 1, spoken_name = "dal", quantity = 1.0, unit = "katori", grams = 180.0,
        food_source = source, food_id = "toor_dal_tadka", confidence_band = ConfidenceBand.ROUGH, confidence_reasons = reasons,
    )

    @Test
    fun `a total with a hole is a floor that names the hole, and one with no measured contributor is not a figure`() {
        val items = listOf(item("EXACT_FOOD_MATCH,QUANTITY_INFERRED,HOUSEHOLD_UNIT_DEFAULT"))
        val partial = figureOf(NutrientTotalRow("PROTEIN", "G", 10.8, unknown_count = 1, measured_count = 1), listOf("kadha"), Diary.reasonsOf(items), Diary.sourcesOf(items))!!
        assertEquals(Completeness.PARTIAL, partial.total.completeness)
        assertEquals(listOf("kadha"), partial.total.unknownContributors)
        assertEquals(ConfidenceBand.ROUGH, partial.confidence.band)
        assertEquals(listOf(DataSource.AUTHORED_RECIPE), partial.sources)
        val shown = partial.shown(contextText)
        assertEquals(10.8, shown.amount, 0.0)
        assertEquals(listOf("kadha"), shown.missing)

        val none = figureOf(NutrientTotalRow("VITAMIN_B12", "UG", 0.0, unknown_count = 2, measured_count = 0), listOf("dal", "kadha"), emptyList(), emptyList())!!
        assertEquals(Completeness.NONE, none.total.completeness)
        // A row written before reasons were recorded is not GOOD by default.
        assertEquals(ConfidenceBand.ROUGH, figureOf(NutrientTotalRow("ENERGY", "KCAL", 300.0, 0, 2), emptyList(), emptyList(), emptyList())!!.confidence.band)
        assertNull("an unknown nutrient name is not a figure", figureOf(NutrientTotalRow("CAFFEINE", "MG", 1.0, 0, 1), emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun `a lab value is flagged against the printed range only`() {
        fun lab(v: Double, low: Double?, high: Double?) = LabValueEntity(1, "Haemoglobin", v, "g/dL", low, high, "2026-09-12", 0L).shown()
        assertEquals(LabStatus.BELOW, lab(9.8, 13.0, 17.0).status)
        assertEquals(LabStatus.ABOVE, lab(17.5, 13.0, 17.0).status)
        assertEquals(LabStatus.WITHIN, lab(13.0, 13.0, 17.0).status)
        assertEquals(LabStatus.WITHIN, lab(17.0, 13.0, 17.0).status)
        assertEquals("no printed range, no flag", LabStatus.NO_RANGE, lab(9.8, null, null).status)
        assertEquals("a one-sided range still flags on that side", LabStatus.ABOVE, lab(7.0, null, 6.4).status)
        assertEquals(LocalDate.of(2026, 9, 12), lab(9.8, 13.0, 17.0).reportDate)
    }

    @Test
    fun `a streak is consecutive days with a meal ending on the last day`() {
        val meal = Diary.Meal(1, Instant.EPOCH, emptyList(), emptyMap(), emptyList(), null, null)
        fun day(d: Int, logged: Boolean) = Diary.Day(LocalDate.of(2026, 9, d), if (logged) listOf(meal) else emptyList(), emptyList())
        assertEquals(3, Diary.streak(listOf(day(15, true), day(16, false), day(17, true), day(18, true), day(19, true))))
        assertEquals(0, Diary.streak(listOf(day(17, true), day(18, true), day(19, false))))
        assertEquals(0, Diary.streak(emptyList()))
    }

    @Test
    fun `a logged item shows the catalogue name when it has one and the word said when it does not`() {
        val meal = Diary.Meal(
            id = 7, loggedAt = Instant.EPOCH,
            items = listOf(
                item("EXACT_FOOD_MATCH,QUANTITY_INFERRED,HOUSEHOLD_UNIT_DEFAULT").copy(display_name = "Dal tadka"),
                item("EXACT_FOOD_MATCH,QUANTITY_STATED", source = null).copy(id = 2, spoken_name = "kadha", food_id = null, grams = null, display_name = null),
            ),
            itemNutrients = mapOf(1L to mapOf(Nutrient.PROTEIN to 10.8)),
            figures = emptyList(), rawTranscript = "a little dal and a kadha", source = "SPOKEN",
        ).shown(contextText)
        assertEquals("Dal tadka", meal.items[0].name)
        assertEquals(true, meal.items[0].inferred)
        assertEquals(mapOf(Nutrient.PROTEIN to 10.8), meal.items[0].nutrients)
        assertEquals("kadha", meal.items[1].name)
        assertNull(meal.items[1].grams)
        assertEquals(emptyMap<Nutrient, Double>(), meal.items[1].nutrients)
        assertEquals(false, meal.items[1].inferred)
    }
}
