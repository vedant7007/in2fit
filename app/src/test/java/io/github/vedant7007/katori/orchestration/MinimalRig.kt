package io.github.vedant7007.katori.orchestration

import io.github.vedant7007.katori.data.knowledge.KnowledgeFact
import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts
import io.github.vedant7007.katori.domain.CandidateFood
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.DefaultRulesEngine
import io.github.vedant7007.katori.domain.DietType
import io.github.vedant7007.katori.domain.Goal
import io.github.vedant7007.katori.domain.LifeContext
import io.github.vedant7007.katori.domain.MealResolver
import io.github.vedant7007.katori.domain.MealItemSnapshot
import io.github.vedant7007.katori.domain.ResolvedItem
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.NutrientProfile
import io.github.vedant7007.katori.domain.model.NutrientTotal
import io.github.vedant7007.katori.domain.model.NutrientUnit
import io.github.vedant7007.katori.domain.model.NutrientValue
import io.github.vedant7007.katori.domain.model.NutritionFigure
import io.github.vedant7007.katori.ml.llm.ExtractedItem
import io.github.vedant7007.katori.domain.MealStore
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.ProfileSnapshot
import io.github.vedant7007.katori.domain.ResolvedMeal
import io.github.vedant7007.katori.domain.Sex
import io.github.vedant7007.katori.domain.SpeechLanguageRef
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserContext
import io.github.vedant7007.katori.domain.UserContextSource
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.AsrEngine
import io.github.vedant7007.katori.ml.asr.AsrEvent
import io.github.vedant7007.katori.ml.asr.AudioClip
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import io.github.vedant7007.katori.ml.asr.Transcript
import io.github.vedant7007.katori.ml.llm.AnswerLength
import io.github.vedant7007.katori.ml.llm.AnswerRequest
import io.github.vedant7007.katori.ml.llm.ExtractionRequest
import io.github.vedant7007.katori.ml.llm.ExtractionResult
import io.github.vedant7007.katori.ml.llm.Intent
import io.github.vedant7007.katori.ml.llm.LlmEngine
import io.github.vedant7007.katori.ml.llm.PhrasedText
import io.github.vedant7007.katori.ml.llm.PhrasingRequest
import io.github.vedant7007.katori.ml.llm.RecommendRequest
import io.github.vedant7007.katori.ml.tts.TtsEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The smallest rig that drives [DefaultOrchestrator] end to end against fakes, with NOBODY on
 * file: no labs, no conditions, no meals. Shared by the integrator's target tests, so a test
 * reads as the property it checks and not as fixture. A copy of the shape in
 * `DefaultOrchestratorTest`, reduced to what an ANSWER or RECOMMEND turn touches.
 */
internal object MinimalRig {

    class FakeLlm(
        var answered: Outcome<PhrasedText> = Outcome.Ok(PhrasedText("answered", numericGuardPassed = true)),
        var recommended: Outcome<PhrasedText> = Outcome.Ok(PhrasedText("recommended", numericGuardPassed = true)),
    ) : LlmEngine, LlmLease {
        val answers = mutableListOf<AnswerRequest>()
        val recommendations = mutableListOf<RecommendRequest>()
        /** The length the orchestrator asked for, per call. */
        val lengths = mutableListOf<AnswerLength>()
        var intent: Outcome<Intent> = Outcome.Ok(Intent.ANSWER)
        override suspend fun classify(transcript: String, languageTag: String) = intent
        /** A real extraction, so a LOG that should never have been one visibly reaches the store. */
        override suspend fun extract(request: ExtractionRequest): Outcome<ExtractionResult> =
            Outcome.Ok(ExtractionResult(listOf(ExtractedItem("roti", 2.0, "piece", null)), "{}"))
        override suspend fun phrase(request: PhrasingRequest): Outcome<PhrasedText> = Outcome.NotImplemented("test")
        override suspend fun answer(request: AnswerRequest, length: AnswerLength) = answered.also { answers += request; lengths += length }
        override suspend fun recommend(request: RecommendRequest, length: AnswerLength) = recommended.also { recommendations += request; lengths += length }
        override suspend fun <T> use(block: suspend (LlmEngine) -> T): Outcome<T> = Outcome.Ok(block(this))
    }

