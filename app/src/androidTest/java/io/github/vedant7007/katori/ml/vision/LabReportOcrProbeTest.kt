package io.github.vedant7007.katori.ml.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Beat 3's camera path, end to end, on a device: a rendered lab report through the real ML Kit
 * recogniser, through [MlKitOcrEngine], through [LabReportExtractor].
 *
 * WRITTEN BY ARJUN, NEVER RUN BY ARJUN. Only Rao drives the phone. Nothing in this file is a
 * hardware claim until its report has been read off the device; the JVM test on the extractor
 * says nothing about what ML Kit does to a printed table.
 *
 * Run it with `am instrument`, never with gradle (COORDINATION.md, rule 2), with the screen held
 * awake (rule 3), and read the numbers from `katori-ocr-report.txt` in the app's external media
 * directory or from logcat tag `IN2FIT-OCR`, never from the exit code (rule 1):
 *
 *     adb shell am instrument -w -e class \
 *       io.github.vedant7007.katori.ml.vision.LabReportOcrProbeTest \
 *       io.github.vedant7007.katori.test/androidx.test.runner.AndroidJUnitRunner
 *
 * THE REPORT IS RENDERED, NOT PHOTOGRAPHED. A rendered page is the easy case: square, sharp,
 * evenly lit, one typeface. A pass here means the row reconstruction and the parser survive ML
 * Kit's real line segmentation of a table; it does not mean a phone photo of a real report reads.
 * That measurement needs a real report under the camera, which is the next probe after this one.
 *
 * WHAT IS ASSERTED. Zero wrong values and zero wrong ranges, because a wrong number fires a rule
 * the report did not print. The read rate is reported and asserted only above zero: a floor for a
 * synthetic page would be a number nobody has measured.
 */
