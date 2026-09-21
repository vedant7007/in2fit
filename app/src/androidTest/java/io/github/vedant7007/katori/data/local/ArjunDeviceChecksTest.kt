package io.github.vedant7007.katori.data.local

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vedant7007.katori.data.food.AndroidFoodDbSource
import io.github.vedant7007.katori.data.food.FoodClass
import io.github.vedant7007.katori.data.food.FoodQuery
import io.github.vedant7007.katori.data.food.LookupMealResolver
import io.github.vedant7007.katori.data.food.SqliteFoodLookup
import io.github.vedant7007.katori.data.knowledge.MarkerExplanations
import io.github.vedant7007.katori.domain.MealItemSnapshot
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.Period
import io.github.vedant7007.katori.domain.ResolvedItem
import io.github.vedant7007.katori.domain.ResolvedMeal
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientProfile
import io.github.vedant7007.katori.domain.model.NutrientTotal
import io.github.vedant7007.katori.domain.model.NutrientValue
import io.github.vedant7007.katori.domain.model.NutritionFigure
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.vision.FrameStore
import io.github.vedant7007.katori.ml.vision.LabReportExtractor
import io.github.vedant7007.katori.ml.vision.MlKitOcrEngine
import io.github.vedant7007.katori.ml.vision.PdfPages
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.Instant

