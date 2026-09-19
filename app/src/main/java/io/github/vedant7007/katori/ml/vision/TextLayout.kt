package io.github.vedant7007.katori.ml.vision

/**
 * Turns recognised lines back into printed rows. Shared by every extractor over [RecognisedText].
 *
 * ML Kit returns one line per run of text on a shared baseline, and a printed table row is
 * usually two to four such lines because the column gaps break them. Lines whose vertical
 * centres fall within half a typical line height of each other are one row, read left to right.
 * ponytail: axis-aligned; a page skewed more than a few degrees drifts rows into each other.
 * Upgrade path is deskewing from ML Kit's corner points before grouping.
 */
internal object TextLayout {

    fun rows(lines: List<TextBlock>): List<List<TextBlock>> {
        if (lines.isEmpty()) return emptyList()
        val heights = lines.map { it.bottom - it.top }.sorted()
        val tolerance = heights[heights.size / 2].coerceAtLeast(1) * 0.5
        val out = mutableListOf<MutableList<TextBlock>>()
        var anchor = Double.NEGATIVE_INFINITY
        for (line in lines.sortedBy { it.centreY }) {
            if (line.centreY - anchor > tolerance) {
                out += mutableListOf(line)
                anchor = line.centreY
            } else {
                out.last() += line
            }
        }
        return out.map { row -> row.sortedBy { it.left } }
    }

    val TextBlock.centreY get() = (top + bottom) / 2.0
    val TextBlock.centreX get() = (left + right) / 2.0
}