@RunWith(AndroidJUnit4::class)
class LabReportOcrProbeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    /** A row as printed: name, value, unit, range. Null bounds mean the print has no range. */
    private data class Printed(val name: String, val value: String, val unit: String, val range: String,
                               val expectValue: Double, val lo: Double?, val hi: Double?)

    private val rows = listOf(
        Printed("Haemoglobin", "9.8", "g/dL", "13.0 - 17.0", 9.8, 13.0, 17.0),
        Printed("PCV", "30.2", "%", "40 - 50", 30.2, 40.0, 50.0),
        Printed("RBC Count", "4.5", "million/cumm", "4.5 - 5.5", 4.5, 4.5, 5.5),
        Printed("Platelet Count", "1,50,000", "/cumm", "1,50,000 - 4,50,000", 150000.0, 150000.0, 450000.0),
        Printed("Serum Iron", "45", "ug/dL", "60 - 170", 45.0, 60.0, 170.0),
        Printed("Ferritin", "8.2", "ng/mL", "15 - 150", 8.2, 15.0, 150.0),
        Printed("Vitamin B12", "250", "pg/mL", "200 - 900", 250.0, 200.0, 900.0),
        Printed("Total Cholesterol", "210", "mg/dL", "< 200", 210.0, null, 200.0),
        Printed("HbA1c", "7.2", "%", "", 7.2, null, null),
    )

    private val reportDate = LocalDate.of(2026, 3, 13)

    @Test
    fun a_renderedReportReadsWithNoWrongValueAndNoWrongRange() = runBlocking {
        heading("Lab report OCR, rendered page, demo build")
        val page = render()
        val store = FrameStore()
        val outcome = MlKitOcrEngine(store).readText(store.hold(page))
        val report = measure(outcome, "upright")
        assertTrue("the OCR path produced no fields at all", report.read > 0)
        assertEquals("WRONG VALUE must be zero", 0, report.wrongValue)
        assertEquals("WRONG RANGE must be zero", 0, report.wrongRange)
    }

    /**
     * The same page handed over as CameraX hands a portrait capture over: landscape pixels plus
     * the clockwise rotation that makes them upright. If this reads nothing, the rotation
     * convention in [FrameStore] is the wrong way round and every camera capture will be too.
     */
    @Test
    fun b_rotatedCaptureReadsTheSame() = runBlocking {
        heading("Lab report OCR, capture rotated 90")
        val page = render()
        val sideways = Bitmap.createBitmap(page, 0, 0, page.width, page.height, Matrix().apply { postRotate(-90f) }, true)
        val store = FrameStore()
        val outcome = MlKitOcrEngine(store).readText(store.hold(sideways, rotationDegrees = 90))
        val report = measure(outcome, "rotated 90")
        assertTrue("rotated capture read nothing: FrameStore's rotation convention is inverted", report.read > 0)
        assertEquals("WRONG VALUE must be zero", 0, report.wrongValue)
        assertEquals("WRONG RANGE must be zero", 0, report.wrongRange)
    }

    private class Tally(var read: Int = 0, var missed: Int = 0, var wrongValue: Int = 0, var wrongRange: Int = 0, var extra: Int = 0)

    private fun measure(outcome: Outcome<RecognisedText>, label: String): Tally {
        val t = Tally()
        val text = when (outcome) {
            is Outcome.Ok -> outcome.value
            else -> { say("OCR ($label): $outcome"); return t }
        }
        say("lines recognised  ${text.blocks.size}")
        text.blocks.forEach { say("  line  [${it.left},${it.top}-${it.right},${it.bottom}]  \"${it.text}\"") }

        val extracted = LabReportExtractor.extract(text)
        say("fields extracted  ${extracted.fields.size}")
        say("report date       ${extracted.reportDate}  (printed $reportDate)")

        for (p in rows) {
            val got = extracted.fields.filter { it.testName.contains(p.name, ignoreCase = true) }
            val verdict = when {
                got.isEmpty() -> { t.missed++; "MISSED" }
                got.size > 1 -> { t.extra++; "DUPLICATE" }
                got[0].value != p.expectValue -> { t.wrongValue++; "WRONG VALUE" }
                got[0].referenceLow != p.lo || got[0].referenceHigh != p.hi -> { t.wrongRange++; "WRONG RANGE" }
                else -> { t.read++; "ok" }
            }
            say("  %-12s %-18s %-10s %-14s %-20s -> %s".format(verdict, p.name, p.value, p.unit, p.range,
                got.firstOrNull()?.let { "${it.value} ${it.unit} ${it.referenceLow}..${it.referenceHigh}  \"${it.sourceRow}\"" } ?: "-"))
        }
        val unexpected = extracted.fields.filter { f -> rows.none { f.testName.contains(it.name, ignoreCase = true) } }
        unexpected.forEach { say("  UNEXPECTED   ${it.testName} ${it.value} ${it.unit} ${it.referenceLow}..${it.referenceHigh}  \"${it.sourceRow}\"") }
        say("read ${t.read}/${rows.size}  missed ${t.missed}  wrong value ${t.wrongValue}  wrong range ${t.wrongRange}  " +
            "duplicate ${t.extra}  unexpected ${unexpected.size}")
        return t
    }

    /** A plausible printed report: header block, four-column table, footer. One typeface, 1240 px wide. */
    private fun render(): Bitmap {
        val bmp = Bitmap.createBitmap(1240, 1000, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp).apply { drawColor(Color.WHITE) }
        val body = Paint().apply { color = Color.BLACK; textSize = 30f; isAntiAlias = true; typeface = Typeface.SANS_SERIF }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val cols = floatArrayOf(60f, 520f, 700f, 900f)

        c.drawText("Name: Test Patient    Age: 34 Years    Sex: Male", 60f, 60f, body)
        c.drawText("Collected: 12/03/2026 09:10    Reported: 13/03/2026 18:00", 60f, 105f, body)
        c.drawText("Test Name", cols[0], 180f, bold)
        c.drawText("Result", cols[1], 180f, bold)
        c.drawText("Unit", cols[2], 180f, bold)
        c.drawText("Reference Range", cols[3], 180f, bold)
        rows.forEachIndexed { i, r ->
            val y = 250f + i * 62f
            c.drawText(r.name, cols[0], y, body)
            c.drawText(r.value, cols[1], y, body)
            c.drawText(r.unit, cols[2], y, body)
            c.drawText(r.range, cols[3], y, body)
        }
        c.drawText("Dr. A. Sharma MD (Pathology)          Page 1 of 1", 60f, 930f, body)
        return bmp
    }

    private val report: File by lazy { File(ctx.externalMediaDirs.first(), "katori-ocr-report.txt") }

    private fun say(line: String) {
        Log.i("IN2FIT-OCR", line)
        report.appendText(line + "\n")
    }

    private fun heading(title: String) { say(""); say("=== $title ===") }
}
