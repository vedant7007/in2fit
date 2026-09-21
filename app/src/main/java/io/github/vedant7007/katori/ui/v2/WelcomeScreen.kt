package io.github.vedant7007.katori.ui.v2

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.theme.LocalReduceMotion
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif
import kotlinx.coroutines.delay

/**
 * Welcome as drawn (amendment 1: pixel for pixel, including the sign-in link): the two drifting
 * glows, the lockup at 198 dp, the 208 dp ring with its sweep, its pulse and the thirteen bars,
 * "YOU SAY" over the cycling sentence, the tagline sentence, "Get started" into first run, and
 * "I already have an account", which shows ONE honest sentence (a prototype, no account is
 * created, nothing leaves the phone) and then goes straight in.
 *
 * NOT AS DRAWN, and why: the pill under the sentence ("412 kcal · 18 g protein") states figures
 * the system has not produced for those sentences (Arjun's (ad): computed through the real
 * resolver at first launch, 0029) and is absent until they exist; the sentences themselves are
 * the design's words and carry no number. Every animation here is decorative and stops under
 * reduce motion; none runs during inference because nothing infers on this page.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val s = scheme()
    val reduce = LocalReduceMotion.current
    var notice by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(s.ground).clip(RoundedCornerShape(0.dp))) {
        Glows(reduce)
        Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.lockup),
                contentDescription = stringResource(R.string.app_name),
                colorFilter = if (s.isDark) null else ColorFilter.tint(s.mark),
                modifier = Modifier.padding(top = 18.dp).width(198.dp),
            )
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Ring(reduce)
                Column(Modifier.heightIn(min = 128.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(stringResource(R.string.v2_you_say).uppercase(), micro(10.5f, 0.2.em, FontWeight.Bold), color = s.text4, modifier = Modifier.padding(bottom = 14.dp))
                    Phrase(reduce)
                }
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 26.dp, end = 26.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T(stringResource(R.string.v2_welcome_line), sans(14.5f, FontWeight.Normal, 1.6f), color = Color(0xFF98A49E).takeIf { s.isDark } ?: s.text2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp).padding(bottom = 22.dp))
                Pill2(stringResource(R.string.v2_get_started), onClick = onGetStarted, modifier = Modifier.fillMaxWidth(), size = 15f, vertical = 17.dp)
                if (notice) {
                    // The honest sentence, in the link's place, then straight in.
                    T(stringResource(R.string.v2_sign_in_notice), sans(14f, FontWeight.Normal, 1.5f), color = s.text2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
                    Pill2(stringResource(R.string.v2_continue), onClick = onSignIn, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), filled = false, size = 15f, vertical = 17.dp)
                } else {
                    T(stringResource(R.string.v2_have_account), sans(14f, FontWeight.Normal, 1.2f), color = s.text2, modifier = Modifier.clickable { notice = true }.padding(top = 18.dp))
                }
            }
        }
    }
}

/** The two radial glows, 400 and 420 dp, drifting over 16 and 21 s as the design's do. */
@Composable
private fun Glows(reduce: Boolean) {
    val s = scheme()
    val t = rememberInfiniteTransition()
    val a by if (reduce) remember { mutableStateOf(0f) } else t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse))
    val b by if (reduce) remember { mutableStateOf(0f) } else t.animateFloat(0f, 1f, infiniteRepeatable(tween(21000, easing = LinearEasing), RepeatMode.Reverse))
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.size(400.dp).graphicsLayer {
                translationX = (-90 - 24 + 56 * a).dp.toPx(); translationY = (-120 - 16 + 40 * a).dp.toPx()
                val k = 1f + 0.15f * a; scaleX = k; scaleY = k
            }.background(Brush.radialGradient(listOf(s.accent.copy(alpha = 0.20f), s.accent.copy(alpha = 0f)), radius = 200.dp.value * 3f), CircleShape),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).size(420.dp).graphicsLayer {
                translationX = (110 + 25 - 59 * b).dp.toPx(); translationY = (150 + 17 - 42 * b).dp.toPx()
                val k = 1.1f - 0.15f * b; scaleX = k; scaleY = k
            }.background(Brush.radialGradient(listOf(s.teal.copy(alpha = 0.18f), s.teal.copy(alpha = 0f)), radius = 210.dp.value * 3f), CircleShape),
        )
    }
}

