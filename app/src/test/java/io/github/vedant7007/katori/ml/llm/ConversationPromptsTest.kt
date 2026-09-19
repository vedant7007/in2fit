package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.data.knowledge.KnowledgeFact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The conversational prompts and the intent parser, tested as text.
 *
 * WHAT THIS DOES NOT TEST. The model. No prompt here has run on the phone, and no claim about
 * what the model answers, how fast, or how often it picks the right intent follows from any
 * test in this file. The authored case set in `data-authoring/intent-test-set.csv` is checked
 * for shape only; its score is a device measurement (COORDINATION.md).
 *
 * What it does test is what can be wrong without a model: a prompt that omits a figure it was
 * given, a permitted list that does not cover what the prompt shows (the guard would then fail
 * every correct answer), a parser that admits "SUGAR" as SUGGEST, a case file with a label that
 * is not one of the four.
 */
class ConversationPromptsTest {

    private fun fact(id: String, text: String, vararg tags: String) =
        KnowledgeFact(id, "t", tags.toSet(), text, "Src", "https://s", "2026-09-20", "")

    private val ironFacts = listOf(
        fact("iron.rda_india", "The ICMR-NIN 2020 recommended dietary allowance for iron is 19 mg per day for adult men and 29 mg per day for adult women.", "iron"),
        fact("iron.tea_timing", "Beverages like tea bind dietary iron and make it unavailable, so they are best avoided for at least an hour before, during or soon after a meal.", "tea", "iron"),
    )

    // --- the intent parser ---------------------------------------------------------------------

    @Test fun `the four labels parse in any case with trailing noise`() {
        assertEquals(Intent.LOG, Intent.parse("LOG"))
        assertEquals(Intent.LOG, Intent.parse("log"))
        assertEquals(Intent.LOG, Intent.parse(" Log.\n"))
        assertEquals(Intent.ANSWER, Intent.parse("ANSWER\nThey asked about"))
        assertEquals(Intent.SUGGEST, Intent.parse("SUGGEST - they want to add"))
        assertEquals(Intent.RECOMMEND, Intent.parse("Recommend"))
    }

    @Test fun `a label cut off by the token budget still parses`() {
        assertEquals(Intent.RECOMMEND, Intent.parse("RECOMM"))
        assertEquals(Intent.ANSWER, Intent.parse("ANS"))
        assertEquals(Intent.RECOMMEND, Intent.parse("recommendation"))
    }

    /** The caller asks rather than guesses: anything that is not clearly one of the four is null. */
    @Test fun `anything that is not one of the four is null`() {
        assertNull(Intent.parse(""))
        assertNull(Intent.parse("   "))
        assertNull(Intent.parse("LO"))
        assertNull(Intent.parse("SUGAR"))
        assertNull(Intent.parse("Yes"))
        assertNull(Intent.parse("1"))
        assertNull(Intent.parse("The intent is LOG"))
    }

    // --- the intent prompt ---------------------------------------------------------------------

    @Test fun `the intent prompt names the four labels, asks for one word and carries the utterance`() {
        val p = ConversationPrompts.intent("nenu annam pappu tinnanu", "te-IN")
        Intent.entries.forEach { assertTrue("missing ${it.name}", p.contains(it.name)) }
        assertTrue(p.contains("one word"))
        assertTrue(p.contains("nenu annam pappu tinnanu"))
        assertTrue(p.contains("te-IN"))
        assertTrue("the classifier must not ask for JSON", !p.contains("JSON"))
    }

    /**
     * A size ceiling as a proxy for prompt tokens, which this test cannot count. Generation is
     * the expensive side (0014) but the prompt is paid on every utterance, and it is easy to
     * grow a classifier prompt one helpful sentence at a time. Raise this only with a device
     * measurement that says the extra words bought accuracy.
     */
    @Test fun `the intent prompt stays short`() {
        val p = ConversationPrompts.intent("I had two rotis and a katori of dal", "en-IN")
        assertTrue("classifier prompt is ${p.length} chars", p.length < 800)
    }

    // --- the authored case set ---------------------------------------------------------------

