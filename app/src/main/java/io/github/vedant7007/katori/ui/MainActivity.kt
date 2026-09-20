package io.github.vedant7007.katori.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import io.github.vedant7007.katori.R

/**
 * The demo shell: three tabs, no more. Talk carries beats 1, 2 and 4; Scan carries beat 3;
 * About carries the notices. No settings, no onboarding, no profile editor.
 *
 * Nothing on any tab is a sample. A path that is not built says so with its component's name
 * (Outcome.kt), and no figure appears that the pipeline did not produce.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { Shell() }
            }
        }
    }
}

private val tabs = listOf(R.string.tab_talk, R.string.tab_scan, R.string.tab_about)

@Composable
private fun Shell() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, title ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {},
                        label = { Text(stringResource(title)) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            0 -> Box(content) { TalkScreen() }
            1 -> Box(content) { ScanScreen() }
            else -> Box(content) { AboutScreen() }
        }
    }
}
