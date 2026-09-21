package io.github.vedant7007.katori.ml.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Measures [LabReportExtractor] over a corpus of report rows, the way `MatchRateTest` measures the
 * food matcher: a table is printed, and the number that must be zero is asserted at zero.
 *
 * THE NUMBERS THAT MATTER ARE WRONG VALUE AND WRONG RANGE. A missed row is silence and the person
 * types the value in; a wrong value or an invented range fires a rule on a number that is not on
 * the report, which is the one failure this pipeline exists to prevent. The read rate is printed
 * so the corpus can be grown against evidence and asserted only at a floor.
 *
 * THE CORPUS IS AUTHORED, NOT PHOTOGRAPHED. Every row here is what a printed Indian lab report
 * looks like after ML Kit has read it, as far as anyone on this project knows, plus the one real
 * observation there is: the hardware run returned `9.8g/dL` with no space. It is a regression
 * guard over the parser, not a measurement of OCR. The OCR measurement is
 * `androidTest/.../ml/vision/LabReportOcrProbeTest`, and until Rao runs it nothing here says a
 * photographed report reads correctly.
 */
class LabReportExtractorTest {

    /** One row as OCR would return it, and what a person reading the print would say it means. */
    private data class Case(
        val row: String,
        val name: String?,       // null: the row must be dropped
        val value: Double? = null,
        val unit: String? = null,
        val lo: Double? = null,
        val hi: Double? = null,
        val note: String = "",
    )

