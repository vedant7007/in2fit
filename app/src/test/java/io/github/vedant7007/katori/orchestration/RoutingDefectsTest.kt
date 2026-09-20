package io.github.vedant7007.katori.orchestration

import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.llm.Intent
import io.github.vedant7007.katori.orchestration.MinimalRig.FakeLlm
import io.github.vedant7007.katori.orchestration.MinimalRig.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TARGET FOR THE INTEGRATOR, FAILING BY DESIGN until the wiring lands: the router of `0033`.
 *
 * On the phone the classifier sent three questions to LOG, and a LOG writes a meal into the
 * diary; in the demo, beat 2 corrupts beat 1. `IntentRouter` makes the words decide where they
 * can and refuses a LOG verdict on any question. The wiring is the one line where the
 * orchestrator consults the model:
 *
 *     decided ?: IntentRouter.decide(text).let { d -> (d as? Decided)?.intent?.toDomain() }
 *       ?: run { classify(text) ... IntentRouter.accept(c.value, text)?.toDomain() ?: NeedsIntent(text) }
 *
 * The rig has nobody on file. `MinimalRig.FakeLlm.intent` is what the model would say.
 */
class RoutingDefectsTest {

    /** The misroute from the phone, made impossible: a question the model calls LOG never reaches the diary. */
    @Test fun `a question the model calls LOG is refused and the person is asked`() {
        val llm = FakeLlm().apply { intent = Outcome.Ok(Intent.LOG) }
        // "how many rotis have I eaten today" is decided ANSWER by the words and never reaches
        // the model; the refusal is exercised where the evidence conflicts and the model is asked.
        val events = run(llm, "I'm having rice now, what did I eat yesterday?")
        assertEquals("a question was written into the diary: ${MinimalRig.lastStore.saved}", 0, MinimalRig.lastStore.saved.size)
        assertTrue("a question was resolved as a plate: $events", events.none { it is OrchestratorEvent.MealLogged || it is OrchestratorEvent.MealResolved })
        assertTrue("the person must be asked which they meant: $events", events.any { it is OrchestratorEvent.NeedsIntent })
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    /** The words decide, so the model is not consulted and its failure cannot end the turn. */
    @Test fun `a sentence the words can route never consults the model`() {
        val llm = FakeLlm().apply { intent = Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "must not be consulted") }
        val events = run(llm, "what should I eat for more iron")
        assertTrue("the model was consulted and its failure ended the turn: $events", events.none { it is OrchestratorEvent.Failed })
        assertTrue("the words say RECOMMEND: $events", events.any { it is OrchestratorEvent.Advice })
        val asked = run(llm, "how much protein did I eat today")
        assertTrue("the words say ANSWER: $asked", asked.any { it is OrchestratorEvent.Answered })
    }

    /**
     * `0025`: SHORT is the default for a spoken turn. The engine now takes the length explicitly
     * and the orchestrator's default is what the phone runs; until it is SHORT the ruling is not
     * in effect. One word in the constructor default, or `AppModule` passing it.
     */
    @Test fun `a spoken turn asks the model for the short answer by default`() {
        val llm = FakeLlm().apply { intent = Outcome.Ok(Intent.ANSWER) }
        run(llm, "how much protein did I eat today")
        assertEquals(listOf(io.github.vedant7007.katori.ml.llm.AnswerLength.SHORT), llm.lengths)
    }

    /** Where the words are silent, the model still decides, as before. */
    @Test fun `a sentence with no evidence still goes to the model`() {
        val llm = FakeLlm().apply { intent = Outcome.Ok(Intent.SUGGEST) }
        // no eating word, no quantity, no marker: "tea with two biscuits" was this case until a
        // quantity beside a food became a meal stated (the Beat 1 sentence has no verb)
        val events = run(llm, "tea and biscuits")
        assertTrue("$events", events.any { it is OrchestratorEvent.Progress && (it as OrchestratorEvent.Progress).stage.name == "CLASSIFYING" })
    }
}
