package io.github.vedant7007.katori.ml.llm

/**
 * The last line of defence behind spec 11.5: the model never produces a number.
 *
 * WHAT THIS CATCHES. The model is handed figures as finished strings and asked only to put words
 * around them. It can still write "that's roughly 450 calories" because language models complete
 * sentences, and a sentence about food wants a number in it. That output is a fabricated health
 * figure wearing the app's voice, and nothing downstream would question it. This rejects it.
 *
 * THE RULE. Every numeric value in the output must appear as a numeric value in the input, where
 * input means the display figures, the trigger sentence and the allowed food names. One invented
 * value fails the whole response; the caller then shows the rules engine's own sentence, which
 * needs no model at all.
 *
 * ### Why values and not literal strings
 *
 * Matching the literal text would reject "5g" when the input said "5 g", and reject Telugu or
 * Devanagari numerals for a figure the model was actually given. Those are the same number, and
 * refusing them would make the guard fire on correct output often enough that somebody would
 * eventually be tempted to soften it. So the comparison is on the parsed value, with separators
 * and script normalised away.
 *
 * ### The one place that strictness is restored
 *
 * A value that appears in the input ONLY attached to letters does not licence a bare number in the
 * output. "B12" would otherwise permit "12 g of protein", which is exactly the invention this
 * exists to stop. So "b12" in, "b12" out is fine; "b12" in, bare "12" out is rejected.
 *
 * ### Out of scope, deliberately
 *
 * Numbers written as words. "two rotis" and "half a katori" are not caught here, because a guard
 * that tried would have to understand nine languages' number words and would fail open in the
 * ones it got wrong. Spec 15's string review covers phrasing; this covers digits.
 *
 * Pure, allocation-light, no I/O. Runs on every phrasing call.
 */
class DefaultNumericGuard : NumericGuard {

    override fun firstInventedNumber(output: String, permittedFrom: List<String>): String? {
        val permitted = HashSet<Long>()
        val permittedBare = HashSet<Long>()
        for (source in permittedFrom) {
            for (t in tokens(source)) {
                permitted += t.scaled
                if (!t.letterAttached) permittedBare += t.scaled
            }
        }

        for (t in tokens(output)) {
            if (t.scaled !in permitted) return t.text
            // A value the input only ever showed glued to letters, such as the 12 in "B12",
            // does not licence a free-standing 12 in the output.
            if (!t.letterAttached && t.scaled !in permittedBare) return t.text
        }
        return null
    }

    /**
     * One numeric run as it appeared, plus the value it denotes.
     *
     * [scaled] is the value times 1000 rounded, so 2.5 and 2.500 compare equal without any
     * floating-point equality being involved. Three decimal places is past anything this app
     * displays; nutrition figures are shown to at most one.
     */
    private data class Token(val text: String, val scaled: Long, val letterAttached: Boolean)

    private fun tokens(s: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < s.length) {
            if (digitValue(s[i]) < 0) { i++; continue }

            // The run: digits, with separators kept only when they sit between two digits, so a
            // trailing full stop at the end of a sentence is not swallowed into the number.
            val start = i
            while (i < s.length) {
                val c = s[i]
                if (digitValue(c) >= 0) { i++; continue }
                val isSeparator = c == '.' || c == ',' || c == ':' || c == '/'
                if (isSeparator && i + 1 < s.length && digitValue(s[i + 1]) >= 0) { i++; continue }
                break
            }
            val run = s.substring(start, i)

            val letterBefore = start > 0 && s[start - 1].isLetter()
            val letterAfter = i < s.length && s[i].isLetter()

            // Include the attached letters in the reported text, so the caller's error message
            // says "B12" rather than a bare "12" that looks like it came from nowhere.
            var from = start
            while (from > 0 && s[from - 1].isLetter()) from--
            var to = i
            while (to < s.length && s[to].isLetter()) to++

            out += Token(
                text = s.substring(from, to),
                scaled = scaledValue(run),
                letterAttached = letterBefore || letterAfter,
            )
        }
        return out
    }

    /**
     * The value of a digit run, times 1000.
     *
     * Thousands separators are dropped. A run holding more than one decimal point, or a slash or
     * colon, is not one number: "1/2" and "10:30" are kept as their own opaque values so they can
     * only match an identical construction in the input, never a bare 1 or 10.
     */
    private fun scaledValue(run: String): Long {
        val hasSlashOrColon = run.any { it == '/' || it == ':' }
        val dots = run.count { it == '.' }
        val commas = run.count { it == ',' }

        if (hasSlashOrColon || dots > 1) {
            // Opaque: hash the normalised digits and separators. Two identical constructions
            // agree; nothing else does. Negative to keep it clear of real values.
            return -run.map { c -> if (digitValue(c) >= 0) digitValue(c).digitToChar() else c }
                .joinToString("").hashCode().toLong().let { if (it > 0) -it else it }
        }

        // One dot is a decimal point only when it is not acting as a thousands separator, which is
        // the "1.200" case in European writing. Three digits after it and any comma elsewhere in
        // the run means grouping, not a decimal.
        val dotAt = run.indexOf('.')
        val dotIsGrouping = dotAt >= 0 && run.length - dotAt - 1 == 3 && commas == 0 && dotAt <= 3 &&
            run.length > 4 && run.getOrNull(0) != '0'

        val sb = StringBuilder()
        var decimals = -1
        for (c in run) {
            val d = digitValue(c)
            when {
                d >= 0 -> { sb.append(d); if (decimals >= 0) decimals++ }
                c == '.' && !dotIsGrouping -> decimals = 0
                else -> Unit // a comma, or a grouping dot: drop it
            }
        }
        if (sb.isEmpty()) return 0L
        val digits = sb.toString().trimStart('0').ifEmpty { "0" }
        val whole = digits.toLongOrNull() ?: return -1L
        val places = if (decimals < 0) 0 else decimals
        return when {
            places == 0 -> whole * 1000
            places == 1 -> whole * 100
            places == 2 -> whole * 10
            places == 3 -> whole
            // Past three decimals, round rather than overflow. Nothing here displays that many.
            else -> {
                var v = whole
                repeat(places - 3) { v /= 10 }
                v
            }
        }
    }

    /** Digit value across Latin, Devanagari and Telugu numerals. */
    private fun digitValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in '०'..'९' -> c - '०'  // Devanagari
        in '౦'..'౯' -> c - '౦'  // Telugu
        else -> -1
    }
}
