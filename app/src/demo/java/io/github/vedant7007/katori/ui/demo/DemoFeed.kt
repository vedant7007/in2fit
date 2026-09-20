package io.github.vedant7007.katori.ui.demo

import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.Orchestrator
import io.github.vedant7007.katori.domain.RulesEngine
import io.github.vedant7007.katori.domain.SpokenIntent
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.ml.vision.RecognisedText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * THE DEMO BUILD HAS NO SCRIPTED FEED (docs/decisions/0027, ruled by Vedant 20 Sep evening).
 *
 * This is the `demo` flavour's `DemoFeed`: the same surface `ui/` compiles against, with
 * nothing behind it. [available] is false, [orchestrator] and [scriptedReport] are null, and
 * [enabled] is a flow that can never become true. `ScriptedOrchestrator` does not exist in this
 * flavour's source at all, so the one path that could put unguarded text on a screen is absent
 * from the build on stage by construction, the way INTERNET is absent from its manifest.
 * `DemoFeedAbsentTest` asserts every line of this.
 */
object DemoFeed {
    const val available: Boolean = false

    private val never = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = never.asStateFlow()

    /** No-op. There is nothing to switch on. */
    @Suppress("UNUSED_PARAMETER")
    fun set(on: Boolean) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun orchestrator(rules: RulesEngine, contextText: ContextText, triggerText: TriggerText, leadIns: Map<SpokenIntent, String>): Orchestrator? = null

    fun scriptedReport(): RecognisedText? = null
}
