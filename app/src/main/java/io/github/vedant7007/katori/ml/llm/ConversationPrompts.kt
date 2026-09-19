package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.data.knowledge.KnowledgeFact

/**
 * The prompts of the conversational assistant (`docs/decisions/0015`): one to route an
 * utterance to an intent, and two to answer from retrieved knowledge rows.
 *
 * NOTHING HERE IS WIRED. The Orchestrator that routes by intent and the third `LlmEngine` path
 * are the integrator's. This file is the prompt text, the request shapes those paths will take,
 * and [permitted], which is the list the numeric guard must be given. It is defined next to the
 * prompt so the two cannot drift: a figure the prompt shows but the guard does not know about
 * would fail every answer.
 *
 * THE SAFETY LINE, as `0015` draws it. The model MAY explain, guide, suggest swaps, answer
 * nutrition questions and encourage. It MAY NOT state a number it was not given, name a condition
 * the user has not declared, diagnose, or prescribe. Each prompt below states that line in the
 * system block; [NumericGuard] enforces the number half of it at runtime, and the
 * condition/diagnosis half is a string-review matter (spec 15.2) with the prompt as its first
 * defence and the deterministic parts of the response as its second: the doctor referral is a
 * fixed line appended by code, never generated.
 *
 * WHY THE FACTS GO IN AS PROSE. The rows carry their numbers in the sentence ("about 228 mg of
 * vitamin C per 100 g"), so the guard's permitted set is the row text itself. There is no typed
 * number anywhere in these prompts for the model to do arithmetic on, which is the same defence
 * the phrasing prompt uses.
 *
 * TOKEN COST. On the test device a prompt token costs about a fifth of a generated token and
 * generation runs at about 9 tok/s (`0014`). Each retrieved row is roughly 25 prompt tokens; the
 * answer is capped in words by the prompt because the answer is the expensive part. Nothing here
 * has been measured on the phone; the numbers above are the extraction path's and the first run
 * of these prompts replaces them.
 */
internal object ConversationPrompts {

    /**
     * Routes an utterance to LOG, ANSWER, SUGGEST or RECOMMEND.
     *
     * THE OUTPUT IS ONE WORD, because generated tokens are the cost. `maxTokens` for this prompt is
     * [INTENT_MAX_TOKENS] and the stop is a newline; [Intent.parse] reads the first word and
     * ignores anything after it. The four labels are ordinary English words rather than letters
     * or digits because a 1.5B model is more reliable at emitting a word it was shown than at
     * mapping a category to a symbol, and the token difference is one or two tokens, about a
     * tenth of a second.
     *
     * Each label is defined by one example in the same line, which is the cheapest form of
     * few-shot. The SUGGEST/RECOMMEND boundary is the one a small model blurs (both ask for
     * advice); the definitions pin it on whether they are describing a plate in front of them.
     *
     * NOT MEASURED. `data-authoring/intent-test-set.csv` is the authored case set; its result
     * on the device is the first number this prompt gets, and the examples in the definitions
     * are the first thing to try cutting if that number holds without them.
     */
    fun intent(transcript: String, languageTag: String): String {
        val system = """
            You sort what a person said to a food-diary app into one of four kinds. Reply with one word: LOG, ANSWER, SUGGEST or RECOMMEND. Nothing else.

            LOG: they are telling you what they ate or drank. "I had two rotis and dal"
            ANSWER: they ask a question about their past meals, or a general nutrition question. "how much protein did I eat today", "does tea reduce iron"
            SUGGEST: they are eating something now and ask what to add or change. "I'm having rice and sambar, what should I add"
            RECOMMEND: they ask what to eat for a health goal or condition. "what should I eat for more iron"

            They may speak Telugu, Hindi, English or a mix.
        """.trimIndent()
        val user = "Language: $languageTag\nThey said: $transcript"
        return Prompts.chat(system, user)
    }

