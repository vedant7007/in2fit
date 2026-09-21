package io.github.vedant7007.katori.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * The design's icons, as the design draws them: its SVG path data verbatim, in its own box (24
 * units for the tab glyphs, 12 for the tick and the pencil, 16 for the send arrow), scaled to
 * the size the design sets. Stroke widths scale with the box exactly as an SVG's do.
 */
class Stroke2(val d: String, val fill: Boolean = false, val width: Float = 1.7f)

class Glyph(val box: Float, vararg val strokes: Stroke2)

object Glyphs {
    val home = Glyph(24f, Stroke2("M4 10.2 12 4l8 6.2V20a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1v-9.8Z"))
    val diary = Glyph(
        24f,
        Stroke2("M6.5 4.5h11a2.5 2.5 0 0 1 2.5 2.5v10.5a2.5 2.5 0 0 1-2.5 2.5h-11a2.5 2.5 0 0 1-2.5-2.5V7a2.5 2.5 0 0 1 2.5-2.5Z"),
        Stroke2("M8 3v3M16 3v3M4 9.5h16"),
    )
    val mic = Glyph(
        24f,
        Stroke2("M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3Z", fill = true),
        Stroke2("M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3", width = 1.9f),
    )
    val coach = Glyph(24f, Stroke2("M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v7a2.5 2.5 0 0 1-2.5 2.5H10l-4.4 3.4A1 1 0 0 1 4 18.6V6.5Z"))
    val you = Glyph(
        24f,
        Stroke2("M12 4.8a3.7 3.7 0 1 1 0 7.4a3.7 3.7 0 1 1 0-7.4Z"),
        Stroke2("M4.8 20c.9-3.6 3.7-5.5 7.2-5.5s6.3 1.9 7.2 5.5"),
    )
    val bell = Glyph(24f, Stroke2("M6.5 10a5.5 5.5 0 0 1 11 0c0 4 1.5 5.5 1.5 5.5H5S6.5 14 6.5 10ZM10 19a2.2 2.2 0 0 0 4 0"))
    val tick = Glyph(12f, Stroke2("M2.5 6.2l2.4 2.4L9.5 4", width = 2f))
    val pencil = Glyph(12f, Stroke2("M8.2 1.8l2 2L4.6 9.4 2 10l.6-2.6 5.6-5.6Z", width = 1.3f))
    val send = Glyph(16f, Stroke2("M8 13V3m0 0L3.5 7.5M8 3l4.5 4.5", width = 1.8f))
    val chevron = Glyph(24f, Stroke2("M9 6l6 6-6 6"))
    val upload = Glyph(18f, Stroke2("M9 13V4m0 0L5.5 7.5M9 4l3.5 3.5M3.5 14.5h11", width = 1.6f))
    val arrowLeft = Glyph(14f, Stroke2("M11 7H3m0 0 3.5-3.5M3 7l3.5 3.5", width = 1.6f))
    val arrowRight = Glyph(14f, Stroke2("M3 7h8m0 0L7.5 3.5M11 7l-3.5 3.5", width = 1.6f))
    val chevronSmall = Glyph(14f, Stroke2("M5 2.5 9.5 7 5 11.5", width = 1.6f))
    val scan = Glyph(24f, Stroke2("M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16M4 12h16"))
    val back = Glyph(24f, Stroke2("M15 6l-6 6 6 6"))
}

@Composable
fun Icon2(glyph: Glyph, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val paths = remember(glyph) { glyph.strokes.map { it to PathParser().parsePathString(it.d).toPath() } }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / glyph.box
        scale(k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            paths.forEach { (s, p: Path) ->
                if (s.fill) drawPath(p, color, style = Fill)
                else drawPath(p, color, style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
