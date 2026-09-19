package io.github.vedant7007.katori.ml.vision

import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientUnit
import io.github.vedant7007.katori.domain.model.NutrientValue
import io.github.vedant7007.katori.ml.vision.TextLayout.centreX

/** The ONE basis every value in a [NutritionLabel] is stated in. */
sealed interface LabelBasis {
    /** Per 100 g or per 100 ml, as printed. The food database's own basis. */
    data class Per100(val unit: String) : LabelBasis

    /** Per serving, with the serving's amount and unit when the panel prints them. */
    data class PerServing(val amount: Double?, val unit: String?) : LabelBasis
}

/** One printed row of the panel, in the label's basis, name and unit as printed. */
data class LabelRow(val name: String, val value: Double, val unit: String?, val sourceRow: String)

/**
 * What a packaged-food panel says (spec 12.2). [rows] is every nutrient row that could be read,
 * in [basis]. [nutrients] is the subset the domain has a name for, converted to its units by
 * arithmetic (mg to g, never kJ to kcal). [ingredients] is the ingredient list as printed, for
 * the rules that flag sugars, palm oil and sodium, which live in `domain/`, not here.
 */
data class NutritionLabel(
    val basis: LabelBasis?,
    val rows: List<LabelRow>,
    val nutrients: Map<Nutrient, NutrientValue.Measured>,
    val ingredients: String?,
) {
    /**
     * The domain nutrients per 100 g/ml: as read for a per-100 panel; scaled by arithmetic for
     * a per-serving panel whose serving amount is printed; empty for anything else. Empty is
     * "not known", never zero.
     */
    fun nutrientsPer100(): Map<Nutrient, NutrientValue.Measured> = when (basis) {
        is LabelBasis.Per100 -> nutrients
        is LabelBasis.PerServing -> basis.amount?.takeIf { it > 0 }?.let { per ->
            nutrients.mapValues { (_, v) -> NutrientValue.Measured(v.amount * 100.0 / per, v.unit) }
        } ?: emptyMap()
        null -> emptyMap()
    }
}

/**
 * Turns recognised lines from a nutrition panel into [NutritionLabel]. Layout and regex only.
 *
 * THE COLUMN RULE, decided before the parser was written. A panel states values per 100 g AND
 * per serving, usually side by side, and taking the wrong column is a silent factor-of-two that
 * looks entirely reasonable. So a value is reported only under a basis PROVEN by a header token
 * on the panel ("Per 100 g", "Per serve (30 g)", "%RDA"), and per-100 wins when both are
 * printed. Cells are assigned to header columns by x-position when every header and every cell
 * has its own box, and by order-and-count when they do not; a row whose cells cannot be assigned
 * is dropped. A panel with no header row yields NO nutrient values, unless every nutrient row
 * carries the same basis itself ("Energy 520 kcal per 100 g"), which proves it just as well.
 * Silence, never a guess.
 *
 * THE NAME RULE, from `0022`: a printed row name matches the lexicon EXACTLY, so "saturated fat"
 * never becomes FAT and "of which sugars" never becomes CARBOHYDRATE. Rows the lexicon does not
 * name are dropped; nothing on a pack is a nutrient because it has a number next to it.
 *
 * WHAT ELSE IS NOT DONE. kJ is never converted to kcal; a panel printing only kJ has no energy.
 * "<0.5 g" is not a value and the row is dropped. A missing unit keeps the row in [rows] and out
 * of [nutrients], because "Sodium 650" is mg on every pack and this code still does not assume.
 */
object NutritionLabelExtractor {

