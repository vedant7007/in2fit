package io.github.vedant7007.katori.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vedant7007.katori.R

/**
 * Licences and notices. The components list is legal text and is not translated; the verbatim
 * copyright notice for the voice models is Nila's to supply and is shown as pending until it is.
 */
@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.about_offline))
        Text(stringResource(R.string.about_licences_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_notices_pending), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_components), style = MaterialTheme.typography.bodySmall)
        SafetyLine()
    }
}
