package io.github.vedant7007.katori.orchestration

import io.github.vedant7007.katori.data.food.FoodDbSource
import io.github.vedant7007.katori.data.food.JdbcFoodDbSource
import io.github.vedant7007.katori.data.food.LookupMealResolver
import io.github.vedant7007.katori.data.food.SqliteFoodLookup
import io.github.vedant7007.katori.data.food.dbl
import io.github.vedant7007.katori.data.food.str
import io.github.vedant7007.katori.data.knowledge.Csv
import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts
import io.github.vedant7007.katori.domain.CandidateFood
import io.github.vedant7007.katori.domain.ConditionSource
import io.github.vedant7007.katori.domain.Constraint
import io.github.vedant7007.katori.domain.DeclaredCondition
import io.github.vedant7007.katori.domain.DefaultRulesEngine
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.LifeContext
import io.github.vedant7007.katori.domain.MealSnapshot
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.ProfileSnapshot
import io.github.vedant7007.katori.domain.ResolvedMeal
import io.github.vedant7007.katori.domain.RuleEvaluation
import io.github.vedant7007.katori.domain.RuleInput
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.llm.ExtractedItem
import io.github.vedant7007.katori.ml.llm.Intent
import io.github.vedant7007.katori.ml.llm.IntentRouter
import io.github.vedant7007.katori.ml.llm.SafetyLine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * The ten demo sentences, end to end, minus speech and minus the model.
 *
 * WHAT THIS ANSWERS. Not whether the corpus works: whether the sentences the presenter will
 * actually say on the 26th work, after the transcript. Each row of
 * `data-authoring/demo-utterance-set.csv` (both language columns) goes through the intent
 * router, the shipped resolver over the shipped database, and the rules engine with the demo
 * profile, and the report prints per sentence: the intent and who decided it, the foods
 * resolved with their grams and band, the exact figures the database returns with their
 * completeness, and what the rules engine did. Those are the numbers the run of show and the
 * deck should quote.
 *
 * WHAT IS AUTHORED HERE, AND LABELLED. Extraction is the model's step and does not run on the
 * JVM. [EXTRACTED] is what the extraction prompt is asked to produce from each transcript,
 * written by hand; it is the intended extraction, not the model's output, and the on-device
 * extraction cases (`0014`) are where the model is measured on the same shapes. The lab value
 * for beat 4 is a placeholder with the shape the run of show describes (a fasting glucose above
 * the range printed beside it); the number on the day is whatever the paper says.
 *
 * WHAT MUST HOLD, loudly: every sentence's intent is decided by the words, and correctly; every
 * food named resolves, to the food meant, never to a wrong one; no plate fails. A sentence that
 * breaks any of these is changed, not the system, at this distance from the day.
 */
class DemoSentencesTest {

    private lateinit var db: FoodDbSource
    private lateinit var lookup: SqliteFoodLookup
    private lateinit var resolver: LookupMealResolver

    @Before fun setUp() {
        db = JdbcFoodDbSource.openBundled()
        lookup = SqliteFoodLookup(db)
        resolver = LookupMealResolver(lookup)
    }

    @After fun tearDown() = db.close()

    private val projectDir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")

    private data class Row(val id: Int, val beat: Int, val intent: Intent, val lang: String, val spoken: String, val reference: String, val expectedFoods: List<String>)

    private fun rows(): List<Row> {
        val parsed = Csv.parse(File(projectDir, "data-authoring/demo-utterance-set.csv").readText())
        assertEquals(listOf("id", "beat", "intent", "language", "spoken", "reference", "expected_foods"), parsed.first())
        return parsed.drop(1).map { r ->
            Row(r[0].toInt(), r[1].toInt(), Intent.valueOf(r[2]), r[3], r[4], r[5], r[6].split(';').filter { it.isNotBlank() })
        }
    }

