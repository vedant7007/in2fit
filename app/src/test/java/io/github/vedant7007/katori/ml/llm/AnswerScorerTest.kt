package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.data.knowledge.Csv
import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The answer scorer, proved on the authored quality set.
 *
 * WHAT THIS PROVES. That the five criteria discriminate: every grounded answer and every short
 * answer scores full marks, every generic answer scores below them, and on the pair the same
 * generic answer scores 0 of 5 against the grounded request. The scorer is mechanical and this
 * shows it is not vacuous.
 *
 * WHAT IT DOES NOT PROVE. Anything about what the model writes. The set is circular. The
 * device run feeds the model's own answers through [AnswerScorer.score] with the same requests,
 * and that is the number, labelled as a small sample when it comes from five speakers.
 */
class AnswerScorerTest {

    private data class Row(
        val id: String, val pair: String, val kind: Intent, val utterance: String, val declared: List<String>,
        val context: String?, val constraints: List<String>, val figures: List<String>, val trigger: String?,
        val allowed: List<String>, val lang: String, val grounded: String, val short: String, val generic: String,
    )

    private val projectDir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
    private val knowledge: KnowledgeFacts by lazy {
        KnowledgeFacts.load { File(projectDir, "app/src/main/assets/" + KnowledgeFacts.ASSET_PATH).inputStream() }
    }

    private fun split(s: String) = s.split('|').map { it.trim() }.filter { it.isNotEmpty() }

    private fun rows(): List<Row> {
        val f = File(projectDir, "data-authoring/answer-quality-set.csv")
        val parsed = Csv.parse(f.readText())
        assertEquals("id", parsed.first().first())
        return parsed.drop(1).map { r ->
            Row(r[0], r[1], Intent.valueOf(r[2]), r[3], split(r[4]), r[5].ifBlank { null }, split(r[6]), split(r[7]),
                r[8].ifBlank { null }, split(r[9]), r[10], r[11], r[12], r[13])
        }
    }

    /** Builds the request the orchestrator would build: retrieval over the utterance, the declared conditions and the allowed names. */
    private fun score(r: Row, answer: String): AnswerScore {
        val facts = knowledge.find((listOf(r.utterance) + r.declared + r.allowed).joinToString(" "))
        return when (r.kind) {
            Intent.ANSWER -> AnswerScorer.score(
                AnswerRequest(r.utterance, r.lang, r.declared, r.context, r.figures.map(::DisplayFigure), facts), answer)
            Intent.RECOMMEND -> AnswerScorer.score(
                RecommendRequest(r.utterance, r.lang, r.declared, r.context, r.constraints, r.trigger, facts, r.allowed, referralFollows = false), answer)
            else -> error("${r.id}: kind must be ANSWER or RECOMMEND")
        }
    }

    @Test fun `grounded and short answers score full marks and generic answers score below them`() {
        val all = rows()
        assertTrue("set looks empty", all.size >= 6)
        val report = StringBuilder("=== ANSWER QUALITY, authored set ===\n")
        report.appendLine("  id                   grounded  short   generic")
        for (r in all) {
            val g = score(r, r.grounded); val s = score(r, r.short); val x = score(r, r.generic)
            report.appendLine("  ${r.id.padEnd(20)} ${g.toString().padEnd(9)} ${s.toString().padEnd(7)} $x")
            assertEquals("${r.id}: grounded answer should score full marks: $g", g.outOf, g.held)
            assertEquals("${r.id}: short answer should score full marks: $s", s.outOf, s.held)
            // On a contextless general question only three criteria apply and an on-topic chatbot
            // answer can keep two; what the pitch claims is the gap, so the gap is what is asserted.
            assertTrue("${r.id}: generic answer should score below the grounded one: $x vs $g", x.held < g.held)
        }
        println(report)
    }

    /** The demo's argument in one screen: the same question, the same generic answer, with and without the person's data. */
    @Test fun `the pair - the generic answer cannot score context without data and scores nothing against it`() {
        val pair = rows().filter { it.pair == "iron" }
        val generic = pair.single { it.id == "iron.generic" }
        val grounded = pair.single { it.id == "iron.grounded" }
        assertEquals("the pair asks the same question", generic.utterance, grounded.utterance)
        assertEquals("the pair uses the same generic answer", generic.generic, grounded.generic)

        val onGeneric = score(generic, generic.generic)
        assertNull("no context was given, so 'uses context' cannot apply", onGeneric.usesContext)
        assertNull("no constraints were given", onGeneric.respectsConstraints)

        val onGrounded = score(grounded, grounded.generic)
        assertEquals("a generic answer against the person's own data: $onGrounded", 0, onGrounded.held)
        assertEquals(5, onGrounded.outOf)
        assertEquals(listOf("traces", "context", "constraints", "judgement", "actionable"), onGrounded.failed)

        val best = score(grounded, grounded.grounded)
        assertEquals("the grounded answer against the same request: $best", 5, best.held)
        println("=== THE PAIR ===\n  '${generic.utterance}'\n  generic answer, no data:        $onGeneric\n  generic answer, with their data: $onGrounded\n  grounded answer, with their data: $best")
    }

    // --- the criteria, one at a time ----------------------------------------------------------

    private val iron = rows().single { it.id == "iron.grounded" }

    @Test fun `an invented number fails traces even when the words are right`() {
        val s = score(iron, iron.grounded.replace("9.8", "9.5"))
        assertTrue(!s.tracesToRows)
    }

    @Test fun `ignoring the report and the situation fails context`() {
        val s = score(iron, "Thotakura and sprouted moong carry iron, and lemon helps it absorb.")
        assertEquals(false, s.usesContext)
        assertTrue(s.tracesToRows && s.actionable)
    }

    @Test fun `naming a constrained food fails constraints`() {
        val s = score(iron, "Your report shows haemoglobin at 9.8 g/dL; mutton and thotakura both carry iron.")
        assertEquals(false, s.respectsConstraints)
    }

    @Test fun `a verdict fails judgement`() {
        val s = score(iron, "Your haemoglobin at 9.8 g/dL is dangerous; thotakura helps.")
        assertTrue(!s.noJudgement)
    }

    @Test fun `no allowed food named fails actionable`() {
        val s = score(iron, "Your report shows haemoglobin at 9.8 g/dL, so iron absorption matters.")
        assertTrue(!s.actionable)
    }
}
