package io.github.vedant7007.katori.ml.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.SystemClock
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

/**
 * BEAT 3, MEASURED: the whole path, recognition ([MlKitOcrEngine]) then extraction
 * ([LabReportExtractor]), over every fixture bundled in the test APK under `lab-reports/`, with
 * a table of what it found against what is printed on each sheet. DEVICE-ONLY: the recogniser
 * is ML Kit. Nothing is staged; the fixtures ride in the APK.
 *
 * WRITTEN WITHOUT A PHONE, in the shape of `PhotographedReportProbeTest` (same decode, same
 * engine call, same sidecar format), for the integrator's measurement script. Until it has run
 * on the demo phone, Beat 3 is unproven; the README beside the fixtures is the threshold,
 * written before this ran, and this test asserts it:
 *
 *   1. WRONG VALUE = 0 and WRONG RANGE = 0 on every fixture, rendered or photographed.
 *   2. MISSED = 0 on the rendered sheets, except the two known-ceiling sheets (two-column,
 *      range-before-result), which may miss and may not misread.
 *   3. On the photographs of the demo's sheet: fasting glucose and HbA1c read with value and
 *      printed range on every photograph. Not asserted while there is no photograph; the table
 *      says NO PHOTOGRAPH in that case, in capitals, so a green run is not read as proof.
 *
 * A rendered image proves the extractor. Only a photograph proves the demo.
 *
 *     adb shell am instrument -w -e class io.github.vedant7007.katori.ml.vision.LabReportOcrTest \
 *       io.github.vedant7007.katori.test/androidx.test.runner.AndroidJUnitRunner
 *
 * The table lands in `katori-ocr-report.txt` under the app's external media dir and in logcat
 * under `IN2FIT-OCR`.
 */
