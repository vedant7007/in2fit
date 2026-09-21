package io.github.vedant7007.katori.orchestration

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vedant7007.katori.data.food.AndroidFoodDbSource
import io.github.vedant7007.katori.data.food.LookupMealResolver
import io.github.vedant7007.katori.data.food.SqliteFoodLookup
import io.github.vedant7007.katori.data.local.Diary
import io.github.vedant7007.katori.data.local.KatoriDatabase
import io.github.vedant7007.katori.data.local.ProfileStore
import io.github.vedant7007.katori.data.local.RoomLabStore
import io.github.vedant7007.katori.data.local.RoomMealStore
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.vision.FrameStore
import io.github.vedant7007.katori.ml.vision.LabReportExtractor
import io.github.vedant7007.katori.ml.vision.MlKitOcrEngine
import io.github.vedant7007.katori.ml.vision.PdfPages
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * THE COLD PHONE'S FIRST SIXTY SECONDS, MADE TRUE BEFORE THE JUDGES SEE THEM. A fresh iQOO has
 * no profile, no diary and no report, and the one time an empty diary was seen it made the worst
 * screenshot of the project. This writes the demo's starting state THROUGH THE APP'S OWN PATHS,
 * into the app's own database file (`KatoriDatabase.NAME`, the same migrations): the profile
 * through `ProfileStore.save` with the values the first-run pickers write (enum names, never a
 * word the app would not have stored); a week of meals through the real `LookupMealResolver` and
 * `RoomMealStore.save`, dated over the last six days, three of them SPOKEN in Hindi with the
 * demo set's own Devanagari as the transcript and three TYPED in English, so the diary reads
 * like someone used the app; and the lab report BY THE ROUTE BEAT 3 USES ON THE DAY: `-e report
 * pdf` draws the report as a PDF, renders it with the platform's `PdfRenderer`, reads it with ML
 * Kit on this phone and the real extractor, and saves ONLY what was read, after checking it
 * against what was printed (one misread number is a stop, 0036). The PDF itself is left at
 * `<externalMediaDirs>/in2fit-lab-report.pdf` for `cold-phone.ps1` to put in Downloads, so the
 * presenter opens the same file through the picker on stage. `-e report true` is the older
 * fallback, the values straight through `RoomLabStore`. Nothing is written behind a store's back.
 *
 * At the end it reads everything back through `Diary` and `ProfileStore`, prints what it found,
 * and prints how long each part took. Run by `tools/cold-phone.ps1`; the app must not be running.
 *
 * What it does NOT seed: today's meal (Beat 1 logs it live), and any stored advice (a seeded
 * meal's "Advise again" computes live). Seed logic Rao (21 Sep 12:35); the stores, the PDF route
 * and the read-back Arjun (21 Sep 13:00).
 */
class DemoSeedTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val report = File(ctx.externalMediaDirs.first(), "katori-seed-report.txt").also { it.parentFile?.mkdirs(); it.delete() }
    private fun say(s: String) { android.util.Log.i("katori-seed", s); report.appendText(s + "\n") }

    private fun item(name: String, quantity: Double?, unit: String?) = ParsedItem(
        spokenName = name, quantity = quantity, unit = unit, matchedFoodCode = null,
        confidence = ConfidenceRules.of(if (quantity == null) ConfidenceReason.QUANTITY_INFERRED else ConfidenceReason.QUANTITY_STATED),
    )

    /** One seeded turn: how it was logged, in what language, what was said (verbatim, as the recogniser would return it), what the model extracted. */
    private class Turn(val source: String, val languageTag: String, val said: String, val items: List<ParsedItem>)

    /**
     * The week, as the demo set says it (`data-authoring/demo-utterance-set.csv`, the Hindi rows
     * verbatim): spoken Hindi and typed English alternating, oldest first, none today.
     */
    private val week: List<Turn> = listOf(
        Turn("SPOKEN", "hi", "मैंने दो रोटी और थोड़ी दाल खाई", listOf(item("roti", 2.0, null), item("dal", null, null))),
        Turn("TYPED", "en-IN", "three idlis and sambar", listOf(item("idli", 3.0, null), item("sambar", 1.0, "katori"))),
        Turn("SPOKEN", "hi", "एक plate चावल, दाल और एक कटोरी दही", listOf(item("rice", 1.0, "plate"), item("dal", null, null), item("curd", 1.0, "katori"))),
        Turn("TYPED", "en-IN", "a glass of milk and one boiled egg", listOf(item("milk", 1.0, "glass"), item("egg", 1.0, null))),
        Turn("SPOKEN", "hi", "नाश्ते में तीन इडली और सांबर खाया", listOf(item("idli", 3.0, null), item("sambar", null, null))),
        Turn("TYPED", "en-IN", "two rotis and palak", listOf(item("roti", 2.0, null), item("palak", 1.0, "katori"))),
    )

    /** The report as printed, and as the seed expects the reader to read it back. */
    private val printedReport = listOf(
        Triple("Haemoglobin", "9.8 g/dL", "12.0 - 15.0"),
        Triple("Ferritin", "8.0 ng/mL", "15.0 - 150.0"),
    )
    private val expected = listOf(
        LabValue("Haemoglobin", 9.8, "g/dL", 12.0, 15.0, LocalDate.MIN),
        LabValue("Ferritin", 8.0, "ng/mL", 15.0, 150.0, LocalDate.MIN),
    )

    @Test
    fun seed() = runBlocking {
        val t0 = System.nanoTime()
        fun ms() = (System.nanoTime() - t0) / 1_000_000
        val db = Room.databaseBuilder(ctx, KatoriDatabase::class.java, KatoriDatabase.NAME).addMigrations(*KatoriDatabase.MIGRATIONS).build()
        val foods = AndroidFoodDbSource.open(ctx)
        val zone = ZoneId.systemDefault()
        try {
            say("=== DEMO SEED ${java.time.LocalDateTime.now()} into ${ctx.getDatabasePath(KatoriDatabase.NAME)} ===")
            // The profile: the demo person, as the first-run pickers write it (enum NAMES, `ProfileStore`
            // 0037), and Hindi as the speech language (0031). Activity stays absent until Priya's rule
            // names its levels; a word the rule does not know is a guess stored as a fact.
            val profile = ProfileStore(db)
            profile.save(name = args.getString("name", "Vedant"), ageYears = 19, weightKg = 62.0, heightCm = 172.0, sex = "MALE", activity = null, goal = "MAINTAIN", lifeContext = "HOSTEL_STUDENT", dietType = "VEGETARIAN")
            profile.setSpeechLanguage("hi")
            val profileMs = ms()
            say("profile: written (19 / 62 / 172, MALE, MAINTAIN, HOSTEL_STUDENT, VEGETARIAN, speech hi) in $profileMs ms")

            // The week: six meals on six different days, lunch and dinner times, none today. The
            // store is given the turn's source and language exactly as `CurrentTurn` gives them in
            // the app (Rao, 21 Sep 11:20); a Hindi turn keeps its Devanagari transcript, the model's
            // copy is romanised and is what the resolver sees here as the items.
            var current = week.first()
            val resolver = LookupMealResolver(SqliteFoodLookup(foods))
            val store = RoomMealStore(db, languageTag = { current.languageTag }, source = { current.source })
            var written = 0
            val seededIds = mutableListOf<Long>()
            week.forEachIndexed { i, turn ->
                current = turn
                val meal = ParsedMeal(turn.items, ConfidenceRules.combine(turn.items.map { it.confidence }), turn.said)
                when (val r = resolver.resolve(meal, turn.languageTag)) {
                    is Outcome.Ok -> {
                        val at = LocalDate.now(zone).minusDays((6 - i).toLong()).atTime(if (i % 2 == 0) LocalTime.of(13, 10) else LocalTime.of(20, 30)).atZone(zone).toInstant()
                        when (val id = store.save(r.value, at)) {
                            is Outcome.Ok -> { written++; seededIds += id.value; say("meal ${id.value} ${turn.source}/${turn.languageTag} at $at: ${r.value.items.map { "${it.snapshot.foodCode} ${it.snapshot.grams} g" }} from \"${turn.said}\"") }
                            else -> say("meal NOT saved for \"${turn.said}\": $id")
                        }
                    }
                    else -> say("meal NOT resolved for \"${turn.said}\": $r")
                }
            }
            val mealsMs = ms() - profileMs
            say("meals: $written of ${week.size} in $mealsMs ms")
            assertTrue("fewer than 5 meals seeded", written >= 5)

            // The report, only when asked. "pdf": Beat 3's route on the day. "true": the values straight through the store.
            val reportStart = ms()
            when (args.getString("report", "false")) {
                "pdf" -> seedReportThroughPdf(db, zone)
                "true" -> {
                    val date = LocalDate.now(zone).minusDays(9)
                    val n = RoomLabStore(db).save(expected.map { it.copy(reportDate = date) })
                    say("report: $n values saved straight through the store (haemoglobin 9.8 and ferritin 8, both below the range printed)")
                }
                else -> say("report: not seeded (pass -e report pdf for Beat 3's route, or -e report true for the store alone)")
            }
            val reportMs = ms() - reportStart

            // READ IT ALL BACK the way the screens do, and print what is there.
            val verifyStart = ms()
            val diary = Diary(db, foods)
            val days = diary.days(7).first()
            val logged = days.filter { it.meals.isNotEmpty() }
            val row = profile.profile.first()
            val labs = diary.labs().first()
            say("VERIFY profile: name=${row?.name} age=${row?.age_years} weight=${row?.weight_kg} height=${row?.height_cm} sex=${row?.sex} context=${row?.life_context} diet=${row?.diet_type} speech=${row?.speech_language_tag}")
            days.forEach { d ->
                val energy = d.figures.firstOrNull { it.total.nutrient == Nutrient.ENERGY }?.total
                say("VERIFY ${d.date}: ${d.meals.size} meal(s) ${d.meals.map { m -> "[${m.source}/${m.items.joinToString("+") { it.display_name ?: it.spoken_name }}]" }} energy=${energy?.let { "${it.amount} ${it.unit} ${it.completeness}" } ?: "none"}")
            }
            say("VERIFY labs: ${labs.size} ${labs.map { "${it.test_name} ${it.value} ${it.unit} [${it.reference_low}-${it.reference_high}] ${it.report_date}" }}")
            val readBack = days.flatMap { it.meals }.associateBy { it.id }
            val seededBack = seededIds.mapNotNull { readBack[it] }
            say("VERIFY seeded meals read back: ${seededBack.size} of ${seededIds.size} (${seededBack.count { it.source == "SPOKEN" }} spoken/hi, ${seededBack.count { it.source == "TYPED" }} typed/en-IN); logged days in the week: ${logged.size}; today: ${days.last().meals.size} meal(s), which must be 0 for Beat 1 on the day")
            val seededLabs = labs.count { l -> expected.any { e -> e.testName == l.test_name && e.value == l.value } }
            say("VERIFY seeded lab values read back: $seededLabs (${if (args.getString("report", "false") == "false") 0 else expected.size} seeded this run; more means an earlier run on a used database)")
            assertTrue("every seeded meal must read back through Diary", seededBack.size == seededIds.size)
            assertTrue("the seeded meals must carry their source and language", seededBack.all { it.source == "SPOKEN" || it.source == "TYPED" })
            assertTrue("the profile row must read back Hindi", row?.speech_language_tag == "hi")
            // At least the seeded values (a re-run on a used database finds the earlier run's too; a fresh phone finds exactly these).
            if (args.getString("report", "false") != "false") assertTrue("the seeded lab values must read back", seededLabs >= expected.size)
            if (days.last().meals.isNotEmpty()) say("WARNING: today already has meals; on the day the phone must be fresh so Beat 1 logs the first one live")
            val verifyMs = ms() - verifyStart
            say("SEED TIMING: profile $profileMs ms, meals $mealsMs ms, report $reportMs ms, verify $verifyMs ms, total ${ms()} ms")
            say("=== SEED END ===")
        } finally {
            db.close(); foods.close()
        }
        Unit
    }

    /**
     * Beat 3's route: the report drawn as a PDF, rendered by the platform, read by ML Kit on THIS
     * phone, extracted, compared with what was printed, and only then saved. A misread value is a
     * stop, not a save; the seed then says so and the fallback (`-e report true`) is the person's call.
     */
    private suspend fun seedReportThroughPdf(db: KatoriDatabase, zone: ZoneId) {
        val date = LocalDate.now(zone).minusDays(9)
        val pdf = File(ctx.externalMediaDirs.first(), "in2fit-lab-report.pdf")
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val paint = Paint().apply { color = Color.BLACK; textSize = 14f; isAntiAlias = true }
            val bold = Paint(paint).apply { textSize = 18f; isFakeBoldText = true }
            val c: Canvas = page.canvas
            c.drawColor(Color.WHITE)
            c.drawText("Haematology report", 40f, 60f, bold)
            c.drawText("Reported on ${date.dayOfMonth}/${date.monthValue}/${date.year}", 40f, 90f, paint)
            c.drawText("Test", 40f, 140f, bold); c.drawText("Result", 260f, 140f, bold); c.drawText("Reference range", 400f, 140f, bold)
            printedReport.forEachIndexed { i, (name, value, range) ->
                val y = 175f + i * 32f
                c.drawText(name, 40f, y, paint); c.drawText(value, 260f, y, paint); c.drawText(range, 400f, y, paint)
            }
            doc.finishPage(page)
            pdf.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        say("report: PDF written, ${pdf.length()} bytes, at $pdf (cold-phone.ps1 puts it in Downloads for the picker)")

        val pages = PdfPages.render(ctx.contentResolver, Uri.fromFile(pdf))
        val bitmap = (pages as? Outcome.Ok)?.value?.firstOrNull()
        if (bitmap == null) { say("report: NOT saved, the PDF did not render: $pages"); assertTrue(false); return }
        val frames = FrameStore()
        val read = MlKitOcrEngine(frames).readText(frames.hold(bitmap, 0))
        val text = (read as? Outcome.Ok)?.value
        if (text == null) { say("report: NOT saved, the recogniser read nothing: $read"); assertTrue(false); return }
        val extracted = LabReportExtractor.extract(text)
        say("report: read ${text.blocks.size} lines, extracted ${extracted.fields.map { "${it.testName}=${it.value} ${it.unit ?: "?"} [${it.referenceLow}-${it.referenceHigh}]" }}, date ${extracted.reportDate}")

        // Every printed value must have been read exactly; anything else is a stop (0036's rule 1).
        val misread = expected.filter { e ->
            extracted.fields.none { f -> f.testName.equals(e.testName, ignoreCase = true) && f.value == e.value && f.referenceLow == e.referenceLow && f.referenceHigh == e.referenceHigh }
        }
        if (misread.isNotEmpty() || extracted.reportDate != date) {
            say("report: NOT saved, the reader disagreed with the print: missing or wrong ${misread.map { it.testName }}, date read ${extracted.reportDate} vs $date. Use -e report true as the fallback.")
            assertTrue(false); return
        }
        val values = extracted.fields.map { LabValue(it.testName, it.value, it.unit.orEmpty(), it.referenceLow, it.referenceHigh, extracted.reportDate ?: date) }
        val n = RoomLabStore(db).save(values)
        say("report: $n values saved BY THE PDF ROUTE, as read on this phone (haemoglobin 9.8 and ferritin 8, both below the range printed)")
    }
}
