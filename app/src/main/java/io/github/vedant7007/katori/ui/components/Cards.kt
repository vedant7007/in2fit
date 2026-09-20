package io.github.vedant7007.katori.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vedant7007.katori.ui.theme.In2fitColors
import io.github.vedant7007.katori.ui.theme.In2fitText
import io.github.vedant7007.katori.ui.theme.Space
import io.github.vedant7007.katori.ui.theme.arrive

/**
 * A prose block on the ground: a label, then text. For what the app says, as opposed to a plate.
 * It arrives with the entrance unless [arrives] is false, which a caller sets when the block
 * carries a figure: a number appears at once (the figures-first ruling).
 */
@Composable
fun Prose(label: String?, modifier: Modifier = Modifier, arrives: Boolean = true, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().then(if (arrives) Modifier.arrive() else Modifier).padding(vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (label != null) Label(label)
        content()
    }
}

/** The fixed doctor line, set apart by a 3 dp accent rule at its left: a mark, the words in ink. */
@Composable
fun Referral(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = Space.s).height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(In2fitColors.accent))
        Text(text, style = In2fitText.body, modifier = Modifier.padding(start = Space.m))
    }
}

/**
 * Advice: the rules engine's own sentence first, in medium weight, because it is the proof; the
 * model's phrasing under it in regular; the referral set apart; the candidates as a list with
 * hairlines. On a raised surface because it is the thing beat 4 changes.
 */
@Composable
fun AdviceCard(
    label: String,
    trigger: String,
    phrased: String?,
    referral: String?,
    candidatesLabel: String,
    candidates: List<String>,
    modifier: Modifier = Modifier,
) {
    Raised(modifier.arrive()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Label(label)
            Text(trigger, style = In2fitText.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            if (phrased != null) Text(phrased, style = In2fitText.body)
            if (referral != null) Referral(referral)
            if (candidates.isNotEmpty()) {
                Label(candidatesLabel, Modifier.padding(top = Space.s))
                candidates.forEachIndexed { i, c ->
                    if (i > 0) Hairline()
                    Text(c, style = In2fitText.bodySmall, modifier = Modifier.padding(vertical = Space.s))
                }
            }
        }
    }
}

/** The model's sentence, or the fixed refusal over the person's own figures. */
@Composable
fun AnswerCard(label: String, text: String, referral: String?, figures: List<String>, modifier: Modifier = Modifier) {
    Prose(label, modifier, arrives = figures.isEmpty()) {
        if (figures.isNotEmpty()) ContextLines(figures, modifier = Modifier.padding(bottom = Space.s))
        Text(text, style = In2fitText.body)
        if (referral != null) Referral(referral)
    }
}

/**
 * The question is the buttons: full-width 48 dp rows with hairlines, one thumb each, in the
 * bottom half of the screen. [question] is kept small above them; it restates the rows.
 */
@Composable
fun AskCard(question: String, options: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth().arrive(), color = In2fitColors.raised, shape = MaterialTheme.shapes.medium) {
        Column {
            Text(question, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary, modifier = Modifier.padding(Space.l))
            options.forEach { (word, act) ->
                Hairline()
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = act).padding(horizontal = Space.l), contentAlignment = Alignment.CenterStart) {
                    Text(word, style = In2fitText.button)
                }
            }
        }
    }
}

/** A refusal: what could not be done, the items as heard, what to do next. Nothing was saved. */
@Composable
fun ConfirmCard(title: String, sentence: String, items: List<String>, hint: String, modifier: Modifier = Modifier) {
    Prose(title, modifier) {
        Text(sentence, style = In2fitText.body)
        items.forEach { Text(it, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary) }
        Text(hint, style = In2fitText.bodySmall)
    }
}

/** A failed turn is one sentence (0026). The detail is there for whoever taps, in the label style. */
@Composable
fun FailedCard(sentence: String, detail: String?, modifier: Modifier = Modifier) {
    var showDetail by remember { mutableStateOf(false) }
    Prose(null, modifier.clickable(enabled = detail != null) { showDetail = !showDetail }) {
        Text(sentence, style = In2fitText.body)
        if (showDetail && detail != null) Text(detail, style = In2fitText.label)
    }
}
