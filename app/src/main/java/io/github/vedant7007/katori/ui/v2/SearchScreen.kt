package io.github.vedant7007.katori.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.ui.SearchViewModel
import io.github.vedant7007.katori.ui.Sentences
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.num
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif

/**
 * Search as drawn: the back circle and the search pill, "YOU LOG THESE OFTEN" or "N MATCHES",
 * the dashed no-match card with "Log by voice", one 18 dp row per match (the name, the usual
 * household unit with its grams said as a bundled default, the energy for that portion), and
 * the food page: the name in serif 30, the source line, the Portion card (−, the amount in
 * serif 27 with the grams under it, +, three presets), the figures for that portion, and
 * "Add to diary".
 *
 * NOT AS DRAWN, and why: "IFCT 2017 food table" is the record's own source name (rule 14); the
 * food page's "Glycaemic load · Low" has no source and is not drawn; the advice card ("Good pick
 * for you…") is a sentence the model would write for a food and is absent; "Add to diary" waits
 * on Rao's `UserIntent.LogItems` ((w)) and is drawn and dimmed; "Log by voice" opens Coach, where
 * the microphone is, because a tap cannot hold it. A different portion is a new lookup call
 * (`setQuantity`), never arithmetic on the screen.
 */
@Composable
fun SearchScreen(onBack: () -> Unit, onVoice: () -> Unit, modifier: Modifier = Modifier, vm: SearchViewModel = hiltViewModel()) {
    val s = scheme()
    val state by vm.state.collectAsState()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val row = state.results.firstOrNull { it.code.id == open }
    if (row != null) {
        FoodPage(row, onBack = { open = null }, onQuantity = { vm.setQuantity(row, it) }, modifier = modifier)
        return
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).border(1.dp, s.text.copy(alpha = 0.12f), CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Icon2(Glyphs.arrowLeft, 15.dp, s.text) }
            Row(
                Modifier.weight(1f).background(s.card, RoundedCornerShape(100.dp)).border(1.dp, s.text.copy(alpha = 0.09f), RoundedCornerShape(100.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon2(Glyphs.search, 16.dp, s.text3)
                BasicTextField(
                    value = state.query, onValueChange = vm::search, modifier = Modifier.weight(1f),
                    textStyle = sans(14.5f, FontWeight.Normal, 1.3f, s.text), cursorBrush = SolidColor(s.accent), singleLine = true,
                    decorationBox = { inner -> Box { if (state.query.isEmpty()) T(stringResource(R.string.v2_search_foods), sans(14.5f, FontWeight.Normal, 1.3f), color = s.text3); inner() } },
                )
            }
        }
        val searching = state.query.isNotBlank()
        val heading = when {
            !searching -> stringResource(R.string.v2_log_these_often)
            state.results.size == 1 -> stringResource(R.string.v2_one_match)
            else -> stringResource(R.string.v2_n_matches, state.results.size)
        }
        if (!searching && state.frequent.isEmpty()) {
            // Nothing logged yet: no heading, no rows; the search pill is the whole page.
        } else {
            T(heading.uppercase(), micro(11f, 0.12.em, FontWeight.SemiBold), color = s.text3, modifier = Modifier.padding(bottom = 12.dp))
        }
        if (searching && !state.searching && state.results.isEmpty()) {
            val line = s.text.copy(alpha = 0.14f)
            Column(
                Modifier.fillMaxWidth().drawBehind { drawRoundRect(line, cornerRadius = CornerRadius(22.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))) }.padding(horizontal = 20.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(stringResource(R.string.v2_no_match), sans(14.5f, FontWeight.Normal, 1.5f), color = s.onMessage, textAlign = TextAlign.Center)
                T(stringResource(R.string.v2_no_match_sub), sans(13f, FontWeight.Normal, 1.5f), color = s.text2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 7.dp))
                Pill2(stringResource(R.string.v2_log_by_voice), onClick = onVoice, modifier = Modifier.padding(top = 16.dp), size = 14f, vertical = 12.dp, horizontal = 22.dp)
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (searching) {
                state.results.forEach { r ->
                    Card2(Modifier.fillMaxWidth(), radius = 18.dp, onClick = { open = r.code.id }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                T(r.name, sans(14.5f, FontWeight.SemiBold, 1.3f), color = s.text)
                                T(portionLine(r), sans(12.5f, FontWeight.Normal, 1.3f), color = s.text2, modifier = Modifier.padding(top = 4.dp))
                            }
                            val e = r.nutrients[Nutrient.ENERGY]
                            if (e != null) N(stringResource(R.string.v2_item_kcal, fig(e)), sans(14f, FontWeight.SemiBold, 1.2f), color = s.text, modifier = Modifier.weight(1f, fill = false))
                        }
                    }
                }
            } else {
                // The names logged most often; a tap searches for one.
                state.frequent.forEach { name ->
                    Card2(Modifier.fillMaxWidth(), radius = 18.dp, onClick = { vm.search(name) }) {
                        T(name, sans(14.5f, FontWeight.SemiBold, 1.3f), color = s.text, modifier = Modifier.padding(horizontal = 17.dp, vertical = 15.dp))
                    }
                }
            }
        }
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    }
}