    /**
     * ANSWER: a question about the person's own history, or a general nutrition question,
     * answered from database figures and knowledge rows. `0015` lists "answer nutrition
     * questions" among what the model may do, so "does tea reduce iron" routes here with no
     * figures and only rows; a question about what THEY should eat is RECOMMEND.
     *
     * The figures are the orchestrator's: totals from the DAO, already formatted, already
     * carrying their completeness ("at least 42 g; two items had no protein value"). The model
     * does not see a number it could add up. If the question needs a figure that is not in the
     * list, the prompt tells it to say so rather than estimate, which the guard would reject
     * anyway; saying so is the better failure.
     *
     * NEVER CONTEXT-FREE. The orchestrator renders the person's recent meals and lab values into
     * [AnswerRequest.figures], and their declared conditions and situation into the request
     * verbatim, before calling this; "did I get enough iron this week" is answered from what they
     * logged and what their last report said, not from a generic paragraph. The condition rule is
     * the same as RECOMMEND's: only what they told us, in their words.
     */
    fun answer(request: AnswerRequest): String {
        val system = """
            You answer a person's question about their food diary or about nutrition. Use only what is listed below.

            Rules:
            - Use ONLY the figures and facts given, with numbers exactly as written. Never calculate, total, convert or estimate. If the answer needs a figure that is not listed, say you do not have that figure.
            - Say what the data shows. You may mention only the conditions they have told us about, in their words. Never suggest they have any other condition, never diagnose, never tell them to take, stop or change any medicine or supplement.
            - Plain words, two or three short sentences. No greeting, no sign-off, no markdown, no list.
        """.trimIndent()

        val user = buildString {
            append("Language to reply in: ").append(request.languageTag).append("\n\n")
            if (request.declaredConditions.isNotEmpty()) {
                append("They have told us: ").append(request.declaredConditions.joinToString("; ")).append("\n")
            }
            request.context?.let { append("Their situation: ").append(it).append('\n') }
            if (request.declaredConditions.isNotEmpty() || request.context != null) append('\n')
            if (request.figures.isNotEmpty()) {
                append("Figures from their diary and reports, exactly as written:\n")
                request.figures.forEach { append("- ").append(it.text).append('\n') }
                append('\n')
            } else {
                append("There are no figures for this question.\n\n")
            }
            appendFacts(request.facts)
            if (request.referralFollows) {
                append("A line asking them to discuss this with a doctor is shown after your answer. Do not contradict it, and do not judge what a reading or a symptom means.\n\n")
            }
            append("They asked: ").append(request.question)
        }
        checkPermitted(request.figures.map { it.text } + request.facts.map { it.fact }, permitted(request))
        return Prompts.chat(system, user)
    }

    /**
     * RECOMMEND: what to eat for a goal or a declared condition, from knowledge rows, the
     * declared profile and the allowed food list.
     *
     * WHAT IT MAY NAME. Only the conditions under "They have told us", verbatim: the prompt
     * shows the person's own words for their condition and nothing else, so the model has
     * nothing to diagnose from. A lab value arrives, if at all, as the rules engine's rendered
     * trigger sentence, which already reads "below the range printed on it" and never names a
     * condition (spec 15.2); the model may repeat it, not interpret it.
     *
     * THE REFERRAL IS NOT GENERATED. When the rules engine decides a referral is mandatory
     * (`Severity.ESCALATE` as `0015` redefines it: alongside help, not instead of it), the caller
     * appends the fixed referral line after the model's text and sets [RecommendRequest.referralFollows]
     * so the model is told not to contradict it. A generated referral could be softened, hedged or
     * omitted on a bad sample; a fixed one cannot.
     *
     * CONSTRAINTS ARE NEVER RELAXED. Diet type and allergies go in as "Never suggest" lines, and
     * the allowed food list is already filtered by them upstream, so a slip in the prose has no
     * food to land on. The model picks from the list and explains; it does not invent a food
     * (spec 4.3), and the UI renders suggestions from the list, not from the prose.
     */
    fun recommend(request: RecommendRequest): String {
        val system = """
            You help a person choose what to eat, using only the facts and foods listed below.

            Rules:
            - Use ONLY the facts given, with numbers exactly as written. Never calculate, convert or estimate a number. Do not add a figure from memory.
            - Suggest only foods from the allowed list. Explain briefly why, from the facts.
            - You may mention only the conditions they have told us about, in their words. Never suggest they have any other condition, never diagnose, never tell them to take, stop or change a medicine or supplement. If a report line is given, repeat it only as written.
            - Respect every "never suggest" line without exception.
            - Be encouraging and practical. Three or four short sentences. No greeting, no sign-off, no markdown, no list.
        """.trimIndent()

        val user = buildString {
            append("Language to reply in: ").append(request.languageTag).append("\n\n")
            if (request.declaredConditions.isNotEmpty()) {
                append("They have told us: ").append(request.declaredConditions.joinToString("; ")).append("\n")
            }
            request.context?.let { append("Their situation: ").append(it).append('\n') }
            request.constraints.forEach { append("Never suggest: ").append(it).append('\n') }
            if (request.declaredConditions.isNotEmpty() || request.context != null || request.constraints.isNotEmpty()) append('\n')
            request.triggerText?.let { append("From their last report, exactly as written:\n").append(it).append("\n\n") }
            appendFacts(request.facts)
            if (request.allowedFoodNames.isNotEmpty()) {
                append("Foods you may suggest, and no others:\n")
                request.allowedFoodNames.forEach { append("- ").append(it).append('\n') }
                append('\n')
            } else {
                append("There is no food you may suggest by name; explain from the facts only.\n\n")
            }
            if (request.referralFollows) {
                append("A line asking them to discuss this with a doctor is shown after your answer. Do not contradict it.\n\n")
            }
            append("They asked: ").append(request.request)
        }
        checkPermitted(request.facts.map { it.fact } + listOfNotNull(request.triggerText) + request.allowedFoodNames, permitted(request))
        return Prompts.chat(system, user)
    }

