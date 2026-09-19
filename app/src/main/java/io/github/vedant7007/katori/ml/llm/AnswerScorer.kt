package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.data.food.FoodTextMatching

/**
 * What a good ANSWER or RECOMMEND contains, as five checks a machine can make on the text.
 *
 * WHY THIS EXISTS. Nothing scored an answer. "High accuracy" cannot be claimed for prose the
 * way WRONG FOOD can be counted for the matcher, so this pins down what the pitch means by a
 * good answer and counts it: the claim traces to a knowledge row, it uses the person's own
 * context, it respects their declared constraints, it does not diagnose or prescribe, and it is
 * actionable. Five booleans, no weighting, no percentage of "quality"; the number is how many of
 * the five hold, and each one that fails is named.
 *
 * WHAT IT IS NOT. Not a judge of truth or tone. A fluent wrong sentence with the right numbers
 * passes; a correct sentence that quotes nothing fails "traces". It is deliberately mechanical
 * so that the same answer scores the same every run, on the JVM against authored answers and on
 * the phone against the model's. The authored set is circular (`0024`); the model's output on
 * the phone is the measurement, and a 5 or 6 speaker recorded set is a small sample.
 *
 * THE PAIR THAT PROVES THE PITCH. The same question answered with no context and with the
 * person's logged meals and lab values. [usesContext] is null when no context was given, so
 * the generic request cannot score it at all; the grounded request can, and a generic answer to
 * the grounded request scores it false. That difference is the demo's argument in one screen.
 */
data class AnswerScore(
    /** Every number in the answer was in the permitted set, and the answer shares a content word with a row it was given. */
    val tracesToRows: Boolean,
    /** Null when the request carried no context to use. Otherwise: the answer mentions something from the figures, the context line, a declared condition or the trigger. */
    val usesContext: Boolean?,
    /** Null when there were no constraints. Otherwise no constrained food is named. */
    val respectsConstraints: Boolean?,
    /** [SafetyLine.prescribesOrJudges] finds nothing. (The undeclared-condition check is the engine's and runs before an answer reaches here.) */
    val noJudgement: Boolean,
    /** ANSWER: quotes a figure, or says plainly that it has none. RECOMMEND: names an allowed food. */
    val actionable: Boolean,
) {
    private val applicable get() = listOfNotNull(tracesToRows, usesContext, respectsConstraints, noJudgement, actionable)
    /** Criteria that held, over those that applied. */
    val held: Int get() = applicable.count { it }
    val outOf: Int get() = applicable.size
    val failed: List<String> get() = buildList {
        if (!tracesToRows) add("traces")
        if (usesContext == false) add("context")
        if (respectsConstraints == false) add("constraints")
        if (!noJudgement) add("judgement")
        if (!actionable) add("actionable")
    }
    override fun toString() = "$held/$outOf" + if (failed.isEmpty()) "" else " (failed: ${failed.joinToString()})"
}

object AnswerScorer {

    private val guard = DefaultNumericGuard()

    fun score(request: AnswerRequest, answer: String): AnswerScore {
        val permitted = ConversationPrompts.permitted(request)
        val given = request.figures.map { it.text } + listOfNotNull(request.context) + request.declaredConditions
        return AnswerScore(
            tracesToRows = guard.firstInventedNumber(answer, permitted) == null && sharesContent(answer, request.facts.map { it.fact }),
            usesContext = if (given.isEmpty()) null else sharesContent(answer, given, minLength = 4, except = request.question) || sharesNumber(answer, request.figures.map { it.text }),
            respectsConstraints = null,
            noJudgement = SafetyLine.prescribesOrJudges(answer) == null,
            // With figures on file the answer quotes one or says it has none; with none, a general
            // question is answered from the rows, and sharing their content is what "answered" means.
            actionable = if (request.figures.isNotEmpty()) answer.any { it.isDigit() } || NO_FIGURE.any { answer.lowercase().contains(it) }
                         else sharesContent(answer, request.facts.map { it.fact }) || NO_FIGURE.any { answer.lowercase().contains(it) },
        )
    }

