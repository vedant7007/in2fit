package io.github.vedant7007.katori.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vedant7007.katori.data.local.Diary
import io.github.vedant7007.katori.data.local.ProfileStore
import io.github.vedant7007.katori.data.local.TargetsSource
import io.github.vedant7007.katori.domain.AdviceStore
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.TargetProgress
import io.github.vedant7007.katori.domain.Targets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * HOME (the v2 design's Today), every row from a query or the engine and nothing else:
 * today's totals, the last meal with the advice stored for it, the lab values outside their
 * printed range, and the targets when the rule has them. Where the data is not there the field
 * is null or empty and the screen shows nothing, never a placeholder: no ring fill without a
 * target, no name in the greeting without one.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    diary: Diary,
    profileStore: ProfileStore,
    targets: TargetsSource,
    private val advice: AdviceStore,
    private val contextText: ContextText,
) : ViewModel() {

    data class State(
        /** Null until the row has been read; then the name, or null when none was given (the greeting drops it). */
        val name: String? = null,
        val loaded: Boolean = false,
        /** Today's derived totals; empty when nothing is logged today. */
        val totals: List<ShownFigure> = emptyList(),
        val mealCount: Int = 0,
        val lastMeal: ShownMeal? = null,
        /** The engine's own trigger sentence stored for [lastMeal], when one fired; never a rule it did not run. */
        val lastMealTrigger: String? = null,
        /** The model's guarded sentence for [lastMeal], when one passed. */
        val lastMealAdvice: String? = null,
        /** Values outside the range printed on the report, newest report first. */
        val outOfRange: List<LabRow> = emptyList(),
        /** Newest report date, for "knows your report of …"; null with no report. */
        val latestReport: java.time.LocalDate? = null,
        /** Null until Priya's rule answers; the ring and bars render empty. */
        val targets: Targets? = null,
        val progress: List<TargetProgress> = emptyList(),
    ) {
        val energy: ShownFigure? get() = totals.firstOrNull { it.nutrient == io.github.vedant7007.katori.domain.model.Nutrient.ENERGY }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        val today = diary.bounds(diary.today())
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                profileStore.profile,
                diary.totals(today),
                diary.meals(today),
                diary.lastMeal(),
                diary.labs(),
            ) { profile, totals, meals, last, labs ->
                val stored = last?.let { advice.latest(it.id) }
                val rows = labs.map { it.shown() }
                State(
                    name = profile?.name,
                    loaded = true,
                    totals = totals.map { it.shown(contextText) },
                    mealCount = meals.size,
                    lastMeal = last?.shown(contextText),
                    lastMealTrigger = stored?.triggerText,
                    lastMealAdvice = stored?.phrased,
                    outOfRange = rows.filter { it.status == LabStatus.BELOW || it.status == LabStatus.ABOVE },
                    latestReport = rows.maxOfOrNull { it.reportDate },
                )
            }.combine(targets.current().combine(targets.progressToday()) { t, p -> t to p }) { s, (t, p) ->
                s.copy(targets = t, progress = p)
            }.collect { _state.value = it }
        }
    }
}
