package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.data.food.FoodTextMatching

/**
 * Short-circuits the intent classifier for utterances that are CERTAINLY a meal log.
 *
 * WHY. LOG is the most common turn by a wide margin, and on the test device the classifier
 * prompt costs about two seconds of prompt processing before extraction's ten (`0014`). Someone
 * logging three meals a day pays that every time for a decision a rule can make with certainty.
 * When this returns true the orchestrator routes to LOG without a model call; when it returns
 * false the model decides, exactly as before. It never returns anything but "certain" or "ask".
 *
 * THE FAILURE THIS MUST NEVER HAVE. A wrong short-circuit turns a question into a logged meal,
 * and a meal the person never ate goes into their history. So the rule is asymmetric on purpose:
 * it needs POSITIVE evidence of a log (a past-tense eating or drinking word, or "lunch was ...")
 * AND the absence of every question or advice marker it knows. Any doubt goes to the model,
 * which is only a slower correct answer. A missed short-circuit costs two seconds; a wrong one
 * costs the timeline.
 *
 * The marker lists are deliberately over-inclusive: "add", "better", "help" and "want" are
 * rejected even though a log could contain them, because the cost of rejecting is nothing. The
 * log-word list is deliberately short. The roman-script Hindi and Telugu entries were written by
 * someone who does not speak either fluently, in the same spirit as the alias tables (`0006`):
 * a wrong MARKER only sends a log to the model, which is safe; a wrong LOG WORD could
 * short-circuit a question, so those are the ones a fluent speaker should review first.
 *
 * MEASURED on the authored case set by [LogPrefilterTest], which asserts zero misroutes and
 * prints the hit rate. The set is circular (same hand, same day) and the number is a regression
 * guard, not accuracy; Vedant's recorded transcripts replace it.
 *
 * THE LISTS ARE INTERNAL, NOT PRIVATE, so that two tests can read them: one fails if any word is
 * in both a log list and a marker list (the shape of the "do" bug, which nothing else prevents
 * recurring), and one fails if a log word is missing from the reviewer's sheet in
 * `data-authoring/log-words-review.md`, so the words a fluent speaker checks are always the
 * words the code uses.
 */
object LogPrefilter {

    /** True only when [transcript] is certainly a meal log. False means "let the model decide". */
    fun isCertainLog(transcript: String): Boolean {
        if (hasMarker(transcript)) return false
        val text = FoodTextMatching.normalise(transcript)
        if (text.isEmpty()) return false
        val words = text.split(' ')
        return words.any { it in LOG_WORDS } || MEAL_WAS.any { FoodTextMatching.containsAsWords(text, it) }
    }

    /** True when anything in the utterance says it is a question or an ask: a question mark, a marker word, a marker phrase. */
    fun hasMarker(transcript: String): Boolean {
        if (transcript.contains('?') || transcript.contains('？')) return true
        val text = FoodTextMatching.normalise(transcript)
        if (text.isEmpty()) return false
        val words = text.split(' ')
        if (words.first() in LEADING_MARKERS) return true
        if (words.any { it in MARKERS }) return true
        return MULTI_WORD_MARKERS.any { FoodTextMatching.containsAsWords(text, it) }
    }

    /**
     * Any one of these, as a whole word, sends the utterance to the model. Question words,
     * modals, advice verbs and their common roman-script Hindi and Telugu equivalents.
     */
    internal val MARKERS: Set<String> = setOf(
        // English question words and modals
        "what", "whats", "which", "how", "why", "when", "where", "who", "whether",
        "should", "shall", "can", "could", "would", "will", "may", "might", "must",
        "does", "did", "are", "am", "any", "anything", "enough",
        // advice and comparison
        "add", "suggest", "recommend", "recommendation", "advice", "advise", "avoid", "better", "best", "worse",
        "help", "helps", "need", "needs", "want", "wants", "prefer", "instead", "swap", "replace", "option", "options",
        "question", "tell", "show", "check", "compare", "versus", "vs", "ok", "okay", "fine", "good", "healthy",
        // not about food at all, or about editing the diary rather than adding to it
        "report", "doctor", "checked", "reading", "level", "medicine", "tablet", "tablets",
        "remove", "delete", "undo", "cancel", "correct", "change", "edit", "wrong", "mistake",
        // present or future tense: a plate in front of them, not behind them
        "having", "eating", "making", "cooking", "planning", "going", "now", "tonight", "later",
        // roman Hindi
        "kya", "kitna", "kitni", "kitne", "kaise", "kaun", "kaunsa", "kaunsi", "kab", "kahan", "kyun", "kyon",
        "chahiye", "karun", "karoon", "karu", "sakta", "sakti", "sakte", "hoon", "raha", "rahi", "rahe",
        "abhi", "batao", "bataye", "bataiye", "accha", "achha", "behtar", "sahi", "theek",
        // roman Telugu
        "emi", "em", "enti", "entha", "enni", "ela", "ekkada", "eppudu", "evaru", "endhuku", "enduku",
        "tinali", "tinaali", "thinali", "cheyali", "cheyyali", "kavali", "kavaali", "cheppu", "cheppandi",
        "ippudu", "tintunna", "tintunnanu", "tintunnam", "manchidi", "manchida", "bagunda", "avuna", "kada",
    )

    /**
     * Markers only when they open the utterance. "do" and "is" are English question openers
     * ("do I...", "is it...") but mid-sentence they are Hindi: "do" is two ("maine do roti
     * khaya") and "is" is this ("is subah"). Found by the measurement: the whole-word rule sent
     * every "do roti" log to the model.
     */
    internal val LEADING_MARKERS: Set<String> = setOf("do", "is")

    /** Multi-word markers, matched as whole-word runs. */
    internal val MULTI_WORD_MARKERS: List<String> = listOf(
        "was there", "is there", "are there", "about to", "going to", "kha raha", "kha rahi", "kha rahe",
        "hai to", "hai toh", "ke liye", "kosam",
    )

    /** Past-tense eating and drinking words. Positive evidence of a log. */
    internal val LOG_WORDS: Set<String> = setOf(
        // English; "log" and "record" are the app's own verbs ("log two rotis")
        "ate", "had", "drank", "eaten", "finished", "log", "record",
        // roman Hindi
        "khaya", "khayi", "khaye", "khaaya", "khai", "piya", "pi", "peeya",
        // roman Telugu
        "tinnanu", "tinnaanu", "thinnanu", "tinna", "thinna", "tinnam", "tinnaam", "tagaanu", "taganu", "thaganu",
    )

    /**
     * "lunch was ..." is a log when nothing above vetoes it. "for lunch" alone is NOT enough:
     * "ideas for lunch" has no marker this file knows, and a miss there would log a meal.
     */
    internal val MEAL_WAS: List<String> = listOf(
        "breakfast was", "lunch was", "dinner was", "snack was", "tiffin was",
    )
}
