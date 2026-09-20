package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Confidence
import io.github.vedant7007.katori.domain.model.NutritionFigure
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.flow.Flow

/**
 * Routes a user intent to the pipelines that serve it, and is the only place where pipelines meet.
 *
 * CONTRACT
 * - Every input type resolves into a structured write to the local store, and every output is
 *   generated from that store. No pipeline talks directly to another (spec 8.1). ASR does not call
 *   the LLM; the orchestrator does.
 * - Emits a [Flow] of [OrchestratorEvent] rather than returning a single result, because perceived
 *   responsiveness is the design answer to having no streaming transcripts (spec 10.5 ruling). The
 *   UI must render progress from these events, not a generic spinner.
 * - Every flow terminates in exactly one terminal event: [OrchestratorEvent.Completed] or
 *   [OrchestratorEvent.Failed]. A flow that ends without one is a bug.
 * - The orchestrator owns ordering: rules are evaluated BEFORE the LLM phrases anything, always.
 *   There is no path where generated prose reaches the user without a [RuleEvaluation] behind it.
 * - The orchestrator owns MODEL ACQUISITION for a round trip, and acquires atomically. The voice
 *   path takes ASR, LLM and TTS together through ModelArbiter.withModels before it starts, and holds
 *   them for the whole round trip. Acquiring them one stage at a time leaves gaps in which eviction
 *   forces a reload, and one reload is the whole 3.5 s budget for beat 1.
 * - If the device cannot hold that set at once, the orchestrator degrades the round trip rather than
 *   splitting the lease: it drops spoken confirmation to on-screen confirmation and says so. Which
 *   devices this applies to is established by measurement, not assumed.
 *
 * THE FOUR SPOKEN INTENTS (`docs/decisions/0015`)
 * A spoken or typed turn is not a log entry until the model says so. [UserIntent.Speak] and
 * [UserIntent.Type] carry no intent of their own; the orchestrator classifies the utterance into
 * one [SpokenIntent] first and then routes:
 *
 *  - LOG        the person ate something. Extract, resolve, compute, SAVE, evaluate rules, phrase.
 *  - SUGGEST    the person is ABOUT to eat something and asks what to add or change. Extract,
 *               resolve, compute and evaluate exactly as LOG does, against a HYPOTHETICAL meal, and
 *               NEVER SAVE. "I'm having rice and palak paneer, what should I add" is a question about
 *               a plate, not a record of one. No [OrchestratorEvent.MealLogged] is emitted, the
 *               [MealStore] is not called, and the timeline is untouched. A SUGGEST that writes is
 *               the pollution `0015` names: a meal in their history they never ate.
 *  - ANSWER     a question about their own diary, or about nutrition.
 *  - RECOMMEND  what to eat for a goal or a declared condition.
 *
 * ANSWER AND RECOMMEND ARE NEVER CONTEXT-FREE. A question is only worth answering in the light of
 * the person's own records: their logged meals, their lab values, the conditions they declared,
 * where they live and eat. Before either path calls the model, the orchestrator reads the current
 * [UserContext] from the store and renders it into the request, so "what should I eat for iron"
 * is answered knowing their last haemoglobin, what they logged this week, what they said they are
 * managing and that they eat in a hostel canteen. A chatbot gives the generic paragraph; this app
 * does not, and the difference is the product. The context goes in as finished strings the model
 * may quote and cannot compute with, the same defence the phrasing path uses.
 *
 * THE ANSWER IS ON SCREEN BEFORE THE MODEL RUNS. Ruled 20 Sep after the first measured
 * conversational turn (22.8 s at best, 30 s typical): "how much protein today" is a number in
 * the database and needs no model, so ANSWER and RECOMMEND emit [OrchestratorEvent.OwnFigures],
 * the person's own rendered lines, the moment the store is read and before any retrieval or
 * generation, while the lead-in phrase is spoken. What arrives later, as [OrchestratorEvent.Answered]
 * or [OrchestratorEvent.Advice], is the phrasing, not the answer.
 *
 * WHEN THE CLASSIFIER IS NOT SURE it emits [OrchestratorEvent.NeedsIntent] and stops. It never
 * guesses LOG, because a guessed LOG writes to the timeline. The UI asks, and re-enters with
 * [UserIntent.Resolve], which carries the person's own answer and skips the classifier.
 *
 * THE REFERRAL. When the rules engine marks a referral as required ([RuleEvaluation.referralRequired],
 * `0015`), the referral sentence is the rendered trigger and is appended to the response by THIS
 * class, as a fixed line, after whatever the model said. It is never generated, so no sample can
 * soften or drop it; and it comes alongside the help, not instead of it. A question that itself
 * asks for a clinical judgement ("is 7 dangerous", "should I stop my tablets"; `SafetyLine`,
 * `0024`) gets the fixed referral line from the string table even when no report is on file for
 * the engine to render a sentence about.
 *
 * FAILURE MODES
 * - Any stage returning [Outcome.Unavailable] ends the flow in [OrchestratorEvent.Failed] carrying
 *   that reason. The orchestrator never substitutes a fallback value and never retries silently
 *   more than the stage's own contract allows.
 * - Low confidence on extraction produces [OrchestratorEvent.NeedsConfirmation] rather than a save.
 *   Spec 10.7: the system asks rather than guesses, and silently logging the wrong food is the
 *   outcome to avoid.
 * - Generated prose failing its guard is not a failure of the turn. The rules engine's own
 *   rendered sentence is shown in its place ([OrchestratorEvent.Advice.phrased] null), and the
 *   turn completes. The referral, when required, is still appended.
 */
