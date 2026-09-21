package io.github.vedant7007.katori.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.vedant7007.katori.R

/**
 * The v2 faces, as the design sets them, bundled as assets (a downloadable font is a network
 * call): Instrument Sans (one variable file, wght 400–700) for text, Instrument Serif for the
 * display lines, and IBM Plex Sans Devanagari for EVERY DIGIT (ruled 21 Sep: Instrument's digits
 * are proportional, measured 391–666 units; Plex's are tabular at 600, and a number that jitters
 * between frames looks broken on a projector). A Plex number sits at the same optical height as
 * a serif label at 1.02× its size (digit heights 722 against 739 units, measured).
 *
 * Devanagari and Telugu are not in either Instrument face and fall to the system's Noto beside
 * it; a line that is mostly Devanagari (the demo's Hindi transcript) therefore renders in Noto
 * Sans Devanagari, which is the honest face for it.
 */
val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.instrument_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.instrument_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.instrument_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

private val platform = PlatformTextStyle(includeFontPadding = false)
private val lineStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** Instrument Sans at the design's exact size and weight; line height as the design's ratio. */
fun sans(size: Float, weight: FontWeight = FontWeight.Normal, lineHeight: Float = 1.45f, color: Color = Color.Unspecified): TextStyle =
    TextStyle(fontFamily = InstrumentSans, fontSize = size.sp, fontWeight = weight, lineHeight = (size * lineHeight).sp, color = color, platformStyle = platform, lineHeightStyle = lineStyle)

/** Instrument Serif at the design's exact size, for the display lines (never a digit). */
fun serif(size: Float, lineHeight: Float = 1.2f, color: Color = Color.Unspecified, italic: Boolean = false): TextStyle =
    TextStyle(fontFamily = InstrumentSerif, fontSize = size.sp, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, lineHeight = (size * lineHeight).sp, color = color, platformStyle = platform, lineHeightStyle = lineStyle)

/** A number, in Plex, at the optical size of the serif it stands beside (× 1.02) or of the sans (× 1.0). */
fun num(size: Float, weight: FontWeight = FontWeight.SemiBold, besideSerif: Boolean = false, color: Color = Color.Unspecified, lineHeight: Float = 1.2f): TextStyle {
    val s = if (besideSerif) size * 1.02f else size
    return TextStyle(fontFamily = Plex, fontSize = s.sp, fontWeight = weight, lineHeight = (s * lineHeight).sp, color = color, platformStyle = platform, lineHeightStyle = lineStyle)
}

/** The design's tracked uppercase label: 10.5–12 sp, 600–700, letter-spacing .1–.2 em. */
fun micro(size: Float = 11f, tracking: TextUnit = 0.12.em, weight: FontWeight = FontWeight.SemiBold, color: Color = Color.Unspecified): TextStyle =
    TextStyle(fontFamily = InstrumentSans, fontSize = size.sp, fontWeight = weight, letterSpacing = tracking, color = color, lineHeight = (size * 1.3f).sp, platformStyle = platform, lineHeightStyle = lineStyle)

@Composable
fun String.upper(): String = uppercase()