/**
 * ARJUN'S DEVICE CHECKS, ONE SCRIPTED RUN (device queue items 10, 13-15, 24-28 and 35; 21 Sep). Opens
 * THE APP'S OWN DATABASE FILE through the same migrations the app runs, on the real device,
 * with whatever rows the phone holds, and checks each item against it. Every row this class
 * writes it deletes again; the person's diary is read, never left changed.
 *
 * Run: `tools/arjun-checks.ps1`, or
 *   adb shell am instrument -w -r -e class io.github.vedant7007.katori.data.local.ArjunDeviceChecksTest
 *       io.github.vedant7007.katori.test/androidx.test.runner.AndroidJUnitRunner
 * Every line it decides is logged as `ARJUN-CHECK <item> PASS|FAIL …` (tag `katori-arjun`) and
 * written to `<externalMediaDirs>/katori-arjun-checks.txt`. Stop the app first (the script does)
 * so two processes are not writing one WAL.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ArjunDeviceChecksTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private val db: KatoriDatabase by lazy {
        Room.databaseBuilder(ctx, KatoriDatabase::class.java, KatoriDatabase.NAME).addMigrations(*KatoriDatabase.MIGRATIONS).build()
    }
    private val foods by lazy { AndroidFoodDbSource.open(ctx) }
    private val diary by lazy { Diary(db, foods) }

    @Test
    fun a_item10and24_the_real_database_opened_through_the_migrations() {
        val version = db.openHelper.readableDatabase.version
        val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet()
        }
        val profileColumns = db.openHelper.readableDatabase.query("PRAGMA table_info(profile)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(1) else null }.toSet()
        }
        val meals = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM meals").use { it.moveToFirst(); it.getInt(0) }
        val labs = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM lab_values").use { it.moveToFirst(); it.getInt(0) }
        val profile = runBlocking { db.profileDao().get() }
        check("10/24 migration", version == 4 && setOf("water", "weights", "reminders").all { it in tables } && setOf("name", "activity", "speech_language_tag").all { it in profileColumns }) {
            "user_version=$version tables=${tables.size} meals=$meals labs=$labs profile=${profile?.let { "${it.age_years}/${it.weight_kg}/${it.height_cm} lang=${it.speech_language_tag}" } ?: "none"}"
        }
    }

    @Test
    fun b_item11_the_language_reads_from_the_profile_row() = runBlocking {
        val store = ProfileStore(db)
        val tag = store.speechLanguage.first()
        val row = db.profileDao().get()?.speech_language_tag
        check("11 language home", tag == (row ?: "hi")) { "row=$row read=$tag (null row reads hi)" }
    }

    @Test
    fun c_item13_the_diary_agrees_with_what_answer_speaks() = runBlocking {
        val day = diary.day(diary.today()).first()
        val context = RoomUserContextSource(db, foods).current()
        val spoken = context.periodTotals.firstOrNull { it.period == Period.TODAY }?.figures.orEmpty()
        val screenEnergy = day.figures.firstOrNull { it.total.nutrient == Nutrient.ENERGY }?.total
        val spokenEnergy = spoken.firstOrNull { it.total.nutrient == Nutrient.ENERGY }?.total
        val same = day.figures.map { it.total }.toSet() == spoken.map { it.total }.toSet()
        check("13 diary = answer", same) {
            "meals today=${day.meals.size} (${day.meals.joinToString { m -> m.items.joinToString("+") { it.display_name ?: it.spoken_name } }}) " +
                "screen energy=${screenEnergy?.amount} spoken energy=${spokenEnergy?.amount} figures screen=${day.figures.size} spoken=${spoken.size}"
        }
    }

    @Test
    fun d_item14and26_delete_an_item_then_the_meal_and_leave_nothing() = runBlocking {
        val before = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM meals").use { it.moveToFirst(); it.getInt(0) }
        val mealId = (RoomMealStore(db).save(testMeal(), Instant.now()) as Outcome.Ok).value
        val items = db.mealDao().itemsFor(mealId)
        val dal = items.first { it.spoken_name == "dal" }
        val kept = diary.deleteItem(dal.id)
        val afterItem = db.mealDao().meal(mealId)
        val remaining = db.mealDao().itemsFor(mealId)
        val bandOk = afterItem?.confidence_band == ConfidenceBand.GOOD && remaining.size == 1 && remaining.single().spoken_name == "roti"
        diary.deleteMeal(mealId)
        val gone = db.mealDao().meal(mealId) == null && db.mealDao().itemsFor(mealId).isEmpty()
        val after = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM meals").use { it.moveToFirst(); it.getInt(0) }
        check("14/26 delete", kept == mealId && bandOk && gone && after == before) {
            "meal $mealId: 2 items -> band after item delete=${afterItem?.confidence_band} remaining=${remaining.map { it.spoken_name }} gone=$gone meals before/after=$before/$after"
        }
    }

    @Test
    fun e_item15_search_gives_a_portion_with_grams_and_nutrients() = runBlocking {
        // As `SearchViewModel.portion` does it: the match's portion through the plate's own resolver.
        val lookup = SqliteFoodLookup(foods)
        val matches = (lookup.candidates(FoodQuery("dal", "en-IN"), 10) as? Outcome.Ok)?.value.orEmpty()
        val first = matches.firstOrNull()
        val resolved = first?.let { m ->
            val item = ParsedItem(m.displayName, 1.0, null, m.code.id, ConfidenceRules.of(ConfidenceReason.QUANTITY_STATED))
            (LookupMealResolver(lookup).resolve(ParsedMeal(listOf(item), item.confidence, m.displayName), "en-IN") as? Outcome.Ok)?.value
        }
        val portion = resolved?.items?.singleOrNull()
        val said = resolved?.parsed?.items?.singleOrNull()
        val protein = portion?.snapshot?.nutrients?.get(Nutrient.PROTEIN)
        check("15 search", first != null && portion?.snapshot?.foodCode == first.code.id && said?.unit != null && (portion.snapshot.grams ?: 0.0) > 0 && protein != null) {
            "matches=${matches.size} first=${first?.displayName} class=${first?.foodClass} unit=${said?.unit} grams=${portion?.snapshot?.grams} band=${portion?.confidence?.band} reasons=${portion?.confidence?.reasons} protein=$protein"
        }
    }

    @Test
    fun f_item25_water_and_weight_write_and_read_back() = runBlocking {
        val today = diary.bounds(diary.today())
        val before = diary.waterTotal(today).first()
        diary.logWater(250, "TAPPED")
        diary.logWater(250, "TAPPED")
        val after = diary.waterTotal(today).first()
        val rows = diary.water(today).first().takeLast(2)
        rows.forEach { diary.deleteWater(it.id) }
        val restored = diary.waterTotal(today).first()
        val waterOk = after == (before ?: 0) + 500 && restored == before

        val store = ProfileStore(db)
        val profileBefore = db.profileDao().get()
        store.recordWeight(61.5)
        val weights = store.weights.first()
        val profileAfter = db.profileDao().get()
        val weightOk = weights.lastOrNull()?.kg == 61.5 && profileAfter?.weight_kg == 61.5
        weights.lastOrNull()?.let { store.deleteWeight(it.id) }
        if (profileBefore == null) db.openHelper.writableDatabase.execSQL("DELETE FROM profile WHERE id = 1")
        else db.profileDao().upsert(profileBefore)
        check("25 water/weight", waterOk && weightOk) { "water before=$before after=$after restored=$restored; weight row=${weights.lastOrNull()?.kg} profile=${profileAfter?.weight_kg} (profile restored to ${profileBefore?.weight_kg})" }
    }

    @Test
    fun g_item27_the_marker_file_loads_from_the_apk_and_resolves_printed_names() {
        val markers = MarkerExplanations.load { ctx.assets.open(MarkerExplanations.ASSET_PATH) }
        val hb = markers.find("Haemoglobin")
        val hba1c = markers.find("Glycosylated Haemoglobin (HbA1c)")
        val none = markers.find("Platelet count")
        check("27 markers", markers.rows.size >= 7 && hb?.testName == "Haemoglobin" && hba1c?.testName == "HbA1c" && none == null) {
            "rows=${markers.rows.size} hb=${hb?.testName} long-hba1c=${hba1c?.testName} platelets=${none?.testName}"
        }
    }

    @Test
    fun h_item28_the_export_is_one_line_per_item_per_nutrient() = runBlocking {
        val (meals, labs) = diary.exportCsv()
        val mealLines = meals.trimEnd().split("\r\n")
        val labLines = labs.trimEnd().split("\r\n")
        val items = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM meal_items").use { it.moveToFirst(); it.getInt(0) }
        val nutrientRows = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM meal_item_nutrients").use { it.moveToFirst(); it.getInt(0) }
        val labRows = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM lab_values").use { it.moveToFirst(); it.getInt(0) }
        val ok = mealLines[0] == Export.MEAL_HEADER.joinToString(",") && labLines[0] == Export.LAB_HEADER.joinToString(",") &&
            labLines.size == 1 + labRows && mealLines.size >= 1 + nutrientRows && "TOTAL" !in meals.uppercase()
        check("28 export", ok) { "meal lines=${mealLines.size} (items=$items nutrient rows=$nutrientRows) lab lines=${labLines.size} (rows=$labRows)" }
    }

    /** Item 35: a PDF the test draws itself, rendered by the platform, read by ML Kit on THIS device, extracted. */
    @Test
    fun i_item35_a_pdf_page_renders_and_reads_through_the_real_recogniser() = runBlocking {
        val file = File(ctx.cacheDir, "arjun-check.pdf")
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val paint = Paint().apply { color = Color.BLACK; textSize = 14f; isAntiAlias = true }
            val c: Canvas = page.canvas
            c.drawColor(Color.WHITE)
            c.drawText("Reported on 12/09/2026", 40f, 60f, paint)
            c.drawText("Haemoglobin", 40f, 120f, paint); c.drawText("9.8 g/dL", 260f, 120f, paint); c.drawText("13.0 - 17.0", 400f, 120f, paint)
            c.drawText("Vitamin D 25 Hydroxy", 40f, 150f, paint); c.drawText("18.5 ng/mL", 260f, 150f, paint); c.drawText("30 - 100", 400f, 150f, paint)
            doc.finishPage(page)
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        val pages = PdfPages.render(ctx.contentResolver, Uri.fromFile(file))
        val bitmaps = (pages as? Outcome.Ok)?.value.orEmpty()
        val frames = FrameStore()
        val read = bitmaps.firstOrNull()?.let { MlKitOcrEngine(frames).readText(frames.hold(it, 0)) }
        val report = (read as? Outcome.Ok)?.value?.let { LabReportExtractor.extract(it) }
        val hb = report?.fields?.firstOrNull { it.testName.contains("Haemoglobin", ignoreCase = true) }
        check("35 pdf", bitmaps.size == 1 && hb != null && hb.value == 9.8 && hb.referenceLow == 13.0 && hb.referenceHigh == 17.0) {
            "render=${pages::class.java.simpleName} pages=${bitmaps.size} ${bitmaps.firstOrNull()?.let { "${it.width}x${it.height}" }} ocr=${read?.let { it::class.java.simpleName }} " +
                "fields=${report?.fields?.map { "${it.testName}=${it.value}${it.unit ?: ""} [${it.referenceLow}-${it.referenceHigh}]" }} date=${report?.reportDate}"
        }
        file.delete()
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun testMeal(): ResolvedMeal {
        fun item(name: String, code: String, grams: Double, protein: Double, reasons: List<ConfidenceReason>) = ResolvedItem(
            snapshot = MealItemSnapshot("$name (arjun check)", code, grams, mapOf(Nutrient.PROTEIN to protein, Nutrient.ENERGY to grams)),
            source = DataSource.AUTHORED_RECIPE,
            nutrients = NutrientProfile(mapOf(Nutrient.PROTEIN to NutrientValue.Measured(protein, Nutrient.PROTEIN.unit), Nutrient.ENERGY to NutrientValue.Measured(grams, Nutrient.ENERGY.unit))),
            confidence = ConfidenceRules.of(reasons),
        )
        val stated = listOf(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_STATED)
        val inferred = listOf(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_INFERRED, ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT)
        val items = listOf(item("roti", "chapati", 90.0, 7.7, stated), item("dal", "toor_dal_tadka", 180.0, 10.8, inferred))
        val parsed = ParsedMeal(
            items = listOf(ParsedItem("roti", 2.0, "piece", "chapati", ConfidenceRules.of(stated)), ParsedItem("dal", 1.0, "katori", "toor_dal_tadka", ConfidenceRules.of(inferred))),
            confidence = ConfidenceRules.combine(items.map { it.confidence }),
            rawTranscript = "arjun device check: two rotis and a little dal",
        )
        val figures = Nutrient.entries.map { n ->
            NutritionFigure(NutrientTotal(n, items.sumOf { it.snapshot.nutrients[n] ?: 0.0 }, n.unit, Completeness.COMPLETE, emptyList()), parsed.confidence, listOf(DataSource.AUTHORED_RECIPE))
        }
        return ResolvedMeal(parsed, items, figures)
    }

    private fun check(item: String, ok: Boolean, detail: () -> String) {
        val line = "ARJUN-CHECK $item ${if (ok) "PASS" else "FAIL"} ${detail()}"
        Log.i(TAG, line)
        report.appendText(line + "\n")
        assertTrue(line, ok)
    }

    companion object {
        private const val TAG = "katori-arjun"
        private val report: File by lazy {
            File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(), "katori-arjun-checks.txt")
        }
        @JvmStatic @AfterClass fun done() = Unit
    }
}