interface Orchestrator {
    fun handle(intent: UserIntent): Flow<OrchestratorEvent>
}

sealed interface UserIntent {
    /**
     * A spoken turn. The app does not know which of the four it is until the model has classified
     * the transcript; beat 1 (log) and beat 2 (query) both start here.
     */
    data class Speak(val language: SpeechLanguageRef) : UserIntent

    /** The same turn, typed. The fallback when the microphone is denied, and the JVM test path. */
    data class Type(val text: String, val language: SpeechLanguageRef) : UserIntent

    /**
     * The person answered a [OrchestratorEvent.NeedsIntent] question. Same text, their decision;
     * the classifier is not consulted again.
     */
    data class Resolve(val text: String, val language: SpeechLanguageRef, val intent: SpokenIntent) : UserIntent

    /** Beat 3, the capture half: the Scan screen's own. Sent only while [SaveLabReport] is unwired. */
    data object ScanLabReport : UserIntent

    /**
     * Beat 3, the write: the values the person CONFIRMED on the Scan screen, report date on
     * each. The orchestrator writes them, emits [OrchestratorEvent.LabReportSaved], and then
     * regenerates the last meal's advice against the new context inside the same turn, so the
     * next [AdviseOnMeal] is instant (`AdviceStore`).
     */
    data class SaveLabReport(val values: List<LabValue>) : UserIntent

    /**
     * Beat 4. Advice for a meal already logged, against the CURRENT context. Instant when the
     * stored advice's digest matches; the model runs only when something in the context changed
     * since it was phrased and nothing has regenerated it yet.
     */
    data class AdviseOnMeal(val mealId: Long) : UserIntent

    /** Correct something the system inferred. Spec 15.3 requires this to always be available. */
    data class CorrectValue(val mealId: Long, val itemIndex: Int, val correction: Correction) : UserIntent

    data class ScanPackagedLabel(val barcode: String?) : UserIntent
    data class CheckExerciseForm(val movement: String) : UserIntent

    /** Stop whatever is being spoken. The screen's stop control; nothing is written or undone. */
    data object StopSpeaking : UserIntent

    /**
     * Push-to-talk (`0031`, ruled 20 Sep): the thumb lifted. Ends the recording that [Speak] began
     * and nothing else ends it; the clip goes to the recogniser as it stands. Accepted at any
     * moment of a spoken turn, including before the microphone is live (a very fast tap), and a
     * no-op outside one. Added by Arjun 21 Sep with the screen's release; the orchestrator's
     * handling is Rao's.
     */
    data object EndSpeech : UserIntent
}

/**
 * The four intents of `0015`, in domain terms. The model's own label type lives in ml/llm and is
 * mapped onto this at the boundary, so the UI and the tests never import a prompt file.
 */
enum class SpokenIntent { LOG, ANSWER, SUGGEST, RECOMMEND }

sealed interface OrchestratorEvent {
    /** A stage started. The UI shows what is happening, named, not a bare spinner. */
    data class Progress(val stage: Stage) : OrchestratorEvent

    /** Microphone level while recording, for the meter that replaces streaming transcripts. */
    data class AudioLevel(val rms: Float) : OrchestratorEvent

    /**
     * The microphone is delivering signal: `AsrEvent.SpeechStarted`, which push-to-talk emits on
     * the first frame that carries any signal, not at the press (`PushToTalk`, 2f5f803). The
     * screen lights its recording cue on THIS and never on touch-down, so the cue tells the
     * truth: anything said before it was not recorded. [Progress] with `Stage.RECORDING` means
     * the turn has begun, not that the microphone is live.
     */
    data object MicrophoneLive : OrchestratorEvent

    /** What was heard, once endpointed. Shown immediately so the user sees they were understood. */
    data class Transcribed(val text: String) : OrchestratorEvent

    /**
     * Which of the four the turn became, once decided (by the pre-filter, the classifier, or the
     * person's own [UserIntent.Resolve]), and the lead-in phrase being spoken while the route
     * runs, from the string table; null when no lead-in is spoken.
     */
    data class IntentKnown(val intent: SpokenIntent, val leadIn: String?) : OrchestratorEvent

    /**
     * The person's own figures, rendered by `ContextText`, straight from the store, BEFORE any
     * model call: on ANSWER and RECOMMEND these are on screen within the first second and are
     * the answer to "how much X today"; the model's sentence that follows is phrasing. The same
     * lines are given to the model and carried again on [Answered.figures].
     */
    data class OwnFigures(val lines: List<String>) : OrchestratorEvent