    fun extract(text: RecognisedText): NutritionLabel {
        val rows = TextLayout.rows(text.blocks).map { Row(it) }

        // The header is the row with the most basis tokens and no nutrient name. Without one, a
        // small pack that prints the basis on every row ("Energy 520 kcal per 100 g") still proves
        // it, row by row, provided every such row proves the SAME basis.
        val header = rows.filter { it.columns.isNotEmpty() && it.name == null }.maxByOrNull { it.columns.size }
        val perRow = header == null && rows.filter { it.name != null && it.columns.isNotEmpty() }
            .map { r -> r.columns.mapNotNull { it.basis } }.let { it.isNotEmpty() && it.all { c -> c.size == 1 } && it.distinct().size == 1 }
        val columns = header?.columns.orEmpty()
        fun columnsFor(row: Row) = if (perRow) row.columns else columns
        fun basisIndex(cols: List<Column>) = cols.indexOfFirst { it.basis is LabelBasis.Per100 }.takeIf { it >= 0 }
            ?: cols.indexOfFirst { it.basis is LabelBasis.PerServing }.takeIf { it >= 0 }

        val servingSize = rows.firstNotNullOfOrNull { SERVING_SIZE.find(it.text) }?.let { m ->
            val (amount, unit) = if (m.groupValues[1].isNotEmpty()) m.groupValues[1] to m.groupValues[2] else m.groupValues[3] to m.groupValues[4]
            amount.toDouble() to unit.lowercase().let { if (it.startsWith("g")) "g" else "ml" }
        }

        val read = rows.filter { it !== header && it.name != null }.mapNotNull { row ->
            val cols = columnsFor(row)
            val i = basisIndex(cols) ?: return@mapNotNull null
            val cell = row.cellFor(cols, i) ?: return@mapNotNull null
            row.name!! to LabelRow(row.printedName!!, cell.value, cell.unit ?: row.nameUnit, row.text)
        }
        val basis = (if (perRow) rows.firstOrNull { it.name != null && it.columns.isNotEmpty() }?.columns else columns)
            ?.let { cols -> basisIndex(cols)?.let { cols[it].basis } }
            ?.let { b -> if (b is LabelBasis.PerServing && b.amount == null && servingSize != null) LabelBasis.PerServing(servingSize.first, servingSize.second) else b }
        val nutrients = read.mapNotNull { (key, r) ->
            val n = DOMAIN[key] ?: return@mapNotNull null
            convert(r.value, r.unit, n.unit)?.let { n to NutrientValue.Measured(it, n.unit) }
        }.distinctBy { it.first }.toMap()

        return NutritionLabel(basis, read.map { it.second }, nutrients, ingredients(rows))
    }

    // --- one printed row ---------------------------------------------------------------------

    private class Column(val basis: LabelBasis?, val centreX: Double?)
    private class Cell(val value: Double, val unit: String?, val percent: Boolean, val centreX: Double?)

    private class Row(val lines: List<TextBlock>) {
        /** The row as printed, left to right. */
        val text = lines.joinToString(" ") { it.text.trim() }
        private val whole = normalise(text)
        /** Which line each character of [whole] came from, so a token can find its own box. */
        private val lineAt = IntArray(whole.length).also { at ->
            var off = 0
            lines.forEachIndexed { i, l -> val n = l.text.trim().length; for (k in off until minOf(off + n, at.size)) at[k] = i; off += n + 1 }
        }
        private val basisTokens = BASIS.findAll(whole).toList()
        /** [whole] with basis tokens blanked, so their digits are never cells. */
        private val clean = blank(whole, basisTokens.asSequence())

        /**
         * Basis tokens in x order, found on the joined row so "Per 100" and "g" on two lines still
         * read. A column knows its x only when it sits on one line that holds nothing else.
         */
        val columns: List<Column> = basisTokens.map { m ->
            val i = lineAt[m.range.first]
            val alone = lineAt[m.range.last] == i && normalise(lines[i].text).replace(m.value, "").isBlank()
            Column(basisOf(m), if (alone) lines[i].centreX else null)
        }

        /** Every free-standing number after the name, in x order, kJ dropped. */
        private val cells: List<Cell>
        /** The name before the first cell, basis tokens blanked, for the lexicon; null without cells or letters. */
        private val keyName: String?
        /** The same span as printed, for the person. */
        val printedName: String?

