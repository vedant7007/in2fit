package io.github.vedant7007.katori.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.ui.DiaryViewModel
import io.github.vedant7007.katori.ui.Sentences
import io.github.vedant7007.katori.ui.ShownMeal
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.num
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Diary as drawn: the serif title with "1180 kcal logged · 4 entries" under it and the Trends
 * pill beside it, the seven-day strip (a tick for a day with a meal, a dot for the chosen day,
 * a dash for none; a tap selects the day), one 22 dp card per meal (the time in the tracked
 * label, the items' names, "2 items", the energy in serif 22 (Plex) with "kcal" under it, and
 * under a hairline the engine's own flag when a rule fired), then the dashed "Log a meal".
 *
 * WORDS: the design's "Breakfast · 9:05 am" needs meal slots (deferred to Priya); the label is
 * the time. "1.5 katori · 210 g" is the item's portion line on the meal itself; here the count
 * of items, as the design's third row does ("3 items · voice logged"), without "voice logged"
 * because the source is not on `ShownMeal`. "Log dinner" is "Log a meal" for the same reason.
 */
@Composable
fun DiaryScreen(onTrends: () -> Unit, onMeal: (Long) -> Unit, onLog: () -> Unit, modifier: Modifier = Modifier, vm: DiaryViewModel = hiltViewModel()) {
    val s = scheme()
    val state by vm.state.collectAsState()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T(stringResource(R.string.v2_tab_diary), serif(27f, 1.2f), color = s.text)
                val energy = state.energy
                val line = listOfNotNull(
                    energy?.let { stringResource(R.string.v2_kcal_logged, fig(it.amount)) },
                    state.entries.size.takeIf { it > 0 }?.let { stringResource(R.string.v2_entries, it) },
                ).joinToString(" · ")
                if (line.isNotEmpty()) T(line, sans(13f, FontWeight.Normal, 1.3f), color = s.text2, modifier = Modifier.padding(top = 3.dp))
            }
            Box(Modifier.border(1.dp, s.text.copy(alpha = 0.12f), RoundedCornerShape(100.dp)).clickable(onClick = onTrends).padding(horizontal = 15.dp, vertical = 10.dp)) {
                T(stringResource(R.string.v2_trends), sans(13f, FontWeight.SemiBold, 1.2f), color = s.button)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            state.week.forEach { (date, logged) ->
                val chosen = date == state.date
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.weight(1f)
                        .background(if (chosen) s.accent.copy(alpha = 0.12f) else s.text.copy(alpha = 0.03f), shape)
                        .border(1.dp, if (chosen) s.accent.copy(alpha = 0.3f) else s.text.copy(alpha = 0.06f), shape)
                        .clickable { vm.select(date) }
                        .padding(vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    T(date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), sans(10.5f, FontWeight.SemiBold, 1.2f), color = s.text3)
                    T(
                        stringResource(if (logged) R.string.v2_day_logged else if (chosen) R.string.v2_day_today else R.string.v2_day_none),
                        sans(14f, FontWeight.SemiBold, 1.2f),
                        color = if (logged) s.accent else if (chosen) s.text else s.dim,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.entries.forEach { entry -> MealCard(entry.meal, entry.flag) { onMeal(entry.meal.id) } }
            val line = s.text.copy(alpha = 0.14f)
            Box(
                Modifier.fillMaxWidth()
                    .drawBehind { drawRoundRect(line, cornerRadius = CornerRadius(22.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))) }
                    .clickable(onClick = onLog).padding(18.dp),
                contentAlignment = Alignment.Center,
            ) { T(stringResource(R.string.v2_log_a_meal), sans(14f, FontWeight.SemiBold, 1.2f), color = s.text2) }
        }
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    }
}

@Composable
private fun MealCard(meal: ShownMeal, flag: String?, onOpen: () -> Unit) {
    val s = scheme()
    Card2(Modifier.fillMaxWidth(), radius = 22.dp, onClick = onOpen) {
        Column(Modifier.padding(horizontal = 19.dp, vertical = 17.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(timeOf(meal).uppercase(), micro(11f, 0.12.em, FontWeight.SemiBold), color = s.text3)
                    T(meal.items.joinToString(", ") { it.name }, sans(15.5f, FontWeight.SemiBold, 1.3f), color = s.text, modifier = Modifier.padding(top = 7.dp))
                    T(stringResource(R.string.v2_items_count, meal.items.size), sans(12.5f, FontWeight.Normal, 1.3f), color = s.text2, modifier = Modifier.padding(top = 5.dp))
                }
                val energy = meal.energy
                if (energy != null) {
                    Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.End) {
                        N(fig(energy.amount), num(22f, FontWeight.Normal, besideSerif = true, lineHeight = 1.1f), color = s.text)
                        T(stringResource(R.string.v2_kcal), sans(11f, FontWeight.Normal, 1.2f), color = s.text3)
                    }
                }
            }
            if (flag != null) {
                Box(Modifier.fillMaxWidth().padding(top = 13.dp).height(1.dp).background(s.text.copy(alpha = 0.06f)))
                T(flag, sans(12.5f, FontWeight.Normal, 1.45f), color = s.warm, modifier = Modifier.padding(top = 13.dp))
            }
        }
    }
}

