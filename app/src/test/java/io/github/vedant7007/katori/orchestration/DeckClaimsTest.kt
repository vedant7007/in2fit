package io.github.vedant7007.katori.orchestration

import io.github.vedant7007.katori.data.food.CandidateCatalogue
import io.github.vedant7007.katori.data.food.FoodDbSource
import io.github.vedant7007.katori.data.food.JdbcFoodDbSource
import io.github.vedant7007.katori.data.food.LookupMealResolver
import io.github.vedant7007.katori.data.food.SqliteFoodLookup
import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts
import io.github.vedant7007.katori.domain.AdviceStore
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.DefaultRulesEngine
import io.github.vedant7007.katori.domain.LabStore
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.LifeContext
import io.github.vedant7007.katori.domain.LoggedMeal
import io.github.vedant7007.katori.domain.MealSnapshot
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.Period
import io.github.vedant7007.katori.domain.PeriodTotals
import io.github.vedant7007.katori.domain.ProfileSnapshot
import io.github.vedant7007.katori.domain.ResolvedMeal
import io.github.vedant7007.katori.domain.RuleInput
import io.github.vedant7007.katori.domain.SpeechLanguageRef
import io.github.vedant7007.katori.domain.StoredAdvice
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserContext
import io.github.vedant7007.katori.domain.UserContextSource
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientTotal
import io.github.vedant7007.katori.domain.model.NutritionFigure
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.vision.LabReportExtractor
import io.github.vedant7007.katori.ml.vision.RecognisedText
import io.github.vedant7007.katori.ml.vision.TextBlock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * THE SLIDE FOLLOWS THE CODE. One test per numeric claim the deck makes about app behaviour,
 * each asserting against the real code path on the shipped database, each writing what the
 * code produces into `logs/deck-plate.md` beside the claim, with MATCHES / DIFFERS / NOT BUILT.
 * The deck changes to fit this file; this file never changes to fit the deck. 402 kcal reached a
 * slide because nothing guarded it.
 */
class DeckClaimsTest {

    companion object {
        private lateinit var db: FoodDbSource
        private lateinit var resolver: LookupMealResolver
        private lateinit var knowledge: KnowledgeFacts
        private val report = StringBuilder()
        private val sections = java.util.TreeMap<Int, String>()
        @JvmStatic @BeforeClass fun open() {
            db = JdbcFoodDbSource.openBundled(); resolver = LookupMealResolver(SqliteFoodLookup(db))
            val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
            knowledge = KnowledgeFacts.load { File(dir, "app/src/main/assets/knowledge/facts.csv").inputStream() }
            report.appendLine("# The deck's claims, as the app computes them").appendLine()
                .appendLine("Written by `DeckClaimsTest` on every run: the shipped database, `LookupMealResolver`, `DefaultRulesEngine`, `DefaultOrchestrator`'s ANSWER path, `LabReportExtractor`. The slide follows this file.").appendLine()
        }
        @JvmStatic @AfterClass fun close() {
            db.close()
            sections.values.forEach { report.append(it) }
            val dir = System.getProperty("katori.projectDir")!!
            File(dir, "logs").mkdirs(); File(dir, "logs/deck-plate.md").writeText(report.toString())
            println(report)
        }
    }

    private val context = ContextText(ContextText.ENGLISH, TriggerText.ENGLISH, ZoneId.of("Asia/Kolkata"))
    private val trigger = TriggerText(TriggerText.ENGLISH)
    private val profile = ProfileSnapshot(22, 62.0, 168.0, null, null, LifeContext.HOSTEL_STUDENT, null, emptySet())
    private val now: Instant = Instant.parse("2026-09-26T07:30:00Z")
    private val glucose142 = LabValue("Fasting glucose", 142.0, "mg/dL", 70.0, 100.0, LocalDate.of(2026, 9, 24))

    private fun item(name: String, quantity: Double?, unit: String?) = ParsedItem(
        name, quantity, unit, null,
        ConfidenceRules.of(if (quantity == null) ConfidenceReason.QUANTITY_INFERRED else ConfidenceReason.QUANTITY_STATED),
    )

