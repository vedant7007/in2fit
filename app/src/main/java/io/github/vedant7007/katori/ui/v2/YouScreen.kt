package io.github.vedant7007.katori.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.ProfileViewModel
import io.github.vedant7007.katori.ui.TodayViewModel
import io.github.vedant7007.katori.ui.shareText
import io.github.vedant7007.katori.ui.theme.ThemePreference
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif
import java.util.Locale

/** The six list pages under You, in the design's order; "appearance" is the ruled switch's page. */
enum class YouList { GOALS, REMINDERS, LANGUAGE, PORTIONS, PRIVACY, HELP }

/**
 * You as drawn: the serif title, the profile card (the 54 dp initial, the name in 17/600, the
 * age and weight under it, the chevron) into Edit details, the rows card (Goals, Reminders,
 * Language, Portion reference, Privacy, Help, and the ruled Appearance row for the dark/cream
 * switch), the "On-device mode" card, then the safety line.
 *
 * NOT AS DRAWN, and why: the design's on-device toggle is a statement with no switch (override,
 * the network seven: there is no cloud to switch to); "Sign out" has no account behind it
 * (amendment 1: the local first run replaced sign-in) and is not drawn; the city in "32 · 74 kg ·
 * Hyderabad" is not stored (MISSING) and the line ends at the weight. Every value on a row is
 * the store's or nothing: the reminders' count when there are reminders, the language's name,
 * the energy target only when the rule has one.
 */
@Composable
fun YouScreen(
    onEdit: () -> Unit,
    onList: (YouList) -> Unit,
    onReports: () -> Unit,
    onPreflight: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = hiltViewModel(),
    today: TodayViewModel = hiltViewModel(),
) {
    val s = scheme()
    val state by vm.state.collectAsState()
    val t by today.state.collectAsState()
    val context = LocalContext.current
    val profile = state.profile
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        // The pre-flight check (Arjun's, for whoever sets the phone up; judges never see it) is behind the title, long-pressed, as it was behind About's wordmark.
        T(stringResource(R.string.v2_tab_you), serif(27f, 1.2f), color = s.text, modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onPreflight).padding(bottom = 20.dp))
        Card2(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = 26.dp, onClick = onEdit) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).background(Brush.linearGradient(listOf(s.avatar, s.card)), CircleShape).border(1.dp, s.text.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val initial = profile?.name?.trim()?.firstOrNull()?.uppercase()
                    if (initial != null) T(initial, sans(19f, FontWeight.SemiBold, 1f), color = s.accent)
                }
                Column(Modifier.weight(1f)) {
                    val name = profile?.name?.takeIf { it.isNotBlank() }
                    if (name != null) T(name, sans(17f, FontWeight.SemiBold, 1.3f), color = s.text)
                    val line = listOfNotNull(profile?.age_years?.toString(), profile?.weight_kg?.let { stringResource(R.string.v2_kg, fig(it)) }).joinToString(" · ")
                    if (line.isNotEmpty()) T(line, sans(13f, FontWeight.Normal, 1.3f), color = s.text2, modifier = Modifier.padding(top = 3.dp))
                }
                Icon2(Glyphs.chevronSmall, 16.dp, s.dim)
            }
        }
        Card2(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = 26.dp) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                YouRow(stringResource(R.string.v2_goals), t.targets?.let { stringResource(R.string.v2_item_kcal, fig(it.energyKcal)) }) { onList(YouList.GOALS) }
                YouRow(stringResource(R.string.v2_reminders), state.reminders.count { it.enabled }.takeIf { it > 0 }?.let { stringResource(R.string.v2_n_on, it) }) { onList(YouList.REMINDERS) }
                YouRow(stringResource(R.string.language_picker_title), stringResource(languageName(state.language))) { onList(YouList.LANGUAGE) }
                YouRow(stringResource(R.string.v2_portion_reference), null) { onList(YouList.PORTIONS) }
                YouRow(stringResource(R.string.v2_reports_title), t.latestReport?.let { java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(it) }, onReports)
                YouRow(stringResource(R.string.v2_privacy), stringResource(R.string.v2_on_device)) { onList(YouList.PRIVACY) }
                YouRow(stringResource(R.string.v2_appearance), stringResource(if (ThemePreference.dark) R.string.v2_dark else R.string.v2_cream)) { ThemePreference.set(context, !ThemePreference.dark) }
                YouRow(stringResource(R.string.v2_help), null) { onList(YouList.HELP) }
                // Log out: the phone forgets the person. Asked first; then Welcome again.
                var askLogOut by rememberSaveable { mutableStateOf(false) }
                YouRow(stringResource(R.string.v2_log_out), null) { askLogOut = true }
                if (askLogOut) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { askLogOut = false },
                        title = { androidx.compose.material3.Text(stringResource(R.string.v2_log_out_title)) },
                        text = { androidx.compose.material3.Text(stringResource(R.string.v2_log_out_body)) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                askLogOut = false
                                vm.logOut { ThemePreference.setWelcomed(context, false) }
                            }) { androidx.compose.material3.Text(stringResource(R.string.v2_log_out)) }
                        },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { askLogOut = false }) { androidx.compose.material3.Text(stringResource(R.string.v2_cancel)) } },
                    )
                }
            }
        }
        // The on-device statement (override: the cloud toggle is gone; there is nothing to switch to).
        Card2(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = 26.dp) {
            Column(Modifier.padding(20.dp)) {
                T(stringResource(R.string.v2_on_device_mode), sans(14.5f, FontWeight.SemiBold, 1.3f), color = s.text)
                T(stringResource(R.string.v2_on_device_sub), sans(12.5f, FontWeight.Normal, 1.45f), color = s.text2, modifier = Modifier.padding(top = 4.dp))
            }
        }
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
    }
}

