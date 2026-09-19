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
 * FAILURE MODES
 * - Any stage returning [Outcome.Unavailable] ends the flow in [OrchestratorEvent.Failed] carrying
 *   that reason. The orchestrator never substitutes a fallback value and never retries silently
 *   more than the stage's own contract allows.
 * - Low confidence on extraction produces [OrchestratorEvent.NeedsConfirmation] rather than a save.
 *   Spec 10.7: the system asks rather than guesses, and silently logging the wrong food is the
 *   outcome to avoid.
 */
interface Orchestrator {
    fun handle(intent: UserIntent): Flow<OrchestratorEvent>
}

sealed interface UserIntent {
    /** Beat 1. Log a meal from speech. */
    data class LogMealByVoice(val language: SpeechLanguageRef) : UserIntent

    /** Beat 2. Ask a question about stored history. */
    data class QueryHistoryByVoice(val language: SpeechLanguageRef) : UserIntent

    /** Beat 3. Scan a printed lab report. */
    data object ScanLabReport : UserIntent

    /** Beat 4. Re-evaluate advice for a meal already logged. */
    data class AdviseOnMeal(val mealId: Long) : UserIntent

    /** Correct something the system inferred. Spec 15.3 requires this to always be available. */
    data class CorrectValue(val mealId: Long, val itemIndex: Int, val correction: Correction) : UserIntent

    data class ScanPackagedLabel(val barcode: String?) : UserIntent
    data class CheckExerciseForm(val movement: String) : UserIntent
}

sealed interface OrchestratorEvent {
    /** A stage started. The UI shows what is happening, named, not a bare spinner. */
    data class Progress(val stage: Stage) : OrchestratorEvent

    /** Microphone level while recording, for the meter that replaces streaming transcripts. */
    data class AudioLevel(val rms: Float) : OrchestratorEvent

    /** What was heard, once endpointed. Shown immediately so the user sees they were understood. */
    data class Transcribed(val text: String) : OrchestratorEvent

    /** The parse is uncertain; ask the user before saving anything. */
    data class NeedsConfirmation(val question: String, val parsed: ParsedMeal) : OrchestratorEvent

    /** A meal was resolved and computed. Figures carry their bands and sources. */
    data class MealResolved(val meal: ParsedMeal, val figures: List<NutritionFigure>) : OrchestratorEvent

    /** Rules fired. Carries the triggering sentence for beat 4. */
    data class Advice(val evaluation: RuleEvaluation, val phrased: String?) : OrchestratorEvent

    data object Completed : OrchestratorEvent

    data class Failed(val reason: UnavailableReason, val detail: String? = null) : OrchestratorEvent

    /** This path is not built yet. The UI shows an explicit not-implemented state, never a sample. */
    data class NotImplemented(val component: String) : OrchestratorEvent
}

enum class Stage { RECORDING, TRANSCRIBING, EXTRACTING, MATCHING_FOODS, COMPUTING, EVALUATING_RULES, PHRASING, SPEAKING, CAPTURING, READING_TEXT }

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
