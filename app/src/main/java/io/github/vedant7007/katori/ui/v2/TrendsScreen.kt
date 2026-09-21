package io.github.vedant7007.katori.ui.v2

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.ProfileViewModel
import io.github.vedant7007.katori.ui.TodayViewModel
import io.github.vedant7007.katori.ui.TrendsViewModel
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.num
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * This week as drawn: Back to Diary, the serif title and the date span, "Calories vs goal" with
 * seven bars (each day's energy, the tallest at 112 dp; a day with nothing logged has no bar,
 * never a zero bar), the four stat cards, then "What the coach noticed".
 *
 * WHAT EACH CARD CARRIES, and what it does not: Avg calories = the mean over the days with a
 * complete energy figure, "over N days" under it (the design's "137 under goal" needs a target:
 * absent until the rule); "Protein hit 3/7 days at target" needs a target and is drawn empty
 * until then; Logged meals = the count, "N days logged" under it (the design's "22 by voice"
 * needs the meal's source on the day rows, not yet there); Weight = the change between the
 * first and the latest weight entered, "since <date>", absent with fewer than two entries. A
 * bar is coloured against no goal, so every bar is the accent (the design colours over-goal
 * days warm). "What the coach noticed" is a weekly summary the system does not write yet
 * (Priya's (u), Arjun's templates) and the card is absent until it does.
 */
@Composable
fun TrendsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, vm: TrendsViewModel = hiltViewModel(), profile: ProfileViewModel = hiltViewModel(), today: TodayViewModel = hiltViewModel()) {
    val s = scheme()
    val state by vm.state.collectAsState()
    val p by profile.state.collectAsState()
    val t by today.state.collectAsState()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Row(Modifier.clickable(onClick = onBack).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon2(Glyphs.arrowLeft, 14.dp, s.text2)
            T(stringResource(R.string.v2_tab_diary), sans(13.5f, FontWeight.Normal, 1.2f), color = s.text2)
        }
        T(stringResource(R.string.v2_this_week), serif(27f, 1.2f), color = s.text, modifier = Modifier.padding(bottom = 4.dp))
        val first = state.days.firstOrNull()?.date
        val last = state.days.lastOrNull()?.date
        if (first != null && last != null) {
            val d = DateTimeFormatter.ofPattern("d", Locale.getDefault())
            val dm = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
            T(stringResource(R.string.v2_date_span, if (first.month == last.month) d.format(first) else dm.format(first), dm.format(last)), sans(13f, FontWeight.Normal, 1.3f), color = s.text2, modifier = Modifier.padding(bottom = 22.dp))
        }
        Card2(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = 26.dp) {
            Column(Modifier.padding(22.dp)) {
                T(stringResource(R.string.v2_calories_vs_goal).uppercase(), micro(12f, 0.10.em, FontWeight.SemiBold), color = s.text3, modifier = Modifier.padding(bottom = 18.dp))
                val energies = state.days.map { it.energy?.amount }
                val top = energies.filterNotNull().maxOrNull()?.takeIf { it > 0 }
                Row(Modifier.fillMaxWidth().height(128.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
                    state.days.forEachIndexed { i, day ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            val e = energies[i]
                            if (e != null && top != null) {
                                Box(Modifier.fillMaxWidth().height(112.dp * (e / top).toFloat()).background(s.accent, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)))
                            }
                            T(day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), sans(10.5f, FontWeight.SemiBold, 1.2f), color = s.text3)
                        }
                    }
                }
            }
        }
        val weights = p.weights
        val weightChange = if (weights.size >= 2) weights.last().kg - weights.first().kg else null
        val stats: List<Triple<Int, String?, String?>> = listOf(
            Triple(R.string.v2_avg_calories, state.averageEnergyKcal?.let { fig(Math.rint(it)) }, state.averagedDays.takeIf { it > 0 }?.let { stringResource(R.string.v2_over_n_days, it) }),
            Triple(R.string.v2_protein_hit, null, null),
            Triple(R.string.v2_logged_meals, state.mealCount.takeIf { it > 0 }?.toString(), state.daysLogged.takeIf { it > 0 }?.let { stringResource(R.string.v2_n_days_logged, it) }),
            Triple(R.string.v2_weight, weightChange?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + stringResource(R.string.v2_kg, fig(Math.abs(it))) }, weights.firstOrNull()?.let { stringResource(R.string.v2_since, DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(java.time.Instant.ofEpochMilli(it.recorded_at_epoch_ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate())) }),
        )
        stats.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, value, note) ->
                    Card2(Modifier.weight(1f), radius = 22.dp) {
                        Column(Modifier.padding(18.dp)) {
                            T(stringResource(label).uppercase(), micro(11f, 0.10.em, FontWeight.SemiBold), color = s.text3)
                            Box(Modifier.padding(top = 9.dp, bottom = 4.dp).height(32.dp), contentAlignment = Alignment.CenterStart) {
                                if (value != null) T(value, num(27f, FontWeight.Normal, besideSerif = true, lineHeight = 1.1f), color = if (label == R.string.v2_weight) s.accent else s.text, maxLines = 1)
                            }
                            if (note != null) T(note, sans(12f, FontWeight.Normal, 1.4f), color = s.text2)
                        }
                    }
                }
            }
        }
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
    }
}