@Composable
private fun YouRow(label: String, value: String?, onClick: () -> Unit) {
    val s = scheme()
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(s.text.copy(alpha = 0.06f)))
        // The label and the value share the row; the label wraps by word, the value shrinks before it clips.
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            T(label, sans(14.5f, FontWeight.Normal, 1.2f), color = s.onMessage, modifier = Modifier.weight(1f))
            // A value shares the row half and half at most and shrinks before it clips; no value, the label has the row.
            if (value != null) N(value, sans(14f, FontWeight.Normal, 1.2f), color = s.text2, modifier = Modifier.weight(1f, fill = false), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Icon2(Glyphs.chevronSmall, 14.dp, s.dim)
        }
    }
}

/** The language the person speaks in, by its tag; the picker's own names. */
fun languageName(tag: String): Int = when (tag) {
    "te" -> R.string.language_telugu
    "hi" -> R.string.language_hindi
    else -> R.string.language_english
}

@Composable
private fun BackLink(text: String, onBack: () -> Unit) {
    val s = scheme()
    Row(Modifier.clickable(onClick = onBack).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon2(Glyphs.arrowLeft, 14.dp, s.text2)
        T(text, sans(13.5f, FontWeight.Normal, 1.2f), color = s.text2)
    }
}

/**
 * Edit details as drawn: one 18 dp box per field (the tracked label, the value in 15.5), the
 * accent "Save changes" pill. Name, Age, Weight and Height write through `ProfileStore.save`,
 * the profile's one write path; the design's "Doctor" field has no store (MISSING) and is not
 * drawn. A blank field saves as not stated, never as 0.
 */
