package io.github.vedant7007.katori.ml.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The deterministic router, measured on the authored 53-case set.
 *
 * TWO NUMBERS MUST BE ZERO: intents the words decided wrongly, and non-LOG sentences the words
 * decided as LOG. The third number, how many sentences still go to the model, is reported and
 * asserted only at a ceiling, because every one of them is a model call on the hot path and a
 * chance for the model to be wrong. The set is circular (same hand as the markers); the number
 * is a regression guard, not accuracy. The recorded speakers replace it: a small sample.
 */
class IntentRouterTest {

    private data class Row(val utterance: String, val expect: Intent, val note: String)

    private fun rows(): List<Row> {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        return File(dir, "data-authoring/intent-test-set.csv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split(",") }
            .map { Row(it[0].trim(), Intent.valueOf(it[1].trim()), it.getOrElse(3) { "" }.trim()) }
    }

    @Test fun `measure the router and prove the words never decide wrongly`() {
        val all = rows()
        var decided = 0; var toModel = 0
        val wrong = mutableListOf<String>()
        val logOnQuestion = mutableListOf<String>()
        val report = StringBuilder("=== INTENT ROUTER, authored 53-case set ===\n")
        val perIntent = Intent.entries.associateWith { intArrayOf(0, 0) } // decided right, sent to model
        for (r in all) {
            when (val d = IntentRouter.decide(r.utterance)) {
                is IntentRouter.Decision.Decided -> {
                    decided++
                    if (d.intent == r.expect) perIntent[r.expect]!![0]++
                    else {
                        wrong += "'${r.utterance}' -> ${d.intent} (${d.evidence}), expected ${r.expect}"
                        if (d.intent == Intent.LOG) logOnQuestion += r.utterance
                    }
                }
                is IntentRouter.Decision.AskModel -> {
                    toModel++; perIntent[r.expect]!![1]++
                    report.appendLine("  model: '${r.utterance}'  [${d.why}; allowed ${d.allowed}]  expected ${r.expect}")
                    assertTrue("'${r.utterance}': the model could not even give the right answer, ${r.expect} is not allowed", r.expect in d.allowed)
                }
            }
        }
        report.appendLine("  cases                      ${all.size}")
        report.appendLine("  decided by the words       $decided   (${"%.1f".format(decided * 100.0 / all.size)} %)  <- no model call")
        report.appendLine("  decided WRONGLY            ${wrong.size}   <- must be zero")
        report.appendLine("  question decided as LOG    ${logOnQuestion.size}   <- must be zero: this writes a meal")
        report.appendLine("  sent to the model          $toModel")
        perIntent.forEach { (i, c) -> report.appendLine("    ${i.name.padEnd(10)} decided ${c[0]}   to model ${c[1]}") }
        wrong.forEach { report.appendLine("  WRONG: $it") }
        println(report)
        assertEquals("the words decided an intent wrongly:\n" + wrong.joinToString("\n"), 0, wrong.size)
        assertEquals("a question was decided as LOG", 0, logOnQuestion.size)
        assertTrue("more than a third of the set still goes to the model: $toModel of ${all.size}", toModel * 3 <= all.size)
    }

    /** The dangerous misroute the phone run produced, made impossible: a LOG verdict on a question is refused. */
    @Test fun `a model LOG on a sentence with a question marker is refused`() {
        for (q in listOf("how many rotis have I eaten today", "was there any B12 in what I ate today", "kal kya khaya tha", "what did I eat on Tuesday", "I had rice and dal, was that enough iron?")) {
            assertNull("'$q': LOG from the model reached the diary", IntentRouter.accept(Intent.LOG, q))
        }
    }

    @Test fun `a model verdict outside what the words allow is refused, inside it is accepted`() {
        // No evidence at all: the model may say anything.
        assertEquals(Intent.LOG, IntentRouter.accept(Intent.LOG, "a plate of vegetable biryani and some raita"))
        assertEquals(Intent.SUGGEST, IntentRouter.accept(Intent.SUGGEST, "tea with two biscuits"))
        // The words decided: the model's opinion does not override them.
        assertEquals(Intent.ANSWER, IntentRouter.accept(Intent.RECOMMEND, "how much protein did I eat today"))
        assertEquals(Intent.RECOMMEND, IntentRouter.accept(Intent.ANSWER, "what should I eat for more iron"))
    }

    @Test fun `the brief's four examples are decided by the words alone`() {
        assertEquals(Intent.LOG, (IntentRouter.decide("I ate paneer curry with two spoons of oil and two cucumbers") as IntentRouter.Decision.Decided).intent)
        assertEquals(Intent.ANSWER, (IntentRouter.decide("How much protein did I eat today") as IntentRouter.Decision.Decided).intent)
        assertEquals(Intent.SUGGEST, (IntentRouter.decide("I'm having rice and palak paneer, what should I add") as IntentRouter.Decision.Decided).intent)
        assertEquals(Intent.RECOMMEND, (IntentRouter.decide("I have anaemia, what should I eat to increase iron") as IntentRouter.Decision.Decided).intent)
    }

    /**
     * Written AFTER the router, against it, which is the nearest thing to a held-out set the same
     * hand can produce. A wrong decision here fails; a model referral is allowed and listed.
     */
    @Test fun `sentences written to break the router are decided right or sent to the model`() {
        val cases = mapOf(
            "I want to eat something for iron" to Intent.RECOMMEND,
            "suggest a breakfast for me" to Intent.RECOMMEND,
            "can I have sweets with diabetes" to Intent.RECOMMEND,
            "what should I eat today" to Intent.RECOMMEND,
            "aaj kya khaun" to Intent.RECOMMEND,
            "is curd good for me at night" to Intent.RECOMMEND,
            "how much iron is in spinach" to Intent.ANSWER,
            "did I eat too much today" to Intent.ANSWER,
            "what is in my diary for today" to Intent.ANSWER,
            "what was my sodium today" to Intent.ANSWER,
            "I'm eating idli now what should I add" to Intent.SUGGEST,
            "log two rotis and dal" to Intent.LOG,
            "record my lunch rice and dal" to Intent.LOG,
            "sirf ek roti khayi" to Intent.LOG,
            "kya main chai pi sakta hoon" to Intent.RECOMMEND,
            "I ate too much rice yesterday, what should I eat today" to Intent.RECOMMEND,
            "abhi kya khaun" to Intent.RECOMMEND,
        )
        val toModel = mutableListOf<String>()
        for ((q, expect) in cases) {
            when (val d = IntentRouter.decide(q)) {
                is IntentRouter.Decision.Decided -> assertEquals("'$q' (${d.evidence})", expect, d.intent)
                is IntentRouter.Decision.AskModel -> { toModel += "'$q' [${d.why}]"; assertTrue("'$q': $expect not allowed", expect in d.allowed) }
            }
        }
        println("=== held-out, to the model: ${toModel.size} of ${cases.size} ===" + toModel.joinToString("") { "\n  $it" })
    }

    /** Hindi is a demo language: these must be decided, not modelled. */
    @Test fun `roman Hindi questions are decided by the words`() {
        assertEquals(Intent.ANSWER, (IntentRouter.decide("aaj maine kitna protein khaya") as IntentRouter.Decision.Decided).intent)
        assertEquals(Intent.ANSWER, (IntentRouter.decide("kal kya khaya tha") as IntentRouter.Decision.Decided).intent)
        assertEquals(Intent.SUGGEST, (IntentRouter.decide("main abhi dal chawal kha raha hoon kya add karun") as IntentRouter.Decision.Decided).intent)
        assertEquals(Intent.RECOMMEND, (IntentRouter.decide("mujhe iron ke liye kya khana chahiye") as IntentRouter.Decision.Decided).intent)
        assertEquals(Intent.LOG, (IntentRouter.decide("maine do roti aur thoda chawal khaya") as IntentRouter.Decision.Decided).intent)
    }
}
