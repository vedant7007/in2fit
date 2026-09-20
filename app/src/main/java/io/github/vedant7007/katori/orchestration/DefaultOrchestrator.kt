package io.github.vedant7007.katori.orchestration

import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts
import io.github.vedant7007.katori.domain.AdviceStore
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.Evidence
import io.github.vedant7007.katori.domain.LabStore
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.RuleEvaluation
import io.github.vedant7007.katori.domain.StoredAdvice
import io.github.vedant7007.katori.domain.MealResolver
import io.github.vedant7007.katori.domain.MealSnapshot
import io.github.vedant7007.katori.domain.MealStore
import io.github.vedant7007.katori.domain.Orchestrator
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.OrchestratorEvent.Progress
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.ResolvedMeal
import io.github.vedant7007.katori.domain.RuleInput
import io.github.vedant7007.katori.domain.RulesEngine
import io.github.vedant7007.katori.domain.SpeechLanguageRef
import io.github.vedant7007.katori.domain.SpokenIntent
import io.github.vedant7007.katori.domain.Stage
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserContext
import io.github.vedant7007.katori.domain.UserContextSource
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.AsrConfidence
import io.github.vedant7007.katori.ml.asr.AsrEngine
import io.github.vedant7007.katori.ml.asr.AsrEvent
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import io.github.vedant7007.katori.ml.asr.Transcript
import io.github.vedant7007.katori.ml.llm.AnswerLength
import io.github.vedant7007.katori.ml.llm.AnswerRequest
import io.github.vedant7007.katori.ml.llm.DisplayFigure
import io.github.vedant7007.katori.ml.llm.ExtractionRequest
import io.github.vedant7007.katori.ml.llm.Intent
import io.github.vedant7007.katori.ml.llm.IntentRouter
import io.github.vedant7007.katori.ml.llm.LlmEngine
import io.github.vedant7007.katori.ml.llm.PhrasedText
import io.github.vedant7007.katori.ml.llm.PhrasingRequest
import io.github.vedant7007.katori.ml.llm.RecommendRequest
import io.github.vedant7007.katori.ml.llm.SafetyLine
import io.github.vedant7007.katori.ml.tts.TtsEngine
import io.github.vedant7007.katori.ml.tts.withSpokenLeadIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.util.Locale

/**
 * Leases the language model for one turn. Production wraps `ModelArbiter.withModel` and builds a
 * `LlamaCppLlmEngine` over the admitted runtime; a test hands in a scripted engine. Keeping the
 * lease outside the orchestrator is what lets the routing be tested without a model file.
 */
interface LlmLease {
    suspend fun <T> use(block: suspend (LlmEngine) -> T): Outcome<T>
}

/**
 * [Orchestrator] for the four spoken intents of `0015`, and the seams for the rest.
 *
 * Read the contract on [Orchestrator] first; this file is the order of operations and nothing
 * else. Two properties are enforced by the tests rather than by hoping:
 *
 *  - SUGGEST never writes. [MealStore.save] is reachable from [plate] only when `save` is true,
 *    and `save` is true only on LOG.
 *  - ANSWER and RECOMMEND read [UserContextSource.current] before the model is called and put
 *    the rendered context in the request. There is no code path to either prompt that skips it.
 *
 * ponytail: DEFERRED, NOT SATISFIED. The contract requires ASR, LLM and TTS to be acquired
 * atomically for the whole turn; here ASR and TTS acquire their own models and the LLM is
 * leased per call, which leaves the eviction gaps the contract forbids. It is deferred because
 * the one device measured has headroom (three models peak at 2,229.9 MB against a 4,190.7 MB
 * ceiling, `0013`), and that is device-specific reasoning against a requirement written for the
 * devices not yet tested. The upgrade is a `ModelArbiter.withModels` lease around [turn]; it
 * becomes due the day a device is measured without that headroom, or earlier if a gap is seen.
 */