        init {
            val first = NUM_RX.find(clean)
            fun String.nameSpan() = substring(0, first!!.range.first).trim().trim('-', ':', '•', '*', '†', ' ')
            keyName = first?.let { clean.nameSpan() }?.takeIf { s -> s.any { it.isLetter() } }
            printedName = keyName?.let { whole.nameSpan() }
            val found = if (first == null) emptyList() else CELL.findAll(clean, first.range.first).toList()
                .filter { !it.groupValues[2].equals("kj", true) }
            val perLine = found.groupBy { lineAt[it.range.first] }
            cells = found.map { m ->
                val i = lineAt[m.range.first]
                val alone = perLine[i]!!.size == 1 && normalise(lines[i].text).let { blank(it, BASIS.findAll(it)) }.replace(m.value, "").isBlank()
                Cell(m.groupValues[1].replace(",", "").toDouble(), m.groupValues[2].lowercase().ifEmpty { null },
                    m.groupValues[2] == "%", if (alone) lines[i].centreX else null)
            }
        }

        /** The unit the name carries: "(g)", "(kcal/100g)", ", g" or a trailing "g". */
        val nameUnit: String? = keyName?.let { UNIT_IN_NAME.find(it)?.groupValues?.get(1)?.lowercase() }
        val name: String? = keyName?.let { normaliseName(if (nameUnit != null) it.replace(UNIT_IN_NAME, " ") else it) }
            ?.takeIf { it in LEXICON }

        /**
         * The cell under the basis column: by nearest x when every column and every cell has one;
         * otherwise by order, where a percent cell can only fill a percent column (which may be
         * left empty, as %RDA usually is) and the plain cells must fill the plain columns exactly.
         * Anything else is no value. A row printing "3.1  0.9" under "Per 100 g | Per serve |
         * %RDA" is therefore read, and a row printing "0" under the same header is not, because
         * nothing says which column it is.
         */
        fun cellFor(columns: List<Column>, basisIndex: Int): Cell? {
            // A percent is marked as one, so it can never be mistaken for a value: it is not a cell.
            val plain = cells.filter { !it.percent }
            val plainColumns = columns.indices.filter { columns[it].basis != null }
            if (plain.isEmpty()) return null
            val byPosition = plainColumns.all { columns[it].centreX != null } && plain.all { it.centreX != null }
            return if (byPosition) {
                plain.filter { c ->
                    plainColumns.minByOrNull { i -> kotlin.math.abs(columns[i].centreX!! - c.centreX!!) } == basisIndex
                }.singleOrNull()
            } else {
                if (plain.size == plainColumns.size) plain[plainColumns.indexOf(basisIndex)] else null
            }
        }
    }

    // --- patterns ------------------------------------------------------------------------------

    /** A free-standing number: not glued to a letter, a comparison sign, a comma decimal or a date. */
    private const val NUM = """(?<![A-Za-z0-9.,/:<>\-])(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?(?![0-9]|-[A-Za-z]|,\d)"""
    private val NUM_RX = Regex(NUM)
    /** A number and its unit, if one follows. The space is inside the optional group so a bare number matches exactly its own text. */
    private val CELL = Regex("""($NUM)(?:\s*(kcal|kj|mcg|ug|mg|gm|g|ml|%))?(?![A-Za-z])""", RegexOption.IGNORE_CASE)
    /** A unit at the end of a printed name: "(g)", "(kcal/100g)", ", mg", " g". */
    private val UNIT_IN_NAME = Regex("""(?:\(|\[|,\s*|\s)(kcal|kj|mcg|ug|mg|gm|g|ml)\b[^)\]]*[)\]]?\s*$""", RegexOption.IGNORE_CASE)