    /**
     * The intended extraction per sentence and language: the items, quantities and units as
     * the extraction prompt is asked to return them from the reference transcript. HAND-WRITTEN.
     * Sentence 4 is the self-correction: the intended result is three rotis, not two and three.
     */
    private val EXTRACTED: Map<Pair<Int, String>, List<ExtractedItem>> = mapOf(
        (1 to "en") to listOf(ExtractedItem("roti", 2.0, null, null), ExtractedItem("dal", null, null, null)),
        (1 to "hi") to listOf(ExtractedItem("रोटी", 2.0, null, null), ExtractedItem("दाल", null, null, null)),
        (2 to "en") to listOf(ExtractedItem("roti", 2.0, null, null), ExtractedItem("dal", 1.0, "katori", null), ExtractedItem("oil", 2.0, "spoon", null)),
        (2 to "hi") to listOf(ExtractedItem("रोटी", 2.0, null, null), ExtractedItem("दाल", 1.0, "कटोरी", null), ExtractedItem("तेल", 2.0, "चम्मच", null)),
        (3 to "en") to listOf(ExtractedItem("milk", 200.0, "ml", null), ExtractedItem("boiled egg", 1.0, null, "boiled")),
        (3 to "hi") to listOf(ExtractedItem("दूध", 200.0, "ml", null), ExtractedItem("उबला अंडा", 1.0, null, "boiled")),
        (4 to "en") to listOf(ExtractedItem("roti", 3.0, null, null), ExtractedItem("dal", null, null, null)),
        (4 to "hi") to listOf(ExtractedItem("रोटी", 3.0, null, null), ExtractedItem("दाल", null, null, null)),
        (5 to "en") to listOf(ExtractedItem("rice", 1.0, "plate", null), ExtractedItem("dal", null, null, null), ExtractedItem("curd", 1.0, "bowl", null)),
        (5 to "hi") to listOf(ExtractedItem("चावल", 1.0, "प्लेट", null), ExtractedItem("दाल", null, null, null), ExtractedItem("दही", 1.0, "कटोरी", null)),
        (8 to "en") to listOf(ExtractedItem("rice", null, null, null), ExtractedItem("dal", null, null, null)),
        (8 to "hi") to listOf(ExtractedItem("चावल", null, null, null), ExtractedItem("दाल", null, null, null)),
        (10 to "en") to listOf(ExtractedItem("idli", 3.0, null, null), ExtractedItem("sambar", null, null, null)),
        (10 to "hi") to listOf(ExtractedItem("इडली", 3.0, null, null), ExtractedItem("सांबर", null, null, null)),
    )

    /** What each expected food must resolve to. A different code is a WRONG FOOD. */
    private val MEANT = mapOf(
        "roti" to "chapati", "dal" to "toor_dal_tadka", "oil" to "groundnut_oil", "milk" to "milk_whole", "egg" to "egg",
        "rice" to "rice_cooked", "curd" to "curd", "idli" to "idli", "sambar" to "sambar",
    )

    // --- the demo person: the scripted feed's profile, the database's candidate list -------------

    private val profile = ProfileSnapshot(ageYears = 22, weightKg = 62.0, heightCm = 168.0, sex = null, goal = null, context = LifeContext.HOSTEL_STUDENT, dietType = null, avoidedFoodCodes = emptySet())
    private val now: Instant = Instant.parse("2026-09-26T07:30:00Z")
    private val rules = DefaultRulesEngine()
    private val triggerText = TriggerText(TriggerText.ENGLISH)

    /** Placeholder with the shape the run of show describes; the number on the day is the paper's. */
    private val glucoseAboveRange = LabValue("Fasting glucose", 118.0, "mg/dL", 70.0, 100.0, LocalDate.of(2026, 9, 24))

    /** Every bundled food and recipe, as RoomUserContextSource builds it for a profile with no diet type. */
    private fun candidates(): List<CandidateFood> {
        val everywhere = LifeContext.entries.toSet()
        fun nutrient(name: String) = runCatching { Nutrient.valueOf(name) }.getOrNull()
        val foodNutrients = db.query("SELECT food_key, nutrient, amount FROM food_nutrients WHERE state = 'MEASURED'")
            .groupBy({ it.str("food_key") }) { nutrient(it.str("nutrient"))?.let { n -> n to it.dbl("amount") } }
        // spices and cooking fats are not candidates since the first end-to-end run (Rao, f2297a6)
        val foods = db.query("SELECT food_key, display_name FROM foods WHERE food_class NOT IN ('SPICE', 'FAT_OIL')").map { r ->
            CandidateFood(r.str("food_key"), r.str("display_name"), everywhere, foodNutrients[r.str("food_key")].orEmpty().filterNotNull().toMap())
        }
        val recipeNutrients = db.query("SELECT recipe_key, nutrient, amount_per_100g FROM recipe_nutrients WHERE state = 'MEASURED'")
            .groupBy({ it.str("recipe_key") }) { nutrient(it.str("nutrient"))?.let { n -> n to it.dbl("amount_per_100g") } }
        val recipes = db.query("SELECT recipe_key, display_name FROM recipes").map { r ->
            CandidateFood(r.str("recipe_key"), r.str("display_name"), everywhere, recipeNutrients[r.str("recipe_key")].orEmpty().filterNotNull().toMap())
        }
        return foods + recipes
    }