@Composable
fun EditDetailsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, vm: ProfileViewModel = hiltViewModel()) {
    val s = scheme()
    val state by vm.state.collectAsState()
    val p = state.profile
    var name by rememberSaveable(p?.name) { mutableStateOf(p?.name.orEmpty()) }
    var age by rememberSaveable(p?.age_years) { mutableStateOf(p?.age_years?.toString().orEmpty()) }
    var weight by rememberSaveable(p?.weight_kg) { mutableStateOf(p?.weight_kg?.let(::fig).orEmpty()) }
    var height by rememberSaveable(p?.height_cm) { mutableStateOf(p?.height_cm?.let(::fig).orEmpty()) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp)) {
        BackLink(stringResource(R.string.v2_tab_you), onBack)
        T(stringResource(R.string.v2_edit_details), serif(27f, 1.2f), color = s.text, modifier = Modifier.padding(bottom = 20.dp))
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(R.string.v2_field_name), name, KeyboardType.Text) { name = it }
            Field(stringResource(R.string.v2_field_age), age, KeyboardType.Number) { age = it }
            Field(stringResource(R.string.v2_field_weight), weight, KeyboardType.Decimal) { weight = it }
            Field(stringResource(R.string.v2_field_height), height, KeyboardType.Decimal) { height = it }
        }
        Pill2(stringResource(R.string.v2_save_changes), onClick = {
            vm.save(
                name = name.trim().ifEmpty { null },
                ageYears = age.trim().toIntOrNull(),
                weightKg = weight.trim().toDoubleOrNull(),
                heightCm = height.trim().toDoubleOrNull(),
                sex = p?.sex, activity = p?.activity, goal = p?.goal, lifeContext = p?.life_context, dietType = p?.diet_type,
            )
            onBack()
        }, modifier = Modifier.fillMaxWidth(), size = 15f, vertical = 17.dp)
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    }
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    val s = scheme()
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().background(s.card, shape).border(1.dp, s.text.copy(alpha = 0.08f), shape).padding(horizontal = 18.dp, vertical = 14.dp)) {
        T(label.uppercase(), micro(11f, 0.12.em, FontWeight.SemiBold), color = s.text3)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            textStyle = sans(15.5f, FontWeight.Normal, 1.3f, s.text),
            cursorBrush = SolidColor(s.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        )
    }
}

/**
 * The six list pages as drawn: the back link, the serif title, its line, one 26 dp card of rows
 * (label, a note under it when there is one, the value on the right in 500). What each page
 * carries, and what it does not:
 *  - Goals: the five targets from the rule (empty until Priya's `TargetRules` answers); the
 *    design's notes ("Raised because…") and "Weekly weight change" have no source and are not drawn.
 *  - Reminders: the in-app list from the store, by time of day; "Quiet hours" has no store.
 *  - Language: the three speech languages; a tap chooses; the design's "Mixed" is cut (override)
 *    and the line says "speech", not "speech and replies" (the replies are English, 0019).
 *  - Portion reference: the BUNDLED household units the resolver converts with, said so on every
 *    row, never "your katori" (0035).
 *  - Privacy: the facts as stored; "Share with your doctor" and "Export my data" open the system
 *    share sheet with the CSV text (no file, no permission); "Delete everything" is drawn and
 *    dimmed until the store has it (Arjun, (z)).
 *  - Help: "Contact support · Chat" is gone (override: this is the help page); the design's two
 *    answers that describe editing a portion or a katori describe controls that do not exist
 *    and are not drawn; the version is the build's.
 */
