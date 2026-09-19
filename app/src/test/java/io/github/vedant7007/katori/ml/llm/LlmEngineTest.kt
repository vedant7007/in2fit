package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.domain.RuleEvaluation
import io.github.vedant7007.katori.domain.RuleId
import io.github.vedant7007.katori.domain.RuleIds
import io.github.vedant7007.katori.domain.TriggerStatement
import io.github.vedant7007.katori.domain.TriggerTemplate
import io.github.vedant7007.katori.domain.Evidence
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The LLM path, tested against a scripted runtime rather than a model.
 *
 * WHY SCRIPTED. Every failure worth defending against here is a specific bad output: truncated
 * JSON, a markdown fence, an invented quantity, a fabricated calorie figure. Waiting for a real
 * model to produce each one by chance is not testing, it is hoping. A scripted runtime produces
 * them on demand and the same way every run.
 *
 * WHAT THIS DOES NOT TEST. Anything native. No claim about model load, speed, memory or output
 * quality on a phone follows from any test in this file.
 */
class LlmEngineTest {

    /** Returns the scripted responses in order, and records the prompts it was given. */
    private class ScriptedRuntime(vararg responses: String) : LlamaRuntime {
        private val queue = ArrayDeque(responses.toList())
        val prompts = mutableListOf<String>()
        var closed = false

        override fun generate(prompt: String, maxTokens: Int, stop: List<String>): String {
            prompts += prompt
            return queue.removeFirstOrNull() ?: error("the engine asked more times than the script allows")
        }
        override fun close() { closed = true }
    }

    private fun engine(vararg responses: String) = ScriptedRuntime(*responses).let { it to LlamaCppLlmEngine(it) }

    private fun request(transcript: String = "two rotis and a katori of dal", reasks: Int = 2) =
        ExtractionRequest(transcript = transcript, languageTag = "te-IN", maxReasks = reasks)

    // --- extraction: the good path ---------------------------------------------------------

    @Test fun `well formed JSON is extracted as stated`() = runBlocking {
        val (_, e) = engine("""{"items":[{"name":"roti","quantity":2,"unit":"piece","method":null},{"name":"dal","quantity":1,"unit":"katori","method":null}]}""")
        val r = e.extract(request())
        assertTrue("$r", r is Outcome.Ok)
        val items = (r as Outcome.Ok).value.items
        assertEquals(2, items.size)
        assertEquals("roti", items[0].name)
        assertEquals(2.0, items[0].quantity!!, 0.0)
        assertEquals("katori", items[1].unit)
    }

    @Test fun `an unstated quantity stays null and is never filled in`() = runBlocking {
        val (_, e) = engine("""{"items":[{"name":"dal","quantity":null,"unit":null,"method":null}]}""")
        val items = ((e.extract(request("I had dal")) as Outcome.Ok).value).items
        assertNull("an absent quantity must stay absent", items[0].quantity)
    }

    @Test fun `absent optional fields are the same as null`() = runBlocking {
        val (_, e) = engine("""{"items":[{"name":"dal"}]}""")
        val items = ((e.extract(request()) as Outcome.Ok).value).items
        assertEquals("dal", items[0].name)
        assertNull(items[0].quantity)
        assertNull(items[0].unit)
    }

    @Test fun `a code fence around the whole answer is an envelope and is removed`() = runBlocking {
        val (_, e) = engine("```json\n{\"items\":[{\"name\":\"idli\",\"quantity\":3,\"unit\":\"piece\"}]}\n```")
        val r = e.extract(request("three idlis"))
        assertTrue("$r", r is Outcome.Ok)
        assertEquals(3.0, ((r as Outcome.Ok).value).items[0].quantity!!, 0.0)
    }

    // --- extraction: the refusals -----------------------------------------------------------

    @Test fun `a malformed answer is re-asked and the good one is taken`() = runBlocking {
        val (rt, e) = engine(
            """{"items":[{"name":"roti",""",
            """{"items":[{"name":"roti","quantity":2,"unit":"piece"}]}""",
        )
        val r = e.extract(request())
        assertTrue("$r", r is Outcome.Ok)
        assertEquals(2, rt.prompts.size)
        assertTrue("the re-ask must say what was wrong with the last answer",
            rt.prompts[1].contains("previous answer was rejected"))
    }

    @Test fun `when the re-ask budget is spent the call fails rather than salvaging`() = runBlocking {
        val (rt, e) = engine("not json", "still not json", "nope")
        val r = e.extract(request(reasks = 2))
        assertEquals(UnavailableReason.SCHEMA_VALIDATION_FAILED, (r as Outcome.Unavailable).reason)
        assertEquals("one attempt plus two re-asks", 3, rt.prompts.size)
    }

    @Test fun `prose wrapped around the JSON is refused, because pulling it out would be a guess`() = runBlocking {
        val (_, e) = engine(
            """Sure! Here you go: {"items":[{"name":"roti"}]} Hope that helps.""",
            "same again",
            "and again",
        )
        assertTrue(e.extract(request()) is Outcome.Unavailable)
    }

    @Test fun `a field the schema does not know is refused rather than ignored`() = runBlocking {
        val bad = """{"items":[{"name":"roti","calories":250}]}"""
        val (_, e) = engine(bad, bad, bad)
        val r = e.extract(request())
        assertEquals(UnavailableReason.SCHEMA_VALIDATION_FAILED, (r as Outcome.Unavailable).reason)
        assertTrue("$r", r.detail!!.contains("calories"))
    }

