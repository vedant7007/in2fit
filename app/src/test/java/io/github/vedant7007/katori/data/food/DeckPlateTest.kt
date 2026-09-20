package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.time.ZoneId

/**
 * THE SLIDE FOLLOWS THE CODE. The deck's phone mockup quotes a plate for "rendu roti and one
 * katori dal"; this prints what the shipped resolver renders for that plate, line by line, in
 * the screen's own English templates (`meal_item_with_quantity`, `plate_unit_taken_as`,
 * `context_figure`, `figure_with_band`), so the deck can be checked against it and never the
 * other way round. Written to `logs/deck-plate.md` on every run.
 */
class DeckPlateTest {

    companion object {
        private lateinit var db: JdbcFoodDbSource
        private lateinit var resolver: LookupMealResolver
        @JvmStatic @BeforeClass fun open() { db = JdbcFoodDbSource.openBundled(); resolver = LookupMealResolver(SqliteFoodLookup(db)) }
        @JvmStatic @AfterClass fun close() = db.close()
    }

    private val context = ContextText(ContextText.ENGLISH, TriggerText.ENGLISH, ZoneId.of("Asia/Kolkata"))

    private fun item(name: String, quantity: Double?, unit: String?) = ParsedItem(
        name, quantity, unit, null,
        ConfidenceRules.of(if (quantity == null) ConfidenceReason.QUANTITY_INFERRED else ConfidenceReason.QUANTITY_STATED),
    )

    @Test fun `the deck's plate, as the app renders it`() {
        // "rendu roti and one katori dal": the extraction the prompt is asked for is roti × 2 (no
        // unit: a count) and dal × 1 katori (the unit said).
        val parsed = ParsedMeal(listOf(item("roti", 2.0, null), item("dal", 1.0, "katori")), ConfidenceRules.of(ConfidenceReason.QUANTITY_STATED), "rendu roti and one katori dal")
        val meal = (runBlocking { resolver.resolve(parsed, "en-IN") } as Outcome.Ok).value
        val lines = buildList {
            for ((ri, pi) in meal.items.zip(meal.parsed.items)) {
                // meal_item_with_quantity "%1$s: %2$s %3$s", then plate_unit_taken_as "taken as %1$s g" ONLY when inferred (0035)
                val q = pi.quantity?.let { "%.0f".format(it) } ?: ""
                val face = "${pi.spokenName}: $q ${pi.unit.orEmpty()}".trim()
                val taken = ri.snapshot.grams?.let { g ->
                    if (ConfidenceReason.QUANTITY_INFERRED in ri.confidence.reasons) " · taken as ${"%.0f".format(g)} g" else ""
                }.orEmpty()
                add("$face$taken (${ri.snapshot.displayName}, ${"%.0f".format(ri.snapshot.grams ?: 0.0)} g, ${ri.confidence.band.name.lowercase()})")
            }
            for (f in meal.figures.filter { it.total.nutrient in setOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT) }) {
                // context_figure "%1$s: %2$s %3$s" inside figure_with_band "%1$s (%2$s)"
                add("${context.figure(f)} (${f.confidence.band.name.lowercase().replaceFirstChar { it.uppercase() }})")
            }
        }
        val out = buildString {
            appendLine("# The deck's plate, as the app renders it")
            appendLine()
            appendLine("Utterance: \"rendu roti and one katori dal\". Extraction: roti × 2, dal × 1 katori. Shipped database, `LookupMealResolver`, 0035.")
            appendLine()
            lines.forEach { appendLine("    $it") }
        }
        println(out)
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        File(dir, "logs").mkdirs()
        File(dir, "logs/deck-plate.md").writeText(out)
        assertTrue(lines.size >= 4)
    }
}
