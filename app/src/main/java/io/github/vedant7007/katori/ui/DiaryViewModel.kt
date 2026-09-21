package io.github.vedant7007.katori.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vedant7007.katori.data.local.Diary
import io.github.vedant7007.katori.domain.AdviceStore
import io.github.vedant7007.katori.domain.ContextText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * THE DIARY: one day at a time, its meals in the order they were logged, each with its items and
 * its own derived figures, the day's totals from the same query, and the week strip (which of the
 * last seven days has a meal). Deleting a meal removes it and everything derived from it; the
 * next read shows the diary without it, and nothing else remembers it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val diary: Diary,
    private val advice: AdviceStore,
    private val contextText: ContextText,
) : ViewModel() {

    data class Entry(val meal: ShownMeal, /** The engine's stored trigger sentence for this meal, when one fired. */ val flag: String?)

    data class State(
        val date: LocalDate,
        val loaded: Boolean = false,
        val entries: List<Entry> = emptyList(),
        val totals: List<ShownFigure> = emptyList(),
        /** The seven days ending on [date], oldest first: logged or not. No streak is implied. */
        val week: List<Pair<LocalDate, Boolean>> = emptyList(),
    ) {
        val energy: ShownFigure? get() = totals.firstOrNull { it.nutrient == io.github.vedant7007.katori.domain.model.Nutrient.ENERGY }
    }

    private val date = MutableStateFlow(diary.today())
    private val _state = MutableStateFlow(State(date.value))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            date.flatMapLatest { d ->
                combine(diary.day(d), diary.days(7, d)) { day, week ->
                    State(
                        date = d,
                        loaded = true,
                        entries = day.meals.map { Entry(it.shown(contextText), advice.latest(it.id)?.triggerText) },
                        totals = day.figures.map { it.shown(contextText) },
                        week = week.map { it.date to it.meals.isNotEmpty() },
                    )
                }
            }.collect { _state.value = it }
        }
    }

    fun select(d: LocalDate) { date.value = d }
    fun previousDay() = select(date.value.minusDays(1))
    fun nextDay() = select(date.value.plusDays(1))

    fun delete(mealId: Long) {
        viewModelScope.launch(Dispatchers.IO) { diary.deleteMeal(mealId) }
    }
}
