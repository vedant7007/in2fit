package io.github.vedant7007.katori.orchestration

import io.github.vedant7007.katori.data.knowledge.KnowledgeFact
import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts
import io.github.vedant7007.katori.domain.CandidateFood
import io.github.vedant7007.katori.domain.ConditionSource
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.DeclaredCondition
import io.github.vedant7007.katori.domain.DefaultRulesEngine
import io.github.vedant7007.katori.domain.DietType
import io.github.vedant7007.katori.domain.Goal
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.LifeContext
import io.github.vedant7007.katori.domain.LoggedMeal
import io.github.vedant7007.katori.domain.MealItemSnapshot
import io.github.vedant7007.katori.domain.MealResolver
import io.github.vedant7007.katori.domain.MealSnapshot
import io.github.vedant7007.katori.domain.MealStore
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.Period
import io.github.vedant7007.katori.domain.PeriodTotals
import io.github.vedant7007.katori.domain.ProfileSnapshot
import io.github.vedant7007.katori.domain.ResolvedItem
import io.github.vedant7007.katori.domain.ResolvedMeal
import io.github.vedant7007.katori.domain.Sex
import io.github.vedant7007.katori.domain.SpeechLanguageRef
import io.github.vedant7007.katori.domain.SpokenIntent
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserContext
import io.github.vedant7007.katori.domain.UserContextSource
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientProfile
import io.github.vedant7007.katori.domain.model.NutrientValue
import io.github.vedant7007.katori.domain.model.NutrientTotal
import io.github.vedant7007.katori.domain.model.NutrientUnit
import io.github.vedant7007.katori.domain.model.NutritionFigure
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.AsrEngine
import io.github.vedant7007.katori.ml.asr.AsrEvent
import io.github.vedant7007.katori.ml.asr.AudioClip
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import io.github.vedant7007.katori.ml.asr.Transcript
import io.github.vedant7007.katori.ml.llm.AnswerLength
import io.github.vedant7007.katori.ml.llm.AnswerRequest
import io.github.vedant7007.katori.ml.llm.ExtractedItem
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The routing of `0015`, against fakes for every pipeline. Two properties are the point of the
 * file: a SUGGEST never reaches the store, and an ANSWER or RECOMMEND never reaches the model
 * without the person's own context in the request. Everything else is the order of events.
 */
class DefaultOrchestratorTest {

    // --- fakes ---------------------------------------------------------------------------------

    private class FakeLlm(
        var intent: Outcome<Intent> = Outcome.Ok(Intent.LOG),
        var extraction: Outcome<ExtractionResult> = Outcome.Ok(
            ExtractionResult(listOf(ExtractedItem("rice", 1.0, "plate", null), ExtractedItem("palak paneer", 1.0, "katori", null)), "{}")
        ),
        var phrased: Outcome<PhrasedText> = Outcome.Ok(PhrasedText("phrased", numericGuardPassed = true)),
        var answered: Outcome<PhrasedText> = Outcome.Ok(PhrasedText("answered", numericGuardPassed = true)),
        var recommended: Outcome<PhrasedText> = Outcome.Ok(PhrasedText("recommended", numericGuardPassed = true)),
    ) : LlmEngine, LlmLease {
        val phrasings = mutableListOf<PhrasingRequest>()
        val answers = mutableListOf<AnswerRequest>()
        val recommendations = mutableListOf<RecommendRequest>()
        override suspend fun classify(transcript: String, languageTag: String) = intent
        override suspend fun extract(request: ExtractionRequest) = extraction
        override suspend fun phrase(request: PhrasingRequest) = phrased.also { phrasings += request }
        override suspend fun answer(request: AnswerRequest, length: AnswerLength) = answered.also { answers += request }
        override suspend fun recommend(request: RecommendRequest, length: AnswerLength) = recommended.also { recommendations += request }
        override suspend fun <T> use(block: suspend (LlmEngine) -> T): Outcome<T> = Outcome.Ok(block(this))
    }