    private val corpus = listOf(
        // The one real observation: ML Kit glued the unit to the number on the phone.
        Case("Haemoglobin 9.8g/dL 13.0-17.0", "Haemoglobin", 9.8, "g/dL", 13.0, 17.0, "hardware run, glued unit"),
        Case("Haemoglobin 9.8 g/dL 13.0 - 17.0", "Haemoglobin", 9.8, "g/dL", 13.0, 17.0),
        Case("Haemoglobin (Hb) 9.8 g/dL 13.0 – 17.0", "Haemoglobin (Hb)", 9.8, "g/dL", 13.0, 17.0, "en dash"),
        Case("Hb 9.8 L g/dL 13 - 17", "Hb", 9.8, "g/dL", 13.0, 17.0, "L flag between value and unit"),
        Case("Haemoglobin 9.8 gm% 13.0 to 17.0", "Haemoglobin", 9.8, "gm%", 13.0, 17.0, "'to' as separator"),
        Case("Haemoglobin: 9.8 g/dL (13.0 - 17.0)", "Haemoglobin", 9.8, "g/dL", 13.0, 17.0, "range in brackets"),
        Case("Haemoglobin 9.8 13.0 - 17.0", "Haemoglobin", 9.8, null, 13.0, 17.0, "unit column cut off"),
        Case("PCV 30.2 % 40 - 50", "PCV", 30.2, "%", 40.0, 50.0),
        Case("Neutrophils 65% 40-80", "Neutrophils", 65.0, "%", 40.0, 80.0, "glued percent"),
        Case("MCV 78 fL 80 - 100", "MCV", 78.0, "fL", 80.0, 100.0),
        Case("MCH 27 pg 27 - 32", "MCH", 27.0, "pg", 27.0, 32.0),
        Case("RBC Count 4.5 million/cumm 4.5 - 5.5", "RBC Count", 4.5, "million/cumm", 4.5, 5.5),
        Case("WBC Count 11,500 /cumm 4,000 - 11,000", "WBC Count", 11500.0, "/cumm", 4000.0, 11000.0, "Indian comma grouping"),
        Case("Platelet Count 1,50,000 /cumm 1,50,000 - 4,50,000", "Platelet Count", 150000.0, "/cumm", 150000.0, 450000.0, "lakh grouping"),
        Case("Platelets 250 x10^3/µL 150 - 450", "Platelets", 250.0, "x10^3/uL", 150.0, 450.0, "micro sign"),
        Case("ESR 12 mm/1st hr 0 - 15", "ESR", 12.0, "mm/1st hr", 0.0, 15.0, "digit inside the unit"),
        Case("Vitamin B12 250 pg/mL 200 - 900", "Vitamin B12", 250.0, "pg/mL", 200.0, 900.0, "digit in the name"),
        Case("Vitamin D (25-OH) 18.5 ng/mL 30 - 100", "Vitamin D (25-OH)", 18.5, "ng/mL", 30.0, 100.0, "number in the name"),
        Case("Vitamin D 25-OH 18.5 30 - 100", "Vitamin D 25-OH", 18.5, null, 30.0, 100.0, "number in the name, no unit"),
        Case("HbA1c 7.2 % 4.0 - 5.6", "HbA1c", 7.2, "%", 4.0, 5.6),
        Case("Glucose (Fasting) 126 mg/dL 70 - 100 H", "Glucose (Fasting)", 126.0, "mg/dL", 70.0, 100.0, "H flag after the range"),
        Case("Total Cholesterol 210 mg/dL < 200", "Total Cholesterol", 210.0, "mg/dL", null, 200.0, "upper bound only"),
        Case("HDL Cholesterol 38 mg/dL > 40", "HDL Cholesterol", 38.0, "mg/dL", 40.0, null, "lower bound only"),
        Case("Triglycerides 180 mg/dL Up to 150", "Triglycerides", 180.0, "mg/dL", null, 150.0),
        Case("LDL Cholesterol 130 mg/dL Optimal <100 Near Optimal 100-129 Borderline 130-159",
            "LDL Cholesterol", 130.0, "mg/dL", null, null, "three bands: range must be null"),
        Case("Serum Iron 45 µg/dL 60 - 170", "Serum Iron", 45.0, "ug/dL", 60.0, 170.0),
        Case("Ferritin 8.2 ng/mL 15 - 150", "Ferritin", 8.2, "ng/mL", 15.0, 150.0),
        Case("TSH 6.2 µIU/mL 0.4 - 4.2", "TSH", 6.2, "uIU/mL", 0.4, 4.2),
        Case("Free T4 1.1 ng/dL 0.8 - 1.8", "Free T4", 1.1, "ng/dL", 0.8, 1.8),
        Case("T3 1.2 ng/mL 0.8-2.0", "T3", 1.2, "ng/mL", 0.8, 2.0),
        Case("S. Creatinine 1.1 mg/dl 0.6-1.2", "S. Creatinine", 1.1, "mg/dl", 0.6, 1.2),
        Case("SGOT (AST) 45 U/L 0 - 40", "SGOT (AST)", 45.0, "U/L", 0.0, 40.0),
        Case("Bilirubin (Total) 1.2 mg/dL 0.3 - 1.2", "Bilirubin (Total)", 1.2, "mg/dL", 0.3, 1.2),
        Case("Sodium 132 mmol/L 135 - 145", "Sodium", 132.0, "mmol/L", 135.0, 145.0),
        Case("Vitamin B12 250 pg/mL", "Vitamin B12", 250.0, "pg/mL", null, null, "no range printed"),
        Case("Haemoglobin 9.8 g/dL Ref: 13.0 - 17.0 g/dL", "Haemoglobin", 9.8, "g/dL", 13.0, 17.0),
        Case("Haemoglobin 9.8L g/dL 13-17", "Haemoglobin", 9.8, "g/dL", 13.0, 17.0, "glued flag"),
        Case("Haemoglobin 9.8 g/dL 13.0-17.0 1", "Haemoglobin", 9.8, "g/dL", 13.0, 17.0, "footnote digit after the range"),
        Case("Iron 45 ug/dL 60-170 Previous: 52", "Iron", 45.0, "ug/dL", 60.0, 170.0),
        Case("Sample No. 2 Haemoglobin 9.8 g/dL 13-17", "Sample No. 2 Haemoglobin", 9.8, "g/dL", 13.0, 17.0, "prefix stays in the name"),
        Case("INR 1.1 0.8 - 1.2", "INR", 1.1, null, 0.8, 1.2, "no unit, as printed"),
        Case("eGFR 95 mL/min/1.73m2 > 60", "eGFR", 95.0, "mL/min/1.73m2", 60.0, null),
        Case("Platelets 250 K/uL 150-450", "Platelets", 250.0, null, 150.0, 450.0, "unit not in the lexicon"),
        // Found by probing, each a WRONG VALUE or an invented range before the fix that this row now guards.
        Case("Vitamin D 25 Hydroxy 18.5 30-100", "Vitamin D 25 Hydroxy", 18.5, null, 30.0, 100.0, "probe: first number was taken"),
        Case("Vitamin D3 25 OH Total 18.5 30 - 100", "Vitamin D3 25 OH Total", 18.5, null, 30.0, 100.0, "probe: first number was taken"),
        Case("Vitamin D (25-0H) 18.5 ng/mL 30 - 100", "Vitamin D (25-0H)", 18.5, "ng/mL", 30.0, 100.0, "probe: OCR O as 0 made a fake range"),
        Case("Haemoglobin 9,8 g/dL 13-17", null, note = "probe: comma decimal was read as 9; now refused"),
        // OCR damage degrades to silence on the range, never to a guess.
        Case("Haemoglobin 9.8 g/dL 13.0 17.0", "Haemoglobin", 9.8, "g/dL", null, null, "dash lost by OCR"),
        Case("Haemoglobin 9.8 13.0 17.0", null, note = "dash lost and no unit: nothing to keep"),
        Case("Haemoglobin 9.8 g/dL l3.0 - 17.0", "Haemoglobin", 9.8, "g/dL", null, null, "OCR l for 1"),
        Case("Hemoglobin 9.8 g/dL 12.0-15.0 (F) 13.0-17.0 (M)", "Hemoglobin", 9.8, "g/dL", null, null, "sex-specific bands: ambiguous"),
        // Ceilings, stated in the extractor's doc comment.
        Case("Haemoglobin 9.8 g/dL 13-17 Sodium 132 mmol/L 135-145", "Haemoglobin", 9.8, "g/dL", null, null, "CEILING two-column row"),
        Case("Haemoglobin 13.0 - 17.0 9.8 g/dL", "Haemoglobin 13.0 - 17.0", 9.8, "g/dL", null, null,
            "CEILING range printed before the result: no range, and the name keeps the print"),
        Case("CRP < 0.5 mg/L < 6", null, note = "value is itself a bound: ambiguous, dropped"),
        Case("Test Name Result Unit Reference Range", null, note = "header"),
        Case("Page 1 of 2", null, note = "no unit, no range"),
        Case("Age 34 Years Sex Male", null),
        Case("Ref No 12345 Collected 12/03/2026 10:30", null, note = "date and time are not values"),
        Case("Reported on 13/03/2026", null),
        Case("13.0 - 17.0", null, note = "range column fragment, no name"),
        Case("9.8 g/dL", null, note = "value column fragment, no name"),
        Case("Dr. A. Sharma MD (Pathology)", null),
    )