    private fun plate(transcript: String, vararg items: ParsedItem): ResolvedMeal =
        (runBlocking { resolver.resolve(ParsedMeal(items.toList(), ConfidenceRules.combine(items.map { it.confidence }), transcript), "en-IN") } as Outcome.Ok).value

    /** The plate as the screen shows it: face, "taken as" only when inferred (0035), then the figures with their band. */
    private fun render(meal: ResolvedMeal, nutrients: Set<Nutrient>): List<String> = buildList {
        for ((ri, pi) in meal.items.zip(meal.parsed.items)) {
            val q = pi.quantity?.let { "%.0f".format(it) } ?: ""
            val taken = ri.snapshot.grams?.let { g -> if (ConfidenceReason.QUANTITY_INFERRED in ri.confidence.reasons) " · taken as ${"%.0f".format(g)} g" else "" }.orEmpty()
            add("${pi.spokenName}: $q ${pi.unit.orEmpty()}".trim() + taken + "    (${ri.snapshot.displayName}, ${"%.0f".format(ri.snapshot.grams ?: 0.0)} g, ${ri.confidence.band.name.lowercase()})")
        }
        for (f in meal.figures.filter { it.total.nutrient in nutrients }) add("${context.figure(f)} (${f.confidence.band.name.lowercase().replaceFirstChar { it.uppercase() }})")
    }

    private fun section(n: Int, title: String, claim: String, code: List<String>, verdict: String) {
        val s = StringBuilder().appendLine("## $n. $title").appendLine()
            .appendLine("The deck says: $claim").appendLine().appendLine("The code produces:").appendLine()
        code.forEach { s.appendLine("    $it") }
        s.appendLine().appendLine("**$verdict**").appendLine()
        sections[n] = s.toString()
    }

    // --- 1. the plate --------------------------------------------------------------------------

    private val lunch by lazy { plate("rendu roti and one katori dal", item("roti", 2.0, null), item("dal", 1.0, "katori")) }

