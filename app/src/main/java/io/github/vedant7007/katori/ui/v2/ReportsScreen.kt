package io.github.vedant7007.katori.ui.v2

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.LabRow
import io.github.vedant7007.katori.ui.LabStatus
import io.github.vedant7007.katori.ui.ReportsViewModel
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.num
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reports as drawn: the serif title and its line, one 26 dp card per report (the date where the
 * design names the panel, the count of values where it names the lab, the "Active" pill on
 * every report because the coach reads every saved value), one row per value with the PRINTED
 * range under the name and the value's standing against that range on the right, then "How this
 * changes your day" with the engine's own sentence, then the dashed "Add another report".
 *
 * WORDS CHANGED, on the ruling of 21 Sep (amendment 2): the design's "Normal < 5.7" is "printed
 * range up to 5.7"; its flags "Prediabetic", "Low", "Slightly high" are "above the printed
 * range" / "below the printed range" / "within the printed range", one colour for out of range
 * (the app cannot grade "slightly"); a value with no printed range carries no flag. The design's
 * "Scanned 12 Sep · Apollo Diagnostics" needs a lab name the extractor does not read (MISSING,
 * Priya); the line carries the count of values. The three "how this changes your day" bullets
 * are the engine's trigger sentence for the last meal, one bullet, and the card is absent when
 * no rule fired.
 */
@Composable
fun ReportsScreen(onOpenMarker: (String) -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier, vm: ReportsViewModel = hiltViewModel()) {
    val s = scheme()
    val state by vm.state.collectAsState()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        T(stringResource(R.string.v2_reports_title), serif(27f, 1.2f), color = s.text, modifier = Modifier.padding(bottom = 4.dp))
        T(stringResource(R.string.v2_reports_sub), sans(13f, FontWeight.Normal, 1.45f), color = s.text2, modifier = Modifier.padding(bottom = 22.dp))
        state.reports.forEach { report -> ReportCard(report, onOpenMarker) }
        val trigger = state.lastMealTrigger
        if (trigger != null) {
            Card2(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = 26.dp) {
                Column(Modifier.padding(20.dp)) {
                    T(stringResource(R.string.v2_reports_effects).uppercase(), micro(12f, 0.10.em, FontWeight.SemiBold), color = s.text3, modifier = Modifier.padding(bottom = 14.dp))
                    Row(Modifier.padding(bottom = 13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 7.dp).size(7.dp).background(s.accent, CircleShape))
                        T(trigger, sans(14f, FontWeight.Normal, 1.5f), color = s.onMessage)
                    }
                }
            }
        }
        AddReport(onScan)
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    }
}

@Composable
private fun ReportCard(report: ReportsViewModel.Report, onOpenMarker: (String) -> Unit) {
    val s = scheme()
    Card2(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = 26.dp) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(stringResource(R.string.scan_report_date, dateLong(report.date)), sans(15f, FontWeight.SemiBold, 1.3f), color = s.text)
                    T(stringResource(R.string.scan_saved, report.values.size), sans(12.5f, FontWeight.Normal, 1.45f), color = s.text2, modifier = Modifier.padding(top = 3.dp))
                }
                Box(Modifier.background(s.accent.copy(alpha = 0.12f), RoundedCornerShape(100.dp)).padding(horizontal = 11.dp, vertical = 6.dp)) {
                    T(stringResource(R.string.v2_reports_active).uppercase(), micro(11f, 0.06.em, FontWeight.SemiBold), color = s.accent)
                }
            }
            report.values.forEach { row -> MarkerRow(row) { onOpenMarker(row.testName) } }
        }
    }
}

