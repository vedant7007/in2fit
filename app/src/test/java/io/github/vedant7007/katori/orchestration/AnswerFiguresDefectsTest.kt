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
import io.github.vedant7007.katori.domain.SpeechLanguageRef
import io.github.vedant7007.katori.domain.StoredAdvice
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserContext
import io.github.vedant7007.katori.domain.UserContextSource
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.llm.AnswerLength
import io.github.vedant7007.katori.ml.llm.AnswerRequest
import io.github.vedant7007.katori.ml.llm.ExtractedItem
import io.github.vedant7007.katori.ml.llm.ExtractionRequest
import io.github.vedant7007.katori.ml.llm.ExtractionResult
import io.github.vedant7007.katori.ml.llm.Intent
import io.github.vedant7007.katori.ml.llm.LlamaCppLlmEngine
import io.github.vedant7007.katori.ml.llm.LlamaRuntime
import io.github.vedant7007.katori.ml.llm.LlmEngine
import io.github.vedant7007.katori.ml.llm.PhrasedText
import io.github.vedant7007.katori.ml.llm.PhrasingRequest
import io.github.vedant7007.katori.ml.llm.RecommendRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * TARGET FOR THE INTEGRATOR, RED BY DESIGN until the ANSWER path gives the model the figures a
 * how-much question asks for.
 *
 * FOUND ON THE PHONE (21 Sep, typed, verbatim): "I said that I ate two chapatiis, can you tell
 * me the nutritional information of it". Routed ANSWER by the words, correctly; not clinical;
 * refused: "I can't put a number or a judgement on that." Reproduced here through the real
 * orchestrator with the real guards and a scripted model, and separated into its layers:
 *
 *   - with the diary EMPTY (the LOG had missed "chapatiis": the matcher, fixed beside this),
 *     the request carries `figures=[]`; a numeric answer is refused by the numeric guard, a
 *     general claim by ClaimGuard, and only a figure-free sentence passes. The guards did
 *     their job on an empty request.
 *   - with the two chapatis LOGGED, the request carries the meal line WITHOUT ITS FIGURES
 *     ("Monday 21 Sept, 10:30 am: Chapati / roti.") because `nutrientsNamed` knows only the
 *     rules engine's words for nutrients ("energy", "carbohydrate") and a question naming none
 *     is treated as a question about WHAT was eaten. "Nutritional information", "calories",
 *     "carbs" and "what did that give me" name nothing it knows, so the model is asked for
 *     figures it was not given, writes them from memory, and is refused. Same screen, full diary.
 *
 * THE ONE CHANGE, in the ANSWER path: `asked` comes from `NutrientWords.named(text)` (calories,
 * carbs, salt, and the knowledge file's terms), and when `NutrientWords.asksForAll(text)` the
 * meal lines carry every figure (`only = null`) and the period lines all of theirs. A question
 * about WHAT was eaten still gets the items and the time alone.
 */
class AnswerFiguresDefectsTest {

    companion object {
        private lateinit var db: FoodDbSource
        @JvmStatic @BeforeClass fun open() { db = JdbcFoodDbSource.openBundled() }
        @JvmStatic @AfterClass fun close() = db.close()
    }

    private class ScriptedRuntime(private val response: String) : LlamaRuntime {
        override fun generate(prompt: String, maxTokens: Int, stop: List<String>) = response
        override fun close() = Unit
    }

    /** The real guards, a scripted sentence, and the request the orchestrator built. */
    private class Scripted(modelSays: String) : LlmEngine, LlmLease {
        private val real = LlamaCppLlmEngine(ScriptedRuntime(modelSays))
        var request: AnswerRequest? = null
        override suspend fun classify(transcript: String, languageTag: String) = Outcome.Ok(Intent.ANSWER)
        override suspend fun extract(request: ExtractionRequest): Outcome<ExtractionResult> = Outcome.Ok(ExtractionResult(emptyList(), "{}"))
        override suspend fun phrase(request: PhrasingRequest): Outcome<PhrasedText> = Outcome.NotImplemented("test")
        override suspend fun answer(request: AnswerRequest, length: AnswerLength) = real.answer(request, length).also { this.request = request }
        override suspend fun recommend(request: RecommendRequest, length: AnswerLength) = real.recommend(request, length)
        override suspend fun <T> use(block: suspend (LlmEngine) -> T): Outcome<T> = Outcome.Ok(block(this))
    }

    private val lookup by lazy { SqliteFoodLookup(db) }
    private val resolver by lazy { LookupMealResolver(lookup) }
    private val profile = ProfileSnapshot(22, 62.0, 168.0, null, null, LifeContext.HOSTEL_STUDENT, null, emptySet())

    /** Two chapatis, logged this morning, through the real resolver: the diary the person meant. */
    private fun diaryWithTwoChapatis(): UserContext {
        val item = ParsedItem("chapati", 2.0, null, null, ConfidenceRules.of(ConfidenceReason.QUANTITY_STATED))
        val meal = (runBlocking { resolver.resolve(ParsedMeal(listOf(item), item.confidence, "two chapatis"), "en-IN") } as Outcome.Ok).value
        val logged = LoggedMeal(MealSnapshot(1L, meal.items.map { it.snapshot }, Instant.parse("2026-09-21T05:00:00Z")), meal.figures)
        return UserContext(profile, emptyList(), emptyList(), listOf(logged), listOf(PeriodTotals(Period.TODAY, meal.figures)), CandidateCatalogue.load(db))
    }

    private fun ask(question: String, modelSays: String, ctx: UserContext): Pair<Scripted, OrchestratorEvent.Answered?> {
        val dir = System.getProperty("katori.projectDir")!!
        val knowledge = KnowledgeFacts.load { File(dir, "app/src/main/assets/knowledge/facts.csv").inputStream() }
        val llm = Scripted(modelSays)
        val orchestrator = DefaultOrchestrator(
            asr = MinimalRig.NoAsr(), llm = llm, tts = MinimalRig.SilentTts(), rules = DefaultRulesEngine(), resolver = resolver, store = MinimalRig.RecordingStore(),
            advice = object : AdviceStore { override suspend fun latest(mealId: Long) = null; override suspend fun save(mealId: Long, advice: StoredAdvice) = Unit },
            labs = object : LabStore { override suspend fun save(values: List<LabValue>) = Outcome.Ok(values.size) },
            contextSource = object : UserContextSource { override suspend fun current() = ctx },
            knowledge = knowledge, triggerText = TriggerText(TriggerText.ENGLISH),
            contextText = ContextText(ContextText.ENGLISH, TriggerText.ENGLISH, ZoneId.of("Asia/Kolkata")),
            clock = Clock.fixed(Instant.parse("2026-09-21T06:00:00Z"), ZoneOffset.UTC),
        )
        val events = runBlocking { orchestrator.handle(UserIntent.Type(question, SpeechLanguageRef("en-IN"))).toList() }
        return llm to events.filterIsInstance<OrchestratorEvent.Answered>().singleOrNull()
    }

    private val screenshot = "I said that I ate two chapatiis, can you tell me the nutritional information of it"

    /** The screenshot, with the meal in the diary: the model must be given the meal's figures, and its grounded answer must pass. */
    @Test fun `a how-much question about a logged meal gives the model the meal's figures`() {
        val (llm, answered) = ask(screenshot, "Your two chapatis came to 232.3 kcal, with 9.0 g of protein and 49.2 g of carbohydrate.", diaryWithTwoChapatis())
        val given = llm.request!!.figures.map { it.text }
        assertTrue("the model was given the meal without its figures: $given", given.any { "232.3 kcal" in it && "9.0 g" in it })
        assertNull("a grounded answer was refused: ${answered?.refused}", answered?.refused)
        assertTrue(answered?.text?.contains("232.3") == true)
    }

    @Test fun `calories carbs and what-did-that-give-me are how-much questions`() {
        val ctx = diaryWithTwoChapatis()
        for ((q, a) in listOf(
            "how many calories in that" to "That came to 232.3 kcal.",
            "is that a lot of carbs" to "It was 49.2 g of carbohydrate.",
            "what did that give me" to "Two chapatis: 232.3 kcal, 9.0 g of protein, 49.2 g of carbohydrate and 7.3 g of fibre.",
            "how much protein was in that" to "9.0 g of protein.",
        )) {
            val (llm, answered) = ask(q, a, ctx)
            val given = llm.request!!.figures.map { it.text }
            assertTrue("'$q': the figure the question asks for was not given: $given", given.any { it.contains("kcal") || it.contains("carbohydrate: 49.2") || it.contains("protein: 9.0") })
            assertNull("'$q': a grounded answer was refused: ${answered?.refused}", answered?.refused)
        }
    }

    /** A question about WHAT was eaten keeps its shape: the items and the time, no figures. */
    @Test fun `what did I eat today is still answered from the items alone`() {
        val (llm, _) = ask("what did I eat today", "You had two chapatis this morning.", diaryWithTwoChapatis())
        val given = llm.request!!.figures.map { it.text }
        assertTrue("$given", given.any { "Chapati / roti" in it } && given.none { "kcal" in it })
    }
}
