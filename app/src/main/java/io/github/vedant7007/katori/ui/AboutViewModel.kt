package io.github.vedant7007.katori.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vedant7007.katori.data.food.FoodDbSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The USDA attribution, licence and disclosure travel with the data they describe, as rows in
 * the food database's `meta` table (`0002`; `docs/localisation/string-conventions.md`). They are
 * read from there, never typed a second time.
 */
@HiltViewModel
class AboutViewModel @Inject constructor(private val foods: FoodDbSource) : ViewModel() {

    data class Meta(val attribution: String = "", val licence: String = "", val disclosure: String = "", val releases: String = "")

    private val _meta = MutableStateFlow(Meta())
    val meta: StateFlow<Meta> = _meta.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = runCatching { foods.query("SELECT key, value FROM meta") }.getOrDefault(emptyList())
                .associate { it["key"].toString() to it["value"].toString() }
            _meta.value = Meta(
                attribution = rows["attribution"].orEmpty(),
                licence = rows["licence"].orEmpty(),
                disclosure = rows["disclosure"].orEmpty(),
                releases = listOfNotNull(rows["sr_legacy_release"]?.let { "SR Legacy $it" }, rows["foundation_release"]?.let { "Foundation $it" }).joinToString(", "),
            )
        }
    }
}
