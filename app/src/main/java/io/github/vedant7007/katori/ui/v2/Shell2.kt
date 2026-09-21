package io.github.vedant7007.katori.ui.v2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.Stage
import io.github.vedant7007.katori.ui.AboutScreen
import io.github.vedant7007.katori.ui.PreflightScreen
import io.github.vedant7007.katori.ui.ScanScreen
import io.github.vedant7007.katori.ui.TalkScreen
import io.github.vedant7007.katori.ui.TalkViewModel
import io.github.vedant7007.katori.ui.TalkViewModel.Entry
import io.github.vedant7007.katori.ui.components.Splash
import io.github.vedant7007.katori.ui.demo.DemoFeed
import io.github.vedant7007.katori.ui.theme.In2fitTheme
import io.github.vedant7007.katori.ui.theme.ThemePreference
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Date

/**
 * The v2 shell: the design's five-place bar (Today, Diary, the microphone, Coach, You) under
 * one content area, the voice sheet over everything, the scripted-feed banner above everything.
 *
 * The microphone opens the sheet and starts the turn on the press (0031); the sheet follows the
 * turn through `TalkViewModel.State` and nothing else: listening while the microphone is live,
 * the stages while the pipeline runs, the plate the moment it lands (a number never waits on a
 * transition). A turn that ends in anything but a plate closes the sheet and opens Coach, where
 * every turn's text lives.
 *
 * UNTIL THEIR STEPS LAND (today, in order): Today, Diary and You open the old Talk, Scan and
 * About screens in their own cream theme, so nothing the phone could do yesterday is lost.
 */
@Composable
fun Shell2() {
    val vm: TalkViewModel = hiltViewModel()
    val quiet = remember(vm) { vm.state.map { it.copy(level = 0f, elapsedSeconds = 0) }.distinctUntilChanged() }
    val state by quiet.collectAsState(vm.state.value.copy(level = 0f, elapsedSeconds = 0))
    val level: State<Float> = remember(vm) { vm.state.map { it.level } }.collectAsState(0f)
    val elapsed: State<Int> = remember(vm) { vm.state.map { it.elapsedSeconds } }.collectAsState(0)
    val demo by DemoFeed.enabled.collectAsState()
    val context = LocalContext.current
    val s = scheme()

    var tab by rememberSaveable { mutableStateOf(Tab2.TODAY) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    // Where this turn's entries begin, so the sheet reads only what its own press produced.
    var openedAt by rememberSaveable { mutableIntStateOf(0) }
    var seenBusy by rememberSaveable { mutableStateOf(false) }
    var splash by rememberSaveable { mutableStateOf(true) }
    // The pre-flight check (Arjun's), reached from the old About screen's title as before.
    var preflight by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = preflight) { preflight = false }

    val open = {
        if (!vm.state.value.busy) {
            openedAt = vm.state.value.entries.size
            seenBusy = false
            sheet = true
            vm.speak()
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) open() }

    val turn = state.entries.drop(openedAt)
    val plate = turn.lastOrNull { it is Entry.Plate } as? Entry.Plate
    val elsewhere = turn.any { it is Entry.Answer || it is Entry.AskIntent || it is Entry.Confirm || it is Entry.Failed || it is Entry.NotBuilt || (it is Entry.Advice && plate == null) }
    LaunchedEffect(state.busy, elsewhere) {
        if (state.busy) seenBusy = true
        else if (sheet && seenBusy && plate == null) { sheet = false; tab = Tab2.COACH }
        if (sheet && elsewhere && plate == null) { sheet = false; tab = Tab2.COACH }
    }
    // Listening from the press until the pipeline moves past the microphone; `seenBusy` covers
    // the frame before the turn's first state reaches the screen.
    val listening = sheet && plate == null && (!seenBusy || state.busy && (state.stage == null || state.stage == Stage.RECORDING))
    // Thirteen samples of the microphone level, one every 80 ms, oldest first: the bars.
    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(listening) {
        samples.clear()
        while (listening) {
            samples.add(level.value)
            if (samples.size > 13) samples.removeAt(0)
            delay(80)
        }
    }
    // The clock when the plate reached the screen, once; `logged` flipping later does not move it.
    val at = remember(plate != null) { if (plate == null) null else DateFormat.getTimeFormat(context).format(Date()) }
    val phase: SheetPhase? = when {
        !sheet -> null
        plate != null -> SheetPhase.Result(plate, at.orEmpty())
        listening -> SheetPhase.Listening(stringResource(languageName(state.language)))
        else -> SheetPhase.Analysing((turn.firstOrNull { it is Entry.Said } as? Entry.Said)?.text, state.stages)
    }
    BackHandler(enabled = sheet) { sheet = false }

    Box(Modifier.fillMaxSize().background(s.ground)) {
        Column(Modifier.fillMaxSize()) {
            if (demo) {
                Text(
                    stringResource(R.string.demo_banner),
                    color = s.onAccent,
                    style = sans(12f, FontWeight.SemiBold, 1.3f),
                    modifier = Modifier.fillMaxWidth().background(s.warm).statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // The design blurs what is behind the sheet by 14 px; the phone can from API 31.
                            renderEffect = if (sheet && Build.VERSION.SDK_INT >= 31) BlurEffect(14.dp.toPx(), 14.dp.toPx(), TileMode.Clamp) else null
                        }
                        .then(if (demo) Modifier else Modifier.statusBarsPadding())
                        .padding(top = 8.dp, bottom = 152.dp),
                ) {
                    when {
                        preflight -> Legacy { PreflightScreen() }
                        tab == Tab2.COACH -> CoachScreen(vm, state, elapsed, onTitleLongPress = { ThemePreference.setLegacy(context, true) })
                        tab == Tab2.TODAY -> Legacy { TalkScreen(vm) }
                        tab == Tab2.DIARY -> Legacy { ScanScreen() }
                        else -> Legacy { AboutScreen(onPreflight = { preflight = true }) }
                    }
                }
                TabBar2(
                    selected = tab,
                    onSelect = { tab = it; preflight = false },
                    micEnabled = !state.busy,
                    onMicPress = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (granted) open() else askMic.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onMicRelease = vm::endSpeech,
                    onMicStop = if (state.stage == Stage.SPEAKING) vm::stopSpeaking else null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                if (phase != null) {
                    VoiceSheet(
                        phase = phase,
                        levels = samples,
                        elapsed = elapsed,
                        onClose = { sheet = false },
                        // Discard waits on a delete for the logged meal (Arjun, (d)).
                        onDiscard = null,
                    )
                }
            }
        }
        if (splash) Splash(ready = { true }, onFinished = { splash = false })
    }
}

/** An old screen inside the v2 shell: its own cream theme on its own ground, until its v2 screen lands. */
@Composable
private fun Legacy(content: @Composable () -> Unit) {
    In2fitTheme(legacy = true) { Surface(Modifier.fillMaxSize()) { content() } }
}

/** The name of the language the person chose to speak in, for "Listening · English". */
private fun languageName(tag: String): Int = when (tag) {
    "te" -> R.string.language_telugu
    "hi" -> R.string.language_hindi
    else -> R.string.language_english
}
