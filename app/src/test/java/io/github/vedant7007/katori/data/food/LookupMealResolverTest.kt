package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.ResolvedMeal
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The resolver against the REAL bundled database, so a figure here is the figure the phone
 * would show. Weights are asserted against the authored files (`household-units.csv`,
 * `recipes.csv`), not against numbers this test invented.
 */
class LookupMealResolverTest {

    companion object {
        private lateinit var db: JdbcFoodDbSource
        private lateinit var resolver: LookupMealResolver

        @JvmStatic @BeforeClass fun open() {
            db = JdbcFoodDbSource.openBundled()
            resolver = LookupMealResolver(SqliteFoodLookup(db))
        }

        @JvmStatic @AfterClass fun close() = db.close()
    }

    private fun item(name: String, quantity: Double? = null, unit: String? = null) = ParsedItem(
        spokenName = name, quantity = quantity, unit = unit, matchedFoodCode = null,
        confidence = ConfidenceRules.of(if (quantity == null) ConfidenceReason.QUANTITY_INFERRED else ConfidenceReason.QUANTITY_STATED),
    )

    private fun meal(vararg items: ParsedItem) = ParsedMeal(items.toList(), ConfidenceRules.combine(items.map { it.confidence }), "test")

    private fun resolve(vararg items: ParsedItem): ResolvedMeal =
        (runBlocking { resolver.resolve(meal(*items), "en-IN") } as Outcome.Ok).value

    @Test fun `a plate of named foods resolves to weights from the authored tables`() {
        val r = resolve(item("roti", 2.0, null), item("dal", 1.0, "katori"), item("curd", 1.0, "katori"))
        val roti = r.items[0]
        val dal = r.items[1]
        val curd = r.items[2]
        // recipes.csv: chapati yields 270 g over 6 servings, so one roti is 45 g and two are 90.
        assertEquals("chapati", roti.snapshot.foodCode)
        assertEquals(90.0, roti.snapshot.grams!!, 0.01)
        assertEquals(DataSource.AUTHORED_RECIPE, roti.source)
        // "dal" is the tadka recipe (720 g over 4 servings); a katori of a dish is one serving,
        // and that is a shipped default, so the item says so.
        assertEquals("toor_dal_tadka", dal.snapshot.foodCode)
        assertEquals(180.0, dal.snapshot.grams!!, 0.01)
        assertTrue(dal.confidence.reasons.contains(ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT))
        // household-units.csv: a katori of dairy is 150 g, also a shipped default.
        assertEquals(150.0, curd.snapshot.grams!!, 0.01)
        assertTrue(curd.confidence.reasons.contains(ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT))
        // The parse comes back with the codes filled in, and the figures carry their sources.
        assertEquals("chapati", r.parsed.items[0].matchedFoodCode)
        val energy = r.figures.single { it.total.nutrient == Nutrient.ENERGY }
        assertEquals(Completeness.COMPLETE, energy.total.completeness)
        assertTrue(energy.total.amount > 100.0)
        assertTrue("${energy.sources}", energy.sources.containsAll(listOf(DataSource.AUTHORED_RECIPE, DataSource.USDA_SR_LEGACY)))
    }

    @Test fun `an item the database deliberately holds nothing for stays on the plate as a hole`() {
        val r = resolve(item("dal", 1.0, "katori"), item("curry leaves"))
        val leaves = r.items[1]
        assertNull(leaves.snapshot.foodCode)
        assertNull(leaves.snapshot.grams)
        assertNull(leaves.source)
        // Every total is a floor that names the hole; nothing was dropped and nothing was zeroed.
        r.figures.forEach { f ->
            assertEquals("${f.total.nutrient}", Completeness.PARTIAL, f.total.completeness)
            assertEquals(listOf("curry leaves"), f.total.unknownContributors)
        }
    }

    /** The first end-to-end run (20 Sep) was NO_MATCH on "rotis": one edit at four letters is refused, so the plural is taken off. */
    @Test fun `an English plural resolves to the food, as the person said it`() {
        val r = resolve(item("rotis", 2.0, null), item("idlis", 3.0, null), item("eggs", 1.0, null))
        assertEquals("chapati", r.items[0].snapshot.foodCode)
        assertEquals(90.0, r.items[0].snapshot.grams!!, 0.01)
        assertEquals("idli", r.items[1].snapshot.foodCode?.substringBefore('_'))
        assertEquals("egg", r.items[2].snapshot.foodCode)
    }

    @Test fun `nothing matching is a refusal, never the nearest food`() {
        val r = runBlocking { resolver.resolve(meal(item("xyzzy plugh", 1.0, "katori")), "en-IN") }
        assertEquals(UnavailableReason.NO_MATCH, (r as Outcome.Unavailable).reason)
    }

    @Test fun `an unstated quantity is one usual unit, marked inferred, capped at rough`() {
        val r = resolve(item("dal"))
        val dal = r.items.single()
        assertEquals("one serving of the dish", 180.0, dal.snapshot.grams!!, 0.01)
        assertTrue(dal.confidence.reasons.contains(ConfidenceReason.QUANTITY_INFERRED))
        assertEquals(ConfidenceBand.ROUGH, dal.confidence.band)
        assertEquals(ConfidenceBand.ROUGH, r.parsed.confidence.band)
    }

    @Test fun `a unit the class cannot convert leaves the weight unknown rather than guessed`() {
        // Curd is a plain food with no recipe to fall back on; a unit its class has no row for
        // leaves the weight unknown, and every figure with it.
        val r = resolve(item("curd", 1.0, "furlong"))
        val dal = r.items.single()
        assertNull(dal.snapshot.grams)
        assertNotNull(r.figures.single { it.total.nutrient == Nutrient.PROTEIN })
        assertEquals(Completeness.NONE, r.figures.single { it.total.nutrient == Nutrient.PROTEIN }.total.completeness)
    }
}
