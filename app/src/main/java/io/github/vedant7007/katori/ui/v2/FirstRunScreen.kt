package io.github.vedant7007.katori.ui.v2

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.LabStatus
import io.github.vedant7007.katori.ui.ProfileViewModel
import io.github.vedant7007.katori.ui.TodayViewModel
import io.github.vedant7007.katori.ui.theme.LocalReduceMotion
import io.github.vedant7007.katori.ui.theme.Plex
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif

/** The design's steps without the phone and the code (override: the local first run replaces sign-in). */
enum class Step { LANG, ABOUT, CONDITIONS, REPORT, SCAN, MIC, READY }

/**
 * First run as drawn: the back circle, the 3 dp progress bar and "n/7", the serif 31 title and
 * its line, then one step at a time. Language writes `setLanguage`; About you writes
 * `ProfileStore.save` (age, weight, height, activity as typed, the goal from the domain's own
 * vocabulary); Conditions writes `declareCondition` / `removeCondition`; the report step opens
 * the scan screen (photo or PDF) or is skipped; the microphone step asks the system for the
 * permission or is skipped; Ready shows what will be used from today, each row the store's or
 * nothing, and "Start logging" goes in.
 *
 * NOT AS DRAWN, and why: the design's "Reverse prediabetes" goal has no place in the domain's
 * goals (a condition, not a goal) and is not drawn; the goals are the domain's four. Activity
 * is stored as typed ("3", days a week) until Priya names the levels her rule reads. The
 * scanning step is the scan screen itself (photo or PDF, the same reader), in its old dress
 * until it is redrawn. Ready's "Daily budget" and "Protein target" wait on the rule; "Watching"
 * names the values outside their PRINTED range, in those words (amendment 2).
 */
