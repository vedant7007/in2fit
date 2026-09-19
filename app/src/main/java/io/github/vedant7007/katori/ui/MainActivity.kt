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
import androidx.compose.ui.unit.dp

/**
 * Scaffold screen.
 *
 * It shows what is and is not built. It shows NO nutrition figures, no sample meals and no
 * placeholder numbers, because a plausible-looking number in a screenshot is how stub data becomes
 * demo data (spec risk 8). Everything here is either a fact about the build or an explicit
 * not-implemented state.
 */
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

private data class PipelineStatus(val name: String, val state: String)

@Composable
private fun BuildStatusScreen() {
    // Contracts exist; no implementation does. Nothing here is a placeholder for a real value.
    val pipelines = listOf(
        PipelineStatus("Voice logging (ASR, LLM extract, TTS)", "Not implemented"),
        PipelineStatus("Nutrition lookup", "Not implemented"),
        PipelineStatus("Timeline and voice query", "Not implemented"),
        PipelineStatus("Lab report scan", "Not implemented"),
        PipelineStatus("Adaptive suggestions", "Not implemented"),
        PipelineStatus("Camera dish first guess", "Not implemented"),
        PipelineStatus("Exercise form check", "Not implemented"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Katori", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Phase 1B scaffold. Contracts are defined; no pipeline is built yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        pipelines.forEach {
            Text("${it.name}: ${it.state}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
