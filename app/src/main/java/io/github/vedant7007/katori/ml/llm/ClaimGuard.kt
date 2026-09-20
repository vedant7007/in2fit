package io.github.vedant7007.katori.ml.llm

/**
 * A nutrition claim is quoted verbatim from its row by code; the model may not restate one in
 * its own words.
 *
 * RULED 20 Sep, from the ten demo sentences on the handset: "Chickpeas (bengal gram), cooked.
 * They contain iron and are rich in vitamin C, which enhances iron absorption." passed every
 * guard, because no number was invented and no condition named, and it is false: the model
 * fused a knowledge row's claim (vitamin C helps iron absorption) onto the food it was naming.
 * Paraphrasing a sourced claim is deciding, and the model does not decide. So a sentence that
 * makes a general claim (`CLAIM_MARKERS`: rich in, source of, contains, helps, absorbs, good
 * for ...) must appear VERBATIM in one of the rows or lines the model was given, or it is
 * refused. The model may name the food, introduce it and connect it to the question; a figure
 * it restates is the numeric guard's business, not this one's.
 *
 * THE SAME CLASS, ABOUT THE DIARY. "You ate rice, dal, curd, rotis, milk and egg on Tuesday"
 * when nothing was logged on a Tuesday: an assertion about the person's own records that the
 * records do not contain, and nothing stopped it. A day name in the output must appear in a
 * line the model was given; the diary lines carry their weekday for exactly this reason.
 *
 * Verbatim means: the sentence, lower-cased with punctuation and spacing normalised, is
 * contained in a normalised source, or a whole normalised source is contained in it (a row
 * quoted with a lead-in around it still counts). Nothing looser, because looser is paraphrase.
 */
object ClaimGuard {

    /**
     * The first sentence that makes a claim the sources do not carry verbatim, or null when
     * clean. [sources] are the rows and lines the model was given, NOT the question: a day the
     * person asked about is not a day the diary contains.
     */
    fun firstUngroundedClaim(output: String, sources: List<String>): String? {
        val normalisedSources = sources.map(::normalise).filter { it.isNotEmpty() }
        for (sentence in sentences(output)) {
            val n = normalise(sentence)
            if (n.isEmpty()) continue
            // A claim is a claim verb about a nutrition noun. "if that helps the conversation"
            // has the verb and no noun, and is not one.
            val isClaim = CLAIM_MARKERS.any { containsWords(n, it) } && NUTRITION_NOUNS.any { containsWords(n, it) }
            val day = DAY_WORDS.firstOrNull { containsWords(n, it) }
            if (!isClaim && day == null) continue
            val verbatim = normalisedSources.any { src -> src.contains(n) || n.contains(src) }
            if (verbatim) continue
            if (day != null && normalisedSources.none { containsWords(it, day) }) return sentence.trim()
            if (isClaim) return sentence.trim()
        }
        return null
    }

    private fun sentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?।])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun normalise(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun containsWords(haystack: String, needle: String): Boolean =
        (" $haystack ").contains(" $needle ")

    /**
     * What makes a sentence a general nutrition claim rather than a name, an introduction or a
     * restated figure. Phrases, matched as whole words, English; the model answers in English
     * on the phone today (measured 20 Sep with languageTag hi).
     */
    internal val CLAIM_MARKERS: List<String> = listOf(
        "rich in", "source of", "sources of", "good source", "contain", "contains", "containing", "high in", "low in",
        "helps", "help", "helping", "improve", "improves", "increase", "increases", "reduce", "reduces", "lower", "lowers",
        "boost", "boosts", "absorb", "absorbs", "absorbed", "absorption", "enhance", "enhances", "good for", "bad for",
        "important for", "essential", "prevent", "prevents", "protects", "supports", "benefit", "beneficial",
    )

    internal val NUTRITION_NOUNS: List<String> = listOf(
        "iron", "protein", "vitamin", "vitamins", "calcium", "fibre", "fiber", "carbohydrate", "carbohydrates", "carbs", "fat", "fats",
        "sodium", "salt", "sugar", "sugars", "glycaemic", "glycemic", "cholesterol", "potassium", "folate", "b12", "zinc",
        "absorption", "haemoglobin", "hemoglobin", "calories", "kcal", "energy", "nutrients", "nutrient", "insulin", "blood pressure",
    )

    internal val DAY_WORDS: List<String> = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
}
