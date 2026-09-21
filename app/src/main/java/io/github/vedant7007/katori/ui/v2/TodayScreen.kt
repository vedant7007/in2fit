package io.github.vedant7007.katori.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.TargetProgress
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.ui.ShownFigure
import io.github.vedant7007.katori.ui.ShownMeal
import io.github.vedant7007.katori.ui.TodayViewModel
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.num
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Today as drawn, every element, each bound to `TodayViewModel` or rendering its designed empty
 * state: the date and the greeting (the name when the profile has one), the bell and the
 * initial, the energy ring with the three macro bars, the "left today" line, the water and
 * last-meal cards, the two buttons, the nudge card, the safety line.
 *
 * EMPTY UNTIL ITS SOURCE LANDS, never a 0: the ring's fill, each bar's fill and denominator, and
 * the "left today" line wait on `Targets` (Priya's rule; the consumed figure alone shows until
 * then); the water card's denominator and its filled glasses wait on the water target, its
 * number on the first glass logged; the bell's dot waits on nudges; the initial waits on the name. The nudge
 * card carries the engine's own sentence for the last meal (the model's guarded one when it
 * passed), and is absent when no rule fired. WORDS: "dinner still to log" needs meal slots
 * (deferred), so the line ends at "kcal left today"; the pill cites the report by its printed
 * date, not by a marker's word (amendment 2).
 */
@Composable
fun TodayScreen(
    onNudges: () -> Unit,
    onProfile: () -> Unit,
    onLastMeal: (Long) -> Unit,
    onAddManually: () -> Unit,
    onDiary: () -> Unit,
    onCoach: () -> Unit,
    modifier: Modifier = Modifier,
    vm: TodayViewModel = hiltViewModel(),
) {
    val s = scheme()
    val state by vm.state.collectAsState()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Header(state.name, onNudges, onProfile)
        EnergyCard(state.totals, state.progress)
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WaterCard(state.waterMl, state.targets?.waterMl, Modifier.weight(1f)) { vm.logWater(250) }
            LastMealCard(state.lastMeal, Modifier.weight(1f), onLastMeal)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton(stringResource(R.string.v2_add_manually), Modifier.weight(1f), onAddManually)
            QuietButton(stringResource(R.string.v2_todays_diary), Modifier.weight(1f), onDiary)
        }
        val nudge = state.lastMealAdvice ?: state.lastMealTrigger
        if (nudge != null) NudgeCard(nudge, state.latestReport, onCoach)
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    }
}

@Composable
private fun Header(name: String?, onNudges: () -> Unit, onProfile: () -> Unit) {
    val s = scheme()
    val now = LocalTime.now()
    val greeting = stringResource(
        when {
            now.hour < 12 -> R.string.v2_greeting_morning
            now.hour < 17 -> R.string.v2_greeting_afternoon
            else -> R.string.v2_greeting_evening
        },
    )
    Row(Modifier.fillMaxWidth().padding(bottom = 26.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()).format(LocalDate.now()).uppercase(), micro(12f, 0.14.em, FontWeight.SemiBold), color = s.text3)
            T(if (name.isNullOrBlank()) greeting else stringResource(R.string.v2_greeting_with_name, greeting, name), serif(30f, 1.1f), color = s.text, modifier = Modifier.padding(top = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(40.dp).border(1.dp, s.text.copy(alpha = 0.10f), CircleShape).clickable(onClick = onNudges), contentAlignment = Alignment.Center) {
                Icon2(Glyphs.bell, 18.dp, s.button)
                // The unread dot waits on nudges (MISSING); nothing to show means no dot.
            }
            Box(
                Modifier
                    .size(40.dp)
                    .background(Brush.linearGradient(listOf(s.avatar, s.card)), CircleShape)
                    .border(1.dp, s.text.copy(alpha = 0.10f), CircleShape)
                    .clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) {
                val initial = name?.trim()?.firstOrNull()?.uppercase()
                if (initial != null) T(initial, sans(14f, FontWeight.SemiBold, 1f), color = s.accent)
            }
        }
    }
}

