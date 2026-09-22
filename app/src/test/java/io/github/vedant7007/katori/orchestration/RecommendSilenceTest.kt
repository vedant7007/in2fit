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
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.ProfileSnapshot
import io.github.vedant7007.katori.domain.SpeechLanguageRef
import io.github.vedant7007.katori.domain.StoredAdvice
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserContext
import io.github.vedant7007.katori.domain.UserContextSource
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.llm.AnswerLength
import io.github.vedant7007.katori.ml.llm.AnswerRequest
import io.github.vedant7007.katori.ml.llm.ConversationPrompts
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
import org.junit.Assert.assertFalse
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
 * THE SILENCE, ON THE JVM. The integrator's e2e run of 21 Sep 00:33: RECOMMEND "what should I
 * eat for more iron" generated 30 tokens in 3.2 s and reached the screen as `phrased=null`;
 * SUGGEST generated 60 tokens and the same. So it is not the budget: the model wrote a
 * sentence and a guard refused it, and `DefaultOrchestrator.textOrNull()` drops the guard's
 * detail, so no run has ever shown which guard or what sentence.
 *
 * This is the same turn on the JVM with the real request, the real four guards and the kinds of
 * sentence a small model writes for this prompt. FOUND: every sentence that "says why" in the
 * model's own words is refused by ClaimGuard ("a good source of iron", "help raise
 * haemoglobin", "vitamin C helps you absorb it"); a bare food name passes, and a name followed
 * by a row quoted verbatim passes. The SHORT rule of 20 Sep asked for exactly what the guard
 * refuses, so the prompt and the guard contradicted each other, and the guard is right: a
 * paraphrased claim is what it exists to refuse. The rule now asks for the food and how to have
 * it, no reason; the reason is appended by code, verbatim from the row (the integrator's line).
 * The phone re-measures; this pins the contract on the JVM.
 */
class RecommendSilenceTest {

    companion object {
        private lateinit var db: FoodDbSource
        @JvmStatic @BeforeClass fun open() { db = JdbcFoodDbSource.openBundled() }
        @JvmStatic @AfterClass fun close() = db.close()
    }

    private class ScriptedRuntime(private val response: String) : LlamaRuntime {
        override fun generate(prompt: String, maxTokens: Int, stop: List<String>) = response
        override fun close() = Unit
    }

    private class Scripted(modelSays: String) : LlmEngine, LlmLease {
        val real = LlamaCppLlmEngine(ScriptedRuntime(modelSays))
        var request: RecommendRequest? = null
        var outcome: Outcome<PhrasedText>? = null
        override suspend fun classify(transcript: String, languageTag: String) = Outcome.Ok(Intent.RECOMMEND)
        override suspend fun extract(request: ExtractionRequest): Outcome<ExtractionResult> = Outcome.Ok(ExtractionResult(listOf(ExtractedItem("rice", 1.0, "plate", null)), "{}"))
        override suspend fun phrase(request: PhrasingRequest): Outcome<PhrasedText> = Outcome.NotImplemented("test")
        override suspend fun answer(request: AnswerRequest, length: AnswerLength) = real.answer(request, length)
        override suspend fun recommend(request: RecommendRequest, length: AnswerLength) = real.recommend(request, length).also { this.request = request; outcome = it }
        override suspend fun <T> use(block: suspend (LlmEngine) -> T): Outcome<T> = Outcome.Ok(block(this))
    }

    /** The phone's context on 21 Sep 00:33: a haemoglobin of 9.8 against a printed 12 to 15, nothing declared, hostel student. */
    private fun phoneContext(): UserContext = UserContext(
        ProfileSnapshot(19, 62.0, 172.0, null, null, LifeContext.HOSTEL_STUDENT, null, emptySet()),
        emptyList(), listOf(LabValue("Haemoglobin", 9.8, "g/dL", 12.0, 15.0, LocalDate.of(2026, 9, 12))), emptyList(), emptyList(),
        CandidateCatalogue.load(db),
    )

    private fun turn(question: String, modelSays: String): Scripted {
        val dir = System.getProperty("katori.projectDir")!!
        val knowledge = KnowledgeFacts.load { File(dir, "app/src/main/assets/knowledge/facts.csv").inputStream() }
        val llm = Scripted(modelSays)
        val lookup = SqliteFoodLookup(db)
        val orchestrator = DefaultOrchestrator(
            asr = MinimalRig.NoAsr(), llm = llm, tts = MinimalRig.SilentTts(), rules = DefaultRulesEngine(), resolver = LookupMealResolver(lookup), store = MinimalRig.RecordingStore(),
            advice = object : AdviceStore { override suspend fun latest(mealId: Long) = null; override suspend fun save(mealId: Long, advice: StoredAdvice) = Unit },
            labs = object : LabStore { override suspend fun save(values: List<LabValue>) = Outcome.Ok(values.size) },
            contextSource = object : UserContextSource { override suspend fun current() = phoneContext() },
            knowledge = knowledge, triggerText = TriggerText(TriggerText.ENGLISH),
            contextText = ContextText(ContextText.ENGLISH, TriggerText.ENGLISH, ZoneId.of("Asia/Kolkata")),
            clock = Clock.fixed(Instant.parse("2026-09-21T00:33:00Z"), ZoneOffset.UTC),
        )
        val events = runBlocking { orchestrator.handle(UserIntent.Type(question, SpeechLanguageRef("en-IN"))).toList() }
        val advice = events.filterIsInstance<OrchestratorEvent.Advice>().singleOrNull()
        println("  ADVICE phrased=${advice?.phrased}")
        return llm
    }

    @Test fun `the phone's RECOMMEND turn, the request it built, and what each kind of sentence meets`() {
        val question = "what should I eat for more iron"
        val first = turn(question, "Chickpeas.")
        val req = first.request!!
        println("=== THE REQUEST THE PHONE BUILT ===")
        println("allowed: ${req.allowedFoodNames}")
        println("rows: ${req.facts.map { it.id }}")
        req.facts.forEach { println("  row ${it.id}: ${it.fact}") }
        println("trigger: ${req.triggerText}")
        println("length rule: ${AnswerLength.SHORT.recommendRule}   budget ${ConversationPrompts.RECOMMEND_MAX_TOKENS} tokens")
        println()
        println("=== SENTENCES A MODEL WRITES FOR THAT PROMPT, THROUGH THE FOUR GUARDS ===")
        val candidates = listOf(
            "Chickpeas (bengal gram), cooked.",
            "Try cooked chickpeas: they are a good source of iron.",
            "Add cooked chickpeas to your meals, since they contain iron and help raise haemoglobin.",
            "Drumstick leaves, raw: rich in iron, and vitamin C helps you absorb it.",
            "Chickpeas (bengal gram), cooked. " + req.facts.firstOrNull()?.fact.orEmpty(),
            "Have cooked chickpeas with your dal. " + req.facts.firstOrNull()?.fact.orEmpty(),
            "Chickpeas, cooked, at 2.9 mg of iron per 100 g.",
            "Cooked chickpeas, with lemon: the notes say vitamin C helps the iron absorb.",
            "Your haemoglobin is low, so eat more spinach and take an iron tablet daily.",
        )
        val verdicts = candidates.associateWith { s ->
            when (val o = turn(question, s).outcome) {
                is Outcome.Ok -> "PASSES"
                is Outcome.Unavailable -> "REFUSED: ${o.detail}"
                else -> "$o"
            }
        }
        verdicts.forEach { (s, v) ->
            println("  ${if (v == "PASSES") "PASSES " else "REFUSED"} \"$s\"")
            if (v != "PASSES") println("          " + v.removePrefix("REFUSED: "))
        }

        // The contract: a name passes; a name plus the row verbatim passes; a reason in the model's own words is a ClaimGuard refusal.
        assertTrue(verdicts.getValue(candidates[0]) == "PASSES")
        assertTrue(verdicts.getValue(candidates[4]) == "PASSES" && verdicts.getValue(candidates[5]) == "PASSES")
        for (i in listOf(1, 2, 3, 7)) assertTrue("'${candidates[i]}': ${verdicts.getValue(candidates[i])}", verdicts.getValue(candidates[i]).contains("claim in its own words"))
        assertTrue(verdicts.getValue(candidates[6]).contains("invented the number"))
        assertTrue(verdicts.getValue(candidates[8]).contains("prescribed or judged"))
        // And the prompt no longer asks for what the guard refuses.
        val prompt = ConversationPrompts.recommend(req)
        assertFalse("the prompt still asks the model to say why", prompt.contains("say why"))
        assertTrue(prompt.contains("No reason"))
        assertTrue("a runaway is capped near one sentence", ConversationPrompts.RECOMMEND_MAX_TOKENS <= 48)
    }
}