    private fun parsed(items: List<ExtractedItem>, transcript: String) = ParsedMeal(
        items = items.map {
            ParsedItem(it.name, it.quantity, it.unit, null, ConfidenceRules.of(if (it.quantity == null) ConfidenceReason.QUANTITY_INFERRED else ConfidenceReason.QUANTITY_STATED))
        },
        confidence = ConfidenceRules.of(ConfidenceReason.QUANTITY_STATED),
        rawTranscript = transcript,
    )

    private fun figures(meal: ResolvedMeal): String = meal.figures.joinToString("; ") { f ->
        val t = f.total
        val amount = "%.1f".format(t.amount).removeSuffix(".0")
        val unit = t.unit.name.lowercase().replace("milligram", "mg").replace("microgram", "µg").replace("gram", "g").replace("kcal", "kcal")
        val floor = if (t.completeness == Completeness.PARTIAL) " (at least; no value for ${t.unknownContributors.joinToString()})" else ""
        "${t.nutrient.name.lowercase()} $amount $unit$floor"
    }

    private fun evaluation(meal: ResolvedMeal?, declared: List<DeclaredCondition>, labs: List<LabValue>): RuleEvaluation =
        rules.evaluate(RuleInput(profile, declared, labs, meal?.let { MealSnapshot(0L, it.items.map { i -> i.snapshot }, now) }, candidates(), now))

    private fun describe(e: RuleEvaluation): String {
        val fired = e.firedRules.joinToString { "${it.id.value}/${it.severity}" }.ifEmpty { "none" }
        val trigger = e.trigger?.let { triggerText.render(it) } ?: "no trigger"
        val prefers = e.constraints.filterIsInstance<Constraint.PreferNutrient>().joinToString { "${it.nutrient.name.lowercase()} ${it.direction.name.lowercase()}" }.ifEmpty { "no preference" }
        val top = e.rankedCandidates.take(3).joinToString { it.candidate.displayName }.ifEmpty { "no ranking" }
        return "fired: $fired | prefers: $prefers | \"$trigger\" | top: $top"
    }