@Composable
fun FirstRunScreen(
    onScan: () -> Unit,
    onDone: () -> Unit,
    onBackOut: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = hiltViewModel(),
    today: TodayViewModel = hiltViewModel(),
) {
    val s = scheme()
    val state by vm.state.collectAsState()
    val t by today.state.collectAsState()
    var step by rememberSaveable { mutableStateOf(Step.LANG) }
    val steps = Step.entries.filter { it != Step.SCAN }
    val index = steps.indexOf(if (step == Step.SCAN) Step.REPORT else step)
    val back = {
        when (step) {
            Step.LANG -> onBackOut()
            Step.SCAN -> step = Step.REPORT
            else -> step = steps[index - 1]
        }
    }
    BackHandler { back() }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = Step.READY }

    Column(modifier.fillMaxSize().background(s.ground).statusBarsPadding().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 26.dp, end = 26.dp, top = 8.dp, bottom = 30.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).border(1.dp, s.text.copy(alpha = 0.12f), CircleShape).clickable(onClick = back), contentAlignment = Alignment.Center) {
                Icon2(Glyphs.arrowLeft, 15.dp, s.text)
            }
            Box(Modifier.weight(1f).height(3.dp).background(s.text.copy(alpha = 0.09f), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth((index + 1f) / steps.size).height(3.dp).background(s.accent, RoundedCornerShape(2.dp)))
            }
            T(stringResource(R.string.v2_step_of, index + 1, steps.size), sans(12f, FontWeight.SemiBold, 1.2f), color = s.text3)
        }
        val (title, sub) = when (step) {
            Step.LANG -> R.string.v2_step_lang_title to R.string.v2_step_lang_sub
            Step.ABOUT -> R.string.v2_step_about_title to R.string.v2_step_about_sub
            Step.CONDITIONS -> R.string.v2_step_conditions_title to R.string.v2_step_conditions_sub
            Step.REPORT, Step.SCAN -> R.string.v2_step_report_title to R.string.v2_step_report_sub
            Step.MIC -> R.string.v2_step_mic_title to R.string.v2_step_mic_sub
            Step.READY -> R.string.v2_step_ready_title to R.string.v2_step_ready_sub
        }
        T(stringResource(title), serif(31f, 1.2f), color = s.text)
        T(stringResource(sub), sans(14.5f, FontWeight.Normal, 1.55f), color = s.text2, modifier = Modifier.padding(top = 10.dp, bottom = 26.dp))

        when (step) {
            Step.LANG -> {
                var chosen by rememberSaveable { mutableStateOf(state.language) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("en-IN" to R.string.v2_language_english_note, "te" to R.string.v2_language_speech_note, "hi" to R.string.v2_language_speech_note).forEach { (tag, note) ->
                        val on = chosen == tag
                        val shape = RoundedCornerShape(18.dp)
                        Row(
                            Modifier.fillMaxWidth().background(if (on) s.accent.copy(alpha = 0.08f) else s.card2, shape)
                                .border(1.dp, if (on) s.accent.copy(alpha = 0.35f) else s.text.copy(alpha = 0.09f), shape)
                                .clickable { chosen = tag }.padding(horizontal = 19.dp, vertical = 17.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                T(stringResource(languageName(tag)), sans(15f, FontWeight.SemiBold, 1.3f), color = s.text)
                                T(stringResource(note), sans(12.5f, FontWeight.Normal, 1.3f), color = s.text2, modifier = Modifier.padding(top = 3.dp))
                            }
                            Box(Modifier.size(20.dp).background(if (on) s.accent else s.accent.copy(alpha = 0f), CircleShape).border(1.5.dp, if (on) s.accent else s.text.copy(alpha = 0.2f), CircleShape))
                        }
                    }
                    Pill2(stringResource(R.string.v2_continue), onClick = { vm.setLanguage(chosen); step = Step.ABOUT }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), size = 15f, vertical = 17.dp)
                }
            }
            Step.ABOUT -> {
                val p = state.profile
                // Keyed on the row's values: the row may load after the step is first drawn.
                var age by rememberSaveable(p?.age_years) { mutableStateOf(p?.age_years?.toString().orEmpty()) }
                var weight by rememberSaveable(p?.weight_kg) { mutableStateOf(p?.weight_kg?.let(::fig).orEmpty()) }
                var height by rememberSaveable(p?.height_cm) { mutableStateOf(p?.height_cm?.let(::fig).orEmpty()) }
                var activity by rememberSaveable(p?.activity) { mutableStateOf(p?.activity.orEmpty()) }
                var goal by rememberSaveable(p?.goal) { mutableStateOf(p?.goal) }
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BodyField(stringResource(R.string.v2_field_age), age, stringResource(R.string.v2_unit_yrs), Modifier.weight(1f)) { age = it }
                    BodyField(stringResource(R.string.v2_field_weight), weight, stringResource(R.string.v2_unit_kg), Modifier.weight(1f)) { weight = it }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BodyField(stringResource(R.string.v2_field_height), height, stringResource(R.string.v2_unit_cm), Modifier.weight(1f)) { height = it }
                    BodyField(stringResource(R.string.v2_field_activity), activity, stringResource(R.string.v2_unit_days_wk), Modifier.weight(1f)) { activity = it }
                }
                val shape = RoundedCornerShape(18.dp)
                Column(Modifier.fillMaxWidth().background(s.card2, shape).border(1.dp, s.text.copy(alpha = 0.08f), shape).padding(18.dp)) {
                    T(stringResource(R.string.v2_goal).uppercase(), micro(11f, 0.12.em, FontWeight.SemiBold), color = s.text3, modifier = Modifier.padding(bottom = 13.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("MAINTAIN" to R.string.v2_goal_maintain, "LOSE_WEIGHT" to R.string.v2_goal_lose, "GAIN_WEIGHT" to R.string.v2_goal_gain, "BUILD_MUSCLE" to R.string.v2_goal_muscle).forEach { (k, label) ->
                            Chip2(stringResource(label), on = goal == k) { goal = if (goal == k) null else k }
                        }
                    }
                }
                Pill2(stringResource(R.string.v2_continue), onClick = {
                    vm.save(
                        name = p?.name, ageYears = age.trim().toIntOrNull(), weightKg = weight.trim().toDoubleOrNull(), heightCm = height.trim().toDoubleOrNull(),
                        sex = p?.sex, activity = activity.trim().ifEmpty { null }, goal = goal, lifeContext = p?.life_context, dietType = p?.diet_type,
                    )
                    step = Step.CONDITIONS
                }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), size = 15f, vertical = 17.dp)
            }
            Step.CONDITIONS -> {
                val declared = state.conditions.filter { it.source == "USER_DECLARED" }
                val names = listOf(R.string.v2_cond_prediabetes, R.string.v2_cond_t2d, R.string.v2_cond_bp, R.string.v2_cond_thyroid, R.string.v2_cond_pcos, R.string.v2_cond_vitd, R.string.v2_cond_cholesterol)
                FlowRow(Modifier.padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    names.forEach { res ->
                        val label = stringResource(res)
                        val row = declared.firstOrNull { it.name.equals(label, ignoreCase = true) }
                        Chip2(label, on = row != null, vertical = 11.dp, horizontal = 16.dp, size = 13.5f) { if (row != null) vm.removeCondition(row.id) else vm.declareCondition(label) }
                    }
                    Chip2(stringResource(R.string.v2_cond_none), on = declared.isEmpty(), vertical = 11.dp, horizontal = 16.dp, size = 13.5f) { declared.forEach { vm.removeCondition(it.id) } }
                }
                Pill2(stringResource(R.string.v2_continue), onClick = { step = Step.REPORT }, modifier = Modifier.fillMaxWidth(), size = 15f, vertical = 17.dp)
            }
            Step.REPORT, Step.SCAN -> {
                val line = s.accent.copy(alpha = 0.35f)
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        .drawBehind { drawRoundRect(line, cornerRadius = CornerRadius(24.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))) }
                        .clickable { step = Step.SCAN; onScan() }.padding(horizontal = 20.dp, vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.padding(bottom = 16.dp).size(48.dp).background(s.accent.copy(alpha = 0.14f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon2(Glyphs.scan, 22.dp, s.accent) }
                    T(stringResource(R.string.v2_scan_report), sans(15f, FontWeight.SemiBold, 1.3f), color = s.text)
                    T(stringResource(R.string.v2_scan_report_sub), sans(13f, FontWeight.Normal, 1.5f), color = s.text2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
                Pill2(stringResource(R.string.v2_skip_for_now), onClick = { step = Step.MIC }, modifier = Modifier.fillMaxWidth(), filled = false, size = 15f, vertical = 17.dp)
                if (t.latestReport != null) {
                    Pill2(stringResource(R.string.v2_continue), onClick = { step = Step.MIC }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), size = 15f, vertical = 17.dp)
                }
            }
            Step.MIC -> {
                val reduce = LocalReduceMotion.current
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp, bottom = 34.dp).size(96.dp), contentAlignment = Alignment.Center) {
                    if (!reduce) {
                        val p by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart))
                        val k = if (p < 0.5f) p * 2f else (1f - p) * 2f
                        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = 1f + 0.35f * k; scaleY = 1f + 0.35f * k; alpha = 0.5f * (1f - k) }.background(s.accent.copy(alpha = 0.25f), CircleShape))
                    }
                    Box(Modifier.fillMaxSize().background(s.accent, CircleShape), contentAlignment = Alignment.Center) { Icon2(Glyphs.mic, 34.dp, s.onAccent) }
                }
                Pill2(stringResource(R.string.v2_allow_microphone), onClick = { askMic.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth(), size = 15f, vertical = 17.dp)
                T(stringResource(R.string.v2_not_now), sans(14f, FontWeight.Normal, 1.2f), color = s.text2, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { step = Step.READY }.padding(top = 18.dp))
            }
            Step.READY -> {
                Image(painterResource(R.drawable.mark_lime), contentDescription = null, modifier = Modifier.padding(bottom = 24.dp).width(128.dp))
                val shape = RoundedCornerShape(24.dp)
                Column(Modifier.fillMaxWidth().padding(bottom = 18.dp).background(s.card2, shape).border(1.dp, s.text.copy(alpha = 0.08f), shape).padding(22.dp)) {
                    ReadyRow(stringResource(R.string.v2_ready_budget), t.targets?.let { stringResource(R.string.v2_item_kcal, fig(it.energyKcal)) })
                    ReadyRow(stringResource(R.string.v2_ready_protein), t.targets?.let { stringResource(R.string.v2_macro_alone, fig(it.proteinG)) })
                    val above = stringResource(R.string.scan_above_range)
                    val below = stringResource(R.string.scan_below_range)
                    // The newest report's values outside their printed range, in those words (amendment 2).
                    val watching = t.outOfRange.filter { it.reportDate == t.latestReport }.joinToString(" · ") { it.testName + " " + (if (it.status == LabStatus.ABOVE) above else below) }
                    ReadyRow(stringResource(R.string.v2_ready_watching), watching.ifEmpty { null })
                    ReadyRow(stringResource(R.string.v2_ready_voice), stringResource(languageName(state.language)))
                }
                Pill2(stringResource(R.string.v2_start_logging), onClick = onDone, modifier = Modifier.fillMaxWidth(), size = 15f, vertical = 17.dp)
            }
        }
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 24.dp))
    }
}