class DefaultOrchestrator(
    private val asr: AsrEngine,
    private val llm: LlmLease,
    private val tts: TtsEngine,
    private val rules: RulesEngine,
    private val resolver: MealResolver,
    private val store: MealStore,
    private val advice: AdviceStore,
    private val labs: LabStore,
    private val contextSource: UserContextSource,
    private val knowledge: KnowledgeFacts,
    private val triggerText: TriggerText,
    private val contextText: ContextText,
    private val clock: Clock = Clock.systemUTC(),
    /** The product decision on answer length (`0024`, measured in `0014`). One flip, here. */
    private val answerLength: AnswerLength = AnswerLength.SHORT,
) : Orchestrator {

    override fun handle(intent: UserIntent): Flow<OrchestratorEvent> = flow {
        when (intent) {
            is UserIntent.Speak -> spoken(intent.language)
            is UserIntent.Type -> turn(intent.text, intent.language, AsrConfidence.HIGH, decided = null)
            is UserIntent.Resolve -> turn(intent.text, intent.language, AsrConfidence.HIGH, decided = intent.intent)
            is UserIntent.AdviseOnMeal -> adviseOnMeal(intent.mealId)
            is UserIntent.SaveLabReport -> saveLabReport(intent.values)
            is UserIntent.CorrectValue -> notBuilt("orchestration.CorrectValue")
            UserIntent.ScanLabReport -> notBuilt("orchestration.ScanLabReport")
            is UserIntent.ScanPackagedLabel -> notBuilt("orchestration.ScanPackagedLabel")
            is UserIntent.CheckExerciseForm -> notBuilt("orchestration.CheckExerciseForm")
            UserIntent.StopSpeaking -> { tts.stop(); emit(OrchestratorEvent.Completed) }
        }
    }

    // --- the voice front end -------------------------------------------------------------------

    private suspend fun FlowCollector<OrchestratorEvent>.spoken(language: SpeechLanguageRef) {
        val lang = speechLanguage(language) ?: return fail(UnavailableReason.INPUT_NOT_USABLE, "no speech language for '${language.tag}'")
        emit(Progress(Stage.RECORDING))
        var result: Transcript? = null
        var failure: Outcome.Unavailable? = null
        asr.listen(lang).collect { ev ->
            when (ev) {
                is AsrEvent.Level -> emit(OrchestratorEvent.AudioLevel(ev.rms))
                AsrEvent.SpeechEnded -> emit(Progress(Stage.TRANSCRIBING))
                is AsrEvent.Result -> result = ev.transcript
                is AsrEvent.Unavailable -> failure = ev.outcome
                AsrEvent.SpeechStarted, AsrEvent.Transcribing -> Unit
            }
        }
        failure?.let { return fail(it.reason, it.detail) }
        val transcript = result ?: return fail(UnavailableReason.INTERNAL_ERROR, "the recogniser ended without a result")
        turn(transcript.text, language, transcript.confidence, decided = null)
    }

    /**
     * One turn: classify unless the person already told us, then route.
     *
     * [IntentRouter] answers first (`0027`): the words decide a certain log, a question about
     * what they ate, a plate in front of them or a request for what to eat, and the model is
     * consulted only when the evidence is silent or conflicts; its LOG verdict on a question is
     * refused. A decided turn skips the classifier's 6-8 s, and a question can never be
     * short-circuited into a write.
     */
    private suspend fun FlowCollector<OrchestratorEvent>.turn(
        text: String, language: SpeechLanguageRef, heard: AsrConfidence, decided: SpokenIntent?,
    ) {
        emit(OrchestratorEvent.Transcribed(text))
        // THE WORDS DECIDE WHERE THEY CAN (IntentRouter, 0027): a certain log, a question about
        // what they ate, a plate in front of them, a request for what to eat. The model is asked
        // only when the evidence is silent or conflicts, and its LOG verdict on any question is
        // refused, so a question can never write a meal into the diary.
        val routed = if (decided == null) IntentRouter.decide(text) else null
        val certainLog = decided == null && routed is IntentRouter.Decision.Decided && routed.intent == Intent.LOG
        // MEASURED on the first end-to-end run (20 Sep): the classifier alone is 6-8 s, so
        // figures emitted after it are not "under a second". For anything that is not certainly
        // a log the person's own figures go on screen NOW, before the model decides what the
        // turn is: for "how much iron this week" they are the answer, and for the rest they are
        // the context the answer will be about. The route re-reads the store; Room is milliseconds.
        if (!certainLog && decided != SpokenIntent.LOG) {
            val ctx = contextSource.current()
            emit(OrchestratorEvent.OwnFigures(ownLines(ctx, language)))
        }
        val intent = decided ?: (routed as? IntentRouter.Decision.Decided)?.intent?.toDomain() ?: run {
            emit(Progress(Stage.CLASSIFYING))
            when (val c = withLlm { it.classify(text, language.tag) }) {
                is Outcome.Ok -> IntentRouter.accept(c.value, text)?.toDomain() ?: run {
                    // The model said something the words refuse (a LOG on a question, or an
                    // intent the evidence ruled out). Ask the person; never guess.
                    emit(OrchestratorEvent.NeedsIntent(text))
                    emit(OrchestratorEvent.Completed)
                    return
                }
                is Outcome.Unavailable -> {
                    // Not sure is not "probably LOG". A guessed LOG writes to the timeline.
                    if (c.reason == UnavailableReason.BELOW_CONFIDENCE_THRESHOLD) {
                        emit(OrchestratorEvent.NeedsIntent(text))
                        emit(OrchestratorEvent.Completed)
                    } else {
                        fail(c.reason, c.detail)
                    }
                    return
                }
                is Outcome.NotImplemented -> return notBuilt(c.component)
            }
        }
        // The intent is known; the screen shows it as a heading and the lead-in is spoken over
        // whatever follows, so the wait reads as work. The lead-in is a status phrase from the
        // string table (`tts_lead_in_*`), never health content.
        val leadIn = contextText.leadIn(intent).takeIf { speechLanguage(language)?.let { l -> l in tts.supportedLanguages } == true }
        emit(OrchestratorEvent.IntentKnown(intent, leadIn))
        when (intent) {
            SpokenIntent.LOG -> plate(text, language, heard, save = true, leadIn)
            SpokenIntent.SUGGEST -> plate(text, language, heard, save = false, leadIn)
            SpokenIntent.ANSWER -> answer(text, language, leadIn)
            SpokenIntent.RECOMMEND -> recommend(text, language, leadIn)
        }
    }

    /**
     * Run one model call with the lead-in phrase spoken over it (Meera's `withSpokenLeadIn`):
     * the phrase starts as generation starts, and the call returns only after the phrase has
     * finished, so the answer never talks over it. With no lead-in, or no voice for the
     * language, the call runs alone.
     */
    private suspend fun <T> withLlmSpoken(leadIn: String?, language: SpeechLanguageRef, block: suspend (LlmEngine) -> Outcome<T>): Outcome<T> {
        val lang = speechLanguage(language)
        return if (leadIn == null || lang == null) withLlm(block) else tts.withSpokenLeadIn(leadIn, lang) { withLlm(block) }
    }

    // --- LOG and SUGGEST: a plate, saved or hypothetical ---------------------------------------

    private suspend fun FlowCollector<OrchestratorEvent>.plate(
        text: String, language: SpeechLanguageRef, heard: AsrConfidence, save: Boolean, leadIn: String?,
    ) {
        emit(Progress(Stage.EXTRACTING))
        val extracted = when (val e = withLlmSpoken(leadIn, language) { it.extract(ExtractionRequest(text, language.tag)) }) {
            is Outcome.Ok -> e.value
            is Outcome.Unavailable -> return fail(e.reason, e.detail)
            is Outcome.NotImplemented -> return notBuilt(e.component)
        }
        val parsed = ParsedMeal(
            items = extracted.items.map {
                ParsedItem(
                    spokenName = it.name, quantity = it.quantity, unit = it.unit, matchedFoodCode = null,
                    confidence = ConfidenceRules.of(
                        listOfNotNull(
                            if (it.quantity == null) ConfidenceReason.QUANTITY_INFERRED else ConfidenceReason.QUANTITY_STATED,
                            ConfidenceReason.LOW_ASR_CONFIDENCE.takeIf { heard == AsrConfidence.LOW },
                        ),
                    ),
                )
            },
            confidence = ConfidenceRules.of(
                listOfNotNull(
                    if (extracted.items.any { it.quantity == null }) ConfidenceReason.QUANTITY_INFERRED else ConfidenceReason.QUANTITY_STATED,
                    ConfidenceReason.LOW_ASR_CONFIDENCE.takeIf { heard == AsrConfidence.LOW },
                ),
            ),
            rawTranscript = text,
        )

        emit(Progress(Stage.MATCHING_FOODS))
        val resolved: ResolvedMeal = when (val r = resolver.resolve(parsed, language.tag)) {
            is Outcome.Ok -> r.value
            is Outcome.Unavailable -> when (r.reason) {
                // The plate is not understood well enough to act on. Ask; do not save, do not guess.
                UnavailableReason.NO_MATCH, UnavailableReason.KNOWN_ITEM_NO_DATA, UnavailableReason.BELOW_CONFIDENCE_THRESHOLD -> {
                    emit(OrchestratorEvent.NeedsConfirmation(r.reason, parsed))
                    emit(OrchestratorEvent.Completed)
                    return
                }
                else -> return fail(r.reason, r.detail)
            }
            is Outcome.NotImplemented -> return notBuilt(r.component)
        }
        emit(OrchestratorEvent.MealResolved(resolved.parsed, resolved.figures, hypothetical = !save))

        val now = clock.instant()
        // THE ONLY WRITE. `save` is true on LOG and false on SUGGEST; a hypothetical plate never
        // reaches the store, whatever else happens below.
        val mealId: Long? = if (save) {
            emit(Progress(Stage.SAVING))
            when (val s = store.save(resolved, now)) {
                is Outcome.Ok -> s.value.also { emit(OrchestratorEvent.MealLogged(it)) }
                is Outcome.Unavailable -> return fail(s.reason, s.detail)
                is Outcome.NotImplemented -> return notBuilt(s.component)
            }
        } else {
            null
        }

        emit(Progress(Stage.EVALUATING_RULES))
        val ctx = contextSource.current()
        val evaluation = rules.evaluate(
            RuleInput(
                profile = ctx.profile, declaredConditions = ctx.declaredConditions, labValues = ctx.labValues,
                // 0 is never a Room row id. A hypothetical plate has no id and never gets one.
                meal = MealSnapshot(mealId = mealId ?: 0L, items = resolved.items.map { it.snapshot }, loggedAt = now),
                candidates = ctx.candidates, evaluatedAt = now,
            )
        )
        val trigger = evaluation.trigger?.let(triggerText::render)
        val referral = trigger.takeIf { evaluation.referralRequired }

        emit(Progress(Stage.PHRASING))
        val phrased = withLlmSpoken(leadIn, language) {
            it.phrase(
                PhrasingRequest(
                    evaluation = evaluation, triggerText = trigger,
                    figures = resolved.figures.map { f -> DisplayFigure(contextText.figure(f)) },
                    languageTag = language.tag,
                    allowedFoodNames = evaluation.rankedCandidates.take(ALLOWED_FOODS).map { c -> c.candidate.displayName },
                )
            )
        }.textOrNull()
        // PRECOMPUTED: the advice is stored against the digest it was phrased under, so beat 4
        // reads it back without a model until something in the context changes.
        if (mealId != null) advice.save(mealId, StoredAdvice(phrased, trigger, evaluation.trigger?.ruleId, evaluation.inputDigest, now))
        emit(OrchestratorEvent.Advice(evaluation, phrased, referral))
        speak(listOfNotNull(phrased ?: trigger, referral.takeIf { phrased != null }).joinToString(" "), language)
        emit(OrchestratorEvent.Completed)
    }

    // --- beat 4: advice for a logged meal, precomputed ------------------------------------------

    /**
     * Instant when the stored advice was phrased under the digest the current context yields;
     * otherwise phrase now and store. The rules engine runs either way, so the ranked list and
     * the trigger sentence are always the current ones; what the store saves is the prose.
     */
    private suspend fun FlowCollector<OrchestratorEvent>.adviseOnMeal(mealId: Long, language: SpeechLanguageRef = SpeechLanguageRef("en-IN")) {
        val meal = store.meal(mealId) ?: return fail(UnavailableReason.NO_MATCH, "no meal $mealId")
        emit(Progress(Stage.EVALUATING_RULES))
        val (evaluation, trigger, referral) = evaluateMeal(meal)
        val stored = advice.latest(mealId)
        val phrased = if (stored != null && stored.inputDigest == evaluation.inputDigest) {
            stored.phrased
        } else {
            regenerate(mealId, meal, evaluation, trigger, language)
        }
        emit(OrchestratorEvent.Advice(evaluation, phrased, referral))
        speak(listOfNotNull(phrased ?: trigger, referral.takeIf { phrased != null }).joinToString(" "), language)
        emit(OrchestratorEvent.Completed)
    }

    /**
     * Beat 3's write, and the regeneration the ruling asks for: the save is what changes the
     * advice, so the save is what regenerates it, inside the scan's own pause, for the last
     * meal logged. The next [adviseOnMeal] is then a digest match and instant.
     */
    private suspend fun FlowCollector<OrchestratorEvent>.saveLabReport(values: List<LabValue>) {
        if (values.isEmpty()) return fail(UnavailableReason.INPUT_NOT_USABLE, "no values to save")
        emit(Progress(Stage.SAVING))
        val count = when (val s = labs.save(values)) {
            is Outcome.Ok -> s.value
            is Outcome.Unavailable -> return fail(s.reason, s.detail)
            is Outcome.NotImplemented -> return notBuilt(s.component)
        }
        val mealId = store.latestMealId()
        val meal = mealId?.let { store.meal(it) }
        if (mealId != null && meal != null) {
            emit(Progress(Stage.EVALUATING_RULES))
            val (evaluation, trigger, _) = evaluateMeal(meal)
            if (advice.latest(mealId)?.inputDigest != evaluation.inputDigest) {
                emit(Progress(Stage.PHRASING))
                regenerate(mealId, meal, evaluation, trigger, SpeechLanguageRef("en-IN"))
            }
        }
        emit(OrchestratorEvent.LabReportSaved(count, mealId.takeIf { meal != null }))
        emit(OrchestratorEvent.Completed)
    }

    private suspend fun evaluateMeal(meal: MealSnapshot): Triple<RuleEvaluation, String?, String?> {
        val ctx = contextSource.current()
        val evaluation = rules.evaluate(
            RuleInput(ctx.profile, ctx.declaredConditions, ctx.labValues, meal = meal, candidates = ctx.candidates, evaluatedAt = clock.instant())
        )
        val trigger = evaluation.trigger?.let(triggerText::render)
        return Triple(evaluation, trigger, trigger.takeIf { evaluation.referralRequired })
    }

    private suspend fun regenerate(mealId: Long, meal: MealSnapshot, evaluation: RuleEvaluation, trigger: String?, language: SpeechLanguageRef): String? {
        val figures = meal.items.flatMap { it.nutrients.entries }.groupBy({ it.key }, { it.value })
            .map { (n, amounts) -> "${TriggerText.ENGLISH.nutrient(n)}: ${amounts.sum()}" }
        val phrased = withLlm {
            it.phrase(
                PhrasingRequest(
                    evaluation = evaluation, triggerText = trigger, figures = figures.map(::DisplayFigure),
                    languageTag = language.tag,
                    allowedFoodNames = evaluation.rankedCandidates.take(ALLOWED_FOODS).map { c -> c.candidate.displayName },
                )
            )
        }.textOrNull()
        advice.save(mealId, StoredAdvice(phrased, trigger, evaluation.trigger?.ruleId, evaluation.inputDigest, clock.instant()))
        return phrased
    }

    // --- ANSWER: their diary and their reports, in the request ----------------------------------

    private suspend fun FlowCollector<OrchestratorEvent>.answer(text: String, language: SpeechLanguageRef, leadIn: String?) {
        emit(Progress(Stage.EVALUATING_RULES))
        val now = clock.instant()
        val ctx = contextSource.current()
        // Rules before prose, always: the referral is decided by the engine, not by the question.
        val evaluation = rules.evaluate(
            RuleInput(ctx.profile, ctx.declaredConditions, ctx.labValues, meal = null, candidates = ctx.candidates, evaluatedAt = now)
        )
        val referral = referralFor(text, evaluation)
        // MEASURED, 20 Sep: with only per-meal figures the model summed two meals' iron itself
        // and the guard refused it, and it read a below-range haemoglobin line as "within the
        // normal range". So the period totals go in, computed by the store, and the rules
        // engine's own sentence about the value goes in with them, in words the model can
        // repeat rather than a range it has to compare.
        // The person's lines went out on OwnFigures before classification; with the engine's
        // sentence now known they are complete, and they ride on Answered.figures.
        val lines = ownLines(ctx, language) + listOfNotNull(evaluation.trigger?.let { triggerText.render(it) })

        emit(Progress(Stage.RETRIEVING_FACTS))
        val declared = ctx.declaredConditions.map { it.name }
        // THE MODEL IS NOT GIVEN A CHOICE (ruled 20 Sep, 0014). Code selects: the period-total
        // lines for the nutrients the question names (or every period line when it names none),
        // the engine's sentence if one fired, and at most two rows keyed by the question and the
        // fired rules. The person's full lines went out on OwnFigures already.
        val asked = nutrientsNamed(text)
        val periodLines = ctx.periodTotals.map { p ->
            contextText.period(if (asked.isEmpty()) p else p.copy(figures = p.figures.filter { it.total.nutrient in asked }))
        }.filter { it.isNotBlank() }
        val given = periodLines + listOfNotNull(evaluation.trigger?.let { triggerText.render(it) })
        val facts = knowledge.find((listOf(text) + declared + ruleWords(evaluation)).joinToString(" "), limit = FACT_ROWS)
        val request = AnswerRequest(
            question = text, languageTag = language.tag, declaredConditions = declared, context = situation(ctx),
            figures = given.map(::DisplayFigure),
            facts = facts,
            referralFollows = referral != null,
        )

        emit(Progress(Stage.PHRASING))
        val answered = when (val a = withLlmSpoken(leadIn, language) { it.answer(request, answerLength) }) {
            is Outcome.Ok -> OrchestratorEvent.Answered(a.value.text, facts.map { it.id }, referral, figures = lines)
            // A guard refused every attempt. The person still gets a turn: the UI's own
            // `answer_refused` line, their own figures, and the referral. Never nothing (0024).
            is Outcome.Unavailable -> OrchestratorEvent.Answered(null, facts.map { it.id }, referral, refused = a.reason, figures = lines)
            is Outcome.NotImplemented -> return notBuilt(a.component)
        }
        emit(answered)
        speak(listOfNotNull(answered.text, referral).joinToString(" "), language)
        emit(OrchestratorEvent.Completed)
    }

    /**
     * The referral line, fixed, never generated: the engine's rendered sentence when a value on
     * file requires one; the string table's line when the QUESTION asks for a clinical judgement
     * and there is no report to render a sentence about; null otherwise.
     */
    private fun referralFor(text: String, evaluation: io.github.vedant7007.katori.domain.RuleEvaluation): String? =
        evaluation.trigger?.let(triggerText::render).takeIf { evaluation.referralRequired }
            ?: contextText.referral().takeIf { SafetyLine.invitesClinicalJudgement(text) }

    // --- RECOMMEND: their profile, their reports, the allowed list, in the request ---------------

    private suspend fun FlowCollector<OrchestratorEvent>.recommend(text: String, language: SpeechLanguageRef, leadIn: String?) {
        emit(Progress(Stage.EVALUATING_RULES))
        val now = clock.instant()
        val ctx = contextSource.current()
        val evaluation = rules.evaluate(
            RuleInput(ctx.profile, ctx.declaredConditions, ctx.labValues, meal = null, candidates = ctx.candidates, evaluatedAt = now)
        )
        val trigger = evaluation.trigger?.let(triggerText::render)
        val referral = referralFor(text, evaluation)

        emit(Progress(Stage.RETRIEVING_FACTS))
        val declared = ctx.declaredConditions.map { it.name }
        // Code selects (0014): the top few ranked foods, and at most two rows keyed by the
        // question, the declared conditions and what the fired rules are about; never the whole
        // allowed list as retrieval text, which is what buried the row a question most needed.
        val allowed = evaluation.rankedCandidates.take(ALLOWED_FOODS).map { it.candidate.displayName }
        val facts = knowledge.find((listOf(text) + declared + ruleWords(evaluation)).joinToString(" "), limit = FACT_ROWS)
        val request = RecommendRequest(
            request = text, languageTag = language.tag, declaredConditions = declared, context = situation(ctx),
            constraints = listOfNotNull(ctx.profile.dietType?.let(contextText::neverSuggest)),
            triggerText = trigger, facts = facts, allowedFoodNames = allowed, referralFollows = referral != null,
        )

        emit(Progress(Stage.PHRASING))
        // A guard failure here is not a failure of the turn: the ranked list and the trigger
        // sentence are the engine's own and need no model. The prose is what is lost.
        val phrased = withLlmSpoken(leadIn, language) { it.recommend(request, answerLength) }.textOrNull()
        emit(OrchestratorEvent.Advice(evaluation, phrased, referral, facts.map { it.id }))
        speak(listOfNotNull(phrased ?: trigger, referral.takeIf { phrased != null }).joinToString(" "), language)
        emit(OrchestratorEvent.Completed)
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun situation(ctx: UserContext): String? = ctx.profile.context?.let(contextText::lifeContext)

    /** The person's own lines: period totals, recent meals, lab values. What goes on screen first. */
    private fun ownLines(ctx: UserContext, language: SpeechLanguageRef): List<String> {
        val locale = Locale.forLanguageTag(language.tag)
        return ctx.periodTotals.map { contextText.period(it) } +
            ctx.recentMeals.map { contextText.meal(it, locale) } +
            ctx.labValues.map { contextText.lab(it) }
    }

    /** The nutrients a question names, by the English word or the locale's, whole-word. */
    private fun nutrientsNamed(text: String): Set<Nutrient> {
        val lower = " " + text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ") + " "
        return Nutrient.entries.filterTo(mutableSetOf()) { n ->
            listOf(TriggerText.ENGLISH.nutrient(n), contextText.nutrientWord(n)).any { w -> lower.contains(" ${w.lowercase()} ") }
        }
    }

    /** What the fired rules are about, as retrieval words: test names, condition names, nutrient words. */
    private fun ruleWords(evaluation: RuleEvaluation): List<String> = evaluation.firedRules.mapNotNull { r ->
        when (val e = r.evidence) {
            is Evidence.LabValueOutsideRange -> e.testName
            is Evidence.UserDeclaredCondition -> e.condition.name
            is Evidence.MealComposition -> TriggerText.ENGLISH.nutrient(e.nutrient)
            is Evidence.ProfileContext, is Evidence.TimelinePattern -> null
        }
    }

    /** Lease the model and run one call, flattening "could not lease" and "call failed" into one outcome. */
    private suspend fun <T> withLlm(block: suspend (LlmEngine) -> Outcome<T>): Outcome<T> =
        when (val leased = llm.use(block)) {
            is Outcome.Ok -> leased.value
            is Outcome.Unavailable -> leased
            is Outcome.NotImplemented -> leased
        }

    private fun Outcome<PhrasedText>.textOrNull(): String? =
        (this as? Outcome.Ok)?.value?.takeIf { it.numericGuardPassed }?.text

    /**
     * Spoken confirmation is best effort by the TTS contract: on any failure the text is already
     * on screen and stays there, so a TTS outcome is not a failure of the turn and is not
     * surfaced as one. Silently skipped when the language has no voice.
     */
    private suspend fun FlowCollector<OrchestratorEvent>.speak(text: String, language: SpeechLanguageRef) {
        if (text.isBlank()) return
        val lang = speechLanguage(language)?.takeIf { it in tts.supportedLanguages } ?: return
        emit(Progress(Stage.SPEAKING))
        tts.speak(text, lang)
    }

    private suspend fun FlowCollector<OrchestratorEvent>.fail(reason: UnavailableReason, detail: String?) =
        emit(OrchestratorEvent.Failed(reason, detail))

    private suspend fun FlowCollector<OrchestratorEvent>.notBuilt(component: String) {
        emit(OrchestratorEvent.NotImplemented(component))
        emit(OrchestratorEvent.Completed)
    }

    private fun speechLanguage(ref: SpeechLanguageRef): SpeechLanguage? =
        SpeechLanguage.entries.firstOrNull { it.tag.equals(ref.tag, ignoreCase = true) }
            ?: SpeechLanguage.entries.firstOrNull { ref.tag.substringBefore('-').equals(it.tag.substringBefore('-'), ignoreCase = true) }

    private companion object {
        /** Rows the model is given: the one or two the question and the fired rules point at. */
        const val FACT_ROWS = 2
        /** Foods the model may name: the top of the ranked list, not the whole of it. */
        const val ALLOWED_FOODS = 4
    }

    private fun Intent.toDomain(): SpokenIntent = when (this) {
        Intent.LOG -> SpokenIntent.LOG
        Intent.ANSWER -> SpokenIntent.ANSWER
        Intent.SUGGEST -> SpokenIntent.SUGGEST
        Intent.RECOMMEND -> SpokenIntent.RECOMMEND
    }
}
