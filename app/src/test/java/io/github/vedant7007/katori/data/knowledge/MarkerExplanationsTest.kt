package io.github.vedant7007.katori.data.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The shipped marker file loads, every row is cited, no row carries a threshold or names a
 * condition, and a printed test name finds the right row or none. What the words mean to a
 * reader is Nila's review, not a test.
 */
class MarkerExplanationsTest {

    private val shipped: MarkerExplanations by lazy {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        val f = File(dir, "app/src/main/assets/" + MarkerExplanations.ASSET_PATH)
        check(f.isFile) { "markers file not found at ${f.absolutePath}" }
        MarkerExplanations.load { f.inputStream() }
    }

    @Test
    fun `the shipped file loads, every row is cited, and none states a threshold or a condition`() {
        assertTrue(shipped.rows.size >= 5)
        val forbidden = listOf("normal range", "within normal", "deficien", "anaemi", "anemi", "diabet", "prediabet", "hypothyroid", "hyperthyroid", "mg/dl", "g/dl", "ng/ml", "pg/ml", "%", "may indicate", "suggests")
        shipped.rows.forEach { r ->
            assertTrue("${r.testName}: source", r.source.isNotBlank() && r.sourceUrl.startsWith("https://"))
            assertTrue("${r.testName}: accessed", Regex("\\d{4}-\\d{2}-\\d{2}").matches(r.accessed))
            val text = r.explanation.lowercase()
            assertTrue("${r.testName}: no number that could read as a threshold", Regex("\\d").containsMatchIn(r.aliases.fold(text) { t, a -> t.replace(a, "") }).not())
            forbidden.forEach { w -> assertTrue("${r.testName}: says '$w'", w !in text) }
        }
    }

    @Test
    fun `a printed name finds its row, the long HbA1c form beats haemoglobin, and an unknown name finds nothing`() {
        assertEquals("Haemoglobin", shipped.find("Haemoglobin")?.testName)
        assertEquals("Haemoglobin", shipped.find("Hemoglobin (Hb)")?.testName)
        assertEquals("HbA1c", shipped.find("HbA1c")?.testName)
        assertEquals("HbA1c", shipped.find("Glycosylated Haemoglobin (HbA1c)")?.testName)
        assertEquals("Vitamin D", shipped.find("Vitamin D 25 Hydroxy")?.testName)
        assertEquals("Vitamin D", shipped.find("25-OH Vitamin D")?.testName)
        assertEquals("Vitamin B12", shipped.find("Vitamin B12")?.testName)
        assertEquals("Ferritin", shipped.find("Serum Ferritin")?.testName)
        assertEquals("TSH", shipped.find("TSH")?.testName)
        assertEquals("Glucose", shipped.find("Fasting Blood Sugar")?.testName)
        assertNull(shipped.find("Platelet count"))
        assertNull("'Hb' inside 'HbA1c' is not the haemoglobin row", shipped.find("HbA1C").takeIf { it?.testName == "Haemoglobin" })
    }

    @Test
    fun `a row without a source, a duplicate alias, or a wrong header is refused`() {
        val header = MarkerExplanations.HEADER.joinToString(",")
        try { MarkerExplanations.parse("$header\nX,what it measures,,,2026-09-21\n"); fail("a sourceless row loaded") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("source")) }
        try { MarkerExplanations.parse("$header\nX|y,a,S,https://s,2026-09-21\nY,b,S,https://s,2026-09-21\n"); fail("a duplicate alias loaded") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("twice")) }
        try { MarkerExplanations.parse("name,text\nX,a\n"); fail("a wrong header loaded") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("header")) }
        assertEquals(0, MarkerExplanations.parse("$header\n").rows.size)
    }
}
