package io.github.vedant7007.katori.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * The motion tokens, and the one rule that outranks them: the figures-first ruling. A number
 * appears at once, never behind a transition; a card that carries only words may arrive.
 *
 * Springs, from Material 3's Standard scheme (research §1.2): spatial 700 / 0.9 for anything that
 * moves, effects 1600 / 1.0 for anything that fades. Nothing here runs during inference by
 * design: the stage list and the counter change with no animation, and the only draw during a
 * turn is the level bar while recording and the counter once a second.
 */
object Motion {
    val spatial: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 700f)
    val effects: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)
}

/**
 * True when the system's animator duration scale is 0 (Settings → Accessibility → Remove
 * animations, or Developer options). Every animation in the app checks it; the splash goes
 * straight to its end state.
 */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * A block of words arriving: alpha 0 → 1 and 8 dp of rise, on the effects spring, once per item,
 * in `graphicsLayer` so nothing lays out or recomposes per frame. Interruptible (a spring), never
 * blocks input, and remembered as arrived so a scroll back does not replay it. Not for a card
 * that carries a figure: those appear at once (the plate, the diary figures).
 */
@Composable
fun Modifier.arrive(): Modifier {
    val reduce = LocalReduceMotion.current
    var arrived by rememberSaveable { mutableStateOf(reduce) }
    val t = remember { Animatable(if (arrived) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!arrived) {
            t.animateTo(1f, Motion.effects)
            arrived = true
        }
    }
    // t is read only inside the draw lambda: the frames of the entrance never recompose anything.
    return graphicsLayer {
        alpha = t.value
        translationY = (1f - t.value) * 8.dp.toPx()
    }
}
