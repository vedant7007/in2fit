package io.github.vedant7007.katori.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.SpokenIntent
import io.github.vedant7007.katori.ui.Sentences
import io.github.vedant7007.katori.ui.LabStatus
import io.github.vedant7007.katori.ui.TalkViewModel
import io.github.vedant7007.katori.ui.TodayViewModel
import io.github.vedant7007.katori.ui.TalkViewModel.Entry
import io.github.vedant7007.katori.ui.components.FigureLine
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.num
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif

/**
 * Coach as drawn: the serif title over a hairline, the thread of bubbles (the person's on the
 * right in the accent tint, the assistant's on the left on the message ground, 84% wide at
 * most, 13 / 16 padding, 14.5 sp at 1.55), the three suggestion chips, and the input pill with
 * its accent send circle. Every bubble is a `TalkViewModel.Entry`; nothing else is a bubble.
 *
 * WORDS CHANGED, on the ruling of 21 Sep: the design's typing dots are the stage list (0026:
 * named stages and a counter, never a spinner); the design's context line under the title
 * ("Knows your Sept blood report · HbA1c 6.4 · Vit D low") is attributed (amendment 2): the
 * report by its printed date, each value outside its printed range in those words, from
 * `TodayViewModel`; the offline mark stands there while no report is saved. The safety line
 * sits above the chips, once, in the design's caption style.
 */
@Composable
fun CoachScreen(
    vm: TalkViewModel,
    state: TalkViewModel.State,
    elapsed: State<Int>,
    onTitleLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    today: TodayViewModel = hiltViewModel(),
) {
    val s = scheme()
    val known by today.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val itemCount = state.entries.size + if (state.stages.isNotEmpty()) 1 else 0
    LaunchedEffect(itemCount) { if (itemCount > 0) listState.animateScrollToItem(itemCount - 1) }
    val send = { if (!state.busy && draft.isNotBlank()) { vm.type(draft.trim()); draft = "" } }

    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onTitleLongPress)
                .padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
        ) {
            T(stringResource(R.string.v2_coach_title), serif(27f, 1.2f), color = s.text)
            // The design's context line, attributed: the report by its printed date, then each
            // value outside its printed range in those words; the offline mark when there is no report.
            val report = known.latestReport
            val line = if (report == null) stringResource(R.string.talk_offline_mark) else {
                val flags = known.outOfRange.filter { it.reportDate == report }.map { row ->
                    row.testName + " " + stringResource(if (row.status == LabStatus.ABOVE) R.string.scan_above_range else R.string.scan_below_range)
                }
                (listOf(stringResource(R.string.v2_coach_knows_report, DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(report))) + flags).joinToString(" · ")
            }
            T(line, sans(12.5f, FontWeight.Normal, 1.45f), color = s.text2, modifier = Modifier.padding(top = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(s.text.copy(alpha = 0.06f)))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(state.entries, key = { i, e -> "$i:${e::class.simpleName}" }) { _, entry -> Bubble(entry, onResolve = vm::resolve) }
            if (state.stages.isNotEmpty()) {
                item(key = "stages") {
                    Box(Modifier.fillMaxWidth(0.84f)) { StageRows(state.stages, elapsed) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 10.dp)) {
            T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(bottom = 10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.v2_suggest_1, R.string.v2_suggest_2, R.string.v2_suggest_3).forEach { res ->
                    val text = stringResource(res)
                    Chip2(text, enabled = !state.busy) { vm.type(text) }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(s.card, RoundedCornerShape(100.dp))
                    .border(1.dp, s.text.copy(alpha = 0.09f), RoundedCornerShape(100.dp))
                    .padding(start = 18.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    textStyle = sans(14.5f, FontWeight.Normal, 1.3f, s.text),
                    cursorBrush = SolidColor(s.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) Text(stringResource(R.string.v2_coach_placeholder), style = sans(14.5f, FontWeight.Normal, 1.3f), color = s.text3)
                            inner()
                        }
                    },
                )
                Box(Modifier.size(38.dp).background(s.accent, CircleShape).clickable(enabled = !state.busy, onClick = send), contentAlignment = Alignment.Center) {
                    Icon2(Glyphs.send, 17.dp, s.onAccent)
                }
            }
        }
    }
}

