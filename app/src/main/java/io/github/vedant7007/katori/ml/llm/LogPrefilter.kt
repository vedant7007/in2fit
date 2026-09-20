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
        // A stated reading is not a meal. FOUND RED ON MASTER (20 Sep, 19:17): "my haemoglobin is
        // 7, is that dangerous" has a digit and no marker, so the quantity rule made it a certain
        // log and the diary would have been written from a health question. A question that
        // asks for a clinical judgement, or states a lab reading, is never a log, whatever else
        // its words say; this is the refusal-that-abandons class, and it goes first.
        if (SafetyLine.invitesClinicalJudgement(transcript)) return false
        val text = FoodTextMatching.normalise(transcript)
        if (text.isEmpty()) return false
        val words = text.split(' ')
        return words.any { it in LOG_WORDS } || MEAL_WAS.any { FoodTextMatching.containsAsWords(text, it) } ||
            words.any { it in QUANTITY_WORDS || it.all { c -> c.isDigit() } }
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
        // Devanagari, the script the hi recogniser emits (demo-utterance-set.csv). Generated from the
        // roman forms above; Vedant reads Devanagari and verifies these himself.
        "क्या", "कितना", "कितनी", "कितने", "कैसे", "कैसा", "कौन", "कौनसा", "कौनसी", "कब", "कहाँ", "कहां", "क्यों", "क्यूं",
        "चाहिए", "चाहिये", "करूँ", "करूं", "करू", "सकता", "सकती", "सकते", "हूँ", "हूं", "रहा", "रही", "रहे",
        "अभी", "बताओ", "बताइए", "बताइये", "अच्छा", "बेहतर", "सही", "ठीक", "सुझाव", "ऐड", "जोड़", "जोड़ूँ", "बदल", "बदलो", "हटा", "हटाओ", "डिलीट",
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
        "खा रहा", "खा रही", "खा रहे", "के लिए", "खाना चाहिए",
    )

    /** Past-tense eating and drinking words. Positive evidence of a log. */
    internal val LOG_WORDS: Set<String> = setOf(
        // English; "log" and "record" are the app's own verbs ("log two rotis")
        "ate", "had", "drank", "eaten", "finished", "used", "log", "record",
        // roman Hindi
        "khaya", "khayi", "khaye", "khaaya", "khai", "piya", "pi", "peeya",
        // Devanagari, as the hi recogniser writes them; Vedant verifies
        "खाया", "खाई", "खाए", "खायी", "खाये", "पिया", "पी",
        // roman Telugu
        "tinnanu", "tinnaanu", "thinnanu", "tinna", "thinna", "tinnam", "tinnaam", "tagaanu", "taganu", "thaganu",
    )

    /**
     * A quantity beside a food, with no marker anywhere, is the statement of a meal: "two rotis,
     * a katori of dal, and two spoons of oil" has no eating verb and is the Beat 1 sentence. In a
     * food diary nothing else is said that way. Number words, household units and digits; the
     * article "a" is deliberately absent, since "a friend told me to eat rice" would log rice.
     * ponytail: "three days no rice" would log rice; negation is not read. The recorded
     * speakers say whether that shape occurs.
     */
    internal val QUANTITY_WORDS: Set<String> = setOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "half", "quarter", "dozen",
        "katori", "bowl", "bowls", "plate", "plates", "glass", "glasses", "cup", "cups", "spoon", "spoons", "teaspoon", "teaspoons",
        "tablespoon", "tablespoons", "piece", "pieces", "slice", "slices", "ml", "gram", "grams", "kg", "litre", "liter",
        // roman Hindi ("do" only mid-sentence: leading "do" is the English question word, checked first)
        "ek", "do", "teen", "char", "paanch", "panch", "chhe", "saat", "aath", "nau", "das", "aadha", "adha", "katori", "chammach", "gilas", "plate",
        // Devanagari, as the hi recogniser writes them; Vedant verifies
        "एक", "दो", "तीन", "चार", "पाँच", "पांच", "छह", "सात", "आठ", "नौ", "दस", "आधा", "आधी",
        "कटोरी", "कटोरा", "प्लेट", "गिलास", "ग्लास", "कप", "चम्मच", "एमएल", "ग्राम",
    )

    /**
     * "lunch was ..." is a log when nothing above vetoes it. "for lunch" alone is NOT enough:
     * "ideas for lunch" has no marker this file knows, and a miss there would log a meal.
     */
    internal val MEAL_WAS: List<String> = listOf(
        "breakfast was", "lunch was", "dinner was", "snack was", "tiffin was",
    )
}
