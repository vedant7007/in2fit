package io.github.vedant7007.katori.data.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The shipped knowledge-facts file, checked row by row, and the retrieval over it.
 *
 * WHAT THIS PROVES. That every row the app can quote carries a source, that nothing in the file
 * is a diagnosis, a prescription or an IFCT figure, and that a request retrieves the rows it
 * should and none it should not. It does not prove a fact is TRUE: that is the authoring
 * discipline (read the source, quote the figure, cite it), and a test cannot read a citation.
 *
 * WHAT IT DOES NOT PROVE. Anything about the model. The rows are handed to a prompt that has
 * never run on the phone; how the model phrases them is measured there, not here.
 */
class KnowledgeFactsTest {

    private val shipped: KnowledgeFacts by lazy {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        val f = File(dir, "app/src/main/assets/" + KnowledgeFacts.ASSET_PATH)
        check(f.isFile) { "knowledge file not found at ${f.absolutePath}" }
        KnowledgeFacts.load { f.inputStream() }
    }

    // --- the file ---------------------------------------------------------------------------

    @Test fun `the shipped file loads and is not a stub`() {
        assertTrue("expected a real file, got ${shipped.facts.size} rows", shipped.facts.size >= 100)
        val byTopic = shipped.facts.groupingBy { it.topic }.eachCount().toSortedMap()
        println(buildString {
            appendLine("=== KNOWLEDGE FACTS, shipped file ===")
            appendLine("rows       ${shipped.facts.size}")
            byTopic.forEach { (t, n) -> appendLine("  ${t.padEnd(10)} $n") }
            appendLine("sources    ${shipped.facts.map { it.sourceUrl.substringAfter("//").substringBefore("/") }.toSet().size} distinct hosts")
        })
    }

    /** The hard rule. The loader enforces it too; this reads the shipped file so the rule is checked on what ships. */
    @Test fun `every row has a source, a URL and an access date`() {
        for (f in shipped.facts) {
            assertTrue("${f.id}: blank source", f.source.isNotBlank())
            assertTrue("${f.id}: source URL must be https, got '${f.sourceUrl}'", f.sourceUrl.startsWith("https://"))
            assertTrue("${f.id}: accessed must be an ISO date, got '${f.accessed}'", Regex("\\d{4}-\\d{2}-\\d{2}").matches(f.accessed))
        }
    }

    /** Decision 0002 and HANDOVER §6 rule 14: no IFCT 2017, no INDB, in any form, including as a reference. */
    @Test fun `no row cites the excluded Indian composition tables`() {
        for (f in shipped.facts) {
            val all = (f.source + " " + f.sourceUrl + " " + f.note + " " + f.fact).lowercase()
            assertTrue("${f.id} references IFCT/INDB", !all.contains("ifct") && !all.contains("indb"))
        }
    }

    /**
     * Spec 15.1 and 0015: the file may explain and guide; it may not diagnose or prescribe. A row
     * that read "you have anaemia" or "take 60 mg of iron" would hand the model a sentence it is
     * forbidden to say, and the prompt is the only thing standing between the row and the screen.
     */
    @Test fun `no row diagnoses or prescribes`() {
        val forbidden = listOf("you have ", "you are anaemic", "you are diabetic", "you should take", "take a supplement", "take iron tablets", "stop taking", "dose of")
        for (f in shipped.facts) {
            val text = f.fact.lowercase()
            forbidden.forEach { p -> assertTrue("${f.id} reads like a diagnosis or prescription: '$p'", !text.contains(p)) }
        }
    }

    /**
     * Diagnostic thresholds are deliberately absent (file header): the reference range comes
     * from the person's own report. A haemoglobin or glucose cut-off in a row would let the model
     * turn "below the range printed on it" into a named condition.
     */
    @Test fun `no row carries a diagnostic threshold`() {
        val markers = listOf("haemoglobin below", "hemoglobin below", "g/dl", "mg/dl", "mmol/l", "hba1c", "fasting glucose")
        for (f in shipped.facts) {
            val text = f.fact.lowercase()
            markers.forEach { m -> assertTrue("${f.id} looks like a diagnostic threshold: '$m'", !text.contains(m)) }
        }
    }

    @Test fun `tags are normalised, non-empty and retrievable`() {
        for (f in shipped.facts) {
            assertTrue("${f.id} has no tags", f.tags.isNotEmpty())
            f.tags.forEach { t ->
                assertTrue("${f.id}: tag '$t' is too short to be a word", t.length >= 2)
                assertEquals("${f.id}: tag '$t' is not normalised", t, t.lowercase().trim())
            }
            // Every row must be reachable by at least one of its own tags, or it can never be shown.
            assertTrue("${f.id} is unreachable by its own tags", shipped.find(f.tags.first(), limit = Int.MAX_VALUE).contains(f))
        }
    }

    // --- retrieval ---------------------------------------------------------------------------

    @Test fun `the brief's iron request retrieves iron rows and nothing else`() {
        val hits = shipped.find("I have anaemia, what should I eat to increase iron")
        assertTrue("expected some rows, got none", hits.isNotEmpty())
        assertTrue("default limit is ${KnowledgeFacts.DEFAULT_LIMIT}", hits.size <= KnowledgeFacts.DEFAULT_LIMIT)
        hits.forEach { assertTrue("${it.id} retrieved for an iron question", it.topic == "iron" || "iron" in it.tags || "anaemia" in it.tags) }
        // The top row hits both words; a row hitting one of them cannot outrank it.
        assertTrue("top row should match both 'iron' and 'anaemia': ${hits.first().id}", "iron" in hits.first().tags && "anaemia" in hits.first().tags)
    }

    @Test fun `a dish on the plate retrieves the glycaemic rows for that dish`() {
        val hits = shipped.find("I'm having plain dosa and sambar, what should I add")
        assertTrue(hits.any { it.id == "gi.india_dishes" })
    }

    @Test fun `a tag matches whole words only, so tea does not match team`() {
        assertTrue(shipped.find("our team ate steak").none { "tea" in it.tags })
        assertTrue(shipped.find("chai with lunch").any { "chai" in it.tags })
    }

    @Test fun `punctuation and case in the request do not matter`() {
        val a = shipped.find("Vitamin-C, does it help IRON?")
        val b = shipped.find("vitamin c does it help iron")
        assertEquals(a.map { it.id }, b.map { it.id })
        assertTrue(a.isNotEmpty())
    }

    @Test fun `nothing matches nothing`() {
        assertTrue(shipped.find("").isEmpty())
        assertTrue(shipped.find("   ").isEmpty())
        assertTrue(shipped.find("zzqx plorf").isEmpty())
    }

    // --- select: the rows the engine actually used -----------------------------------------------

    @Test fun `select returns at most two rows, every one touching something the engine decided`() {
        val engine = KnowledgeFacts.nutrientTerms("IRON") + listOf("anaemia", "thotakura", "sprouted moong")
        val rows = shipped.select(engine, "what should I eat for more iron")
        assertEquals(KnowledgeFacts.SELECT_LIMIT, rows.size)
        val engineText = engine.joinToString(" ")
        rows.forEach { f -> assertTrue("${f.id} touches nothing the engine decided", f.tags.any { engineText.contains(it) }) }
    }

    /** A row that touches only the question is not what the engine used, while a used row exists. */
    @Test fun `a question-only row loses to an engine row`() {
        val rows = shipped.select(listOf("protein"), "I have anaemia, what should I eat for more protein and iron")
        rows.forEach { assertTrue("${it.id} is not a protein row", "protein" in it.tags) }
    }

    /** The engine decided nothing: a general question is answered from the question itself. */
    @Test fun `with no engine terms select falls back to the question`() {
        val rows = shipped.select(emptyList(), "does tea reduce iron absorption")
        assertEquals(shipped.find("does tea reduce iron absorption", KnowledgeFacts.SELECT_LIMIT), rows)
        assertTrue(rows.any { "tea" in it.tags })
    }

    /** Beat 2 in Hindi: the recogniser writes the nutrient in Devanagari and the tags are English. */
    @Test fun `a Hindi question as the recogniser writes it finds the same rows as the English one`() {
        assertEquals(shipped.select(emptyList(), "how much protein was in my lunch"), shipped.select(emptyList(), "मेरे लंच में कितना प्रोटीन था"))
        assertEquals(shipped.find("what should I eat for iron"), shipped.find("आयरन के लिए क्या खाना चाहिए"))
        assertTrue(shipped.find("मेरा आयरन कम है").any { "iron" in it.tags })
    }

    @Test fun `select is deterministic and honours the limit`() {
        val a = shipped.select(listOf("iron"), "iron", limit = 1)
        val b = shipped.select(listOf("iron"), "iron", limit = 1)
        assertEquals(a, b); assertEquals(1, a.size)
    }

    @Test fun `byId finds a row and misses an unknown id`() {
        assertEquals("iron.tea_timing", shipped.byId("iron.tea_timing")?.id)
        assertEquals(null, shipped.byId("no.such.row"))
    }

    // --- the parser ----------------------------------------------------------------------------

    private val header = "id,topic,tags,fact,source,source_url,accessed,note\n"

    @Test fun `quoted commas, doubled quotes and comment lines parse`() {
        val csv = "# a comment\n" + header +
            "a.b,t,x|y,\"A fact, with a comma and a \"\"quote\"\".\",Src,https://s,2026-09-20,\n"
        val k = KnowledgeFacts.parse(csv)
        assertEquals(1, k.facts.size)
        assertEquals("A fact, with a comma and a \"quote\".", k.facts[0].fact)
        assertEquals(setOf("x", "y"), k.facts[0].tags)
    }

    @Test fun `CRLF line endings parse the same as LF`() {
        val lf = header + "a.b,t,x,F,Src,https://s,2026-09-20,n\n"
        val crlf = lf.replace("\n", "\r\n")
        assertEquals(KnowledgeFacts.parse(lf).facts, KnowledgeFacts.parse(crlf).facts)
    }

    /** The rule, enforced where the file is read: a fact with no source does not load, let alone ship. */
    @Test fun `a row without a source is refused`() {
        val csv = header + "a.b,t,x,A fact.,,,2026-09-20,\n"
        try { KnowledgeFacts.parse(csv); fail("a sourceless row loaded") } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!, e.message!!.contains("source"))
        }
    }

    @Test fun `a duplicate id is refused`() {
        val csv = header + "a.b,t,x,F,S,https://s,d,\na.b,t,x,G,S,https://s,d,\n"
        try { KnowledgeFacts.parse(csv); fail("a duplicate id loaded") } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!, e.message!!.contains("duplicate"))
        }
    }

    @Test fun `a wrong header is refused`() {
        try { KnowledgeFacts.parse("id,fact\na,b\n"); fail("a wrong header loaded") } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!, e.message!!.contains("header"))
        }
    }
}
