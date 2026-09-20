package io.github.vedant7007.katori.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R

/** The setup check. Facts about this phone now; a Load button per model; the scripted-feed switch. */
@Composable
fun PreflightScreen(vm: PreflightViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val yes = stringResource(R.string.preflight_yes)
    val no = stringResource(R.string.preflight_no)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.preflight_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.preflight_intro), style = MaterialTheme.typography.bodySmall)

        Line(R.string.preflight_app, "${state.applicationId} ${state.appVersion}")
        Line(R.string.preflight_device, state.device)
        Line(R.string.preflight_locale, state.locale)
        Line(R.string.preflight_mic, if (state.micGranted) yes else no)
        Line(R.string.preflight_camera, if (state.cameraGranted) yes else no)
        Line(R.string.preflight_models_dir, state.modelsDir)
        Line(R.string.preflight_free_space, PreflightViewModel.mb(state.freeBytes))
        Line(R.string.preflight_voices, state.voices)
        Line(R.string.preflight_residency, state.residency)

        Text(stringResource(R.string.preflight_models_title), style = MaterialTheme.typography.titleMedium)
        state.models.forEach { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.handle.id, style = MaterialTheme.typography.labelLarge)
                    Text(row.file.absolutePath, style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (row.present) stringResource(R.string.preflight_present, PreflightViewModel.mb(row.bytes))
                        else stringResource(R.string.preflight_absent),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    row.load?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(enabled = row.present && !row.loading, onClick = { vm.load(row.handle) }) {
                        Text(stringResource(if (row.loading) R.string.preflight_loading else R.string.preflight_load))
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.demo_switch_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.demo_switch_hint), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = state.demo, onCheckedChange = vm::setDemo)
        }
        OutlinedButton(onClick = vm::refresh, modifier = Modifier.padding(bottom = 16.dp)) { Text(stringResource(R.string.preflight_refresh)) }
    }
}

@Composable
private fun Line(labelRes: Int, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