    /**
     * Everything the numeric guard may permit for an ANSWER: the figures, the row text, and the
     * question itself. The question is included because a number the person said ("in 2 rotis")
     * is theirs, not the model's; refusing an echo of it would fail correct answers constantly.
     */
    fun permitted(request: AnswerRequest): List<String> = buildList {
        request.figures.forEach { add(it.text) }
        request.facts.forEach { add(it.fact) }
        addAll(request.declaredConditions)
        request.context?.let { add(it) }
        add(request.question)
    }

    /**
     * Everything the numeric guard may permit for a RECOMMEND. Declared conditions and the
     * context are included for the same reason as the request text: "type 2 diabetes" in the
     * person's own words carries a 2 the model must be allowed to repeat.
     */
    fun permitted(request: RecommendRequest): List<String> = buildList {
        request.facts.forEach { add(it.fact) }
        request.triggerText?.let { add(it) }
        addAll(request.allowedFoodNames)
        addAll(request.declaredConditions)
        request.context?.let { add(it) }
        addAll(request.constraints)
        add(request.request)
    }

    private fun StringBuilder.appendFacts(facts: List<KnowledgeFact>) {
        if (facts.isEmpty()) {
            append("There are no facts on this topic in your notes.\n\n")
            return
        }
        append("Facts you may use, exactly as written:\n")
        facts.forEach { append("- ").append(it.fact).append('\n') }
        append('\n')
    }

    /** The same guard-drift check `Prompts.phrasing` makes: what the prompt shows, the guard must know. */
    private fun checkPermitted(shown: List<String>, permitted: List<String>) =
        check(permitted.containsAll(shown)) { "the prompt is showing text the numeric guard has not been given" }

    /** Generation budget for [intent]: one word, with room for the model to spell RECOMMEND in pieces. */
    const val INTENT_MAX_TOKENS = 4
    val INTENT_STOPS = listOf("\n", "</s>", "<|im_end|>")

    /** Generation budgets for [answer] and [recommend]. Three or four sentences; the prompt asks for fewer. */
    const val ANSWER_MAX_TOKENS = 120
    const val RECOMMEND_MAX_TOKENS = 160
    val CONVERSATION_STOPS = listOf("\n\n", "</s>", "<|im_end|>")
}

/** The four conversational intents of `0015`. Capture-shaped intents (scan, correct) are not spoken and are not routed here. */
enum class Intent {
    LOG, ANSWER, SUGGEST, RECOMMEND;

    companion object {
        /**
         * Reads the classifier's answer. Lenient about case, punctuation and trailing words,
         * strict about the label: the first alphabetic word must be one of the four, or the
         * result is null and the caller asks rather than guesses (spec 10.7). A prefix match on
         * the first three letters covers a model that stops mid-word at the token budget
         * ("RECOMM") without admitting anything that is not clearly one of the four.
         */
        fun parse(raw: String): Intent? {
            val word = raw.trim().takeWhile { it.isLetter() }.uppercase()
            if (word.length < 3) return null
            return entries.firstOrNull { it.name.startsWith(word) || word.startsWith(it.name) }
        }
    }
}

data class AnswerRequest(
    /** What they asked, as transcribed. */
    val question: String,
    val languageTag: String,
    /** Conditions the person declared, in their own words. The only conditions the model may name. */
    val declaredConditions: List<String>,
    /** Spec 4.2 context as a display string, e.g. "hostel student, canteen food, no kitchen". */
    val context: String?,
    /**
     * Their recent meals and lab values, each rendered by `ContextText` as one line with its
     * figures formatted, units and completeness included. Strings, never numbers.
     */
    val figures: List<DisplayFigure>,
    /** Retrieved rows. Their text is the model's only source of general nutrition claims. */
    val facts: List<KnowledgeFact>,
    /**
     * True when the caller will append the fixed doctor-referral line after the model's text:
     * the rules engine asked for one, or [SafetyLine.invitesClinicalJudgement] said the question
     * itself does. Defaulted so an existing caller is unchanged; the orchestrator sets it.
     */
    val referralFollows: Boolean = false,
)

data class RecommendRequest(
    /** What they asked, as transcribed. */
    val request: String,
    val languageTag: String,
    /** Conditions the person declared, in their own words. The only conditions the model may name. */
    val declaredConditions: List<String>,
    /** Spec 4.2 context as a display string, e.g. "hostel student, canteen food, no kitchen". */
    val context: String?,
    /** Diet type and allergies as "never suggest" lines, e.g. "meat, fish or eggs (vegetarian)". */
    val constraints: List<String>,
    /** The rules engine's trigger sentence, already rendered in the user's language. Null when nothing fired. */
    val triggerText: String?,
    val facts: List<KnowledgeFact>,
    /** Names the model may suggest, already filtered by the constraints. */
    val allowedFoodNames: List<String>,
    /** True when the caller will append the fixed doctor-referral line after the model's text. */
    val referralFollows: Boolean,
)
