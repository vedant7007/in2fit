package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.LifeContext
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Spec 4.3's list, as the shipped database carries it. The properties are the ones the demo's
 * beat 3 and beat 4 depend on: a raw ingredient is never suggested, a spice is never a helping,
 * the spec's own examples are available where the spec says they are.
 */
class CandidateCatalogueTest {

    companion object {
        private lateinit var db: FoodDbSource
        @BeforeClass @JvmStatic fun open() { db = JdbcFoodDbSource.openBundled() }
        @AfterClass @JvmStatic fun close() { db.close() }
    }

    private val all by lazy { CandidateCatalogue.load(db) }
    private val classOf by lazy { db.query("SELECT food_key, food_class FROM foods").associate { it.str("food_key") to it.str("food_class") } }

    /** The importer's context list is a copy of the enum; a context it accepts that the enum lacks would be silently dropped here. */
    @Test fun `every context in the table is a LifeContext`() {
        val names = db.query("SELECT DISTINCT context FROM candidates").map { it.str("context") }
        val unknown = names.filter { n -> LifeContext.entries.none { it.name == n } }
        assertTrue("contexts the app cannot read: $unknown", unknown.isEmpty())
        assertEquals(LifeContext.entries.map { it.name }.toSet(), names.toSet())
    }

    @Test fun `a raw grain or pulse is never a candidate`() {
        val raw = all.filter { classOf[it.foodCode] in setOf("GRAIN_RAW", "PULSE_RAW") }.map { it.foodCode }
        assertTrue("raw ingredients offered as suggestions: $raw", raw.isEmpty())
        assertTrue(all.none { it.foodCode in setOf("cowpea", "urad_dal", "masoor_dal", "atta", "rice_raw") })
    }

    @Test fun `a spice or a fat is a candidate only as a stated helping`() {
        val seasoning = all.filter { classOf[it.foodCode] in setOf("SPICE", "FAT_OIL", "SWEET", "PACKAGED") }
        seasoning.forEach { assertTrue("${it.foodCode} at ${it.servingGrams} g is a teaspoon, not a helping", it.servingGrams >= 20.0) }
        assertTrue(all.none { it.foodCode in setOf("cumin", "turmeric", "bay_leaf", "ghee", "sugar") })
        assertEquals(30.0, all.single { it.foodCode == "peanuts_roasted" }.servingGrams, 0.0)
    }

    /** Spec 4.2's own examples: boiled eggs and sprouts for the hostel; curd for the PG. */
    @Test fun `the spec's own examples are available where it says`() {
        fun inContext(ctx: LifeContext) = all.filter { ctx in it.contexts }.map { it.foodCode }.toSet()
        val hostel = inContext(LifeContext.HOSTEL_STUDENT)
        assertTrue(setOf("egg", "sprouts_salad", "curd", "idli", "chapati", "toor_dal_tadka").all { it in hostel })
        assertTrue("a hostel student has no fridge and no mutton budget", setOf("mutton_curry", "cheese_sandwich", "cold_coffee").none { it in hostel })
        assertTrue("curd" in inContext(LifeContext.PG_OWN_COOKING))
        assertTrue("a tiffin box holds jonna rotte, not a milkshake", "jowar_roti" in inContext(LifeContext.FIELD_OR_MANUAL_WORKER) && "banana_shake" !in inContext(LifeContext.FIELD_OR_MANUAL_WORKER))
        assertTrue(all.none { it.foodCode == "water" && LifeContext.HOSTEL_STUDENT !in it.contexts })
    }

    @Test fun `a suggestion shows the name a person would say`() {
        assertEquals("Boiled egg", all.single { it.foodCode == "egg" }.displayName)
        assertEquals("Curd", all.single { it.foodCode == "curd" }.displayName)
        assertEquals(200.0, all.single { it.foodCode == "milk_whole" }.servingGrams, 0.0)
        assertEquals(50.0, all.single { it.foodCode == "egg" }.servingGrams, 0.0)
        assertEquals(45.0, all.single { it.foodCode == "chapati" }.servingGrams, 0.0)
    }

    /** The caller's diet filter reaches a recipe through its ingredients, as RoomUserContextSource's does. */
    @Test fun `a forbidden ingredient forbids the dish`() {
        val vegetarian = CandidateCatalogue.load(db) { _, description, _ ->
            description.lowercase().split(Regex("[^a-z]+")).none { it in setOf("chicken", "egg", "eggs", "fish", "prawn", "shrimp", "goat") }
        }
        val codes = vegetarian.map { it.foodCode }.toSet()
        assertTrue(setOf("chicken_curry", "chicken_biryani", "egg", "egg_curry", "omelette", "fish_pulusu", "royyala_iguru").none { it in codes })
        assertTrue(setOf("idli", "curd", "sprouts_salad", "toor_dal_tadka").all { it in codes })
    }

    @Test fun `nothing is a candidate that the file does not list`() {
        val listed = db.query("SELECT DISTINCT key FROM candidates").map { it.str("key") }.toSet()
        assertEquals(listed, all.map { it.foodCode }.toSet())
        assertTrue("the list is a fraction of the database, by design", all.size < 120)
    }
}