    fun score(request: RecommendRequest, answer: String): AnswerScore {
        val permitted = ConversationPrompts.permitted(request)
        val given = listOfNotNull(request.context, request.triggerText) + request.declaredConditions
        val forbidden = request.constraints.flatMap(::constrainedFoods)
        val normalised = FoodTextMatching.normalise(answer)
        return AnswerScore(
            tracesToRows = guard.firstInventedNumber(answer, permitted) == null && sharesContent(answer, request.facts.map { it.fact }),
            usesContext = if (given.isEmpty()) null else sharesContent(answer, given, minLength = 4, except = request.request) || sharesNumber(answer, listOfNotNull(request.triggerText)),
            respectsConstraints = if (forbidden.isEmpty()) null else forbidden.none { FoodTextMatching.containsAsWords(normalised, it) },
            noJudgement = SafetyLine.prescribesOrJudges(answer) == null,
            // With an allowed list, the answer names a food from it. With none (no profile, no
            // ranked candidates), naming a food would be inventing one, so answering from the
            // rows is what actionable means.
            actionable = if (request.allowedFoodNames.isNotEmpty()) request.allowedFoodNames.any { FoodTextMatching.containsAsWords(normalised, FoodTextMatching.normalise(it)) }
                         else sharesContent(answer, request.facts.map { it.fact }),
        )
    }

    /**
     * True when [answer] and any of [sources] share a word of at least [minLength] letters that
     * is not a stop word and not in [except]. A proxy for "drawn from", and stated as one.
     * [except] is the question: a word the person asked about ("protein") appearing in both the
     * figure line and the answer is the topic, not evidence that the figure was used.
     */
    private fun sharesContent(answer: String, sources: List<String>, minLength: Int = 5, except: String = ""): Boolean {
        if (sources.isEmpty()) return false
        val topic = FoodTextMatching.normalise(except).split(' ').toSet()
        val words = FoodTextMatching.normalise(answer).split(' ').filter { it.length >= minLength && it !in STOP && it !in topic }.toSet()
        return sources.any { src -> FoodTextMatching.normalise(src).split(' ').any { it.length >= minLength && it !in STOP && it in words } }
    }

    /** True when any figure in [sources] is quoted in [answer]. Digit runs, so "9.8" matches "9.8" and not "9". */
    private fun sharesNumber(answer: String, sources: List<String>): Boolean {
        val quoted = NUMBER.findAll(answer).map { it.value }.toSet()
        return sources.any { src -> NUMBER.findAll(src).any { it.value in quoted } }
    }
    private val NUMBER = Regex("[0-9]+(?:[.][0-9]+)?")

    /**
     * The foods a constraint line rules out. The line is the string table's ("meat, fish or
     * eggs (vegetarian)"), so its own words count, plus the everyday names those words stand
     * for. Small on purpose; a constraint the scorer does not know scores null, not true.
     */
    private fun constrainedFoods(constraint: String): List<String> {
        val own = FoodTextMatching.normalise(constraint).split(' ').filter { it.length >= 3 && it !in STOP && it !in CONSTRAINT_NOISE }
        val expanded = when {
            constraint.contains("vegan", ignoreCase = true) -> DIET_VEGAN
            constraint.contains("vegetarian", ignoreCase = true) -> DIET_VEGETARIAN
            constraint.contains("eggetarian", ignoreCase = true) -> DIET_EGGETARIAN
            else -> emptyList()
        }
        return (own + expanded).distinct()
    }

    private val NO_FIGURE = listOf("do not have", "don't have", "not known", "no figure", "cannot say", "can't say", "not logged", "no record")
    private val STOP = setOf("their", "there", "these", "those", "which", "about", "would", "could", "should", "because", "after", "before", "with", "from", "that", "this", "have", "they", "them", "your", "more", "than", "when", "what", "some", "much", "many", "also", "only", "every", "other", "being", "meals", "meal", "today", "week", "eat", "eating", "food", "foods", "diet")
    private val CONSTRAINT_NOISE = setOf("vegetarian", "vegan", "eggetarian", "allergy", "any", "or", "and")
    private val DIET_VEGETARIAN = listOf("meat", "chicken", "mutton", "goat", "beef", "pork", "fish", "prawn", "prawns", "shrimp", "egg", "eggs", "kodi", "chepa", "royyalu", "mamsam", "guddu", "anda")
    private val DIET_VEGAN = DIET_VEGETARIAN + listOf("milk", "curd", "yoghurt", "yogurt", "paneer", "ghee", "butter", "buttermilk", "cheese", "paalu", "perugu", "majjiga", "doodh", "dahi")
    private val DIET_EGGETARIAN = DIET_VEGETARIAN - setOf("egg", "eggs", "guddu", "anda")
}