    private val BASIS = Regex(
        // "per 100 g" and "/100 g" both prove the basis; a bare "100 g" does not (it is a net weight).
        """(?<per100>(?:per\s*|/\s*)100\s*(?<u100>g|gm|gms|grams?|ml)\b)""" +
            """|(?<servAmt>per\s*(?:serv(?:e|ing)|portion|pack|piece)[^\d%()]{0,12}\(?\s*(?:approx\.?\s*)?(?<amt>\d+(?:\.\d+)?)\s*(?<uamt>g|gm|ml)\b\)?)""" +
            """|(?<perN>per\s*(?<n>\d+(?:\.\d+)?)\s*(?<un>g|gm|ml)\b)""" +
            """|(?<serv>per\s*(?:serv(?:e|ing)|portion|pack|piece|unit|scoop|cup|bar|biscuit|sachet)\b)""" +
            // A percent column often reads "%RDA per serve": that trailing "per serve" belongs to it, not to
            // a fourth column. It may absorb ONLY "per serve": a percent RDA is per serving by definition,
            // and a "%RDA" printed left of "Per 100 g" must never swallow the basis (found by probing).
            """|(?<pct>(?:%\s*(?:rda|dv|ri|nrv|daily)|\b(?:rda|nrv|daily\s*value))(?:\s*value)?(?:\s*per\s*serv(?:e|ing)\b)?)""",
        RegexOption.IGNORE_CASE,
    )

    private fun basisOf(m: MatchResult): LabelBasis? = when {
        m.groups["per100"] != null -> LabelBasis.Per100(m.groups["u100"]!!.value.lowercase().let { if (it.startsWith("g")) "g" else "ml" })
        m.groups["servAmt"] != null -> LabelBasis.PerServing(m.groups["amt"]!!.value.toDouble(), m.groups["uamt"]!!.value.lowercase().let { if (it.startsWith("g")) "g" else "ml" })
        m.groups["perN"] != null -> LabelBasis.PerServing(m.groups["n"]!!.value.toDouble(), m.groups["un"]!!.value.lowercase().let { if (it.startsWith("g")) "g" else "ml" })
        m.groups["serv"] != null -> LabelBasis.PerServing(null, null)
        else -> null
    }

    private val SERVING_SIZE = Regex(
        """serv(?:e|ing)\s*size\s*[:\-]?\s*(?:approx\.?\s*)?(\d+(?:\.\d+)?)\s*(g|gm|ml)\b""" +
            """|(\d+(?:\.\d+)?)\s*(g|gm|ml)\s*per\s*serv""",
        RegexOption.IGNORE_CASE,
    )

    /** Names the panel may print, exactly. The domain knows eight of them; the rest stay rows. */
    private val DOMAIN: Map<String, Nutrient> = mapOf(
        "energy" to Nutrient.ENERGY, "energy value" to Nutrient.ENERGY, "calories" to Nutrient.ENERGY,
        "protein" to Nutrient.PROTEIN, "proteins" to Nutrient.PROTEIN,
        "carbohydrate" to Nutrient.CARBOHYDRATE, "carbohydrates" to Nutrient.CARBOHYDRATE,
        "total carbohydrate" to Nutrient.CARBOHYDRATE, "total carbohydrates" to Nutrient.CARBOHYDRATE, "carbs" to Nutrient.CARBOHYDRATE,
        "fat" to Nutrient.FAT, "fats" to Nutrient.FAT, "total fat" to Nutrient.FAT, "total fats" to Nutrient.FAT,
        "fibre" to Nutrient.FIBRE, "fiber" to Nutrient.FIBRE, "dietary fibre" to Nutrient.FIBRE, "dietary fiber" to Nutrient.FIBRE,
        "total dietary fibre" to Nutrient.FIBRE, "total dietary fiber" to Nutrient.FIBRE,
        "iron" to Nutrient.IRON,
        "vitamin b12" to Nutrient.VITAMIN_B12, "vitamin b 12" to Nutrient.VITAMIN_B12, "vitamin b-12" to Nutrient.VITAMIN_B12,
        "cobalamin" to Nutrient.VITAMIN_B12, "b12" to Nutrient.VITAMIN_B12,
        "sodium" to Nutrient.SODIUM,
    )
    private val LEXICON: Set<String> = DOMAIN.keys + setOf(
        "sugar", "sugars", "total sugar", "total sugars", "added sugar", "added sugars", "sucrose",
        "saturated fat", "saturated fats", "saturates", "saturated fatty acids", "sat fat",
        "trans fat", "trans fats", "trans fatty acids", "cholesterol",
        "mufa", "pufa", "monounsaturated fat", "polyunsaturated fat", "monounsaturated fatty acids", "polyunsaturated fatty acids",
        "calcium", "potassium", "salt", "zinc", "magnesium", "phosphorus", "folate", "folic acid",
        "vitamin a", "vitamin c", "vitamin d", "vitamin e", "vitamin b6", "thiamine", "riboflavin", "niacin",
    )

