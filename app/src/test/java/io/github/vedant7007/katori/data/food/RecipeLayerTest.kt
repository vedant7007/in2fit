package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientProfile
import io.github.vedant7007.katori.domain.model.NutrientValue
import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The authored reference recipe layer, measured rather than reviewed.
 *
 * WHY MEASURED. The alias bugs in the ingredient corpus were all found by running the whole set
 * and reading what came out, never by reading the data. Recipes have the same property and worse:
 * a plausible total hides a wrong composition, and every downstream layer will happily carry that
 * total to the screen. So every recipe in the shipped database is exercised here, not a chosen few.
 *
 * WHAT IS AND IS NOT MEASURED. These tests prove INTERNAL CONSISTENCY: that the composition adds
 * up to the stated yield, that the shipped per-100 g figures are exactly what the ingredient rows
 * produce, that nothing silently drops out. They prove NOTHING about whether our sambar resembles
 * anyone's actual sambar. That claim would need weighed plates, and we do not have them. It is
 * why every figure from this layer is capped at Approximate.
 */
class RecipeLayerTest {

    private lateinit var db: FoodDbSource
    private lateinit var lookup: SqliteFoodLookup
    private val calculator = DefaultRecipeCalculator()

    @Before fun setUp() {
        db = JdbcFoodDbSource.openBundled()
        lookup = SqliteFoodLookup(db)
    }

    @After fun tearDown() = db.close()

    private fun <T> ok(o: Outcome<T>): T {
        assertTrue("expected Ok but got $o", o is Outcome.Ok)
        return (o as Outcome.Ok).value
    }

    private fun allRecipeKeys(): List<String> =
        db.query("SELECT recipe_key FROM recipes ORDER BY recipe_key").map { it.str("recipe_key") }

    private fun code(key: String) = FoodCode(DataSource.AUTHORED_RECIPE, key)

    // --- resolution ----------------------------------------------------------------------------

    @Test fun `every authored recipe is reachable by its own display name`() = runBlocking {
        val unreachable = mutableListOf<String>()
        for (key in allRecipeKeys()) {
            val name = db.query("SELECT display_name FROM recipes WHERE recipe_key = ?", listOf(key))
                .first().str("display_name")
            val r = lookup.resolve(FoodQuery(name, "en-IN"))
            if (r !is Outcome.Ok || r.value.code.id != key) unreachable += "$key ('$name') -> $r"
        }
        assertTrue("a dish nobody can say is a dish nobody can log:\n" + unreachable.joinToString("\n"),
            unreachable.isEmpty())
    }

    @Test fun `a resolved dish is a composed dish from the authored recipe source`() = runBlocking {
        val m = ok(lookup.resolve(FoodQuery("sambar", "en-IN")))
        assertEquals(DataSource.AUTHORED_RECIPE, m.code.source)
        assertEquals(FoodClass.COMPOSED_DISH, m.foodClass)
    }

    /**
     * The ruling that makes this layer safe to ship. An exact name match on a dish is still only
     * Approximate, because knowing the word is not knowing the plate.
     */
    @Test fun `no dish can ever reach Good however exact the name match`() = runBlocking {
        val tooGood = mutableListOf<String>()
        for (key in allRecipeKeys()) {
            val name = db.query("SELECT display_name FROM recipes WHERE recipe_key = ?", listOf(key))
                .first().str("display_name")
            val m = ok(lookup.resolve(FoodQuery(name, "en-IN")))
            if (m.confidence.band == ConfidenceBand.GOOD) tooGood += key
            if (ConfidenceReason.AUTHORED_REFERENCE_RECIPE !in m.confidence.reasons) tooGood += "$key missing reason"
        }
        assertTrue("a reference recipe read better than Approximate: $tooGood", tooGood.isEmpty())
    }

    // --- composition ---------------------------------------------------------------------------