@Composable
private fun MarkerRow(row: LabRow, onClick: () -> Unit) {
    val s = scheme()
    val tint = statusColor(row.status)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(s.text.copy(alpha = 0.06f)))
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(row.testName, sans(14.5f, FontWeight.Normal, 1.3f), color = s.text)
                T(printedRange(row), sans(12f, FontWeight.Normal, 1.3f), color = s.text3, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                T(labValue(row.value) + " " + row.unit, sans(15f, FontWeight.SemiBold, 1.2f), color = tint, maxLines = 1)
                val flag = statusWord(row.status)
                if (flag != null) T(flag, sans(11.5f, FontWeight.Normal, 1.2f), color = tint.copy(alpha = 0.8f), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun AddReport(onScan: () -> Unit) {
    val s = scheme()
    val line = s.text.copy(alpha = 0.14f)
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(line, cornerRadius = CornerRadius(22.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            }
            .clickable(onClick = onScan)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(s.text.copy(alpha = 0.05f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon2(Glyphs.upload, 18.dp, s.accent)
        }
        Column {
            T(stringResource(R.string.v2_reports_add), sans(14f, FontWeight.SemiBold, 1.3f), color = s.text)
            T(stringResource(R.string.v2_reports_add_sub), sans(12.5f, FontWeight.Normal, 1.45f), color = s.text2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * One marker as drawn: the back link, the test's name in serif 29, the latest value in serif 44
 * (Plex, ×1.02) with its unit and its standing against the printed range, the "Last four tests"
 * bars (every report of this test, oldest first, the newest in the warm colour when it is out
 * of its printed range), and the explanation card, which waits on Priya's sourced file and is
 * absent until then.
 */
@Composable
fun MarkerScreen(test: String, onBack: () -> Unit, modifier: Modifier = Modifier, vm: ReportsViewModel = hiltViewModel()) {
    val s = scheme()
    val state by vm.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(test) { vm.select(test) }
    // Oldest first from the ViewModel; the design draws the last four.
    val history = state.history.filter { it.testName == test }.takeLast(4)
    val latest = history.lastOrNull()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Row(Modifier.clickable(onClick = onBack).padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon2(Glyphs.arrowLeft, 14.dp, s.text2)
            T(stringResource(R.string.v2_reports_title), sans(13.5f, FontWeight.Normal, 1.2f), color = s.text2)
        }
        T(test, serif(29f, 1.2f), color = s.text)
        if (latest != null) {
            val tint = statusColor(latest.status)
            Row(Modifier.padding(top = 10.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                T(labValue(latest.value), num(44f, FontWeight.Normal, besideSerif = true, lineHeight = 1f), color = tint)
                T(listOfNotNull(latest.unit, statusWord(latest.status)).joinToString(" · "), sans(14f, FontWeight.Normal, 1.2f), color = s.text2, modifier = Modifier.padding(bottom = 4.dp))
            }
            Card2(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = 26.dp) {
                Column(Modifier.padding(22.dp)) {
                    T(stringResource(R.string.v2_marker_history).uppercase(), micro(12f, 0.10.em, FontWeight.SemiBold), color = s.text3, modifier = Modifier.padding(bottom = 18.dp))
                    // The bars span the person's own values (the lowest at 12 dp, the highest at 64
                    // dp), as the design's do, inside its 110 dp; a single value stands at the top.
                    val lo = history.minOf { it.value }
                    val span = (history.maxOf { it.value } - lo).takeIf { it > 0 } ?: 1.0
                    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                        history.forEach { h ->
                            val out = h.status == LabStatus.ABOVE || h.status == LabStatus.BELOW
                            val newest = h === latest
                            val bar = when {
                                newest && out -> s.warm
                                out -> s.warm.copy(alpha = 0.35f)
                                else -> s.text.copy(alpha = 0.12f)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                T(labValue(h.value), sans(12f, FontWeight.SemiBold, 1.2f), color = if (newest && out) s.warm else s.text2, maxLines = 1)
                                val ratio = if (history.size == 1) 1f else ((h.value - lo) / span).toFloat()
                                Box(Modifier.fillMaxWidth().height(12.dp + 52.dp * ratio).background(bar, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)))
                                T(dateShort(h.reportDate), sans(10.5f, FontWeight.Normal, 1.2f), color = s.text3, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
    }
}

@Composable
private fun statusColor(status: LabStatus): Color = when (status) {
    LabStatus.ABOVE, LabStatus.BELOW -> scheme().warm
    LabStatus.WITHIN -> scheme().accent
    LabStatus.NO_RANGE -> scheme().text
}

@Composable
private fun statusWord(status: LabStatus): String? = when (status) {
    LabStatus.ABOVE -> stringResource(R.string.scan_above_range)
    LabStatus.BELOW -> stringResource(R.string.scan_below_range)
    LabStatus.WITHIN -> stringResource(R.string.v2_within_range)
    LabStatus.NO_RANGE -> null
}

@Composable
private fun printedRange(row: LabRow): String {
    val lo = row.referenceLow
    val hi = row.referenceHigh
    return when {
        lo != null && hi != null -> stringResource(R.string.scan_range_both, labValue(lo), labValue(hi))
        hi != null -> stringResource(R.string.scan_range_high_only, labValue(hi))
        lo != null -> stringResource(R.string.scan_range_low_only, labValue(lo))
        else -> stringResource(R.string.scan_range_none)
    }
}

/** A lab value as the sheet printed it, to two decimals at most, trailing zeros off: 6.4, 118, 0.85. */
fun labValue(v: Double): String = java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun dateLong(d: LocalDate): String = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(d)
private fun dateShort(d: LocalDate): String = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault()).format(d)
