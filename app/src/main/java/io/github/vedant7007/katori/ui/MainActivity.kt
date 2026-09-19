package io.github.vedant7007.katori.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vedant7007.katori.R

/**
 * Scaffold screen.
 *
 * It shows what is and is not built. It shows NO nutrition figures, no sample meals and no
 * placeholder numbers, because a plausible-looking number in a screenshot is how stub data becomes
 * demo data (spec risk 8). Everything here is either a fact about the build or an explicit
 * not-implemented state.
 */

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BuildStatusScreen()
                }
            }
        }
    }
}

@Composable
private fun BuildStatusScreen() {
    // Contracts exist; no implementation does. Nothing here is a placeholder for a real value.
    // Every string is a resource key: see res/values/strings.xml for the localisation rules.
    val pipelines = listOf(
        R.string.pipeline_voice_logging,
        R.string.pipeline_nutrition_lookup,
        R.string.pipeline_timeline_query,
        R.string.pipeline_lab_report_scan,
        R.string.pipeline_adaptive_suggestions,
        R.string.pipeline_dish_first_guess,
        R.string.pipeline_exercise_form,
    )
    val notImplemented = stringResource(R.string.state_not_implemented)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.status_screen_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        pipelines.forEach {
            Text(
                stringResource(R.string.status_line, stringResource(it), notImplemented),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
