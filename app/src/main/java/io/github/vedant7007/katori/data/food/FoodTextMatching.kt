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
            .split(' ').joinToString(" ") { closeFinalTeluguU(it) }
    }

    /**
     * A word-final ు is treated as ్. Telugu closes a borrowed consonant-final word with a short
     * u that the speaker did not say: the ASR model rendered పనీర్ as పనీరు, బటర్ as బటరు, వాటర్ as
     * వాటరు (`logs/asr-codemix-renderings.log`, 0 of 25 English food words said the Telugu way
     * resolved). This is a property of Telugu phonology, not of that model, so it lives in the
     * normaliser, on both the alias and the query side: `tools/build_food_db.py` applies the same
     * rule to `alias_norm`, and a test proves the two agree on the shipped tables. Native words
     * that really end in ు (పప్పు, పాలు) are normalised the same way on both sides, so nothing
     * changes for them; the importer's ambiguous-alias assertion would catch a collision.
     */
    private fun closeFinalTeluguU(word: String): String =
        if (word.endsWith('ు')) word.dropLast(1) + '్' else word

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
     * different word: "dal" to "dahi" must not match.
     *
     * The middle band was 2 and is now 1. The measured utterance set caught "bendakaya", okra,
     * being pulled onto "dondakaya", ivy gourd, two edits apart at nine characters. Two Telugu
     * vegetable names that differ by two letters are common enough that two edits is too generous.
     */
    fun toleranceFor(length: Int): Int = when {
        length <= 4 -> 0
        length <= 12 -> 1
        else -> 2
    }

    /**
     * The query with an English plural ending removed, or null when it has none. Roman script
     * only, and only the two regular endings: "rotis" -> "roti", "idlis" -> "idli", "chapatis" ->
     * "chapati", "tomatoes" -> "tomato". A word that is not a plural but ends in s ("sprouts" as
     * an alias, "dosas") is tried in its full form first, so nothing is lost by the second try.
     */
    fun singular(q: String): String? {
        if (isNativeScript(q) || q.length < 4) return null
        return when {
            q.endsWith("ies") -> q.dropLast(3) + "y"
            q.endsWith("oes") -> q.dropLast(2)
            q.endsWith("ses") || q.endsWith("xes") || q.endsWith("ches") || q.endsWith("shes") -> q.dropLast(2)
            q.endsWith("ss") -> null
            q.endsWith("s") -> q.dropLast(1)
            else -> null
        }
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
     * True when [alias] appears in [query] as a whole run of words.
     *
     * WHY WHOLE WORDS. An earlier version tested plain substring containment in BOTH directions,
     * and the measured utterance set showed exactly what that costs:
     *
     *   "biryani" matched the alias "biryani aaku"  -> bay leaf
     *   "upma"    matched the alias "upma rava"     -> semolina
     *   "atta"    matched the alias "kadi patta"    -> curry leaves, a no-data item
     *   "avalu"   matched the alias "ulavalu"       -> horse gram, a no-data item
     *
     * Three of those are a whole dish collapsing onto one of its ingredients, which is the worst
     * kind of wrong answer here because the number that follows looks reasonable.
     *
     * So containment now runs in ONE direction only, the alias inside the utterance, and only on
     * word boundaries. "two spoons of groundnut oil" still finds "groundnut oil"; "biryani" no
     * longer finds "biryani aaku".
     */
    fun containsAsWords(query: String, alias: String): Boolean {
        val q = query.split(' ').filter { it.isNotEmpty() }
        val a = alias.split(' ').filter { it.isNotEmpty() }
        if (a.isEmpty() || a.size > q.size) return false
        for (i in 0..(q.size - a.size)) {
            if ((a.indices).all { q[i + it] == a[it] }) return true
        }
        return false
    }

    /**
     * Matches [query] against a table of normalised aliases.
     *
     * @param allowFuzzy false disables the edit-distance stage entirely. Used for the no-data
     *   list: refusing a food because its name merely RESEMBLES something we hold no data for is
     *   worse than missing it. The measured set caught "gajar" being refused as "gawar", cluster
     *   beans, one edit apart.
     */
    fun match(query: String, aliases: Map<String, String>, allowFuzzy: Boolean = true): Candidate? {
        val q = normalise(query)
        if (q.isEmpty()) return null

        aliases[q]?.let { return Candidate(it, q, MatchStrength.EXACT, 0) }

        val contained = mutableListOf<Candidate>()
        for ((alias, key) in aliases) {
            if (alias.length >= 3 && containsAsWords(q, alias)) {
                contained += Candidate(key, alias, MatchStrength.CONTAINED, q.length - alias.length)
            }
        }
        if (contained.isNotEmpty()) return best(contained)

        // An English plural. "rotis" against "roti" is one edit at four letters, which the
        // tolerance rule rightly refuses (that slack also reaches "dahi" from "dal"), so the
        // plural is taken off explicitly and the exact and contained stages run once more.
        // MEASURED on the first end-to-end run (20 Sep): "I had two rotis and a katori of dal"
        // was NO_MATCH on "rotis", and the demo language is English.
        singular(q)?.let { sq ->
            aliases[sq]?.let { return Candidate(it, sq, MatchStrength.EXACT, 0) }
            val c = aliases.filter { (alias, _) -> alias.length >= 3 && containsAsWords(sq, alias) }
                .map { (alias, key) -> Candidate(key, alias, MatchStrength.CONTAINED, sq.length - alias.length) }
            if (c.isNotEmpty()) return best(c)
        }

        if (!allowFuzzy) return null

        // The fuzzy stage runs on the query AND on its singular. FOUND ON THE PHONE (21 Sep, a
        // typed turn): "chapatiis", a plural with a typo, is two edits from "chapati" at nine
        // letters, which the tolerance refuses; its singular "chapatii" is one edit at eight,
        // which it allows, and the singular never reached this stage. A real typo and a real
        // mishearing, and the LOG said "I do not know a food in that".
        val fuzzy = mutableListOf<Candidate>()
        for (form in listOfNotNull(q, singular(q))) {
            for ((alias, key) in aliases) {
                // Never fuzzy-match across scripts: a roman query must not fuzzy onto a Telugu alias.
                if (isNativeScript(alias) != isNativeScript(form)) continue
                val tol = toleranceFor(minOf(alias.length, form.length))
                if (tol == 0) continue
                val d = editDistance(form, alias)
                if (d <= tol) fuzzy += Candidate(key, alias, MatchStrength.FUZZY, d)
            }
        }
        return best(fuzzy)
    }
}
