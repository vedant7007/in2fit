package io.github.vedant7007.katori.data.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matching is where a wrong answer is most likely and most harmful, so these tests are weighted
 * towards what must NOT match.
 */
class FoodTextMatchingTest {

    private val aliases = mapOf(
        "dal" to "toor_dal_cooked",
        "pappu" to "toor_dal_cooked",
        "పప్పు" to "toor_dal_cooked",
        "curd" to "curd",
        "dahi" to "curd",
        "perugu" to "curd",
        "rice" to "rice_cooked",
        "annam" to "rice_cooked",
        "groundnut oil" to "groundnut_oil",
        "bendakaya" to "okra",
    )

    @Test
    fun `exact match wins`() {
        assertEquals("toor_dal_cooked", FoodTextMatching.match("dal", aliases)?.key)
        assertEquals(FoodTextMatching.MatchStrength.EXACT, FoodTextMatching.match("dal", aliases)?.strength)
    }

    @Test
    fun `case and punctuation do not matter`() {
        assertEquals("curd", FoodTextMatching.match("  Dahi, ", aliases)?.key)
    }

    @Test
    fun `native script matches without transliteration`() {
        assertEquals("toor_dal_cooked", FoodTextMatching.match("పప్పు", aliases)?.key)
    }

    @Test
    fun `a food name inside a longer phrase is found`() {
        assertEquals("groundnut_oil", FoodTextMatching.match("two spoons of groundnut oil", aliases)?.key)
    }

    @Test
    fun `short names get no fuzzy slack`() {
        // "dahi" and "dal" are three and four characters. One edit apart must not match.
        assertEquals(0, FoodTextMatching.toleranceFor(3))
        assertEquals(0, FoodTextMatching.toleranceFor(4))
        assertNull("'dil' must not become 'dal'", FoodTextMatching.match("dil", aliases))
    }

    @Test
    fun `a longer name tolerates a small typo`() {
        assertEquals("okra", FoodTextMatching.match("bendakya", aliases)?.key)
    }

    @Test
    fun `nonsense matches nothing`() {
        assertNull(FoodTextMatching.match("zzzqqq", aliases))
        assertNull(FoodTextMatching.match("", aliases))
        assertNull(FoodTextMatching.match("   ", aliases))
    }

    @Test
    fun `a roman query never fuzzy-matches onto a native script alias`() {
        val onlyNative = mapOf("పప్పు" to "toor_dal_cooked")
        assertNull(FoodTextMatching.match("pappu", onlyNative))
    }

    @Test
    fun `matching is deterministic across repeated runs`() {
        val results = (1..50).map { FoodTextMatching.match("two spoons of groundnut oil", aliases)?.key }
        assertEquals(1, results.distinct().size)
    }

    @Test
    fun `edit distance is symmetric and correct`() {
        assertEquals(0, FoodTextMatching.editDistance("dal", "dal"))
        assertEquals(1, FoodTextMatching.editDistance("dal", "dahl"))
        assertEquals(
            FoodTextMatching.editDistance("pappu", "papu"),
            FoodTextMatching.editDistance("papu", "pappu"),
        )
    }

    @Test
    fun `normalise keeps indic characters intact`() {
        assertTrue(FoodTextMatching.normalise("పప్పు").isNotEmpty())
        assertTrue(FoodTextMatching.isNativeScript("పప్పు"))
        assertTrue(FoodTextMatching.isNativeScript("दाल"))
        assertTrue(!FoodTextMatching.isNativeScript("dal"))
    }
}
