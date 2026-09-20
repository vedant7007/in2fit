package io.github.vedant7007.katori.orchestration

import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.llm.Intent
import io.github.vedant7007.katori.orchestration.MinimalRig.FakeLlm
import io.github.vedant7007.katori.orchestration.MinimalRig.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Defect 1 of `0024`, landed red by design and green since the integrator's `3a32642`: the
 * referral is a property of the question as well as of a scanned lab value.
 *
 * With nobody on file the rules engine has nothing to escalate on, so "my haemoglobin is 7, is
 * that dangerous?" gets its referral from `SafetyLine.invitesClinicalJudgement`, decided before
 * the model runs, and a guard failure on a clinical ANSWER shows the fixed line rather than
 * ending the turn with nothing (`0015`: alongside help, never instead of it). A plain food
 * question stays referral-free. The rig is `MinimalRig`.
 */
class ReferralDefectsTest {

    @Test fun `a spoken reading with no report on file still gets the referral on ANSWER`() {
        val llm = FakeLlm()
        val events = run(llm, "my haemoglobin is 7, is that dangerous?")
        assertTrue("the model must be told a referral follows", llm.answers.single().referralFollows)
        val answered = events.filterIsInstance<OrchestratorEvent.Answered>().single()
        assertNotNull("a clinical question with nothing on file got no referral", answered.referral)
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    @Test fun `a medication question with no report on file still gets the referral on RECOMMEND`() {
        val llm = FakeLlm().apply { intent = Outcome.Ok(Intent.RECOMMEND) }
        val events = run(llm, "should I stop my medication if I eat better?")
        assertTrue("the model must be told a referral follows", llm.recommendations.single().referralFollows)
        val advice = events.filterIsInstance<OrchestratorEvent.Advice>().single()
        assertNotNull("a clinical question with nothing on file got no referral", advice.referral)
        assertEquals("the help is still there beside it", "recommended", advice.phrased)
    }

    /** 0015: a referral comes ALONGSIDE help, never instead of it, and never nothing. */
    @Test fun `a guard failure on a clinical ANSWER shows the referral, not Failed`() {
        val llm = FakeLlm(answered = Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "the model invented the number '60'"))
        val events = run(llm, "how much iron tablet should I take?")
        assertTrue("the turn ended in Failed and the person got nothing: $events", events.none { it is OrchestratorEvent.Failed })
        val answered = events.filterIsInstance<OrchestratorEvent.Answered>().single()
        assertNotNull("the fixed referral line is what survives a refused answer", answered.referral)
        assertEquals(OrchestratorEvent.Completed, events.last())
    }

    /** And the control: a plain food question must not grow a referral line. */
    @Test fun `a plain food question with nothing on file gets no referral`() {
        val llm = FakeLlm().apply { intent = Outcome.Ok(Intent.RECOMMEND) }
        val events = run(llm, "what should I eat for more iron")
        assertEquals(false, llm.recommendations.single().referralFollows)
        assertNull(events.filterIsInstance<OrchestratorEvent.Advice>().single().referral)
    }
}
