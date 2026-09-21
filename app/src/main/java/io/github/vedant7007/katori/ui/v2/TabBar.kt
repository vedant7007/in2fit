package io.github.vedant7007.katori.ui.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.theme.LocalReduceMotion
import io.github.vedant7007.katori.ui.theme.Motion
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import kotlinx.coroutines.launch

/** The four tabs, in the design's order; the microphone sits between Diary and Coach. */
enum class Tab2 { TODAY, DIARY, COACH, YOU }

/**
 * The tab bar as drawn: four 58 dp columns (a 22 dp glyph, a 10 sp label, 5 dp between), the
 * 64 dp accent microphone lifted 26 dp above them, over a gradient that is solid for its lower
 * 62% and clears to nothing at the top. Padding 10 / 18 / 26 with the navigation inset under it.
 *
 * The microphone is push-to-talk (0031, ruled 20 Sep): the press starts the turn, the release
 * ends the recording. The design taps; the app holds, because the recording has no other end.
 * While the reply is being spoken a tap stops the speech (0026 step 8) and nothing else.
 */
@Composable
fun TabBar2(
    selected: Tab2,
    onSelect: (Tab2) -> Unit,
    micEnabled: Boolean,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onMicStop: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val s = scheme()
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.38f to s.ground, 1f to s.ground))
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Four equal slots around the fixed microphone: the design's 58 dp columns spaced apart
        // land at the same places, and a label at a 2× font scale still has its whole slot.
        TabItem(Glyphs.home, stringResource(R.string.v2_tab_today), selected == Tab2.TODAY, Modifier.weight(1f)) { onSelect(Tab2.TODAY) }
        TabItem(Glyphs.diary, stringResource(R.string.v2_tab_diary), selected == Tab2.DIARY, Modifier.weight(1f)) { onSelect(Tab2.DIARY) }
        MicFab(micEnabled, onMicPress, onMicRelease, onMicStop)
        TabItem(Glyphs.coach, stringResource(R.string.v2_tab_coach), selected == Tab2.COACH, Modifier.weight(1f)) { onSelect(Tab2.COACH) }
        TabItem(Glyphs.you, stringResource(R.string.v2_tab_you), selected == Tab2.YOU, Modifier.weight(1f)) { onSelect(Tab2.YOU) }
    }
}

@Composable
private fun TabItem(glyph: Glyph, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val s = scheme()
    val tint = if (on) s.accent else s.text4
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon2(glyph, 22.dp, tint)
        Spacer(Modifier.height(5.dp))
        T(label, sans(10f, FontWeight.SemiBold, 1.2f), color = tint, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun MicFab(enabled: Boolean, onPress: () -> Unit, onRelease: () -> Unit, onStop: (() -> Unit)?) {
    val s = scheme()
    val haptics = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(1f) }
    // Read through a state holder, never as a key: the press flips `enabled` (the turn goes
    // busy) and a pointerInput keyed on it would restart mid-gesture and lose the release.
    val isEnabled by rememberUpdatedState(enabled)
    val stop by rememberUpdatedState(onStop)
    val label = stringResource(R.string.v2_mic_hold)
    Box(
        Modifier
            .offset(y = (-26).dp)
            .size(64.dp)
            .graphicsLayer { scaleX = press.value; scaleY = press.value }
            .shadow(14.dp, CircleShape, ambientColor = s.accent.copy(alpha = 0.5f), spotColor = s.accent.copy(alpha = 0.5f))
            .background(s.accent, CircleShape)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val stopNow = stop
                    if (stopNow != null) { stopNow(); return@detectTapGestures }
                    if (isEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!reduceMotion) scope.launch { press.animateTo(0.94f, Motion.spatial) }
                        onPress()
                        tryAwaitRelease()
                        if (!reduceMotion) scope.launch { press.animateTo(1f, Motion.spatial) }
                        onRelease()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon2(Glyphs.mic, 27.dp, s.onAccent)
    }
}
