package io.github.vedant7007.katori.ml.llm

/**
 * The two prompts, and there are only two.
 *
 * SEPARATE ON PURPOSE (spec 11.6). One prompt doing both extraction and explanation degrades
 * both: the extraction half starts writing prose and the explanation half starts emitting JSON.
 * They share nothing here but the chat template.
 *
 * WHAT THE PHRASING PROMPT IS NOT ALLOWED TO CONTAIN. No typed numbers. Figures arrive as finished
 * display strings and go into the prompt as those strings. There is nothing numeric in the
 * phrasing prompt for the model to do arithmetic on, which is the second of the three defences in
 * the [LlmEngine] contract; [NumericGuard] is the third.
 *
 * Qwen 2.5's chat template is used because that is the model in `docs/decisions/0005`. Changing
 * model means changing these markers, which is why they are in one place.
 */
internal object Prompts {

    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"

    private fun chat(system: String, user: String): String = buildString {
        append(IM_START).append("system\n").append(system).append(IM_END).append('\n')
        append(IM_START).append("user\n").append(user).append(IM_END).append('\n')
        append(IM_START).append("assistant\n")
    }

    /**
     * Transcript in, JSON out.
     *
     * The instruction against inventing a quantity is stated twice and in the negative, because
     * that is the single field a model most wants to helpfully fill in. It is not the only thing
     * stopping it: the schema refuses a zero or a negative, and an unstated quantity carries
     * QUANTITY_INFERRED downstream and is shown to the person as correctable.
     *
     * @param retryOf the reason the previous response was refused, passed back so the re-ask is
     *   informed rather than a repeat of the same request. Null on the first attempt.
     */
    fun extraction(transcript: String, languageTag: String, retryOf: String? = null): String {
        val system = """
            You extract food items from what a person said about a meal. You output JSON and nothing else.

            Output exactly this shape:
            {"items":[{"name":"...","quantity":null,"unit":null,"method":null}]}

            Rules:
            - name: the food as the person said it. Keep their word. Do not translate it into another food.
            - quantity: a number ONLY if the person stated one. If they did not, use null.
            - Never guess a quantity. "I had dal" is quantity null, not 1.
            - unit: the unit as spoken, such as katori, plate, spoon, piece, g. Otherwise null.
            - method: fried, boiled, steamed, tempered, raw, if they said one. Otherwise null.
            - One entry per food. No extra fields. No commentary. No markdown.
        """.trimIndent()

        val user = buildString {
            if (retryOf != null) {
                append("Your previous answer was rejected: ").append(retryOf).append('\n')
                append("Answer again, as JSON only.\n\n")
            }
            append("Language: ").append(languageTag).append('\n')
            append("They said: ").append(transcript)
        }
        return chat(system, user)
    }

    /**
     * An already-decided result in, one sentence out.
     *
     * Every decision is made before this runs: the rules engine chose the constraints and ranked
     * the candidates, and the database produced the figures. This only puts words around them.
     */
    fun phrasing(request: PhrasingRequest, permitted: List<String>): String {
        val system = """
            You write one short, plain sentence for a person about the meal they just logged.

            Rules:
            - Use ONLY the numbers given to you below, exactly as written. Never calculate, total,
              convert or estimate a number. If a number is not in the list, it must not appear.
            - Suggest only foods from the allowed list. Do not name any other food.
            - Do not name a disease, diagnose, or tell them to take anything.
            - No greeting, no sign-off, no markdown. One or two sentences at most.
        """.trimIndent()

        val user = buildString {
            append("Language to reply in: ").append(request.languageTag).append("\n\n")
            if (request.figures.isNotEmpty()) {
                append("Figures you may use, exactly as written:\n")
                request.figures.forEach { append("- ").append(it.text).append('\n') }
                append('\n')
            }
            request.evaluation.trigger?.let {
                append("What changed and why:\n").append(it.text).append("\n\n")
            }
            if (request.allowedFoodNames.isNotEmpty()) {
                append("Foods you may suggest, and no others:\n")
                request.allowedFoodNames.forEach { append("- ").append(it).append('\n') }
                append('\n')
            }
            append("Write the sentence.")
        }
        // `permitted` is the same list the numeric guard will check the answer against. It is a
        // parameter rather than something rebuilt here so the two can never drift apart: a figure
        // the prompt shows but the guard does not know about would fail every time.
        check(permitted.containsAll(request.figures.map { it.text })) {
            "the phrasing prompt is showing a figure the numeric guard has not been given"
        }
        return chat(system, user)
    }
}
