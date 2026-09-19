package io.github.vedant7007.katori.ml.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The packaged-label path (spec 12.2) on a device: a rendered FSSAI panel and then photographs
 * of real packs, through the real recogniser, [MlKitOcrEngine] and [NutritionLabelExtractor].
 *
 * WRITTEN BY ARJUN, NEVER RUN BY ARJUN. Only Rao drives the phone. Compiled in Arjun's worktree;
 * nothing in this file is a hardware claim until its report has been read off the device.
 *
 * WHAT IS ASSERTED. Zero WRONG VALUE and zero WRONG BASIS, because the per-serving column is a
 * silent factor of two that looks reasonable. MISSED is reported, not asserted: a missed row is
 * silence and the person types it in.
 *
 * PHOTOGRAPHS ARE STAGED, like the models: JPEGs or PNGs in `/sdcard/Android/media/<pkg>/packs/`,
 * with an optional `x.expected.txt` beside `x.jpg` written by a person reading the pack:
 * `basis` on the first line (`per 100 g`, `per 100 ml` or `per serving 30 g`), then one nutrient
 * row per line as `name|value|unit`, values in that basis, `#` for comments:
 *
 *     per 100 g
 *     Energy|520|kcal
 *     Protein|7.2|g
 *     Sodium|650|mg
 *
 * Run with `am instrument`, screen awake, read `katori-ocr-report.txt` or logcat `IN2FIT-OCR`:
 *
 *     adb shell am instrument -w -e class \
 *       io.github.vedant7007.katori.ml.vision.NutritionLabelOcrProbeTest \
 *       io.github.vedant7007.katori.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class NutritionLabelOcrProbeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val packsDir: File get() = File(ctx.externalMediaDirs.first(), "packs")

    private data class Printed(val name: String, val per100: String, val perServe: String, val rda: String, val expect: Double)

    private val panel = listOf(
        Printed("Energy (kcal)", "520", "156", "7.8%", 520.0),
        Printed("Protein (g)", "7.2", "2.2", "4.4%", 7.2),
        Printed("Carbohydrate (g)", "58.0", "17.4", "6.7%", 58.0),
        Printed("of which Sugars (g)", "2.1", "0.6", "", 2.1),
        Printed("Added Sugars (g)", "0", "", "", 0.0),
        Printed("Total Fat (g)", "29.0", "8.7", "13.0%", 29.0),
        Printed("Saturated Fat (g)", "13.0", "3.9", "17.7%", 13.0),
        Printed("Trans Fat (g)", "0", "0", "", 0.0),
        Printed("Sodium (mg)", "650", "195", "9.8%", 650.0),
        Printed("Dietary Fibre (g)", "3.1", "0.9", "", 3.1),
    )

    @Test
    fun a_renderedPanelReadsWithNoWrongValueAndNoWrongBasis() = runBlocking {
        heading("Nutrition label OCR, rendered FSSAI panel, demo build")
        val store = FrameStore()
        val label = when (val o = MlKitOcrEngine(store).readText(store.hold(render()))) {
            is Outcome.Ok -> {
                say("lines recognised ${o.value.blocks.size}")
                o.value.blocks.forEach { say("  line  [${it.left},${it.top}-${it.right},${it.bottom}]  \"${it.text}\"") }
                NutritionLabelExtractor.extract(o.value)
            }
            else -> { say("OCR: $o"); throw AssertionError("OCR produced nothing: $o") }
        }
        say("basis            ${label.basis}")
        say("ingredients      ${label.ingredients}")
        var read = 0; var missed = 0; var wrongValue = 0; var wrongBasis = 0
        for (p in panel) {
            val got = label.rows.filter { it.name.substringBefore("(").trim().equals(p.name.substringBefore("(").trim(), ignoreCase = true) }
            val verdict = when {
                got.isEmpty() -> { missed++; "MISSED" }
                got.size > 1 -> { missed++; "DUPLICATE" }
                got[0].value == p.perServe.toDoubleOrNull() && p.perServe != p.per100 -> { wrongBasis++; "WRONG BASIS" }
                got[0].value != p.expect -> { wrongValue++; "WRONG VALUE" }
                else -> { read++; "ok" }
            }
            say("  %-12s %-22s %-6s %-6s -> %s".format(verdict, p.name, p.per100, p.perServe,
                got.firstOrNull()?.let { "${it.value} ${it.unit}  \"${it.sourceRow}\"" } ?: "-"))
        }
        say("domain nutrients ${label.nutrients.map { "${it.key}=${it.value.amount}" }}")
        say("read $read/${panel.size}  missed $missed  wrong value $wrongValue  wrong basis $wrongBasis")
        assertTrue("the label path produced no rows at all", read > 0)
        assertEquals("the basis must be per 100 g", LabelBasis.Per100("g"), label.basis)
        assertEquals("WRONG VALUE must be zero", 0, wrongValue)
        assertEquals("WRONG BASIS must be zero", 0, wrongBasis)
    }

    @Test
    fun b_photographedPacksReadWithNoWrongValueAndNoWrongBasis() = runBlocking {
        heading("Nutrition label OCR, photographed packs")
        val photos = packsDir.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }?.sortedBy { it.name }.orEmpty()
        assertTrue("no pack photographs staged in ${packsDir.absolutePath}", photos.isNotEmpty())

        var decoded = 0; var withBasis = 0; var scored = 0; var read = 0; var missed = 0; var wrongValue = 0; var wrongBasis = 0
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
            val text = when (outcome) { is Outcome.Ok -> outcome.value; else -> { say("OCR: $outcome"); continue } }
            say("lines recognised ${text.blocks.size}")
            text.blocks.forEach { say("  line  [${it.left},${it.top}-${it.right},${it.bottom}]  \"${it.text}\"") }

            val label = NutritionLabelExtractor.extract(text)
            if (label.basis != null) withBasis++
            say("basis            ${label.basis}")
            label.rows.forEach { say("  row  ${it.name} | ${it.value} | ${it.unit}  <- \"${it.sourceRow}\"") }
            say("domain nutrients ${label.nutrients.map { "${it.key}=${it.value.amount}" }}")
            say("ingredients      ${label.ingredients}")

            val sidecar = File(photo.parentFile, photo.nameWithoutExtension + ".expected.txt")
            if (!sidecar.isFile) { say("no ${sidecar.name}: printed only, not scored"); continue }
            scored++
            val lines = sidecar.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            val expectedBasis = lines.firstOrNull().orEmpty()
            val basisOk = when {
                expectedBasis.startsWith("per 100 g") -> label.basis == LabelBasis.Per100("g")
                expectedBasis.startsWith("per 100 ml") -> label.basis == LabelBasis.Per100("ml")
                expectedBasis.startsWith("per serving") -> label.basis is LabelBasis.PerServing
                else -> false
            }
            if (!basisOk) { wrongBasis++; say("  WRONG BASIS  expected \"$expectedBasis\", read ${label.basis}") }
            for (e in lines.drop(1)) {
                val p = e.split("|").map { it.trim() }
                val value = p.getOrNull(1)?.toDoubleOrNull()
                if (p[0].isEmpty() || value == null) { say("  bad expected line: \"$e\""); continue }
                val got = label.rows.filter { it.name.substringBefore("(").trim().equals(p[0], ignoreCase = true) }
                val verdict = when {
                    got.isEmpty() -> { missed++; "MISSED" }
                    got.size > 1 -> { missed++; "DUPLICATE" }
                    got[0].value != value -> { wrongValue++; "WRONG VALUE" }
                    else -> { read++; "ok" }
                }
                say("  %-12s %-22s %s %s -> %s".format(verdict, p[0], value, p.getOrNull(2),
                    got.firstOrNull()?.let { "${it.value} ${it.unit}" } ?: "-"))
            }
        }
        say("")
        say("photos ${photos.size}  decoded $decoded  with basis $withBasis  scored $scored  " +
            "read $read  missed $missed  wrong value $wrongValue  wrong basis $wrongBasis")
        assertTrue("no photograph decoded", decoded > 0)
        assertEquals("WRONG VALUE must be zero", 0, wrongValue)
        assertEquals("WRONG BASIS must be zero", 0, wrongBasis)
    }

    /** An FSSAI-format panel: title, three-column header, ten rows, an ingredient line. */
    private fun render(): Bitmap {
        val bmp = Bitmap.createBitmap(1240, 1000, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp).apply { drawColor(Color.WHITE) }
        val body = Paint().apply { color = Color.BLACK; textSize = 30f; isAntiAlias = true; typeface = Typeface.SANS_SERIF }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        c.drawText("NUTRITIONAL INFORMATION", 60f, 60f, bold)
        c.drawText("Per 100 g", 560f, 130f, bold)
        c.drawText("Per serve (30 g)", 780f, 130f, bold)
        c.drawText("%RDA per serve", 1020f, 130f, bold)
        panel.forEachIndexed { i, r ->
            val y = 200f + i * 58f
            c.drawText(r.name, 60f, y, body)
            c.drawText(r.per100, 580f, y, body)
            if (r.perServe.isNotEmpty()) c.drawText(r.perServe, 820f, y, body)
            if (r.rda.isNotEmpty()) c.drawText(r.rda, 1060f, y, body)
        }
        c.drawText("INGREDIENTS: Refined wheat flour (maida), Sugar, Edible vegetable oil (palm oil), Salt.", 60f, 860f, body)
        c.drawText("Net Wt: 150 g    MRP Rs. 45.00 (incl. of all taxes)", 60f, 930f, body)
        return bmp
    }

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
