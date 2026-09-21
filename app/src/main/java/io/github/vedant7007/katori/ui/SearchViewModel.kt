package io.github.vedant7007.katori.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vedant7007.katori.data.food.FoodCode
import io.github.vedant7007.katori.data.food.FoodLookup
import io.github.vedant7007.katori.data.food.FoodMatch
import io.github.vedant7007.katori.data.food.FoodQuery
import io.github.vedant7007.katori.data.local.Diary
import io.github.vedant7007.katori.domain.MealResolver
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientProfile
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
 * FOOD SEARCH over the bundled database through the same [FoodLookup] the resolver uses for the
 * matches, and THE SAME [MealResolver] the plate uses for each match's portion: the household word
 * a person would say for it ("katori" for a dal, "piece" for a roti, "glass" for milk), the grams
 * behind it (a shipped default, said as such, 0035) and the nutrients for them, exactly as a
 * logged plate would show. A different portion is a new resolve, never a multiplication here.
 * "You log these often" is the most frequent `food_id` in the diary.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(private val lookup: FoodLookup, private val diary: Diary, private val resolver: MealResolver) : ViewModel() {

    data class Row(
        val match: FoodMatch,
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
            val rows = matches.mapNotNull { m -> portion(m, quantity = 1.0) }
            _state.update { it.copy(results = rows, searching = false) }
        }
    }

    /** The same row for another amount: the resolver computes it, this class only asks. */
    fun setQuantity(row: Row, quantity: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = portion(row.match, quantity) ?: return@launch
            _state.update { s -> s.copy(results = s.results.map { if (it.code == row.code) updated else it }) }
        }
    }

    /**
     * A match as a portion, THROUGH THE SAME RESOLVER THE PLATE USES: the row is resolved as if the
     * person had said its name with [quantity] and no unit, so the household word ("katori" for a
     * dal, "piece" for a roti, "glass" for milk), the grams behind it and the nutrients for them
     * are exactly what a logged plate would show (0035), and no second unit table exists here.
     * Resolves to a different food than the match: the match's own record per 100 g, said as such.
     */
    private suspend fun portion(m: FoodMatch, quantity: Double): Row? {
        val item = ParsedItem(m.displayName, quantity, unit = null, matchedFoodCode = m.code.id, confidence = ConfidenceRules.of(ConfidenceReason.QUANTITY_STATED))
        val resolved = (resolver.resolve(ParsedMeal(listOf(item), item.confidence, m.displayName), LANGUAGE) as? Outcome.Ok)?.value
        val r = resolved?.items?.singleOrNull()?.takeIf { it.snapshot.foodCode == m.code.id }
        val said = resolved?.parsed?.items?.singleOrNull()
        if (r == null || r.snapshot.grams == null || said?.unit == null) {
            val grams = 100.0 * quantity
            val profile = (lookup.nutrientsFor(m.code, grams) as? Outcome.Ok)?.value ?: return null
            return Row(m, m.code, m.displayName, unit = "g", unitGrams = 100.0, defaultConversion = false, quantity = quantity, nutrients = measured(profile), band = m.confidence.band, disclosure = m.disclosure)
        }
        return Row(
            match = m, code = m.code, name = r.snapshot.displayName, unit = said.unit, unitGrams = r.snapshot.grams / quantity,
            defaultConversion = ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT in r.confidence.reasons,
            quantity = quantity, nutrients = r.snapshot.nutrients, band = r.confidence.band, disclosure = m.disclosure,
        )
    }

    private fun measured(profile: NutrientProfile): Map<Nutrient, Double> =
        profile.values.mapNotNull { (n, v) -> (v as? NutrientValue.Measured)?.let { n to it.amount } }.toMap()

    companion object {
        /** Typed search is in the catalogue's own names; the resolver's language-tagged aliases apply to speech. */
        const val LANGUAGE = "en-IN"
    }
}