/** The 208 dp ring: the conic sweep turning every 5.5 s, the hairline, the 3.2 s pulse, the bars. */
@Composable
private fun Ring(reduce: Boolean) {
    val s = scheme()
    val t = rememberInfiniteTransition()
    val turn by if (reduce) remember { mutableStateOf(0f) } else t.animateFloat(0f, 360f, infiniteRepeatable(tween(5500, easing = LinearEasing)))
    val pulse by if (reduce) remember { mutableStateOf(0f) } else t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)))
    val bar by if (reduce) remember { mutableStateOf(0.5f) } else t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)))
    Box(Modifier.padding(bottom = 30.dp).size(208.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = turn }) {
            // rgba(200,241,105,0) at 0°, .55 at 90°, 0 at 200°, as a sweep behind a 93 dp hole: a 12 dp band.
            drawArc(
                Brush.sweepGradient(0f to s.accent.copy(alpha = 0f), 0.25f to s.accent.copy(alpha = 0.55f), 0.556f to s.accent.copy(alpha = 0f), 1f to s.accent.copy(alpha = 0f)),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(5.5.dp.toPx(), 5.5.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width - 11.dp.toPx(), size.height - 11.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(11.dp.toPx()),
            )
        }
        Box(Modifier.fillMaxSize().border(1.dp, s.text.copy(alpha = 0.08f), CircleShape))
        val k = if (pulse < 0.5f) pulse * 2f else (1f - pulse) * 2f
        Box(Modifier.padding(24.dp).fillMaxSize().graphicsLayer { scaleX = 1f + 0.35f * k; scaleY = 1f + 0.35f * k; alpha = 0.5f * (1f - k) }.background(s.accent.copy(alpha = 0.06f), CircleShape))
        Row(Modifier.height(62.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            repeat(13) { i ->
                // Each bar's phase is offset by 0.08 s as the design's delays are.
                val phase = ((bar - i * 0.08f / 1.2f) % 1f + 1f) % 1f
                val y = 0.25f + 0.75f * (if (phase < 0.5f) phase * 2f else (1f - phase) * 2f)
                Box(Modifier.size(4.dp, 54.dp).graphicsLayer { scaleY = y; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }.background(s.accent, RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** The design's four sentences, one every 4.2 s, in serif 25 at 1.32 with the design's rise and fade. */
@Composable
private fun Phrase(reduce: Boolean) {
    val s = scheme()
    val phrases = listOf(R.string.v2_phrase_1, R.string.v2_phrase_2, R.string.v2_phrase_3, R.string.v2_phrase_4)
    var i by remember { mutableIntStateOf(0) }
    val t = rememberInfiniteTransition()
    val cycle by if (reduce) remember { mutableStateOf(0.5f) } else t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)))
    LaunchedEffect(reduce) { if (!reduce) while (true) { delay(4200); i = (i + 1) % phrases.size } }
    val alpha = when { cycle < 0.12f -> cycle / 0.12f; cycle > 0.84f -> (1f - cycle) / 0.16f; else -> 1f }
    val rise = when { cycle < 0.12f -> 8f * (1f - cycle / 0.12f); cycle > 0.84f -> -8f * ((cycle - 0.84f) / 0.16f); else -> 0f }
    Box(Modifier.heightIn(min = 66.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        T(
            "“" + stringResource(phrases[i]) + "”", serif(25f, 1.32f), color = s.text, textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { this.alpha = alpha; translationY = rise.dp.toPx() },
        )
    }
    // The kcal · protein pill: absent until the figures come from the real resolver (Arjun, (ad)).
    Spacer(Modifier.height(8.dp))
}
