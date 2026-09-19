package io.github.vedant7007.katori.orchestration

import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts
import io.github.vedant7007.katori.domain.ContextText
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
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.AsrConfidence
import io.github.vedant7007.katori.ml.asr.AsrEngine
import io.github.vedant7007.katori.ml.asr.AsrEvent
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import io.github.vedant7007.katori.ml.asr.Transcript
import io.github.vedant7007.katori.ml.llm.AnswerRequest
import io.github.vedant7007.katori.ml.llm.DisplayFigure
import io.github.vedant7007.katori.ml.llm.ExtractionRequest
import io.github.vedant7007.katori.ml.llm.Intent
import io.github.vedant7007.katori.ml.llm.LlmEngine
import io.github.vedant7007.katori.ml.llm.LogPrefilter
import io.github.vedant7007.katori.ml.llm.PhrasedText
import io.github.vedant7007.katori.ml.llm.PhrasingRequest
import io.github.vedant7007.katori.ml.llm.RecommendRequest
import io.github.vedant7007.katori.ml.tts.TtsEngine
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
 * ponytail: ASR and TTS acquire their own models and the LLM is leased per call, so the
 * three-model atomic acquisition the contract asks for is not done here. On the test device the
 * three together peak at 2,229.9 MB against a 4,190.7 MB ceiling (`0013`), so nothing is evicted
 * between stages in practice; a `withModels` lease around the whole turn is the upgrade when a
 * device is measured that needs it.
 */