    @Test fun `every recipe exposes its full composition and never an empty one`() = runBlocking {
        for (key in allRecipeKeys()) {
            val r = ok(lookup.recipe(code(key)))
            assertTrue("$key has no ingredients", r.ingredients.isNotEmpty())
            assertTrue("$key has a non-positive yield", r.totalYieldGrams > 0.0)
            assertTrue("$key has a non-positive serving count", r.servings > 0.0)
            assertTrue("$key must carry the authoring row id so its figures can be challenged",
                r.authoringNoteId.isNotEmpty())
            r.ingredients.forEach {
                assertTrue("${key}/${it.code.id} has a non-positive weight", it.grams > 0.0)
            }
        }
    }

    /**
     * No ingredient row may be dropped between the database and the object the UI shows.
     *
     * The import already asserts that all 434 rows reached the database. This asserts the other
     * half of the journey: that the query and the mapping do not lose one on the way out, which a
     * JOIN against a missing food would do silently.
     */
    @Test fun `no ingredient row is lost between the database and the recipe object`() = runBlocking {
        var expected = 0
        var got = 0
        for (key in allRecipeKeys()) {
            expected += db.query(
                "SELECT COUNT(*) AS n FROM recipe_ingredients WHERE recipe_key = ?", listOf(key)
            ).first().dbl("n").toInt()
            got += ok(lookup.recipe(code(key))).ingredients.size
        }
        assertTrue("the shipped database should hold the full authored set", expected > 400)
        assertEquals("an ingredient was dropped on the way out of the database", expected, got)
    }

    @Test fun `ingredient weights plus the stated water change reconcile to the yield`() = runBlocking {
        val off = mutableListOf<String>()
        for (key in allRecipeKeys()) {
            val r = ok(lookup.recipe(code(key)))
            val water = db.query("SELECT water_change_g FROM recipes WHERE recipe_key = ?", listOf(key))
                .first().dbl("water_change_g")
            val sum = r.ingredients.sumOf { it.grams } + water
            if (kotlin.math.abs(sum - r.totalYieldGrams) > 1.0) {
                off += "$key: ingredients ${r.ingredients.sumOf { it.grams }} + water $water = $sum, stated ${r.totalYieldGrams}"
            }
        }
        assertTrue("a recipe does not add up to its own yield:\n" + off.joinToString("\n"), off.isEmpty())
    }

    /**
     * A frying fat records what the dish RETAINS, not what the pan held.
     *
     * Counting the whole bath is what made a published database read 745 kcal per 100 g for a
     * vada. This asserts every fried dish flags its retained fat, so the distinction cannot be
     * quietly lost by an edit to the authoring file.
     */
    @Test fun `every fried dish flags its fat as retained rather than used`() = runBlocking {
        val fried = db.query(
            "SELECT recipe_key FROM recipes WHERE oil_method IN ('DEEP_FRY','SHALLOW_FRY')"
        ).map { it.str("recipe_key") }
        assertTrue("the set should contain fried dishes", fried.size >= 5)
        for (key in fried) {
            val r = ok(lookup.recipe(code(key)))
            assertTrue("$key is fried but flags no retained fat",
                r.ingredients.any { it.isAbsorbedFat })
        }
    }

    // --- arithmetic ----------------------------------------------------------------------------

    private fun per100(code: FoodCode): NutrientProfile = runBlocking { ok(lookup.nutrientsFor(code, 100.0)) }