@Composable
private fun EnergyCard(totals: List<ShownFigure>, progress: List<TargetProgress>) {
    val s = scheme()
    val energy = totals.firstOrNull { it.nutrient == Nutrient.ENERGY }
    val energyTarget = progress.firstOrNull { it.nutrient == Nutrient.ENERGY }
    Card2(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = 26.dp) {
        Column(Modifier.padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                    val track = s.text.copy(alpha = 0.08f)
                    val fill = s.accent
                    // The ring clamps at a full turn for drawing (the fraction itself is unclamped).
                    val sweep = energyTarget?.let { (it.fraction.coerceIn(0.0, 0.999) * 360.0).toFloat() }
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 11.dp.toPx()
                        val inset = w / 2f
                        val arcSize = Size(size.width - w, size.height - w)
                        drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w))
                        if (sweep != null) drawArc(fill, -90f, sweep, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w, cap = StrokeCap.Butt))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Today's energy, the store's own total; nothing logged today means no number.
                        if (energy != null) T(fig(energy.amount), num(30f, FontWeight.Normal, besideSerif = true, lineHeight = 1f), color = s.text)
                        T(stringResource(R.string.v2_kcal_in).uppercase(), micro(10f, 0.12.em, FontWeight.SemiBold), color = s.text3, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    MacroBar(stringResource(R.string.v2_protein), totals.firstOrNull { it.nutrient == Nutrient.PROTEIN }, progress.firstOrNull { it.nutrient == Nutrient.PROTEIN }, s.accent)
                    MacroBar(stringResource(R.string.v2_carbs), totals.firstOrNull { it.nutrient == Nutrient.CARBOHYDRATE }, progress.firstOrNull { it.nutrient == Nutrient.CARBOHYDRATE }, s.teal)
                    MacroBar(stringResource(R.string.v2_fat), totals.firstOrNull { it.nutrient == Nutrient.FAT }, progress.firstOrNull { it.nutrient == Nutrient.FAT }, s.warm)
                }
            }
            // "412 kcal left today": the engine's remaining figure; absent without a target.
            if (energyTarget != null) {
                Box(Modifier.fillMaxWidth().padding(top = 20.dp).height(1.dp).background(s.text.copy(alpha = 0.07f)))
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    T(fig(energyTarget.remaining.coerceAtLeast(0.0)), num(26f, FontWeight.Normal, besideSerif = true, lineHeight = 1f), color = s.accent)
                    T(stringResource(R.string.v2_kcal_left_today), sans(13f, FontWeight.Normal, 1.2f), color = s.text2, modifier = Modifier.padding(bottom = 1.dp))
                }
            }
        }
    }
}

/** One macro as drawn: the label, "57 / 95 g" (or "57 g" with no target), the 6 dp track and its fill. */
@Composable
private fun MacroBar(label: String, figure: ShownFigure?, progress: TargetProgress?, color: Color) {
    val s = scheme()
    val consumed = progress?.consumed ?: figure?.amount
    val value = when {
        progress != null -> stringResource(R.string.v2_macro_of, fig(progress.consumed), fig(progress.target))
        consumed != null -> stringResource(R.string.v2_macro_alone, fig(consumed))
        else -> null
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            T(label, sans(13f, FontWeight.Normal, 1.2f), color = s.quote)
            if (value != null) T(value, sans(13f, FontWeight.SemiBold, 1.2f), color = s.text, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(s.text.copy(alpha = 0.08f), RoundedCornerShape(3.dp))) {
            if (progress != null) {
                Box(Modifier.fillMaxWidth(progress.fraction.coerceIn(0.0, 1.0).toFloat()).height(6.dp).background(color, RoundedCornerShape(3.dp)))
            }
        }
    }
}

/**
 * Water as drawn: the litres today in serif 28 (Plex) with "/ 3.0 L" beside it when the rule
 * has a water target, six glasses each a sixth of that target, and "+" for a glass of 250 ml
 * (the design's 0.25). No water logged today shows no number; no target shows no denominator
 * and no filled glass, and the six stay drawn empty.
 */
