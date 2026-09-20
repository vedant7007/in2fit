package io.github.vedant7007.katori.ml.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The how-much question, read the way a person means it (the screenshot of 21 Sep and five like it). */
class NutrientWordsTest {

    @Test fun `everyday words name their nutrient`() {
        assertEquals(setOf("ENERGY"), NutrientWords.named("how many calories in that"))
        assertEquals(setOf("CARBOHYDRATE"), NutrientWords.named("is that a lot of carbs"))
        assertEquals(setOf("PROTEIN"), NutrientWords.named("how much protein did those chapatis have"))
        assertEquals(setOf("SODIUM"), NutrientWords.named("was there a lot of salt in it"))
        assertEquals(setOf("IRON", "VITAMIN_B12"), NutrientWords.named("did I get enough iron and B12 this week"))
        assertEquals(emptySet<String>(), NutrientWords.named("what did I eat on Tuesday"))
    }

    @Test fun `a question for the whole picture asks for all figures`() {
        for (q in listOf(
            "I said that I ate two chapatiis, can you tell me the nutritional information of it",
            "what did that give me", "what is the nutrition of the two chapatis I logged", "what was in that",
            "how much did that give me", "give me the numbers for my lunch", "what are the macros of my breakfast",
        )) assertTrue("'$q'", NutrientWords.asksForAll(q))
    }

    /** A question about WHAT was eaten is not a how-much question: its answer is the items and the time. */
    @Test fun `a question about what was eaten is not one`() {
        for (q in listOf("what did I eat on Tuesday", "what did I have for lunch", "did I eat rice today", "how many calories in that")) {
            assertFalse("'$q'", NutrientWords.asksForAll(q))
        }
    }
}