/** The design's suggestion chip: 9 / 15 padding, 13 sp, a hairline, the button text colour. */
@Composable
fun Chip2(text: String, enabled: Boolean = true, on: Boolean = false, size: Float = 13f, vertical: androidx.compose.ui.unit.Dp = 9.dp, horizontal: androidx.compose.ui.unit.Dp = 15.dp, onClick: () -> Unit) {
    val s = scheme()
    val shape = RoundedCornerShape(100.dp)
    Box(
        Modifier
            .background(if (on) s.accent.copy(alpha = 0.14f) else s.text.copy(alpha = 0.05f), shape)
            .border(1.dp, if (on) s.accent.copy(alpha = 0.35f) else s.text.copy(alpha = 0.09f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontal, vertical = vertical),
    ) {
        T(text, sans(size, FontWeight.Normal, 1.2f), color = if (on) s.accent else s.button, maxLines = 1)
    }
}

@Composable
private fun Bubble(entry: Entry, onResolve: (String, SpokenIntent) -> Unit) {
    when (entry) {
        is Entry.Said -> Own(entry.text)
        // 0026 step 5: the intent as a small heading over what follows, in the design's label style.
        is Entry.Heading -> T(stringResource(Sentences.intent(entry.intent)).uppercase(), micro(11.5f, 0.14.em, FontWeight.SemiBold), color = scheme().text3)
        is Entry.Figures -> Assistant { Lines(entry.lines) }
        is Entry.Plate -> Assistant { PlateLines(entry) }
        is Entry.Advice -> Assistant {
            Lines(listOfNotNull(entry.trigger ?: stringResource(R.string.advice_no_rule), entry.phrased, entry.referral))
            if (entry.candidates.isNotEmpty()) {
                T(stringResource(R.string.advice_candidates_title), sans(12.5f, FontWeight.SemiBold, 1.45f), color = scheme().text2, modifier = Modifier.padding(top = 8.dp))
                Lines(entry.candidates)
            }
        }
        is Entry.Answer -> Assistant {
            Lines(listOfNotNull(entry.text ?: stringResource(R.string.answer_refused), entry.referral))
            if (entry.figures.isNotEmpty()) {
                T(stringResource(R.string.answer_from_diary), sans(12.5f, FontWeight.SemiBold, 1.45f), color = scheme().text2, modifier = Modifier.padding(top = 8.dp))
                Lines(entry.figures)
            }
        }
        is Entry.AskIntent -> Assistant {
            Lines(listOf(stringResource(R.string.ask_intent_question)))
            Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip2(stringResource(R.string.intent_log)) { onResolve(entry.transcript, SpokenIntent.LOG) }
                Chip2(stringResource(R.string.intent_answer)) { onResolve(entry.transcript, SpokenIntent.ANSWER) }
                Chip2(stringResource(R.string.intent_suggest)) { onResolve(entry.transcript, SpokenIntent.SUGGEST) }
                Chip2(stringResource(R.string.intent_recommend)) { onResolve(entry.transcript, SpokenIntent.RECOMMEND) }
            }
        }
        is Entry.Confirm -> Assistant {
            Lines(listOf(stringResource(R.string.confirm_title), stringResource(Sentences.unavailable(entry.why))))
            Lines(entry.items.map { itemLine(it) })
            T(stringResource(R.string.confirm_hint), sans(12.5f, FontWeight.Normal, 1.45f), color = scheme().text2, modifier = Modifier.padding(top = 6.dp))
        }
        is Entry.Failed -> Assistant {
            Lines(listOf(stringResource(Sentences.unavailable(entry.reason))))
            if (entry.detail != null) T(entry.detail, sans(12.5f, FontWeight.Normal, 1.45f), color = scheme().text3, modifier = Modifier.padding(top = 6.dp))
        }
        is Entry.NotBuilt -> Assistant { Lines(listOf(stringResource(R.string.not_built, entry.component))) }
    }
}

@Composable
private fun Own(text: String) {
    val s = scheme()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .fillMaxWidth(0.84f)
                .wrapContentWidth(Alignment.End)
                .background(s.accent.copy(alpha = 0.14f), RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                .border(1.dp, s.accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) { T(text, sans(14.5f, FontWeight.Normal, 1.55f), color = s.onOwn) }
    }
}

@Composable
private fun Assistant(content: @Composable () -> Unit) {
    val s = scheme()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(
            Modifier
                .fillMaxWidth(0.84f)
                .wrapContentWidth(Alignment.Start)
                .background(s.message, RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                .border(1.dp, s.text.copy(alpha = 0.07f), RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) { content() }
    }
}

@Composable
private fun Lines(lines: List<String>) {
    val s = scheme()
    Column { lines.forEach { T(it, sans(14.5f, FontWeight.Normal, 1.55f), color = s.onMessage) } }
}

/** The plate inside the thread: its energy first (in Plex), the items as said, the other figures. */
@Composable
private fun PlateLines(plate: Entry.Plate) {
    val s = scheme()
    val figures = plate.figures.map { (line, band) -> FigureLine.fromRendered(line, band) }
    val energyWord = stringResource(R.string.nutrient_energy)
    val energy = figures.firstOrNull { it.name == energyWord }
    val status = when {
        plate.hypothetical -> stringResource(R.string.meal_hypothetical)
        plate.logged -> stringResource(R.string.meal_logged)
        else -> null
    }
    if (status != null) T(status.uppercase(), micro(11.5f, 0.14.em, FontWeight.SemiBold), color = s.text3, modifier = Modifier.padding(bottom = 6.dp))
    if (energy != null) T(energy.value, num(20f, FontWeight.SemiBold, lineHeight = 1.2f), color = s.onMessage, modifier = Modifier.padding(bottom = 4.dp))
    // The resolved rows carry the name shown and, only when the amount was assumed, "taken as N g" (0035).
    if (plate.rows.isNotEmpty()) {
        Lines(
            plate.rows.map { r ->
                val amount = r.quantity?.let { q -> listOfNotNull(Sentences.number(q), r.unit).joinToString(" ") }
                val takenAs = if (r.inferred && r.grams != null) stringResource(R.string.plate_unit_taken_as, fig(r.grams)) else null
                listOfNotNull(r.name, amount, takenAs).joinToString(" · ")
            },
        )
    } else {
        Lines(plate.items.map { itemLine(it) })
    }
    // The other figures as the contract renders them, untouched.
    val rest = plate.figures.map { it.first }.filter { FigureLine.fromRendered(it).name != energyWord }
    if (rest.isNotEmpty()) Column(Modifier.padding(top = 6.dp)) { rest.forEach { T(it, sans(12.5f, FontWeight.Normal, 1.45f), color = s.text2) } }
}

@Composable
private fun itemLine(item: ParsedItem): String {
    val quantity = item.quantity
    return if (quantity != null) {
        stringResource(R.string.meal_item_with_quantity, item.spokenName, Sentences.number(quantity), item.unit.orEmpty())
    } else {
        stringResource(R.string.meal_item_no_quantity, item.spokenName)
    }
}