    private val BRACKETS = Regex("""\([^)]*\)|\[[^\]]*]""")
    private val NAME_NOISE = Regex("""^(?:of\s+which|total\s+of\s+which|-\s*of\s+which)\s+|[*†]""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")

    private fun normaliseName(printed: String): String =
        printed.lowercase().replace(BRACKETS, " ").replace(NAME_NOISE, " ")
            .replace(Regex("""[^a-z0-9\- ]"""), " ").replace(SPACES, " ").trim()

    private fun normalise(s: String) = s.replace('µ', 'u').replace('μ', 'u')

    private fun blank(s: String, spans: Sequence<MatchResult>): String {
        val chars = s.toCharArray()
        spans.forEach { m -> m.range.forEach { chars[it] = ' ' } }
        return String(chars)
    }

    /** Printed unit to the domain unit, by factor. Anything else, including no unit, is not a value. */
    private fun convert(value: Double, printed: String?, target: NutrientUnit): Double? {
        val u = printed?.lowercase() ?: return null
        val grams = when (u) { "g", "gm" -> 1.0; "mg" -> 1e-3; "mcg", "ug" -> 1e-6; else -> null }
        return when (target) {
            NutrientUnit.KCAL -> if (u == "kcal") value else null
            NutrientUnit.GRAM -> grams?.let { value * it }
            NutrientUnit.MILLIGRAM -> grams?.let { value * it * 1e3 }
            NutrientUnit.MICROGRAM -> grams?.let { value * it * 1e6 }
        }
    }

    // --- the ingredient list -----------------------------------------------------------------

    private val INGREDIENTS = Regex("""\bingredients?\s*[:\-]?\s*""", RegexOption.IGNORE_CASE)
    private val STOP = Regex(
        """^\s*(?:allergen|contains|may contain|nutrition|nutritional|net\s|net\.|mfg|mfd|manufactured|marketed|packed|""" +
            """best before|use by|expiry|exp\.|fssai|mrp|batch|lot\b|store|keep|customer|consumer|serving|per\s*100|energy|protein)""",
        RegexOption.IGNORE_CASE,
    )

    /** A number with a nutrient unit: where the ingredient list has ended and the panel begun. E-numbers (`500(ii)`) are not. */
    private val NUTRIENT_LIKE = Regex("""\d\s*(?:g|mg|mcg|ug|kcal|kj|%)(?![A-Za-z])""", RegexOption.IGNORE_CASE)

    private fun ingredients(rows: List<Row>): String? {
        val start = rows.indexOfFirst { INGREDIENTS.containsMatchIn(it.text) }
        if (start < 0) return null
        val first = rows[start].text.let { it.substring(INGREDIENTS.find(it)!!.range.last + 1) }
        val more = rows.drop(start + 1).takeWhile { r ->
            !STOP.containsMatchIn(r.text) && !NUTRIENT_LIKE.containsMatchIn(r.text) && r.name == null
        }.take(6).map { it.text }
        return (listOf(first) + more).joinToString(" ").replace(SPACES, " ").trim().ifEmpty { null }
    }
}