    /**
     * THE CROSS-CHECK. The shipped per-100 g table was computed in Python at import time; the
     * user-editable path computes the same thing in Kotlin at runtime. They must agree.
     *
     * If they ever diverge, one of two things happened: the authoring data changed without the
     * database being rebuilt, or one of the two implementations is wrong. Both are silent
     * failures that produce a perfectly plausible number, which is exactly the class of error
     * this project cannot afford.
     */
    @Test fun `the shipped recipe figures equal what the ingredient rows add up to`() = runBlocking {
        val drift = mutableListOf<String>()
        var comparisons = 0

        for (key in allRecipeKeys()) {
            val recipe = ok(lookup.recipe(code(key)))
            val profiles = recipe.ingredients.associate { it.code to per100(it.code) }

            // The whole batch: servings asked for equals servings the recipe makes.
            val computed = calculator.nutrientsForServing(recipe, profiles, recipe.servings)
            val shipped = db.query(
                "SELECT nutrient, state, amount_per_100g FROM recipe_nutrients WHERE recipe_key = ?",
                listOf(key),
            ).associate { it.str("nutrient") to (it.str("state") to it.dblOrNull("amount_per_100g")) }

            for (n in Nutrient.entries) {
                val (state, amount) = shipped[n.name] ?: continue
                val total = computed.getValue(n)
                if (state == "MEASURED") {
                    comparisons++
                    // The table is per 100 g of finished dish; the calculator returns the batch.
                    val expected = amount!! * (recipe.totalYieldGrams / 100.0)
                    if (total.completeness != Completeness.COMPLETE) {
                        drift += "$key/$n: shipped says measured, calculator says ${total.completeness} " +
                            "(unknown: ${total.unknownContributors})"
                    } else if (kotlin.math.abs(total.amount - expected) > 0.01 + kotlin.math.abs(expected) * 1e-9) {
                        drift += "$key/$n: shipped $expected, calculated ${total.amount}"
                    }
                } else {
                    // The import makes the whole dish unknown if any one ingredient is unknown.
                    // The calculator, which has somewhere to put the detail, says partial and
                    // names the ingredients. Either way it must not read as a complete total.
                    if (total.completeness == Completeness.COMPLETE) {
                        drift += "$key/$n: shipped says unknown, calculator claims a complete total"
                    }
                }
            }
        }
        assertTrue("the recipe layer should have compared a lot of figures", comparisons > 200)
        assertTrue("the shipped table has drifted from the ingredient rows. Rebuild the database " +
            "with tools/build_food_db.py, then find out which side is wrong:\n" + drift.joinToString("\n"),
            drift.isEmpty())
    }

    @Test fun `a portion is a linear share of the batch`() = runBlocking {
        val recipe = ok(lookup.recipe(code("sambar")))
        val profiles = recipe.ingredients.associate { it.code to per100(it.code) }
        val one = calculator.nutrientsForServing(recipe, profiles, 1.0).getValue(Nutrient.ENERGY)
        val two = calculator.nutrientsForServing(recipe, profiles, 2.0).getValue(Nutrient.ENERGY)
        assertEquals(one.amount * 2, two.amount, 0.0001)
    }

    @Test fun `nutrients for a dish scale linearly with the weight eaten`() = runBlocking {
        val c = code("sambar")
        val per100 = ok(lookup.nutrientsFor(c, 100.0))[Nutrient.ENERGY] as NutrientValue.Measured
        val per250 = ok(lookup.nutrientsFor(c, 250.0))[Nutrient.ENERGY] as NutrientValue.Measured
        assertEquals(per100.amount * 2.5, per250.amount, 0.0001)
    }

    /**
     * The user-editable path, which is the point of shipping the composition rather than only the
     * total. Halving the oil must move the number, and move it in the right direction.
     */
    @Test fun `editing an ingredient changes the figure, which is why the composition ships`() = runBlocking {
        val recipe = ok(lookup.recipe(code("pakoda")))
        val profiles = recipe.ingredients.associate { it.code to per100(it.code) }
        val asShipped = calculator.nutrientsForServing(recipe, profiles, 1.0).getValue(Nutrient.ENERGY)

        val fat = recipe.ingredients.first { it.isAbsorbedFat }
        val lessOil = recipe.copy(
            ingredients = recipe.ingredients.map { if (it === fat) it.copy(grams = it.grams / 2) else it }
        )
        val edited = calculator.nutrientsForServing(lessOil, profiles, 1.0).getValue(Nutrient.ENERGY)

        assertTrue("halving the absorbed oil must lower the energy: ${asShipped.amount} -> ${edited.amount}",
            edited.amount < asShipped.amount)
        assertEquals(Completeness.COMPLETE, edited.completeness)
    }

