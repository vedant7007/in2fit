package io.github.vedant7007.katori.ml.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The LOG pre-filter, measured against the authored intent case set.
 *
 * THE NUMBER THAT MATTERS IS MISROUTES, and it must be zero: a non-LOG utterance the pre-filter
 * short-circuits would be written into the person's history as a meal. The hit rate (LOG cases it
 * catches without a model call) is reported and asserted only at a floor, so a regression is
 * caught; it is a regression guard on a circular set, not accuracy.
 */
class LogPrefilterTest {

    private data class Row(val utterance: String, val expect: Intent, val note: String)

    private fun rows(): List<Row> {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        val f = File(dir, "data-authoring/intent-test-set.csv")
        check(f.isFile) { "case set not found at ${f.absolutePath}" }
        return f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1)
            .map { it.split(",") }
            .map { Row(it[0].trim(), Intent.valueOf(it[1].trim()), it.getOrElse(3) { "" }.trim()) }
    }

    @Test fun `measure the pre-filter and prove it never short-circuits a non-LOG utterance`() {
        val all = rows()
        val logs = all.filter { it.expect == Intent.LOG }
        val others = all.filter { it.expect != Intent.LOG }
        val hits = logs.filter { LogPrefilter.isCertainLog(it.utterance) }
        val missed = logs - hits.toSet()
        val misrouted = others.filter { LogPrefilter.isCertainLog(it.utterance) }

        println(buildString {
            appendLine("=== LOG PRE-FILTER, authored intent set ===")
            appendLine("cases                     ${all.size}")
            appendLine("LOG cases                 ${logs.size}")
            appendLine("  short-circuited         ${hits.size}  (${"%.1f".format(hits.size * 100.0 / logs.size)} %)  <- model calls saved")
            appendLine("  sent to the model       ${missed.size}")
            appendLine("non-LOG cases             ${others.size}")
            appendLine("  MISROUTED as LOG        ${misrouted.size}   <- the number that matters")
            if (missed.isNotEmpty()) { appendLine("--- LOG cases that still go to the model ---"); missed.forEach { appendLine("  ${it.utterance}  [${it.note}]") } }
            if (misrouted.isNotEmpty()) { appendLine("--- MISROUTED ---"); misrouted.forEach { appendLine("  ${it.utterance}  (expected ${it.expect})") } }
        })

        assertEquals(
            "a non-LOG utterance was short-circuited as a log; that writes a meal they never ate:\n" +
                misrouted.joinToString("\n") { "  ${it.utterance} (expected ${it.expect})" },
            0, misrouted.size,
        )
        assertTrue("pre-filter catches fewer than half the LOG cases: ${hits.size}/${logs.size}", hits.size * 2 >= logs.size)
    }

    // --- the shape of the rule, on cases chosen to be awkward -----------------------------------

    @Test fun `a past-tense eating word with no marker is certain`() {
        assertTrue(LogPrefilter.isCertainLog("I ate dal and rice"))
        assertTrue(LogPrefilter.isCertainLog("nenu annam pappu tinnanu"))
        assertTrue(LogPrefilter.isCertainLog("maine do roti khaya"))
        assertTrue(LogPrefilter.isCertainLog("Lunch was curd rice."))
    }

    /** The trap: a question that mentions eating in the past tense. The marker vetoes the log word. */
    @Test fun `a question containing an eating word is not a log`() {
        assertFalse(LogPrefilter.isCertainLog("was there any B12 in what I ate today"))
        assertFalse(LogPrefilter.isCertainLog("how many rotis have I eaten today"))
        assertFalse(LogPrefilter.isCertainLog("kal kya khaya tha"))
        assertFalse(LogPrefilter.isCertainLog("ninna em tinnanu"))
        assertFalse(LogPrefilter.isCertainLog("did I have enough protein yesterday"))
    }

    @Test fun `a question mark alone is enough to send it to the model`() {
        assertFalse(LogPrefilter.isCertainLog("I ate dal?"))
        assertTrue(LogPrefilter.isCertainLog("I ate dal"))
    }

    /** No positive evidence means the model decides, even when nothing looks like a question. */
    @Test fun `no eating word means the model decides`() {
        assertFalse(LogPrefilter.isCertainLog("a plate of vegetable biryani and some raita"))
        assertFalse(LogPrefilter.isCertainLog("ideas for lunch"))
        assertFalse(LogPrefilter.isCertainLog(""))
        assertFalse(LogPrefilter.isCertainLog("   "))
    }

    /** Not in the case set: sentences with a log word that are not about adding a meal. */
    @Test fun `a log word in a sentence about something else is not certain`() {
        assertFalse(LogPrefilter.isCertainLog("I had my report checked"))
        assertFalse(LogPrefilter.isCertainLog("delete the dal I had"))
        assertFalse(LogPrefilter.isCertainLog("I ate roti not rice, change it"))
        assertFalse(LogPrefilter.isCertainLog("I had a question"))
    }

    // --- the lists themselves ------------------------------------------------------------------

    /**
     * The shape of the "do" bug: Hindi "do" (two) sat in the single-word marker list and vetoed
     * every "maine do roti khaya". A single-word marker that equals a log word, or a word inside
     * a log phrase ("lunch" against "lunch was"), means that log can never fire, and nothing but
     * this test stops one being added. Marker PHRASES are compared as phrases: "was there" and
     * "lunch was" share a word and cannot collide, because each only matches as a whole run.
     */
    @Test fun `no single-word marker is a log word or part of a log phrase`() {
        val markers = LogPrefilter.MARKERS + LogPrefilter.LEADING_MARKERS
        val logWords = LogPrefilter.LOG_WORDS + LogPrefilter.MEAL_WAS.flatMap { it.split(' ') }
        val both = markers intersect logWords
        assertTrue("a marker that vetoes a log word, so that log can never fire: $both", both.isEmpty())
        val phraseClash = LogPrefilter.MULTI_WORD_MARKERS intersect LogPrefilter.MEAL_WAS.toSet()
        assertTrue("a phrase in both lists: $phraseClash", phraseClash.isEmpty())
    }

    /** Every list entry is lower case and has no stray spaces, or the whole-word match silently never hits. */
    @Test fun `list entries are normalised`() {
        (LogPrefilter.MARKERS + LogPrefilter.LEADING_MARKERS + LogPrefilter.LOG_WORDS).forEach {
            assertEquals("'$it' is not a normalised single word", it, it.trim().lowercase())
            assertFalse("'$it' has a space; put it in the multi-word list", it.contains(' '))
        }
        (LogPrefilter.MULTI_WORD_MARKERS + LogPrefilter.MEAL_WAS).forEach {
            assertEquals("'$it' is not normalised", it, it.trim().lowercase().replace(Regex("\\s+"), " "))
        }
    }

    /**
     * The reviewer's sheet is the only way a fluent speaker sees these words, and a log word
     * that is in the code but not on the sheet is a word nobody will ever check. The sheet is
     * hand-written; this keeps it honest.
     */
    @Test fun `every log word and phrase is on the reviewer's sheet`() {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        val sheet = File(dir, "data-authoring/log-words-review.md").readText()
        val missing = (LogPrefilter.LOG_WORDS + LogPrefilter.MEAL_WAS).filterNot { sheet.contains("`$it`") }
        assertTrue("log words the reviewer will never see: $missing", missing.isEmpty())
    }

    @Test fun `a plate in front of them is not a log yet`() {
        assertFalse(LogPrefilter.isCertainLog("having dal and roti now"))
        assertFalse(LogPrefilter.isCertainLog("ippudu annam pappu tintunna"))
        assertFalse(LogPrefilter.isCertainLog("about to eat upma"))
    }
}