    /** Records what reaches it. On these turns that must be nothing; a saved meal is the defect. */
    class RecordingStore : MealStore {
        val saved = mutableListOf<ResolvedMeal>()
        override suspend fun save(meal: ResolvedMeal, loggedAt: Instant): Outcome<Long> { saved += meal; return Outcome.Ok(42L) }
    }

    /** Resolves every item to a plausible food, so a misrouted LOG goes all the way to the store. */
    class PlainResolver : MealResolver {
        override suspend fun resolve(parsed: ParsedMeal, languageTag: String): Outcome<ResolvedMeal> = Outcome.Ok(
            ResolvedMeal(
                parsed = parsed,
                items = parsed.items.map {
                    ResolvedItem(
                        MealItemSnapshot(it.spokenName, it.spokenName, 80.0, mapOf(Nutrient.IRON to 0.8)),
                        DataSource.USDA_SR_LEGACY,
                        NutrientProfile(mapOf(Nutrient.IRON to NutrientValue.Measured(0.8, NutrientUnit.MILLIGRAM))),
                        it.confidence,
                    )
                },
                figures = listOf(NutritionFigure(
                    NutrientTotal(Nutrient.IRON, 1.6, NutrientUnit.MILLIGRAM, Completeness.COMPLETE, emptyList()),
                    ConfidenceRules.of(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_STATED),
                    listOf(DataSource.USDA_SR_LEGACY),
                )),
            )
        )
    }

    class SilentTts : TtsEngine {
        override val supportedLanguages = setOf(SpeechLanguage.ENGLISH_INDIA)
        override suspend fun prepare(language: SpeechLanguage) = Outcome.Ok(Unit)
        override suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun stop() {}
    }

    class NoAsr : AsrEngine {
        override val supportedLanguages = SpeechLanguage.entries.toSet()
        override suspend fun prepare(language: SpeechLanguage) = Outcome.Ok(Unit)
        override fun listen(language: SpeechLanguage): Flow<AsrEvent> = flow {}
        override suspend fun transcribe(clip: AudioClip, language: SpeechLanguage): Outcome<Transcript> = Outcome.NotImplemented("test")
    }

    /** No labs, no conditions: the rules engine has nothing to escalate on. The question is the only source of a referral. */
    val nobodyOnFile = UserContext(
        profile = ProfileSnapshot(19, 62.0, 172.0, Sex.MALE, Goal.MAINTAIN, LifeContext.HOSTEL_STUDENT, DietType.VEGETARIAN, emptySet()),
        declaredConditions = emptyList(), labValues = emptyList(), recentMeals = emptyList(), periodTotals = emptyList(),
        candidates = listOf(CandidateFood("thotakura", "thotakura", setOf(LifeContext.HOSTEL_STUDENT), mapOf(Nutrient.IRON to 3.9))),
    )

    val facts = KnowledgeFacts(listOf(
        KnowledgeFact("iron.vitc", "iron", setOf("iron", "haemoglobin"), "Vitamin C taken with a meal increases the iron absorbed from plant foods.", "t", "https://example.invalid", "2026-09-20", ""),
    ))

    val en = SpeechLanguageRef("en-IN")

    /** Runs one typed turn. [store] is returned to the caller through [lastStore] so a test can assert nothing was written. */
    var lastStore = RecordingStore()
        private set

    fun run(llm: FakeLlm, text: String): List<OrchestratorEvent> {
        lastStore = RecordingStore()
        val orchestrator = DefaultOrchestrator(
            asr = NoAsr(), llm = llm, tts = SilentTts(), rules = DefaultRulesEngine(), resolver = PlainResolver(), store = lastStore,
            contextSource = object : UserContextSource { override suspend fun current() = nobodyOnFile },
            knowledge = facts, triggerText = TriggerText(TriggerText.ENGLISH),
            contextText = ContextText(ContextText.ENGLISH, TriggerText.ENGLISH, ZoneId.of("Asia/Kolkata")),
            clock = Clock.fixed(Instant.parse("2026-09-20T08:00:00Z"), ZoneOffset.UTC),
        )
        return runBlocking { orchestrator.handle(UserIntent.Type(text, en)).toList() }
    }

}
