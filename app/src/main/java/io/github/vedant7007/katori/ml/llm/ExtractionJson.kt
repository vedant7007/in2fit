package io.github.vedant7007.katori.ml.llm

/**
 * A strict reader for the one JSON shape the extraction path accepts.
 *
 * WHY A HAND-WRITTEN PARSER RATHER THAN A LIBRARY. A general JSON library is built to accept as
 * much as it reasonably can. Here the useful property is the opposite one: anything that is not
 * exactly the agreed shape must be REFUSED so the model gets re-asked, and after the re-asks are
 * spent the orchestrator asks the person instead. Spec 10.7 already requires that, and the
 * contract on [LlmEngine] states plainly that this path never repairs the JSON by inference.
 *
 * So this refuses an unknown field, a nested object, a duplicate key, a trailing comma, anything
 * after the closing brace, and a quantity that is not a plain positive number. A permissive
 * parser would have swallowed several of those and handed on a half-understood meal.
 *
 * THE ONE THING IT DOES TIDY, and the line being drawn. A model asked for JSON often returns it
 * inside a markdown code fence. Removing a fence that wraps the WHOLE response is removing an
 * envelope, not guessing at contents, so it is allowed. Pulling a JSON-looking fragment out of a
 * sentence would be a guess about what the model meant, so it is not.
 *
 * The accepted shape, and nothing else:
 *
 *     {"items":[{"name":"roti","quantity":2,"unit":"piece","method":"griddle"}]}
 *
 * `name` is required and non-empty. `quantity`, `unit` and `method` may be null or absent, and an
 * absent quantity stays absent: the model is told never to fill one in, and [Rejected] is the
 * answer if it returns something that is not a number.
 */
internal object ExtractionJson {

    /** The most items one utterance may produce. A longer list is a model that has run away. */
    const val MAX_ITEMS = 12

    /** Longest accepted value for any string field. */
    const val MAX_FIELD_LENGTH = 64

    sealed interface Result {
        data class Parsed(val items: List<ExtractedItem>, val normalisedJson: String) : Result
        data class Rejected(val why: String) : Result
    }

    private val ITEM_KEYS = setOf("name", "quantity", "unit", "method")

    fun parse(raw: String): Result {
        val text = stripCodeFence(raw.trim())
        if (text.isEmpty()) return Result.Rejected("empty response")

        val p = Cursor(text)
        return try {
            p.skipSpace()
            val items = p.readRoot()
            p.skipSpace()
            if (!p.atEnd()) return Result.Rejected("trailing content after the JSON object")
            if (items.size > MAX_ITEMS) {
                return Result.Rejected("${items.size} items, more than the $MAX_ITEMS permitted")
            }
            Result.Parsed(items, text)
        } catch (e: Bad) {
            Result.Rejected(e.message ?: "malformed JSON")
        }
    }

    /** Removes a fence that wraps the entire response. A fence around part of it is left alone. */
    private fun stripCodeFence(s: String): String {
        if (!s.startsWith("```")) return s
        val firstBreak = s.indexOf('\n')
        if (firstBreak < 0) return s
        val end = s.lastIndexOf("```")
        if (end <= firstBreak) return s
        return s.substring(firstBreak + 1, end).trim()
    }

    private class Bad(message: String) : Exception(message)

    private class Cursor(val s: String) {
        var i = 0

        fun atEnd() = i >= s.length

        fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun expect(c: Char) {
            skipSpace()
            if (i >= s.length || s[i] != c) {
                throw Bad("expected '$c' at position $i")
            }
            i++
        }

        fun peek(): Char {
            skipSpace()
            if (i >= s.length) throw Bad("response ended early")
            return s[i]
        }

