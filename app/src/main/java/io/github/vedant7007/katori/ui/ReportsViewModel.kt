package io.github.vedant7007.katori.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.vedant7007.katori.data.knowledge.MarkerExplanations
import io.github.vedant7007.katori.data.local.Diary
import io.github.vedant7007.katori.domain.AdviceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * REPORTS: every saved lab value, grouped by the date printed on the report, newest first; one
 * test's history by report date for the bars (one report scanned is one bar, shown as one); and,
 * for "how this changes your day", the engine's own trigger sentence stored for the last meal.
 * Flags are below / above / within THE PRINTED RANGE and no other word (spec 15.1, 0018).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val diary: Diary,
    private val advice: AdviceStore,
    @ApplicationContext context: Context,
) : ViewModel() {

    /** The shipped, cited explanations, loaded once; a file that fails its own checks shows nothing rather than half a file. */
    private val markers: MarkerExplanations? = runCatching { MarkerExplanations.load { context.assets.open(MarkerExplanations.ASSET_PATH) } }.getOrNull()

    data class Report(val date: LocalDate, val values: List<LabRow>) {
        val outOfRange: List<LabRow> get() = values.filter { it.status == LabStatus.BELOW || it.status == LabStatus.ABOVE }
    }

    data class State(
        val loaded: Boolean = false,
        val reports: List<Report> = emptyList(),
        /** The test whose history is shown, chosen by the person; null shows none. */
        val selectedTest: String? = null,
        /** [selectedTest]'s values by report date, oldest first. */
        val history: List<LabRow> = emptyList(),
        /** The trigger sentence stored for the last meal, when one fired; the one sentence "how this changes your day" may show. */
        val lastMealTrigger: String? = null,
    )

    private val selected = MutableStateFlow<String?>(null)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val history = selected.flatMapLatest { test -> test?.let { diary.labHistory(it) } ?: flowOf(emptyList()) }
            combine(diary.labs(), history, selected, diary.lastMeal()) { labs, hist, test, last ->
                State(
                    loaded = true,
                    reports = labs.map { it.shown(markers) }.groupBy { it.reportDate }.toSortedMap(compareByDescending { it }).map { (d, v) -> Report(d, v) },
                    selectedTest = test,
                    history = hist.map { it.shown(markers) },
                    lastMealTrigger = last?.let { advice.latest(it.id)?.triggerText },
                )
            }.collect { _state.value = it }
        }
    }

    fun select(testName: String?) { selected.value = testName }
}
