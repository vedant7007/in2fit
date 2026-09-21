package io.github.vedant7007.katori.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vedant7007.katori.data.food.FoodDbSource
import io.github.vedant7007.katori.data.food.dbl
import io.github.vedant7007.katori.data.food.str
import io.github.vedant7007.katori.data.local.ProfileStore
import io.github.vedant7007.katori.data.local.entity.ConditionEntity
import io.github.vedant7007.katori.data.local.entity.ProfileEntity
import io.github.vedant7007.katori.data.local.entity.ReminderEntity
import io.github.vedant7007.katori.data.local.entity.WeightEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * YOU (profile, first run and settings): the row as declared, the conditions declared or read
 * off a report (each with its source, because the wording differs, spec 15.2), the speech
 * language, and the bundled household units the resolver converts with, shown as the shipped
 * defaults they are (0035). [save] is the profile's one write path, through [ProfileStore].
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(private val store: ProfileStore, private val foods: FoodDbSource) : ViewModel() {

    /** A shipped default: a katori of dal is 150 g in the bundle, and the screen says "bundled", never "your". */
    data class HouseholdUnit(val unit: String, val foodClass: String, val grams: Double)

    data class State(
        val loaded: Boolean = false,
        /** Null until read, and null when no row has ever been written: that is first run. */
        val profile: ProfileEntity? = null,
        val conditions: List<ConditionEntity> = emptyList(),
        /** A `SpeechLanguage.tag`; Hindi until chosen. */
        val language: String = ProfileStore.DEFAULT_LANGUAGE,
        val units: List<HouseholdUnit> = emptyList(),
        /** Every weight entered, oldest first. */
        val weights: List<WeightEntity> = emptyList(),
        /** The in-app reminder list, by time of day. */
        val reminders: List<ReminderEntity> = emptyList(),
    ) {
        val firstRun: Boolean get() = loaded && profile == null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val units = runCatching { foods.query("SELECT unit, food_class, grams FROM unit_conversions ORDER BY food_class, unit") }
                .getOrDefault(emptyList()).map { HouseholdUnit(it.str("unit"), it.str("food_class"), it.dbl("grams")) }
            _state.update { it.copy(units = units) }
            combine(store.profile, store.conditions, store.speechLanguage, store.weights, store.reminders) { p, c, l, w, r ->
                State(loaded = true, profile = p, conditions = c, language = l, units = units, weights = w, reminders = r)
            }.collect { _state.value = it }
        }
    }

    fun save(
        name: String?, ageYears: Int?, weightKg: Double?, heightCm: Double?, sex: String?, activity: String?,
        goal: String?, lifeContext: String?, dietType: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) { store.save(name, ageYears, weightKg, heightCm, sex, activity, goal, lifeContext, dietType) }
    }

    fun setLanguage(tag: String) {
        viewModelScope.launch(Dispatchers.IO) { store.setSpeechLanguage(tag) }
    }

    fun declareCondition(name: String) {
        viewModelScope.launch(Dispatchers.IO) { store.declareCondition(name) }
    }

    fun removeCondition(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { store.removeCondition(id) }
    }

    fun recordWeight(kg: Double) {
        viewModelScope.launch(Dispatchers.IO) { store.recordWeight(kg) }
    }

    fun setReminder(reminder: ReminderEntity) {
        viewModelScope.launch(Dispatchers.IO) { store.setReminder(reminder) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { store.deleteReminder(id) }
    }
}
