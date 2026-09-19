package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientValue
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs against the REAL bundled database produced by tools/build_food_db.py, not fixtures.
 *
 * That is the point: these tests fail if the import breaks, if an alias is wrong, or if a column
 * is renamed, none of which a fixture would ever notice.
 */
class SqliteFoodLookupTest {

    private lateinit var db: FoodDbSource
    private lateinit var lookup: SqliteFoodLookup

    @Before fun setUp() {
        db = JdbcFoodDbSource.openBundled()
        lookup = SqliteFoodLookup(db)
    }

    @After fun tearDown() = db.close()

    private fun <T> ok(o: Outcome<T>): T {
        assertTrue("expected Ok but got $o", o is Outcome.Ok)
        return (o as Outcome.Ok).value
    }

    // Bare "dal" and bare "pappu" moved from the plain boiled pulse to the tempered dish when
    // the recipe layer landed, because that is what the word means when somebody says it about a
    // meal they ate. The plain pulse is still reachable, by a name that says so.
    @Test fun `plain dal resolves to the tempered dish people actually eat`() = runBlocking {
        assertEquals("toor_dal_tadka", ok(lookup.resolve(FoodQuery("dal", "en-IN"))).code.id)
    }

    @Test fun `telugu pappu resolves through the native alias`() = runBlocking {
        assertEquals("toor_dal_tadka", ok(lookup.resolve(FoodQuery("పప్పు", "te"))).code.id)
    }

    @Test fun `the plain boiled pulse is still reachable under a name that says so`() = runBlocking {
        assertEquals("toor_dal_cooked", ok(lookup.resolve(FoodQuery("plain boiled toor dal", "en-IN"))).code.id)
        assertEquals("toor_dal_raw", ok(lookup.resolve(FoodQuery("kandi pappu", "te"))).code.id)
    }

    @Test fun `a code-mixed phrase finds the food inside it`() = runBlocking {
        assertEquals("groundnut_oil", ok(lookup.resolve(FoodQuery("two spoons of groundnut oil", "en-IN"))).code.id)
    }

    // --- the no-data discipline ------------------------------------------------------------

    @Test fun `ragi returns a named no-data result and never a substitute grain`() = runBlocking {
        val r = lookup.resolve(FoodQuery("ragi", "en-IN"))
        assertTrue("ragi must never resolve to a food: $r", r is Outcome.Unavailable)
        val u = r as Outcome.Unavailable
        assertEquals(UnavailableReason.KNOWN_ITEM_NO_DATA, u.reason)
        assertTrue(u.detail!!.contains("Ragi"))
    }

    @Test fun `bajra and jaggery are also no-data`() = runBlocking {
        listOf("bajra", "jaggery", "bellam", "gur").forEach { name ->
            val r = lookup.resolve(FoodQuery(name, "en-IN"))
            assertTrue("$name must be no-data, got $r", r is Outcome.Unavailable)
            assertEquals(UnavailableReason.KNOWN_ITEM_NO_DATA, (r as Outcome.Unavailable).reason)
        }
    }

    @Test fun `an unknown food is a plain no match, not a no-data item`() = runBlocking {
        val r = lookup.resolve(FoodQuery("zzqqxx", "en-IN"))
        assertEquals(UnavailableReason.NO_MATCH, (r as Outcome.Unavailable).reason)
    }

    // --- nutrients ----------------------------------------------------------------------------

    @Test fun `nutrients scale linearly with grams`() = runBlocking {
        val code = ok(lookup.resolve(FoodQuery("cooked rice", "en-IN"))).code
        val per100 = ok(lookup.nutrientsFor(code, 100.0))[Nutrient.ENERGY] as NutrientValue.Measured
        val per200 = ok(lookup.nutrientsFor(code, 200.0))[Nutrient.ENERGY] as NutrientValue.Measured
        assertEquals(per100.amount * 2, per200.amount, 0.0001)
    }

    @Test fun `an absent nutrient reads as Unknown and never as zero`() = runBlocking {
        // Across the whole database, no nutrient may come back as a measured zero it did not have.
        val rows = db.query("SELECT food_key FROM foods")
        var sawUnknown = false
        for (row in rows) {
            val code = FoodCode(io.github.vedant7007.katori.domain.model.DataSource.USDA_SR_LEGACY, row.str("food_key"))
            val profile = ok(lookup.nutrientsFor(code, 100.0))
            Nutrient.entries.forEach { n ->
                when (val v = profile[n]) {
                    is NutrientValue.Unknown -> sawUnknown = true
                    is NutrientValue.AssumedZero -> Unit
                    is NutrientValue.Measured -> assertTrue(v.amount >= 0.0)
                }
            }
        }
        assertTrue("the fixture should contain at least one genuinely unknown nutrient", sawUnknown)
    }

