package io.github.vedant7007.katori.data.local

import io.github.vedant7007.katori.data.local.entity.LabValueEntity
import io.github.vedant7007.katori.data.local.entity.MealItemNutrientEntity
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "SHARE WITH YOUR DOCTOR" (the design's row; Ira's (ab)): the diary and the lab values as CSV
 * text, straight from the rows the DAOs hold, for the system share sheet. Text, not a file: a
 * `FileProvider` and a permission are not needed to hand text to another app, and the demo
 * build's permission whitelist stays exactly as it is.
 *
 * Every figure here is a stored row: an item's measured nutrients with their state, never a
 * total (totals are derived, and a doctor's spreadsheet can sum what it likes). Nothing is
 * rounded, nothing is labelled beyond what the row says, and the source column travels with
 * every food.
 */
object Export {

    val MEAL_HEADER = listOf("logged_at", "meal_id", "logged_by", "item", "spoken_as", "quantity", "unit", "grams", "food_source", "food_id", "band", "nutrient", "amount", "nutrient_unit", "state")
    val LAB_HEADER = listOf("report_date", "test_name", "value", "unit", "reference_low", "reference_high")

    /** [nutrients]: each item's stored nutrient rows by item id, state and amount as written. */
    fun mealsCsv(meals: List<Diary.Meal>, nutrients: Map<Long, List<MealItemNutrientEntity>>, zone: ZoneId): String {
        val at = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val lines = mutableListOf(MEAL_HEADER.joinToString(","))
        meals.sortedBy { it.loggedAt }.forEach { m ->
            m.items.forEach { i ->
                val head = listOf(
                    at.format(m.loggedAt.atZone(zone)), m.id.toString(), m.source.orEmpty(), i.display_name ?: i.spoken_name, i.spoken_name,
                    i.quantity?.toString().orEmpty(), i.unit.orEmpty(), i.grams?.toString().orEmpty(), i.food_source.orEmpty(), i.food_id.orEmpty(), i.confidence_band.name,
                )
                val rows = nutrients[i.id].orEmpty().sortedBy { it.nutrient }
                if (rows.isEmpty()) lines += (head + listOf("", "", "", "")).joinToString(",") { cell(it) }
                rows.forEach { n ->
                    lines += (head + listOf(n.nutrient, n.amount?.toString().orEmpty(), n.unit, n.state)).joinToString(",") { cell(it) }
                }
            }
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    fun labsCsv(labs: List<LabValueEntity>): String {
        val lines = mutableListOf(LAB_HEADER.joinToString(","))
        labs.sortedWith(compareBy({ it.report_date }, { it.test_name })).forEach { l ->
            lines += listOf(l.report_date, l.test_name, l.value.toString(), l.unit, l.reference_low?.toString().orEmpty(), l.reference_high?.toString().orEmpty()).joinToString(",") { cell(it) }
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    /** RFC 4180: a cell with a comma, a quote or a line break is quoted, and a quote is doubled. */
    fun cell(s: String): String = if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

/** Every meal and every lab value in the database, as two CSV texts. */
suspend fun Diary.exportCsv(): Pair<String, String> {
    val meals = meals(0L..Long.MAX_VALUE).first()
    val nutrients = meals.flatMap { it.items }.associate { item -> item.id to nutrientRows(item.id) }
    return Export.mealsCsv(meals, nutrients, zone) to Export.labsCsv(labs().first())
}