fun timeOf(meal: ShownMeal): String = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(meal.loggedAt.toEpochMilli()))

/**
 * The meal as drawn: Back, the time in the tracked label, the items' names in serif 30, the
 * four totals (kcal, protein, carbs, fat, in serif 21 / Plex), one card per item with the
 * portion chip (and "taken as N g" only when assumed, 0035) and its own energy and protein,
 * the note card, then "Delete meal" (a real delete) and "Edit portions".
 *
 * NOT AS DRAWN, and why: the note ("Logged by voice at 1:42 pm. Portions used your katori.
 * Confidence is high on roti…") is the meal's band, said once, in the same card style; the
 * design's sentence names a learned katori and confidence words the system does not use.
 * "Edit portions" opens nothing yet (Rao's `CorrectValue`), so it is drawn and dimmed.
 */
@Composable
fun MealScreen(mealId: Long, date: LocalDate?, onBack: () -> Unit, modifier: Modifier = Modifier, vm: DiaryViewModel = hiltViewModel()) {
    val s = scheme()
    val state by vm.state.collectAsState()
    // A meal opened from Today may be on another day than the diary is showing.
    androidx.compose.runtime.LaunchedEffect(date) { if (date != null) vm.select(date) }
    val meal = state.entries.firstOrNull { it.meal.id == mealId }?.meal
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Row(Modifier.clickable(onClick = onBack).padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon2(Glyphs.arrowLeft, 14.dp, s.text2)
            T(stringResource(R.string.v2_back), sans(13.5f, FontWeight.Normal, 1.2f), color = s.text2)
        }
        if (meal != null) {
            T(timeOf(meal).uppercase(), micro(11.5f, 0.14.em, FontWeight.SemiBold), color = s.text3)
            T(meal.items.joinToString(", ") { it.name }, serif(30f, 1.2f), color = s.text, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
            // The four totals in one row as drawn; two rows of two at a large font scale, so no
            // figure and no label is cut (hostile pass, 21 Sep).
            val totals = listOf(Nutrient.ENERGY to R.string.v2_kcal, Nutrient.PROTEIN to R.string.v2_protein, Nutrient.CARBOHYDRATE to R.string.v2_carbs, Nutrient.FAT to R.string.v2_fat)
            val perRow = if (androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.3f) 4 else 2
            totals.chunked(perRow).forEach { rowOf ->
                Row(Modifier.fillMaxWidth().padding(bottom = if (perRow == 4) 16.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowOf.forEach { (n, label) ->
                        val f = meal.figures.firstOrNull { it.nutrient == n }
                        Card2(Modifier.weight(1f), radius = 18.dp) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (f != null) N(fig(f.amount), num(21f, FontWeight.Normal, besideSerif = true, lineHeight = 1.1f), color = s.text)
                                N(stringResource(label).uppercase(), micro(10.5f, 0.06.em, FontWeight.SemiBold), color = s.text3, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            if (perRow == 2) Box(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                meal.items.forEach { item ->
                    val amount = item.quantity?.let { q -> listOfNotNull(Sentences.number(q), item.unit).joinToString(" ") }
                    val takenAs = if (item.inferred && item.grams != null) stringResource(R.string.plate_unit_taken_as, fig(item.grams)) else null
                    val energy = item.nutrients[Nutrient.ENERGY]
                    val protein = item.nutrients[Nutrient.PROTEIN]
                    Card2(Modifier.fillMaxWidth(), radius = 20.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                T(item.name, sans(15f, FontWeight.SemiBold, 1.3f), color = s.text)
                                if (amount != null || takenAs != null) PortionChip(amount, takenAs)
                            }
                            Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.End) {
                                if (energy != null) N(stringResource(R.string.v2_item_kcal, fig(energy)), sans(15f, FontWeight.SemiBold, 1.2f), color = s.text)
                                if (protein != null) N(stringResource(R.string.v2_item_protein, fig(protein)), sans(11.5f, FontWeight.Normal, 1.2f), color = s.text3, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                }
            }
            // The note card: the meal's band, said once, and the time it was logged.
            val band = meal.figures.maxOfOrNull { it.band }
            if (band != null) {
                Card2(Modifier.fillMaxWidth().padding(bottom = 16.dp), radius = 22.dp) {
                    T(stringResource(R.string.v2_meal_note, timeOf(meal), stringResource(Sentences.band(band))), sans(13.5f, FontWeight.Normal, 1.55f), color = s.quote, modifier = Modifier.padding(18.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).border(1.dp, s.warm.copy(alpha = 0.35f), RoundedCornerShape(100.dp)).clickable { vm.delete(meal.id); onBack() }.padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { T(stringResource(R.string.v2_delete_meal), sans(14.5f, FontWeight.SemiBold, 1.2f), color = s.warm) }
                // Edit portions waits on the correction path (Rao's CorrectValue): drawn, dimmed.
                Box(Modifier.weight(1f).alpha(0.32f)) { Pill2(stringResource(R.string.v2_edit_portions), onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(), horizontal = 16.dp) }
            }
        }
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    }
}
