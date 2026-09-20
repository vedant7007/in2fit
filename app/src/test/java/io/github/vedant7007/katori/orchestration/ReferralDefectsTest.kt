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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * TARGET FOR THE INTEGRATOR, FAILING BY DESIGN until the fix lands: defect 1 of `0024`.
 *
 * The referral is decided only by the rules engine, from a scanned lab value. A person who
 * SAYS "my haemoglobin is 7, is that dangerous?" with no report on file gets no referral at all,
 * and a guard failure on ANSWER ends the turn with nothing, which is the refusal-that-abandons
 * `0015` forbids. `SafetyLine.invitesClinicalJudgement` decides it from the question, before
 * the model runs; the fix is `referralFollows = evaluation.referralRequired || SafetyLine.invitesClinicalJudgement(text)`
 * with a fixed line when there is no trigger to render, and a fixed line plus "I can't judge
 * that" instead of `Failed` on a guard failure.
 *
 * Fixtures are a minimal copy of `DefaultOrchestratorTest`'s: no labs on file, so nothing can
 * come from the engine, which is the point. When these pass, this header goes and the tests stay.
 */
class ReferralDefectsTest {

    private class FakeLlm(
        var answered: Outcome<PhrasedText> = Outcome.Ok(PhrasedText("answered", numericGuardPassed = true)),
        var recommended: Outcome<PhrasedText> = Outcome.Ok(PhrasedText("recommended", numericGuardPassed = true)),
    ) : LlmEngine, LlmLease {
        val answers = mutableListOf<AnswerRequest>()
        val recommendations = mutableListOf<RecommendRequest>()
        var intent: Outcome<Intent> = Outcome.Ok(Intent.ANSWER)
        override suspend fun classify(transcript: String, languageTag: String) = intent
        override suspend fun extract(request: ExtractionRequest): Outcome<ExtractionResult> = Outcome.NotImplemented("test")
        override suspend fun phrase(request: PhrasingRequest): Outcome<PhrasedText> = Outcome.NotImplemented("test")
        override suspend fun answer(request: AnswerRequest, length: AnswerLength) = answered.also { answers += request }
        override suspend fun recommend(request: RecommendRequest, length: AnswerLength) = recommended.also { recommendations += request }
        override suspend fun <T> use(block: suspend (LlmEngine) -> T): Outcome<T> = Outcome.Ok(block(this))
    }

    private class NoStore : MealStore {
        override suspend fun save(meal: ResolvedMeal, loggedAt: Instant): Outcome<Long> = error("nothing here logs a meal")
    }

    private class NoResolver : MealResolver {
        override suspend fun resolve(parsed: ParsedMeal, languageTag: String): Outcome<ResolvedMeal> = error("nothing here resolves a plate")
    }

    private class SilentTts : TtsEngine {
        override val supportedLanguages = setOf(SpeechLanguage.ENGLISH_INDIA)
        override suspend fun prepare(language: SpeechLanguage) = Outcome.Ok(Unit)
        override suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun stop() {}
    }

    private class NoAsr : AsrEngine {
        override val supportedLanguages = SpeechLanguage.entries.toSet()
        override suspend fun prepare(language: SpeechLanguage) = Outcome.Ok(Unit)
        override fun listen(language: SpeechLanguage): Flow<AsrEvent> = flow {}
        override suspend fun transcribe(clip: AudioClip, language: SpeechLanguage): Outcome<Transcript> = Outcome.NotImplemented("test")
    }

    /** No labs, no conditions: the rules engine has nothing to escalate on. The question is the only source of a referral. */
    private val nobodyOnFile = UserContext(
        profile = ProfileSnapshot(19, 62.0, 172.0, Sex.MALE, Goal.MAINTAIN, LifeContext.HOSTEL_STUDENT, DietType.VEGETARIAN, emptySet()),
        declaredConditions = emptyList(), labValues = emptyList(), recentMeals = emptyList(), periodTotals = emptyList(),
        candidates = listOf(CandidateFood("thotakura", "thotakura", setOf(LifeContext.HOSTEL_STUDENT), mapOf(Nutrient.IRON to 3.9))),
    )

    private val facts = KnowledgeFacts(listOf(
        KnowledgeFact("iron.vitc", "iron", setOf("iron", "haemoglobin"), "Vitamin C taken with a meal increases the iron absorbed from plant foods.", "t", "https://example.invalid", "2026-09-20", ""),
    ))

    private val en = SpeechLanguageRef("en-IN")

    private fun run(llm: FakeLlm, text: String): List<OrchestratorEvent> {
        val orchestrator = DefaultOrchestrator(
            asr = NoAsr(), llm = llm, tts = SilentTts(), rules = DefaultRulesEngine(), resolver = NoResolver(), store = NoStore(),
            contextSource = object : UserContextSource { override suspend fun current() = nobodyOnFile },
            knowledge = facts, triggerText = TriggerText(TriggerText.ENGLISH),
            contextText = ContextText(ContextText.ENGLISH, TriggerText.ENGLISH, ZoneId.of("Asia/Kolkata")),
            clock = Clock.fixed(Instant.parse("2026-09-20T08:00:00Z"), ZoneOffset.UTC),
        )
        return runBlocking { orchestrator.handle(UserIntent.Type(text, en)).toList() }
    }

    @Test fun `a spoken reading with no report on file still gets the referral on ANSWER`() {
        val llm = FakeLlm()
        val events = run(llm, "my haemoglobin is 7, is that dangerous?")
        assertTrue("the model must be told a referral follows", llm.answers.single().referralFollows)
        val answered = events.filterIsInstance<OrchestratorEvent.Answered>().single()
        assertNotNull("a clinical question with nothing on file got no referral", answered.referral)
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    @Test fun `a medication question with no report on file still gets the referral on RECOMMEND`() {
        val llm = FakeLlm().apply { intent = Outcome.Ok(Intent.RECOMMEND) }
        val events = run(llm, "should I stop my medication if I eat better?")
        assertTrue("the model must be told a referral follows", llm.recommendations.single().referralFollows)
        val advice = events.filterIsInstance<OrchestratorEvent.Advice>().single()
        assertNotNull("a clinical question with nothing on file got no referral", advice.referral)
        assertEquals("the help is still there beside it", "recommended", advice.phrased)
    }

    /** 0015: a referral comes ALONGSIDE help, never instead of it, and never nothing. */
    @Test fun `a guard failure on a clinical ANSWER shows the referral, not Failed`() {
        val llm = FakeLlm(answered = Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "the model invented the number '60'"))
        val events = run(llm, "how much iron tablet should I take?")
        assertTrue("the turn ended in Failed and the person got nothing: $events", events.none { it is OrchestratorEvent.Failed })
        val answered = events.filterIsInstance<OrchestratorEvent.Answered>().single()
        assertNotNull("the fixed referral line is what survives a refused answer", answered.referral)
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    /** And the control: a plain food question must not grow a referral line. */
    @Test fun `a plain food question with nothing on file gets no referral`() {
        val llm = FakeLlm().apply { intent = Outcome.Ok(Intent.RECOMMEND) }
        val events = run(llm, "what should I eat for more iron")
        assertEquals(false, llm.recommendations.single().referralFollows)
        assertNull(events.filterIsInstance<OrchestratorEvent.Advice>().single().referral)
    }
}
