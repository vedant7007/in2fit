package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * The US-record sweep of 20 September (`data-authoring/us-record-sweep.csv`), asserted on the
 * shipped database: what a judge is told when asked "why does your app think Indian bread has
 * American iron in it". Found by the demo run: a katori of white bread topped the iron list.
 */
class UsRecordSweepTest {

    companion object {
        private lateinit var db: FoodDbSource
        private lateinit var lookup: SqliteFoodLookup
        @BeforeClass @JvmStatic fun open() { db = JdbcFoodDbSource.openBundled(); lookup = SqliteFoodLookup(db) }
        @AfterClass @JvmStatic fun close() { db.close() }
    }

    private fun match(name: String): FoodMatch = when (val r = runBlocking { lookup.resolve(FoodQuery(name, "en-IN")) }) {
        is Outcome.Ok -> r.value
        else -> error("'$name' did not resolve: $r")
    }

    @Test fun `white bread ships no iron figure, and says why beside its figures`() {
        assertNull(db.query("SELECT 1 FROM food_nutrients WHERE food_key = 'bread_white' AND nutrient = 'IRON'").firstOrNull())
        val m = match("white bread")
        assertEquals("bread_white", m.code.id)
        assertNotNull("the disclosure must travel with the match", m.disclosure)
        assertTrue(m.disclosure!!.contains("enrichment"))
        assertTrue("a fact about the data, not an apology", m.disclosure!!.startsWith("Iron not shown"))
        // the sandwiches made from it read Unknown for iron, a named floor, never a number
        for (recipe in listOf("bread_butter", "cheese_sandwich", "egg_sandwich")) {
            assertEquals("UNKNOWN", db.query("SELECT state FROM recipe_nutrients WHERE recipe_key = ? AND nutrient = 'IRON'", listOf(recipe)).single().str("state"))
        }
    }

    @Test fun `curd is whole-milk yogurt and milk is the unfortified record`() {
        assertEquals(171284, db.query("SELECT fdc_id FROM foods WHERE food_key = 'curd'").single().dbl("fdc_id").toInt())
        assertEquals(172217, db.query("SELECT fdc_id FROM foods WHERE food_key = 'milk_whole'").single().dbl("fdc_id").toInt())
        val protein = db.query("SELECT amount FROM food_nutrients WHERE food_key = 'curd' AND nutrient = 'PROTEIN'").single().dbl("amount")
        assertTrue("home-set curd is not low-fat yogurt with added milk solids: protein $protein", protein < 4.0)
        assertNull("the record itself is honest now; no disclosure needed", match("milk").disclosure)
    }

    @Test fun `buttermilk is the chaas recipe, not undiluted US cultured milk`() {
        for (name in listOf("buttermilk", "chaas", "majjiga", "छाछ")) {
            val m = match(name)
            assertEquals("'$name'", "chaas", m.code.id)
        }
        val kcal = db.query("SELECT amount_per_100g FROM recipe_nutrients WHERE recipe_key = 'chaas' AND nutrient = 'ENERGY'").single().dbl("amount_per_100g")
        assertTrue("a glass of chaas is mostly water: $kcal kcal per 100 g", kcal in 10.0..30.0)
        assertNull(db.query("SELECT 1 FROM foods WHERE food_key = 'buttermilk'").firstOrNull())
    }

    @Test fun `every shipped record is in the sweep file, with a finding`() {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        val rows = File(dir, "data-authoring/us-record-sweep.csv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1)
        val swept = rows.map { it.substringBefore(',') }.toSet()
        val shipped = db.query("SELECT food_key FROM foods").map { it.str("food_key") }.toSet()
        assertTrue("shipped records never swept: ${shipped - swept}", (shipped - swept).isEmpty())
        assertEquals("the sweep names the whole disclosure", 2, db.query("SELECT COUNT(*) AS n FROM foods WHERE disclosure IS NOT NULL").single().dbl("n").toInt())
        assertTrue(db.query("SELECT value FROM meta WHERE key = 'disclosure'").single().str("value").contains("white bread"))
    }
}