    @Test fun `the intent case set is well formed and covers each intent`() {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        val f = File(dir, "data-authoring/intent-test-set.csv")
        check(f.isFile) { "case set not found at ${f.absolutePath}" }
        val rows = f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split(",") }
        assertTrue("case set looks empty: ${rows.size}", rows.size >= 40)
        val utterances = rows.map { it[0].trim() }
        assertEquals("duplicate utterances", utterances.size, utterances.toSet().size)
        val counts = rows.groupingBy { Intent.valueOf(it[1].trim()) }.eachCount()
        Intent.entries.forEach { assertTrue("fewer than 8 cases for $it: ${counts[it]}", (counts[it] ?: 0) >= 8) }
        // Every expected label round-trips through the parser the way the model's answer would.
        rows.forEach { assertEquals(it[1].trim(), Intent.parse(it[1].trim())!!.name) }
    }

    // --- ANSWER ------------------------------------------------------------------------------

    private fun answer(figures: List<String> = listOf("Protein today: at least 42 g (two items had no protein value)"), facts: List<KnowledgeFact> = ironFacts) =
        AnswerRequest(question = "how much protein did I eat today", languageTag = "te-IN", declaredConditions = emptyList(), context = null, figures = figures.map(::DisplayFigure), facts = facts)

    @Test fun `the answer prompt shows every figure and fact verbatim, and the question`() {
        val r = answer()
        val p = ConversationPrompts.answer(r)
        r.figures.forEach { assertTrue(p.contains(it.text)) }
        r.facts.forEach { assertTrue(p.contains(it.fact)) }
        assertTrue(p.contains(r.question))
        assertTrue(p.contains("te-IN"))
        assertTrue("answer must not ask for JSON", !p.contains("JSON"))
    }

    @Test fun `the answer prompt carries the referral notice only when the caller will append the referral`() {
        val r = answer()
        assertTrue(!ConversationPrompts.answer(r).contains("discuss this with a doctor"))
        assertTrue(ConversationPrompts.answer(r.copy(referralFollows = true)).contains("discuss this with a doctor"))
    }

    @Test fun `the answer prompt says when there are no figures rather than leaving a gap`() {
        val p = ConversationPrompts.answer(answer(figures = emptyList(), facts = emptyList()))
        assertTrue(p.contains("no figures"))
        assertTrue(p.contains("no facts"))
    }

    /** The guard is Rao's; this only proves the permitted list built here covers what the prompt shows. */
    @Test fun `the answer guard permits the figures and facts and refuses an invention`() {
        val r = answer()
        val permitted = ConversationPrompts.permitted(r)
        val guard = DefaultNumericGuard()
        assertNull(guard.firstInventedNumber("You had at least 42 g of protein today; the allowance is 29 mg of iron for women.", permitted))
        assertEquals("55", guard.firstInventedNumber("You had about 55 g of protein today.", permitted))
    }

    @Test fun `a number the person said is permitted, because it is theirs`() {
        val r = AnswerRequest("how much protein is in 2 rotis", "en-IN", emptyList(), null, emptyList(), emptyList())
        assertNull(DefaultNumericGuard().firstInventedNumber("I do not have a figure for 2 rotis.", ConversationPrompts.permitted(r)))
    }

    // --- RECOMMEND ---------------------------------------------------------------------------

    private fun recommend(referral: Boolean = false, trigger: String? = null) = RecommendRequest(
        request = "I have anaemia, what should I eat to increase iron",
        languageTag = "te-IN",
        declaredConditions = listOf("anaemia", "type 2 diabetes"),
        context = "hostel student, canteen food, no kitchen",
        constraints = listOf("meat, fish or eggs (vegetarian)", "peanuts (allergy)"),
        triggerText = trigger,
        facts = ironFacts,
        allowedFoodNames = listOf("thotakura", "sprouted moong", "guava"),
        referralFollows = referral,
    )

    @Test fun `the recommend prompt shows the declared conditions, constraints, foods and facts`() {
        val r = recommend()
        val p = ConversationPrompts.recommend(r)
        r.declaredConditions.forEach { assertTrue(p.contains(it)) }
        r.constraints.forEach { assertTrue("constraint must appear as a never-suggest line", p.contains("Never suggest: $it")) }
        r.allowedFoodNames.forEach { assertTrue(p.contains(it)) }
        r.facts.forEach { assertTrue(p.contains(it.fact)) }
        assertTrue(p.contains(r.context!!))
        assertTrue(p.contains(r.request))
    }

    @Test fun `the referral notice appears only when the caller will append the referral`() {
        assertTrue(ConversationPrompts.recommend(recommend(referral = true)).contains("discuss this with a doctor"))
        assertTrue(!ConversationPrompts.recommend(recommend(referral = false)).contains("discuss this with a doctor"))
    }

    @Test fun `a lab trigger is shown as written and permitted by the guard`() {
        val trigger = "Your report from 12 September shows haemoglobin at 9.8 g/dL, below the 12.0 printed on it."
        val r = recommend(trigger = trigger)
        assertTrue(ConversationPrompts.recommend(r).contains(trigger))
        assertNull(DefaultNumericGuard().firstInventedNumber("Your report shows 9.8 g/dL, below the 12.0 printed on it, so thotakura is a good add.", ConversationPrompts.permitted(r)))
    }

    /** "type 2 diabetes" carries a 2 the model must be allowed to repeat, because the person said it. */
    @Test fun `a declared condition's own digits are permitted`() {
        assertNull(DefaultNumericGuard().firstInventedNumber("Since you told us about type 2 diabetes, sprouted moong is a good choice.", ConversationPrompts.permitted(recommend())))
        assertEquals("450", DefaultNumericGuard().firstInventedNumber("That would be about 450 kcal.", ConversationPrompts.permitted(recommend())))
    }

    @Test fun `with no allowed food the prompt says so rather than inviting an invented one`() {
        val r = recommend().copy(allowedFoodNames = emptyList())
        assertTrue(ConversationPrompts.recommend(r).contains("no food you may suggest by name"))
    }

    @Test fun `the conversational prompts are not the extraction prompt`() {
        val p = ConversationPrompts.intent("x", "en") + ConversationPrompts.answer(answer()) + ConversationPrompts.recommend(recommend())
        assertTrue(!p.contains("\"items\""))
    }
}