    private class RecordingStore : MealStore {
        val saved = mutableListOf<ResolvedMeal>()
        override suspend fun save(meal: ResolvedMeal, loggedAt: Instant): Outcome<Long> {
            saved += meal
            return Outcome.Ok(42L)
        }
    }

    private class FakeResolver(var outcome: Outcome<ResolvedMeal>? = null) : MealResolver {
        override suspend fun resolve(parsed: ParsedMeal, languageTag: String): Outcome<ResolvedMeal> =
            outcome ?: Outcome.Ok(
                ResolvedMeal(
                    parsed = parsed,
                    items = parsed.items.map {
                        ResolvedItem(
                            snapshot = MealItemSnapshot(it.spokenName, it.spokenName, 150.0, mapOf(Nutrient.CARBOHYDRATE to 40.0, Nutrient.IRON to 0.8)),
                            source = DataSource.USDA_SR_LEGACY,
                            nutrients = NutrientProfile(mapOf(Nutrient.CARBOHYDRATE to NutrientValue.Measured(40.0, NutrientUnit.GRAM), Nutrient.IRON to NutrientValue.Measured(0.8, NutrientUnit.MILLIGRAM))),
                            confidence = it.confidence,
                        )
                    },
                    figures = listOf(figure(Nutrient.ENERGY, 520.0, NutrientUnit.KCAL), figure(Nutrient.IRON, 1.6, NutrientUnit.MILLIGRAM)),
                )
            )
    }

    private class SilentTts : TtsEngine {
        val spoken = mutableListOf<String>()
        override val supportedLanguages = setOf(SpeechLanguage.ENGLISH_INDIA)
        override suspend fun prepare(language: SpeechLanguage) = Outcome.Ok(Unit)
        override suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit> { spoken += text; return Outcome.Ok(Unit) }
        override suspend fun stop() {}
    }

    private class ScriptedAsr(private val events: List<AsrEvent>) : AsrEngine {
        override val supportedLanguages = SpeechLanguage.entries.toSet()
        override suspend fun prepare(language: SpeechLanguage) = Outcome.Ok(Unit)
        override fun listen(language: SpeechLanguage): Flow<AsrEvent> = flow { events.forEach { emit(it) } }
        override suspend fun transcribe(clip: AudioClip, language: SpeechLanguage): Outcome<Transcript> =
            Outcome.NotImplemented("test")
    }

    // --- the person ----------------------------------------------------------------------------

    private val haemoglobin = LabValue("Haemoglobin", 9.8, "g/dL", 12.0, 15.0, LocalDate.of(2026, 9, 12))
    private val glucoseFarAbove = LabValue("Fasting glucose", 260.0, "mg/dL", 70.0, 100.0, LocalDate.of(2026, 9, 12))

    private fun context(labs: List<LabValue> = listOf(haemoglobin)) = UserContext(
        profile = ProfileSnapshot(19, 62.0, 172.0, Sex.MALE, Goal.MAINTAIN, LifeContext.HOSTEL_STUDENT, DietType.VEGETARIAN, emptySet()),
        declaredConditions = listOf(DeclaredCondition("low iron", ConditionSource.USER_DECLARED)),
        labValues = labs,
        recentMeals = listOf(
            LoggedMeal(
                MealSnapshot(7L, listOf(MealItemSnapshot("roti", "usda:roti", 80.0, emptyMap()), MealItemSnapshot("dal", "usda:dal", 150.0, emptyMap())), Instant.parse("2026-09-19T07:40:00Z")),
                listOf(figure(Nutrient.IRON, 2.5, NutrientUnit.MILLIGRAM), partial(Nutrient.PROTEIN, 11.0, NutrientUnit.GRAM, "dal")),
            )
        ),
        periodTotals = listOf(PeriodTotals(Period.LAST_SEVEN_DAYS, listOf(partial(Nutrient.IRON, 4.6, NutrientUnit.MILLIGRAM, "curry leaves")))),
        candidates = listOf(
            CandidateFood("thotakura", "thotakura", setOf(LifeContext.HOSTEL_STUDENT), mapOf(Nutrient.IRON to 3.9, Nutrient.FIBRE to 2.0, Nutrient.CARBOHYDRATE to 4.0)),
            CandidateFood("sprouts", "sprouts", setOf(LifeContext.HOSTEL_STUDENT), mapOf(Nutrient.IRON to 2.6, Nutrient.FIBRE to 6.0, Nutrient.CARBOHYDRATE to 20.0)),
        ),
    )