    /**
     * An ingredient we hold no value for makes the figure a FLOOR, never a total, and it is named.
     * Dropping it would understate the dish by exactly the amount nobody can see.
     */
    @Test fun `an unknown ingredient makes the total partial and names itself`() = runBlocking {
        val recipe = ok(lookup.recipe(code("sambar")))
        val profiles = recipe.ingredients.associate { it.code to per100(it.code) }.toMutableMap()
        val dropped = recipe.ingredients.first()
        profiles[dropped.code] = NutrientProfile(emptyMap())

        val total = calculator.nutrientsForServing(recipe, profiles, 1.0).getValue(Nutrient.ENERGY)
        assertEquals(Completeness.PARTIAL, total.completeness)
        assertTrue("the partial figure must name what is missing from it",
            dropped.displayName in total.unknownContributors)
    }

    @Test fun `a dish with no known values at all is not a zero`() = runBlocking {
        val recipe = ok(lookup.recipe(code("sambar")))
        val blank = recipe.ingredients.associate { it.code to NutrientProfile(emptyMap()) }
        val total = calculator.nutrientsForServing(recipe, blank, 1.0).getValue(Nutrient.ENERGY)
        assertEquals(Completeness.NONE, total.completeness)
    }

    // --- plausibility --------------------------------------------------------------------------

    /**
     * A band check, not an accuracy check. It cannot tell a right number from a nearly right one;
     * it catches the order-of-magnitude error, which is the one that reaches the screen looking
     * calm. The upper bound is set above pure ghee, so only a genuinely impossible figure trips it.
     */
    @Test fun `no dish lands outside the range physically available to food`() = runBlocking {
        val absurd = mutableListOf<String>()
        for (key in allRecipeKeys()) {
            val e = ok(lookup.nutrientsFor(code(key), 100.0))[Nutrient.ENERGY]
            assertTrue("$key has no energy figure", e is NutrientValue.Measured)
            val kcal = (e as NutrientValue.Measured).amount
            // Nothing made of food is below about 10 kcal per 100 g once it has any solids in it,
            // and nothing short of neat fat exceeds 900.
            if (kcal < 10.0 || kcal > 900.0) absurd += "$key: ${"%.0f".format(kcal)} kcal/100 g"
        }
        assertTrue("a dish landed outside what food can be:\n" + absurd.joinToString("\n"), absurd.isEmpty())
    }

    /**
     * The specific regression this project already made once: counting the frying bath instead of
     * what the dish keeps. A deep-fried snack sits near 250-350 kcal per 100 g, not 745.
     */
    @Test fun `deep fried snacks sit where deep fried snacks sit, not where a fat bath would put them`() = runBlocking {
        val deepFried = db.query("SELECT recipe_key FROM recipes WHERE oil_method = 'DEEP_FRY'")
            .map { it.str("recipe_key") }
        assertTrue("the set should contain deep-fried dishes", deepFried.size >= 4)
        val off = mutableListOf<String>()
        for (key in deepFried) {
            val kcal = (ok(lookup.nutrientsFor(code(key), 100.0))[Nutrient.ENERGY] as NutrientValue.Measured).amount
            if (kcal > 450.0) off += "$key: ${"%.0f".format(kcal)} kcal/100 g, which is a fat bath, not a snack"
        }
        assertTrue(off.joinToString("\n"), off.isEmpty())
    }

    @Test fun `the demo dishes are all present and resolvable`() = runBlocking {
        // The four demo beats name these. A miss here is a demo that stops on stage.
        listOf("sambar", "idli", "chapati", "toor_dal_tadka", "veg_biryani", "plain_vada")
            .forEach { key ->
                assertNotNull(
                    "$key must exist in the shipped database",
                    db.query("SELECT recipe_key FROM recipes WHERE recipe_key = ?", listOf(key)).firstOrNull()
                )
                assertTrue("$key must expose a composition", ok(lookup.recipe(code(key))).ingredients.isNotEmpty())
            }
    }
}
