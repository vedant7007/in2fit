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
 * THE MEASUREMENT THAT MATTERS for beat 3: photographs of REAL lab reports, taken with a phone,
 * through the real recogniser, [MlKitOcrEngine] and [LabReportExtractor].
 *
 * WRITTEN BY ARJUN, NEVER RUN BY ARJUN, AND NOT YET COMPILED BY ARJUN EITHER: the laptop's
 * Gradle is Rao's (COORDINATION.md) and this session has no handle on the container. It is
 * written against the same APIs as `LabReportOcrProbeTest`, which did compile, plus
 * `BitmapFactory` and `android.media.ExifInterface`. If it does not compile, delete it and say
 * so; the rendered-page probe is the one that must run first.
 *
 * WHY THIS EXISTS. Every row the extractor was tuned on is authored. The single real OCR
 * observation this project has is `9.8g/dL` from one rendered line (`0004`). Until this test has
 * run over a photograph, the extractor is tuned to imagined output and nobody should trust it.
 *
 * PHOTOGRAPHS ARE STAGED, NOT BUNDLED, like the models. Put JPEGs or PNGs in
 * `/sdcard/Android/media/<pkg>/reports/`. A missing directory FAILS the test with the path it
 * looked in; it does not skip, because a skipped hardware check reads as a pass later.
 *
 * GROUND TRUTH IS WRITTEN BY A PERSON READING THE PAPER. Beside `x.jpg`, an optional
 * `x.expected.txt`, one printed test per line, `name|value|unit|low|high`, blank where the paper
 * is blank, `#` for comments:
 *
 *     Haemoglobin|9.8|g/dL|13.0|17.0
 *     Vitamin B12|250|pg/mL||
 *     Total Cholesterol|210|mg/dL||200
 *
 * With a sidecar the photo is scored: WRONG VALUE and WRONG RANGE must be zero, MISSED is
 * reported. Without one, every field is printed beside the row it was read from so a person can
 * check it against the paper, and nothing is scored.
 *
 * Run with `am instrument`, screen awake, read `katori-ocr-report.txt` or logcat `IN2FIT-OCR`:
 *
 *     adb shell am instrument -w -e class \
 *       io.github.vedant7007.katori.ml.vision.PhotographedReportProbeTest \
 *       io.github.vedant7007.katori.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class PhotographedReportProbeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val reportsDir: File get() = File(ctx.externalMediaDirs.first(), "reports")

    private data class Expected(val name: String, val value: Double, val unit: String?, val lo: Double?, val hi: Double?)

    @Test
    fun photographedReportsReadWithNoWrongValueAndNoWrongRange() = runBlocking {
        heading("Lab report OCR, photographed reports")
        val photos = reportsDir.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }
            ?.sortedBy { it.name }.orEmpty()
        assertTrue("no photographs staged in ${reportsDir.absolutePath}", photos.isNotEmpty())

        var decoded = 0; var linesTotal = 0
        var scored = 0; var read = 0; var missed = 0; var wrongValue = 0; var wrongRange = 0
        for (photo in photos) {
            heading(photo.name)
            val frame = decode(photo)
            if (frame == null) { say("could not decode"); continue }
            decoded++
            say("decoded          ${frame.bitmap.width}x${frame.bitmap.height}, exif rotation ${frame.rotationDegrees}")

            val store = FrameStore()
            val started = SystemClock.elapsedRealtime()
            val outcome = MlKitOcrEngine(store).readText(store.hold(frame.bitmap, frame.rotationDegrees))
            say("ocr              ${SystemClock.elapsedRealtime() - started} ms")
            val text = when (outcome) {
                is Outcome.Ok -> outcome.value
                else -> { say("OCR: $outcome"); continue }
            }
            linesTotal += text.blocks.size
            say("lines recognised ${text.blocks.size}")
            text.blocks.forEach { say("  line  [${it.left},${it.top}-${it.right},${it.bottom}]  \"${it.text}\"") }

            val extracted = LabReportExtractor.extract(text)
            say("fields extracted ${extracted.fields.size}, report date ${extracted.reportDate}")
            extracted.fields.forEach {
                say("  field  ${it.testName} | ${it.value} | ${it.unit} | ${it.referenceLow}..${it.referenceHigh}  <- \"${it.sourceRow}\"")
            }

            val sidecar = File(photo.parentFile, photo.nameWithoutExtension + ".expected.txt")
            if (!sidecar.isFile) { say("no ${sidecar.name}: printed only, not scored"); continue }
            scored++
            for (e in sidecar.readLines().mapNotNull(::parseExpected)) {
                val got = extracted.fields.filter { it.testName.contains(e.name, ignoreCase = true) }
                val verdict = when {
                    got.isEmpty() -> { missed++; "MISSED" }
                    got.size > 1 -> { missed++; "DUPLICATE" }
                    got[0].value != e.value -> { wrongValue++; "WRONG VALUE" }
                    got[0].referenceLow != e.lo || got[0].referenceHigh != e.hi -> { wrongRange++; "WRONG RANGE" }
                    else -> { read++; "ok" }
                }
                say("  %-12s %-24s %s %s %s..%s -> %s".format(verdict, e.name, e.value, e.unit, e.lo, e.hi,
                    got.firstOrNull()?.let { "${it.value} ${it.unit} ${it.referenceLow}..${it.referenceHigh}" } ?: "-"))
            }
        }
        say("")
        say("photos ${photos.size}  decoded $decoded  lines $linesTotal  scored $scored  " +
            "read $read  missed $missed  wrong value $wrongValue  wrong range $wrongRange")

        assertTrue("no photograph decoded", decoded > 0)
        assertTrue("OCR recognised no text on any photograph", linesTotal > 0)
        assertEquals("WRONG VALUE must be zero", 0, wrongValue)
        assertEquals("WRONG RANGE must be zero", 0, wrongRange)
    }

    /** `name|value|unit|low|high`; a line that does not parse is reported and skipped. */
    private fun parseExpected(line: String): Expected? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#")) return null
        val p = t.split("|").map { it.trim() }
        val value = p.getOrNull(1)?.toDoubleOrNull()
        if (p[0].isEmpty() || value == null) { say("  bad expected line: \"$line\""); return null }
        return Expected(p[0], value, p.getOrNull(2)?.takeIf { it.isNotEmpty() },
            p.getOrNull(3)?.toDoubleOrNull(), p.getOrNull(4)?.toDoubleOrNull())
    }

    /**
     * Decodes with the long edge held under about 3,000 px, and reads the EXIF orientation the
     * camera wrote so ML Kit is told how the page is turned. ponytail: power-of-two subsampling;
     * a 12 MP page lands at 2,000 px wide, ~25 px per character on an A4 sheet, which is above
     * ML Kit's floor. If small print is missed, this is the first knob.
     */
    private fun decode(f: File): FrameStore.Frame? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
        val bitmap: Bitmap = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
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
