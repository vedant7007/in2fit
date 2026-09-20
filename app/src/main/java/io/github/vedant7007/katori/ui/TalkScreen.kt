package io.github.vedant7007.katori.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.SpokenIntent
import io.github.vedant7007.katori.domain.Stage
import io.github.vedant7007.katori.ui.TalkViewModel.Entry

/**
 * Beats 1, 2 and 4 on one surface: speak, see what was heard, see what happened, hear the reply.
 * The orchestrator speaks; this screen only shows.
 */
@Composable
fun TalkScreen(vm: TalkViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var micDenied by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) vm.speak()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) listState.animateScrollToItem(state.entries.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            LanguageChip(state.language, "te", R.string.language_telugu, vm::setLanguage)
            LanguageChip(state.language, "hi", R.string.language_hindi, vm::setLanguage)
            LanguageChip(state.language, "en-IN", R.string.language_english, vm::setLanguage)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.entries.isEmpty()) {
                item { Text(stringResource(R.string.talk_hint), style = MaterialTheme.typography.bodyLarge) }
            }
            items(state.entries) { entry -> EntryCard(entry, onResolve = vm::resolve) }
        }

        // 0026 step 6: every stage named, completed ones ticked, a seconds counter beside the
        // current one. While recording the bar is the microphone level; at the endpoint it freezes.
        if (state.stages.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.stages.dropLast(1).forEach { done ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.talk_stage_done), style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(Sentences.stage(done)), style = MaterialTheme.typography.labelMedium)
                    }
                }
                val current = state.stages.last()
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Sentences.stage(current)), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.talk_elapsed_seconds, state.elapsedSeconds), style = MaterialTheme.typography.labelLarge)
                }
                if (current == Stage.RECORDING || current == Stage.TRANSCRIBING) {
                    LinearProgressIndicator(progress = { state.level }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (micDenied) Text(stringResource(R.string.mic_permission_needed), style = MaterialTheme.typography.bodySmall)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.stage == Stage.SPEAKING) {
                // 0026 step 8: stops speech only; the answer stays on screen.
                Button(onClick = vm::stopSpeaking, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.stop_speaking)) }
            } else {
                Button(
                    enabled = !state.busy,
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (granted) vm.speak() else askMic.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (state.stage == Stage.RECORDING) R.string.mic_listening else R.string.mic_speak))
                }
            }
            if (state.lastMealId != null) {
                OutlinedButton(enabled = !state.busy, onClick = vm::adviseAgain, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.advise_again))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.type_hint)) },
                singleLine = true,
            )
            OutlinedButton(
                enabled = !state.busy && typed.isNotBlank(),
                onClick = { vm.type(typed.trim()); typed = "" },
            ) { Text(stringResource(R.string.send)) }
        }
    }
}

@Composable
private fun LanguageChip(current: String, tag: String, nameRes: Int, onPick: (String) -> Unit) {
    FilterChip(selected = current == tag, onClick = { onPick(tag) }, label = { Text(stringResource(nameRes)) })
}

@Composable
private fun EntryCard(entry: Entry, onResolve: (String, SpokenIntent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (entry) {
                is Entry.Said -> {
                    Text(stringResource(R.string.said_by_you), style = MaterialTheme.typography.labelMedium)
                    Text(entry.text, style = MaterialTheme.typography.bodyLarge)
                }
                is Entry.Heading -> {
                    Text(stringResource(Sentences.intent(entry.intent)), style = MaterialTheme.typography.titleMedium)
                    // Nothing spoken is not on screen (0026): the lead-in phrase, as it is spoken.
                    entry.leadIn?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                is Entry.Figures -> {
                    Text(stringResource(R.string.answer_from_diary), style = MaterialTheme.typography.labelMedium)
                    entry.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
                is Entry.Plate -> {
                    if (entry.hypothetical) Text(stringResource(R.string.meal_hypothetical), style = MaterialTheme.typography.labelMedium)
                    if (entry.logged) Text(stringResource(R.string.meal_logged), style = MaterialTheme.typography.labelMedium)
                    entry.items.forEach { ItemLine(it) }
                    entry.figures.forEach { (line, band) ->
                        Text(stringResource(R.string.figure_with_band, line, stringResource(Sentences.band(band))), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is Entry.Advice -> {
                    Text(stringResource(R.string.advice_title), style = MaterialTheme.typography.labelMedium)
                    Text(entry.trigger ?: stringResource(R.string.advice_no_rule), fontWeight = FontWeight.SemiBold)
                    entry.phrased?.let { Text(it) }
                    entry.referral?.let { Text(it, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                    if (entry.candidates.isNotEmpty()) {
                        Text(stringResource(R.string.advice_candidates_title), style = MaterialTheme.typography.labelMedium)
                        entry.candidates.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    SafetyLine()
                }
                is Entry.Answer -> {
                    Text(stringResource(R.string.answer_title), style = MaterialTheme.typography.labelMedium)
                    // Their own lines first, from the database: the answer is in them, and the
                    // model's sentence is the part that arrives late (Vedant, 20 Sep). A refused
                    // answer (0024) is the fixed line over the same lines.
                    if (entry.figures.isNotEmpty()) {
                        Text(stringResource(R.string.answer_from_diary), style = MaterialTheme.typography.labelSmall)
                        entry.figures.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    Text(entry.text ?: stringResource(R.string.answer_refused), style = MaterialTheme.typography.bodyLarge)
                    entry.referral?.let { Text(it, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                    SafetyLine()
                }
                is Entry.AskIntent -> {
                    Text(stringResource(R.string.ask_intent_question))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onResolve(entry.transcript, SpokenIntent.LOG) }) { Text(stringResource(R.string.intent_log)) }
                        OutlinedButton(onClick = { onResolve(entry.transcript, SpokenIntent.ANSWER) }) { Text(stringResource(R.string.intent_answer)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onResolve(entry.transcript, SpokenIntent.SUGGEST) }) { Text(stringResource(R.string.intent_suggest)) }
                        OutlinedButton(onClick = { onResolve(entry.transcript, SpokenIntent.RECOMMEND) }) { Text(stringResource(R.string.intent_recommend)) }
                    }
                }
                is Entry.Confirm -> {
                    Text(stringResource(R.string.confirm_title), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(Sentences.unavailable(entry.why)))
                    entry.items.forEach { ItemLine(it) }
                    Text(stringResource(R.string.confirm_hint), style = MaterialTheme.typography.bodySmall)
                }
                is Entry.Failed -> {
                    Text(stringResource(Sentences.unavailable(entry.reason)))
                    entry.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                is Entry.NotBuilt -> Text(stringResource(R.string.not_built, entry.component))
            }
        }
    }
}

@Composable
private fun ItemLine(item: ParsedItem) {
    val quantity = item.quantity
    val line = if (quantity != null) {
        stringResource(R.string.meal_item_with_quantity, item.spokenName, Sentences.number(quantity), item.unit.orEmpty())
    } else {
        stringResource(R.string.meal_item_no_quantity, item.spokenName)
    }
    Text(line, style = MaterialTheme.typography.bodyMedium)
}

/** Spec 15.3: persistent on every advice surface. */
@Composable
fun SafetyLine() {
    Text(stringResource(R.string.safety_not_medical_advice), style = MaterialTheme.typography.labelSmall)
}
