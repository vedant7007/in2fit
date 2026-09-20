package io.github.vedant7007.katori.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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

/** The one separator the app uses. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(In2fitColors.hairline))
}

/** A 12 sp secondary label over a block. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = In2fitText.label, modifier = modifier)
}

/** A screen's title, 28 sp. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = In2fitText.title, modifier = modifier)
}

/**
 * A card that is a thing: a plate, the figures, a report field. Lighter than the ground, 16 dp
 * corner, no shadow; hierarchy comes from the tint (research §1.3). Not for prose blocks.
 */
@Composable
fun Raised(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = In2fitColors.raised, shape = androidx.compose.material3.MaterialTheme.shapes.medium) {
        Column(Modifier.padding(Space.l)) { content() }
    }
}

/** Spec 15.3: once per advice screen, pinned, never inside a card. */
@Composable
fun SafetyLine(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.safety_not_medical_advice), style = In2fitText.label, modifier = modifier)
}

/**
 * The demo build's no-INTERNET fact, in words, at the top of the Talk screen: the deck's first
 * claim, compile-time true, readable over a shoulder. A static string; it never changes.
 */
@Composable
fun OfflineMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(In2fitColors.ink, CircleShape))
        Text(stringResource(R.string.talk_offline_mark), style = In2fitText.label, modifier = Modifier.padding(start = Space.s))
    }
}

/**
 * Three words at the bottom, the chosen one in ink with a 2 dp accent rule under it: a mark, not
 * a pill around a missing icon. 56 dp tall, each a third of the width, one thumb each.
 */
@Composable
fun BottomTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(In2fitColors.ground)) {
        Hairline()
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            titles.forEachIndexed { i, title ->
                val chosen = i == selected
                Column(
                    Modifier.weight(1f).height(56.dp).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        title,
                        style = if (chosen) In2fitText.bodySmall.copy(fontWeight = FontWeight.Medium) else In2fitText.bodySmall,
                        color = if (chosen) In2fitColors.ink else In2fitColors.inkSecondary,
                    )
                    Box(Modifier.padding(top = Space.xs).width(24.dp).height(2.dp).background(if (chosen) In2fitColors.accent else Color.Transparent))
                }
            }
        }
    }
}

/** The one filled control on a screen: 56 dp, ink, cream word. Never in the accent (research §0). */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val fill = if (enabled) In2fitColors.ink else In2fitColors.hairline
    val word = if (enabled) In2fitColors.onPerson else In2fitColors.inkSecondary
    Box(
        modifier.heightIn(min = 56.dp).background(fill, CircleShape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = Space.xl),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = In2fitText.button, color = word) }
}

/** A second action beside the first: a hairline pill, ink word. */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.heightIn(min = 56.dp).border(1.dp, In2fitColors.hairline, CircleShape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = Space.xl),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = In2fitText.button, color = if (enabled) In2fitColors.ink else In2fitColors.inkSecondary) }
}
