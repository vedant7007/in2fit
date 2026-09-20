package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.data.knowledge.KnowledgeFact
import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Defects 2 to 4 of `0024`, landed red by design and green since the integrator's `3a32642`:
 * a verdict, a dose or a medication change is refused by `SafetyLine.prescribesOrJudges` beside
 * the other two checks; a condition word the request itself contains may be repeated; a
 * percentage does not license the same number as a mass. Defect 1, the engine-only referral, is
 * `ReferralDefectsTest` in `orchestration/`. They stay as the guard on the shipped path.
 */
class EngineDefectsTest {

    private class Scripted(private val response: String) : LlamaRuntime {
        override fun generate(prompt: String, maxTokens: Int, stop: List<String>) = response
        override fun close() = Unit
    }

    private fun fact(id: String, text: String) = KnowledgeFact(id, "t", setOf("x"), text, "Src", "https://s", "2026-09-20", "")

    private fun ask(question: String, declared: List<String> = emptyList(), facts: List<KnowledgeFact> = emptyList()) =
        AnswerRequest(question, "en-IN", declared, null, emptyList(), facts)

    // --- defect 2: a prescription or a verdict passes both existing checks ----------------------

    /**
     * The numeric guard permits the 7 because the person said it; the condition check finds no
     * condition word. The sentence is a clinical verdict and must not reach the screen.
     * `SafetyLine.prescribesOrJudges` is the third check; the fix is one line in `guarded()`.
     */
    @Test fun `a verdict on the person's own reading is refused`() = runBlocking {
        val out = LlamaCppLlmEngine(Scripted("A haemoglobin of 7 is dangerous; eat more greens.")).answer(ask("My haemoglobin is 7, is that dangerous?"))
        assertTrue("a verdict reached the screen: $out", out is Outcome.Unavailable)
    }

    @Test fun `a medication change is refused even when every number was given`() = runBlocking {
        val out = LlamaCppLlmEngine(Scripted("Yes, you can stop your medication if you eat this well."))
            .answer(ask("Should I stop my medication if I eat better?", declared = listOf("type 2 diabetes")))
        assertTrue("a medication change reached the screen: $out", out is Outcome.Unavailable)
    }

    @Test fun `a cure verdict on a declared condition is refused`() = runBlocking {
        val out = LlamaCppLlmEngine(Scripted("With all those greens your anaemia is cured."))
            .answer(ask("Has my anaemia gone away?", declared = listOf("anaemia")))
        assertTrue("a cure verdict reached the screen: $out", out is Outcome.Unavailable)
    }

    // --- defect 3: the condition check refuses words the request itself contains -----------------

    /**
     * The row is sourced and the prompt says "use these facts exactly as written". Refusing the
     * model for quoting it means a correct answer is lost. The rule the numeric guard already
     * applies is the fix: a word present in the request (a fact, a figure, the question, the
     * declared conditions) may be repeated.
     */
    @Test fun `a condition word quoted from a given fact is permitted`() = runBlocking {
        val row = fact("fat.unsat", "Unsaturated fats lower disease risk; saturated fats are best eaten in moderation.")
        val out = LlamaCppLlmEngine(Scripted("The notes say unsaturated fats lower disease risk, so prefer groundnut oil to ghee."))
            .answer(ask("is ghee bad for me", facts = listOf(row)))
        assertTrue("the model quoted a row it was told to quote and was refused: $out", out is Outcome.Ok)
    }

    @Test fun `the person's own question word may be repeated to decline it`() = runBlocking {
        val out = LlamaCppLlmEngine(Scripted("Whether a reading means anaemia is for a doctor to say against the range printed on your report."))
            .answer(ask("Is 9.8 haemoglobin anaemia?"))
        assertTrue("the answer declined to judge, using the person's word, and was refused: $out", out is Outcome.Ok)
    }

    /** The check must still hold where it should: a condition from nowhere in the request. */
    @Test fun `a condition word from nowhere in the request is still refused`() = runBlocking {
        val out = LlamaCppLlmEngine(Scripted("Dizziness after meals usually means diabetes."))
            .answer(ask("I feel dizzy after meals, what is wrong with me?"))
        assertTrue("an undeclared diagnosis reached the screen: $out", out is Outcome.Unavailable)
    }

    // --- defect 4: the numeric guard is unit-blind ---------------------------------------------

    /**
     * A row saying "14% to 18%" permitted "18 mg a day". A number is licensed with the unit it
     * was given: a percentage licenses a percentage, a mass a mass, and a bare number either.
     * This is the guard's one known hole in "every number traces to a row", stated as a target.
     */
    @Test fun `a percentage does not license the same number as a mass`() {
        val guard = DefaultNumericGuard()
        val permitted = listOf("Iron bioavailability is estimated at 14% to 18% for people who eat animal products.")
        assertNotNull("18% licensed 18 mg", guard.firstInventedNumber("Aim for 18 mg of iron a day.", permitted))
        assertNull("the same figure as a percentage is the given one", guard.firstInventedNumber("About 18% is absorbed.", permitted))
        assertNull("a mass licenses the same mass", guard.firstInventedNumber("Aim for 19 mg a day.", listOf("The allowance is 19 mg per day.")))
        assertNull("a bare number in the input licenses either reading", guard.firstInventedNumber("about 2 rotis", listOf("They said: 2 rotis")))
    }
}
