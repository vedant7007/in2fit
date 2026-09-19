package io.github.vedant7007.katori.data.food

/**
 * Name matching for spoken food names, across Telugu, Hindi, English and the code-mixed speech
 * that is the normal case in Hyderabad rather than the edge case.
 *
 * Pure functions, no I/O, no Android. Every rule here is unit tested.
 *
 * DESIGN
 * The matcher is deliberately conservative. A wrong food is worse than no food in a health app,
 * so the thresholds are set to refuse rather than to reach. When nothing clears the bar the caller
 * asks the user, which spec 10.7 already requires for uncertain parses.
 *
 * Three stages, in order, and the first one that clears its bar wins:
 *   1. exact match on the normalised alias
 *   2. containment, for "two rotis with dal" style phrases where the food name sits inside a
 *      longer utterance fragment
 *   3. edit distance, capped tightly and scaled to the length of the shorter string
 *
 * NOT DONE HERE: transliteration between scripts. Telugu "పప్పు" and roman "pappu" are matched by
 * both appearing in the alias table, not by converting one into the other. Transliteration is a
 * source of confident-looking wrong answers and it is not needed when the alias table carries
 * both forms.
 */
object FoodTextMatching {

    /**
     * Normalises a spoken or typed name for comparison.
     *
     * Keeps Devanagari (U+0900..U+097F) and Telugu (U+0C00..U+0C7F) letters intact, lowercases
     * Latin, collapses whitespace, and strips punctuation. It does NOT strip plurals or stem:
     * "rotis" and "roti" are handled by the edit-distance stage rather than by a rule that would
     * also mangle a legitimate name.
     */
    fun normalise(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.lowercase()) {
            val keep = ch.isLetterOrDigit() || ch == ' ' ||
                ch in 'ऀ'..'ॿ' || ch in 'ఀ'..'౿'
            sb.append(if (keep) ch else ' ')
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    /** True when the text contains Devanagari or Telugu characters. */
    fun isNativeScript(text: String): Boolean =
        text.any { it in 'ऀ'..'ॿ' || it in 'ఀ'..'౿' }

    /**
     * Levenshtein distance. Iterative, two rows, so a long utterance cannot blow the stack.
     */
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /**
     * The maximum edit distance tolerated for a candidate of this length.
     *
     * Short names get no slack at all, because at three characters a single edit reaches a
     * different word: "dal" to "dahi" must not match. Longer names get proportionally more.
     */
    fun toleranceFor(length: Int): Int = when {
        length <= 4 -> 0
        length <= 7 -> 1
        length <= 12 -> 2
        else -> 3
    }

    /** How a candidate was reached. Ordered best to worst; used to pick a winner. */
    enum class MatchStrength { EXACT, CONTAINED, FUZZY }

    data class Candidate(val key: String, val alias: String, val strength: MatchStrength, val distance: Int)

    /**
     * Picks the best candidate, or null when nothing clears the bar.
     *
     * Ties are broken deterministically: strength first, then edit distance, then the longer alias
     * (a longer alias that still matched is more specific), then the key alphabetically. The last
     * tiebreak exists so two equally good candidates never swap between runs.
     */
    fun best(candidates: List<Candidate>): Candidate? =
        candidates.minWithOrNull(
            compareBy<Candidate> { it.strength.ordinal }
                .thenBy { it.distance }
                .thenByDescending { it.alias.length }
                .thenBy { it.key }
        )

    /**
     * Matches [query] against a table of normalised aliases.
     *
     * @param aliases normalised alias to food key. Many aliases map to one key.
     */
    fun match(query: String, aliases: Map<String, String>): Candidate? {
        val q = normalise(query)
        if (q.isEmpty()) return null

        val out = mutableListOf<Candidate>()

        aliases[q]?.let { return Candidate(it, q, MatchStrength.EXACT, 0) }

        for ((alias, key) in aliases) {
            if (alias.length >= 4 && (q.contains(alias) || alias.contains(q))) {
                out += Candidate(key, alias, MatchStrength.CONTAINED, kotlin.math.abs(q.length - alias.length))
            }
        }
        if (out.isNotEmpty()) return best(out)

        for ((alias, key) in aliases) {
            // Never fuzzy-match across scripts: a roman query must not fuzzy onto a Telugu alias.
            if (isNativeScript(alias) != isNativeScript(q)) continue
            val tol = toleranceFor(minOf(alias.length, q.length))
            if (tol == 0) continue
            val d = editDistance(q, alias)
            if (d <= tol) out += Candidate(key, alias, MatchStrength.FUZZY, d)
        }
        return best(out)
    }
}
