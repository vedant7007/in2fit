package io.github.vedant7007.katori.ui.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.Stage
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.ui.Sentences
import io.github.vedant7007.katori.ui.TalkViewModel.Entry
import io.github.vedant7007.katori.ui.components.FigureLine
import io.github.vedant7007.katori.ui.theme.LocalReduceMotion
import io.github.vedant7007.katori.ui.theme.Motion
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.num
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif

/** What the sheet shows, derived from `TalkViewModel.State` by the shell and nothing else. */
sealed interface SheetPhase {
    /** The thumb is down. [live] from MicrophoneLive on; [language] is the name of the language chosen. */
    data class Listening(val language: String, val live: Boolean) : SheetPhase

    /** The turn is running past the microphone: the transcript once it is known, the stages so far. */
    data class Analysing(val transcript: String?, val stages: List<Stage>) : SheetPhase

    /** The plate the turn produced. [at] is the clock time it reached the screen. */
    data class Result(val plate: Entry.Plate, val at: String) : SheetPhase
}

/**
 * The voice sheet as drawn: a scrim over the screen, the panel rising from the bottom (34 dp
 * top corners, 14 / 22 / 34 padding, the 38×4 handle), and one of three bodies.
 *
 * NOT AS DRAWN, and why: (1) the design streams the transcript word by word while listening;
 * the recogniser has no partial results (0022, 0026), so the transcript slot stays empty until
 * the whole sentence is written down and the level bars carry the signal instead, each bar one
 * real sample of the microphone level. (2) The design spins the current stage's ring; nothing
 * animates during inference (ruled 20 Sep), so the current ring is the accent outline and the
 * seconds counter beside it is the honesty device (0026). (3) The design's "Add to today" is a
 * confirm-before-save; the pipeline saves at MealResolved (ruled right, 21 Sep), so the pill
 * says "Saved" and Discard deletes what was saved. (4) The design's "Portions use your katori" caption states a
 * learned katori the system does not have; the slot carries the safety line, which the screen
 * must show once anyway.
 */
@Composable
fun VoiceSheet(
    phase: SheetPhase,
    levels: List<Float>,
    elapsed: State<Int>,
    onClose: () -> Unit,
    onDiscard: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val s = scheme()
    val reduce = LocalReduceMotion.current
    val rise = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) rise.animateTo(1f, Motion.spatial) }
    val shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.96f
    Box(modifier.fillMaxSize().background(s.scrim)) {
        // The scrim closes the sheet; the turn behind it keeps running (the shell decides what closing means).
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - rise.value) * 24.dp.toPx(); alpha = rise.value }
                .background(s.sheet, shape)
                .border(1.dp, s.text.copy(alpha = 0.09f), shape)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 34.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 22.dp).size(38.dp, 4.dp).background(s.text.copy(alpha = 0.18f), RoundedCornerShape(2.dp)))
            when (phase) {
                is SheetPhase.Listening -> ListeningBody(phase.language, phase.live, levels)
                is SheetPhase.Analysing -> AnalysingBody(phase.transcript, phase.stages, elapsed)
                is SheetPhase.Result -> ResultBody(phase.plate, phase.at, onClose, onDiscard)
            }
        }
    }
}

