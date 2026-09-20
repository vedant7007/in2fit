package io.github.vedant7007.katori.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.SpokenIntent
import io.github.vedant7007.katori.domain.Stage
import io.github.vedant7007.katori.ui.TalkViewModel.Entry
import io.github.vedant7007.katori.ui.components.AdviceCard
import io.github.vedant7007.katori.ui.components.AnswerCard
import io.github.vedant7007.katori.ui.components.AskCard
import io.github.vedant7007.katori.ui.components.ConfirmCard
import io.github.vedant7007.katori.ui.components.ContextLines
import io.github.vedant7007.katori.ui.components.FailedCard
import io.github.vedant7007.katori.ui.components.FigureLine
import io.github.vedant7007.katori.ui.components.IntentHeading
import io.github.vedant7007.katori.ui.components.MicButton
import io.github.vedant7007.katori.ui.components.OfflineMark
import io.github.vedant7007.katori.ui.components.PlateCard
import io.github.vedant7007.katori.ui.components.PlateItem
import io.github.vedant7007.katori.ui.components.Prose
import io.github.vedant7007.katori.ui.components.SafetyLine
import io.github.vedant7007.katori.ui.components.SaidBlock
import io.github.vedant7007.katori.ui.components.SecondaryButton
import io.github.vedant7007.katori.ui.components.StageIndicator
import io.github.vedant7007.katori.ui.theme.In2fitColors
import io.github.vedant7007.katori.ui.theme.In2fitText
import io.github.vedant7007.katori.ui.theme.Space
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Beats 1, 2 and 4 on one surface: speak, see what was heard, see what happened, hear the reply.
 * The orchestrator speaks; this screen only shows. What it shows, in what order, from which event,
 * is Arjun's `TalkViewModel` and 0026; how it looks is `ui/theme` and `ui/components` (Ira).
 *
 * Three reads of the one `State`, so the parts that change often recompose only what reads them:
 * the layout (entries, stages, busy, language) recomposes on a new entry or stage; the level is a
 * `State<Float>` read inside the mic's draw phase and nowhere else; the seconds are read inside
 * the stage indicator and nowhere else (research §5.4).
 */
@Composable
fun TalkScreen(vm: TalkViewModel = hiltViewModel()) {
    val quiet = remember(vm) { vm.state.map { it.copy(level = 0f, elapsedSeconds = 0) }.distinctUntilChanged() }
    val state by quiet.collectAsState(vm.state.value.copy(level = 0f, elapsedSeconds = 0))
    val level: State<Float> = remember(vm) { vm.state.map { it.level } }.collectAsState(0f)
    val elapsed: State<Int> = remember(vm) { vm.state.map { it.elapsedSeconds } }.collectAsState(0)

    val context = LocalContext.current
    var micDenied by rememberSaveable { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) vm.speak() else typing = true
    }
    val listState = rememberLazyListState()
    // The newest thing is always at the bottom: the stage indicator while a turn runs (with the
    // transcript just above it, 0026 step 3), the answer when it lands.
    val itemCount = 1 + state.entries.size + if (state.stages.isNotEmpty() || state.lastMealId != null) 1 else 0
    LaunchedEffect(itemCount) { if (itemCount > 1) listState.animateScrollToItem(itemCount - 1) }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = Space.l)) {
        OfflineMark(Modifier.padding(top = Space.m, bottom = Space.xs))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            contentPadding = PaddingValues(vertical = Space.s),
        ) {
            // The language is set once, before a demo, never during a turn: it heads the
            // conversation rather than the pinned block, which stays small enough at a 2x font
            // scale on a 360 dp screen to leave the conversation its room (hostile check, 21 Sep).
            item(key = "picker") {
                FlowRow(Modifier.fillMaxWidth().padding(bottom = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs), itemVerticalAlignment = Alignment.CenterVertically) {
                    // The chips choose the language the person will SPEAK in. Answers are English, on
                    // screen and aloud, whatever is chosen (0019 addendum 7); the label says so.
                    Text(stringResource(R.string.speak_in_label), style = In2fitText.label, modifier = Modifier.padding(end = Space.s))
                    LanguageChip(state.language, "te", R.string.language_telugu, vm::setLanguage)
                    LanguageChip(state.language, "hi", R.string.language_hindi, vm::setLanguage)
                    LanguageChip(state.language, "en-IN", R.string.language_english, vm::setLanguage)
                }
            }
            if (state.entries.isEmpty()) {
                item { Text(stringResource(R.string.talk_hint), style = In2fitText.transcript, modifier = Modifier.padding(top = Space.l)) }
            }
            itemsIndexed(state.entries, key = { i, e -> "$i:${e::class.simpleName}" }) { _, entry -> EntryView(entry, onResolve = vm::resolve) }
            if (state.stages.isNotEmpty()) {
                item(key = "progress") {
                    val names = state.stages.map { stringResource(Sentences.stage(it)) }
                    StageIndicator(done = names.dropLast(1), current = names.last(), elapsed = { elapsed.value })
                }
            } else if (state.lastMealId != null) {
                // Beat 4's control sits with the meal it acts on, not in the pinned block (which must
                // stay small enough at a 2x font scale to leave the conversation its room).
                item(key = "advise-again") {
                    SecondaryButton(stringResource(R.string.advise_again), onClick = vm::adviseAgain, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(vertical = Space.s))
                }
            }
        }

        // The controls, in the thumb's third of the screen. The safety line once, pinned (spec 15.3).
        SafetyLine(Modifier.padding(vertical = Space.s))
        if (state.stage == Stage.SPEAKING) {
            // 0026 step 8: stops speech only; the answer stays on screen.
            MicButton(label = stringResource(R.string.stop_speaking), enabled = true, active = false, level = { 0f }, onPress = {}, onRelease = {}, stop = vm::stopSpeaking)
        } else {
            val recording = state.stage == Stage.RECORDING
            MicButton(
                label = stringResource(if (recording) R.string.mic_listening else R.string.mic_speak),
                enabled = !state.busy,
                active = recording,
                level = { level.value },
                onPress = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) vm.speak() else askMic.launch(Manifest.permission.RECORD_AUDIO)
                },
                // Release ends the recording the hour `UserIntent.EndSpeech` is in the contract
                // (Arjun 20:15); until then the endpointer ends it and this is a no-op.
                onRelease = {},
            )
        }
        TextButton(onClick = { typing = !typing }, contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(top = Space.xs)) {
            Text(stringResource(R.string.talk_type_instead), style = In2fitText.bodySmall.copy(fontWeight = FontWeight.Medium), color = In2fitColors.ink)
        }
        if (micDenied) Text(stringResource(R.string.mic_permission_needed), style = In2fitText.bodySmall, color = In2fitColors.inkSecondary)
        if (typing) {
            Row(Modifier.fillMaxWidth().padding(bottom = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                val send = { if (!state.busy && typed.isNotBlank()) { vm.type(typed.trim()); typed = "" } }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.type_hint), style = In2fitText.bodySmall) },
                    textStyle = In2fitText.body,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                TextButton(enabled = !state.busy && typed.isNotBlank(), onClick = send) {
                    Text(stringResource(R.string.send), style = In2fitText.button)
                }
            }
        } else {
            Spacer(Modifier.padding(bottom = Space.s))
        }
    }
}

