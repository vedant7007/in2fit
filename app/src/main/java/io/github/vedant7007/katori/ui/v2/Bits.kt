package io.github.vedant7007.katori.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vedant7007.katori.ui.theme.InstrumentSerif
import io.github.vedant7007.katori.ui.theme.Plex
import io.github.vedant7007.katori.ui.theme.Scheme
import io.github.vedant7007.katori.ui.theme.scheme

/**
 * EVERY DIGIT IN PLEX (ruled 21 Sep): a run of digits inside any v2 line is set in IBM Plex Sans,
 * tabular, at the line's size, or at 1.02× it when the line is serif (measured: Plex's digit is
 * 722 units to Instrument Serif's 739). The rest of the line keeps its face. Used by [T] for
 * every v2 text, so no screen can forget it.
 */
fun withPlexDigits(text: String, style: TextStyle): AnnotatedString {
    val size = if (style.fontFamily == InstrumentSerif) style.fontSize * 1.02f else style.fontSize
    val span = SpanStyle(fontFamily = Plex, fontSize = size)
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val digit = text[i].isDigit()
            var j = i
            while (j < text.length && text[j].isDigit() == digit) j++
            if (digit) withStyle(span) { append(text, i, j) } else append(text, i, j)
            i = j
        }
    }
}

/** A v2 line: the design's style, the digits in Plex. */
@Composable
fun T(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
) {
    Text(withPlexDigits(text, style), style = style, color = color, modifier = modifier, maxLines = maxLines, textAlign = textAlign, overflow = overflow, softWrap = softWrap)
}

/** The design's panel: card ground, a 1 dp hairline, the radius it sets (26, 22, 20, 18). */
@Composable
fun Card2(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    bg: Color = scheme().card,
    line: Color = scheme().line,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/** The design's pill button: filled (accent on ground) or outlined (a hairline, the button text colour). */
@Composable
fun Pill2(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, enabled: Boolean = true, size: Float = 14.5f, vertical: Dp = 16.dp, horizontal: Dp = 20.dp) {
    val s: Scheme = scheme()
    val shape = RoundedCornerShape(100.dp)
    Box(
        modifier
            .clip(shape)
            .then(if (filled) Modifier.background(s.accent, shape) else Modifier.border(1.dp, s.text.copy(alpha = 0.12f), shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = vertical, horizontal = horizontal),
        contentAlignment = Alignment.Center,
    ) {
        T(text, io.github.vedant7007.katori.ui.theme.sans(size, androidx.compose.ui.text.font.FontWeight.SemiBold, 1.2f), color = if (filled) s.onAccent else s.button, maxLines = 1)
    }
}