    private val facts = KnowledgeFacts(
        listOf(
            KnowledgeFact("iron.vitc", "iron", setOf("iron", "vitamin c"), "Vitamin C taken with a meal increases the iron absorbed from plant foods.", "t", "https://example.invalid", "2026-09-20", ""),
            KnowledgeFact("protein.dal", "protein", setOf("dal", "protein"), "Cooked dal has about 9 g of protein per cup.", "t", "https://example.invalid", "2026-09-20", ""),
        )
    )

    private val en = SpeechLanguageRef("en-IN")

    private class Rig(
        val llm: FakeLlm = FakeLlm(),
        val store: RecordingStore = RecordingStore(),
        val resolver: FakeResolver = FakeResolver(),
        val tts: SilentTts = SilentTts(),
        val asr: AsrEngine = ScriptedAsr(emptyList()),
        val ctx: UserContext,
        val facts: KnowledgeFacts,
    ) {
        val orchestrator = DefaultOrchestrator(
            asr = asr, llm = llm, tts = tts, rules = DefaultRulesEngine(), resolver = resolver, store = store,
            contextSource = object : UserContextSource { override suspend fun current() = ctx },
            knowledge = facts, triggerText = TriggerText(TriggerText.ENGLISH),
            contextText = ContextText(ContextText.ENGLISH, TriggerText.ENGLISH, ZoneId.of("Asia/Kolkata")),
            clock = Clock.fixed(Instant.parse("2026-09-20T08:00:00Z"), ZoneOffset.UTC),
        )
        fun run(intent: UserIntent) = runBlocking { orchestrator.handle(intent).toList() }
    }

    private fun rig(llm: FakeLlm = FakeLlm(), ctx: UserContext = context(), resolver: FakeResolver = FakeResolver()) =
        Rig(llm = llm, ctx = ctx, resolver = resolver, facts = facts)

    // --- LOG writes, SUGGEST does not --------------------------------------------------------

    @Test fun `LOG resolves, saves, evaluates and phrases, in that order`() {
        val r = rig(FakeLlm(intent = Outcome.Ok(Intent.LOG)))
        val events = r.run(UserIntent.Type("I had rice and palak paneer", en))

        assertEquals(1, r.store.saved.size)
        val resolvedAt = events.indexOfFirst { it is OrchestratorEvent.MealResolved }
        val loggedAt = events.indexOfFirst { it is OrchestratorEvent.MealLogged }
        val adviceAt = events.indexOfFirst { it is OrchestratorEvent.Advice }
        assertTrue("$events", resolvedAt in 0 until loggedAt && loggedAt < adviceAt)
        assertEquals(42L, (events[loggedAt] as OrchestratorEvent.MealLogged).mealId)
        assertEquals(false, (events[resolvedAt] as OrchestratorEvent.MealResolved).hypothetical)
        assertEquals(OrchestratorEvent.Completed, events.last())
        // Rules ran before the model phrased: the phrasing request carries the evaluation.
        assertEquals(1, r.llm.phrasings.size)
        assertTrue(r.llm.phrasings.single().evaluation.firedRules.isNotEmpty())
    }

