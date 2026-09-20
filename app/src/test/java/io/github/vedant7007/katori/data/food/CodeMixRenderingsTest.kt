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
 * Measures the matcher against the ASR model's own renderings of English food words said the
 * Telugu way (`data-authoring/codemix-renderings.csv`, from `logs/asr-codemix-renderings.log`).
 *
 * THIS IS WHERE CODE-MIXED SPEECH BROKE. Whichever ASR is chosen, an English word said the Indian
 * way arrives in an Indic script, and on 20 September 0 of 25 of the model's renderings resolved
 * against the shipped tables. The number this test prints is "n of 25 resolve", on that exact
 * list, and it is a regression guard: the list is one synthetic voice, one rendering per word.
 *
 * WRONG FOOD is still the number that matters, as in `MatchRateTest`: a rendering that resolves
 * to the wrong food, or a composed dish that collapses onto an ingredient, fails the build. A
 * miss is reported.
 */
class CodeMixRenderingsTest {

    private lateinit var db: FoodDbSource
    private lateinit var lookup: SqliteFoodLookup

    @Before fun setUp() {
        db = JdbcFoodDbSource.openBundled()
        lookup = SqliteFoodLookup(db)
    }

    @After fun tearDown() = db.close()

    private data class Row(val english: String, val emitted: String, val said: String, val expect: String, val note: String)

    private fun rows(): List<Row> {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        val f = File(dir, "data-authoring/codemix-renderings.csv")
        check(f.isFile) { "renderings not found at ${f.absolutePath}" }
        return f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1)
            .map { it.split(",") }
            .map { Row(it[0].trim(), it[1].trim(), it[2].trim(), it[3].trim(), it.getOrElse(4) { "" }.trim()) }
    }

    /** UNIT rows resolve through resolveUnit; every other row through resolve. Returns what happened, in one word, for the report. */
    private suspend fun outcome(text: String, expect: String): Pair<Boolean, String> = when {
        expect.startsWith("UNIT:") -> {
            val r = lookup.resolveUnit(expect.removePrefix("UNIT:"), FoodClass.DAIRY)
            (r is Outcome.Ok) to (if (r is Outcome.Ok) "unit ${r.value.grams} g" else "no unit")
        }
        expect.startsWith("NODATA:") -> {
            val r = lookup.resolve(FoodQuery(text, "te"))
            val ok = r is Outcome.Unavailable && r.reason == UnavailableReason.KNOWN_ITEM_NO_DATA
            ok to (if (ok) "refused by name" else if (r is Outcome.Ok) "WRONG FOOD ${r.value.code.id}" else "plain no match")
        }
        expect == "MISS" -> {
            val r = lookup.resolve(FoodQuery(text, "te"))
            (r !is Outcome.Ok) to (if (r is Outcome.Ok) "WRONG FOOD ${r.value.code.id}" else "honest miss")
        }
        else -> {
            val r = lookup.resolve(FoodQuery(text, "te"))
            when {
                r is Outcome.Ok && r.value.code.id == expect -> true to r.value.code.id
                r is Outcome.Ok -> false to "WRONG FOOD ${r.value.code.id}"
                else -> false to "miss"
            }
        }
    }

    /**
     * The importer's `norm()` and `FoodTextMatching.normalise` must agree, or an alias is stored
     * under a key the lookup never computes and silently never matches. Checked on every alias in
     * every shipped table, so the final-u rule (or any later rule) cannot be added on one side.
     */
    @Test fun `the importer and the matcher normalise every shipped alias identically`() {
        var n = 0
        for ((table, col) in listOf("food_aliases" to "alias", "no_data_aliases" to "alias", "recipe_aliases" to "alias")) {
            for (row in db.query("SELECT $col AS a, alias_norm FROM $table")) {
                assertEquals("$table: '${row.str("a")}'", row.str("alias_norm"), FoodTextMatching.normalise(row.str("a")))
                n++
            }
        }
        for (row in db.query("SELECT unit FROM unit_conversions")) {
            assertEquals("unit '${row.str("unit")}' is not stored normalised", row.str("unit"), FoodTextMatching.normalise(row.str("unit")))
            n++
        }
        assertTrue("expected hundreds of aliases, saw $n", n > 500)
    }

    /** The rule from the measurement: a word-final u the model adds to a borrowed word is the virama the speaker meant. */
    @Test fun `a word-final u is the same as the virama, and native words still differ from each other`() {
        assertEquals(FoodTextMatching.normalise("పనీర్"), FoodTextMatching.normalise("పనీరు"))
        assertEquals(FoodTextMatching.normalise("బటర్ చికెన్"), FoodTextMatching.normalise("బటరు చికెన్"))
        assertTrue(FoodTextMatching.normalise("పప్పు") != FoodTextMatching.normalise("పాలు"))
        // Only the LAST character of a word: a u inside a word is untouched.
        assertEquals(FoodTextMatching.normalise("గుడ్డు").dropLast(1), FoodTextMatching.normalise("గుడ్డ్").dropLast(1))
        assertTrue(FoodTextMatching.normalise("గుడ్డు").startsWith("గు"))
    }

    @Test fun `measure the renderings and prove no rendering resolves to the wrong food`() = runBlocking {
        val all = rows()
        assertEquals("Jacob's list is 25 rows", 25, all.size)
        var emittedOk = 0; var saidOk = 0
        val wrong = mutableListOf<String>()
        val report = StringBuilder("=== CODE-MIXED RENDERINGS, the te model's own output ===\n")
        report.appendLine("  english      emitted            said               expect            emitted ->            said ->")
        for (r in all) {
            val (e, eWhat) = outcome(r.emitted, r.expect)
            val (s, sWhat) = outcome(r.said, r.expect)
            if (e) emittedOk++
            if (s) saidOk++
            if (eWhat.startsWith("WRONG") || sWhat.startsWith("WRONG")) wrong += "${r.english}: $eWhat / $sWhat"
            report.appendLine("  ${r.english.padEnd(12)} ${r.emitted.padEnd(18)} ${r.said.padEnd(18)} ${r.expect.padEnd(17)} ${eWhat.padEnd(21)} $sWhat")
        }
        report.appendLine("  emitted renderings resolve   $emittedOk of ${all.size}   <- Jacob's number, the model's exact output")
        report.appendLine("  said forms resolve           $saidOk of ${all.size}   <- what the speaker meant, with the final consonant as spoken")
        report.appendLine("  WRONG FOOD                   ${wrong.size}   <- the number that matters")
        println(report)
        assertEquals("a rendering resolved to the wrong food:\n" + wrong.joinToString("\n"), 0, wrong.size)
        assertTrue("fewer than 20 of ${all.size} emitted renderings resolve: $emittedOk", emittedOk >= 20)
    }
}