@Composable
private fun ListeningBody(language: String, live: Boolean, levels: List<Float>) {
    val s = scheme()
    val reduce = LocalReduceMotion.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 6.dp, bottom = 26.dp).size(84.dp), contentAlignment = Alignment.Center) {
            if (!reduce) {
                // The design's pulse: a ring at 25% accent, 1 → 1.35 while it fades, every 1.9 s. Recording, not inference.
                val t by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart))
                val k = if (t < 0.5f) t * 2f else (1f - t) * 2f
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = 1f + 0.35f * k; scaleY = 1f + 0.35f * k; alpha = 0.5f * (1f - k) }.background(s.accent.copy(alpha = 0.25f), CircleShape))
            }
            Box(Modifier.fillMaxSize().background(s.accent, CircleShape), contentAlignment = Alignment.Center) { Icon2(Glyphs.mic, 30.dp, s.onAccent) }
        }
        // Thirteen bars, 4×30, each the microphone's level at one moment: a meter, not a decoration.
        Row(Modifier.height(34.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(13) { i ->
                val v = levels.getOrNull(i) ?: 0f
                Box(Modifier.size(4.dp, 30.dp).graphicsLayer { scaleY = 0.25f + 0.75f * v }.background(s.accent.copy(alpha = 0.75f), RoundedCornerShape(2.dp)))
            }
        }
        Spacer(Modifier.height(24.dp))
        // The hold is deliberate (ruled 21 Sep, on measurement): "Hold to speak" until the first frame
        // with signal, "Listening · <language>" from MicrophoneLive on, never on the press.
        T(
            (if (live) stringResource(R.string.v2_listening, language) else stringResource(R.string.mic_hold_to_speak)).uppercase(),
            micro(11.5f, 0.14.em, FontWeight.SemiBold), color = if (live) s.text3 else s.accent, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        // The transcript's slot, as drawn (serif 25, 100 dp): the recogniser has no partial results, so
        // the slot carries Jacob's five-word hint in the label style until the sentence is written down.
        Box(Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.TopCenter) {
            T(stringResource(R.string.mic_hold_hint), micro(10.5f, 0.12.em, FontWeight.SemiBold), color = s.text4, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AnalysingBody(transcript: String?, stages: List<Stage>, elapsed: State<Int>) {
    val s = scheme()
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 14.dp)) {
        if (transcript != null) {
            T("“$transcript”", serif(22f, 1.4f), color = s.quote, modifier = Modifier.padding(bottom = 26.dp))
        }
        StageRows(stages, elapsed)
    }
}

/**
 * The stages so far, one row each (0026: named, ticked as they complete, the seconds beside the
 * current one). The ring is 22 dp with a 1.5 dp border; done fills it with the accent and a tick.
 */
@Composable
fun StageRows(stages: List<Stage>, elapsed: State<Int>, counter: Boolean = true) {
    val s = scheme()
    Column(Modifier.fillMaxWidth()) {
        stages.forEachIndexed { i, stage ->
            val done = i < stages.lastIndex
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 1.dp)
                        .size(22.dp)
                        .then(if (done) Modifier.background(s.accent, CircleShape) else Modifier)
                        .border(1.5.dp, s.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (done) Icon2(Glyphs.tick, 12.dp, s.onAccent) }
                Column(Modifier.weight(1f)) {
                    T(stringResource(Sentences.stage(stage)), sans(14.5f, FontWeight.SemiBold, 1.3f), color = s.text)
                    // The counter only where a clock feeds it; a frozen "0 s" would be a lie.
                    if (!done && counter) {
                        T(stringResource(R.string.talk_elapsed_seconds, elapsed.value), sans(13f, FontWeight.Normal, 1.45f), color = s.text2, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}

/** A figure's value split for the hero: "412 kcal" → 412 and kcal; anything else stays whole. */
private fun splitValue(value: String): Pair<String, String>? {
    val i = value.indexOf(' ')
    if (i <= 0 || !value[0].isDigit() || value.indexOf(' ', i + 1) >= 0) return null
    return value.substring(0, i) to value.substring(i + 1)
}

@Composable
private fun ResultBody(plate: Entry.Plate, at: String, onClose: () -> Unit, onDiscard: (() -> Unit)?) {
    val s = scheme()
    val figures = plate.figures.map { (line, band) -> FigureLine.fromRendered(line, band) }
    val energy = figures.firstOrNull { it.name == stringResource(R.string.nutrient_energy) }
    val protein = figures.firstOrNull { it.name == stringResource(R.string.nutrient_protein) }
    val carbs = figures.firstOrNull { it.name == stringResource(R.string.nutrient_carbohydrate) }
    val fat = figures.firstOrNull { it.name == stringResource(R.string.nutrient_fat) }
    val band: ConfidenceBand? = plate.figures.maxOfOrNull { it.second }
    val status = when {
        plate.hypothetical -> stringResource(R.string.meal_hypothetical)
        band != null -> stringResource(Sentences.band(band))
        else -> null
    }
    Column(Modifier.fillMaxWidth()) {
        // A FlowRow: on a narrow screen at a large font scale the three macros drop under the hero
        // rather than squeeze it (hostile pass, 21 Sep); on the design's frame they sit beside it.
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false)) {
                // The design's "Lunch · 1:42 pm": the slot waits on meal slots (MISSING); the time
                // is the clock, and the band is the plate's, said once.
                T(listOfNotNull(at, status).joinToString(" · ").uppercase(), micro(11.5f, 0.14.em, FontWeight.SemiBold), color = s.text3)
                Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    val split = energy?.let { splitValue(it.value) }
                    when {
                        split != null -> {
                            N(split.first, num(38f, FontWeight.Normal, besideSerif = true, lineHeight = 1f), color = s.text)
                            T(split.second, sans(14f, FontWeight.Normal, 1.2f), color = s.text2, modifier = Modifier.padding(bottom = 2.dp))
                        }
                        energy != null -> T(energy.value, sans(14f, FontWeight.Normal, 1.2f), color = s.text2)
                    }
                }
            }
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Macro(protein, stringResource(R.string.v2_protein))
                Macro(carbs, stringResource(R.string.v2_carbs))
                Macro(fat, stringResource(R.string.v2_fat))
            }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The resolved rows (da67d04) carry the name shown, the grams and the item's own
            // figures; until the orchestrator fills them the items as said stand in.
            if (plate.rows.isNotEmpty()) plate.rows.forEach { ResolvedRow(it) } else plate.items.forEach { ItemRow(it) }
        }
        // The caption slot: the safety line, once per screen, in the design's caption style.
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Discard deletes the meal the pipeline has already logged; with nothing to delete
            // (a hypothetical plate, or before MealLogged) it is drawn and dimmed, never a lie.
            Pill2(stringResource(R.string.v2_discard), onClick = onDiscard ?: {}, filled = false, enabled = onDiscard != null, modifier = Modifier.alpha(if (onDiscard != null) 1f else 0.32f))
            // Ruled 21 Sep: the pipeline saves at MealResolved and that is right; the pill reads
            // "Saved" (Discard is beside it), "Close" for a plate that was only asked about.
            val primary = if (plate.hypothetical || !plate.logged) R.string.v2_close else R.string.v2_saved
            Pill2(stringResource(primary), onClick = onClose, modifier = Modifier.weight(1f), horizontal = 16.dp)
        }
    }
}

