package io.github.vedant7007.katori.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R

/**
 * Licences and notices. The components list is legal text and is not translated; the USDA
 * lines are read from the food database's own `meta` table; the verbatim copyright notice for
 * the voice models is Nila's to supply and is shown as pending until it is.
 *
 * A long-press on the title opens the pre-flight check. Nothing on screen says so: judges never
 * find it by accident, and the person setting up the phone is told.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(onPreflight: () -> Unit, vm: AboutViewModel = hiltViewModel()) {
    val meta by vm.meta.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onPreflight),
        )
        Text(stringResource(R.string.about_offline))
        Text(stringResource(R.string.about_licences_title), style = MaterialTheme.typography.titleMedium)
        if (meta.attribution.isNotEmpty()) {
            Text(stringResource(R.string.about_usda_title), style = MaterialTheme.typography.labelLarge)
            Text(meta.attribution, style = MaterialTheme.typography.bodySmall)
            Text(meta.releases, style = MaterialTheme.typography.bodySmall)
            Text(meta.licence, style = MaterialTheme.typography.bodySmall)
            Text(meta.disclosure, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.about_notices_pending), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_components), style = MaterialTheme.typography.bodySmall)
        SafetyLine()
    }
}
