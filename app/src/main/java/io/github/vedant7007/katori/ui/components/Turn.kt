package io.github.vedant7007.katori.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.theme.In2fitColors
import io.github.vedant7007.katori.ui.theme.In2fitText
import io.github.vedant7007.katori.ui.theme.Space

/**
 * The person's words, verbatim, in their script, on the one dark block on the screen. 20 sp at
 * 1.5 line height so Devanagari has room. Never edited (0026). [doubtful] draws a hairline in the
 * accent under the words; nothing sets it today, it waits on the ASR confidence reaching the entry.
 */
@Composable
fun SaidBlock(text: String, doubtful: Boolean = false, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = In2fitColors.person, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(Space.l)) {
            Text(stringResource(R.string.said_by_you), style = In2fitText.label, color = In2fitColors.onPerson.copy(alpha = 0.7f))
            Text(text, style = In2fitText.transcript, color = In2fitColors.onPerson, modifier = Modifier.padding(top = Space.xs))
            if (doubtful) Box(Modifier.padding(top = Space.s).fillMaxWidth().height(2.dp).background(In2fitColors.accent))
        }
    }
}

/** 0026 step 5: which of the four the turn became, 28 sp, with the lead-in as it is spoken. */
@Composable
fun IntentHeading(title: String, leadIn: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = Space.s)) {
        Text(title, style = In2fitText.title)
        if (leadIn != null) Text(leadIn, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary)
    }
}

/**
 * 0026 step 6, with no bar: every stage named, completed ones ticked in a 24 dp column, the
 * current one marked with an accent dot, and the seconds beside it in 28 sp tabular accent, the
 * one moving thing on the screen while the model works. [elapsed] is read here and nowhere
 * else, so the 1 Hz tick recomposes this composable only. The counter does not move
 * horizontally as digits change because Plex's digits are tabular by default.
 */
@Composable
fun StageIndicator(done: List<String>, current: String, elapsed: () -> Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        done.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tick(Modifier.size(24.dp))
                Text(name, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary, modifier = Modifier.padding(start = Space.s))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).background(In2fitColors.accent, CircleShape))
            }
            Text(current, style = In2fitText.body, modifier = Modifier.padding(start = Space.s).weight(1f))
            Text(
                stringResource(R.string.talk_elapsed_seconds, elapsed()),
                style = In2fitText.title, color = In2fitColors.accent, softWrap = false,
                modifier = Modifier.width(88.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

/** A check mark, two strokes, so no icon library is needed for one glyph. */
@Composable
private fun Tick(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(In2fitColors.inkSecondary, Offset(w * 0.28f, h * 0.52f), Offset(w * 0.44f, h * 0.68f), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(In2fitColors.inkSecondary, Offset(w * 0.44f, h * 0.68f), Offset(w * 0.74f, h * 0.36f), strokeWidth = stroke.width, cap = stroke.cap)
    }
}
