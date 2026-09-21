package io.github.vedant7007.katori.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vedant7007.katori.data.local.Diary
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.Nutrient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TRENDS: the last seven days, each day's derived totals from the day's own query, the count of
 * meals, and the average of the days that have a COMPLETE energy figure. The average is the one
 * arithmetic here and it is over figures the query produced, days without one left out, said
 * as "over N days"; there is no goal, so no bar is coloured against one and nothing is "under".
 */
@HiltViewModel
class TrendsViewModel @Inject constructor(diary: Diary, private val contextText: ContextText) : ViewModel() {

    data class State(
        val loaded: Boolean = false,
        /** Oldest first. A day with nothing logged has no figures. */
        val days: List<ShownDay> = emptyList(),
        val mealCount: Int = 0,
        /** Mean energy over [averagedDays] days with a complete figure; null when there are none. */
        val averageEnergyKcal: Double? = null,
        val averagedDays: Int = 0,
        /** The consecutive days ending today with a meal; 0 when today has none. */
        val streak: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            diary.days(7).map { days ->
                val energies = days.mapNotNull { d -> d.figures.firstOrNull { it.total.nutrient == Nutrient.ENERGY && it.total.completeness == Completeness.COMPLETE }?.total?.amount }
                State(
                    loaded = true,
                    days = days.map { it.shown(contextText) },
                    mealCount = days.sumOf { it.meals.size },
                    averageEnergyKcal = energies.takeIf { it.isNotEmpty() }?.average(),
                    averagedDays = energies.size,
                    streak = Diary.streak(days),
                )
            }.collect { _state.value = it }
        }
    }
}