class DefaultOrchestrator(
    private val asr: AsrEngine,
    private val llm: LlmLease,
    private val tts: TtsEngine,
    private val rules: RulesEngine,
    private val resolver: MealResolver,
    private val store: MealStore,
    private val contextSource: UserContextSource,
    private val knowledge: KnowledgeFacts,
    private val triggerText: TriggerText,
    private val contextText: ContextText,
    private val clock: Clock = Clock.systemUTC(),
) : Orchestrator {

    override fun handle(intent: UserIntent): Flow<OrchestratorEvent> = flow {
        when (intent) {
            is UserIntent.Speak -> spoken(intent.language)
            is UserIntent.Type -> turn(intent.text, intent.language, AsrConfidence.HIGH, decided = null)
            is UserIntent.Resolve -> turn(intent.text, intent.language, AsrConfidence.HIGH, decided = intent.intent)
            is UserIntent.AdviseOnMeal -> notBuilt("orchestration.AdviseOnMeal")
            is UserIntent.CorrectValue -> notBuilt("orchestration.CorrectValue")
            UserIntent.ScanLabReport -> notBuilt("orchestration.ScanLabReport")
            is UserIntent.ScanPackagedLabel -> notBuilt("orchestration.ScanPackagedLabel")
            is UserIntent.CheckExerciseForm -> notBuilt("orchestration.CheckExerciseForm")
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
     * [LogPrefilter] answers first, and only ever "certainly a log" or "ask the model": a
     * past-tense eating word with no question or advice marker skips the classifier's two
     * seconds of prompt processing. Anything with a marker in it goes to the model, so a
     * question cannot be short-circuited into a write.
     */
    private suspend fun FlowCollector<OrchestratorEvent>.turn(
        text: String, language: SpeechLanguageRef, heard: AsrConfidence, decided: SpokenIntent?,
    ) {
        emit(OrchestratorEvent.Transcribed(text))
        val intent = decided ?: SpokenIntent.LOG.takeIf { LogPrefilter.isCertainLog(text) } ?: run {
            emit(Progress(Stage.CLASSIFYING))
            when (val c = withLlm { it.classify(text, language.tag) }) {
                is Outcome.Ok -> c.value.toDomain()
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
        when (intent) {
            SpokenIntent.LOG -> plate(text, language, heard, save = true)
            SpokenIntent.SUGGEST -> plate(text, language, heard, save = false)
            SpokenIntent.ANSWER -> answer(text, language)
            SpokenIntent.RECOMMEND -> recommend(text, language)
        }
    }

    // --- LOG and SUGGEST: a plate, saved or hypothetical ---------------------------------------

    private suspend fun FlowCollector<OrchestratorEvent>.plate(
        text: String, language: SpeechLanguageRef, heard: AsrConfidence, save: Boolean,
    ) {
        emit(Progress(Stage.EXTRACTING))
        val extracted = when (val e = withLlm { it.extract(ExtractionRequest(text, language.tag)) }) {
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
                meal = MealSnapshot(mealId = mealId ?: 0L, items = resolved.items, loggedAt = now),
                candidates = ctx.candidates, evaluatedAt = now,
            )
        )
        val trigger = evaluation.trigger?.let(triggerText::render)
        val referral = trigger.takeIf { evaluation.referralRequired }

        emit(Progress(Stage.PHRASING))
        val phrased = withLlm {
            it.phrase(
                PhrasingRequest(
                    evaluation = evaluation, triggerText = trigger,
                    figures = resolved.figures.map { f -> DisplayFigure(contextText.figure(f)) },
                    languageTag = language.tag,
                    allowedFoodNames = evaluation.rankedCandidates.map { c -> c.candidate.displayName },
                )
            )
        }.textOrNull()
        emit(OrchestratorEvent.Advice(evaluation, phrased, referral))
        speak(listOfNotNull(phrased ?: trigger, referral.takeIf { phrased != null }).joinToString(" "), language)
        emit(OrchestratorEvent.Completed)
    }

    // --- ANSWER: their diary and their reports, in the request ----------------------------------

    private suspend fun FlowCollector<OrchestratorEvent>.answer(text: String, language: SpeechLanguageRef) {
        emit(Progress(Stage.EVALUATING_RULES))
        val now = clock.instant()
        val ctx = contextSource.current()
        // Rules before prose, always: the referral is decided by the engine, not by the question.
        val evaluation = rules.evaluate(
            RuleInput(ctx.profile, ctx.declaredConditions, ctx.labValues, meal = null, candidates = ctx.candidates, evaluatedAt = now)
        )
        val referral = evaluation.trigger?.let(triggerText::render).takeIf { evaluation.referralRequired }

        emit(Progress(Stage.RETRIEVING_FACTS))
        val declared = ctx.declaredConditions.map { it.name }
        val locale = Locale.forLanguageTag(language.tag)
        val facts = knowledge.find((listOf(text) + declared).joinToString(" "))
        val request = AnswerRequest(
            question = text, languageTag = language.tag, declaredConditions = declared, context = situation(ctx),
            figures = ctx.recentMeals.map { DisplayFigure(contextText.meal(it, locale)) } +
                ctx.labValues.map { DisplayFigure(contextText.lab(it)) },
            facts = facts,
        )

        emit(Progress(Stage.PHRASING))
        val answered = when (val a = withLlm { it.answer(request) }) {
            is Outcome.Ok -> a.value.text
            // There is no sentence of our own to fall back to on a question; say we could not.
            is Outcome.Unavailable -> return fail(a.reason, a.detail)
            is Outcome.NotImplemented -> return notBuilt(a.component)
        }
        emit(OrchestratorEvent.Answered(answered, facts.map { it.id }, referral))
        speak(listOfNotNull(answered, referral).joinToString(" "), language)
        emit(OrchestratorEvent.Completed)
    }

    // --- RECOMMEND: their profile, their reports, the allowed list, in the request ---------------

    private suspend fun FlowCollector<OrchestratorEvent>.recommend(text: String, language: SpeechLanguageRef) {
        emit(Progress(Stage.EVALUATING_RULES))
        val now = clock.instant()
        val ctx = contextSource.current()
        val evaluation = rules.evaluate(
            RuleInput(ctx.profile, ctx.declaredConditions, ctx.labValues, meal = null, candidates = ctx.candidates, evaluatedAt = now)
        )
        val trigger = evaluation.trigger?.let(triggerText::render)
        val referral = trigger.takeIf { evaluation.referralRequired }

        emit(Progress(Stage.RETRIEVING_FACTS))
        val declared = ctx.declaredConditions.map { it.name }
        val allowed = evaluation.rankedCandidates.map { it.candidate.displayName }
        val facts = knowledge.find((listOf(text) + declared + allowed).joinToString(" "))
        val request = RecommendRequest(
            request = text, languageTag = language.tag, declaredConditions = declared, context = situation(ctx),
            constraints = listOfNotNull(ctx.profile.dietType?.let(contextText::neverSuggest)),
            triggerText = trigger, facts = facts, allowedFoodNames = allowed, referralFollows = referral != null,
        )

        emit(Progress(Stage.PHRASING))
        // A guard failure here is not a failure of the turn: the ranked list and the trigger
        // sentence are the engine's own and need no model. The prose is what is lost.
        val phrased = withLlm { it.recommend(request) }.textOrNull()
        emit(OrchestratorEvent.Advice(evaluation, phrased, referral, facts.map { it.id }))
        speak(listOfNotNull(phrased ?: trigger, referral.takeIf { phrased != null }).joinToString(" "), language)
        emit(OrchestratorEvent.Completed)
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun situation(ctx: UserContext): String? = ctx.profile.context?.let(contextText::lifeContext)

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

    private fun Intent.toDomain(): SpokenIntent = when (this) {
        Intent.LOG -> SpokenIntent.LOG
        Intent.ANSWER -> SpokenIntent.ANSWER
        Intent.SUGGEST -> SpokenIntent.SUGGEST
        Intent.RECOMMEND -> SpokenIntent.RECOMMEND
    }
}
