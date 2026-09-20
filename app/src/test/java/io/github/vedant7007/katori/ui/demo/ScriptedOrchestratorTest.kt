package io.github.vedant7007.katori.ui.demo

import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.DefaultRulesEngine
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.RuleIds
import io.github.vedant7007.katori.domain.SpeechLanguageRef
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserIntent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The scripted feed (0027) is script everywhere except the rules engine, and the one thing it
 * must get right is beat 4: the SAME meal, evaluated again after the report, comes back with a
 * different sentence and a different input digest, because the engine says so.
 */
class ScriptedOrchestratorTest {

    private val feed = ScriptedOrchestrator(
        DefaultRulesEngine(),
        ContextText(ContextText.ENGLISH, TriggerText.ENGLISH),
        TriggerText(TriggerText.ENGLISH),
    )
    private val en = SpeechLanguageRef("en-IN")

    @Before fun on() { DemoFeed.set(true) }
    @After fun off() { DemoFeed.set(false) }

    private fun events(intent: UserIntent) = runBlocking { feed.handle(intent).toList() }

    @Test
    fun `the same meal comes back with different advice once the report is in`() {
        val logged = events(UserIntent.Type("Two rotis, a katori of dal, and I used two spoons of oil.", en))
        assertTrue(logged.any { it is OrchestratorEvent.MealLogged })
        val before = logged.filterIsInstance<OrchestratorEvent.Advice>().single()

        DemoFeed.labValues += LabValue("Haemoglobin", 9.8, "g/dL", 13.0, 17.0, ScriptedOrchestrator.REPORT_DATE)
        val again = events(UserIntent.AdviseOnMeal(1L))
        val after = again.filterIsInstance<OrchestratorEvent.Advice>().single()

        assertNotEquals("the input digest must change: that is beat 4's proof", before.evaluation.inputDigest, after.evaluation.inputDigest)
        assertTrue(after.evaluation.firedRules.any { it.id == RuleIds.LAB_BELOW_RANGE })
        assertNotEquals(before.evaluation.trigger, after.evaluation.trigger)
        assertEquals(OrchestratorEvent.Completed, again.last())
    }

    @Test
    fun `a suggest never logs and every turn ends in exactly one terminal event`() {
        val suggest = events(UserIntent.Type("I'm having rice and dal, what should I add?", en))
        assertTrue(suggest.none { it is OrchestratorEvent.MealLogged })
        assertTrue(suggest.filterIsInstance<OrchestratorEvent.MealResolved>().single().hypothetical)
        listOf(
            suggest,
            events(UserIntent.Type("How much protein was in my lunch?", en)),
            events(UserIntent.Type("I have anaemia, what should I eat for iron?", en)),
            events(UserIntent.Type("hmm", en)),
        ).forEach { turn ->
            assertEquals(1, turn.count { it == OrchestratorEvent.Completed || it is OrchestratorEvent.Failed })
        }
    }
}