    @Test
    fun `no wrong value and no wrong range anywhere in the corpus`() {
        var read = 0; var missed = 0; var wrongValue = 0; var wrongRange = 0; var junk = 0
        val lines = mutableListOf<String>()
        for (c in corpus) {
            val got = parse(c.row)
            val verdict = when {
                c.name == null && got == null -> "dropped"
                c.name == null -> { junk++; "JUNK KEPT" }
                got == null -> { missed++; "MISSED" }
                got.value != c.value -> { wrongValue++; "WRONG VALUE" }
                got.referenceLow != c.lo || got.referenceHigh != c.hi -> { wrongRange++; "WRONG RANGE" }
                got.testName != c.name -> { wrongValue++; "WRONG NAME" }
                !got.unit.equals(c.unit, ignoreCase = true) -> { wrongValue++; "WRONG UNIT" }
                else -> { read++; "ok" }
            }
            lines += "%-12s %-70s -> %s".format(verdict, c.row.take(70), got?.let { field ->
                "${field.testName} | ${field.value} | ${field.unit} | ${field.referenceLow}..${field.referenceHigh}"
            } ?: "-")
        }
        val expected = corpus.count { it.name != null }
        println("=== lab report extraction, ${corpus.size} rows ===")
        lines.forEach(::println)
        println("read $read/$expected  missed $missed  wrong value $wrongValue  wrong range $wrongRange  junk kept $junk")

        assertEquals("WRONG VALUE must be zero: a wrong number fires a rule that is not on the report", 0, wrongValue)
        assertEquals("WRONG RANGE must be zero: an invented range is a comparison the report did not print", 0, wrongRange)
        assertEquals("junk rows must be dropped, not shown as tests", 0, junk)
        assertTrue("read rate fell below the floor: $read/$expected", read >= expected - 1)
    }

    // --- layout: rows reassembled from column-split lines -------------------------------------------

    @Test
    fun `a row split into column lines is read as one row, left to right`() {
        // Three columns, each its own ML Kit line, boxes on one baseline; listed out of order.
        val text = RecognisedText(listOf(
            line("13.0 - 17.0", left = 700, top = 100),
            line("Haemoglobin", left = 20, top = 102),
            line("9.8", left = 400, top = 98),
            line("g/dL", left = 520, top = 101),
        ))
        val fields = LabReportExtractor.extract(text).fields
        assertEquals(1, fields.size)
        assertEquals(LabField("Haemoglobin", 9.8, "g/dL", 13.0, 17.0, "Haemoglobin 9.8 g/dL 13.0 - 17.0"), fields[0])
    }

    @Test
    fun `adjacent rows do not merge and a header contributes nothing`() {
        val text = RecognisedText(listOf(
            line("Test", 20, 20), line("Result", 400, 20), line("Unit", 520, 20), line("Range", 700, 20),
            line("Haemoglobin", 20, 100), line("9.8", 400, 100), line("g/dL", 520, 100), line("13.0 - 17.0", 700, 100),
            line("PCV", 20, 160), line("30.2", 400, 160), line("%", 520, 160), line("40 - 50", 700, 160),
            line("Ferritin", 20, 220), line("8.2", 400, 220), line("ng/mL", 520, 220), line("15 - 150", 700, 220),
        ))
        val fields = LabReportExtractor.extract(text).fields
        assertEquals(listOf("Haemoglobin", "PCV", "Ferritin"), fields.map { it.testName })
        assertEquals(listOf(9.8, 30.2, 8.2), fields.map { it.value })
        assertEquals(listOf(13.0, 40.0, 15.0), fields.map { it.referenceLow })
        assertEquals(listOf(17.0, 50.0, 150.0), fields.map { it.referenceHigh })
    }

