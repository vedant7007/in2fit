package io.github.vedant7007.katori.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vedant7007.katori.R

/**
 * The design tokens. Everything on a screen is one of these values; `docs/design/design-system.md`
 * says what each is for and when not to use it. Light only for the battle (ruled 20 Sep).
 *
 * Contrast ratios are WCAG 2, computed in `docs/design/ui-research.md` §0 and §8.1. The accent
 * `#E5522D` is 3.15:1 on the ground, so it never carries text under 24 sp and never fills a
 * button with a small label; `accentInk` is the deeper orange that passes for one small word.
 */
object In2fitColors {
    /** The screen. */
    val ground = Color(0xFFECEBE6)
    /** Every word and every figure: 11.63:1 on the ground. */
    val ink = Color(0xFF252F26)
    /** Labels, units, dates, the source line: 5.30:1 on the ground. */
    val inkSecondary = Color(0xFF55635A)
    /** A card that is a thing (a plate, the figures, a report field); lighter than the ground, no shadow. */
    val raised = Color(0xFFF6F5F1)
    /** The person's own words: the one dark block on the screen. */
    val person = ink
    val onPerson = ground
    /** The only separator the app uses. */
    val hairline = Color(0xFFD1D0C8)
    /** A selected chip or row. */
    val selected = Color(0xFFD3DAD3)
    /** Marks only: the level fill, a range dot, the running counter at 28 sp. Never small text. */
    val accent = Color(0xFFE5522D)
    /** The accent as one small word ("below", a referral label): 4.73:1 on the ground. */
    val accentInk = Color(0xFFB83D1B)
    /** The scripted-feed banner and nothing else. */
    val error = Color(0xFFB3261E)
}

/** 4 / 8 / 12 / 16 / 24 / 32. Nothing on a screen is 2, 6 or 10. */
object Space {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}

/**
 * IBM Plex Sans Devanagari carries Plex's Latin (same x-height, 516/520/522 units) and its
 * Devanagari in one file, and its digits are tabular by default (every digit 600 units, measured
 * with fontTools on 20 Sep), so no `tnum` feature is needed. Telugu falls back to the system's
 * Noto Sans Telugu. Three static weights, an asset each, no network (0004).
 */
val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
)

private val base = TextStyle(
    fontFamily = Plex,
    color = In2fitColors.ink,
    // Tall scripts: no font padding, centred line height, so Devanagari marks get the room the
    // line height gives them rather than the font's own padding (research §4).
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Five text sizes and one number: 12 / 14 / 16 / 20 / 28, and 36 for a hero figure. */
object In2fitText {
    /** One per card at most: the energy on a plate, the nutrient that was asked about. */
    val hero = base.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
    /** A screen's title; the intent heading over a turn. */
    val title = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
    /** The person's words, in their script; 1.5 line height for Devanagari. */
    val transcript = base.copy(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal)
    /** A figure's value in a row. */
    val figure = base.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    /** Health text, answers, the trigger sentence. */
    val body = base.copy(fontSize = 16.sp, lineHeight = 24.sp)
    /** Items, candidates, secondary sentences. */
    val bodySmall = base.copy(fontSize = 14.sp, lineHeight = 20.sp)
    /** A label over a block, a unit, a date, the safety line. */
    val label = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = In2fitColors.inkSecondary)
    /** A button's word. */
    val button = base.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
}

/**
 * The Material roles, so a screen written against `MaterialTheme.typography` (the pre-flight,
 * anything of Arjun's not yet restyled) renders in the same face and sizes with no edit.
 */
private val typography = Typography(
    displayLarge = In2fitText.hero, displayMedium = In2fitText.hero, displaySmall = In2fitText.hero,
    headlineLarge = In2fitText.title, headlineMedium = In2fitText.title, headlineSmall = In2fitText.title,
    titleLarge = In2fitText.transcript.copy(fontWeight = FontWeight.Medium),
    titleMedium = In2fitText.body.copy(fontWeight = FontWeight.Medium),
    titleSmall = In2fitText.bodySmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = In2fitText.body, bodyMedium = In2fitText.bodySmall, bodySmall = In2fitText.label.copy(fontWeight = FontWeight.Normal),
    labelLarge = In2fitText.bodySmall.copy(fontWeight = FontWeight.Medium),
    labelMedium = In2fitText.label, labelSmall = In2fitText.label,
)

private val colorScheme = lightColorScheme(
    primary = In2fitColors.ink, onPrimary = In2fitColors.ground,
    primaryContainer = In2fitColors.selected, onPrimaryContainer = In2fitColors.ink,
    secondary = In2fitColors.inkSecondary, onSecondary = In2fitColors.ground,
    secondaryContainer = In2fitColors.selected, onSecondaryContainer = In2fitColors.ink,
    tertiary = In2fitColors.accentInk, onTertiary = In2fitColors.ground,
    background = In2fitColors.ground, onBackground = In2fitColors.ink,
    surface = In2fitColors.ground, onSurface = In2fitColors.ink,
    surfaceVariant = In2fitColors.raised, onSurfaceVariant = In2fitColors.inkSecondary,
    surfaceContainer = In2fitColors.raised, surfaceContainerLow = In2fitColors.ground,
    surfaceContainerHigh = In2fitColors.raised, surfaceContainerHighest = In2fitColors.raised,
    outline = In2fitColors.inkSecondary, outlineVariant = In2fitColors.hairline,
    error = In2fitColors.error, onError = Color.White,
)

/** 8 dp on a small control, 16 dp on a card; the mic is a pill of its own. */
private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun In2fitTheme(content: @Composable () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes, content = content)
    }
}
