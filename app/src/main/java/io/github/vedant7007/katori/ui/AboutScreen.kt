package io.github.vedant7007.katori.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.components.Hairline
import io.github.vedant7007.katori.ui.components.Label
import io.github.vedant7007.katori.ui.components.SafetyLine
import io.github.vedant7007.katori.ui.theme.In2fitColors
import io.github.vedant7007.katori.ui.theme.In2fitText
import io.github.vedant7007.katori.ui.theme.Space

/**
 * Licences and notices. The components list is legal text and is not translated; the USDA
 * lines are read from the food database's own `meta` table; the verbatim copyright notice for
 * the voice models is Nila's to supply and is shown as pending until it is.
 *
 * A long-press on the wordmark opens the pre-flight check. Nothing on screen says so: judges
 * never find it by accident, and the person setting up the phone is told.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(onPreflight: () -> Unit, vm: AboutViewModel = hiltViewModel()) {
    val meta by vm.meta.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Image(
            painterResource(R.drawable.wordmark),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.width(220.dp).padding(top = Space.s).combinedClickable(onClick = {}, onLongClick = onPreflight),
        )
        Text(stringResource(R.string.about_offline), style = In2fitText.body)
        Text(stringResource(R.string.about_licences_title), style = In2fitText.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), modifier = Modifier.padding(top = Space.s))
        if (meta.attribution.isNotEmpty()) {
            Label(stringResource(R.string.about_usda_title))
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(meta.attribution, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary)
                Text(meta.releases, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary)
                Text(meta.licence, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary)
                Text(meta.disclosure, style = In2fitText.bodySmall)
            }
        }
        Text(stringResource(R.string.about_notices_pending), style = In2fitText.bodySmall)
        // One legal string, one line per component; a hairline between entries, never a paragraph.
        Column {
            stringResource(R.string.about_components).split("\n").forEachIndexed { i, line ->
                if (i > 0) Hairline()
                Text(line, style = In2fitText.bodySmall, color = In2fitColors.inkSecondary, modifier = Modifier.padding(vertical = Space.s))
            }
        }
        SafetyLine(Modifier.padding(top = Space.s, bottom = Space.l))
    }
}
