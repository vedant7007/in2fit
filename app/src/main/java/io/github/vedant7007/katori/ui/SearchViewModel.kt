package io.github.vedant7007.katori.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vedant7007.katori.data.food.FoodCode
import io.github.vedant7007.katori.data.food.FoodLookup
import io.github.vedant7007.katori.data.food.FoodQuery
import io.github.vedant7007.katori.data.local.Diary
import io.github.vedant7007.katori.data.local.USUAL_UNIT
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientValue
import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FOOD SEARCH over the bundled database through the same [FoodLookup] the resolver uses: the
 * matches, each with the household unit its class is usually served in, that unit's grams from
 * `unit_conversions` (a shipped default, said as such, 0035) and the nutrients for that portion
 * from `nutrientsFor`. A different portion is a new call to the lookup, never a multiplication
 * here. "You log these often" is the most frequent `food_id` in the diary.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(private val lookup: FoodLookup, private val diary: Diary) : ViewModel() {

    data class Row(
        val code: FoodCode,
        val name: String,
        /** The household unit shown, e.g. "katori"; "g" when the class has none, and then [unitGrams] is 100. */
        val unit: String,
        val unitGrams: Double,
        /** The grams came from a shipped default conversion, not from anything the person said. */
        val defaultConversion: Boolean,
        val quantity: Double,
        /** Measured nutrients for [quantity] × [unit]; an unknown nutrient is absent. */
        val nutrients: Map<Nutrient, Double>,
        val band: ConfidenceBand,
        /** What this US record carries that Indian production does not, when the sweep found one. */
        val disclosure: String?,
    ) {
        val grams: Double get() = unitGrams * quantity
    }

    data class State(
        val query: String = "",
        val searching: Boolean = false,
        val results: List<Row> = emptyList(),
        /** The catalogue names of the foods logged most often, most often first; empty until the diary has some. */
        val frequent: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var search: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) { _state.update { it.copy(frequent = diary.frequentFoodNames(5)) } }
    }

    fun search(text: String) {
        _state.update { it.copy(query = text, searching = text.isNotBlank()) }
        search?.cancel()
        if (text.isBlank()) { _state.update { it.copy(results = emptyList(), searching = false) }; return }
        search = viewModelScope.launch(Dispatchers.IO) {
            val matches = (lookup.candidates(FoodQuery(text.trim(), LANGUAGE), 10) as? Outcome.Ok)?.value.orEmpty()
            val rows = matches.mapNotNull { m ->
                val unit = USUAL_UNIT[m.foodClass.name]
                val weight = unit?.let { lookup.resolveUnit(it, m.foodClass) as? Outcome.Ok }?.value
                val row = Row(
                    code = m.code, name = m.displayName,
                    unit = if (weight != null) unit else "g",
                    unitGrams = weight?.grams ?: 100.0,
                    defaultConversion = weight?.isDefaultConversion ?: false,
                    quantity = 1.0, nutrients = emptyMap(), band = m.confidence.band, disclosure = m.disclosure,
                )
                withNutrients(row)
            }
            _state.update { it.copy(results = rows, searching = false) }
        }
    }

    /** The same row for another amount: the lookup computes it, this class only asks. */
    fun setQuantity(row: Row, quantity: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = withNutrients(row.copy(quantity = quantity)) ?: return@launch
            _state.update { s -> s.copy(results = s.results.map { if (it.code == row.code) updated else it }) }
        }
    }

    private suspend fun withNutrients(row: Row): Row? {
        val profile = (lookup.nutrientsFor(row.code, row.grams) as? Outcome.Ok)?.value ?: return row.copy(nutrients = emptyMap())
        return row.copy(nutrients = profile.values.mapNotNull { (n, v) -> (v as? NutrientValue.Measured)?.let { n to it.amount } }.toMap())
    }

    companion object {
        /** Typed search is in the catalogue's own names; the resolver's language-tagged aliases apply to speech. */
        const val LANGUAGE = "en-IN"
    }
}
