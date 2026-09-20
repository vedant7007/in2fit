package io.github.vedant7007.katori.data.knowledge

import io.github.vedant7007.katori.data.food.FoodTextMatching
import java.io.InputStream

/**
 * One row of the committed knowledge-facts file, `assets/knowledge/facts.csv`.
 *
 * THE RULE THIS TYPE EXISTS FOR (`docs/decisions/0015`, `0020`): every claim the model makes on the
 * ANSWER and RECOMMEND paths traces to a row, and a row without a real, citable source does not
 * ship. It is the same discipline as the food database, applied to prose: a nutrition fact that
 * nobody can point at is a hallucination that happens to be written down.
 *
 * [fact] is the sentence handed to the model. It is written in English, with every number exactly
 * as the source states it, because the numeric guard permits a number in the model's output only
 * when that number was in its input. A figure rounded here is the figure the app may say; the
 * exact source figure, where it differs, is in [note].
 *
 * WHY THERE IS NO LANGUAGE COLUMN. Nobody on the team writes Telugu or Hindi, and `0017` forbids
 * shipping either unreviewed. So the rows are English and the model renders them in the user's
 * language at generation time, which is what the phrasing path already does, and the numeric
 * guard is script-aware so the figures survive. If a reviewed Telugu set is ever wanted it is a
 * second file keyed by the same [id], with an untranslated row falling back to this one, exactly
 * as a missing string-table key falls back to the default table.
 */
data class KnowledgeFact(
    /** Stable key, `topic.slug`. Referenced by the UI's "why" affordance and by tests. */
    val id: String,
    /** Coarse grouping: iron, protein, glycaemic, swap, ... Used for reporting, not retrieval. */
    val topic: String,
    /** Retrieval keys, lower case. A row is selected when a tag appears in the request as a whole word. */
    val tags: Set<String>,
    /** The sentence the model is given. English. Numbers exactly as the source states them. */
    val fact: String,
    /** The citation as a reader would write it. Never blank. */
    val source: String,
    /** Where the source was read. Never blank. */
    val sourceUrl: String,
    /** ISO date the source was read. */
    val accessed: String,
    /** Provenance detail: the exact figure where [fact] rounds, caveats, why this source. */
    val note: String,
)

/**
 * The knowledge-facts file, loaded once and searched by tag.
 *
 * RETRIEVAL IS DELIBERATELY DUMB. A row is relevant when one of its tags appears in the request
 * text as a whole word, using the same word-bounded containment the food matcher uses and for the
 * same reason (`0006`: reverse or substring containment produced biryani -> bay leaf). Rows are
 * ranked by how many tags hit, ties broken by file order so the author controls precedence.
 * There is no embedding model and no fuzzy matching: a fact retrieved for the wrong reason is a
 * confident sentence about the wrong thing, and a miss is only a shorter answer.
 *
 * ponytail: linear scan over a few hundred rows per request. An inverted index if the file
 * grows past a few thousand, which it should not.
 */
class KnowledgeFacts(val facts: List<KnowledgeFact>) {

