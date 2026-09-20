package io.github.vedant7007.katori.ui.demo

import io.github.vedant7007.katori.domain.CandidateFood
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.DeclaredCondition
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.LifeContext
import io.github.vedant7007.katori.domain.MealItemSnapshot
import io.github.vedant7007.katori.domain.MealSnapshot
import io.github.vedant7007.katori.domain.Orchestrator
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.OrchestratorEvent.Progress
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.Period
import io.github.vedant7007.katori.domain.PeriodTotals
import io.github.vedant7007.katori.domain.ProfileSnapshot
import io.github.vedant7007.katori.domain.RuleInput
import io.github.vedant7007.katori.domain.RulesEngine
import io.github.vedant7007.katori.domain.SpokenIntent
import io.github.vedant7007.katori.domain.Stage
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientTotal
import io.github.vedant7007.katori.domain.model.NutrientUnit
import io.github.vedant7007.katori.domain.model.NutritionFigure
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate

/**
 * The scripted demo feed (docs/decisions/0027): an [Orchestrator] that emits the events of the
 * four beats on a timer, so the screens the judges will see can be built and screenshotted
 * without a phone, a model or a microphone.
 *
 * WHAT IS REAL AND WHAT IS SCRIPT. The rules engine is the real [RulesEngine]; the trigger
 * sentence is what the real engine says about the scripted plate and the scripted report, and
 * beat 4's changed sentence is the engine's own, with a different input digest. The figure
 * lines are rendered by the real [ContextText]. The transcript is one of the presenter's own
 * sentences from `data-authoring/demo-utterance-set.csv`. SCRIPT: the extraction, the plate's
 * per-item figures, the model's phrasing, and the lab report's lines. Every screen carries the
 * `demo_banner` while this feed is on, and the feed is on only by a long-press.
 *
 * It exists only to build screens. It is never provided by Hilt and never on by default.
 */