        fun readRoot(): List<ExtractedItem> {
            expect('{')
            val seen = mutableSetOf<String>()
            var items: List<ExtractedItem>? = null
            if (peek() != '}') {
                while (true) {
                    val key = readString()
                    if (!seen.add(key)) throw Bad("duplicate key '$key'")
                    expect(':')
                    when (key) {
                        "items" -> items = readItems()
                        else -> throw Bad("unknown top-level key '$key'")
                    }
                    skipSpace()
                    if (peek() == ',') { i++; continue }
                    break
                }
            }
            expect('}')
            return items ?: throw Bad("no 'items' array")
        }

        fun readItems(): List<ExtractedItem> {
            expect('[')
            val out = mutableListOf<ExtractedItem>()
            if (peek() == ']') { i++; return out }
            while (true) {
                out += readItem()
                if (out.size > MAX_ITEMS) throw Bad("more than $MAX_ITEMS items")
                skipSpace()
                when (peek()) {
                    ',' -> { i++; if (peek() == ']') throw Bad("trailing comma in items") }
                    ']' -> { i++; return out }
                    else -> throw Bad("expected ',' or ']' at position $i")
                }
            }
        }

        fun readItem(): ExtractedItem {
            expect('{')
            var name: String? = null
            var quantity: Double? = null
            var unit: String? = null
            var method: String? = null
            val seen = mutableSetOf<String>()

            if (peek() != '}') {
                while (true) {
                    val key = readString()
                    if (key !in ITEM_KEYS) throw Bad("unknown item field '$key'")
                    if (!seen.add(key)) throw Bad("duplicate item field '$key'")
                    expect(':')
                    when (key) {
                        "name" -> name = readString().also {
                            if (it.isBlank()) throw Bad("empty 'name'")
                        }
                        "quantity" -> quantity = readNullableNumber()
                        "unit" -> unit = readNullableString()
                        "method" -> method = readNullableString()
                    }
                    skipSpace()
                    if (peek() == ',') {
                        i++
                        if (peek() == '}') throw Bad("trailing comma in an item")
                        continue
                    }
                    break
                }
            }
            expect('}')
            val n = name ?: throw Bad("an item has no 'name'")
            return ExtractedItem(name = n, quantity = quantity, unit = unit, cookingMethod = method)
        }

        fun readNullableString(): String? {
            skipSpace()
            if (s.startsWith("null", i)) { i += 4; return null }
            val v = readString()
            return v.ifBlank { null }
        }

        fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw Bad("unterminated string")
                when (val c = s[i]) {
                    '"' -> { i++; break }
                    '\\' -> {
                        i++
                        if (i >= s.length) throw Bad("unterminated escape")
                        when (val e = s[i]) {
                            '"', '\\', '/' -> sb.append(e)
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 >= s.length) throw Bad("truncated \\u escape")
                                val hex = s.substring(i + 1, i + 5)
                                val code = hex.toIntOrNull(16) ?: throw Bad("bad \\u escape '$hex'")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> throw Bad("unsupported escape '\\$e'")
                        }
                        i++
                    }
                    else -> { sb.append(c); i++ }
                }
                if (sb.length > MAX_FIELD_LENGTH) throw Bad("a string field is longer than $MAX_FIELD_LENGTH")
            }
            return sb.toString()
        }

        /**
         * A quantity is a plain positive decimal number, or null.
         *
         * No exponents, no negatives, no numbers written as strings. Each of those is a sign the
         * model is improvising rather than reporting, and improvisation is what the re-ask exists
         * for. A zero quantity is refused too: nobody says they ate zero rotis, so a zero means
         * the model filled a field it should have left empty.
         */
        fun readNullableNumber(): Double? {
            skipSpace()
            if (s.startsWith("null", i)) { i += 4; return null }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            val run = s.substring(start, i)
            if (run.isEmpty()) throw Bad("'quantity' is not a number")
            if (run.count { it == '.' } > 1) throw Bad("'quantity' is not a number: '$run'")
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) throw Bad("'quantity' uses an exponent")
            val v = run.toDoubleOrNull() ?: throw Bad("'quantity' is not a number: '$run'")
            if (!v.isFinite() || v <= 0.0) throw Bad("'quantity' must be positive, was $v")
            return v
        }
    }
}
