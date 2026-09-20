package io.github.vedant7007.katori.ml.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The half of Beat 3's fixture set that runs without a phone: every image in
 * `app/src/androidTest/assets/lab-reports/` has a sidecar, every sidecar row is well formed, and
 * the row AS PRINTED (the sidecar's sixth field) goes through [LabReportExtractor] alone and
 * yields the value and the printed range the sheet carries. A rendered image proves the
 * extractor; this proves the extractor on each fixture's rows before the recogniser has read
 * them. Only a photograph, through `LabReportOcrTest` on the device, proves the demo.
 *
 * THE THRESHOLD is in the README beside the fixtures, written before any run. This test holds
 * its part of it: WRONG VALUE = 0 and WRONG RANGE = 0 on every sheet, with the two known
 * ceilings (a two-column sheet, a range printed before the result) allowed to miss and never to
 * misread. The table it writes is `logs/lab-report-fixtures.md`.
 */
class LabReportFixturesTest {

    private val dir = File(System.getProperty("katori.projectDir") ?: error("katori.projectDir not set"), "app/src/androidTest/assets/lab-reports")

    private data class Expected(val name: String, val value: Double, val unit: String?, val lo: Double?, val hi: Double?, val printed: String)

    private fun sidecars(): Map<File, List<Expected>> =
        dir.listFiles { f -> f.name.endsWith(".expected.txt") }!!.sortedBy { it.name }.associateWith { f ->
            f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val p = line.split("|").map { it.trim() }
                Expected(p[0], p[1].toDouble(), p[2].ifEmpty { null }, p[3].toDoubleOrNull(), p[4].toDoubleOrNull(), p[5])
            }
        }

    private fun kindOf(sidecar: File): String =
        sidecar.readLines().firstOrNull { it.startsWith("# kind:") }?.substringAfter(":")?.trim() ?: "unknown"

    @Test fun `every fixture has a sidecar, a kind, and rows a person wrote from the sheet`() {
        val images = dir.listFiles { f -> f.extension.lowercase() in setOf("png", "jpg", "jpeg") }!!.map { it.name }.sorted()
        assertTrue("at least six sheets, as ruled: $images", images.size >= 6)
        for (img in images) {
            val sidecar = File(dir, img.substringBeforeLast('.') + ".expected.txt")
            assertTrue("$img has no sidecar", sidecar.isFile)
            assertTrue("$img's sidecar names no kind", kindOf(sidecar) in setOf("rendered", "photograph"))
        }
        val photographs = images.filter { it.startsWith("photo-") }
        println("fixtures ${images.size}: ${images.size - photographs.size} rendered, ${photographs.size} photographs" +
            if (photographs.isEmpty()) " (NO PHOTOGRAPH YET: the demo is unproven until one is added; see the README)" else "")
    }

    @Test fun `the extractor reads every printed row of every sheet with no wrong value and no wrong range`() {
        val out = StringBuilder("# Beat 3 fixtures through the extractor alone (JVM; the recogniser is device-only)\n\n")
        out.appendLine("| sheet | kind | rows | read | missed | no range | WRONG value | WRONG range |").appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
        var wrongValue = 0; var wrongRange = 0; var missedOutsideCeiling = 0
        for ((sidecar, rows) in sidecars()) {
            val sheet = sidecar.name.removeSuffix(".expected.txt")
            val ceiling = sheet.contains("two-column") || sheet.contains("range-before-result")
            var read = 0; var missed = 0; var noRange = 0; var wv = 0; var wr = 0
            for (e in rows) {
                // The row as printed, given to the extractor as one recognised line, the way a
                // square photograph of a one-column sheet reaches it.
                val text = RecognisedText(listOf(TextBlock(e.printed, 20, 100, 20 + 12 * e.printed.length, 140)))
                val got = LabReportExtractor.extract(text).fields.filter { it.testName.contains(e.name, ignoreCase = true) }
                // A range read as NONE is silence (no rule fires on the value: 0018's intended
                // degradation), a miss; a range read as something else is a wrong number.
                when {
                    got.isEmpty() -> { missed++; if (!ceiling) missedOutsideCeiling++ }
                    got[0].value != e.value -> { wv++; out.appendLine("<!-- WRONG VALUE on $sheet: ${e.printed} -> ${got[0].value} -->") }
                    got[0].referenceLow == null && got[0].referenceHigh == null && (e.lo != null || e.hi != null) -> { noRange++; if (!ceiling) missedOutsideCeiling++ }
                    got[0].referenceLow != e.lo || got[0].referenceHigh != e.hi -> { wr++; out.appendLine("<!-- WRONG RANGE on $sheet: ${e.printed} -> ${got[0].referenceLow}..${got[0].referenceHigh} -->") }
                    else -> read++
                }
            }
            wrongValue += wv; wrongRange += wr
            out.appendLine("| $sheet | ${kindOf(sidecar)} | ${rows.size} | $read | $missed | $noRange${if (ceiling && (missed + noRange) > 0) " (known ceiling)" else ""} | $wv | $wr |")
        }
        out.appendLine().appendLine("A rendered image proves the extractor. Only a photograph, through `LabReportOcrTest` on the device, proves the demo.")
        println(out)
        val root = File(System.getProperty("katori.projectDir")!!)
        File(root, "logs").mkdirs(); File(root, "logs/lab-report-fixtures.md").writeText(out.toString())
        assertEquals("WRONG VALUE must be zero", 0, wrongValue)
        assertEquals("WRONG RANGE must be zero", 0, wrongRange)
        assertEquals("a miss outside the two known ceilings", 0, missedOutsideCeiling)
    }

    /** The two rows the demo stands on, as printed on the sheet that will be photographed. */
    @Test fun `the demo's two rows are read from the sheet that will be printed`() {
        val rows = sidecars().entries.single { it.key.name.startsWith("rendered-01") }.value
        val text = RecognisedText(rows.mapIndexed { i, e -> TextBlock(e.printed, 20, 100 + i * 60, 20 + 12 * e.printed.length, 140 + i * 60) })
        val fields = LabReportExtractor.extract(text).fields
        val glucose = fields.single { it.testName.contains("Glucose") }
        val hba1c = fields.single { it.testName.contains("HbA1c") }
        assertEquals(142.0, glucose.value, 0.0); assertEquals(70.0, glucose.referenceLow!!, 0.0); assertEquals(100.0, glucose.referenceHigh!!, 0.0)
        assertEquals(6.4, hba1c.value, 0.0); assertEquals(4.0, hba1c.referenceLow!!, 0.0); assertEquals(5.6, hba1c.referenceHigh!!, 0.0)
    }
}