    /**
     * Rows whose tags appear in [text], best first, at most [limit].
     *
     * [text] is everything the request knows: the utterance, the declared conditions, the food
     * names on the plate. The caller joins them with spaces; this normalises the result the way
     * the food matcher does, so "Vitamin-C" and "vitamin c" hit the same tag.
     *
     * [limit] is small on purpose. On the test device a prompt token costs about a fifth of a
     * generated token but is still paid on every call (`0014`); six rows is roughly 150 tokens.
     */
    fun find(text: String, limit: Int = DEFAULT_LIMIT): List<KnowledgeFact> {
        val query = FoodTextMatching.normalise(text)
        if (query.isEmpty()) return emptyList()
        return facts.asSequence()
            .map { it to it.tags.count { tag -> FoodTextMatching.containsAsWords(query, tag) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second } // stable, so file order breaks ties
            .map { it.first }
            .take(limit)
            .toList()
    }

    fun byId(id: String): KnowledgeFact? = facts.firstOrNull { it.id == id }

    /**
     * The one or two rows the rules engine ACTUALLY USED, for the prompt.
     *
     * The measured conversational turn (`0025`) put the cost in the prompt: 591 to 779 tokens,
     * six retrieved rows among them, 17 s. The integrator is cutting the rows to the one or two
     * the engine used, and this is what "used" means, so that it is a rule and not a guess:
     *
     * A row was used when one of its tags is a term the ENGINE decided on, which are the
     * nutrients its `PreferNutrient` constraints name ([nutrientTerms]), the conditions the
     * person declared, and the foods it ranked at the top. Among those rows, the ones that also
     * touch the question rank first; ties keep file order. A row that touches only the question
     * and nothing the engine decided was not used by the engine, and is not selected while any
     * used row exists. When the engine decided nothing, as on a general question ("does tea
     * reduce iron absorption"), the question is all there is, and [find] over it with the same
     * limit is the fallback.
     *
     * Two rows is roughly 50 prompt tokens against 150 for six. The prompt still says "use only
     * the facts given", so a row not selected is a claim the model cannot make; that is the
     * intended trade, and the quality set's "traces" criterion is what measures it.
     */
    fun select(engineTerms: Collection<String>, question: String, limit: Int = SELECT_LIMIT): List<KnowledgeFact> {
        val engine = FoodTextMatching.normalise(engineTerms.joinToString(" "))
        val q = FoodTextMatching.normalise(question)
        val used = facts.asSequence()
            .map { f ->
                val engineHits = if (engine.isEmpty()) 0 else f.tags.count { FoodTextMatching.containsAsWords(engine, it) }
                val questionHits = if (q.isEmpty()) 0 else f.tags.count { FoodTextMatching.containsAsWords(q, it) }
                Triple(f, engineHits, questionHits)
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<KnowledgeFact, Int, Int>> { it.second * 2 + it.third }) // stable: file order breaks ties
            .map { it.first }
            .take(limit)
            .toList()
        return used.ifEmpty { find(question, limit) }
    }

    companion object {
        const val DEFAULT_LIMIT = 6
        /** [select]'s limit: the one or two rows the engine used. */
        const val SELECT_LIMIT = 2
        const val ASSET_PATH = "knowledge/facts.csv"

        /**
         * The everyday words a nutrient the engine prefers is tagged by in the file. Keyed on the
         * `Nutrient` enum's NAME, as a string, so this package does not import `domain`.
         */
        fun nutrientTerms(nutrientName: String): List<String> = when (nutrientName.uppercase()) {
            "IRON" -> listOf("iron")
            "PROTEIN" -> listOf("protein")
            "FIBRE", "FIBER" -> listOf("fibre", "fiber")
            "SODIUM" -> listOf("sodium", "salt")
            "VITAMIN_B12" -> listOf("b12", "vitamin b12")
            "FAT" -> listOf("fat", "oil")
            "CARBOHYDRATE" -> listOf("carbohydrate", "carbs", "glycaemic")
            "ENERGY" -> listOf("calories", "energy")
            else -> listOf(nutrientName.lowercase().replace('_', ' '))
        }

        private val HEADER = listOf("id", "topic", "tags", "fact", "source", "source_url", "accessed", "note")

        /** Reads the CSV from a stream and closes it. Throws on a malformed file: a bad row is a build defect, not a runtime condition. */
        fun load(open: () -> InputStream): KnowledgeFacts =
            open().use { parse(it.reader(Charsets.UTF_8).readText()) }

        /**
         * Parses the file. `#` lines are comments, the first other line is the header and must be
         * exactly [HEADER], and a row with a blank [KnowledgeFact.fact], [KnowledgeFact.source] or
         * [KnowledgeFact.sourceUrl] is refused here rather than shipped: the hard rule is that a
         * fact without a source does not go in the file, and this is where the file is read.
         */
        fun parse(csv: String): KnowledgeFacts {
            val rows = Csv.parse(csv)
            require(rows.isNotEmpty()) { "knowledge file is empty" }
            require(rows.first() == HEADER) { "knowledge file header is ${rows.first()}, expected $HEADER" }
            val seen = HashSet<String>()
            val facts = rows.drop(1).mapIndexed { i, r ->
                require(r.size == HEADER.size) { "row ${i + 2} has ${r.size} fields, expected ${HEADER.size}: $r" }
                val (id, topic, tags, fact, source) = r
                val (sourceUrl, accessed, note) = r.drop(5)
                require(id.isNotBlank()) { "row ${i + 2} has no id" }
                require(seen.add(id)) { "duplicate id '$id'" }
                require(fact.isNotBlank()) { "'$id' has no fact" }
                require(source.isNotBlank() && sourceUrl.isNotBlank()) { "'$id' has no source; a fact without a source does not ship" }
                require(tags.isNotBlank()) { "'$id' has no tags and could never be retrieved" }
                KnowledgeFact(
                    id = id.trim(),
                    topic = topic.trim(),
                    tags = tags.split('|').map { FoodTextMatching.normalise(it) }.filter { it.isNotEmpty() }.toSet(),
                    fact = fact.trim(),
                    source = source.trim(),
                    sourceUrl = sourceUrl.trim(),
                    accessed = accessed.trim(),
                    note = note.trim(),
                )
            }
            return KnowledgeFacts(facts)
        }
    }
}

/**
 * A minimal RFC 4180 reader: quoted fields, doubled quotes, embedded commas and newlines.
 * The other authored CSVs are split on commas because their fields carry none; facts are prose
 * and do. Comment lines start with `#` at column 0 and are dropped before parsing.
 */
internal object Csv {
    fun parse(text: String): List<List<String>> {
        val body = text.lineSequence().filterNot { it.startsWith("#") }.joinToString("\n")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                quoted && c == '"' && i + 1 < body.length && body[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < body.length && body[i + 1] == '\n') i++
                    row += field.toString(); field.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        require(!quoted) { "unterminated quote at end of file" }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            if (row.any { it.isNotBlank() }) rows += row
        }
        return rows
    }
}
