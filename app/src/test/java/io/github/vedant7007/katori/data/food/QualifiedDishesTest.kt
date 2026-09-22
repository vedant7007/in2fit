package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The bug class `0041` found, swept: a qualified dish name that contains a base ingredient's
 * name collapses onto that ingredient through containment. "fried rice" became plain rice.
 *
 * WRONG FOOD is the number, and here it has a precise meaning: a name in
 * `data-authoring/qualified-dishes.csv` resolving to its `base` ingredient (or to any food other
 * than the one the row names). The sweep is authored and the count it prints is what was found,
 * not a census; a dish nobody wrote down can still collapse, and the recorded speakers are the
 * next sweep.
 */
class QualifiedDishesTest {

    private lateinit var db: FoodDbSource
    private lateinit var lookup: SqliteFoodLookup

    @Before fun setUp() { db = JdbcFoodDbSource.openBundled(); lookup = SqliteFoodLookup(db) }
    @After fun tearDown() = db.close()

    private data class Row(val utterance: String, val base: String, val expect: String, val note: String)

    private fun rows(): List<Row> {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        return File(dir, "data-authoring/qualified-dishes.csv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split(",") }
            .map { Row(it[0].trim(), it[1].trim(), it[2].trim(), it.getOrElse(3) { "" }.trim()) }
    }

    @Test fun `sweep the qualified dish names and prove none collapses onto an ingredient`() = runBlocking {
        val all = rows()
        val collapsed = mutableListOf<String>()
        val wrongOther = mutableListOf<String>()
        var right = 0
        val report = StringBuilder("=== QUALIFIED DISHES, the containment sweep ===\n")
        for (r in all) {
            val out = lookup.resolve(FoodQuery(r.utterance, "en"))
            val got = when (out) {
                is Outcome.Ok -> out.value.code.id
                is Outcome.Unavailable -> if (out.reason == UnavailableReason.KNOWN_ITEM_NO_DATA) "NODATA" else "MISS"
                is Outcome.NotImplemented -> "NOT_IMPLEMENTED"
            }
            val ok = when {
                r.expect.startsWith("NODATA:") -> got == "NODATA"
                r.expect == "MISS" -> got == "MISS" || got == "NODATA"
                else -> got == r.expect
            }
            if (ok) right++
            else {
                val line = "'${r.utterance}' -> $got, expected ${r.expect}"
                // A collapse is a resolution to a FOOD whose key carries the base word; a wrong recipe is wrong in another way.
                val isFood = out is Outcome.Ok && out.value.code.source != io.github.vedant7007.katori.domain.model.DataSource.AUTHORED_RECIPE
                if (isFood && (out.value.code.id.contains(r.base) || r.base.contains(out.value.code.id))) collapsed += line else wrongOther += line
            }
            report.appendLine("  ${r.utterance.padEnd(22)} ${got.padEnd(18)} ${if (ok) "ok" else "WRONG"}   [${r.expect}]")
        }
        report.appendLine("  names swept                 ${all.size}")
        report.appendLine("  handled right               $right")
        report.appendLine("  COLLAPSED onto the base     ${collapsed.size}   <- the bug class; must be zero")
        report.appendLine("  wrong in another way        ${wrongOther.size}")
        println(report)
        assertEquals("a qualified dish collapsed onto its base ingredient:\n" + collapsed.joinToString("\n"), 0, collapsed.size)
        assertEquals("a qualified dish resolved wrongly:\n" + wrongOther.joinToString("\n"), 0, wrongOther.size)
    }
}