    @Test
    fun `a range that drifted onto its own row leaves the value with no range, never a neighbour's`() {
        // The range column of the second row sits a full line lower than its value: it becomes its
        // own row and is dropped. The value keeps NO range. It must not inherit the row above's.
        val text = RecognisedText(listOf(
            line("Haemoglobin", 20, 100), line("9.8", 400, 100), line("g/dL", 520, 100), line("13.0 - 17.0", 700, 100),
            line("Ferritin", 20, 160), line("8.2", 400, 160), line("ng/mL", 520, 160),
            line("15 - 150", 700, 230),
        ))
        val fields = LabReportExtractor.extract(text).fields
        assertEquals(2, fields.size)
        val ferritin = fields.single { it.testName == "Ferritin" }
        assertNull(ferritin.referenceLow)
        assertNull(ferritin.referenceHigh)
    }

    @Test
    fun `nothing readable is an empty report, not an invented one`() {
        val text = RecognisedText(listOf(line("Dr. A. Sharma", 20, 20), line("MD (Pathology)", 20, 60)))
        val report = LabReportExtractor.extract(text)
        assertTrue(report.fields.isEmpty())
        assertNull(report.reportDate)
    }

    // --- the report date ----------------------------------------------------------------------------

    @Test
    fun `the reported-on date is preferred over collection and birth dates`() {
        val text = RecognisedText(listOf(
            line("Date of Birth: 05/08/1991", 20, 20),
            line("Collected: 12/03/2026 09:10", 20, 60),
            line("Reported: 13-Mar-2026 18:00", 20, 100),
            line("Haemoglobin 9.8 g/dL 13.0 - 17.0", 20, 160),
        ))
        assertEquals(LocalDate.of(2026, 3, 13), LabReportExtractor.extract(text).reportDate)
    }

    @Test
    fun `without a reported-on line the latest non-birth date is taken`() {
        val text = RecognisedText(listOf(
            line("DOB 05/08/1991", 20, 20),
            line("Sample received 12 March 2026", 20, 60),
            line("Printed 14/03/2026", 20, 100),
        ))
        assertEquals(LocalDate.of(2026, 3, 14), LabReportExtractor.extract(text).reportDate)
    }

    @Test
    fun `an impossible date is not a date`() {
        val text = RecognisedText(listOf(line("Ref 31/02/2026", 20, 20)))
        assertNull(LabReportExtractor.extract(text).reportDate)
    }

    // --- helpers ------------------------------------------------------------------------------------

    private fun line(text: String, left: Int, top: Int, height: Int = 40) =
        TextBlock(text, left, top, left + 12 * text.length, top + height)

    private fun parse(row: String): LabField? =
        LabReportExtractor.extract(RecognisedText(listOf(line(row, 0, 0)))).fields.singleOrNull()

    /** A PDF's pages are one report: repeats kept once, a repeat sample kept twice, the first date wins. */
    @Test
    fun `pages merge into one report without dropping a genuine repeat`() {
        val hb = LabField("Haemoglobin", 9.8, "g/dL", 13.0, 17.0, "Haemoglobin 9.8 g/dL 13-17")
        val hbAgain = LabField("Haemoglobin", 9.8, "g/dL", 13.0, 17.0, "Haemoglobin 9.8 g/dL 13.0-17.0")
        val hbRepeat = LabField("Haemoglobin", 10.4, "g/dL", 13.0, 17.0, "Haemoglobin 10.4 g/dL 13-17")
        val vitD = LabField("Vitamin D", 18.5, "ng/mL", 30.0, 100.0, "Vitamin D 18.5 ng/mL 30-100")
        val merged = LabReportExtractor.merge(listOf(
            LabReport(listOf(hb), reportDate = null),
            LabReport(listOf(hbAgain, vitD), reportDate = LocalDate.of(2026, 9, 12)),
            LabReport(listOf(hbRepeat), reportDate = LocalDate.of(2026, 9, 13)),
        ))
        assertEquals(listOf(hb, vitD, hbRepeat), merged.fields)
        assertEquals(LocalDate.of(2026, 9, 12), merged.reportDate)
        assertEquals(LabReport(emptyList(), null), LabReportExtractor.merge(emptyList()))
    }
}
