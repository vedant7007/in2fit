package io.github.vedant7007.katori.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.data.local.ProfileStore
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
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ui.demo.DemoFeed
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
 * a second way. It calls no engine and holds no number it was not handed; the one thing it
 * writes is the chosen language, to the profile row, which is that setting's only home.
 *
 * THE TURN ON SCREEN follows `docs/decisions/0026`: the transcript is pinned for the whole turn,
 * every stage is named and ticked as it completes, and an elapsed-seconds counter runs beside
 * the current stage so a long wait reads as work. Text lands before speech.
 *
 * THE SCRIPTED FEED (`0027`) exists only in the `full` flavour and is used only while
 * [DemoFeed.enabled] is on, which only the pre-flight screen can do, with the banner on every
 * tab. In the demo build the flavour's `DemoFeed` hands back null and this class never sees a
 * script. [RulesEngine] is injected for that feed alone.
 */
@HiltViewModel
class TalkViewModel(
    private val orchestrator: Orchestrator,
    private val triggerText: TriggerText,
    private val contextText: ContextText,
    /** Null in the demo build: the flavour's `DemoFeed` has nothing behind it (0027). */
    private val scripted: Orchestrator?,
    /** The one home of the chosen language (a crash mid-demo must not switch it): the profile row. */
    private val profileStore: ProfileStore,
) : ViewModel() {

    @Inject
    constructor(
        orchestrator: Orchestrator,
        triggerText: TriggerText,
        contextText: ContextText,
        rules: RulesEngine,
        profileStore: ProfileStore,
        @ApplicationContext context: Context,
    ) : this(
        orchestrator, triggerText, contextText,
        scripted = DemoFeed.orchestrator(
            rules, contextText, triggerText,
            leadIns = mapOf(
                SpokenIntent.LOG to context.getString(R.string.tts_lead_in_log),
                SpokenIntent.ANSWER to context.getString(R.string.tts_lead_in_answer),
                SpokenIntent.SUGGEST to context.getString(R.string.tts_lead_in_suggest),
                SpokenIntent.RECOMMEND to context.getString(R.string.tts_lead_in_recommend),
            ),
        ),
        profileStore = profileStore,
    )

    sealed interface Entry {
        /** What was heard or typed, shown the moment it is known. */
        data class Said(val text: String) : Entry

        /** 0026 step 5: which of the four the turn became, and the lead-in phrase as it is spoken. */
        data class Heading(val intent: SpokenIntent, val leadIn: String?) : Entry

        /** The person's own lines, straight from the store, before any model call: the answer itself. */
        data class Figures(val lines: List<String>) : Entry

        /**
         * A resolved plate: the items as said, each figure as a rendered line with its band, and
         * [rows], one per item, for the per-item card. [rows] is empty until the orchestrator
         * fills `MealResolved.items` (Rao's line); the card falls back to [items] while it is.
         */
        data class Plate(
            val items: List<ParsedItem>,
            val figures: List<Pair<String, ConfidenceBand>>,
            val hypothetical: Boolean,
            val logged: Boolean,
            val rows: List<PlateItem> = emptyList(),
        ) : Entry

        /**
         * One item as the card reads it. Every field is copied from the event; nothing here is
         * computed, and an absent number is null, never 0.
         */
        data class PlateItem(
            /** The name the database shows, or the word as said when it holds no figures. */
            val name: String,
            /** The amount as said, or the household amount the resolver assumed and wrote back (0035). */
            val quantity: Double?,
            val unit: String?,
            /** What the figures were computed from; null for a no-data item. */
            val grams: Double?,
            /** Measured amounts only; an unknown nutrient is absent. */
            val nutrients: Map<Nutrient, Double>,
            /** QUANTITY_INFERRED is present: the amount was ours, so the card says "taken as" (0035). */
            val inferred: Boolean,
            val band: ConfidenceBand,
            val reasons: List<ConfidenceReason>,
        )

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
        /**
         * The language the person will speak in (spec 10.2: chosen, never detected). HINDI by
         * default, ruled 21 Sep: the presenter's Hindi hears 16 of 16 food words, English 7 of 16,
         * and every restart of the app must come back to it without a tap. Read from the profile
         * row ([ProfileStore.speechLanguage]); the default holds until the row has been read.
         */
        val language: String = DEFAULT_LANGUAGE,
        val busy: Boolean = false,
        /**
         * The microphone is delivering signal ([OrchestratorEvent.MicrophoneLive]). The recording
         * cue lights on this and on nothing else: not on the press, not on `Stage.RECORDING`,
         * because anything said before it was not recorded (PushToTalk, 2f5f803).
         */
        val micLive: Boolean = false,
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

    init {
        viewModelScope.launch(Dispatchers.Default) {
            profileStore.speechLanguage.collect { tag -> _state.update { it.copy(language = tag) } }
        }
    }

    fun setLanguage(tag: String) {
        _state.update { it.copy(language = tag) }
        viewModelScope.launch(Dispatchers.Default) { profileStore.setSpeechLanguage(tag) }
    }

    fun speak() = run(UserIntent.Speak(language()))
    fun type(text: String) = run(UserIntent.Type(text, language()))
    fun resolve(transcript: String, intent: SpokenIntent) = run(UserIntent.Resolve(transcript, language(), intent))
    fun adviseAgain() { state.value.lastMealId?.let { run(UserIntent.AdviseOnMeal(it)) } }

    /** 0026 step 8: stops speech only; the turn and its text are untouched. Sent while busy, by design. */
    fun stopSpeaking() = aside(UserIntent.StopSpeaking)

    /**
     * Push-to-talk: the thumb lifted. Press starts the turn ([speak]); THIS ends the recording, and
     * nothing else ends it. Sent while busy, by design, and accepted by the orchestrator before
     * the microphone is live (a fast tap) or after (the normal case).
     */
    fun endSpeech() = aside(UserIntent.EndSpeech)

    /** An intent sent INTO a running turn rather than starting one: stop, release. */
    private fun aside(intent: UserIntent) {
        val source = scripted?.takeIf { DemoFeed.enabled.value } ?: orchestrator
        viewModelScope.launch(Dispatchers.Default) { source.handle(intent).catch { }.collect { } }
    }

    private fun language() = SpeechLanguageRef(state.value.language)

    private fun run(intent: UserIntent) {
        if (state.value.busy) return
        _state.update { it.copy(busy = true, stages = emptyList(), elapsedSeconds = 0, level = 0f, micLive = false) }
        val source = scripted?.takeIf { DemoFeed.enabled.value } ?: orchestrator
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
            _state.update { it.copy(busy = false, stages = emptyList(), elapsedSeconds = 0, level = 0f, micLive = false) }
        }
    }

    private fun on(event: OrchestratorEvent) {
        when (event) {
            is OrchestratorEvent.Progress -> _state.update {
                // The microphone is done the moment the turn moves past recording.
                it.copy(stages = it.stages + event.stage, elapsedSeconds = 0, micLive = it.micLive && event.stage == Stage.RECORDING)
            }
            is OrchestratorEvent.MicrophoneLive -> _state.update { it.copy(micLive = true) }
            is OrchestratorEvent.AudioLevel -> _state.update { it.copy(level = event.rms.coerceIn(0f, 1f)) }
            is OrchestratorEvent.Transcribed -> add(Entry.Said(event.text))
            is OrchestratorEvent.IntentKnown -> add(Entry.Heading(event.intent, event.leadIn))
            is OrchestratorEvent.OwnFigures -> add(Entry.Figures(event.lines))
            // The Scan tab's turn; the Talk tab never sends SaveLabReport.
            is OrchestratorEvent.LabReportSaved -> Unit
            is OrchestratorEvent.NeedsIntent -> add(Entry.AskIntent(event.transcript))
            is OrchestratorEvent.NeedsConfirmation -> add(Entry.Confirm(event.why, event.parsed.items))
            is OrchestratorEvent.MealResolved -> add(
                Entry.Plate(
                    items = event.meal.items,
                    figures = event.figures.map { contextText.figure(it) to it.confidence.band },
                    hypothetical = event.hypothetical,
                    logged = false,
                    rows = event.items.mapIndexed { i, r ->
                        val said = event.meal.items.getOrNull(i)
                        Entry.PlateItem(
                            name = r.snapshot.displayName,
                            quantity = said?.quantity,
                            unit = said?.unit,
                            grams = r.snapshot.grams,
                            nutrients = r.snapshot.nutrients,
                            inferred = ConfidenceReason.QUANTITY_INFERRED in r.confidence.reasons,
                            band = r.confidence.band,
                            reasons = r.confidence.reasons,
                        )
                    },
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
            // The lines were already on screen if OwnFigures came first; the answer card repeats
            // them only when they were not.
            is OrchestratorEvent.Answered -> _state.update { s ->
                val shown = s.entries.indexOfLast { it is Entry.Figures } > s.entries.indexOfLast { it is Entry.Said }
                s.copy(entries = s.entries + Entry.Answer(event.text, event.referral, if (shown) emptyList() else event.figures))
            }
            is OrchestratorEvent.Failed -> add(Entry.Failed(event.reason, event.detail))
            is OrchestratorEvent.NotImplemented -> add(Entry.NotBuilt(event.component))
            OrchestratorEvent.Completed -> Unit
        }
    }

    private fun add(entry: Entry) = _state.update { it.copy(entries = it.entries + entry) }

    companion object {
        const val DEFAULT_LANGUAGE = ProfileStore.DEFAULT_LANGUAGE
    }
}