    /** THE CONTRACT. A plate not yet eaten is a question, not a record. */
    @Test fun `SUGGEST goes through the whole plate path and never touches the store`() {
        val r = rig(FakeLlm(intent = Outcome.Ok(Intent.SUGGEST)))
        val events = r.run(UserIntent.Type("I'm having rice and palak paneer, what should I add", en))

        assertEquals("a hypothetical plate must never be saved", 0, r.store.saved.size)
        assertTrue("no MealLogged on SUGGEST", events.none { it is OrchestratorEvent.MealLogged })
        val resolved = events.filterIsInstance<OrchestratorEvent.MealResolved>().single()
        assertTrue("the UI must be told the plate is hypothetical", resolved.hypothetical)
        // And it still gets the full evaluation and advice, which is what they asked for.
        val advice = events.filterIsInstance<OrchestratorEvent.Advice>().single()
        assertTrue(advice.evaluation.rankedCandidates.isNotEmpty())
        assertEquals("phrased", advice.phrased)
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    @Test fun `a plate that cannot be resolved asks and saves nothing`() {
        val r = rig(FakeLlm(intent = Outcome.Ok(Intent.LOG)), resolver = FakeResolver(Outcome.Unavailable(UnavailableReason.NO_MATCH, "gongura")))
        val events = r.run(UserIntent.Type("I had gongura pachadi", en))
        val ask = events.filterIsInstance<OrchestratorEvent.NeedsConfirmation>().single()
        assertEquals(UnavailableReason.NO_MATCH, ask.why)
        assertEquals("I had gongura pachadi", ask.parsed.rawTranscript)
        assertEquals(0, r.store.saved.size)
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    // --- an unsure classifier asks, never guesses LOG ---------------------------------------

    @Test fun `an unsure classifier asks which of the four they meant and writes nothing`() {
        val r = rig(FakeLlm(intent = Outcome.Unavailable(UnavailableReason.BELOW_CONFIDENCE_THRESHOLD, "classifier said 'maybe'")))
        val events = r.run(UserIntent.Type("rice", en))
        assertEquals(listOf(OrchestratorEvent.NeedsIntent("rice")), events.filterIsInstance<OrchestratorEvent.NeedsIntent>())
        assertEquals(0, r.store.saved.size)
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    @Test fun `a certain log skips the classifier, a question never does`() {
        val r = rig(FakeLlm(intent = Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "must not be consulted")))
        assertTrue(r.run(UserIntent.Type("I had rice and dal", en)).any { it is OrchestratorEvent.MealLogged })
        val asked = r.run(UserIntent.Type("I had rice and dal, was that enough iron?", en))
        assertEquals("a marker sends it to the model, whose failure ends the turn", OrchestratorEvent.Failed(UnavailableReason.INTERNAL_ERROR, "must not be consulted"), asked.last())
        assertEquals(1, r.store.saved.size)
    }

    @Test fun `Resolve carries the person's own decision and skips the classifier`() {
        val r = rig(FakeLlm(intent = Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "must not be consulted")))
        val events = r.run(UserIntent.Resolve("rice and palak paneer, what should I add", en, SpokenIntent.SUGGEST))
        assertTrue("$events", events.any { it is OrchestratorEvent.Advice })
        assertEquals(0, r.store.saved.size)
    }

    // --- ANSWER and RECOMMEND carry the person's context ----------------------------------------

    @Test fun `ANSWER carries their meals, their reports, their conditions and their situation`() {
        val r = rig(FakeLlm(intent = Outcome.Ok(Intent.ANSWER)))
        val events = r.run(UserIntent.Type("did I get enough iron this week", en))

        val request = r.llm.answers.single()
        assertEquals(listOf("low iron"), request.declaredConditions)
        assertEquals(TriggerText.ENGLISH.lifeContext(LifeContext.HOSTEL_STUDENT), request.context)
        val figures = request.figures.map { it.text }
        assertTrue("the logged meal must be in the request: $figures", figures.any { it.contains("roti, dal") && it.contains("iron: 2.5 mg") })
        assertTrue("a partial total is worded as a floor: $figures", figures.any { it.contains("protein: at least 11 g (no value for dal)") })
        assertTrue("the lab value must be in the request: $figures", figures.any { it.contains("Haemoglobin: 9.8 g/dL, printed range 12 to 15") })
        assertTrue("the period total, computed by the store, must be in the request: $figures", figures.any { it.startsWith("The last seven days: iron: at least 4.6 mg") })
        assertTrue("the engine's own sentence about the value must be in the request: $figures", figures.any { it.contains("below") && it.contains("9.8") })
        assertEquals("retrieval ran on the question", listOf("iron.vitc"), request.facts.map { it.id })

        val answered = events.filterIsInstance<OrchestratorEvent.Answered>().single()
        assertEquals("answered", answered.text)
        assertEquals(listOf("iron.vitc"), answered.factIds)
        assertNull("no referral: haemoglobin at 9.8 is below range, not far below", answered.referral)
        assertEquals(0, r.store.saved.size)
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    @Test fun `RECOMMEND carries their conditions, situation, diet, report line and the allowed list`() {
        val r = rig(FakeLlm(intent = Outcome.Ok(Intent.RECOMMEND)))
        val events = r.run(UserIntent.Type("what should I eat for more iron", en))

        val request = r.llm.recommendations.single()
        assertEquals(listOf("low iron"), request.declaredConditions)
        assertEquals(TriggerText.ENGLISH.lifeContext(LifeContext.HOSTEL_STUDENT), request.context)
        assertEquals(listOf("meat, fish or eggs (vegetarian)"), request.constraints)
        assertNotNull("the rules engine's rendered sentence about their report", request.triggerText)
        assertTrue(request.triggerText!!.contains("9.8"))
        assertTrue("allowed foods come from the ranked list, never invented", request.allowedFoodNames.containsAll(listOf("thotakura", "sprouts")))
        assertEquals(false, request.referralFollows)

        val advice = events.filterIsInstance<OrchestratorEvent.Advice>().single()
        assertEquals("recommended", advice.phrased)
        assertTrue(advice.factIds.contains("iron.vitc"))
        assertEquals(0, r.store.saved.size)
    }

    @Test fun `a guard failure on RECOMMEND keeps the engine's own sentence and the ranked list`() {
        val r = rig(FakeLlm(intent = Outcome.Ok(Intent.RECOMMEND), recommended = Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "invented 18")))
        val events = r.run(UserIntent.Type("what should I eat for more iron", en))
        val advice = events.filterIsInstance<OrchestratorEvent.Advice>().single()
        assertNull(advice.phrased)
        assertNotNull(advice.evaluation.trigger)
        assertTrue(advice.evaluation.rankedCandidates.isNotEmpty())
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    // --- the referral is a fixed line, alongside the help ---------------------------------------

    @Test fun `a far-out-of-range value makes the referral a fixed line the model is told about`() {
        val r = rig(FakeLlm(intent = Outcome.Ok(Intent.RECOMMEND)), ctx = context(labs = listOf(glucoseFarAbove)))
        val events = r.run(UserIntent.Type("what should I eat", en))
        val request = r.llm.recommendations.single()
        assertTrue(request.referralFollows)
        val advice = events.filterIsInstance<OrchestratorEvent.Advice>().single()
        assertNotNull(advice.referral)
        assertTrue(advice.referral!!.contains("worth showing to a doctor"))
        assertEquals("the help is not dropped", "recommended", advice.phrased)
        assertTrue("the referral is spoken after the help", r.tts.spoken.single().endsWith(advice.referral!!))
    }

    @Test fun `a question asking for a clinical judgement gets the fixed referral line with no report on file`() {
        val r = rig(FakeLlm(intent = Outcome.Ok(Intent.ANSWER)), ctx = context(labs = emptyList()))
        val answered = r.run(UserIntent.Type("my haemoglobin is 7, is that dangerous", en)).filterIsInstance<OrchestratorEvent.Answered>().single()
        assertEquals(ContextText.ENGLISH.referral(), answered.referral)
        assertTrue("the model is told the line follows", r.llm.answers.single().referralFollows)
        // And an ordinary food question from a person who named their condition gets no such line.
        val plain = r.run(UserIntent.Type("I have low iron, what did I eat yesterday", en)).filterIsInstance<OrchestratorEvent.Answered>().single()
        assertNull(plain.referral)
    }

    @Test fun `an ANSWER every guard refused still reaches the person, with the referral`() {
        val r = rig(FakeLlm(intent = Outcome.Ok(Intent.ANSWER), answered = Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "invented 18")), ctx = context(labs = listOf(glucoseFarAbove)))
        val events = r.run(UserIntent.Type("how was my week", en))
        val answered = events.filterIsInstance<OrchestratorEvent.Answered>().single()
        assertNull(answered.text)
        assertEquals(UnavailableReason.INTERNAL_ERROR, answered.refused)
        assertNotNull("the referral stands whatever the model did", answered.referral)
        assertTrue("the person still gets their own lines: ${answered.figures}", answered.figures.any { it.contains("Fasting glucose: 260 mg/dL") })
        assertEquals(OrchestratorEvent.Completed, events.last())
        assertEquals("the referral is still spoken", listOf(answered.referral), r.tts.spoken)
    }

    @Test fun `ANSWER still carries the referral when the rules require one`() {
        val r = rig(FakeLlm(intent = Outcome.Ok(Intent.ANSWER)), ctx = context(labs = listOf(glucoseFarAbove)))
        val answered = r.run(UserIntent.Type("how was my week", en)).filterIsInstance<OrchestratorEvent.Answered>().single()
        assertNotNull(answered.referral)
        assertEquals("answered", answered.text)
        assertTrue(r.llm.answers.single().referralFollows)
    }

    // --- the voice front end ----------------------------------------------------------------

    @Test fun `Speak feeds the transcript into the same turn as Type`() {
        val asr = ScriptedAsr(
            listOf(
                AsrEvent.Level(0.2f), AsrEvent.SpeechStarted, AsrEvent.SpeechEnded, AsrEvent.Transcribing,
                AsrEvent.Result(Transcript("I had rice and palak paneer", SpeechLanguage.ENGLISH_INDIA, io.github.vedant7007.katori.ml.asr.AsrConfidence.LOW, 1800)),
            )
        )
        val r = Rig(llm = FakeLlm(intent = Outcome.Ok(Intent.LOG)), asr = asr, ctx = context(), facts = facts)
        val events = r.run(UserIntent.Speak(en))
        assertTrue(events.any { it is OrchestratorEvent.AudioLevel })
        assertEquals("I had rice and palak paneer", events.filterIsInstance<OrchestratorEvent.Transcribed>().single().text)
        val resolved = events.filterIsInstance<OrchestratorEvent.MealResolved>().single()
        assertTrue("low ASR confidence must reach the figure's reasons", resolved.meal.confidence.reasons.contains(ConfidenceReason.LOW_ASR_CONFIDENCE))
        assertEquals(1, r.store.saved.size)
    }

    @Test fun `a recogniser failure ends the turn with its reason and writes nothing`() {
        val asr = ScriptedAsr(listOf(AsrEvent.Unavailable(Outcome.Unavailable(UnavailableReason.PERMISSION_DENIED, "mic"))))
        val r = Rig(llm = FakeLlm(), asr = asr, ctx = context(), facts = facts)
        val events = r.run(UserIntent.Speak(en))
        assertEquals(OrchestratorEvent.Failed(UnavailableReason.PERMISSION_DENIED, "mic"), events.last())
        assertEquals(0, r.store.saved.size)
    }

    @Test fun `a path not built says so and never shows a sample`() {
        val events = rig().run(UserIntent.ScanLabReport)
        assertEquals(OrchestratorEvent.NotImplemented("orchestration.ScanLabReport"), events.first())
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    private companion object {
        fun figure(n: Nutrient, amount: Double, unit: NutrientUnit) = NutritionFigure(
            NutrientTotal(n, amount, unit, Completeness.COMPLETE, emptyList()),
            ConfidenceRules.of(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_STATED),
            listOf(DataSource.USDA_SR_LEGACY),
        )

        fun partial(n: Nutrient, amount: Double, unit: NutrientUnit, vararg unknown: String) = NutritionFigure(
            NutrientTotal(n, amount, unit, Completeness.PARTIAL, unknown.toList()),
            ConfidenceRules.of(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_STATED),
            listOf(DataSource.USDA_SR_LEGACY),
        )
    }
}