class ScriptedOrchestrator(
    private val rules: RulesEngine,
    private val contextText: ContextText,
    private val triggerText: TriggerText,
) : Orchestrator {

    override fun handle(intent: UserIntent): Flow<OrchestratorEvent> = flow {
        when (intent) {
            is UserIntent.Speak -> {
                emit(Progress(Stage.RECORDING))
                repeat(12) { i -> emit(OrchestratorEvent.AudioLevel(0.15f + 0.6f * ((i % 4) / 3f))); delay(120) }
                emit(Progress(Stage.TRANSCRIBING))
                delay(500)
                val line = SENTENCES[DemoFeed.cursor % SENTENCES.size]
                DemoFeed.cursor++
                turn(line.first, line.second)
            }
            is UserIntent.Type -> turn(intent.text, classify(intent.text))
            is UserIntent.Resolve -> turn(intent.text, intent.intent)
            is UserIntent.AdviseOnMeal -> adviseAgain()
            UserIntent.ScanLabReport -> { emit(Progress(Stage.SAVING)); delay(400); emit(OrchestratorEvent.Completed) }
            is UserIntent.CorrectValue, is UserIntent.ScanPackagedLabel, is UserIntent.CheckExerciseForm ->
                { emit(OrchestratorEvent.NotImplemented("demo." + intent::class.simpleName)); emit(OrchestratorEvent.Completed) }
        }
    }

    private suspend fun FlowCollector<OrchestratorEvent>.turn(text: String, intent: SpokenIntent?) {
        emit(OrchestratorEvent.Transcribed(text))
        emit(Progress(Stage.CLASSIFYING))
        delay(700)
        val decided = intent ?: run {
            emit(OrchestratorEvent.NeedsIntent(text))
            emit(OrchestratorEvent.Completed)
            return
        }
        when (decided) {
            SpokenIntent.LOG -> plate(text, save = true)
            SpokenIntent.SUGGEST -> plate(text, save = false)
            SpokenIntent.ANSWER -> answer()
            SpokenIntent.RECOMMEND -> recommend()
        }
    }

    private suspend fun FlowCollector<OrchestratorEvent>.plate(text: String, save: Boolean) {
        emit(Progress(Stage.EXTRACTING)); delay(1400)
        val items = if (text.contains("rice", true)) RICE_AND_DAL else ROTI_AND_DAL
        emit(Progress(Stage.MATCHING_FOODS)); delay(500)
        emit(Progress(Stage.COMPUTING)); delay(300)
        val parsed = ParsedMeal(
            items = items.map { ParsedItem(it.spoken, it.quantity, it.unit, it.foodCode, ConfidenceRules.of(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_STATED)) },
            confidence = ConfidenceRules.of(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_STATED),
            rawTranscript = text,
        )
        val figures = totals(items)
        emit(OrchestratorEvent.MealResolved(parsed, figures, hypothetical = !save))
        val now = Instant.now()
        val snapshot = MealSnapshot(mealId = if (save) 1L else 0L, items = items.map { it.snapshot }, loggedAt = now)
        if (save) {
            emit(Progress(Stage.SAVING)); delay(300)
            DemoFeed.lastMeal = snapshot
            emit(OrchestratorEvent.MealLogged(1L))
        }
        emit(Progress(Stage.EVALUATING_RULES)); delay(400)
        val evaluation = rules.evaluate(input(snapshot, now))
        emit(Progress(Stage.PHRASING)); delay(1800)
        val referral = evaluation.trigger?.let(triggerText::render).takeIf { evaluation.referralRequired }
        emit(OrchestratorEvent.Advice(evaluation, PHRASED_PLATE, referral))
        emit(Progress(Stage.SPEAKING)); delay(1500)
        emit(OrchestratorEvent.Completed)
    }

    private suspend fun FlowCollector<OrchestratorEvent>.adviseAgain() {
        val meal = DemoFeed.lastMeal ?: return run {
            emit(OrchestratorEvent.Failed(UnavailableReason.NO_MATCH, "no meal logged in this scripted session"))
        }
        emit(Progress(Stage.EVALUATING_RULES)); delay(500)
        val evaluation = rules.evaluate(input(meal, Instant.now()))
        emit(Progress(Stage.PHRASING)); delay(1500)
        val referral = evaluation.trigger?.let(triggerText::render).takeIf { evaluation.referralRequired }
        emit(OrchestratorEvent.Advice(evaluation, if (DemoFeed.labValues.isEmpty()) PHRASED_PLATE else PHRASED_AFTER_REPORT, referral))
        emit(Progress(Stage.SPEAKING)); delay(1500)
        emit(OrchestratorEvent.Completed)
    }

    private suspend fun FlowCollector<OrchestratorEvent>.answer() {
        emit(Progress(Stage.EVALUATING_RULES)); delay(300)
        val lines = ownLines()
        emit(Progress(Stage.RETRIEVING_FACTS)); delay(400)
        emit(Progress(Stage.PHRASING)); delay(2500)
        // The script obeys the numeric guard's rule: the only number in its sentence is one the
        // person's own lines already carry, formatted the same way.
        val protein = ownItems().sumOf { it.nutrients[Nutrient.PROTEIN] ?: 0.0 }
        emit(OrchestratorEvent.Answered(PHRASED_ANSWER.format(java.util.Locale.ROOT, protein), emptyList(), referral = null, figures = lines))
        emit(Progress(Stage.SPEAKING)); delay(1500)
        emit(OrchestratorEvent.Completed)
    }

    private suspend fun FlowCollector<OrchestratorEvent>.recommend() {
        emit(Progress(Stage.EVALUATING_RULES)); delay(400)
        val evaluation = rules.evaluate(input(meal = null, Instant.now()))
        emit(Progress(Stage.RETRIEVING_FACTS)); delay(400)
        emit(Progress(Stage.PHRASING)); delay(2200)
        val referral = evaluation.trigger?.let(triggerText::render).takeIf { evaluation.referralRequired }
        emit(OrchestratorEvent.Advice(evaluation, PHRASED_RECOMMEND, referral, factIds = listOf("script")))
        emit(Progress(Stage.SPEAKING)); delay(1500)
        emit(OrchestratorEvent.Completed)
    }

    // --- the scripted person and plate ------------------------------------------------------

    private fun input(meal: MealSnapshot?, now: Instant) = RuleInput(
        profile = PROFILE,
        declaredConditions = emptyList<DeclaredCondition>(),
        labValues = DemoFeed.labValues.toList(),
        meal = meal,
        candidates = CANDIDATES,
        evaluatedAt = now,
    )

    private fun ownItems(): List<Item> =
        DemoFeed.lastMeal?.items?.map { s -> ITEMS.first { it.foodCode == s.foodCode } } ?: ROTI_AND_DAL

    private fun ownLines(): List<String> =
        listOf(contextText.period(PeriodTotals(Period.TODAY, totals(ownItems())))) +
            DemoFeed.labValues.map { contextText.lab(it) }

    private fun classify(text: String): SpokenIntent? {
        val t = text.lowercase()
        return when {
            listOf("should i eat", "what should i eat", "for iron", "recommend").any { it in t } -> SpokenIntent.RECOMMEND
            listOf("having", "about to", "what should i add", "add?").any { it in t } -> SpokenIntent.SUGGEST
            t.contains("?") || listOf("how much", "what did", "did i").any { it in t } -> SpokenIntent.ANSWER
            listOf("had ", "ate ", "drank ", "khaya", "khai", "used ").any { it in t } -> SpokenIntent.LOG
            else -> null
        }
    }

    private class Item(val spoken: String, val quantity: Double, val unit: String, val foodCode: String, val displayName: String, val grams: Double, val per100: Map<Nutrient, Double>) {
        val nutrients get() = per100.mapValues { it.value * grams / 100.0 }
        val snapshot get() = MealItemSnapshot(displayName, foodCode, grams, nutrients)
    }

    private fun totals(items: List<Item>): List<NutritionFigure> = Nutrient.entries.map { n ->
        NutritionFigure(
            total = NutrientTotal(n, items.sumOf { it.nutrients[n] ?: 0.0 }, n.unit, Completeness.COMPLETE, emptyList()),
            confidence = ConfidenceRules.of(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_STATED, ConfidenceReason.AUTHORED_REFERENCE_RECIPE),
            sources = listOf(DataSource.USDA_SR_LEGACY),
        )
    }

    companion object {
        /** The presenter's sentences, `data-authoring/demo-utterance-set.csv` ids 2, 7, 8, 9. */
        val SENTENCES = listOf(
            "Two rotis, a katori of dal, and I used two spoons of oil." to SpokenIntent.LOG,
            "How much protein was in my lunch?" to SpokenIntent.ANSWER,
            "I'm having rice and dal, what should I add?" to SpokenIntent.SUGGEST,
            "I have anaemia, what should I eat for iron?" to SpokenIntent.RECOMMEND,
        )

        // SCRIPT. Plausible per-100 g figures for the screen, not database rows.
        private fun g(energy: Double, protein: Double, carb: Double, fat: Double, fibre: Double, iron: Double, b12: Double, sodium: Double) = mapOf(
            Nutrient.ENERGY to energy, Nutrient.PROTEIN to protein, Nutrient.CARBOHYDRATE to carb, Nutrient.FAT to fat,
            Nutrient.FIBRE to fibre, Nutrient.IRON to iron, Nutrient.VITAMIN_B12 to b12, Nutrient.SODIUM to sodium,
        )
        private val ITEMS = listOf(
            Item("roti", 2.0, "piece", "chapati", "Chapati / roti", 80.0, g(258.0, 8.6, 49.0, 3.6, 5.0, 1.6, 0.0, 300.0)),
            Item("dal", 1.0, "katori", "toor_dal_tadka", "Dal tadka", 150.0, g(110.0, 6.0, 15.0, 3.0, 3.5, 1.4, 0.0, 250.0)),
            Item("oil", 2.0, "spoon", "sunflower_oil", "Sunflower oil", 10.0, g(884.0, 0.0, 0.0, 100.0, 0.0, 0.0, 0.0, 0.0)),
            Item("rice", 1.0, "plate", "rice_cooked", "Cooked rice", 200.0, g(130.0, 2.7, 28.0, 0.3, 0.4, 0.2, 0.0, 1.0)),
        )
        private val ROTI_AND_DAL = ITEMS.take(3)
        private val RICE_AND_DAL = listOf(ITEMS[3], ITEMS[1])

        private val PROFILE = ProfileSnapshot(
            ageYears = 22, weightKg = 62.0, heightCm = 168.0, sex = null, goal = null,
            context = LifeContext.HOSTEL_STUDENT, dietType = null, avoidedFoodCodes = emptySet(),
        )

        /** The constrained list for a hostel student, with the same per-100 g figures the screen shows. */
        private val CANDIDATES = listOf(
            CandidateFood("sprouts_salad", "Sprouts salad", setOf(LifeContext.HOSTEL_STUDENT), g(60.0, 5.0, 8.0, 1.0, 3.0, 1.5, 0.0, 10.0)),
            CandidateFood("palakura_pappu", "Palakura pappu", setOf(LifeContext.HOSTEL_STUDENT), g(95.0, 5.5, 12.0, 3.0, 3.0, 2.4, 0.0, 200.0)),
            CandidateFood("egg", "Boiled egg", setOf(LifeContext.HOSTEL_STUDENT), g(155.0, 13.0, 1.1, 11.0, 0.0, 1.2, 1.1, 124.0)),
            CandidateFood("curd", "Curd", setOf(LifeContext.HOSTEL_STUDENT), g(60.0, 3.5, 4.7, 3.3, 0.0, 0.1, 0.4, 46.0)),
            CandidateFood("peanuts_roasted", "Roasted peanuts", setOf(LifeContext.HOSTEL_STUDENT), g(585.0, 24.0, 21.0, 50.0, 8.0, 2.3, 0.0, 6.0)),
        )

        // SCRIPT. What the model's phrasing might look like; never shown without the banner.
        private const val PHRASED_PLATE = "That is a good, filling plate. The dal carries the protein; a little curd or sprouts alongside would round it out."
        private const val PHRASED_AFTER_REPORT = "Your last report shows haemoglobin below the range printed on it, so iron-rich additions matter more now: palakura pappu or a handful of roasted peanuts with this plate."
        private const val PHRASED_ANSWER = "Your lunch had %.1f g of protein, mostly from the dal."
        private const val PHRASED_RECOMMEND = "For iron, palakura pappu and sprouts are the strongest choices on your list, and curd with a meal helps you absorb it."

        /** Beat 3's report, name / value / printed range, as the real extractor reads it from scripted lines. */
        val REPORT_ROWS = listOf(
            Triple("Haemoglobin", "9.8 g/dL", "13.0 - 17.0"),
            Triple("PCV", "30.2 %", "40 - 50"),
            Triple("Serum Iron", "45 ug/dL", "60 - 170"),
            Triple("Ferritin", "8.2 ng/mL", "15 - 150"),
            Triple("Vitamin B12", "250 pg/mL", "200 - 900"),
        )
        val REPORT_DATE: LocalDate = LocalDate.of(2026, 9, 12)
    }
}
