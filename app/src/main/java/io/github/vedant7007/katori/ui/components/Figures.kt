package io.github.vedant7007.katori.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text as M3Text
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.ui.Sentences
import io.github.vedant7007.katori.ui.theme.In2fitColors
import io.github.vedant7007.katori.ui.theme.In2fitText
import io.github.vedant7007.katori.ui.theme.Space

/**
 * One figure as a row wants it: the nutrient's word, the value with its unit as one token, the
 * band. Built from the rendered line the contract hands the screen today, and shaped to take the
 * parts (nutrient, formatted number, unit, completeness) the hour they are carried on the entry.
 */
data class FigureLine(val name: String, val value: String, val band: ConfidenceBand?) {
    companion object {
        // ponytail: splits at the first ": " exactly as `context_figure` ("%1$s: %2$s %3$s") writes
        // it; a locale that reorders that format needs the parts field instead (asked, COORDINATION
        // 21:01). A line with no ": " is shown whole as the name.
        fun fromRendered(line: String, band: ConfidenceBand? = null): FigureLine {
            val i = line.indexOf(": ")
            return if (i < 0) FigureLine(line, "", band) else FigureLine(line.substring(0, i), line.substring(i + 2), band)
        }
    }
}

/**
 * Name left in 14 sp, value right in 20 sp semibold tabular; the value never wraps. [exception]
 * is the band word shown only when this row's band differs from the card's (research §6.1 rule 6).
 */
@Composable
fun FigureRow(figure: FigureLine, exception: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(figure.name, style = In2fitText.bodySmall)
            if (exception != null) Text(exception, style = In2fitText.label)
        }
        Text(
            figure.value, style = In2fitText.figure, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End, modifier = Modifier.padding(start = Space.l).widthIn(max = 200.dp),
        )
    }
}

/**
 * A column of figure rows with hairlines. The band is said once, at the top, when every row
 * shares it; a row that differs names its own. [hero] is the one figure set at 36 sp above the
 * rows, when the card has one (the energy on a plate, the nutrient asked about).
 */
@Composable
fun FigureList(figures: List<FigureLine>, hero: FigureLine? = null, modifier: Modifier = Modifier) {
    val bands = figures.mapNotNull { it.band }.toSet()
    val common = bands.singleOrNull()
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (hero != null) {
                Column(Modifier.weight(1f)) {
                    Text(hero.value, style = In2fitText.hero, softWrap = false)
                    Text(hero.name, style = In2fitText.label)
                }
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            if (common != null) Text(stringResource(Sentences.band(common)), style = In2fitText.label, modifier = Modifier.padding(bottom = Space.xs))
        }
        figures.forEachIndexed { i, f ->
            if (i > 0 || hero != null) Hairline()
            FigureRow(f, exception = f.band?.takeIf { common == null || it != common }?.let { stringResource(Sentences.band(it)) })
        }
    }
}

/**
 * One item on the plate: the amount as said or as assumed, and, only when the amount was ASSUMED
 * (`QUANTITY_INFERRED`, 0035), the "taken as N g" caption. A stated amount shows as said and its
 * grams stay behind the band (Vedant, 20 Sep). [takenAs] is null until the grams reach the entry.
 */
data class PlateItem(val said: String, val takenAs: String?)

/**
 * The plate as the deck promised it (research §3): the energy as the hero figure, the items as
 * said, one per line, the other figures as aligned rows, the band once. [status] is the "Logged"
 * or "not logged" line the entry carries.
 */
@Composable
fun PlateCard(status: String?, items: List<PlateItem>, figures: List<FigureLine>, modifier: Modifier = Modifier) {
    val energyWord = stringResource(R.string.nutrient_energy)
    val hero = figures.firstOrNull { it.name == energyWord }
    Raised(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            if (status != null) Label(status)
            if (items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    items.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.said, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary)
                            if (item.takenAs != null) Text(item.takenAs, style = In2fitText.label, modifier = Modifier.padding(start = Space.m))
                        }
                    }
                }
            }
            FigureList(figures.filter { it !== hero }, hero = hero)
        }
    }
}
/**
 * A line the store writes for the model and the screen (`ContextText`): a period line is
 * "<heading>: <name>: <value>; <name>: <value>; …" and becomes a heading over figure rows. A lab
 * line ("Haemoglobin: 9.8 g/dL, printed range 12 to 15 (report dated …)") and a meal line
 * ("Saturday 20 Sep, 1:10 pm: roti, dal. iron: 2.5 mg; …") do not have that shape and are shown
 * as the sentence they are. ponytail: shape-sniffing a rendered string; the parts field (asked)
 * replaces it. A part counts as a figure only when its name has no '.' or ',' and its value is
 * short, so a sentence never becomes a row by accident.
 */
fun contextFigures(line: String): Pair<String, List<FigureLine>>? {
    val i = line.indexOf(": ")
    if (i < 0) return null
    val parts = line.substring(i + 2).split("; ").map { FigureLine.fromRendered(it) }
    val looksLikeFigures = parts.all { it.value.isNotEmpty() && it.value.length <= 16 && '.' !in it.name && ',' !in it.name }
    return if (looksLikeFigures) line.substring(0, i) to parts else null
}

/** The person's own lines: figure rows where the line is figures, the sentence where it is not. */
@Composable
fun ContextLines(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        lines.forEach { line ->
            val parsed = contextFigures(line)
            if (parsed != null) {
                Column {
                    M3Text(parsed.first, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary)
                    FigureList(parsed.second)
                }
            } else {
                M3Text(line, style = In2fitText.body)
            }
        }
    }
}

