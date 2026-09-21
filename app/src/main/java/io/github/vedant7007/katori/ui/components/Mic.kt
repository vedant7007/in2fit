package io.github.vedant7007.katori.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import io.github.vedant7007.katori.ui.theme.LocalReduceMotion
import io.github.vedant7007.katori.ui.theme.Motion
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import io.github.vedant7007.katori.ui.theme.In2fitColors
import io.github.vedant7007.katori.ui.theme.In2fitText

/**
 * The microphone, and the meter. A 72 dp pill, full width, at the bottom where the thumb is
 * (research §2.1, WhatsApp's gesture). Held, it starts the turn; released, it ends the
 * recording ([onRelease] is a no-op until `EndSpeech` is in the contract). While [active] the
 * pill's bottom edge is the level: an accent bar that grows with the voice, read from [level] in
 * the draw phase so a 30 Hz RMS never recomposes anything, and frozen at the endpoint because
 * the level stops changing (0026 step 2). The accent never sits under the label, so the label
 * stays cream on ink at 11.6:1 whatever the level.
 *
 * [stop] non-null means the answer is being spoken: the pill is a plain tap that stops speech
 * only (0026 step 8), and the hold gesture is off.
 */
@Composable
fun MicButton(
    label: String,
    enabled: Boolean,
    active: Boolean,
    level: () -> Float,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    stop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    // The press: 1 → 0.97 on the spatial spring, read in the draw lambda only; off under reduce motion.
    val press = remember { Animatable(1f) }
    val fill = when {
        stop != null -> In2fitColors.ink
        enabled || active -> In2fitColors.ink
        else -> In2fitColors.hairline
    }
    val ink = if (enabled || active || stop != null) In2fitColors.onPerson else In2fitColors.inkSecondary
    // Read through a state holder, never as a key: the press itself flips `enabled` (the turn
    // goes busy), and a pointerInput keyed on it restarts mid-gesture, cancelling the wait for
    // the release. That ran every hold to the 30 s cap on the realme (21 Sep 01:24).
    val isEnabled by rememberUpdatedState(enabled)
    val gesture = if (stop != null) {
        Modifier.clickable(onClick = stop)
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                if (isEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!reduceMotion) scope.launch { press.animateTo(0.97f, Motion.spatial) }
                    onPress()
                    tryAwaitRelease()
                    if (!reduceMotion) scope.launch { press.animateTo(1f, Motion.spatial) }
                    onRelease()
                }
            })
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer { scaleX = press.value; scaleY = press.value }
            .clip(CircleShape)
            .drawBehind {
                drawRect(fill)
                if (active) {
                    val bar = 6.dp.toPx()
                    val w = size.width * level().coerceIn(0f, 1f)
                    drawRect(In2fitColors.accent, topLeft = Offset(0f, size.height - bar), size = Size(w, bar))
                }
            }
            .then(gesture),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = In2fitText.button, color = ink)
    }
}