/** "1 katori · 180 g" as the design writes it; a class with no unit is "100 g". */
@Composable
private fun portionLine(r: SearchViewModel.Row): String =
    if (r.unit == "g") stringResource(R.string.v2_grams, fig(r.grams))
    else Sentences.number(r.quantity) + " " + r.unit + " · " + stringResource(R.string.v2_grams, fig(r.grams))

@Composable
private fun FoodPage(row: SearchViewModel.Row, onBack: () -> Unit, onQuantity: (Double) -> Unit, modifier: Modifier) {
    val s = scheme()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Row(Modifier.clickable(onClick = onBack).padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon2(Glyphs.arrowLeft, 14.dp, s.text2)
            T(stringResource(R.string.v2_back), sans(13.5f, FontWeight.Normal, 1.2f), color = s.text2)
        }
        T(row.name, serif(30f, 1.15f), color = s.text)
        // The record's own source (rule 14: the database is named, never "IFCT").
        T(row.code.source.displayName, sans(13f, FontWeight.Normal, 1.3f), color = s.text2, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        Card2(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = 26.dp) {
            Column(Modifier.padding(22.dp)) {
                T(stringResource(R.string.v2_portion).uppercase(), micro(11f, 0.12.em, FontWeight.SemiBold), color = s.text3, modifier = Modifier.padding(bottom = 16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Stepper(stringResource(R.string.v2_minus), enabled = row.quantity > 0.5) { onQuantity(row.quantity - 0.5) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        N(if (row.unit == "g") stringResource(R.string.v2_grams, fig(row.grams)) else Sentences.number(row.quantity) + " " + row.unit, num(27f, FontWeight.Normal, besideSerif = true, lineHeight = 1.1f), color = s.text)
                        T(
                            if (row.defaultConversion) stringResource(R.string.v2_grams_bundled, fig(row.grams)) else stringResource(R.string.v2_grams, fig(row.grams)),
                            sans(12.5f, FontWeight.Normal, 1.2f), color = s.accent, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Stepper(stringResource(R.string.v2_plus), enabled = true) { onQuantity(row.quantity + 0.5) }
                }
                FlowRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5, 1.0, 1.5).forEach { q ->
                        Chip2(Sentences.number(q) + " " + row.unit, on = row.quantity == q, size = 12.5f, vertical = 9.dp, horizontal = 14.dp) { onQuantity(q) }
                    }
                }
            }
        }
        Card2(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = 26.dp) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
                listOf(Nutrient.ENERGY to R.string.v2_energy, Nutrient.PROTEIN to R.string.v2_protein, Nutrient.CARBOHYDRATE to R.string.v2_carbs, Nutrient.FAT to R.string.v2_fat).forEach { (n, label) ->
                    val v = row.nutrients[n]
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(s.text.copy(alpha = 0.06f)))
                        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            T(stringResource(label), sans(14f, FontWeight.Normal, 1.3f), color = s.quote)
                            // An unmeasured nutrient shows nothing, never 0.
                            if (v != null) T(if (n == Nutrient.ENERGY) stringResource(R.string.v2_item_kcal, fig(v)) else stringResource(R.string.v2_macro_alone, fig(v)), sans(14.5f, FontWeight.SemiBold, 1.3f), color = if (n == Nutrient.PROTEIN) s.accent else s.text)
                        }
                    }
                }
            }
        }
        // "Add to diary" waits on the LogItems intent (Rao, (w)): drawn, dimmed.
        Box(Modifier.fillMaxWidth().alpha(0.32f)) { Pill2(stringResource(R.string.v2_add_to_diary), onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(), size = 15f, vertical = 17.dp) }
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    }
}

@Composable
private fun Stepper(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val s = scheme()
    Box(Modifier.size(44.dp).border(1.dp, s.text.copy(alpha = 0.14f), CircleShape).clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.32f), contentAlignment = Alignment.Center) {
        T(glyph, sans(20f, FontWeight.Normal, 1f), color = s.text)
    }
}
