package io.github.vedant7007.katori.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.demo.DemoFeed

/**
 * The demo shell: three tabs, no more. Talk carries beats 1, 2 and 4; Scan carries beat 3;
 * About carries the notices, and a long-press on its title opens the pre-flight check.
 *
 * Nothing on any tab is a sample. A path that is not built says so with its component's name
 * (Outcome.kt), and no figure appears that the pipeline did not produce. While the scripted
 * feed (0027) is on, a banner on every tab says so.
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
    var preflight by rememberSaveable { mutableStateOf(false) }
    val demo by DemoFeed.enabled.collectAsState()
    BackHandler(enabled = preflight) { preflight = false }

    Scaffold(
        topBar = {
            if (demo) {
                Text(
                    stringResource(R.string.demo_banner),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error).statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, title ->
                    NavigationBarItem(
                        selected = tab == i && !preflight,
                        onClick = { tab = i; preflight = false },
                        icon = {},
                        label = { Text(stringResource(title)) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        Column(content) {
            when {
                preflight -> PreflightScreen()
                tab == 0 -> TalkScreen()
                tab == 1 -> ScanScreen()
                else -> AboutScreen(onPreflight = { preflight = true })
            }
        }
    }
}