@Composable
fun ListScreen(which: YouList, onBack: () -> Unit, modifier: Modifier = Modifier, vm: ProfileViewModel = hiltViewModel(), today: TodayViewModel = hiltViewModel()) {
    val s = scheme()
    val state by vm.state.collectAsState()
    val t by today.state.collectAsState()
    val context = LocalContext.current
    val title: Int
    val sub: Int?
    when (which) {
        YouList.GOALS -> { title = R.string.v2_goals; sub = R.string.v2_goals_sub }
        YouList.REMINDERS -> { title = R.string.v2_reminders; sub = R.string.v2_reminders_sub }
        YouList.LANGUAGE -> { title = R.string.language_picker_title; sub = R.string.v2_language_sub }
        YouList.PORTIONS -> { title = R.string.v2_portion_reference; sub = R.string.v2_portions_sub }
        YouList.PRIVACY -> { title = R.string.v2_privacy; sub = R.string.v2_privacy_sub }
        YouList.HELP -> { title = R.string.v2_help; sub = null }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        BackLink(stringResource(R.string.v2_tab_you), onBack)
        T(stringResource(title), serif(27f, 1.2f), color = s.text)
        if (sub != null) T(stringResource(sub), sans(13f, FontWeight.Normal, 1.5f), color = s.text2, modifier = Modifier.padding(top = 5.dp, bottom = 20.dp)) else Box(Modifier.height(20.dp))
        // A page with nothing to list shows its title and line and no empty card (Reminders before any is set).
        val empty = which == YouList.REMINDERS && state.reminders.isEmpty()
        if (!empty) Card2(Modifier.fillMaxWidth(), radius = 26.dp) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                when (which) {
                    YouList.GOALS -> {
                        val g = t.targets
                        ListRow(stringResource(R.string.v2_daily_calories), g?.let { stringResource(R.string.v2_item_kcal, fig(it.energyKcal)) })
                        ListRow(stringResource(R.string.v2_protein), g?.let { stringResource(R.string.v2_macro_alone, fig(it.proteinG)) })
                        ListRow(stringResource(R.string.v2_carbs), g?.let { stringResource(R.string.v2_macro_alone, fig(it.carbohydrateG)) })
                        ListRow(stringResource(R.string.v2_fat), g?.let { stringResource(R.string.v2_macro_alone, fig(it.fatG)) })
                        ListRow(stringResource(R.string.v2_water), g?.let { stringResource(R.string.v2_water_litres, String.format(Locale.ROOT, "%.1f", it.waterMl / 1000.0)) })
                        if (g != null) ListRow(stringResource(R.string.v2_basis), null, note = g.rule)
                    }
                    YouList.REMINDERS -> state.reminders.sortedWith(compareBy({ it.hour }, { it.minute })).forEach { r ->
                        ListRow(
                            stringResource(
                                when (r.kind) {
                                    "WATER" -> R.string.v2_reminder_water
                                    "WEIGHT" -> R.string.v2_reminder_weight
                                    "REPORT" -> R.string.v2_reminder_report
                                    else -> R.string.v2_reminder_meal
                                },
                            ),
                            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, r.hour); set(java.util.Calendar.MINUTE, r.minute) }.time),
                            color = if (r.enabled) s.text2 else s.dim,
                        )
                    }
                    YouList.LANGUAGE -> listOf("en-IN" to R.string.v2_language_english_note, "te" to null, "hi" to null).forEach { (tag, note) ->
                        val on = state.language == tag
                        ListRow(stringResource(languageName(tag)), if (on) stringResource(R.string.v2_on) else null, note = note?.let { stringResource(it) }, color = if (on) s.accent else s.text2) { vm.setLanguage(tag) }
                    }
                    YouList.PORTIONS -> state.units.forEach { u ->
                        ListRow(stringResource(R.string.v2_unit_of, u.unit, u.foodClass), stringResource(R.string.v2_grams, fig(u.grams)), note = stringResource(R.string.v2_bundled_default))
                    }
                    YouList.PRIVACY -> {
                        ListRow(stringResource(R.string.v2_on_device_voice), stringResource(R.string.v2_on))
                        ListRow(stringResource(R.string.v2_report_storage), stringResource(R.string.v2_this_device))
                        val share = { subject: Int ->
                            val title = context.getString(subject)
                            vm.export { meals, labs -> shareText(context, title, meals + "\n\n" + labs) }
                        }
                        ListRow(stringResource(R.string.v2_share_doctor), stringResource(R.string.v2_csv)) { share(R.string.v2_share_doctor) }
                        ListRow(stringResource(R.string.v2_export_data), stringResource(R.string.v2_csv)) { share(R.string.v2_export_data) }
                        // Delete everything: drawn as the design draws it, dimmed until the store has a wipe (Arjun, (z)).
                        Box(Modifier.alpha(0.32f)) { ListRow(stringResource(R.string.v2_delete_everything), stringResource(R.string.v2_delete), color = s.warm) }
                    }
                    YouList.HELP -> {
                        ListRow(stringResource(R.string.v2_help_q_someone_else), null, note = stringResource(R.string.v2_help_a_someone_else))
                        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                        if (version != null) ListRow(stringResource(R.string.v2_version), version)
                    }
                }
            }
        }
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    }
}

@Composable
private fun ListRow(label: String, value: String?, note: String? = null, color: Color = scheme().text2, onClick: (() -> Unit)? = null) {
    val s = scheme()
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(s.text.copy(alpha = 0.06f)))
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T(label, sans(14.5f, FontWeight.Normal, 1.3f), color = Color(0xFFE4E8E5).takeIf { s.isDark } ?: s.text)
                if (note != null) T(note, sans(12.5f, FontWeight.Normal, 1.45f), color = s.text3, modifier = Modifier.padding(top = 4.dp))
            }
            if (value != null) N(value, sans(14f, FontWeight.Medium, 1.3f), color = color)
        }
    }
}