    @Test fun `every demo sentence routes by the words, resolves to the food meant, and yields the database's figures`() = runBlocking {
        val all = rows()
        assertEquals("ten sentences in two languages", 20, all.size)
        val problems = mutableListOf<String>()
        val out = StringBuilder()
        out.appendLine("# The ten demo sentences, as the app computes them")
        out.appendLine()
        out.appendLine("Read off `DemoSentencesTest` against the shipped database and rules engine, minus speech and minus the model.")
        out.appendLine("Extraction is authored as the prompt is asked to produce it (labelled in the test); the lab value in beat 4 is a placeholder with the shape of the report.")
        out.appendLine("Profile: 22 years, 62 kg, hostel student, no diet type declared. Both language columns of `demo-utterance-set.csv`.")
        out.appendLine()
        out.appendLine("| # | beat | lang | reference transcript | intent | decided by | foods resolved (grams, band) | figures the database returns | rules engine |")
        out.appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")

        var loggedMealForBeat4: ResolvedMeal? = null
        for (r in all) {
            // 1. the router
            val decision = IntentRouter.decide(r.reference)
            val (intentCol, byCol) = when (decision) {
                is IntentRouter.Decision.Decided -> {
                    if (decision.intent != r.intent) problems += "#${r.id} ${r.lang}: routed to ${decision.intent}, the demo needs ${r.intent}"
                    decision.intent.name to "words: ${decision.evidence}"
                }
                is IntentRouter.Decision.AskModel -> {
                    problems += "#${r.id} ${r.lang}: the words could not decide (${decision.why}); the model would be consulted on a demo sentence"
                    "?" to "MODEL (${decision.why})"
                }
            }
            val clinical = SafetyLine.invitesClinicalJudgement(r.reference)

            // 2. the resolver, on the intended extraction
            var foodsCol = "—"; var figuresCol = "—"; var rulesCol = "—"
            val items = EXTRACTED[r.id to r.lang]
            if (items != null) {
                when (val res = resolver.resolve(parsed(items, r.reference), if (r.lang == "hi") "hi-IN" else "en-IN")) {
                    is Outcome.Ok -> {
                        val meal = res.value
                        foodsCol = meal.items.zip(meal.parsed.items).joinToString("; ") { (ri, pi) ->
                            "${pi.spokenName} → ${ri.snapshot.foodCode ?: "no data"} ${ri.snapshot.grams?.let { "%.0f g".format(it) } ?: "weight unknown"} ${ri.confidence.band.name.lowercase()}"
                        }
                        figuresCol = figures(meal)
                        // wrong food?
                        r.expectedFoods.forEachIndexed { i, expected ->
                            val code = meal.items.getOrNull(i)?.snapshot?.foodCode
                            if (code != MEANT[expected]) problems += "#${r.id} ${r.lang}: '$expected' resolved to $code, the demo means ${MEANT[expected]}"
                        }
                        // a counted egg is one egg, not a bowl of them: the first run weighed it as a 150 g katori
                        meal.items.zip(meal.parsed.items).filter { (_, pi) -> "egg" in pi.spokenName || "अंडा" in pi.spokenName }.forEach { (ri, pi) ->
                            val g = ri.snapshot.grams ?: 0.0
                            if (g !in 40.0..70.0) problems += "#${r.id} ${r.lang}: one '${pi.spokenName}' weighs $g g"
                        }
                        // 3. the rules engine
                        val eval = when (r.intent) {
                            Intent.LOG, Intent.SUGGEST -> evaluation(meal, emptyList(), emptyList())
                            else -> null
                        }
                        if (eval != null) rulesCol = describe(eval)
                        if (r.id == 2 && r.lang == "hi") loggedMealForBeat4 = meal
                    }
                    is Outcome.Unavailable -> { problems += "#${r.id} ${r.lang}: the plate failed: ${res.reason} ${res.detail}"; foodsCol = "FAILED ${res.reason}" }
                    is Outcome.NotImplemented -> problems += "#${r.id}: not implemented"
                }
            } else {
                // ANSWER and RECOMMEND: what the engine and retrieval hand the model
                val declared = if (r.id == 9) listOf(DeclaredCondition(if (r.lang == "hi") "iron kam hai" else "anaemia", ConditionSource.USER_DECLARED)) else emptyList()
                val eval = evaluation(null, declared, emptyList())
                rulesCol = describe(eval) + (if (clinical) " | REFERRAL follows (question)" else "")
                val terms = eval.constraints.filterIsInstance<Constraint.PreferNutrient>().flatMap { KnowledgeFacts.nutrientTerms(it.nutrient.name) } + declared.map { it.name }
                val facts = knowledge.select(terms, r.reference)
                figuresCol = "rows: " + facts.joinToString { it.id }.ifEmpty { "none" } + (if (r.id == 7) "; the protein figure is the logged lunch's (see #2 or #5)" else "")
                // #6 is a diary question and needs no fact; #7 and #9 answer partly from the file
                if (facts.isEmpty() && r.id in setOf(7, 9)) problems += "#${r.id} ${r.lang}: no knowledge row reaches the model"
            }
            out.appendLine("| ${r.id} | ${r.beat} | ${r.lang} | ${r.reference} | $intentCol | $byCol | $foodsCol | $figuresCol | $rulesCol |")
        }

        // Beat 4: advise again on the last meal, with the report scanned.
        loggedMealForBeat4?.let { meal ->
            val eval = evaluation(meal, emptyList(), listOf(glucoseAboveRange))
            out.appendLine("| 4b | 4 | — | *Advise again on my last meal*, after the report (placeholder: fasting glucose 118 mg/dL, printed range 70 to 100) | — | button | same plate as #2 | same figures as #2 | ${describe(eval)}${if (eval.referralRequired) " | REFERRAL follows (engine)" else ""} |")
        }
        out.appendLine()
        out.appendLine(if (problems.isEmpty()) "No sentence routes wrongly, resolves to a wrong food, or fails." else "## PROBLEMS\n" + problems.joinToString("\n") { "- $it" })
        println(out)
        File(projectDir, "logs/demo-sentences-as-the-app-computes-them.md").apply { parentFile.mkdirs() }.writeText(out.toString())
        assertTrue("demo sentences that do not work after the transcript:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private val knowledge: KnowledgeFacts by lazy { KnowledgeFacts.load { File(projectDir, "app/src/main/assets/" + KnowledgeFacts.ASSET_PATH).inputStream() } }
}
