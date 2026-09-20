package io.github.vedant7007.katori.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ml.vision.LabField
import io.github.vedant7007.katori.ui.Sentences
import io.github.vedant7007.katori.ui.theme.In2fitColors
import io.github.vedant7007.katori.ui.theme.In2fitText
import io.github.vedant7007.katori.ui.theme.Space

/**
 * One value read off the report, beside the row it came from, for the person to confirm. The
 * printed range is drawn: a hairline from the printed low to the printed high, the value as a
 * dot on it; a value outside the line sits past its end in the accent with the word "below" or
 * "above" beside it. The app says nothing the report did not print (0018, spec 15.1): the
 * comparison is the same one the rules engine already speaks. No range printed, no line.
 */
@Composable
fun ReportField(field: LabField, ticked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val lo = field.referenceLow
    val hi = field.referenceHigh
    Raised(modifier.clickable(onClick = onToggle)) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = ticked, onCheckedChange = { onToggle() }, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(start = Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(field.testName, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Sentences.number(field.value), style = In2fitText.figure, softWrap = false)
                    field.unit?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = In2fitText.label, modifier = Modifier.padding(start = Space.xs, bottom = 3.dp))
                    }
                    if (lo != null && hi != null) {
                        val outside = when {
                            field.value < lo -> R.string.scan_below_range
                            field.value > hi -> R.string.scan_above_range
                            else -> null
                        }
                        if (outside != null) {
                            Text(stringResource(outside), style = In2fitText.label.copy(color = In2fitColors.accentInk), modifier = Modifier.padding(start = Space.s, bottom = 3.dp))
                        }
                    }
                }
                when {
                    lo != null && hi != null -> RangeLine(field.value, lo, hi)
                    hi != null -> Text(stringResource(R.string.scan_range_high_only, Sentences.number(hi)), style = In2fitText.label)
                    lo != null -> Text(stringResource(R.string.scan_range_low_only, Sentences.number(lo)), style = In2fitText.label)
                    else -> Text(stringResource(R.string.scan_range_none), style = In2fitText.label)
                }
                // The row as read, so the person can check the parse against the print.
                Text(field.sourceRow, style = In2fitText.label)
            }
        }
    }
}

/** The printed range as a line with its two bounds under the ends, and the value as a dot. */
@Composable
private fun RangeLine(value: Double, lo: Double, hi: Double) {
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val pad = 16.dp.toPx()
            val y = size.height / 2
            val x0 = pad
            val x1 = size.width - pad
            drawLine(In2fitColors.hairline, Offset(x0, y), Offset(x1, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(In2fitColors.inkSecondary, Offset(x0, y - 4.dp.toPx()), Offset(x0, y + 4.dp.toPx()), strokeWidth = 1.5f.dp.toPx())
            drawLine(In2fitColors.inkSecondary, Offset(x1, y - 4.dp.toPx()), Offset(x1, y + 4.dp.toPx()), strokeWidth = 1.5f.dp.toPx())
            val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
            val f = ((value - lo) / span).toFloat()
            val inside = f in 0f..1f
            val x = when {
                f < 0f -> pad / 2
                f > 1f -> size.width - pad / 2
                else -> x0 + (x1 - x0) * f
            }
            drawCircle(if (inside) In2fitColors.ink else In2fitColors.accent, radius = 5.dp.toPx(), center = Offset(x, y))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Sentences.number(lo), style = In2fitText.label)
            Text(Sentences.number(hi), style = In2fitText.label)
        }
    }
}