/** One of the "about you" boxes: the tracked label, the value in serif 26 (Plex, it is a number), the unit. */
@Composable
private fun BodyField(label: String, value: String, unit: String, modifier: Modifier, onChange: (String) -> Unit) {
    val s = scheme()
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.background(s.card2, shape).border(1.dp, s.text.copy(alpha = 0.08f), shape).padding(horizontal = 18.dp, vertical = 16.dp)) {
        T(label.uppercase(), micro(11f, 0.12.em, FontWeight.SemiBold), color = s.text3)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value, onValueChange = { if (it.length <= 12) onChange(it) },
                modifier = Modifier.weight(1f),
                textStyle = sans(26f * 1.02f, FontWeight.Normal, 1.1f, s.text).copy(fontFamily = Plex),
                cursorBrush = SolidColor(s.accent), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            T(unit, sans(12.5f, FontWeight.Normal, 1.2f), color = s.text2, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun ReadyRow(label: String, value: String?) {
    val s = scheme()
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(s.text.copy(alpha = 0.06f)))
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            T(label, sans(14f, FontWeight.Normal, 1.3f), color = s.quote)
            if (value != null) T(value, sans(14f, FontWeight.SemiBold, 1.3f), color = s.text, modifier = Modifier.padding(start = 12.dp), textAlign = TextAlign.End)
        }
    }
}
