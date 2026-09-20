package io.github.vedant7007.katori.ui.demo

import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.MealSnapshot
import io.github.vedant7007.katori.domain.Orchestrator
import io.github.vedant7007.katori.domain.RulesEngine
import io.github.vedant7007.katori.domain.SpokenIntent
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.ml.vision.RecognisedText
import io.github.vedant7007.katori.ml.vision.TextBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The scripted demo feed's switch and state, `full` FLAVOUR ONLY (docs/decisions/0027).
 *
 * THE DEMO BUILD HAS NO FEED. `src/demo/` carries a `DemoFeed` with the same surface whose
 * [available] is false, whose [orchestrator] is null and whose [enabled] can never be true, so
 * the one path that could put unguarded text on a screen is absent from the build on stage by
 * construction, the way INTERNET is absent from its manifest. Same pattern, same reason.
 *
 * Here, in `full`: off by default, process-wide, never injected, turned on only from the
 * pre-flight screen, banner on every tab while on.
 */
object DemoFeed {
    const val available: Boolean = true

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun set(on: Boolean) {
        _enabled.value = on
        if (!on) reset()
    }

    /** The scripted orchestrator, over the real engine and renderers. */
    fun orchestrator(rules: RulesEngine, contextText: ContextText, triggerText: TriggerText, leadIns: Map<SpokenIntent, String>): Orchestrator =
        ScriptedOrchestrator(rules, contextText, triggerText, leadIns)

    /** Beat 3's report as recognised lines, one box per cell, for the REAL extractor. */
    fun scriptedReport(): RecognisedText {
        val d = ScriptedOrchestrator.REPORT_DATE
        val lines = mutableListOf(TextBlock("Reported on ${d.dayOfMonth}/${d.monthValue}/${d.year}", 40, 40, 500, 76))
        ScriptedOrchestrator.REPORT_ROWS.forEachIndexed { i, (name, value, range) ->
            val y = 140 + i * 60
            lines += TextBlock(name, 40, y, 40 + 14 * name.length, y + 36)
            lines += TextBlock(value, 520, y, 520 + 14 * value.length, y + 36)
            lines += TextBlock(range, 760, y, 760 + 14 * range.length, y + 36)
        }
        return RecognisedText(lines)
    }

    /** Lab values "saved" by the script in this session. Beat 3 adds; beat 4 evaluates against them. */
    val labValues = mutableListOf<LabValue>()

    /** The plate beat 1 "logged", as the engine saw it. Beat 4 evaluates the same snapshot again. */
    var lastMeal: MealSnapshot? = null

    /** Which of the presenter's sentences the next spoken turn "hears". Cycles. */
    var cursor = 0

    fun reset() {
        labValues.clear()
        lastMeal = null
        cursor = 0
    }
}