@RunWith(AndroidJUnit4::class)
class LabReportOcrTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private data class Expected(val name: String, val value: Double, val unit: String?, val lo: Double?, val hi: Double?)
    private data class Score(var read: Int = 0, var missed: Int = 0, var noRange: Int = 0, var wrongValue: Int = 0, var wrongRange: Int = 0)

    @Test
    fun everyFixtureReadsWithNoWrongValueAndNoWrongRange() = runBlocking {
        heading("Lab report OCR, the Beat 3 fixture set (bundled)")
        val names = assets.list("lab-reports").orEmpty().filter { it.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg") }.sorted()
        assertTrue("no fixtures bundled under lab-reports/", names.isNotEmpty())

        val table = StringBuilder()
        table.appendLine("| sheet | kind | ocr ms | lines | rows | read | missed | no range | WRONG value | WRONG range | demo rows |")
        table.appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        val perKind = mutableMapOf<String, Score>()
        var demoRowsOnEveryPhoto = true; var photographs = 0
        var missedOutsideCeiling = 0

        for (name in names) {
            heading(name)
            val stem = name.substringBeforeLast('.')
            val sidecarLines = runCatching { assets.open("lab-reports/$stem.expected.txt").bufferedReader().readLines() }.getOrNull()
            val kind = sidecarLines?.firstOrNull { it.startsWith("# kind:") }?.substringAfter(":")?.trim() ?: if (stem.startsWith("photo-")) "photograph" else "rendered"
            if (kind == "photograph") photographs++

            // Through a file, so the decode is byte-for-byte the one PhotographedReportProbeTest uses.
            val file = File(ctx.cacheDir, name).also { f -> assets.open("lab-reports/$name").use { i -> f.outputStream().use { o -> i.copyTo(o) } } }
            val frame = decode(file)
            if (frame == null) { say("could not decode"); table.appendLine("| $stem | $kind | - | - | - | - | - | - | - | - | could not decode |"); continue }
            say("decoded          ${frame.bitmap.width}x${frame.bitmap.height}, exif rotation ${frame.rotationDegrees}")

            val store = FrameStore()
            val started = SystemClock.elapsedRealtime()
            val outcome = MlKitOcrEngine(store).readText(store.hold(frame.bitmap, frame.rotationDegrees))
            val ms = SystemClock.elapsedRealtime() - started
            say("ocr              $ms ms")
            val text = when (outcome) {
                is Outcome.Ok -> outcome.value
                else -> { say("OCR: $outcome"); table.appendLine("| $stem | $kind | $ms | 0 | - | - | - | - | - | - | OCR failed: $outcome |"); continue }
            }
            say("lines recognised ${text.blocks.size}")
            text.blocks.forEach { say("  line  [${it.left},${it.top}-${it.right},${it.bottom}]  \"${it.text}\"") }

            val extracted = LabReportExtractor.extract(text)
            say("fields extracted ${extracted.fields.size}, report date ${extracted.reportDate}")
            extracted.fields.forEach { say("  field  ${it.testName} | ${it.value} | ${it.unit} | ${it.referenceLow}..${it.referenceHigh}  <- \"${it.sourceRow}\"") }

            val expected = sidecarLines.orEmpty().mapNotNull(::parseExpected)
            if (expected.isEmpty()) { say("no sidecar: printed only, not scored"); table.appendLine("| $stem | $kind | $ms | ${text.blocks.size} | 0 | - | - | - | - | - | not scored |"); continue }

            val ceiling = stem.contains("two-column") || stem.contains("range-before-result")
            val s = Score()
            var demoGlucose = false; var demoHba1c = false
            for (e in expected) {
                val got = extracted.fields.filter { it.testName.contains(e.name, ignoreCase = true) }
                // A range read as NONE is silence (no rule fires; 0018's intended degradation),
                // a miss; a range read as something else is a wrong number.
                val verdict = when {
                    got.isEmpty() -> { s.missed++; if (kind == "rendered" && !ceiling) missedOutsideCeiling++; "MISSED" }
                    got.size > 1 -> { s.missed++; if (kind == "rendered" && !ceiling) missedOutsideCeiling++; "DUPLICATE" }
                    got[0].value != e.value -> { s.wrongValue++; "WRONG VALUE" }
                    got[0].referenceLow == null && got[0].referenceHigh == null && (e.lo != null || e.hi != null) -> { s.noRange++; if (kind == "rendered" && !ceiling) missedOutsideCeiling++; "NO RANGE" }
                    got[0].referenceLow != e.lo || got[0].referenceHigh != e.hi -> { s.wrongRange++; "WRONG RANGE" }
                    else -> { s.read++; "ok" }
                }
                if (verdict == "ok" && e.name.contains("Glucose", true)) demoGlucose = true
                if (verdict == "ok" && e.name.contains("HbA1c", true)) demoHba1c = true
                say("  %-12s %-24s %s %s %s..%s -> %s".format(verdict, e.name, e.value, e.unit, e.lo, e.hi,
                    got.firstOrNull()?.let { "${it.value} ${it.unit} ${it.referenceLow}..${it.referenceHigh}" } ?: "-"))
            }
            val demo = if (demoGlucose && demoHba1c) "both" else listOfNotNull("glucose".takeIf { demoGlucose }, "HbA1c".takeIf { demoHba1c }).ifEmpty { listOf("NEITHER") }.joinToString()
            if (kind == "photograph" && !(demoGlucose && demoHba1c)) demoRowsOnEveryPhoto = false
            perKind.getOrPut(kind) { Score() }.let { k -> k.read += s.read; k.missed += s.missed; k.noRange += s.noRange; k.wrongValue += s.wrongValue; k.wrongRange += s.wrongRange }
            table.appendLine("| $stem | $kind | $ms | ${text.blocks.size} | ${expected.size} | ${s.read} | ${s.missed} | ${s.noRange}${if (ceiling && (s.missed + s.noRange) > 0) " (known ceiling)" else ""} | ${s.wrongValue} | ${s.wrongRange} | $demo |")
        }

        heading("THE TABLE: what was found against what is printed")
        table.lines().forEach { say(it) }
        say("")
        perKind.forEach { (k, s) -> say("$k: read ${s.read}  missed ${s.missed}  no range ${s.noRange}  wrong value ${s.wrongValue}  wrong range ${s.wrongRange}") }
        if (photographs == 0) {
            say("NO PHOTOGRAPH IN THE SET. A rendered image proves the extractor; only a photograph proves the demo. Beat 3 is UNPROVEN by this run. See lab-reports/README.md for the three photographs to take.")
        } else {
            say("photographs $photographs; demo rows read on every photograph: $demoRowsOnEveryPhoto")
        }
        val wrongValue = perKind.values.sumOf { it.wrongValue }; val wrongRange = perKind.values.sumOf { it.wrongRange }
        say("threshold 1 (no wrong value, no wrong range, any fixture): ${if (wrongValue + wrongRange == 0) "HOLDS" else "FAILS"}")
        say("threshold 2 (rendered sheets miss nothing outside the two ceilings): ${if (missedOutsideCeiling == 0) "HOLDS" else "FAILS ($missedOutsideCeiling missed)"}")
        say("threshold 3 (both demo rows on every photograph): ${if (photographs == 0) "NOT MEASURED, no photograph" else if (demoRowsOnEveryPhoto) "HOLDS" else "FAILS"}")
        say("Beat 3 safe to perform live: ${if (wrongValue + wrongRange == 0 && missedOutsideCeiling == 0 && photographs > 0 && demoRowsOnEveryPhoto) "YES" else if (photographs == 0) "UNPROVEN (no photograph)" else "NO"}")

        assertEquals("WRONG VALUE must be zero", 0, wrongValue)
        assertEquals("WRONG RANGE must be zero", 0, wrongRange)
        assertEquals("a rendered sheet missed a row outside the two known ceilings", 0, missedOutsideCeiling)
        if (photographs > 0) assertTrue("a photograph of the demo sheet did not yield both demo rows", demoRowsOnEveryPhoto)
    }

    /** `name|value|unit|low|high[|printed row]`; a line that does not parse is reported and skipped. */
    private fun parseExpected(line: String): Expected? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#")) return null
        val p = t.split("|").map { it.trim() }
        val value = p.getOrNull(1)?.toDoubleOrNull()
        if (p[0].isEmpty() || value == null) { say("  bad expected line: \"$line\""); return null }
        return Expected(p[0], value, p.getOrNull(2)?.takeIf { it.isNotEmpty() }, p.getOrNull(3)?.toDoubleOrNull(), p.getOrNull(4)?.toDoubleOrNull())
    }

    /** As `PhotographedReportProbeTest.decode`: long edge under about 3,000 px, EXIF orientation honoured. */
    private fun decode(f: File): FrameStore.Frame? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
        val bitmap: Bitmap = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = when (ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        return FrameStore.Frame(bitmap, rotation)
    }

    private val report: File by lazy { File(ctx.externalMediaDirs.first(), "katori-ocr-report.txt") }

    private fun say(line: String) {
        Log.i("IN2FIT-OCR", line)
        report.appendText(line + "\n")
    }

    private fun heading(title: String) { say(""); say("=== $title ===") }
}