@Composable
private fun Macro(figure: FigureLine?, label: String) {
    val s = scheme()
    // Designed empty: a macro the store did not measure draws nothing in its column, never a 0.
    if (figure == null) return
    Column(horizontalAlignment = Alignment.End) {
        N(figure.value, sans(15f, FontWeight.SemiBold, 1.2f), color = s.text)
        T(label, sans(11f, FontWeight.Normal, 1.2f), color = s.text3, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * One food as drawn: the name the database shows, the portion chip ("2 pieces · 90 g" in the
 * design; here the amount as said, or the amount the resolver assumed with "taken as N g" after
 * it — only then, 0035, Priya's rule: a stated amount shows as said and its grams stay behind
 * the band), and the item's own energy and protein on the right, measured or absent, never 0.
 */
@Composable
private fun ResolvedRow(row: Entry.PlateItem) {
    val s = scheme()
    val quantity = row.quantity
    val amount = if (quantity != null) listOfNotNull(Sentences.number(quantity), row.unit).joinToString(" ") else null
    val takenAs = if (row.inferred && row.grams != null) stringResource(R.string.plate_unit_taken_as, fig(row.grams)) else null
    val energy = row.nutrients[Nutrient.ENERGY]
    val protein = row.nutrients[Nutrient.PROTEIN]
    Card2(Modifier.fillMaxWidth(), radius = 20.dp, bg = s.card2, line = s.text.copy(alpha = 0.07f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(row.name, sans(15f, FontWeight.SemiBold, 1.3f), color = s.text)
                if (amount != null || takenAs != null) PortionChip(amount, takenAs)
            }
            Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.End) {
                if (energy != null) N(stringResource(R.string.v2_item_kcal, fig(energy)), sans(15f, FontWeight.SemiBold, 1.2f), color = s.text)
                if (protein != null) N(stringResource(R.string.v2_item_protein, fig(protein)), sans(11.5f, FontWeight.Normal, 1.2f), color = s.text3, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/**
 * The design's portion chip: accent at 10% over a 22% accent hairline, 12.5 sp, the pencil
 * after. The amount is one line that shrinks before it wraps (a number never wraps from its
 * unit); the "taken as N g" caption, when there is one, sits under it inside the chip with its
 * number and unit joined by a no-break space.
 */
@Composable
fun PortionChip(amount: String?, takenAs: String?) {
    val s = scheme()
    Column(
        Modifier
            .padding(top = 8.dp)
            .background(s.accent.copy(alpha = 0.10f), RoundedCornerShape(100.dp))
            .border(1.dp, s.accent.copy(alpha = 0.22f), RoundedCornerShape(100.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (amount != null) N(amount, sans(12.5f, FontWeight.Normal, 1.2f), color = s.accent, modifier = Modifier.weight(1f, fill = false))
            // The pencil is drawn; the portion editor it opens waits on a correction path in the
            // contract (Correction.Quantity exists, no intent sends it).
            Icon2(Glyphs.pencil, 11.dp, s.accent)
        }
        if (takenAs != null) T(noBreakUnit(takenAs), sans(12.5f, FontWeight.Normal, 1.3f), color = s.accent)
    }
}

/** "taken as 180 g" with its last space made unbreakable, so the unit stays on the number's line. */
fun noBreakUnit(text: String): String {
    val i = text.lastIndexOf(' ')
    return if (i > 0) text.substring(0, i) + " " + text.substring(i + 1) else text
}

/** The item as said, before the resolved rows reach the entry. */
@Composable
private fun ItemRow(item: ParsedItem) {
    val s = scheme()
    Card2(Modifier.fillMaxWidth(), radius = 20.dp, bg = s.card2, line = s.text.copy(alpha = 0.07f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(item.spokenName, sans(15f, FontWeight.SemiBold, 1.3f), color = s.text)
                val quantity = item.quantity
                if (quantity != null) PortionChip(listOfNotNull(Sentences.number(quantity), item.unit).joinToString(" "), null)
            }
            // The item's own figures: designed empty until the resolved rows reach the entry.
        }
    }
}
