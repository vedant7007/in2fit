package io.github.vedant7007.katori.ml.vision

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One test on a lab report, as printed: the name, the value, the unit and the reference range
 * THAT REPORT carries. Pure data; the rules engine's `LabValue` is this plus a report date.
 *
 * [referenceLow] and [referenceHigh] are null whenever the range could not be read from the row,
 * or was printed more than once on it (a lipid panel lists three bands on one line). The app
 * carries no ranges of its own, so null here means no rule fires on this value. That is the
 * intended degradation: silence, never a comparison against an assumed range.
 *
 * [unit] is null when the row carried no recognisable unit. The value is still comparable to the
 * range on the same row, because both are printed in the same unit whether or not it was read.
 *
 * [sourceRow] is the row exactly as recognised, left to right, so the person can check the parse
 * against the print. It is the only string in here that may reach the screen verbatim.
 */
data class LabField(
    val testName: String,
    val value: Double,
    val unit: String?,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    val sourceRow: String,
)

data class LabReport(
    val fields: List<LabField>,
    /** The most plausible report date printed on the page, or null. The UI confirms it. */
    val reportDate: LocalDate?,
)

/**
 * Turns recognised lines into [LabField]s. Layout and regex only; no model, no lookup table of
 * tests, no reference ranges of its own (spec 12.1, `OcrEngine` contract).
 *
 * HOW A ROW IS FOUND. ML Kit returns one line per run of text on a shared baseline, and a printed
 * table row is usually two to four such lines because the column gaps break them. Lines whose
 * vertical centres fall within half a typical line height of each other are one row, read left
 * to right. That is the whole layout model, and it holds for a photograph taken square to the
 * page. ponytail: axis-aligned row grouping; a page skewed by more than a few degrees drifts rows
 * into each other. Upgrade path is deskewing from ML Kit's corner points before grouping.
 *
 * HOW A ROW IS READ. Dates are masked first (they are numbers too). Every reference-range shape
 * on the row is found next (`13.0 - 17.0`, `< 200`, `up to 5.6`) and masked, so a bound cannot
 * be mistaken for the value. The value is the first free-standing number followed by a known
 * unit; the range is then the ONE range printed after it. Two ranges after the value (a lipid
 * panel's bands, a two-column row) are ambiguous and read as none; a range-shaped thing before it
 * (`(25-OH)` in a name) is not a range. With no unit read, the row is kept only if it has exactly
 * one range, and the value is the last free-standing number before that range, so a number in
 * the name is never taken. "Free-standing" means not glued to a letter, so `B12` and `HbA1c`
 * contribute no digits; a unit glued to the number (`9.8g/dL`, which is what the hardware run
 * actually returned) is accepted; a comma decimal (`9,8`) is refused rather than read as 9. The
 * test name is whatever precedes the value.
 *
 * KNOWN CEILINGS, the first two measured in `LabReportExtractorTest`: a report that prints the
 * range before the result reads as no range; a two-column report yields the left test with no
 * range and loses the right one; a stray digit between a unit-less value and its range is taken
 * as the value. Each is silence or a visible number beside its source row, never an invented
 * range.
 *
 * WHAT IS DROPPED. A row with no number; a row whose number has neither a unit nor a range
 * (`Page 1 of 2`, `Age 34`); a row with a number but nothing before it (a column fragment). A
 * dropped row is silence, and silence is the correct output for a row this code cannot read.
 * What is NOT dropped is a value whose range is missing: it is returned with null bounds, so it
 * can be shown, stored and never ruled on.
 */
object LabReportExtractor {

    fun extract(text: RecognisedText): LabReport {
        val rows = rows(text.blocks).map { row -> row.joinToString(" ") { it.text.trim() } }
        val fields = rows.mapNotNull(::parseRow)
        return LabReport(fields, reportDate(rows))
    }

    // --- layout ----------------------------------------------------------------------------------

