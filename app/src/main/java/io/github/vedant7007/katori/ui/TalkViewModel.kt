package io.github.vedant7007.katori.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.Orchestrator
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.RulesEngine
import io.github.vedant7007.katori.domain.SpeechLanguageRef
import io.github.vedant7007.katori.domain.SpokenIntent
import io.github.vedant7007.katori.domain.Stage
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ui.demo.DemoFeed
import io.github.vedant7007.katori.ui.demo.ScriptedOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Talk tab's state: one conversation, rendered from [OrchestratorEvent]s and nothing else.
 *
 * THE BOUNDARY (COORDINATION.md, 20 Sep 14:30, agreed 14:55). This class calls
 * `Orchestrator.handle` and renders the events the contract defines. It renders the trigger
 * sentence with [TriggerText] and the figures with [ContextText.figure], the same renderers the
 * orchestrator uses for speech and for the model's context, so the screen never says a number
 * a second way. It calls no engine, writes to no store, and holds no number it was not handed.
 *
 * THE TURN ON SCREEN follows `docs/decisions/0026`: the transcript is pinned for the whole turn,
 * every stage is named and ticked as it completes, and an elapsed-seconds counter runs beside
 * the current stage so a long wait reads as work. Text lands before speech.
 *
 * THE SCRIPTED FEED (`0027`) is used only while [DemoFeed.enabled] is on, which only the
 * pre-flight screen can do, and the banner on every tab says so. [RulesEngine] is injected for
 * that feed alone: the script runs the real engine over scripted input.
 */
@HiltViewModel
class TalkViewModel @Inject constructor(
    private val orchestrator: Orchestrator,
    private val triggerText: TriggerText,
    private val contextText: ContextText,
    rules: RulesEngine,
) : ViewModel() {

    private val scripted = ScriptedOrchestrator(rules, contextText, triggerText)

    sealed interface Entry {
        /** What was heard or typed, shown the moment it is known. */
        data class Said(val text: String) : Entry

        /** A resolved plate: the items as said, each figure as a rendered line with its band. */
        data class Plate(
            val items: List<ParsedItem>,
            val figures: List<Pair<String, ConfidenceBand>>,
            val hypothetical: Boolean,
            val logged: Boolean,
        ) : Entry

        /** Rules fired. [trigger] is the engine's own sentence; [phrased] the model's, if it passed. */
        data class Advice(val trigger: String?, val phrased: String?, val referral: String?, val candidates: List<String>) : Entry

        /** [text] null: every attempt failed a guard; the screen shows answer_refused and [figures], the person's own lines. */
        data class Answer(val text: String?, val referral: String?, val figures: List<String> = emptyList()) : Entry

        /** The classifier was not sure. The person decides; nothing has been written. */
        data class AskIntent(val transcript: String) : Entry

        /** The plate could not be resolved. Nothing was saved. */
        data class Confirm(val why: UnavailableReason, val items: List<ParsedItem>) : Entry

        data class Failed(val reason: UnavailableReason, val detail: String?) : Entry

        data class NotBuilt(val component: String) : Entry
    }

    data class State(
        /** The language the person chose (spec 10.2: chosen, never detected). The presenter's, by default (0022). */
        val language: String = "en-IN",
        val busy: Boolean = false,
        /** Stages of the current turn in order; the last one is current until Completed. */
        val stages: List<Stage> = emptyList(),
        /** Seconds since the current stage began (0026: the counter is the honesty device). */
        val elapsedSeconds: Int = 0,
        /** Microphone level while recording, 0..1, the meter that replaces streaming transcripts. */
        val level: Float = 0f,
        val entries: List<Entry> = emptyList(),
        /** The meal logged most recently in this session, for beat 4's "advise again". */
        val lastMealId: Long? = null,
    ) {
        val stage: Stage? get() = stages.lastOrNull()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setLanguage(tag: String) = _state.update { it.copy(language = tag) }

    fun speak() = run(UserIntent.Speak(language()))
    fun type(text: String) = run(UserIntent.Type(text, language()))
    fun resolve(transcript: String, intent: SpokenIntent) = run(UserIntent.Resolve(transcript, language(), intent))
    fun adviseAgain() { state.value.lastMealId?.let { run(UserIntent.AdviseOnMeal(it)) } }

    private fun language() = SpeechLanguageRef(state.value.language)

    private fun run(intent: UserIntent) {
        if (state.value.busy) return
        _state.update { it.copy(busy = true, stages = emptyList(), elapsedSeconds = 0, level = 0f) }
        val source = if (DemoFeed.enabled.value) scripted else orchestrator
        viewModelScope.launch(Dispatchers.Default) {
            val ticker = launch {
                while (true) { delay(1000); _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) } }
            }
            source.handle(intent)
                // A flow that throws instead of ending in Failed is a bug in the pipeline; it is
                // shown as one, with the exception's name, rather than swallowed.
                .catch { e -> add(Entry.Failed(UnavailableReason.INTERNAL_ERROR, e.toString())) }
                .collect(::on)
            ticker.cancel()
            _state.update { it.copy(busy = false, stages = emptyList(), elapsedSeconds = 0, level = 0f) }
        }
    }

    private fun on(event: OrchestratorEvent) {
        when (event) {
            is OrchestratorEvent.Progress -> _state.update { it.copy(stages = it.stages + event.stage, elapsedSeconds = 0) }
            is OrchestratorEvent.AudioLevel -> _state.update { it.copy(level = event.rms.coerceIn(0f, 1f)) }
            is OrchestratorEvent.Transcribed -> add(Entry.Said(event.text))
            // The two events the 0026 screen asked for; rendering them is Arjun's next step.
            is OrchestratorEvent.IntentKnown -> Unit
            is OrchestratorEvent.OwnFigures -> Unit
            is OrchestratorEvent.LabReportSaved -> Unit
            is OrchestratorEvent.NeedsIntent -> add(Entry.AskIntent(event.transcript))
            is OrchestratorEvent.NeedsConfirmation -> add(Entry.Confirm(event.why, event.parsed.items))
            is OrchestratorEvent.MealResolved -> add(
                Entry.Plate(
                    items = event.meal.items,
                    figures = event.figures.map { contextText.figure(it) to it.confidence.band },
                    hypothetical = event.hypothetical,
                    logged = false,
                )
            )
            is OrchestratorEvent.MealLogged -> _state.update { s ->
                val i = s.entries.indexOfLast { it is Entry.Plate }
                val entries = if (i < 0) s.entries else s.entries.toMutableList().also { l -> l[i] = (l[i] as Entry.Plate).copy(logged = true) }
                s.copy(entries = entries, lastMealId = event.mealId)
            }
            is OrchestratorEvent.Advice -> add(
                Entry.Advice(
                    trigger = event.evaluation.trigger?.let(triggerText::render),
                    phrased = event.phrased,
                    referral = event.referral,
                    candidates = event.evaluation.rankedCandidates.map { it.candidate.displayName },
                )
            )
            is OrchestratorEvent.Answered -> add(Entry.Answer(event.text, event.referral, event.figures))
            is OrchestratorEvent.Failed -> add(Entry.Failed(event.reason, event.detail))
            is OrchestratorEvent.NotImplemented -> add(Entry.NotBuilt(event.component))
            OrchestratorEvent.Completed -> Unit
        }
    }

    private fun add(entry: Entry) = _state.update { it.copy(entries = it.entries + entry) }
}