@Composable
private fun WaterCard(waterMl: Int?, targetMl: Double?, modifier: Modifier, onAdd: () -> Unit) {
    val s = scheme()
    Card2(modifier, radius = 22.dp) {
        Column(Modifier.padding(18.dp)) {
            T(stringResource(R.string.v2_water).uppercase(), micro(12f, 0.10.em, FontWeight.SemiBold), color = s.text3)
            Row(Modifier.padding(top = 10.dp, bottom = 12.dp).heightIn(min = 30.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                if (waterMl != null) T(litres(waterMl.toDouble()), num(28f, FontWeight.Normal, besideSerif = true, lineHeight = 1f), color = s.text)
                if (targetMl != null) T(stringResource(R.string.v2_water_of, litres(targetMl)), sans(13f, FontWeight.Normal, 1.2f), color = s.text2, modifier = Modifier.padding(bottom = 2.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                val filled = if (waterMl != null && targetMl != null && targetMl > 0) Math.round(waterMl / targetMl * 6).toInt() else 0
                repeat(6) { i ->
                    val on = i < filled
                    Box(
                        Modifier.weight(1f).height(26.dp)
                            .background(if (on) s.accent.copy(alpha = 0.22f) else s.text.copy(alpha = 0.04f), RoundedCornerShape(7.dp))
                            .border(1.dp, if (on) s.accent.copy(alpha = 0.40f) else s.text.copy(alpha = 0.07f), RoundedCornerShape(7.dp)),
                    )
                }
                Box(Modifier.size(26.dp).background(s.accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp)).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                    T(stringResource(R.string.v2_plus), sans(16f, FontWeight.Normal, 1f), color = s.accent)
                }
            }
        }
    }
}

/** Millilitres as the design's litres, one decimal: 1600 -> 1.6, 3000 -> 3.0. */
private fun litres(ml: Double): String = String.format(Locale.ROOT, "%.1f", ml / 1000.0)

@Composable
private fun LastMealCard(meal: ShownMeal?, modifier: Modifier, onOpen: (Long) -> Unit) {
    val s = scheme()
    Card2(modifier, radius = 22.dp, onClick = meal?.let { { onOpen(it.id) } }) {
        Column(Modifier.padding(18.dp)) {
            T(stringResource(R.string.v2_last_meal).uppercase(), micro(12f, 0.10.em, FontWeight.SemiBold), color = s.text3)
            if (meal != null) {
                T(meal.items.joinToString(", ") { it.name }, sans(15f, FontWeight.SemiBold, 1.3f), color = s.text, modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
                val energy = meal.energy
                val protein = meal.figures.firstOrNull { it.nutrient == Nutrient.PROTEIN }
                val detail = listOfNotNull(
                    stringResource(R.string.v2_items_count, meal.items.size),
                    energy?.let { stringResource(R.string.v2_item_kcal, fig(it.amount)) },
                    protein?.let { stringResource(R.string.v2_item_protein, fig(it.amount)) },
                ).joinToString(" · ")
                T(detail, sans(12.5f, FontWeight.Normal, 1.45f), color = s.text2)
                T(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(meal.loggedAt.toEpochMilli())), sans(12f, FontWeight.Normal, 1.2f), color = s.text3, modifier = Modifier.padding(top = 10.dp))
            } else {
                // Nothing logged yet: the slot keeps its height and says nothing.
                Spacer(Modifier.height(10.dp + 20.dp + 3.dp + 18.dp + 10.dp + 14.dp))
            }
        }
    }
}

@Composable
private fun QuietButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val s = scheme()
    Card2(modifier, radius = 18.dp, onClick = onClick) {
        Box(Modifier.fillMaxWidth().padding(15.dp), contentAlignment = Alignment.Center) {
            T(text, sans(13.5f, FontWeight.SemiBold, 1.2f), color = s.button, maxLines = 1)
        }
    }
}

@Composable
private fun NudgeCard(text: String, report: LocalDate?, onCoach: () -> Unit) {
    val s = scheme()
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(0f to s.glowStart, 0.7f to s.card), shape)
            .border(1.dp, s.accent.copy(alpha = 0.18f), shape)
            .padding(20.dp),
    ) {
        if (report != null) {
            Row(
                Modifier.padding(bottom = 12.dp).background(s.accent.copy(alpha = 0.12f), RoundedCornerShape(100.dp)).padding(horizontal = 11.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).background(s.accent, CircleShape))
                T(stringResource(R.string.v2_reading_report, DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(report)), sans(11f, FontWeight.SemiBold, 1.2f), color = s.accent)
            }
        }
        T(text, sans(15.5f, FontWeight.Normal, 1.55f), color = Color(0xFFE8E6DF).takeIf { s.isDark } ?: s.text)
        Row(Modifier.padding(top = 16.dp).clickable(onClick = onCoach), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(stringResource(R.string.v2_ask_about_this), sans(14f, FontWeight.SemiBold, 1.2f), color = s.accent)
            Icon2(Glyphs.arrowRight, 14.dp, s.accent)
        }
    }
}
