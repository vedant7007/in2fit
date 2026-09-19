package io.github.vedant7007.katori.ml.llm

/**
 * The two prompts of the two-path contract: extraction and phrasing.
 *
 * SEPARATE ON PURPOSE (spec 11.6). One prompt doing both extraction and explanation degrades
 * both: the extraction half starts writing prose and the explanation half starts emitting JSON.
 * They share nothing here but the chat template.
 *
 * `docs/decisions/0015` adds a third path, composed from knowledge rows rather than from an
 * already-decided result. Its prompts (intent routing, ANSWER, RECOMMEND) live in
 * [ConversationPrompts] and share only [chat] with this file, for the same reason: the shapes
 * must not bleed into each other.
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

    /** The Qwen 2.5 chat envelope. Internal so [ConversationPrompts] reuses the markers rather than copying them. */
    internal fun chat(system: String, user: String): String = buildString {
        append(IM_START).append("system\n").append(system).append(IM_END).append('\n')
        append(IM_START).append("user\n").append(user).append(IM_END).append('\n')
        append(IM_START).append("assistant\n")
    }

    /**
     * Transcript in, JSON out.
     *
     * THIS PROMPT WAS SHORTENED TO BUY LATENCY AND THE SHORTENING WAS REVERTED, BECAUSE IT
     * EXTRACTED WORSE. Recorded here so nobody spends the same day on it twice.
     *
     * The case for trying: at 207 tokens this prompt cost 4.3 s of a 10.6 s extraction round
     * trip on the test device, and every token is paid on every meal a person logs. A 162-token
     * version cut the prompt phase to about 3.3 s, which is a real saving.
     *
     * What it cost, measured on the device over five transcripts:
     *   - "I ate idli and sambar" came back with idli only. The model dropped a food it had been
     *     told about. That case passes with the long prompt.
     *   - "200 ml of milk and one boiled egg" came back as one item carrying "method" twice, which
     *     the strict reader refused, and the egg was gone.
     * Two of five cases failed. A wrong or missing food is the failure this whole package is
     * built to avoid, and 1 s of prompt time does not buy it.
     *
     * WHAT WAS CUT AND IS NOW BACK: the opening sentence naming the job, the "Rules:" header and
     * its bullets, the worked example, the second statement of the quantity rule, and "No
     * markdown". That last one alone is worth knowing about: without it the model wrapped its
     * answer in a ``` fence and pretty-printed inside it, which cost generated tokens and, at the
     * time, collided with a stop sequence badly enough to fail extraction outright. That
     * collision is fixed in LlamaCppLlmEngine and is no longer load-bearing, but the instruction
     * stays because fenced pretty-printed JSON is still more tokens for nothing.
     *
     * TWO LINES ARE NEW, and they are the only thing kept from the experiment, because they
     * address failures it exposed: "Every food they named gets an entry" and "Never repeat a
     * field".
     *
     * THE NULL FIELDS STAY, AND THAT WAS ALSO MEASURED. Asking for `{"items":[{"name":"..."}]}`
     * and telling the model to leave quantity, unit and method OUT when nothing was said is the
     * obvious way to cut generated tokens, which is where the time is, and [ExtractionJson]
     * accepts absent optional fields with a test to prove it. It was tried. The correctness
     * cases passed, and then the SAME transcript was refused on the next test with "'quantity'
     * is not a number", at a different thread count, because the model emitted the quantity as a
     * string. Valid at eight threads and invalid at four is not a prompt that works; it is a
     * prompt sitting on a decision boundary that floating-point accumulation order can push it
     * over. Spelling the nulls out keeps the model on the shape it was shown. Anyone retrying
     * this measures across BOTH thread counts before believing it.
     *
     * WHAT MUST NOT BE CUT, because each line is the prompt half of a defence that the rest of
     * this package enforces:
     *   - JSON and nothing else, which is what ExtractionJson can refuse cleanly
     *   - the name as said, never translated into another food, which is the wrong-food guard
     *   - a quantity only if stated, which spec 13.4 requires and QUANTITY_INFERRED carries
     *   - no other fields, which the strict reader rejects
     *   - the shape itself
     * Shortening this is a safety change, not a latency change. If it is tried again, it is
     * tried against the correctness cases in HardwareProbeTest and the numbers are reported
     * together.
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
            - One entry per food. Every food they named gets an entry.
            - No extra fields. Never repeat a field. No commentary. No markdown.
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
            request.triggerText?.let {
                append("What changed and why:\n").append(it).append("\n\n")
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
