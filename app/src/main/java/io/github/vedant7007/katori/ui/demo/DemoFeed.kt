package io.github.vedant7007.katori.ui.demo

import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.MealSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The switch for the scripted demo feed (docs/decisions/0027), and the little state the script
 * keeps between turns so beat 4 can re-evaluate the meal beat 1 logged with the report beat 3
 * scanned.
 *
 * OFF BY DEFAULT, PROCESS-WIDE, NEVER INJECTED. It is turned on only from the pre-flight screen,
 * which is reached only by a long-press, and every tab shows a banner while it is on. Nothing
 * that reads [enabled] is a provider: the real orchestrator is what Hilt provides, always.
 */
object DemoFeed {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun set(on: Boolean) {
        _enabled.value = on
        if (!on) reset()
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
