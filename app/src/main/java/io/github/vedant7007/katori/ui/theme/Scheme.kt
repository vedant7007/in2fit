package io.github.vedant7007.katori.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The v2 palette (ruled 21 Sep: the app looks exactly like the design; dark is the default,
 * cream lives behind the switch). Every colour on a v2 screen is a field of the current [Scheme],
 * read through [LocalScheme]; the design's hex values are kept verbatim in [Dark], and [Cream]
 * maps the same roles onto the brand tokens the light theme was ruled on (`#252F26` on `#ECEBE6`).
 *
 * Contrast, computed and not improved (the ruling is to match the design; the numbers are here
 * so nobody is surprised on a projector): lime on ground 14.4:1; `text2` #8E9A95 on ground
 * 5.6:1; `text3` #7E8C86 on card 4.4:1; `text4` #6B7873 on ground 3.6:1 and it is used at 10.5–12 sp
 * for tracked uppercase labels, below AA for small text as drawn.
 */
data class Scheme(
    val isDark: Boolean,
    /** The phone's background. */
    val ground: Color,
    /** A raised panel. */
    val card: Color,
    /** A second panel tone (inputs, the ask-card buttons). */
    val card2: Color,
    /** The hairline around a panel. */
    val line: Color,
    /** A softer hairline inside a panel. */
    val lineSoft: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val text4: Color,
    /** The accent: buttons, marks, the ring. */
    val accent: Color,
    val accentHover: Color,
    val onAccent: Color,
    /** The accent at low alpha, for tinted pills and selected rows. */
    val accentTint: Color,
    val accentLine: Color,
    val teal: Color,
    val warm: Color,
    val amber: Color,
    /** The sheet's ground and its scrim. */
    val sheet: Color,
    val scrim: Color,
    /** The assistant's message panel. */
    val message: Color,
    /** A gradient panel's start (the nudge card); ends on [card]. */
    val glowStart: Color,
    /** The wordmark colour for this scheme. */
    val mark: Color,
    /** The quoted transcript while the turn is analysed (#A6B2AC as drawn). */
    val quote: Color,
    /** The outline button's and chip's text (#C6D0CB). */
    val button: Color,
    /** Text inside the person's own bubble (#E6F7C4) and the assistant's (#DDE2DE). */
    val onOwn: Color,
    val onMessage: Color,
    /** The dimmest mark (#55615C): an unlogged day, a hint inside a row. */
    val dim: Color,
)

val Dark = Scheme(
    isDark = true,
    ground = Color(0xFF0E1312),
    card = Color(0xFF161D1B),
    card2 = Color(0xFF171E1C),
    line = Color(0x12FFFFFF),
    lineSoft = Color(0x0FFFFFFF),
    text = Color(0xFFF3F1EC),
    text2 = Color(0xFF8E9A95),
    text3 = Color(0xFF7E8C86),
    text4 = Color(0xFF6B7873),
    accent = Color(0xFFC8F169),
    accentHover = Color(0xFFDCFF8C),
    onAccent = Color(0xFF0E1312),
    accentTint = Color(0x24C8F169),
    accentLine = Color(0x38C8F169),
    teal = Color(0xFF6FB79A),
    warm = Color(0xFFE58C5A),
    amber = Color(0xFFE5C45A),
    sheet = Color(0xFF101615),
    scrim = Color(0xC7060A09),
    message = Color(0xFF1A211F),
    glowStart = Color(0xFF1D2A20),
    mark = Color(0xFFC8F169),
    quote = Color(0xFFA6B2AC),
    button = Color(0xFFC6D0CB),
    onOwn = Color(0xFFE6F7C4),
    onMessage = Color(0xFFDDE2DE),
    dim = Color(0xFF55615C),
)

/** The brand scheme behind the switch: the same roles, the ruled cream and green. */
val Cream = Scheme(
    isDark = false,
    ground = Color(0xFFECEBE6),
    card = Color(0xFFF6F5F1),
    card2 = Color(0xFFF6F5F1),
    line = Color(0xFFD1D0C8),
    lineSoft = Color(0xFFDEDDD6),
    text = Color(0xFF252F26),
    text2 = Color(0xFF55635A),
    text3 = Color(0xFF55635A),
    text4 = Color(0xFF6B7873),
    accent = Color(0xFF252F26),
    accentHover = Color(0xFF3A473D),
    onAccent = Color(0xFFECEBE6),
    accentTint = Color(0xFFD3DAD3),
    accentLine = Color(0xFFB9C3BB),
    teal = Color(0xFF3F7D66),
    warm = Color(0xFFB83D1B),
    amber = Color(0xFF8A6A12),
    sheet = Color(0xFFF6F5F1),
    scrim = Color(0x99252F26),
    message = Color(0xFFE2E4DE),
    glowStart = Color(0xFFDCE3DD),
    mark = Color(0xFF252F26),
    quote = Color(0xFF55635A),
    button = Color(0xFF3A473D),
    onOwn = Color(0xFF252F26),
    onMessage = Color(0xFF252F26),
    dim = Color(0xFF8E9A95),
)

val LocalScheme = compositionLocalOf { Dark }

/** Material's scheme for the few Material components v2 keeps (text fields, checkboxes). */
fun Scheme.material(): ColorScheme = if (isDark) darkColorScheme(
    primary = accent, onPrimary = onAccent, background = ground, onBackground = text,
    surface = ground, onSurface = text, surfaceVariant = card, onSurfaceVariant = text2,
    outline = text3, outlineVariant = line, error = warm, onError = onAccent,
) else lightColorScheme(
    primary = accent, onPrimary = onAccent, background = ground, onBackground = text,
    surface = ground, onSurface = text, surfaceVariant = card, onSurfaceVariant = text2,
    outline = text3, outlineVariant = line, error = warm, onError = onAccent,
)

/**
 * The person's choice of scheme, kept on the device (no account, no server): one boolean in
 * SharedPreferences, read once at start and written by the switch on the You screen.
 */
object ThemePreference {
    private const val FILE = "in2fit.ui"
    private const val KEY = "dark"
    private const val KEY_LEGACY = "legacy"
    var dark by mutableStateOf(true)
        private set
    /** The old three-tab shell, kept reachable until every v2 screen is proven on the device. */
    var legacy by mutableStateOf(false)
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        dark = p.getBoolean(KEY, true)
        legacy = p.getBoolean(KEY_LEGACY, false)
    }

    fun set(context: Context, value: Boolean) {
        dark = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    fun setLegacy(context: Context, value: Boolean) {
        legacy = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_LEGACY, value).apply()
    }
}

@Composable
fun scheme(): Scheme = LocalScheme.current