    @Test fun `a zero or negative quantity is refused, because nobody ate zero rotis`() = runBlocking {
        listOf("""{"items":[{"name":"roti","quantity":0}]}""", """{"items":[{"name":"roti","quantity":-2}]}""")
            .forEach { bad ->
                val (_, e) = engine(bad, bad, bad)
                assertTrue("$bad should be refused", e.extract(request()) is Outcome.Unavailable)
            }
    }

    @Test fun `a quantity written as a string is refused`() = runBlocking {
        val bad = """{"items":[{"name":"roti","quantity":"two"}]}"""
        val (_, e) = engine(bad, bad, bad)
        assertTrue(e.extract(request()) is Outcome.Unavailable)
    }

    @Test fun `a runaway list is refused`() = runBlocking {
        val many = (1..40).joinToString(",") { """{"name":"food$it"}""" }
        val bad = """{"items":[$many]}"""
        val (_, e) = engine(bad, bad, bad)
        assertTrue(e.extract(request()) is Outcome.Unavailable)
    }

    @Test fun `an empty transcript is refused without consulting the model at all`() = runBlocking {
        val (rt, e) = engine()
        val r = e.extract(request(transcript = "   "))
        assertEquals(UnavailableReason.INPUT_NOT_USABLE, (r as Outcome.Unavailable).reason)
        assertTrue("no model call should have been made", rt.prompts.isEmpty())
    }

    // --- phrasing ----------------------------------------------------------------------------

    private val trigger = TriggerStatement(
        ruleId = RuleIds.LAB_BELOW_RANGE,
        template = TriggerTemplate.LAB_BELOW_RANGE,
        evidence = Evidence.LabValueOutsideRange(
            testName = "Haemoglobin", value = 9.8, unit = "g/dL",
            referenceLow = 12.0, referenceHigh = 15.0, reportDate = LocalDate.of(2026, 9, 12),
        ),
    )

    private fun phrasing() = PhrasingRequest(
        evaluation = RuleEvaluation(
            firedRules = emptyList(),
            constraints = emptyList(),
            rankedCandidates = emptyList(),
            trigger = trigger,
            inputDigest = "test",
        ),
        // Rendered by the caller, in the user's language, before phrasing. The figures in it are
        // the ones the numeric guard is then allowed to permit.
        triggerText = "Your report from 12 September shows haemoglobin at 9.8 g/dL, below the 12.0 printed on it.",
        figures = listOf(DisplayFigure("about 320 kcal"), DisplayFigure("2.5 mg iron")),
        languageTag = "te-IN",
        allowedFoodNames = listOf("palak", "thotakura"),
    )

    @Test fun `a sentence using only the figures it was given is allowed through`() = runBlocking {
        val (_, e) = engine("That meal came to about 320 kcal with 2.5 mg iron. Thotakura would add more iron.")
        val r = e.phrase(phrasing())
        assertTrue("$r", r is Outcome.Ok)
        assertTrue((r as Outcome.Ok).value.numericGuardPassed)
    }

    /** The failure this whole path exists to prevent. */
    @Test fun `an invented figure fails the call and names the token`() = runBlocking {
        val (_, e) = engine("That meal came to roughly 450 kcal.")
        val r = e.phrase(phrasing())
        assertEquals(UnavailableReason.INTERNAL_ERROR, (r as Outcome.Unavailable).reason)
        assertTrue("$r", r.detail!!.contains("450"))
    }

    @Test fun `the model doing arithmetic on its own figures is still an invention`() = runBlocking {
        val (_, e) = engine("320 kcal today and 640 over two meals.")
        assertTrue(e.phrase(phrasing()) is Outcome.Unavailable)
    }

    @Test fun `a figure from the trigger sentence is allowed, since it was given`() = runBlocking {
        val (_, e) = engine("Your haemoglobin at 9.8 g/dL is why thotakura is ranked first now.")
        assertTrue(e.phrase(phrasing()) is Outcome.Ok)
    }

    @Test fun `an empty response fails rather than showing nothing`() = runBlocking {
        val (_, e) = engine("   ")
        assertEquals(UnavailableReason.INTERNAL_ERROR, (e.phrase(phrasing()) as Outcome.Unavailable).reason)
    }

    @Test fun `no typed number reaches the phrasing prompt`() = runBlocking {
        val (rt, e) = engine("About 320 kcal.")
        e.phrase(phrasing())
        val prompt = rt.prompts.single()
        // The figures appear as the exact display strings they will be shown as, never as a bare
        // value the model could be tempted to operate on.
        assertTrue(prompt.contains("about 320 kcal"))
        assertTrue(prompt.contains("2.5 mg iron"))
        assertTrue("the allowed list must be in the prompt", prompt.contains("palak"))
    }

    @Test fun `the two prompts are not the same prompt`() = runBlocking {
        val (rt1, e1) = engine("""{"items":[{"name":"dal"}]}""")
        e1.extract(request())
        val (rt2, e2) = engine("About 320 kcal.")
        e2.phrase(phrasing())
        assertTrue("extraction must ask for JSON", rt1.prompts.single().contains("JSON"))
        assertTrue("phrasing must not ask for JSON", !rt2.prompts.single().contains("JSON"))
    }
}
