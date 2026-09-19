package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Measures the match rate over the authored utterance set, per spec 13.5.
 *
 * Spec 13.5 originally demanded the system "handle anything a user might say". That is not a
 * usable acceptance criterion: it is unfalsifiable, and it drives unbounded work on the exact
 * package the demo depends on. This test is what replaced it.
 *
 * THE NUMBER THAT MATTERS IS NOT THE MATCH RATE. It is WRONG_FOOD, which must be zero. A miss is
 * honest and the user is asked; a wrong food silently puts a wrong number in a health app. The
 * match rate is reported so the corpus can be grown against evidence, and it is asserted only at
 * a floor so a regression is caught.
 *
 * The utterance set is authored, not recorded. It is replaced by real transcripts when the
 * recordings in spec 18.3 exist.
 */
class MatchRateTest {

    private lateinit var db: FoodDbSource
    private lateinit var lookup: SqliteFoodLookup

    @Before fun setUp() {
        db = JdbcFoodDbSource.openBundled()
        lookup = SqliteFoodLookup(db)
    }

    @After fun tearDown() = db.close()

    private data class Row(val utterance: String, val expect: String, val lang: String, val note: String)

    private fun rows(): List<Row> {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        val f = File(dir, "data-authoring/utterance-test-set.csv")
        check(f.isFile) { "utterance set not found at ${f.absolutePath}" }
        return f.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .drop(1)
            .map { line ->
                val p = line.split(",")
                Row(p[0].trim(), p.getOrElse(1) { "" }.trim(), p.getOrElse(2) { "" }.trim(), p.getOrElse(3) { "" }.trim())
            }
            .filter { it.utterance.isNotEmpty() && it.expect.isNotEmpty() }
    }

    @Test
    fun `measure the match rate and prove no utterance resolves to the wrong food`() = runBlocking {
        val all = rows()
        assertTrue("utterance set looks empty", all.size > 50)

        var correct = 0
        var wrongFood = 0
        var noDataCorrect = 0
        var noDataMissed = 0
        var honestMiss = 0
        var unexpectedMiss = 0
        val wrong = mutableListOf<String>()
        val missed = mutableListOf<String>()

        for (r in all) {
            val outcome = lookup.resolve(FoodQuery(r.utterance, r.lang))
            when {
                r.expect.startsWith("NODATA:") -> {
                    val key = r.expect.removePrefix("NODATA:")
                    if (outcome is Outcome.Unavailable && outcome.reason == UnavailableReason.KNOWN_ITEM_NO_DATA) {
                        noDataCorrect++
                    } else if (outcome is Outcome.Ok) {
                        // The worst possible result: a deliberately no-data item resolved to a food.
                        wrongFood++
                        wrong += "${r.utterance} -> ${outcome.value.code.id} (must be no-data '$key')"
                    } else {
                        noDataMissed++
                        missed += "${r.utterance} (expected no-data '$key', got plain no match)"
                    }
                }
                r.expect == "MISS" -> {
                    if (outcome is Outcome.Ok) {
                        wrongFood++
                        wrong += "${r.utterance} -> ${outcome.value.code.id} (expected no match; a composed dish must not resolve to an ingredient)"
                    } else honestMiss++
                }
                else -> {
                    if (outcome is Outcome.Ok) {
                        if (outcome.value.code.id == r.expect) correct++
                        else {
                            wrongFood++
                            wrong += "${r.utterance} -> ${outcome.value.code.id} (expected ${r.expect})"
                        }
                    } else {
                        unexpectedMiss++
                        missed += "${r.utterance} (expected ${r.expect}, got $outcome)"
                    }
                }
            }
        }

        val ingredientRows = all.count { !it.expect.startsWith("NODATA:") && it.expect != "MISS" }
        val noDataRows = all.count { it.expect.startsWith("NODATA:") }
        val missRows = all.count { it.expect == "MISS" }
        val rate = if (ingredientRows == 0) 0.0 else correct * 100.0 / ingredientRows
        val noDataRate = if (noDataRows == 0) 0.0 else noDataCorrect * 100.0 / noDataRows

        println(buildString {
            appendLine("=== MATCH RATE, authored utterance set ===")
            appendLine("utterances                 ${all.size}")
            appendLine("ingredient utterances      $ingredientRows")
            appendLine("  resolved correctly       $correct  (${"%.1f".format(rate)} %)")
            appendLine("  missed, asked the user   $unexpectedMiss")
            appendLine("no-data utterances         $noDataRows")
            appendLine("  refused correctly        $noDataCorrect  (${"%.1f".format(noDataRate)} %)")
            appendLine("  fell through to no match $noDataMissed")
            appendLine("expected-miss utterances   $missRows")
            appendLine("  correctly not matched    $honestMiss")
            appendLine("WRONG FOOD                 $wrongFood   <- the number that matters")
            if (wrong.isNotEmpty()) { appendLine("--- wrong ---"); wrong.forEach { appendLine("  $it") } }
            if (missed.isNotEmpty()) { appendLine("--- missed ---"); missed.forEach { appendLine("  $it") } }
        })

        assertEquals(
            "an utterance resolved to the wrong food. A miss is honest; a wrong food puts a " +
                "wrong number in a health app:\n" + wrong.joinToString("\n"),
            0, wrongFood,
        )
        assertTrue(
            "every deliberately no-data item must be refused BY NAME, not fall through to a " +
                "plain no match:\n" + missed.joinToString("\n"),
            noDataMissed == 0,
        )
        assertTrue("ingredient match rate fell below 85 percent: ${"%.1f".format(rate)}", rate >= 85.0)
    }
}
