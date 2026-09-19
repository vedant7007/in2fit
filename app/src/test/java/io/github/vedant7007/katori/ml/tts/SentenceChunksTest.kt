package io.github.vedant7007.katori.ml.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one rule: everything is spoken, in order, and no chunk exceeds the engine's limit. */
class SentenceChunksTest {

    @Test
    fun `short text is one chunk, untouched`() {
        assertEquals(listOf("Two rotis and dal."), SentenceChunks.split("Two rotis and dal.", 4000))
    }

    @Test
    fun `splits on sentence boundaries and packs sentences up to the limit`() {
        val text = "One. Two! Three? Four."
        assertEquals(listOf("One. Two!", "Three?", "Four."), SentenceChunks.split(text, 10))
    }

    @Test
    fun `the danda ends a sentence`() {
        assertEquals(listOf("रोटी दाल।", "दही।"), SentenceChunks.split("रोटी दाल। दही।", 10))
    }

    @Test
    fun `a sentence longer than the limit is split on words, never dropped`() {
        val text = "alpha beta gamma delta"
        val chunks = SentenceChunks.split(text, 11)
        assertEquals(listOf("alpha beta", "gamma delta"), chunks)
    }

    @Test
    fun `every character is spoken exactly once, whatever the limit`() {
        val text = "Your last report shows iron below the printed range. Ask a doctor. Eat dal, spinach and jaggery-free sweets!"
        for (limit in listOf(1, 3, 8, 20, 50, 4000)) {
            val chunks = SentenceChunks.split(text, limit)
            assertTrue("limit $limit: a chunk exceeds it: $chunks", chunks.all { it.length <= limit })
            assertEquals("limit $limit", text.replace(Regex("\\s+"), ""), chunks.joinToString("").replace(Regex("\\s+"), ""))
        }
    }

    @Test
    fun `blank input is nothing to say`() {
        assertEquals(emptyList<String>(), SentenceChunks.split("  \n ", 100))
    }
}