    /**
     * The classifier could not tell which of the four they meant. The UI asks and re-enters with
     * [UserIntent.Resolve]. The flow completes right after this: nothing has been written and
     * nothing will be.
     */
    data class NeedsIntent(val transcript: String) : OrchestratorEvent

    /**
     * The plate could not be resolved with confidence; ask the user before saving anything. [why]
     * is the resolver's reason, which the UI already maps to a sentence (NO_MATCH: "I do not know
     * that food"; KNOWN_ITEM_NO_DATA: "I know it and hold no figures for it"). The flow completes
     * right after this, as with [NeedsIntent].
     */
    data class NeedsConfirmation(val why: UnavailableReason, val parsed: ParsedMeal) : OrchestratorEvent

    /**
     * A meal was resolved and computed. Figures carry their bands and sources. On SUGGEST this
     * is the hypothetical plate; [hypothetical] says so and the UI must not show it as logged.
     */
    data class MealResolved(
        val meal: ParsedMeal,
        val figures: List<NutritionFigure>,
        val hypothetical: Boolean,
        /**
         * The grams each item was computed from, one per [ParsedMeal.items] in the same order,
         * null for an item the database holds no figures for. The plate's "taken as 180 g"
         * caption (0035) is this number beside an item whose reasons say QUANTITY_INFERRED;
         * a stated amount keeps its grams behind the band's detail, never on the face.
         * Ira's ask of 20 Sep 23:34; defaulted so a feed that has no grams still compiles.
         */
        val grams: List<Double?> = emptyList(),
    ) : OrchestratorEvent

    /** The meal is on the timeline. Emitted on LOG and on nothing else. */
    data class MealLogged(val mealId: Long) : OrchestratorEvent

    /** The confirmed report values are on file. [regeneratedMealId] is the meal whose advice was refreshed, if any. */
    data class LabReportSaved(val count: Int, val regeneratedMealId: Long?) : OrchestratorEvent

    /**
     * Rules fired: LOG, SUGGEST and RECOMMEND all end here, and the UI renders the suggestions
     * from [RuleEvaluation.rankedCandidates], never from prose. [phrased] is the model's text when
     * it passed its guards and null when it did not, in which case the rendered trigger sentence
     * stands alone. [referral] is the fixed referral line when one is required, already rendered,
     * to be shown and spoken whatever [phrased] holds. [factIds] are the knowledge rows a RECOMMEND
     * was given, for the "why" affordance; empty on the other two.
     */
    data class Advice(
        val evaluation: RuleEvaluation,
        val phrased: String?,
        val referral: String?,
        val factIds: List<String> = emptyList(),
    ) : OrchestratorEvent

    /**
     * ANSWER: the model's guarded text, the ids of the knowledge rows it was given, and the fixed
     * referral line when one is required. [text] never contains the referral; the UI shows both.
     *
     * [text] is null when every attempt failed a guard ([refused] says which). That is not a
     * failure of the turn (`0024`: a refusal that abandons the person is what `0015` forbids):
     * the UI shows its own "I can't judge that" sentence, and the referral, when there is one,
     * stands exactly as it would have under an answer.
     */
    data class Answered(
        val text: String?,
        val factIds: List<String>,
        val referral: String?,
        val refused: UnavailableReason? = null,
        /**
         * The person's own lines the model was given, rendered by `ContextText`: period totals,
         * recent meals, lab values, the engine's sentence. When [text] is null the UI shows the
         * string table's `answer_refused` line and THESE, so a refused answer still hands the
         * person their data; when [text] is present they are the "why" behind it.
         */
        val figures: List<String> = emptyList(),
    ) : OrchestratorEvent

    data object Completed : OrchestratorEvent

    data class Failed(val reason: UnavailableReason, val detail: String? = null) : OrchestratorEvent

    /** This path is not built yet. The UI shows an explicit not-implemented state, never a sample. */
    data class NotImplemented(val component: String) : OrchestratorEvent
}

enum class Stage {
    RECORDING, TRANSCRIBING, CLASSIFYING, EXTRACTING, MATCHING_FOODS, COMPUTING, SAVING,
    EVALUATING_RULES, RETRIEVING_FACTS, PHRASING, SPEAKING, CAPTURING, READING_TEXT,
}

data class ParsedMeal(
    val items: List<ParsedItem>,
    val confidence: Confidence,
    val rawTranscript: String,
)

data class ParsedItem(
    val spokenName: String,
    val quantity: Double?,
    val unit: String?,
    val matchedFoodCode: String?,
    val confidence: Confidence,
)

sealed interface Correction {
    data class Quantity(val amount: Double, val unit: String) : Correction
    data class Food(val foodCode: String) : Correction
    data class Remove(val why: String?) : Correction
}

/** Indirection so domain does not import ml/asr. Resolved by the orchestrator implementation. */
@JvmInline
value class SpeechLanguageRef(val tag: String)
