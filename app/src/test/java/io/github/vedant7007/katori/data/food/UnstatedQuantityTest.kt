package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.ResolvedMeal
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * RULED by Vedant, 20 September, from Beat 1: an unstated amount is never QUANTITY_STATED. It
 * is an assumed serving and the band says so.
 *
 * Found by Nila: "थोड़ी दाल", a little dal, and the model writes `1.0`; the resolver then took
 * a unitless quantity on an authored dish as a stated piece, so the opening sentence on stage
 * presented an invented amount at full confidence while slide 6 claimed the opposite. This
 * half holds whichever way the model behaves: a number with no unit is a stated amount only for
 * a COUNTED dish (a roti, an idli, an egg); for anything served, the unit is ours, the item is
 * inferred, and the assumed quantity and unit are written back onto the plate so the card can
 * read "dal · 1 katori · taken as 180 g" and be corrected.
 */
class UnstatedQuantityTest {

    companion object {
        private lateinit var db: JdbcFoodDbSource
        private lateinit var resolver: LookupMealResolver
        @JvmStatic @BeforeClass fun open() { db = JdbcFoodDbSource.openBundled(); resolver = LookupMealResolver(SqliteFoodLookup(db)) }
        @JvmStatic @AfterClass fun close() = db.close()
    }

    /** As the orchestrator builds it: a number from the model is QUANTITY_STATED on the way in. */
    private fun item(name: String, quantity: Double? = null, unit: String? = null) = ParsedItem(
        spokenName = name, quantity = quantity, unit = unit, matchedFoodCode = null,
        confidence = ConfidenceRules.of(if (quantity == null) ConfidenceReason.QUANTITY_INFERRED else ConfidenceReason.QUANTITY_STATED),
    )

    private fun resolve(vararg items: ParsedItem): ResolvedMeal =
        (runBlocking { resolver.resolve(ParsedMeal(items.toList(), ConfidenceRules.combine(items.map { it.confidence }), "test"), "hi-IN") } as Outcome.Ok).value

    /** Beat 1, exactly: the model wrote 1.0 for "a little dal". */
    @Test fun `a little dal that the model wrote as one is an assumed katori, and the band says so`() {
        val meal = resolve(item("दाल", 1.0, null))
        val plate = meal.parsed.items.single(); val resolved = meal.items.single()
        assertEquals("toor_dal_tadka", resolved.snapshot.foodCode)
        assertEquals("taken as one serving of the recipe", 180.0, resolved.snapshot.grams!!, 0.5)
        assertEquals("the plate shows the unit that was assumed", "katori", plate.unit)
        assertEquals(1.0, plate.quantity!!, 0.0)
        assertFalse("an unstated amount is never QUANTITY_STATED", ConfidenceReason.QUANTITY_STATED in resolved.confidence.reasons)
        assertTrue(ConfidenceReason.QUANTITY_INFERRED in resolved.confidence.reasons)
        assertTrue("the serving is a shipped default and is marked as one", ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT in resolved.confidence.reasons)
        assertEquals(ConfidenceBand.ROUGH, resolved.confidence.band)
    }

    @Test fun `a bare dal with no number at all reads the same`() {
        val meal = resolve(item("dal"))
        assertEquals("katori", meal.parsed.items.single().unit)
        assertEquals(1.0, meal.parsed.items.single().quantity!!, 0.0)
        assertEquals(ConfidenceBand.ROUGH, meal.items.single().confidence.band)
    }

    /** A count of pieces IS the amount: "two rotis" states how much. */
    @Test fun `two rotis is a stated count of a counted dish`() {
        val meal = resolve(item("roti", 2.0, null))
        val resolved = meal.items.single()
        assertEquals(90.0, resolved.snapshot.grams!!, 0.5)
        assertEquals("piece", meal.parsed.items.single().unit)
        assertTrue(ConfidenceReason.QUANTITY_STATED in resolved.confidence.reasons)
        assertFalse(ConfidenceReason.QUANTITY_INFERRED in resolved.confidence.reasons)
        assertTrue("the recipe's serving caps it at approximate, not rough", resolved.confidence.band <= ConfidenceBand.APPROXIMATE)
    }

    @Test fun `three idlis and one boiled egg are counted too`() {
        val meal = resolve(item("idli", 3.0, null), item("boiled egg", 1.0, null))
        val (idli, egg) = meal.items
        assertEquals(162.0, idli.snapshot.grams!!, 0.5)
        assertTrue(ConfidenceReason.QUANTITY_STATED in idli.confidence.reasons && ConfidenceReason.QUANTITY_INFERRED !in idli.confidence.reasons)
        assertEquals(50.0, egg.snapshot.grams!!, 0.5)
        assertTrue(ConfidenceReason.QUANTITY_INFERRED !in egg.confidence.reasons)
        assertEquals(listOf("piece", "piece"), meal.parsed.items.map { it.unit })
    }

    /** The person said the unit: "ek katori dal". The serving is still the recipe's, marked as a default, and not rough. */
    @Test fun `a katori of dal that was said is approximate, not rough`() {
        val meal = resolve(item("दाल", 1.0, "कटोरी"))
        val resolved = meal.items.single()
        assertEquals(180.0, resolved.snapshot.grams!!, 0.5)
        assertFalse(ConfidenceReason.QUANTITY_INFERRED in resolved.confidence.reasons)
        assertTrue(ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT in resolved.confidence.reasons)
        assertEquals(ConfidenceBand.APPROXIMATE, resolved.confidence.band)
        assertEquals("कटोरी", meal.parsed.items.single().unit)
    }

    /**
     * "Taken as" is for an inferred amount only (0035): a measurable amount the person stated is
     * theirs, so it never carries QUANTITY_INFERRED, whatever conversion sits behind the band.
     */
    @Test fun `a stated measurable amount is never inferred`() {
        val meal = resolve(item("milk", 200.0, "ml"), item("oil", 2.0, "spoon"), item("rice", 1.0, "plate"))
        meal.items.forEach { assertFalse("${it.snapshot.displayName} was stated and reads as inferred", ConfidenceReason.QUANTITY_INFERRED in it.confidence.reasons) }
        assertEquals(listOf("ml", "spoon", "plate"), meal.parsed.items.map { it.unit })
        assertEquals(200.0, meal.items[0].snapshot.grams!!, 0.5)
    }

    /** A plain food with a number and no unit: "two rice" is two assumed katoris. */
    @Test fun `a number with no unit on a plain food is an assumed household unit`() {
        val meal = resolve(item("rice", 2.0, null))
        val resolved = meal.items.single()
        assertEquals(300.0, resolved.snapshot.grams!!, 0.5)
        assertEquals("katori", meal.parsed.items.single().unit)
        assertFalse(ConfidenceReason.QUANTITY_STATED in resolved.confidence.reasons)
        assertEquals(ConfidenceBand.ROUGH, resolved.confidence.band)
    }

    /** Served dishes get the word a person would say for them, so the card reads naturally. */
    @Test fun `a served dish is shown in the unit a person would say`() {
        val meal = resolve(item("sambar", 1.0, null), item("veg biryani", 1.0, null), item("chai", 1.0, null), item("coconut chutney", 1.0, null))
        assertEquals(listOf("katori", "plate", "cup", "spoon"), meal.parsed.items.map { it.unit })
        meal.items.forEach { assertEquals(it.snapshot.displayName, ConfidenceBand.ROUGH, it.confidence.band) }
    }
}
