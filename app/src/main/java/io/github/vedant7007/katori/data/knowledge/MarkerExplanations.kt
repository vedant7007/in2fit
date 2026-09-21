package io.github.vedant7007.katori.data.knowledge

import java.io.InputStream

/**
 * WHAT A LAB MARKER IS, as shipped content (21 Sep, the design's "what this means" line under
 * each report row): one row per test name, written in advance, cited, and read back verbatim.
 * Nothing here is generated at runtime, nothing names a condition, and nothing carries a
 * threshold: the range is the report's own (spec 15.1, 0018). A marker with no row shows no
 * explanation, and the loader refuses a row without a source, the way [KnowledgeFacts] does.
 *
 * The file is `knowledge/markers.csv`; Priya authors its rows and Nila reviews the words.
 */
data class MarkerExplanation(
    /** The name shown for the marker, the first of [aliases]. */
    val testName: String,
    /** Names a report prints for it, lower case, matched as whole words in the printed name. */
    val aliases: List<String>,
    /** One or two sentences: what the marker measures. No threshold, no condition. */
    val explanation: String,
    val source: String,
    val sourceUrl: String,
    val accessed: String,
)

class MarkerExplanations(val rows: List<MarkerExplanation>) {

    /**
     * The row for a printed test name. A report prints "Haemoglobin", "Hemoglobin", "HbA1c" or
     * "Glycosylated Haemoglobin (HbA1c)"; each row's aliases are matched as whole words, the row
     * with the most aliases hit wins (so the HbA1c row beats the haemoglobin row on the long
     * form), a tie is nobody's, because a wrong explanation is worse than none.
     */
    fun find(printedName: String): MarkerExplanation? {
        val name = key(printedName)
        val hits = rows.map { row -> row to row.aliases.count { alias -> Regex("(?<![a-z0-9])" + Regex.escape(alias) + "(?![a-z0-9])").containsMatchIn(name) } }
            .filter { it.second > 0 }
        val best = hits.maxOfOrNull { it.second } ?: return null
        return hits.filter { it.second == best }.singleOrNull()?.first
    }

    companion object {
        const val ASSET_PATH = "knowledge/markers.csv"
        val HEADER = listOf("test_name", "explanation", "source", "source_url", "accessed")

        private fun key(name: String) = name.trim().lowercase().replace(Regex("\\s+"), " ")

        fun load(open: () -> InputStream): MarkerExplanations =
            open().use { parse(it.reader(Charsets.UTF_8).readText()) }

        /** `#` lines are comments; the header must be exactly [HEADER]; a row with a blank explanation or source is refused. */
        fun parse(csv: String): MarkerExplanations {
            val rows = Csv.parse(csv)
            require(rows.isNotEmpty()) { "markers file is empty" }
            require(rows.first() == HEADER) { "markers file header is ${rows.first()}, expected $HEADER" }
            val seen = HashSet<String>()
            return MarkerExplanations(
                rows.drop(1).mapIndexed { i, r ->
                    require(r.size == HEADER.size) { "row ${i + 2} has ${r.size} fields, expected ${HEADER.size}: $r" }
                    val (name, explanation, source, url, accessed) = r
                    require(name.isNotBlank()) { "row ${i + 2} has no test name" }
                    val aliases = name.split('|').map { key(it) }.filter { it.isNotEmpty() }
                    require(aliases.isNotEmpty()) { "row ${i + 2} has no test name" }
                    aliases.forEach { require(seen.add(it)) { "alias '$it' appears twice" } }
                    require(explanation.isNotBlank()) { "'$name' has no explanation" }
                    require(source.isNotBlank() && url.isNotBlank()) { "'$name' has no source; an explanation without a source does not ship" }
                    MarkerExplanation(name.substringBefore('|').trim(), aliases, explanation.trim(), source.trim(), url.trim(), accessed.trim())
                }
            )
        }
    }
}