    private fun rows(lines: List<TextBlock>): List<List<TextBlock>> {
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

    private val TextBlock.centreY get() = (top + bottom) / 2.0

    // --- one row ---------------------------------------------------------------------------------

    /**
     * A number not glued to a letter, a time or a date; not the `25` of `25-OH`; and not a comma
     * decimal (`9,8` is refused whole rather than read as 9).
     */
    private const val NUM = """(?<![A-Za-z0-9.,/:])(?:\d{1,3}(?:,\d{2,3})+|\d+)(?:\.\d+)?(?![0-9]|-[A-Za-z]|,\d)"""

    private val RANGE = Regex(
        """(?:(?<lo>$NUM)\s*(?:-|to)\s*(?<hi>$NUM))""" +
            """|(?:(?:<|≤|<=|up\s*to|upto|less\s+than|below)\s*=?\s*(?<hiOnly>$NUM))""" +
            """|(?:(?:>|≥|>=|more\s+than|above)\s*=?\s*(?<loOnly>$NUM))""",
        RegexOption.IGNORE_CASE,
    )

    /** Longest first, so `pg/ml` wins over `pg` and `mg/dl` over `g/dl` is never a question. */
    private val UNITS = listOf(
        "million/cumm", "million/cmm", "million/ul", "cells/cumm", "cells/cmm", "cells/ul",
        "lakhs/cumm", "lakh/cumm", "thou/ul", "thousand/ul",
        "x10^3/ul", "x10^6/ul", "x10^9/l", "x10^12/l", "10^3/ul", "10^6/ul", "10^9/l", "10^12/l",
        "103/ul", "106/ul", "109/l", "1012/l",
        "mm/1sthr", "mm/1st hr", "mm/hr", "mm hg", "mmhg",
        "ml/min/1.73m2", "ml/min", "mmol/l", "umol/l", "nmol/l", "pmol/l", "miu/ml", "uiu/ml", "miu/l", "iu/ml", "iu/l", "u/l",
        "meq/l", "mcg/dl", "ug/dl", "ug/l", "ng/ml", "ng/dl", "ng/l", "pg/ml", "mg/dl", "mg/l",
        "mg/g", "gm/dl", "g/dl", "g/l", "gms%", "gm%", "mg%", "g%", "/cumm", "/cmm", "/ul", "fl", "pg",
        "seconds", "sec", "%",
    ).sortedByDescending { it.length }

    private val UNIT = UNITS.joinToString("|") { Regex.escape(it) }

    /** A value, an optional H/L flag, then a unit. The unit may be glued to the number. */
    private val VALUE_WITH_UNIT = Regex(
        """(?<value>$NUM)\s*(?:[HL*]\s+)?(?<unit>$UNIT)(?![A-Za-z/])""",
        RegexOption.IGNORE_CASE,
    )

    private val VALUE = Regex(NUM)
    private val BRACKETS = Regex("""\([^)]*\)|\[[^\]]*]""")

    private fun parseRow(raw: String): LabField? {
        val s = normalise(raw).let { blank(it, DATE.findAll(it)) }
        val ranges = RANGE.findAll(s).toList()
        val masked = blank(s, ranges.asSequence())

        val value: MatchGroup
        val unit: String?
        val range: MatchResult?
        val withUnit = VALUE_WITH_UNIT.find(masked)
        if (withUnit != null) {
            value = withUnit.groups["value"]!!
            unit = withUnit.groups["unit"]!!.value
            // Only a range printed AFTER the value counts. `(25-OH)` in a name is not a range, and
            // two ranges after the value (a lipid panel's bands, a two-column row) are ambiguous.
            range = ranges.filter { it.range.first > value.range.last }.singleOrNull()
        } else {
            // No unit read: the row is kept only if it carries a range, and the value is the last
            // free-standing number before that range, so a number inside the name is never taken.
            range = ranges.singleOrNull() ?: return null
            value = VALUE.findAll(blank(masked, BRACKETS.findAll(masked)))
                .lastOrNull { it.range.last < range.range.first }?.groups?.get(0) ?: return null
            unit = null
        }

        val name = s.substring(0, value.range.first).replace(SPACES, " ").trim().trim(':', '-', '.', '*', '•', ' ')
        if (name.isEmpty() || !name.any { it.isLetter() }) return null

        val lo = range?.let { it.groups["lo"] ?: it.groups["loOnly"] }?.value?.toNumber()
        val hi = range?.let { it.groups["hi"] ?: it.groups["hiOnly"] }?.value?.toNumber()
        return LabField(name, value.value.toNumber(), unit, lo, hi, raw)
    }

    private val SPACES = Regex("""\s+""")

    /** Micro signs and typographic dashes become the ASCII the patterns expect; lengths are kept. */
    private fun normalise(s: String) = s
        .replace('µ', 'u').replace('μ', 'u').replace('×', 'x')
        .replace('–', '-').replace('—', '-').replace('−', '-')

    private fun blank(s: String, spans: Sequence<MatchResult>): String {
        val chars = s.toCharArray()
        spans.forEach { m -> m.range.forEach { chars[it] = ' ' } }
        return String(chars)
    }

    private fun String.toNumber() = replace(",", "").toDouble()

    // --- the report date -------------------------------------------------------------------------

    private val DATE = Regex(
        """\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\b""" +
            """|\b(\d{1,2})[ -]([A-Za-z]{3,9})[ ,-]+(\d{4})\b""",
    )
    private val MONTH = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    private val MON = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /**
     * Prefers a date on a row that says "report"; otherwise the latest date on the page, which
     * is the reported-on date on any report that also prints collection and birth dates. Rows
     * naming a birth date are excluded outright. ponytail: day-first is assumed, as every Indian
     * lab prints; a month-first report would parse wrong or not at all. The UI confirms the date.
     */
    private fun reportDate(rows: List<String>): LocalDate? {
        val dated = rows.filterNot { it.contains("birth", true) || it.contains("dob", true) }
            .flatMap { row -> DATE.findAll(row).mapNotNull { toDate(it) }.map { row to it } }
        return dated.firstOrNull { it.first.contains("report", true) }?.second
            ?: dated.maxOfOrNull { it.second }
    }

    private fun toDate(m: MatchResult): LocalDate? = runCatching {
        val g = m.groupValues
        if (g[1].isNotEmpty()) LocalDate.of(g[3].toInt(), g[2].toInt(), g[1].toInt())
        else {
            val text = "${g[4].toInt()} ${g[5].lowercase().replaceFirstChar { it.uppercase() }} ${g[6]}"
            runCatching { LocalDate.parse(text, MON) }.getOrElse { LocalDate.parse(text, MONTH) }
        }
    }.getOrNull()
}
