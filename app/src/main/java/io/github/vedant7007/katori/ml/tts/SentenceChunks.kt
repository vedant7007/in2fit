package io.github.vedant7007.katori.ml.tts

/**
 * Splits text for an engine with a per-utterance input limit, on sentence boundaries first and
 * on words only when one sentence alone exceeds the limit. Every character of the input ends up
 * in exactly one chunk, in order: the contract forbids truncation because a health sentence cut
 * short can invert its meaning.
 */
internal object SentenceChunks {

    /** Sentence enders followed by whitespace: Latin punctuation plus the Indic danda. */
    private val sentenceEnd = Regex("(?<=[.!?।॥])\\s+")
    private val whitespace = Regex("\\s+")

    fun split(text: String, maxChars: Int): List<String> {
        require(maxChars > 0) { "maxChars must be positive" }
        val units = text.trim().split(sentenceEnd).flatMap { sentence ->
            if (sentence.length <= maxChars) listOf(sentence)
            else sentence.split(whitespace).flatMap { word -> word.chunked(maxChars) }
        }.filter { it.isNotEmpty() }

        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (unit in units) {
            if (current.isNotEmpty() && current.length + 1 + unit.length > maxChars) {
                out += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(unit)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}
