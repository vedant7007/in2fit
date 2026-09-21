package io.github.vedant7007.katori.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.theme.In2fitColors
import io.github.vedant7007.katori.ui.theme.LocalReduceMotion
import io.github.vedant7007.katori.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The splash, as decided on 20 September (Arjun 20:09, Vedant's read of the frames): the wordmark
 * breathes open, `scaleX` 0.72 → 1.0 ease-out over 1.2 s, the tagline fades in as a separate
 * element with alpha only, its position fixed; from 3.5 s it contracts and fades, and loops. The
 * three rules that outrank fidelity: it never gates the app (the screen under it is already
 * built), it ends at the next expand the moment [ready] is true, and it has no minimum duration.
 *
 * Every animated property is a `graphicsLayer` transform, so nothing here lays out or recomposes
 * per frame. The wordmark is the PNG scaled (ruled: no trace; on a splash lossy is invisible).
 * [ready] is read at the end of each expand; today the caller passes true and the splash ends
 * after one breath, because the warm-up signal (0028) does not reach the screen yet.
 */
@Composable
fun Splash(ready: () -> Boolean, onFinished: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val scheme = io.github.vedant7007.katori.ui.theme.LocalScheme.current
    val scaleX = remember { Animatable(if (reduceMotion) 1f else 0.72f) }
    val tagline = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val veil = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        // Reduce motion: the end state, no breathing, gone as soon as the app is ready.
        while (!reduceMotion) {
            launch { tagline.animateTo(1f, tween(900, delayMillis = 200, easing = LinearOutSlowInEasing)) }
            scaleX.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
            if (ready()) break
            delay(3500L - 1200L)
            launch { tagline.animateTo(0f, tween(600)) }
            scaleX.animateTo(0.72f, tween(600, easing = FastOutSlowInEasing))
        }
        if (reduceMotion) { while (!ready()) delay(100) }
        veil.animateTo(0f, tween(if (reduceMotion) 0 else 250))
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = veil.value }.background(scheme.ground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.l)) {
            // One-colour PNGs, tinted to the scheme's mark (lime on the dark ground, the green on cream).
            Image(
                painterResource(R.drawable.wordmark),
                contentDescription = stringResource(R.string.app_name),
                colorFilter = ColorFilter.tint(scheme.mark),
                modifier = Modifier.width(260.dp).graphicsLayer { this.scaleX = scaleX.value },
            )
            Image(
                painterResource(R.drawable.tagline),
                contentDescription = null,
                colorFilter = ColorFilter.tint(scheme.text2),
                modifier = Modifier.width(220.dp).padding(top = Space.s).graphicsLayer { alpha = tagline.value },
            )
        }
    }
}