    @Test fun `a genuine zero stays zero at any portion size`() = runBlocking {
        val code = ok(lookup.resolve(FoodQuery("cooked rice", "en-IN"))).code
        val small = ok(lookup.nutrientsFor(code, 10.0))
        val large = ok(lookup.nutrientsFor(code, 500.0))
        Nutrient.entries.forEach { n ->
            if (small[n] is NutrientValue.AssumedZero) {
                assertTrue("an assumed zero must not become a number", large[n] is NutrientValue.AssumedZero)
            }
        }
    }

    @Test fun `zero or negative grams is refused rather than producing a figure`() = runBlocking {
        val code = ok(lookup.resolve(FoodQuery("cooked rice", "en-IN"))).code
        listOf(0.0, -5.0, Double.NaN).forEach {
            assertTrue(lookup.nutrientsFor(code, it) is Outcome.Unavailable)
        }
    }

    // --- household units -----------------------------------------------------------------------

    @Test fun `a katori of dal resolves and is marked a default conversion`() = runBlocking {
        val w = ok(lookup.resolveUnit("katori", FoodClass.PULSE_COOKED))
        assertEquals(150.0, w.grams, 0.001)
        assertTrue("a default conversion must say so, it caps the band", w.isDefaultConversion)
    }

    @Test fun `grams are not a household unit and carry no default flag`() = runBlocking {
        val w = ok(lookup.resolveUnit("g", FoodClass.PULSE_COOKED))
        assertEquals(1.0, w.grams, 0.001)
        assertTrue(!w.isDefaultConversion)
    }

    // Density 1.0 is a shipped default, and it is wrong by ~8% for oil. The flag is what keeps
    // "two spoons of oil" spoken in ml out of the GOOD band.
    @Test fun `millilitres convert at density one and are flagged as a default`() = runBlocking {
        val w = ok(lookup.resolveUnit("ml", FoodClass.FAT_OIL))
        assertEquals(1.0, w.grams, 0.001)
        assertTrue("a volume taken as a weight is a default conversion", w.isDefaultConversion)
        val m = ok(lookup.resolve(FoodQuery("sunflower oil", "en-IN")))
        assertEquals(ConfidenceBand.APPROXIMATE, figureConfidence(m, w, quantityStated = true).band)
    }

    @Test fun `an unresolvable unit is refused rather than guessed`() = runBlocking {
        assertTrue(lookup.resolveUnit("fistful", FoodClass.PULSE_COOKED) is Outcome.Unavailable)
    }

    // --- confidence ------------------------------------------------------------------------------

    @Test fun `ghee carries its weak source caveat and cannot reach Good`() = runBlocking {
        val m = ok(lookup.resolve(FoodQuery("ghee", "en-IN")))
        assertTrue(ConfidenceReason.WEAK_SOURCE_RECORD in m.confidence.reasons)
        assertEquals(ConfidenceBand.APPROXIMATE, m.confidence.band)
    }

    @Test fun `ridge gourd is marked a substitute and lands at Rough`() = runBlocking {
        val m = ok(lookup.resolve(FoodQuery("beerakaya", "te")))
        assertTrue(ConfidenceReason.SUBSTITUTE_FOOD_RECORD in m.confidence.reasons)
        assertEquals(ConfidenceBand.ROUGH, m.confidence.band)
    }

    @Test fun `a household unit drags the figure down to Approximate`() = runBlocking {
        val m = ok(lookup.resolve(FoodQuery("plain boiled toor dal", "en-IN")))
        assertEquals(ConfidenceBand.GOOD, m.confidence.band)
        val w = ok(lookup.resolveUnit("katori", FoodClass.PULSE_COOKED))
        assertEquals(ConfidenceBand.APPROXIMATE, figureConfidence(m, w, quantityStated = true).band)
    }

    @Test fun `an inferred quantity drags the figure down to Rough`() = runBlocking {
        val m = ok(lookup.resolve(FoodQuery("plain boiled toor dal", "en-IN")))
        val w = ok(lookup.resolveUnit("g", FoodClass.PULSE_COOKED))
        assertEquals(ConfidenceBand.ROUGH, figureConfidence(m, w, quantityStated = false).band)
    }

    // --- refusals that are still refusals ---------------------------------------------------------

    @Test fun `a food code is not a recipe code and is refused rather than improvised`() = runBlocking {
        val code = ok(lookup.resolve(FoodQuery("cooked rice", "en-IN"))).code
        val r = lookup.recipe(code)
        assertTrue("an ingredient must not yield a made-up composition: $r", r is Outcome.Unavailable)
    }

    @Test fun `an unknown recipe key is refused rather than returning an empty dish`() = runBlocking {
        val r = lookup.recipe(FoodCode(io.github.vedant7007.katori.domain.model.DataSource.AUTHORED_RECIPE, "no_such_dish"))
        assertTrue(r is Outcome.Unavailable)
    }
}