/** One word, tinted when chosen, 48 dp of thumb; no Material chip. */
@Composable
private fun LanguageChip(current: String, tag: String, nameRes: Int, onPick: (String) -> Unit) {
    val chosen = current == tag
    Text(
        stringResource(nameRes),
        maxLines = 1, softWrap = false,
        style = if (chosen) In2fitText.bodySmall.copy(fontWeight = FontWeight.Medium) else In2fitText.bodySmall,
        color = if (chosen) In2fitColors.ink else In2fitColors.inkSecondary,
        modifier = Modifier
            .padding(end = Space.xs)
            .background(if (chosen) In2fitColors.selected else In2fitColors.ground, MaterialTheme.shapes.small)
            .border(1.dp, if (chosen) In2fitColors.selected else In2fitColors.hairline, MaterialTheme.shapes.small)
            .clickable { onPick(tag) }
            .padding(horizontal = Space.m, vertical = Space.s),
    )
}

@Composable
private fun EntryView(entry: Entry, onResolve: (String, SpokenIntent) -> Unit) {
    when (entry) {
        is Entry.Said -> SaidBlock(entry.text)
        is Entry.Heading -> IntentHeading(stringResource(Sentences.intent(entry.intent)), entry.leadIn)
        // The person's own lines, straight from the store, before any model call: the answer itself.
        is Entry.Figures -> Prose(stringResource(R.string.answer_from_diary), arrives = false) { ContextLines(entry.lines) }
        is Entry.Plate -> PlateCard(
            status = when {
                entry.hypothetical -> stringResource(R.string.meal_hypothetical)
                entry.logged -> stringResource(R.string.meal_logged)
                else -> null
            },
            // The "taken as" caption (0035) needs the grams, which do not reach the entry yet
            // (COORDINATION 23:34, asked of Rao and Arjun). Until then no caption; the hour
            // `Entry.Plate.grams` lands, this is `entry.grams[i]` and the QUANTITY_INFERRED check.
            items = entry.items.map { PlateItem(said = itemLine(it), takenAs = null) },
            figures = entry.figures.map { (line, band) -> FigureLine.fromRendered(line, band) },
        )
        is Entry.Advice -> AdviceCard(
            label = stringResource(R.string.advice_title),
            trigger = entry.trigger ?: stringResource(R.string.advice_no_rule),
            phrased = entry.phrased,
            referral = entry.referral,
            candidatesLabel = stringResource(R.string.advice_candidates_title),
            candidates = entry.candidates,
        )
        // A refused answer (0024) is the fixed line over the same lines.
        is Entry.Answer -> AnswerCard(
            label = stringResource(R.string.answer_title),
            text = entry.text ?: stringResource(R.string.answer_refused),
            referral = entry.referral,
            figures = entry.figures,
        )
        is Entry.AskIntent -> AskCard(
            question = stringResource(R.string.ask_intent_question),
            options = listOf(
                stringResource(R.string.intent_log) to { onResolve(entry.transcript, SpokenIntent.LOG) },
                stringResource(R.string.intent_answer) to { onResolve(entry.transcript, SpokenIntent.ANSWER) },
                stringResource(R.string.intent_suggest) to { onResolve(entry.transcript, SpokenIntent.SUGGEST) },
                stringResource(R.string.intent_recommend) to { onResolve(entry.transcript, SpokenIntent.RECOMMEND) },
            ),
        )
        is Entry.Confirm -> ConfirmCard(
            title = stringResource(R.string.confirm_title),
            sentence = stringResource(Sentences.unavailable(entry.why)),
            items = entry.items.map { itemLine(it) },
            hint = stringResource(R.string.confirm_hint),
        )
        is Entry.Failed -> FailedCard(stringResource(Sentences.unavailable(entry.reason)), entry.detail)
        is Entry.NotBuilt -> Prose(null) { Text(stringResource(R.string.not_built, entry.component), style = In2fitText.bodySmall) }
    }
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