    @Test fun `1 the plate for rendu roti and one katori dal`() {
        val lines = render(lunch, setOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT))
        val energy = lunch.figures.single { it.total.nutrient == Nutrient.ENERGY }.total.amount
        val protein = lunch.figures.single { it.total.nutrient == Nutrient.PROTEIN }.total.amount
        section(1, "The plate for \"rendu roti and one katori dal\"",
            "402 kcal · \"Two rotis at your 40 g, one katori dal\" · 18 g protein (corrected by Vedant on the night to 467 kcal, 21 g protein, \"Two rotis, one katori dal\")",
            lines,
            "MATCHES the corrected deck: ${"%.0f".format(energy)} kcal, ${"%.0f".format(protein)} g protein, no \"taken as\" (both amounts were stated), every line Approximate. " +
                "The original 402 / 18 assumed a 40 g roti (calibration is descoped; the recipe's roti is 45 g) and a 150 g katori (the dal recipe's katori is its 180 g serving).")
        assertEquals(467.0, energy, 1.0); assertEquals(21.0, protein, 0.5)
        assertTrue(lines.none { "taken as" in it })
    }

    // --- 2. "How much protein today?" ----------------------------------------------------------

    /** A day's diary from the demo's own plates, through the real resolver; the totals summed as the store's query sums them. */
    private fun fixtureDay(): Pair<List<LoggedMeal>, PeriodTotals> {
        val meals = listOf(
            "For breakfast I had three idlis and sambar." to plate("three idlis and sambar", item("idli", 3.0, null), item("sambar", null, null)),
            "I drank 200 ml of milk and ate one boiled egg." to plate("milk and egg", item("milk", 200.0, "ml"), item("boiled egg", 1.0, null)),
            "Two rotis, a katori of dal, and I used two spoons of oil." to plate("lunch", item("roti", 2.0, null), item("dal", 1.0, "katori"), item("oil", 2.0, "spoon")),
        )
        val logged = meals.mapIndexed { i, (_, m) ->
            LoggedMeal(MealSnapshot(i + 1L, m.items.map { it.snapshot }, now.minusSeconds((3 - i) * 3600L * 3)), m.figures)
        }
        val totals = Nutrient.entries.mapNotNull { n ->
            val parts = logged.flatMap { it.figures }.filter { it.total.nutrient == n }
            if (parts.isEmpty()) null else NutritionFigure(
                NutrientTotal(n, parts.sumOf { it.total.amount }, parts.first().total.unit,
                    if (parts.all { it.total.completeness == Completeness.COMPLETE }) Completeness.COMPLETE else Completeness.PARTIAL,
                    parts.flatMap { it.total.unknownContributors }.distinct()),
                ConfidenceRules.combine(parts.map { it.confidence }), parts.flatMap { it.sources }.distinct(),
            )
        }
        return logged to PeriodTotals(Period.TODAY, totals)
    }

    @Test fun `2 how much protein today, against a fixture diary`() {
        val (logged, today) = fixtureDay()
        val ctx = UserContext(profile, emptyList(), emptyList(), logged.reversed(), listOf(today), CandidateCatalogue.load(db))
        val llm = MinimalRig.FakeLlm()
        val orchestrator = DefaultOrchestrator(
            asr = MinimalRig.NoAsr(), llm = llm, tts = MinimalRig.SilentTts(), rules = DefaultRulesEngine(), resolver = resolver, store = MinimalRig.RecordingStore(),
            advice = object : AdviceStore { override suspend fun latest(mealId: Long) = null; override suspend fun save(mealId: Long, advice: StoredAdvice) = Unit },
            labs = object : LabStore { override suspend fun save(values: List<LabValue>) = Outcome.Ok(values.size) },
            contextSource = object : UserContextSource { override suspend fun current() = ctx },
            knowledge = knowledge, triggerText = trigger, contextText = context,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val events = runBlocking { orchestrator.handle(UserIntent.Type("How much protein today?", SpeechLanguageRef("en-IN"))).toList() }
        val own = events.filterIsInstance<OrchestratorEvent.OwnFigures>().single().lines
        val request = llm.answers.single()
        val proteinToday = today.figures.single { it.total.nutrient == Nutrient.PROTEIN }.total.amount
        val code = buildList {
            add("fixture diary (the demo's own three plates through the real resolver): " + logged.joinToString("; ") { m -> m.snapshot.items.joinToString(", ") { it.displayName } })
            add("on screen before the model (OwnFigures): " + own.first { it.startsWith("Today so far") })
            add("given to the model as figures: " + request.figures.joinToString(" | ") { it.text })
            add("knowledge rows given: " + request.facts.joinToString { it.id })
            add("the model's sentence: unmeasured here (a scripted engine); on the phone it may quote only the figures above and the rows, and the numeric guard refuses any other number")
            add("a protein TARGET: no field in the profile, no rule in the engine, no template; a target computed from the RDA row (0.83 g per kg × 62 kg) would be a number the model was not given, and the numeric guard refuses it")
        }
        section(2, "\"How much protein today?\"",
            "\"58 g so far, about 40 g short of your target\"",
            code,
            "DIFFERS, in two parts. The figure: the code produces \"Today so far: protein: ${"%.1f".format(proteinToday)} g\" for this diary, and the number is the diary's, not a fixed 58; the shape \"N g so far\" is real. " +
                "The target: NOT BUILT. There is no protein target anywhere in the app, and no mechanism that could say \"40 g short\"; the sentence must come off the slide or become what a RECOMMEND actually says.")
        assertTrue(own.any { it.startsWith("Today so far") && "protein" in it })
        assertTrue(request.figures.any { "protein" in it.text })
        assertTrue("no target exists to be quoted", request.figures.none { "target" in it.text.lowercase() } && request.facts.none { "target" in it.fact.lowercase() })
        assertEquals(15.3 + 12.6 + 20.9, proteinToday, 0.3)
    }

    // --- 3. the lab report ---------------------------------------------------------------------

    private fun line(text: String, top: Int) = TextBlock(text, 20, top, 20 + 12 * text.length, top + 40)

    @Test fun `3 the lab report figures, through the parse the OCR path uses`() {
        // What ML Kit hands the extractor: rows as the corpus records them, including the one
        // shape the hardware run actually returned (the unit glued to the number).
        val text = RecognisedText(listOf(
            line("Reported on: 24/09/2026", 20),
            line("Glucose (Fasting) 142mg/dL 70 - 100 H", 80),
            line("HbA1c 6.4 % 4.0 - 5.6", 140),
        ))
        val parsed = LabReportExtractor.extract(text)
        val glucose = parsed.fields.firstOrNull { it.testName.contains("Glucose") }
        val hba1c = parsed.fields.firstOrNull { it.testName.contains("HbA1c") }
        val code = buildList {
            add("parse of the recognised rows (LabReportExtractor.extract): " + parsed.fields.joinToString(" | ") { "${it.testName} = ${it.value} ${it.unit ?: "(no unit)"}, printed range ${it.referenceLow} to ${it.referenceHigh}" })
            add("report date read: ${parsed.reportDate}")
            add("the image step (ML Kit reading a printed sheet) is device-only: the one hardware observation on record is a single row, \"Haemoglobin 9.8g/dL\" (0004, 0018); no printed report carrying 142 mg/dL and 6.4 % has been photographed")
        }
        section(3, "The lab report: 142 mg/dL fasting glucose and 6.4 % HbA1c",
            "\"142 mg/dL fasting glucose, 6.4 per cent HbA1c, both above the range the sheet itself prints\"",
            code,
            "MATCHES for the parse: both values and both printed ranges are read, and both are above their printed range, so both fire. " +
                "The photograph step is device-only and measured once on one row, so on stage the values are whatever the printed report used on the day carries; the deck's two are illustrative until that sheet is photographed. NOT MEASURED, not NOT BUILT.")
        assertNotNull(glucose); assertNotNull(hba1c)
        assertEquals(142.0, glucose!!.value, 0.0); assertEquals(100.0, glucose.referenceHigh!!, 0.0)
        assertEquals(6.4, hba1c!!.value, 0.0); assertEquals(5.6, hba1c.referenceHigh!!, 0.0)
        assertEquals(LocalDate.of(2026, 9, 24), parsed.reportDate)
    }

    // --- 4. the after-advice ------------------------------------------------------------------

    @Test fun `4 a stored out-of-range reading changes the suggestion for the same meal`() {
        val rules = DefaultRulesEngine()
        val candidates = CandidateCatalogue.load(db)
        val meal = MealSnapshot(1L, lunch.items.map { it.snapshot }, now)
        val before = rules.evaluate(RuleInput(profile, emptyList(), emptyList(), meal, candidates, now))
        val after = rules.evaluate(RuleInput(profile, emptyList(), listOf(glucose142), meal, candidates, now))
        val code = buildList {
            add("same meal: " + meal.items.joinToString(", ") { "${it.displayName} ${"%.0f".format(it.grams ?: 0.0)} g" })
            add("before the report: trigger = ${before.trigger?.let { trigger.render(it) } ?: "none"}; suggestions = ${before.rankedCandidates.size}")
            add("after the report (fasting glucose 142 mg/dL, printed range 70 to 100): trigger = \"${after.trigger?.let { trigger.render(it) }}\"")
            add("after, the suggestions (ranked, hostel list): " + after.rankedCandidates.take(5).joinToString { it.candidate.displayName })
            add("after, the preferences: " + after.constraints.filterIsInstance<io.github.vedant7007.katori.domain.Constraint.PreferNutrient>().joinToString { "${it.nutrient.name.lowercase()} ${it.direction.name.lowercase()}" })
            add("the wording on the slide is the model's and is not asserted; the trigger sentence above is the template the screen shows verbatim")
        }
        section(4, "The after-advice: the same meal, different advice once a report is on file",
            "\"Swap one roti for more dal. Your last report shows fasting glucose above the printed range …\" (asserted here only as: a stored out-of-range reading changes the suggestion for the same meal)",
            code,
            "MATCHES the property: before the report there is no trigger and no suggestion; after it the engine's own sentence fires and ${after.rankedCandidates.size} suggestions are ranked by \"fibre higher, carbohydrate lower\". " +
                "The deck's prose is the model's to say from that sentence and the ranked names; \"so fewer fast carbs at one sitting helps\" is a claim the guards refuse, per the audit.")
        assertNull(before.trigger); assertTrue(before.rankedCandidates.isEmpty())
        assertNotNull(after.trigger); assertTrue(after.rankedCandidates.isNotEmpty())
        assertTrue(after.rankedCandidates.map { it.candidate.foodCode } != before.rankedCandidates.map { it.candidate.foodCode })
        assertTrue(trigger.render(after.trigger!!).contains("142"))
    }
}
